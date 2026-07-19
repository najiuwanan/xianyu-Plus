<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { getAccountList } from '@/api/account'
import { uploadImage } from '@/api/image'
import { publishProduct, type ProductPublishImage, type ProductPublishProperty } from '@/api/product-publish'
import { checkPublishCapability, type PublishCapabilityResult } from '@/api/system-check'
import type { Account } from '@/types'
import { toast } from '@/utils/toast'

defineOptions({ name: 'ProductPublishWorkbench' })

const accounts = ref<Account[]>([])
const selectedAccountId = ref<number | null>(null)
const probing = ref(false)
const publishing = ref(false)
const uploading = ref(false)
const schema = ref<PublishCapabilityResult | null>(null)
const images = ref<ProductPublishImage[]>([])
const selectedProperties = ref<Record<string, string | string[]>>({})
const acknowledged = ref(false)
const confirmation = ref('')
const createRequestId = () => globalThis.crypto?.randomUUID?.() ||
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, character => {
    const random = Math.floor(Math.random() * 16)
    return (character === 'x' ? random : (random & 0x3) | 0x8).toString(16)
  })
const requestId = ref(createRequestId())

const form = reactive({
  title: '',
  description: '',
  price: 0,
  originalPrice: 0,
  quantity: 1,
  deliveryMode: 'FREE' as 'FREE' | 'FLAT' | 'NONE' | 'SELF_PICKUP',
  postFee: 0
})

const propertyKey = (property: PublishCapabilityResult['properties'][number]) => property.propertyId || property.propertyName
const requiredPropertiesReady = computed(() => (schema.value?.properties || []).every(property => {
  if (!property.required) return true
  const value = selectedProperties.value[propertyKey(property)]
  return Array.isArray(value) ? value.length > 0 : Boolean(value)
}))
const canPublish = computed(() => Boolean(
  selectedAccountId.value && schema.value?.supportLevel === 'GENERAL_FORM' && schema.value.locationApiReady &&
  schema.value.dependentPropertyCount === 0 &&
  requiredPropertiesReady.value &&
  images.value.length && form.title.trim().length >= 2 && form.description.trim().length >= 2 &&
  form.price > 0 && acknowledged.value && confirmation.value === '确认发布' && !publishing.value
))

const loadAccounts = async () => {
  const result = await getAccountList()
  accounts.value = result.data?.accounts || []
  selectedAccountId.value = accounts.value[0]?.id || null
}

watch(selectedAccountId, () => {
  schema.value = null
  images.value = []
  selectedProperties.value = {}
})
watch(() => form.title, () => {
  if (schema.value && form.title.trim()) {
    schema.value = null
    selectedProperties.value = {}
  }
})

const probe = async () => {
  if (!selectedAccountId.value) return toast.warning('请先选择发布账号')
  if (form.title.trim().length < 2) return toast.warning('请先填写明确的商品标题')
  probing.value = true
  try {
    const result = await checkPublishCapability({ accountId: selectedAccountId.value, title: form.title.trim() })
    schema.value = result.data || null
    const values: Record<string, string | string[]> = {}
    for (const property of schema.value?.properties || []) {
      const selected = property.options.filter(option => option.selected).map(option => option.valueId || option.valueName)
      values[propertyKey(property)] = property.multiple ? selected : (selected[0] || '')
    }
    selectedProperties.value = values
    if (schema.value?.supportLevel === 'GENERAL_FORM') toast.success('类目检测通过，可以继续填写发布信息')
    else toast.warning(schema.value?.supportLabel || '该类目暂不能自动发布')
  } catch (error: any) {
    if (!error?.messageShown) toast.error(error?.message || '类目检测失败')
  } finally {
    probing.value = false
  }
}

const imageSize = (file: File) => new Promise<{ width: number; height: number }>((resolve, reject) => {
  const url = URL.createObjectURL(file)
  const image = new Image()
  image.onload = () => { resolve({ width: image.naturalWidth, height: image.naturalHeight }); URL.revokeObjectURL(url) }
  image.onerror = () => { reject(new Error('无法读取图片尺寸')); URL.revokeObjectURL(url) }
  image.src = url
})

const uploadFiles = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!selectedAccountId.value) return toast.warning('请先选择发布账号')
  if (images.value.length + files.length > 9) return toast.warning('商品图片最多 9 张')
  uploading.value = true
  try {
    for (const file of files) {
      if (!file.type.startsWith('image/') || file.size > 10 * 1024 * 1024) {
        throw new Error('仅支持 10MB 以内的图片')
      }
      const originalSize = await imageSize(file)
      const scale = Math.min(1, 1920 / originalSize.width, 1920 / originalSize.height)
      const size = { width: Math.round(originalSize.width * scale), height: Math.round(originalSize.height * scale) }
      const result = await uploadImage(selectedAccountId.value, file)
      if (result.code !== 200 || !result.data) throw new Error(result.msg || '图片上传失败')
      images.value.push({ url: result.data, width: size.width, height: size.height })
    }
    toast.success('图片上传成功')
  } catch (error: any) {
    toast.error(error?.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

const removeImage = (index: number) => images.value.splice(index, 1)

const propertyPayload = (): ProductPublishProperty[] => {
  const payload: ProductPublishProperty[] = []
  for (const property of schema.value?.properties || []) {
    const value = selectedProperties.value[propertyKey(property)]
    for (const item of Array.isArray(value) ? value : (value ? [value] : [])) {
      payload.push({ propertyId: property.propertyId, valueKey: item })
    }
  }
  return payload
}

const submit = async () => {
  if (!canPublish.value || !selectedAccountId.value) return toast.warning('请完成所有发布信息和风险确认')
  if (!window.confirm(`确认使用当前账号发布“${form.title}”吗？发布后商品会真实出现在闲鱼。`)) return
  publishing.value = true
  try {
    const result = await publishProduct({
      accountId: selectedAccountId.value,
      requestId: requestId.value,
      title: form.title.trim(),
      description: form.description.trim(),
      price: form.price,
      originalPrice: form.originalPrice > 0 ? form.originalPrice : undefined,
      quantity: form.quantity,
      deliveryMode: form.deliveryMode,
      postFee: form.deliveryMode === 'FLAT' ? form.postFee : undefined,
      acknowledged: acknowledged.value,
      confirmation: confirmation.value,
      images: images.value,
      properties: propertyPayload()
    })
    if (result.data?.success) {
      toast.success(result.data.message || '商品发布成功')
      requestId.value = createRequestId()
      acknowledged.value = false
      confirmation.value = ''
    } else {
      toast.warning(result.data?.message || '发布结果暂时无法确认，请同步商品列表检查')
    }
  } catch (error: any) {
    if (!error?.messageShown) toast.error(error?.message || '商品发布失败')
  } finally {
    publishing.value = false
  }
}

onMounted(loadAccounts)
</script>

<template>
  <main class="publish-page">
    <header class="publish-page__header">
      <div><h1>发布商品</h1><p>先识别类目并填写动态属性，确认后才会真实发布到闲鱼。</p></div>
      <span>第二阶段 · 单账号普通实物</span>
    </header>

    <section class="publish-card">
      <h2>1. 账号与商品内容</h2>
      <div class="form-grid">
        <label><span>发布账号</span><select v-model="selectedAccountId"><option :value="null" disabled>请选择</option><option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.accountNote || account.unb }}</option></select></label>
        <label class="wide"><span>商品标题</span><input v-model="form.title" maxlength="60" placeholder="例如：iPhone 15 Pro 256G 原装二手手机"></label>
        <button class="probe-button" type="button" :disabled="probing" @click="probe">{{ probing ? '识别中…' : '识别类目' }}</button>
        <label class="full"><span>商品描述</span><textarea v-model="form.description" maxlength="5000" rows="6" placeholder="说明成色、规格、配件、交付方式等信息"></textarea></label>
      </div>
    </section>

    <section v-if="schema" class="publish-card">
      <div class="section-title"><h2>2. 类目与动态属性</h2><span :class="`level level--${schema.supportLevel.toLowerCase()}`">{{ schema.categoryName }} · {{ schema.supportLabel }}</span></div>
      <div v-if="schema.supportLevel !== 'GENERAL_FORM'" class="blocked-tip">该类目需要专项适配，本阶段不会调用真实发布接口。</div>
      <div class="property-grid">
        <label v-for="property in schema.properties" :key="propertyKey(property)">
          <span>{{ property.propertyName }} <em v-if="property.required">必填</em></span>
          <select v-if="property.options.length" v-model="selectedProperties[propertyKey(property)]" :multiple="property.multiple">
            <option v-if="!property.multiple" value="">请选择</option>
            <option v-for="option in property.options" :key="`${propertyKey(property)}-${option.valueId}-${option.valueName}`" :value="option.valueId || option.valueName" :disabled="option.disabled">{{ option.valueName }}</option>
          </select>
          <small v-else>联动选项尚未返回，当前类目暂不能完整提交此字段。</small>
        </label>
      </div>
    </section>

    <section class="publish-card">
      <h2>3. 图片、价格与交付</h2>
      <div class="image-list">
        <div v-for="(image, index) in images" :key="image.url" class="image-card"><img :src="image.url"><button type="button" @click="removeImage(index)">×</button><small v-if="index === 0">主图</small></div>
        <label v-if="images.length < 9" class="image-add"><input type="file" accept="image/*" multiple :disabled="uploading" @change="uploadFiles"><strong>{{ uploading ? '上传中…' : '+ 添加图片' }}</strong><span>最多 9 张</span></label>
      </div>
      <div class="form-grid pricing">
        <label><span>售价（元）</span><input v-model.number="form.price" type="number" min="0.01" step="0.01"></label>
        <label><span>原价（可选）</span><input v-model.number="form.originalPrice" type="number" min="0" step="0.01"></label>
        <label><span>库存</span><input v-model.number="form.quantity" type="number" min="1" max="999"></label>
        <label><span>交付方式</span><select v-model="form.deliveryMode"><option value="FREE">包邮</option><option value="FLAT">固定运费</option><option value="NONE">无需邮寄</option><option value="SELF_PICKUP">仅自提</option></select></label>
        <label v-if="form.deliveryMode === 'FLAT'"><span>运费（元）</span><input v-model.number="form.postFee" type="number" min="0" step="0.01"></label>
      </div>
    </section>

    <section class="publish-card confirm-card">
      <h2>4. 最终确认</h2>
      <label class="ack"><input v-model="acknowledged" type="checkbox">我已核对图片、价格、库存、类目和商品描述，并确认商品符合闲鱼规则。</label>
      <label><span>输入“确认发布”</span><input v-model="confirmation" placeholder="确认发布"></label>
      <button type="button" class="publish-button" :disabled="!canPublish" @click="submit">{{ publishing ? '正在发布…' : '确认并真实发布' }}</button>
    </section>
  </main>
</template>

<style scoped>
.publish-page{max-width:1180px;margin:0 auto;padding:4px 0 48px;color:#1d2939}.publish-page__header,.section-title{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.publish-page__header{margin-bottom:18px}.publish-page__header h1{margin:0;font-size:27px}.publish-page__header p{margin:6px 0 0;color:#667085}.publish-page__header>span{padding:7px 11px;border-radius:999px;background:#eef4ff;color:#3538cd;font-size:12px;font-weight:700}.publish-card{margin-bottom:16px;padding:20px;border:1px solid #e4e7ec;border-radius:14px;background:#fff;box-shadow:0 2px 8px rgba(16,24,40,.04)}.publish-card h2{margin:0 0 16px;font-size:17px}.form-grid{display:grid;grid-template-columns:220px minmax(260px,1fr) auto;gap:14px;align-items:end}.form-grid label,.property-grid label,.confirm-card>label{display:flex;flex-direction:column;gap:6px}.form-grid label>span,.property-grid label>span,.confirm-card label>span{font-size:12px;font-weight:700;color:#475467}.form-grid .wide{min-width:0}.form-grid .full{grid-column:1/-1}.form-grid input,.form-grid select,.form-grid textarea,.property-grid select,.confirm-card>label>input{box-sizing:border-box;width:100%;border:1px solid #d0d5dd;border-radius:8px;background:#fff;padding:10px 11px;color:#1d2939;outline:none}.form-grid textarea{resize:vertical}.probe-button,.publish-button{border:0;border-radius:9px;background:#1570ef;color:#fff;font-weight:700;cursor:pointer}.probe-button{height:40px;padding:0 18px}.probe-button:disabled,.publish-button:disabled{opacity:.5;cursor:not-allowed}.section-title h2{margin:0}.level{padding:5px 9px;border-radius:999px;font-size:12px;font-weight:700}.level--general_form{background:#dcfae6;color:#067647}.level--special_adapter{background:#fef0c7;color:#b54708}.level--blocked{background:#fee4e2;color:#b42318}.blocked-tip{margin:14px 0;padding:11px;border-radius:8px;background:#fffaeb;color:#93370d;font-size:13px}.property-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:16px}.property-grid em{font-style:normal;color:#d92d20}.property-grid small{padding:9px;border:1px dashed #fdb022;border-radius:7px;background:#fffaeb;color:#b54708}.image-list{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:18px}.image-card,.image-add{position:relative;width:112px;height:112px;box-sizing:border-box;border-radius:10px;overflow:hidden}.image-card img{width:100%;height:100%;object-fit:cover}.image-card button{position:absolute;right:5px;top:5px;width:24px;height:24px;border:0;border-radius:50%;background:#0009;color:#fff;cursor:pointer}.image-card small{position:absolute;left:5px;bottom:5px;padding:2px 6px;border-radius:999px;background:#1570ef;color:#fff}.image-add{display:grid;place-content:center;gap:5px;border:1px dashed #98a2b3;text-align:center;color:#667085;cursor:pointer}.image-add input{display:none}.image-add strong{color:#1570ef}.image-add span{font-size:11px}.pricing{grid-template-columns:repeat(5,minmax(120px,1fr))}.confirm-card{display:grid;grid-template-columns:1fr 220px 190px;align-items:end;gap:14px}.confirm-card h2{grid-column:1/-1}.confirm-card .ack{display:flex;flex-direction:row;align-items:center;font-size:13px;color:#475467}.publish-button{height:42px}.publish-button:not(:disabled){background:#d92d20}@media(max-width:900px){.form-grid,.pricing,.property-grid,.confirm-card{grid-template-columns:1fr}.confirm-card h2{grid-column:auto}.publish-page__header{flex-direction:column}}
</style>

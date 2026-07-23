<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAccountList } from '@/api/account'
import { uploadImage } from '@/api/image'
import { getPublishLocations, publishProduct, type ProductPublishImage, type ProductPublishLocation, type ProductPublishProperty } from '@/api/product-publish'
import { generateProductCopywriting, getProductMaterial, saveProductMaterial } from '@/api/product-material'
import { checkPublishCapability, type PublishCapabilityResult } from '@/api/system-check'
import type { Account } from '@/types'
import { toast } from '@/utils/toast'

defineOptions({ name: 'ProductPublishWorkbench' })

const route = useRoute()
const router = useRouter()

const accounts = ref<Account[]>([])
const selectedAccountId = ref<number | null>(null)
const probing = ref(false)
const publishing = ref(false)
const uploading = ref(false)
const schema = ref<PublishCapabilityResult | null>(null)
const images = ref<ProductPublishImage[]>([])
const locations = ref<ProductPublishLocation[]>([])
const loadingLocations = ref(false)
const selectedLocationKey = ref('')
const lookupCoordinates = ref<{ longitude: number; latitude: number } | null>(null)
const customPoiName = ref('')
const selectedProperties = ref<Record<string, string | string[]>>({})
const acknowledged = ref(false)
const confirmation = ref('')
const createRequestId = () => globalThis.crypto?.randomUUID?.() ||
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, character => {
    const random = Math.floor(Math.random() * 16)
    return (character === 'x' ? random : (random & 0x3) | 0x8).toString(16)
  })
const requestId = ref(createRequestId())
const materialId = ref<number | undefined>(undefined)
const materialName = ref('')
const savingMaterial = ref(false)
const aiGenerating = ref(false)
const aiStyle = ref<'NATURAL' | 'CONCISE' | 'DETAILED' | 'PROMOTIONAL'>('NATURAL')
const aiFacts = ref('')
const aiImageUsed = ref<boolean | null>(null)
const batchMode = ref(route.query.batch === '1')
const targetAccountIds = ref<number[]>([])
type BatchStatus = 'PENDING' | 'CHECKING' | 'READY' | 'FAILED' | 'PUBLISHED'
interface BatchState {
  accountId: number
  accountName: string
  status: BatchStatus
  message: string
  categoryName?: string
  locations: ProductPublishLocation[]
  locationKey: string
  properties: ProductPublishProperty[]
  description: string
  itemId?: string
}
const batchStates = ref<BatchState[]>([])
const preparingBatch = ref(false)
const generatingVariations = ref(false)

const form = reactive({
  title: '',
  description: '',
  price: 0,
  originalPrice: 0,
  quantity: 1,
  deliveryMode: 'FREE' as 'FREE' | 'FLAT' | 'NONE' | 'SELF_PICKUP',
  postFee: 0
})

const supportsPublishForm = (supportLevel?: string) => supportLevel === 'GENERAL_FORM' || supportLevel === 'SERVICE_FORM'

const propertyKey = (property: PublishCapabilityResult['properties'][number]) => property.propertyId || property.propertyName
const requiredPropertiesReady = computed(() => (schema.value?.properties || []).every(property => {
  if (!property.required) return true
  const value = selectedProperties.value[propertyKey(property)]
  return Array.isArray(value) ? value.length > 0 : Boolean(value)
}))
const canPublish = computed(() => Boolean(
  selectedAccountId.value && supportsPublishForm(schema.value?.supportLevel) && schema.value.locationApiReady &&
  schema.value.dependentPropertyCount === 0 &&
  requiredPropertiesReady.value &&
  images.value.length && selectedLocationKey.value && form.title.trim().length >= 2 && form.description.trim().length >= 2 &&
  form.price > 0 && acknowledged.value && confirmation.value === '确认发布' && !publishing.value
))
const canBatchPublish = computed(() => Boolean(batchMode.value && targetAccountIds.value.length > 1 &&
  batchStates.value.length === targetAccountIds.value.length &&
  batchStates.value.every(state => state.status === 'READY' && state.locationKey) &&
  images.value.length && form.title.trim().length >= 2 && form.description.trim().length >= 2 && form.price > 0 &&
  acknowledged.value && confirmation.value === '确认发布' && !publishing.value))
const selectedLocation = computed(() => locations.value.find(location => location.key === selectedLocationKey.value) || null)
const locationSourceLabel = (source: string) => ({ SELECTED: '平台默认', COMMON: '常用地址', NEARBY: '附近地点' }[source] || '平台地点')

const loadAccounts = async () => {
  const result = await getAccountList()
  accounts.value = result.data?.accounts || []
  selectedAccountId.value = accounts.value[0]?.id || null
}

const loadLocations = async (coordinates?: { longitude: number; latitude: number }) => {
  if (!selectedAccountId.value) return
  loadingLocations.value = true
  try {
    lookupCoordinates.value = coordinates || null
    const result = await getPublishLocations({ accountId: selectedAccountId.value, ...coordinates })
    locations.value = result.data || []
    selectedLocationKey.value = locations.value.find(location => location.selected)?.key || locations.value[0]?.key || ''
    if (!locations.value.length) toast.warning('闲鱼没有返回可选发布地点')
  } catch (error: any) {
    locations.value = []
    selectedLocationKey.value = ''
    if (!error?.messageShown) toast.error(error?.message || '发布地点加载失败')
  } finally {
    loadingLocations.value = false
  }
}

const useBrowserLocation = () => {
  if (!navigator.geolocation) return toast.warning('当前浏览器不支持定位')
  loadingLocations.value = true
  navigator.geolocation.getCurrentPosition(
    position => void loadLocations({ longitude: position.coords.longitude, latitude: position.coords.latitude }),
    () => { loadingLocations.value = false; toast.error('无法获取当前位置，请检查浏览器定位权限') },
    { enableHighAccuracy: true, timeout: 12000 }
  )
}

watch(selectedAccountId, () => {
  schema.value = null
  selectedProperties.value = {}
  locations.value = []
  selectedLocationKey.value = ''
  lookupCoordinates.value = null
  customPoiName.value = ''
  batchStates.value = []
  if (selectedAccountId.value) void loadLocations()
})
watch(targetAccountIds, () => { batchStates.value = [] }, { deep: true })
watch(batchMode, () => {
  batchStates.value = []
  acknowledged.value = false
  confirmation.value = ''
})
watch(() => form.title, () => {
  if (schema.value && form.title.trim()) {
    schema.value = null
    selectedProperties.value = {}
  }
  batchStates.value = []
})
watch([() => form.description, () => form.price, () => form.originalPrice, () => form.quantity, () => form.deliveryMode, () => form.postFee], () => {
  batchStates.value = []
})
watch(selectedProperties, () => { batchStates.value = [] }, { deep: true })
watch(images, () => { batchStates.value = [] }, { deep: true })

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
    if (supportsPublishForm(schema.value?.supportLevel)) {
      toast.success(schema.value?.supportLevel === 'SERVICE_FORM' ? '已识别拼单/助力服务表单，请完善服务字段' : '类目检测通过，可以继续填写发布信息')
    } else {
      toast.warning(schema.value?.supportLabel || '该类目暂不能自动发布')
    }
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

const loadMaterial = async (id: number) => {
  const result = await getProductMaterial(id)
  const material = result.data
  if (!material) return
  materialId.value = material.id
  materialName.value = material.materialName
  form.title = material.title
  form.description = material.description || ''
  form.price = Number(material.price || 0)
  form.originalPrice = Number(material.originalPrice || 0)
  form.quantity = material.quantity || 1
  form.deliveryMode = material.deliveryMode
  form.postFee = Number(material.postFee || 0)
  images.value = material.images || []
}

const saveMaterial = async () => {
  if (!materialName.value.trim()) return toast.warning('请先填写素材名称')
  if (form.title.trim().length < 2) return toast.warning('请先填写商品标题')
  savingMaterial.value = true
  try {
    const result = await saveProductMaterial({
      id: materialId.value,
      materialName: materialName.value.trim(),
      title: form.title.trim(),
      description: form.description.trim(),
      price: form.price,
      originalPrice: form.originalPrice > 0 ? form.originalPrice : undefined,
      quantity: form.quantity,
      deliveryMode: form.deliveryMode,
      postFee: form.deliveryMode === 'FLAT' ? form.postFee : undefined,
      images: images.value
    })
    materialId.value = result.data?.id
    toast.success('商品素材已保存，可在素材库多账号复用')
  } finally {
    savingMaterial.value = false
  }
}

const runAi = async (mode: 'GENERATE' | 'POLISH') => {
  if (form.title.trim().length < 2) return toast.warning('请先填写商品标题')
  aiGenerating.value = true
  try {
    const result = await generateProductCopywriting({
      mode,
      title: form.title.trim(),
      description: form.description.trim(),
      style: aiStyle.value,
      facts: aiFacts.value.trim(),
      price: form.price > 0 ? form.price : undefined,
      images: images.value
    })
    if (result.data?.description) {
      form.description = result.data.description
      aiImageUsed.value = result.data.imageUsed
      toast.success(result.data.imageUsed ? 'AI 已结合图片生成描述' : 'AI 已根据文字信息生成描述')
    }
  } finally {
    aiGenerating.value = false
  }
}

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

const propertyNames = () => {
  const values = new Map<string, string[]>()
  for (const property of schema.value?.properties || []) {
    const selected = selectedProperties.value[propertyKey(property)]
    const keys = Array.isArray(selected) ? selected : (selected ? [selected] : [])
    values.set(property.propertyName, keys.map(key => property.options.find(option => option.valueId === key || option.valueName === key)?.valueName || key))
  }
  return values
}

const mapProperties = (accountSchema: PublishCapabilityResult, names: Map<string, string[]>): ProductPublishProperty[] => {
  const payload: ProductPublishProperty[] = []
  for (const property of accountSchema.properties || []) {
    const wanted = names.get(property.propertyName) || []
    const options = property.options.filter(option => wanted.includes(option.valueName))
    if (!options.length) {
      const fallback = property.options.filter(option => option.selected)
      options.push(...(property.multiple ? fallback : fallback.slice(0, 1)))
    }
    if (property.required && !options.length) throw new Error(`必填属性“${property.propertyName}”在该账号没有可用选项`)
    for (const option of options) payload.push({ propertyId: property.propertyId, valueKey: option.valueId || option.valueName })
  }
  return payload
}

const accountLabel = (id: number) => {
  const account = accounts.value.find(item => item.id === id)
  return account?.accountNote || account?.unb || `账号 ${id}`
}

const prepareBatch = async () => {
  if (targetAccountIds.value.length < 2) return toast.warning('请至少选择两个发布账号')
  if (!schema.value || !requiredPropertiesReady.value) return toast.warning('请先用主账号识别类目并选择必填属性')
  preparingBatch.value = true
  const names = propertyNames()
  const states: BatchState[] = targetAccountIds.value.map(accountId => ({
    accountId, accountName: accountLabel(accountId), status: 'CHECKING', message: '正在读取类目和地址',
    locations: [], locationKey: '', properties: [], description: form.description.trim()
  }))
  batchStates.value = states
  for (const state of states) {
    try {
      const capability = await checkPublishCapability({ accountId: state.accountId, title: form.title.trim() })
      const accountSchema = capability.data
      if (!accountSchema || !supportsPublishForm(accountSchema.supportLevel) || !accountSchema.locationApiReady || accountSchema.dependentPropertyCount > 0) {
        throw new Error(accountSchema?.supportLabel || '该账号未通过发布能力检测')
      }
      const addressResult = await getPublishLocations({ accountId: state.accountId })
      state.locations = addressResult.data || []
      state.locationKey = state.locations.find(location => location.selected)?.key || state.locations[0]?.key || ''
      if (!state.locationKey) throw new Error('该账号没有可用发布地址')
      state.properties = mapProperties(accountSchema, names)
      state.categoryName = accountSchema.categoryName
      state.status = 'READY'
      state.message = '类目、属性与地址预检通过'
    } catch (error: any) {
      state.status = 'FAILED'
      state.message = error?.message || '预检失败'
    }
  }
  batchStates.value = [...states]
  preparingBatch.value = false
  const ready = states.filter(state => state.status === 'READY').length
  if (ready === states.length) toast.success(`${ready} 个账号全部预检通过`)
  else toast.warning(`${ready}/${states.length} 个账号预检通过，请处理失败账号`)
}

const generateVariations = async () => {
  const ready = batchStates.value.filter(state => state.status === 'READY')
  if (!ready.length) return toast.warning('请先完成多账号预检')
  generatingVariations.value = true
  for (const [index, state] of ready.entries()) {
    try {
      const result = await generateProductCopywriting({
        mode: 'VARIATION', title: form.title.trim(), description: form.description.trim(), style: aiStyle.value,
        facts: aiFacts.value.trim(), price: form.price, variationIndex: index + 1, images: images.value
      })
      state.description = result.data?.description || form.description.trim()
      state.message = '预检通过，已生成差异化描述'
    } catch {
      state.description = form.description.trim()
      state.message = 'AI差异文案失败，将使用原描述'
    }
  }
  batchStates.value = [...batchStates.value]
  generatingVariations.value = false
  toast.success('多账号描述处理完成，可逐条预览')
}

const submitBatch = async () => {
  if (!canBatchPublish.value) return toast.warning('请先完成全部账号预检和风险确认')
  if (!window.confirm(`确认向 ${batchStates.value.length} 个账号逐个真实发布“${form.title}”吗？`)) return
  publishing.value = true
  for (const state of batchStates.value) {
    try {
      const result = await publishProduct({
        accountId: state.accountId, requestId: createRequestId(), title: form.title.trim(), description: state.description,
        price: form.price, originalPrice: form.originalPrice > 0 ? form.originalPrice : undefined, quantity: form.quantity,
        deliveryMode: form.deliveryMode, postFee: form.deliveryMode === 'FLAT' ? form.postFee : undefined,
        acknowledged: true, confirmation: '确认发布',
        address: { locationKey: state.locationKey }, images: images.value, properties: state.properties
      })
      if (!result.data?.success) throw new Error(result.data?.message || '发布结果无法确认')
      state.status = 'PUBLISHED'
      state.itemId = result.data.itemId
      state.message = result.data.message || '发布成功'
    } catch (error: any) {
      state.status = 'FAILED'
      state.message = error?.message || '发布失败'
    }
    batchStates.value = [...batchStates.value]
  }
  publishing.value = false
  const success = batchStates.value.filter(state => state.status === 'PUBLISHED').length
  toast[success === batchStates.value.length ? 'success' : 'warning'](`批量发布完成：成功 ${success}，失败 ${batchStates.value.length - success}`)
  acknowledged.value = false
  confirmation.value = ''
}

const submit = async () => {
  if (batchMode.value) return submitBatch()
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
      address: {
        locationKey: selectedLocationKey.value,
        lookupLongitude: lookupCoordinates.value?.longitude,
        lookupLatitude: lookupCoordinates.value?.latitude,
        customPoiName: customPoiName.value.trim() || undefined
      },
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

onMounted(async () => {
  await loadAccounts()
  const id = Number(route.query.materialId)
  if (Number.isInteger(id) && id > 0) await loadMaterial(id)
  if (batchMode.value) targetAccountIds.value = accounts.value.map(account => account.id)
})
</script>

<template>
  <main class="publish-page">
    <header class="publish-page__header">
      <div><h1>发布商品</h1><p>先识别类目并填写动态属性，确认后才会真实发布到闲鱼。</p></div>
      <div class="header-actions"><button @click="router.push('/product-materials')">商品素材库</button><span>素材复用 · 多账号安全发布</span></div>
    </header>

    <section class="material-bar">
      <label><span>素材名称</span><input v-model="materialName" maxlength="120" placeholder="例如：蓝牙耳机通用发布素材"></label>
      <button type="button" :disabled="savingMaterial" @click="saveMaterial">{{ savingMaterial ? '保存中…' : materialId ? '更新素材' : '保存到素材库' }}</button>
      <small>保存只会写入素材库，不会发布商品。</small>
    </section>

    <section class="publish-card">
      <h2>1. 账号与商品内容</h2>
      <div class="form-grid">
        <label><span>发布账号</span><select v-model="selectedAccountId"><option :value="null" disabled>请选择</option><option v-for="account in accounts" :key="account.id" :value="account.id">{{ account.accountNote || account.unb }}</option></select></label>
        <label class="wide"><span>商品标题</span><input v-model="form.title" maxlength="60" placeholder="例如：iPhone 15 Pro 256G 原装二手手机"></label>
        <button class="probe-button" type="button" :disabled="probing" @click="probe">{{ probing ? '识别中…' : '识别类目' }}</button>
        <div class="description-workspace full">
          <label><span>商品描述</span><textarea v-model="form.description" maxlength="5000" rows="9" placeholder="说明成色、规格、配件、交付方式等信息"></textarea></label>
          <aside class="ai-panel">
            <div><strong>AI 文案助手</strong><small>不会自动发布，生成后仍可手动修改</small></div>
            <label><span>文案风格</span><select v-model="aiStyle"><option value="NATURAL">自然真实</option><option value="CONCISE">简洁直接</option><option value="DETAILED">专业详细</option><option value="PROMOTIONAL">有吸引力</option></select></label>
            <label><span>必须保留的事实</span><textarea v-model="aiFacts" rows="3" maxlength="2000" placeholder="例如：九成新、原装配件、顺丰到付。AI不会自行编造。"></textarea></label>
            <div class="ai-buttons"><button type="button" :disabled="aiGenerating" @click="runAi('GENERATE')">看图/标题生成</button><button type="button" :disabled="aiGenerating || !form.description.trim()" @click="runAi('POLISH')">润色现有描述</button></div>
            <small v-if="aiImageUsed !== null" :class="aiImageUsed ? 'vision-ok' : 'vision-fallback'">{{ aiImageUsed ? '本次已使用图片理解' : '当前模型未使用图片，已按文字生成' }}</small>
          </aside>
        </div>
      </div>
    </section>

    <section v-if="schema" class="publish-card">
      <div class="section-title"><h2>2. 类目与动态属性</h2><span :class="`level level--${schema.supportLevel.toLowerCase()}`">{{ schema.categoryName }} · {{ schema.supportLabel }}</span></div>
      <div v-if="schema.supportLevel === 'SERVICE_FORM'" class="service-tip">已识别拼单/助力服务表单。请完整选择交付周期、服务类型和计价方式；系统会将平台返回的动态字段一并提交。</div>
      <div v-else-if="!supportsPublishForm(schema.supportLevel)" class="blocked-tip">该类目需要专项适配，当前不会调用真实发布接口。</div>
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

    <section class="publish-card batch-card">
      <div class="section-title"><div><h2>多账号发布</h2><p>每个账号分别读取类目、属性和地址，确认后按顺序逐个发布。</p></div><label class="mode-switch"><input v-model="batchMode" type="checkbox">启用多账号发布</label></div>
      <template v-if="batchMode">
        <div class="account-checks">
          <label v-for="account in accounts" :key="account.id"><input v-model="targetAccountIds" type="checkbox" :value="account.id">{{ account.accountNote || account.unb }}</label>
        </div>
        <div class="batch-tools">
          <button type="button" :disabled="preparingBatch" @click="prepareBatch">{{ preparingBatch ? '逐账号预检中…' : '预检所选账号' }}</button>
          <button type="button" :disabled="generatingVariations || !batchStates.some(state => state.status === 'READY')" @click="generateVariations">{{ generatingVariations ? '生成中…' : 'AI生成差异化描述' }}</button>
          <small>先用上方主账号选择类目属性，再执行多账号预检。</small>
        </div>
        <div v-if="batchStates.length" class="batch-results">
          <article v-for="state in batchStates" :key="state.accountId" :class="`status-${state.status.toLowerCase()}`">
            <div class="batch-result-head"><strong>{{ state.accountName }}</strong><span>{{ state.status === 'READY' ? '预检通过' : state.status === 'PUBLISHED' ? '发布成功' : state.status === 'FAILED' ? '失败' : '检测中' }}</span></div>
            <p>{{ state.categoryName || '类目待确认' }} · {{ state.message }}</p>
            <label v-if="state.locations.length"><span>该账号发布地址</span><select v-model="state.locationKey"><option v-for="location in state.locations" :key="location.key" :value="location.key">{{ location.displayName }}（{{ locationSourceLabel(location.source) }}）</option></select></label>
            <label v-if="state.status === 'READY' || state.status === 'PUBLISHED'"><span>该账号商品描述</span><textarea v-model="state.description" rows="4" maxlength="5000"></textarea></label>
            <small v-if="state.itemId">闲鱼商品 ID：{{ state.itemId }}</small>
          </article>
        </div>
      </template>
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

    <section v-if="!batchMode" class="publish-card">
      <div class="section-title">
        <h2>4. 发布地点</h2>
        <button class="location-button" type="button" :disabled="loadingLocations || !selectedAccountId" @click="loadLocations()">{{ loadingLocations ? '加载中…' : '刷新平台地址' }}</button>
      </div>
      <p class="location-help">发布前请选择明确的发货地点。默认、常用和附近地点均由当前闲鱼账号返回。</p>
      <div class="location-actions">
        <button type="button" :disabled="loadingLocations || !selectedAccountId" @click="useBrowserLocation">使用我的当前位置查附近地点</button>
        <span v-if="lookupCoordinates">已按当前位置查询附近地点</span>
      </div>
      <div v-if="locations.length" class="location-list">
        <label v-for="location in locations" :key="location.key" :class="{ selected: selectedLocationKey === location.key }">
          <input v-model="selectedLocationKey" type="radio" :value="location.key">
          <span>
            <strong>{{ location.displayName }}</strong>
            <small>{{ locationSourceLabel(location.source) }} · {{ location.longitude }}, {{ location.latitude }}</small>
          </span>
        </label>
      </div>
      <div v-else-if="!loadingLocations" class="location-empty">当前账号没有返回可用地址，请刷新或使用当前位置查询。</div>
      <label v-if="selectedLocation" class="custom-location">
        <span>自定义地点名称（可选）</span>
        <input v-model="customPoiName" maxlength="100" :placeholder="`默认使用：${selectedLocation.poiName || selectedLocation.displayName}`">
        <small>可以填写小区、商圈或自提点名称；省市区和坐标仍以所选平台地点为准，确保接口可发布。</small>
      </label>
    </section>

    <section class="publish-card confirm-card">
      <h2>5. 最终确认</h2>
      <div class="confirm-location"><span>{{ batchMode ? '本次发布账号' : '本次发布地点' }}</span><strong>{{ batchMode ? `${batchStates.filter(state => state.status === 'READY').length} 个账号已预检通过` : (customPoiName.trim() || selectedLocation?.displayName || '尚未选择') }}</strong></div>
      <label class="ack"><input v-model="acknowledged" type="checkbox">我已逐项核对账号、图片、价格、库存、类目、商品描述和发布地点，并确认商品符合闲鱼规则。</label>
      <label><span>输入“确认发布”</span><input v-model="confirmation" placeholder="确认发布"></label>
      <button type="button" class="publish-button" :disabled="batchMode ? !canBatchPublish : !canPublish" @click="submit">{{ publishing ? (batchMode ? '正在逐账号发布…' : '正在发布…') : (batchMode ? `确认发布到 ${targetAccountIds.length} 个账号` : '确认并真实发布') }}</button>
    </section>
  </main>
</template>

<style scoped>
.publish-page{max-width:1180px;margin:0 auto;padding:4px 0 48px;color:#1d2939}.publish-page__header,.section-title{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}.publish-page__header{margin-bottom:18px}.publish-page__header h1{margin:0;font-size:27px}.publish-page__header p{margin:6px 0 0;color:#667085}.publish-page__header>span{padding:7px 11px;border-radius:999px;background:#eef4ff;color:#3538cd;font-size:12px;font-weight:700}.publish-card{margin-bottom:16px;padding:20px;border:1px solid #e4e7ec;border-radius:14px;background:#fff;box-shadow:0 2px 8px rgba(16,24,40,.04)}.publish-card h2{margin:0 0 16px;font-size:17px}.form-grid{display:grid;grid-template-columns:220px minmax(260px,1fr) auto;gap:14px;align-items:end}.form-grid label,.property-grid label,.confirm-card>label{display:flex;flex-direction:column;gap:6px}.form-grid label>span,.property-grid label>span,.confirm-card label>span{font-size:12px;font-weight:700;color:#475467}.form-grid .wide{min-width:0}.form-grid .full{grid-column:1/-1}.form-grid input,.form-grid select,.form-grid textarea,.property-grid select,.confirm-card>label>input{box-sizing:border-box;width:100%;border:1px solid #d0d5dd;border-radius:8px;background:#fff;padding:10px 11px;color:#1d2939;outline:none}.form-grid textarea{resize:vertical}.probe-button,.publish-button{border:0;border-radius:9px;background:#1570ef;color:#fff;font-weight:700;cursor:pointer}.probe-button{height:40px;padding:0 18px}.probe-button:disabled,.publish-button:disabled{opacity:.5;cursor:not-allowed}.section-title h2{margin:0}.level{padding:5px 9px;border-radius:999px;font-size:12px;font-weight:700}.level--general_form{background:#dcfae6;color:#067647}.level--special_adapter{background:#fef0c7;color:#b54708}.level--blocked{background:#fee4e2;color:#b42318}.blocked-tip{margin:14px 0;padding:11px;border-radius:8px;background:#fffaeb;color:#93370d;font-size:13px}.property-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:16px}.property-grid em{font-style:normal;color:#d92d20}.property-grid small{padding:9px;border:1px dashed #fdb022;border-radius:7px;background:#fffaeb;color:#b54708}.image-list{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:18px}.image-card,.image-add{position:relative;width:112px;height:112px;box-sizing:border-box;border-radius:10px;overflow:hidden}.image-card img{width:100%;height:100%;object-fit:cover}.image-card button{position:absolute;right:5px;top:5px;width:24px;height:24px;border:0;border-radius:50%;background:#0009;color:#fff;cursor:pointer}.image-card small{position:absolute;left:5px;bottom:5px;padding:2px 6px;border-radius:999px;background:#1570ef;color:#fff}.image-add{display:grid;place-content:center;gap:5px;border:1px dashed #98a2b3;text-align:center;color:#667085;cursor:pointer}.image-add input{display:none}.image-add strong{color:#1570ef}.image-add span{font-size:11px}.pricing{grid-template-columns:repeat(5,minmax(120px,1fr))}.confirm-card{display:grid;grid-template-columns:1fr 220px 190px;align-items:end;gap:14px}.confirm-card h2{grid-column:1/-1}.confirm-card .ack{display:flex;flex-direction:row;align-items:center;font-size:13px;color:#475467}.publish-button{height:42px}.publish-button:not(:disabled){background:#d92d20}@media(max-width:900px){.form-grid,.pricing,.property-grid,.confirm-card{grid-template-columns:1fr}.confirm-card h2{grid-column:auto}.publish-page__header{flex-direction:column}}
.location-button,.location-actions button{border:1px solid #b2ccff;border-radius:8px;background:#fff;padding:8px 12px;color:#175cd3;font-weight:700;cursor:pointer}.location-button:disabled,.location-actions button:disabled{opacity:.5;cursor:not-allowed}.location-help{margin:8px 0 12px;color:#667085;font-size:13px}.location-actions{display:flex;align-items:center;gap:12px;margin-bottom:12px}.location-actions span{color:#067647;font-size:12px}.location-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.location-list>label{display:flex;align-items:flex-start;gap:9px;padding:12px;border:1px solid #e4e7ec;border-radius:10px;cursor:pointer}.location-list>label.selected{border-color:#84adff;background:#f5f8ff}.location-list label>span{display:flex;min-width:0;flex-direction:column;gap:4px}.location-list strong{font-size:14px}.location-list small{color:#667085}.location-empty{padding:16px;border:1px dashed #fdb022;border-radius:9px;background:#fffaeb;color:#b54708}.custom-location{display:flex;flex-direction:column;gap:6px;margin-top:14px}.custom-location>span{font-size:12px;font-weight:700;color:#475467}.custom-location input{box-sizing:border-box;width:100%;border:1px solid #d0d5dd;border-radius:8px;padding:10px 11px}.custom-location small{color:#667085}.confirm-location{display:flex;flex-direction:column;gap:5px;padding:10px;border-radius:8px;background:#f2f4f7}.confirm-location span{font-size:12px;color:#667085}.confirm-location strong{font-size:13px}.confirm-card .confirm-location{grid-column:1/-1}@media(max-width:700px){.location-list{grid-template-columns:1fr}.location-actions{align-items:flex-start;flex-direction:column}}
.header-actions{display:flex;align-items:center;gap:9px}.header-actions button,.material-bar button,.ai-buttons button,.batch-tools button{border:1px solid #b2ccff;border-radius:8px;background:#fff;padding:8px 12px;color:#175cd3;font-weight:700;cursor:pointer}.header-actions span{padding:7px 11px;border-radius:999px;background:#eef4ff;color:#3538cd;font-size:12px;font-weight:700}.material-bar{display:flex;align-items:end;gap:10px;margin-bottom:16px;padding:14px 18px;border:1px solid #d1e0ff;border-radius:12px;background:#f5f8ff}.material-bar label{display:flex;min-width:320px;flex-direction:column;gap:5px}.material-bar label span,.ai-panel label span,.batch-results label span{font-size:12px;font-weight:700;color:#475467}.material-bar input,.ai-panel select,.ai-panel textarea,.batch-results select,.batch-results textarea{box-sizing:border-box;width:100%;border:1px solid #d0d5dd;border-radius:8px;padding:9px;background:#fff}.material-bar small{color:#667085}.description-workspace{display:grid;grid-template-columns:minmax(0,1.4fr) minmax(280px,.6fr);gap:14px;align-items:stretch}.description-workspace>label{display:flex;flex-direction:column;gap:6px}.ai-panel{display:flex;flex-direction:column;gap:10px;padding:14px;border:1px solid #d1e9ff;border-radius:10px;background:#f5fbff}.ai-panel>div:first-child{display:flex;flex-direction:column}.ai-panel>div:first-child small{color:#667085}.ai-panel label{display:flex;flex-direction:column;gap:5px}.ai-buttons{display:flex;gap:7px}.ai-buttons button{flex:1}.ai-buttons button:disabled,.batch-tools button:disabled{opacity:.5;cursor:not-allowed}.vision-ok{color:#067647}.vision-fallback{color:#b54708}.batch-card .section-title p{margin:5px 0;color:#667085;font-size:13px}.mode-switch{display:flex;align-items:center;gap:7px;font-weight:700}.account-checks{display:flex;flex-wrap:wrap;gap:9px;margin:14px 0}.account-checks label{padding:8px 11px;border:1px solid #d0d5dd;border-radius:999px;background:#fff}.batch-tools{display:flex;align-items:center;gap:9px}.batch-tools small{color:#667085}.batch-results{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:14px}.batch-results article{padding:13px;border:1px solid #e4e7ec;border-radius:10px}.batch-results article.status-ready,.batch-results article.status-published{border-color:#a6f4c5;background:#f6fef9}.batch-results article.status-failed{border-color:#fecdca;background:#fffbfa}.batch-result-head{display:flex;justify-content:space-between}.batch-result-head span{font-size:12px;font-weight:700}.batch-results p{margin:6px 0 10px;color:#667085;font-size:12px}.batch-results label{display:flex;flex-direction:column;gap:5px;margin-top:8px}.batch-results>article>small{color:#067647}@media(max-width:900px){.description-workspace,.batch-results{grid-template-columns:1fr}.material-bar{align-items:stretch;flex-direction:column}.material-bar label{min-width:0}.header-actions{align-items:flex-start;flex-direction:column}.batch-tools{align-items:stretch;flex-direction:column}}
.level--service_form{background:#e0f2fe;color:#075985}.service-tip{margin:14px 0;padding:11px;border:1px solid #bae6fd;border-radius:8px;background:#f0f9ff;color:#075985;font-size:13px}
</style>

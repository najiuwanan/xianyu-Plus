<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAccountList } from '@/api/account'
import {
  checkPublishCapability,
  getSystemCheckOverview,
  type PublishCapabilityResult,
  type SystemCheckItem,
  type SystemCheckOverview
} from '@/api/system-check'
import type { Account } from '@/types'
import { toast } from '@/utils/toast'

const router = useRouter()
const loading = ref(false)
const overview = ref<SystemCheckOverview | null>(null)
const accounts = ref<Account[]>([])
const selectedAccountId = ref<number | null>(null)
const probeTitle = ref('iPhone 15 Pro 256G 原装二手手机')
const probing = ref(false)
const probeResult = ref<PublishCapabilityResult | null>(null)
const selectedPropertyValues = ref<Record<string, string | string[]>>({})

const checks = computed<SystemCheckItem[]>(() => overview.value?.items || [])
const propertyKey = (property: PublishCapabilityResult['properties'][number]) =>
  property.propertyId || property.propertyName
const unresolvedRequiredCount = computed(() => {
  if (!probeResult.value) return 0
  return probeResult.value.properties.filter((property) => {
    if (!property.required) return false
    const value = selectedPropertyValues.value[propertyKey(property)]
    return Array.isArray(value) ? value.length === 0 : !value
  }).length
})

const initializePropertyValues = (result: PublishCapabilityResult | null) => {
  const values: Record<string, string | string[]> = {}
  for (const property of result?.properties || []) {
    const selected = property.options?.filter((option) => option.selected).map((option) => option.valueId) || []
    values[propertyKey(property)] = property.multiple ? selected : (selected[0] || '')
  }
  selectedPropertyValues.value = values
}
const checkedAt = computed(() => {
  if (!overview.value?.generatedAt) return '尚未检查'
  return String(overview.value.generatedAt).replace('T', ' ').slice(0, 19)
})

const load = async () => {
  loading.value = true
  try {
    const result = await getSystemCheckOverview()
    overview.value = result.data || null
  } catch (error: any) {
    if (!error?.messageShown) toast.error(error?.message || '系统自检失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const loadAccounts = async () => {
  try {
    const result = await getAccountList()
    accounts.value = result.data?.accounts || []
    const firstAccount = accounts.value[0]
    if (!selectedAccountId.value && firstAccount) selectedAccountId.value = firstAccount.id
  } catch (error: any) {
    if (!error?.messageShown) toast.error(error?.message || '账号列表加载失败')
  }
}

const runPublishProbe = async () => {
  if (!selectedAccountId.value) {
    toast.warning('请先选择一个闲鱼账号')
    return
  }
  if (!probeTitle.value.trim()) {
    toast.warning('请输入用于识别类目的商品标题')
    return
  }
  probing.value = true
  probeResult.value = null
  try {
    const result = await checkPublishCapability({
      accountId: selectedAccountId.value,
      title: probeTitle.value.trim()
    })
    probeResult.value = result.data || null
    initializePropertyValues(probeResult.value)
    if (probeResult.value?.status === 'PASS') toast.success('商品发布前置能力检测通过')
    else if (probeResult.value?.status === 'FAIL') toast.error(probeResult.value.summary)
    else toast.warning(probeResult.value?.summary || '检测完成，但仍有项目需要处理')
  } catch (error: any) {
    if (!error?.messageShown) toast.error(error?.message || '发布能力检测失败')
  } finally {
    probing.value = false
  }
}

const goTo = (item: SystemCheckItem) => {
  if (item.path) router.push(item.path)
}

onMounted(() => {
  load()
  loadAccounts()
})
</script>

<template>
  <main class="system-check">
    <section class="system-check__hero">
      <div>
        <h1>系统自检</h1>
        <p>快速查看账号连接、AI、通知渠道、卡券库存和自动化任务是否已经就绪。</p>
      </div>
      <div class="system-check__actions">
        <span>检查时间：{{ checkedAt }}</span>
        <button type="button" class="system-check__refresh" :disabled="loading" @click="load">
          {{ loading ? '检查中…' : '重新检查' }}
        </button>
      </div>
    </section>

    <section v-if="overview" class="system-check__summary">
      <article class="summary-card summary-card--pass"><strong>{{ overview.passCount }}</strong><span>正常</span></article>
      <article class="summary-card summary-card--warn"><strong>{{ overview.warnCount }}</strong><span>需要关注</span></article>
      <article class="summary-card summary-card--fail"><strong>{{ overview.failCount }}</strong><span>异常</span></article>
    </section>

    <section class="publish-probe">
      <header class="publish-probe__header">
        <div>
          <h2>商品发布能力检测</h2>
          <p>只读取类目、动态属性和默认地址，不上传图片、不创建商品。</p>
        </div>
        <span class="publish-probe__safe">只读检测</span>
      </header>
      <div class="publish-probe__form">
        <label>
          <span>测试账号</span>
          <select v-model="selectedAccountId" :disabled="probing">
            <option :value="null" disabled>请选择账号</option>
            <option v-for="account in accounts" :key="account.id" :value="account.id">
              {{ account.accountNote || account.unb || `账号 ${account.id}` }}
            </option>
          </select>
        </label>
        <label class="publish-probe__title">
          <span>商品标题</span>
          <input v-model="probeTitle" :disabled="probing" placeholder="例如：iPhone 15 Pro 256G 原装二手手机" @keyup.enter="runPublishProbe">
        </label>
        <button type="button" class="publish-probe__button" :disabled="probing || !accounts.length" @click="runPublishProbe">
          {{ probing ? '检测中…' : '开始只读检测' }}
        </button>
      </div>

      <article v-if="probeResult" class="probe-result" :class="`probe-result--${probeResult.status.toLowerCase()}`">
        <div class="probe-result__main">
          <strong>{{ probeResult.summary }}</strong>
          <p>{{ probeResult.detail }}</p>
          <div class="probe-result__checks">
            <span :class="{ ready: probeResult.categoryApiReady }">类目识别 {{ probeResult.categoryApiReady ? '通过' : '失败' }}</span>
            <span :class="{ ready: probeResult.dynamicPropertiesReady }">动态属性 {{ probeResult.dynamicPropertiesReady ? '已返回' : '未返回' }}</span>
            <span :class="{ ready: probeResult.locationApiReady }">发布地址 {{ probeResult.locationApiReady ? '通过' : '失败' }}</span>
            <span>真实发布 未执行</span>
          </div>
        </div>
        <div v-if="probeResult.categoryId" class="probe-result__category">
          <small>识别类目</small>
          <strong>{{ probeResult.categoryName || '未返回名称' }}</strong>
          <span>ID：{{ probeResult.categoryId }}</span>
          <em class="support-badge" :class="`support-badge--${probeResult.supportLevel.toLowerCase()}`">
            {{ probeResult.supportLabel }}
          </em>
        </div>
        <div v-if="probeResult.publishWarnings?.length" class="probe-warnings">
          <strong>发布前提醒</strong>
          <ul><li v-for="warning in probeResult.publishWarnings" :key="warning">{{ warning }}</li></ul>
        </div>
        <div v-if="probeResult.properties?.length" class="probe-properties">
          <div v-for="property in probeResult.properties" :key="`${property.propertyId}-${property.propertyName}`" class="probe-property">
            <div class="probe-property__head">
              <strong>{{ property.propertyName }}</strong>
              <span v-if="property.required" class="required-badge">必填</span>
              <span v-if="property.dependent" class="dependent-badge">联动属性</span>
            </div>
            <select
              v-if="property.options?.length"
              v-model="selectedPropertyValues[propertyKey(property)]"
              :multiple="property.multiple"
            >
              <option v-if="!property.multiple" value="">请选择</option>
              <option
                v-for="option in property.options"
                :key="`${propertyKey(property)}-${option.valueId}-${option.valueName}`"
                :value="option.valueId"
                :disabled="option.disabled"
              >{{ option.valueName }}</option>
            </select>
            <input
              v-else-if="property.inputType === 'TEXT'"
              v-model="selectedPropertyValues[propertyKey(property)]"
              type="text"
              :placeholder="`请输入${property.propertyName}`"
            >
            <p v-else class="probe-property__empty">请先选择品牌、产品或上级属性，正式发布页将自动加载此项。</p>
            <small>{{ property.optionCount }} 个选项</small>
          </div>
        </div>
        <div v-if="probeResult.properties?.length" class="schema-readiness">
          <strong>动态表单预览</strong>
          <span v-if="probeResult.supportLevel === 'BLOCKED'">当前商品需要人工核验，自动发布保持关闭。</span>
          <span v-else-if="unresolvedRequiredCount">还有 {{ unresolvedRequiredCount }} 个必填属性未选择。</span>
          <span v-else-if="probeResult.dependentPropertyCount">基础字段可填写；还有 {{ probeResult.dependentPropertyCount }} 个联动属性需要专项加载。</span>
          <span v-else>当前返回的类目字段可以由通用表单承载。</span>
        </div>
      </article>
    </section>

    <section v-if="!loading || checks.length" class="system-check__grid">
      <button v-for="item in checks" :key="item.id" class="check-card" :class="`check-card--${item.status.toLowerCase()}`" @click="goTo(item)">
        <span class="check-card__status">{{ item.status === 'PASS' ? '✓' : item.status === 'WARN' ? '!' : '×' }}</span>
        <span class="check-card__body">
          <span class="check-card__head"><strong>{{ item.title }}</strong><em>{{ item.status === 'PASS' ? '正常' : item.status === 'WARN' ? '需关注' : '异常' }}</em></span>
          <span class="check-card__summary">{{ item.summary }}</span>
          <span class="check-card__detail">{{ item.detail }}</span>
        </span>
        <span class="check-card__arrow">›</span>
      </button>
    </section>
    <div v-else class="system-check__loading">正在检查系统状态…</div>
  </main>
</template>

<style scoped>
.system-check { max-width: 1280px; margin: 0 auto; padding: 28px 30px 48px; color: #1d2939; }
.system-check__hero { display:flex; align-items:flex-start; justify-content:space-between; gap:20px; margin-bottom:22px; }
h1 { margin:0; font-size:28px; letter-spacing:.01em; } p { margin:8px 0 0; color:#667085; font-size:14px; }
.system-check__actions { display:flex; align-items:center; gap:12px; color:#98a2b3; font-size:13px; white-space:nowrap; }
.system-check__refresh { border:1px solid #cbd5e1; border-radius:8px; padding:9px 16px; background:#fff; color:#1565d8; cursor:pointer; font-weight:600; }
.system-check__refresh:disabled { opacity:.6; cursor:wait; }
.system-check__summary { display:grid; grid-template-columns:repeat(3, minmax(0,1fr)); gap:16px; margin-bottom:18px; }
.summary-card { display:flex; align-items:baseline; gap:8px; padding:16px 20px; border:1px solid #eaecf0; border-radius:12px; background:#fff; box-shadow:0 1px 2px rgba(16,24,40,.03); }
.summary-card strong { font-size:28px; }.summary-card span { color:#667085; font-size:14px; }.summary-card--pass strong{color:#12b76a}.summary-card--warn strong{color:#f79009}.summary-card--fail strong{color:#f04438}
.publish-probe { margin-bottom:18px; padding:20px; border:1px solid #d1e9ff; border-radius:14px; background:linear-gradient(135deg,#f5fbff,#fff); box-shadow:0 2px 8px rgba(21,101,216,.05); }
.publish-probe__header { display:flex; align-items:flex-start; justify-content:space-between; gap:16px; margin-bottom:16px; }.publish-probe__header h2{margin:0;font-size:18px}.publish-probe__header p{margin-top:5px}.publish-probe__safe{padding:5px 10px;border-radius:999px;background:#dcfae6;color:#067647;font-size:12px;font-weight:700;white-space:nowrap}
.publish-probe__form{display:grid;grid-template-columns:220px minmax(260px,1fr) auto;align-items:end;gap:12px}.publish-probe__form label{display:flex;flex-direction:column;gap:6px}.publish-probe__form label>span{color:#475467;font-size:12px;font-weight:600}.publish-probe__form select,.publish-probe__form input{height:40px;box-sizing:border-box;border:1px solid #d0d5dd;border-radius:8px;background:#fff;padding:0 12px;color:#1d2939;outline:none}.publish-probe__form select:focus,.publish-probe__form input:focus{border-color:#53b1fd;box-shadow:0 0 0 3px rgba(46,144,250,.12)}.publish-probe__button{height:40px;border:0;border-radius:8px;padding:0 18px;background:#1570ef;color:#fff;font-weight:700;cursor:pointer}.publish-probe__button:disabled{opacity:.55;cursor:not-allowed}
.probe-result{margin-top:16px;padding:16px;border:1px solid #eaecf0;border-radius:10px;background:#fff}.probe-result--pass{border-color:#abefc6}.probe-result--warn{border-color:#fedf89}.probe-result--fail{border-color:#fecdca}.probe-result__main>strong{font-size:16px}.probe-result__main>p{margin:5px 0 12px}.probe-result__checks{display:flex;flex-wrap:wrap;gap:8px}.probe-result__checks span{padding:4px 8px;border-radius:999px;background:#f2f4f7;color:#667085;font-size:12px}.probe-result__checks span.ready{background:#dcfae6;color:#067647}.probe-result__category{display:flex;align-items:baseline;gap:10px;margin-top:14px;padding-top:14px;border-top:1px solid #eaecf0}.probe-result__category small,.probe-result__category span{color:#98a2b3}.probe-properties{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-top:14px}.probe-property{padding:12px;border-radius:8px;background:#f9fafb}.probe-property strong{display:block;font-size:14px}.probe-property span{color:#667085;font-size:12px}.probe-property p{margin:6px 0 0;font-size:12px;line-height:1.5}
.probe-result__category em{font-style:normal}.support-badge{margin-left:auto;padding:4px 9px;border-radius:999px;font-size:12px;font-weight:700}.support-badge--general_form{background:#dcfae6;color:#067647}.support-badge--special_adapter{background:#fef0c7;color:#b54708}.support-badge--blocked{background:#fee4e2;color:#b42318}.probe-warnings{margin-top:12px;padding:12px 14px;border-radius:8px;background:#fffaeb;color:#93370d;font-size:12px}.probe-warnings strong{font-size:13px}.probe-warnings ul{margin:6px 0 0;padding-left:18px;line-height:1.7}.probe-property__head{display:flex;align-items:center;gap:6px;margin-bottom:8px}.probe-property__head strong{margin-right:auto}.probe-property__head .required-badge{padding:2px 6px;border-radius:999px;background:#fee4e2;color:#b42318}.probe-property__head .dependent-badge{padding:2px 6px;border-radius:999px;background:#fef0c7;color:#b54708}.probe-property select,.probe-property input{width:100%;min-height:36px;box-sizing:border-box;border:1px solid #d0d5dd;border-radius:7px;background:#fff;padding:7px 9px;color:#344054}.probe-property select[multiple]{min-height:92px}.probe-property small{display:block;margin-top:7px;color:#98a2b3}.probe-property .probe-property__empty{min-height:36px;margin:0;padding:8px;border:1px dashed #fdb022;border-radius:7px;background:#fffaeb;color:#b54708}.schema-readiness{display:flex;align-items:center;gap:10px;margin-top:14px;padding:12px 14px;border-top:1px solid #eaecf0;color:#475467;font-size:13px}.schema-readiness strong{color:#1d2939}
.system-check__grid { display:grid; grid-template-columns:repeat(2, minmax(0,1fr)); gap:16px; }
.check-card { display:flex; width:100%; gap:14px; align-items:flex-start; text-align:left; padding:18px; border:1px solid #eaecf0; border-radius:12px; background:#fff; cursor:pointer; transition:.18s ease; }
.check-card:hover { transform:translateY(-1px); box-shadow:0 8px 20px rgba(16,24,40,.08); border-color:#b2ddff; }.check-card__status { display:grid; place-items:center; width:30px; height:30px; border-radius:50%; font-size:18px; font-weight:700; flex:0 0 auto; }.check-card--pass .check-card__status{background:#dcfae6;color:#12b76a}.check-card--warn .check-card__status{background:#fef0c7;color:#dc6803}.check-card--fail .check-card__status{background:#fee4e2;color:#d92d20}
.check-card__body{display:flex; flex:1; min-width:0; flex-direction:column; gap:6px}.check-card__head{display:flex; justify-content:space-between; gap:10px}.check-card__head strong{font-size:16px}.check-card__head em{font-style:normal; color:#667085; font-size:12px}.check-card__summary{font-size:14px;color:#344054}.check-card__detail{font-size:12px;line-height:1.5;color:#98a2b3}.check-card__arrow{font-size:26px;line-height:26px;color:#98a2b3}.system-check__loading{padding:70px 0;text-align:center;color:#98a2b3}
@media(max-width:900px){.publish-probe__form{grid-template-columns:1fr}.probe-properties{grid-template-columns:1fr 1fr}.publish-probe__button{width:100%}}
@media(max-width:760px){.system-check{padding:20px 16px}.system-check__hero{flex-direction:column}.system-check__actions{width:100%;justify-content:space-between}.system-check__summary,.system-check__grid{grid-template-columns:1fr}.summary-card{padding:12px 16px}.probe-properties{grid-template-columns:1fr}.probe-result__category{align-items:flex-start;flex-direction:column;gap:3px}}
</style>

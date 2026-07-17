<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAccountList } from '@/api/account'
import {
  queryExceptionCenter,
  retryExceptionCenter,
  type ExceptionCenterRecord,
  type ExceptionCenterSummary,
  type ExceptionType
} from '@/api/exception-center'
import type { Account } from '@/types'
import { showError, showSuccess } from '@/utils'

const loading = ref(false)
const accounts = ref<Account[]>([])
const records = ref<ExceptionCenterRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const selectedAccountId = ref<number | undefined>()
const selectedType = ref<ExceptionType>('ALL')
const retryingKey = ref('')
const summary = ref<ExceptionCenterSummary>({
  total: 0,
  delivery: 0,
  rate: 0,
  redFlower: 0,
  polish: 0,
  reviewRequired: 0
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const loadAccounts = async () => {
  try {
    const response = await getAccountList()
    if (response.code === 0 || response.code === 200) {
      accounts.value = response.data?.accounts || []
    }
  } catch (error) {
    console.error('加载账号列表失败', error)
  }
}

const loadRecords = async () => {
  loading.value = true
  try {
    const response = await queryExceptionCenter({
      accountId: selectedAccountId.value,
      type: selectedType.value,
      page: page.value,
      pageSize
    })
    if (response.code !== 0 && response.code !== 200) {
      throw new Error(response.msg || '加载异常记录失败')
    }
    records.value = response.data?.records || []
    total.value = response.data?.total || 0
    summary.value = response.data?.summary || summary.value
  } catch (error: any) {
    if (!error.messageShown) showError(error.message || '加载异常记录失败')
  } finally {
    loading.value = false
  }
}

const changeType = (type: ExceptionType) => {
  selectedType.value = type
  page.value = 1
  loadRecords()
}

const changeAccount = () => {
  page.value = 1
  loadRecords()
}

const changePage = (nextPage: number) => {
  if (nextPage < 1 || nextPage > totalPages.value || nextPage === page.value) return
  page.value = nextPage
  loadRecords()
}

const recordKey = (record: ExceptionCenterRecord) => `${record.type}:${record.accountId}:${record.recordId}`

const retry = async (record: ExceptionCenterRecord) => {
  if (!record.retryable) return
  const key = recordKey(record)
  retryingKey.value = key
  try {
    const response = await retryExceptionCenter({
      accountId: record.accountId,
      type: record.type,
      recordId: record.recordId
    })
    showSuccess(response.data?.message || '已提交重试')
  } catch (error: any) {
    if (!error.messageShown) showError(error.message || '重试失败')
  } finally {
    retryingKey.value = ''
    await loadRecords()
  }
}

const typeMeta = (type: ExceptionType) => ({
  DELIVERY: { label: '自动发货', className: 'type--delivery' },
  RATE: { label: '自动评价', className: 'type--rate' },
  RED_FLOWER: { label: '小红花', className: 'type--flower' },
  POLISH: { label: '商品擦亮', className: 'type--polish' },
  ALL: { label: '全部异常', className: 'type--all' }
}[type])

const operationName = (record: ExceptionCenterRecord) => {
  if (!record.retryable && record.status === 'REVIEW_REQUIRED') return '需人工核对'
  if (record.type === 'DELIVERY') return '重试发货'
  if (record.type === 'RATE') return '重试评价'
  if (record.type === 'RED_FLOWER') return '重试小红花'
  return '重试擦亮'
}

const formatTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime())
    ? value.replace('T', ' ')
    : date.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

onMounted(async () => {
  await loadAccounts()
  await loadRecords()
})
</script>

<template>
  <section class="exception-center">
    <header class="page-header">
      <div>
        <h1>异常中心</h1>
        <p>集中处理自动发货、自动评价、小红花和商品擦亮失败；重试会沿用原有安全校验。</p>
      </div>
      <button class="refresh-button" :disabled="loading" @click="loadRecords">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </header>

    <div class="summary-grid">
      <button class="summary-card" :class="{ 'summary-card--active': selectedType === 'ALL' }" @click="changeType('ALL')">
        <span>全部异常</span><strong>{{ summary.total }}</strong>
      </button>
      <button class="summary-card summary-card--delivery" :class="{ 'summary-card--active': selectedType === 'DELIVERY' }" @click="changeType('DELIVERY')">
        <span>发货失败</span><strong>{{ summary.delivery }}</strong>
      </button>
      <button class="summary-card summary-card--rate" :class="{ 'summary-card--active': selectedType === 'RATE' }" @click="changeType('RATE')">
        <span>评价失败</span><strong>{{ summary.rate }}</strong>
      </button>
      <button class="summary-card summary-card--flower" :class="{ 'summary-card--active': selectedType === 'RED_FLOWER' }" @click="changeType('RED_FLOWER')">
        <span>小红花失败</span><strong>{{ summary.redFlower }}</strong>
      </button>
      <button class="summary-card summary-card--polish" :class="{ 'summary-card--active': selectedType === 'POLISH' }" @click="changeType('POLISH')">
        <span>擦亮失败</span><strong>{{ summary.polish }}</strong>
      </button>
    </div>

    <div class="filter-bar">
      <label>
        <span>账号</span>
        <select v-model="selectedAccountId" @change="changeAccount">
          <option :value="undefined">全部账号</option>
          <option v-for="account in accounts" :key="account.id" :value="account.id">
            {{ account.accountNote || account.unb || `账号 ${account.id}` }}
          </option>
        </select>
      </label>
      <span v-if="summary.reviewRequired" class="review-hint">{{ summary.reviewRequired }} 条发货记录需要人工核对，不能直接重发。</span>
    </div>

    <div class="table-card">
      <div v-if="loading" class="state">正在加载异常记录…</div>
      <div v-else-if="records.length === 0" class="state">暂无待处理异常</div>
      <div v-else class="table-scroll">
        <table>
          <thead>
            <tr><th>类型</th><th>对象</th><th>失败原因</th><th>发生时间</th><th>处理</th></tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="recordKey(record)">
              <td><span class="type-tag" :class="typeMeta(record.type).className">{{ typeMeta(record.type).label }}</span></td>
              <td class="target-cell">
                <strong>{{ record.goodsTitle || record.orderId || '未命名记录' }}</strong>
                <small>{{ record.accountName || `账号 ${record.accountId}` }}<template v-if="record.buyerUserName"> · {{ record.buyerUserName }}</template></small>
                <small v-if="record.orderId">订单号：{{ record.orderId }}</small>
                <small v-else-if="record.xyGoodsId">商品 ID：{{ record.xyGoodsId }}</small>
              </td>
              <td class="reason" :title="record.reason">{{ record.reason || '未返回失败原因' }}</td>
              <td class="time">{{ formatTime(record.occurredAt) }}</td>
              <td>
                <button class="retry-button" :class="{ 'retry-button--disabled': !record.retryable }"
                  :disabled="!record.retryable || retryingKey === recordKey(record)" @click="retry(record)">
                  {{ retryingKey === recordKey(record) ? '处理中…' : operationName(record) }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <footer v-if="totalPages > 1" class="pagination">
      <button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</span>
      <button :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
    </footer>
  </section>
</template>

<style scoped>
.exception-center { max-width: 1440px; margin: 0 auto; color: #1d2939; }
.page-header, .filter-bar { display:flex; justify-content:space-between; align-items:flex-start; gap:16px; }
.page-header { margin-bottom:22px; }
h1 { margin:0; font-size:26px; letter-spacing:-.5px; }
.page-header p { margin:8px 0 0; color:#667085; font-size:14px; }
.refresh-button, .retry-button, .pagination button { border:1px solid #d0d5dd; border-radius:7px; padding:8px 13px; background:#fff; color:#344054; cursor:pointer; font-size:13px; font-weight:600; }
.refresh-button:hover, .pagination button:not(:disabled):hover { background:#f9fafb; border-color:#98a2b3; }
button:disabled { cursor:not-allowed; opacity:.56; }
.summary-grid { display:grid; grid-template-columns:repeat(5, minmax(0, 1fr)); gap:12px; margin-bottom:16px; }
.summary-card { text-align:left; min-height:89px; border:1px solid #eaecf0; border-radius:10px; background:#fff; padding:16px; cursor:pointer; color:#475467; transition:.18s ease; }
.summary-card:hover, .summary-card--active { border-color:#f2b900; box-shadow:0 4px 12px rgba(255,193,7,.15); }
.summary-card span { display:block; font-size:13px; }
.summary-card strong { display:block; margin-top:8px; font-size:27px; color:#1d2939; }
.summary-card--delivery strong { color:#b42318; }
.summary-card--rate strong { color:#b54708; }
.summary-card--flower strong { color:#a15c07; }
.summary-card--polish strong { color:#6941c6; }
.filter-bar { align-items:center; margin-bottom:14px; }
.filter-bar label { display:flex; align-items:center; gap:8px; color:#475467; font-size:14px; }
.filter-bar select { min-width:175px; padding:8px 10px; border:1px solid #d0d5dd; border-radius:7px; color:#344054; background:#fff; }
.review-hint { color:#b54708; background:#fffaeb; border:1px solid #fedf89; border-radius:7px; padding:8px 10px; font-size:12px; }
.table-card { min-height:260px; overflow:hidden; border:1px solid #eaecf0; border-radius:10px; background:#fff; }
.table-scroll { overflow-x:auto; }
table { width:100%; min-width:950px; border-collapse:collapse; }
th { padding:12px 16px; border-bottom:1px solid #eaecf0; background:#f9fafb; color:#475467; text-align:left; font-size:12px; }
td { padding:14px 16px; border-bottom:1px solid #eaecf0; vertical-align:top; font-size:13px; }
tbody tr:last-child td { border-bottom:0; }
.type-tag { display:inline-flex; border-radius:999px; padding:4px 9px; font-size:12px; font-weight:600; white-space:nowrap; }
.type--delivery { color:#b42318; background:#fef3f2; }
.type--rate { color:#b54708; background:#fffaeb; }
.type--flower { color:#8c5a00; background:#fff7d6; }
.type--polish { color:#6941c6; background:#f4f3ff; }
.target-cell { min-width:220px; max-width:310px; }
.target-cell strong, .target-cell small { display:block; }
.target-cell strong { color:#1d2939; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.target-cell small { margin-top:5px; color:#667085; font-size:12px; }
.reason { max-width:340px; color:#b42318; line-height:19px; word-break:break-word; }
.time { color:#667085; white-space:nowrap; font-size:12px; }
.retry-button { border-color:#fdb022; background:#fffaeb; color:#9a6700; white-space:nowrap; }
.retry-button:hover:not(:disabled) { background:#fff1c2; }
.retry-button--disabled { border-color:#e4e7ec; color:#98a2b3; background:#f9fafb; }
.state { min-height:260px; display:flex; align-items:center; justify-content:center; color:#98a2b3; font-size:14px; }
.pagination { display:flex; justify-content:center; align-items:center; gap:14px; padding:18px 0; color:#667085; font-size:13px; }
@media (max-width: 980px) { .summary-grid { grid-template-columns:repeat(3, minmax(0, 1fr)); } }
@media (max-width: 700px) { .page-header, .filter-bar { flex-direction:column; align-items:stretch; } .refresh-button { align-self:flex-start; } .summary-grid { grid-template-columns:repeat(2, minmax(0, 1fr)); } .review-hint { line-height:18px; } }
</style>

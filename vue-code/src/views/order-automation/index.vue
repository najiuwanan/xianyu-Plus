<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAccountList } from '@/api/account'
import {
  queryOrderAutomation,
  retryOrderAutomation,
  type AutomationAction,
  type AutomationFilterStatus,
  type OrderAutomationRecord,
  type OrderAutomationSummary
} from '@/api/order-automation'
import { showError, showSuccess } from '@/utils'
import type { Account } from '@/types'

const loading = ref(false)
const retryingKey = ref('')
const accounts = ref<Account[]>([])
const records = ref<OrderAutomationRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const selectedAccountId = ref<number | undefined>()
const selectedStatus = ref<AutomationFilterStatus>('ALL')
const summary = ref<OrderAutomationSummary>({
  total: 0,
  completed: 0,
  failed: 0,
  pending: 0,
  rateSuccess: 0,
  redFlowerSuccess: 0
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
    const response = await queryOrderAutomation({
      accountId: selectedAccountId.value,
      status: selectedStatus.value,
      page: page.value,
      pageSize
    })
    if (response.code !== 0 && response.code !== 200) {
      throw new Error(response.msg || '加载执行记录失败')
    }
    records.value = response.data?.records || []
    total.value = response.data?.total || 0
    summary.value = response.data?.summary || summary.value
  } catch (error: any) {
    if (!error.messageShown) {
      showError(error.message || '加载执行记录失败')
    }
  } finally {
    loading.value = false
  }
}

const changeFilter = (status: AutomationFilterStatus) => {
  selectedStatus.value = status
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

const retry = async (record: OrderAutomationRecord, action: AutomationAction) => {
  const key = `${record.accountId}:${record.orderId}:${action}`
  retryingKey.value = key
  try {
    const response = await retryOrderAutomation({
      accountId: record.accountId,
      orderId: record.orderId,
      action
    })
    showSuccess(response.data?.message || '重试成功')
  } catch (error: any) {
    if (!error.messageShown) {
      showError(error.message || '重试失败')
    }
  } finally {
    retryingKey.value = ''
    loadRecords()
  }
}

const canCheckRate = (enabled: number, status: number) => enabled === 1 && status !== 1

const isRetrying = (record: OrderAutomationRecord, action: AutomationAction) =>
  retryingKey.value === `${record.accountId}:${record.orderId}:${action}`

const statusText = (enabled: number, status: number) => {
  if (enabled !== 1) return '未开启'
  if (status === 1) return '成功'
  if (status === 2) return '失败'
  return '待执行'
}

const statusClass = (enabled: number, status: number) => {
  if (enabled !== 1) return 'status--disabled'
  if (status === 1) return 'status--success'
  if (status === 2) return 'status--failed'
  return 'status--pending'
}

const redFlowerStatusText = (record: OrderAutomationRecord) => {
  if (record.redFlowerEnabled !== 1) return '未开启'
  if (record.confirmState !== 1) return '等待确认发货'
  return statusText(record.redFlowerEnabled, record.redFlowerStatus)
}

const redFlowerStatusClass = (record: OrderAutomationRecord) => {
  if (record.redFlowerEnabled !== 1) return 'status--disabled'
  if (record.confirmState !== 1) return 'status--waiting'
  return statusClass(record.redFlowerEnabled, record.redFlowerStatus)
}

const canRetryRedFlower = (record: OrderAutomationRecord) =>
  record.redFlowerEnabled === 1 && record.confirmState === 1 && record.redFlowerStatus === 2

const formatTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ')
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

onMounted(async () => {
  await loadAccounts()
  await loadRecords()
})
</script>

<template>
  <section class="automation-center">
    <header class="page-header">
      <div>
        <h1>自动化执行中心</h1>
        <p>查看自动评价和小红花的执行状态；失败记录可立即补执行。</p>
      </div>
      <button class="refresh-button" :disabled="loading" @click="loadRecords">
        {{ loading ? '刷新中…' : '刷新' }}
      </button>
    </header>

    <div class="summary-grid">
      <button class="summary-card" :class="{ 'summary-card--active': selectedStatus === 'ALL' }" @click="changeFilter('ALL')">
        <span>纳入管理订单</span>
        <strong>{{ summary.total }}</strong>
      </button>
      <button class="summary-card summary-card--success" :class="{ 'summary-card--active': selectedStatus === 'SUCCESS' }" @click="changeFilter('SUCCESS')">
        <span>全部自动化完成</span>
        <strong>{{ summary.completed }}</strong>
      </button>
      <button class="summary-card summary-card--warning" :class="{ 'summary-card--active': selectedStatus === 'PENDING' }" @click="changeFilter('PENDING')">
        <span>等待执行</span>
        <strong>{{ summary.pending }}</strong>
      </button>
      <button class="summary-card summary-card--danger" :class="{ 'summary-card--active': selectedStatus === 'FAILED' }" @click="changeFilter('FAILED')">
        <span>需要处理</span>
        <strong>{{ summary.failed }}</strong>
      </button>
    </div>

    <div class="filter-bar">
      <label>
        <span>账号</span>
        <select v-model="selectedAccountId" @change="changeAccount">
          <option :value="undefined">全部账号</option>
          <option v-for="account in accounts" :key="account.id" :value="account.id">
            {{ account.accountNote || `账号 ${account.id}` }}
          </option>
        </select>
      </label>
      <div class="filter-statuses">
        <button v-for="item in [
          { value: 'ALL', label: '全部' },
          { value: 'SUCCESS', label: '已完成' },
          { value: 'PENDING', label: '待执行' },
          { value: 'FAILED', label: '失败' }
        ]" :key="item.value" class="filter-status"
          :class="{ 'filter-status--active': selectedStatus === item.value }"
          @click="changeFilter(item.value as AutomationFilterStatus)">
          {{ item.label }}
        </button>
      </div>
    </div>

    <div class="hint">
      小红花只会在确认发货成功后处理，失败后会在下次重试时间自动再试；自动评价会先确认订单已进入闲鱼待评价列表，随后立即提交。待执行订单也可手动检查一次。
    </div>

    <div class="table-card">
      <div v-if="loading" class="loading-state">正在加载执行记录…</div>
      <div v-else-if="records.length === 0" class="empty-state">
        暂无符合条件的自动化订单记录
      </div>
      <div v-else class="table-scroll">
        <table>
          <thead>
            <tr>
              <th>订单</th>
              <th>自动评价</th>
              <th>小红花</th>
              <th>下次自动重试</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in records" :key="`${record.accountId}-${record.orderId}`">
              <td class="order-cell">
                <div class="order-title">{{ record.goodsTitle || '未同步商品标题' }}</div>
                <div class="order-meta">{{ record.accountName || `账号 ${record.accountId}` }} · {{ record.buyerUserName || '未知买家' }}</div>
                <div class="order-id">订单号：{{ record.orderId }}</div>
                <div class="order-time">发货记录：{{ formatTime(record.orderCreateTime) }}</div>
              </td>
              <td>
                <span class="status" :class="statusClass(record.rateEnabled, record.rateStatus)">
                  {{ statusText(record.rateEnabled, record.rateStatus) }}
                </span>
                <p v-if="record.rateTime" class="cell-time">{{ formatTime(record.rateTime) }}</p>
                <p v-if="record.rateError" class="error-text" :title="record.rateError">{{ record.rateError }}</p>
              </td>
              <td>
                <span class="status" :class="redFlowerStatusClass(record)">
                  {{ redFlowerStatusText(record) }}
                </span>
                <p v-if="record.redFlowerTime" class="cell-time">{{ formatTime(record.redFlowerTime) }}</p>
                <p v-if="record.redFlowerError" class="error-text" :title="record.redFlowerError">{{ record.redFlowerError }}</p>
                <p v-if="record.redFlowerAttemptCount > 0" class="cell-time">已尝试 {{ record.redFlowerAttemptCount }} 次</p>
              </td>
              <td>
                <span v-if="record.redFlowerNextRetryTime" class="retry-time">{{ formatTime(record.redFlowerNextRetryTime) }}</span>
                <span v-else class="muted">-</span>
              </td>
              <td>
                <div class="actions">
                  <button v-if="canCheckRate(record.rateEnabled, record.rateStatus)" class="retry-button"
                    :disabled="isRetrying(record, record.rateStatus === 2 ? 'RATE' : 'RATE_CHECK')"
                    @click="retry(record, record.rateStatus === 2 ? 'RATE' : 'RATE_CHECK')">
                    {{ isRetrying(record, record.rateStatus === 2 ? 'RATE' : 'RATE_CHECK')
                      ? record.rateStatus === 2 ? '重试中…' : '检查中…'
                      : record.rateStatus === 2 ? '重试评价' : '检查并评价' }}
                  </button>
                  <button v-if="canRetryRedFlower(record)" class="retry-button"
                    :disabled="isRetrying(record, 'RED_FLOWER')" @click="retry(record, 'RED_FLOWER')">
                    {{ isRetrying(record, 'RED_FLOWER') ? '重试中…' : '重试小红花' }}
                  </button>
                  <span v-if="!canCheckRate(record.rateEnabled, record.rateStatus) && !canRetryRedFlower(record)" class="muted">无需操作</span>
                </div>
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
.automation-center { color: #1d2939; max-width: 1440px; margin: 0 auto; }
.page-header { display:flex; justify-content:space-between; gap:20px; align-items:flex-start; margin-bottom:24px; }
.page-header h1 { margin:0; font-size:26px; letter-spacing:-.5px; }
.page-header p { margin:8px 0 0; color:#667085; font-size:14px; }
.refresh-button, .retry-button, .pagination button { border:1px solid #d0d5dd; background:#fff; border-radius:7px; padding:8px 13px; color:#344054; cursor:pointer; font-size:13px; font-weight:600; }
.refresh-button:hover, .pagination button:not(:disabled):hover { border-color:#98a2b3; background:#f9fafb; }
button:disabled { cursor:not-allowed; opacity:.55; }
.summary-grid { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); gap:14px; margin-bottom:18px; }
.summary-card { text-align:left; min-height:96px; border:1px solid #eaecf0; border-radius:10px; background:#fff; padding:18px; cursor:pointer; color:#475467; transition:.18s ease; }
.summary-card:hover, .summary-card--active { border-color:#f2b900; box-shadow:0 4px 12px rgba(255,193,7,.16); }
.summary-card span { display:block; font-size:13px; }
.summary-card strong { display:block; margin-top:8px; font-size:28px; color:#1d2939; }
.summary-card--success strong { color:#15803d; }
.summary-card--warning strong { color:#b54708; }
.summary-card--danger strong { color:#b42318; }
.filter-bar { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:14px; }
.filter-bar label { display:flex; align-items:center; gap:8px; color:#475467; font-size:14px; }
.filter-bar select { min-width:170px; padding:8px 10px; border:1px solid #d0d5dd; border-radius:7px; background:#fff; color:#344054; }
.filter-statuses { display:flex; gap:7px; flex-wrap:wrap; }
.filter-status { border:0; background:#f2f4f7; color:#475467; padding:7px 12px; border-radius:999px; cursor:pointer; font-size:13px; }
.filter-status--active { background:#fff4cc; color:#8a5a00; font-weight:600; }
.hint { color:#667085; font-size:13px; background:#fffaeb; border:1px solid #fedf89; border-radius:8px; padding:10px 12px; margin-bottom:14px; }
.table-card { background:#fff; border:1px solid #eaecf0; border-radius:10px; overflow:hidden; min-height:250px; }
.table-scroll { overflow-x:auto; }
table { width:100%; min-width:1000px; border-collapse:collapse; }
th { background:#f9fafb; color:#475467; font-size:12px; font-weight:600; text-align:left; padding:12px 16px; border-bottom:1px solid #eaecf0; }
td { padding:14px 16px; vertical-align:top; border-bottom:1px solid #eaecf0; font-size:13px; }
tbody tr:last-child td { border-bottom:0; }
.order-cell { width:31%; }
.order-title { color:#1d2939; font-weight:600; margin-bottom:5px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; max-width:320px; }
.order-meta, .order-id, .order-time, .cell-time { color:#667085; font-size:12px; margin-top:4px; }
.status { display:inline-flex; align-items:center; border-radius:999px; font-size:12px; font-weight:600; padding:4px 9px; }
.status--success { color:#067647; background:#ecfdf3; }
.status--failed { color:#b42318; background:#fef3f2; }
.status--pending { color:#b54708; background:#fffaeb; }
.status--waiting { color:#475467; background:#f2f4f7; }
.status--disabled { color:#667085; background:#f2f4f7; }
.error-text { color:#b42318; font-size:12px; line-height:18px; max-width:220px; margin:7px 0 0; overflow:hidden; text-overflow:ellipsis; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
.retry-time { color:#b54708; font-size:12px; line-height:18px; }
.actions { display:flex; flex-direction:column; align-items:flex-start; gap:8px; min-width:102px; }
.retry-button { border-color:#fdb022; color:#9a6700; background:#fffaeb; padding:6px 10px; }
.retry-button:hover:not(:disabled) { background:#fff1c2; }
.muted { color:#98a2b3; font-size:12px; }
.loading-state, .empty-state { min-height:250px; display:flex; align-items:center; justify-content:center; color:#98a2b3; font-size:14px; }
.pagination { display:flex; align-items:center; justify-content:center; gap:14px; padding:18px 0; color:#667085; font-size:13px; }
@media (max-width: 860px) {
  .page-header, .filter-bar { align-items:stretch; flex-direction:column; }
  .refresh-button { align-self:flex-start; }
  .summary-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
  .filter-bar label { justify-content:space-between; }
  .filter-bar select { width:70%; }
}
@media (max-width: 480px) {
  .summary-grid { gap:8px; }
  .summary-card { min-height:78px; padding:13px; }
  .summary-card strong { font-size:23px; }
  .pagination { gap:8px; font-size:12px; }
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAccountList } from '@/api/account'
import {
  batchRedFlowerOrders,
  batchRateOrders,
  queryOrderAutomation,
  retryOrderAutomation,
  type AutomationAction,
  type AutomationFilterStatus,
  type OrderAutomationRecord,
  type OrderAutomationSummary
} from '@/api/order-automation'
import {
  queryExceptionCenter,
  retryExceptionCenter,
  type ExceptionCenterRecord,
  type ExceptionCenterSummary,
  type ExceptionType
} from '@/api/exception-center'
import { showConfirm, showError, showSuccess } from '@/utils'
import type { Account } from '@/types'

const loading = ref(false)
const retryingKey = ref('')
const exceptionRetryingKey = ref('')
const batchAction = ref<'CHECK' | 'RATE' | 'RED_FLOWER' | ''>('')
const accounts = ref<Account[]>([])
const records = ref<OrderAutomationRecord[]>([])
const exceptionRecords = ref<ExceptionCenterRecord[]>([])
const total = ref(0)
const exceptionTotal = ref(0)
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

const exceptionSummary = ref<ExceptionCenterSummary>({
  total: 0,
  delivery: 0,
  rate: 0,
  redFlower: 0,
  polish: 0,
  reviewRequired: 0
})

const displayedTotal = computed(() => selectedStatus.value === 'FAILED' ? exceptionTotal.value : total.value)
const totalPages = computed(() => Math.max(1, Math.ceil(displayedTotal.value / pageSize)))

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
    const [automationResponse, exceptionResponse] = await Promise.all([
      queryOrderAutomation({
        accountId: selectedAccountId.value,
        status: selectedStatus.value,
        page: page.value,
        pageSize
      }),
      queryExceptionCenter({
        accountId: selectedAccountId.value,
        type: 'ALL',
        page: page.value,
        pageSize
      })
    ])
    if (automationResponse.code !== 0 && automationResponse.code !== 200) {
      throw new Error(automationResponse.msg || '加载自动化记录失败')
    }
    if (exceptionResponse.code !== 0 && exceptionResponse.code !== 200) {
      throw new Error(exceptionResponse.msg || '加载待处理异常失败')
    }
    records.value = automationResponse.data?.records || []
    total.value = automationResponse.data?.total || 0
    summary.value = automationResponse.data?.summary || summary.value
    exceptionRecords.value = exceptionResponse.data?.records || []
    exceptionTotal.value = exceptionResponse.data?.total || 0
    exceptionSummary.value = exceptionResponse.data?.summary || exceptionSummary.value
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

const exceptionRecordKey = (record: ExceptionCenterRecord) => `${record.type}:${record.accountId}:${record.recordId}`

const retryException = async (record: ExceptionCenterRecord) => {
  if (!record.retryable) return
  const key = exceptionRecordKey(record)
  exceptionRetryingKey.value = key
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
    exceptionRetryingKey.value = ''
    loadRecords()
  }
}

const batchRate = async (action: 'CHECK' | 'RATE') => {
  const scope = selectedAccountId.value ? '当前账号' : '全部已启用账号'
  const message = action === 'RATE'
    ? `将核验${scope}的待评价订单；列表未匹配时会由平台评价接口再次确认，只处理平台允许评价的订单。确认继续吗？`
    : `将核验${scope}近 30 天订单是否进入闲鱼待评价列表。未匹配的订单只会标记为“待评价状态待核验”，不会直接判定买家未确认。确认继续吗？`
  try {
    await showConfirm(message, action === 'RATE' ? '一键评价' : '一键检查')
  } catch {
    return
  }
  batchAction.value = action
  try {
    const response = await batchRateOrders({ accountId: selectedAccountId.value, action })
    showSuccess(response.data?.message || (action === 'RATE' ? '一键评价完成' : '一键检查完成'))
  } catch (error: any) {
    if (!error.messageShown) {
      showError(error.message || (action === 'RATE' ? '一键评价失败' : '一键检查失败'))
    }
  } finally {
    batchAction.value = ''
    loadRecords()
  }
}

const batchRedFlower = async () => {
  const scope = selectedAccountId.value ? '当前账号' : '全部已启用账号'
  try {
    await showConfirm(
      `将对${scope}近 30 天内已确认发货、尚未成功请求过小红花的订单逐笔发起请求。确认继续吗？`,
      '一键求小红花'
    )
  } catch {
    return
  }
  batchAction.value = 'RED_FLOWER'
  try {
    const response = await batchRedFlowerOrders({ accountId: selectedAccountId.value })
    showSuccess(response.data?.message || '一键求小红花完成')
  } catch (error: any) {
    if (!error.messageShown) {
      showError(error.message || '一键求小红花失败')
    }
  } finally {
    batchAction.value = ''
    loadRecords()
  }
}

const canCheckRate = (enabled: number, status: number) => enabled === 1 && status !== 1 && status !== 3

const isRetrying = (record: OrderAutomationRecord, action: AutomationAction) =>
  retryingKey.value === `${record.accountId}:${record.orderId}:${action}`

const statusText = (enabled: number, status: number) => {
  if (enabled !== 1) return '未开启'
  if (status === 1) return '成功'
  if (status === 2) return '失败'
  if (status === 3) return '无需评价'
  if (status === 4) return '待评价待核验'
  return '待执行'
}

const rateStatusText = (record: OrderAutomationRecord) => {
  if (record.rateEnabled !== 1) return '未开启'
  if (record.rateStatus === 4 && /订单暂未完成|未完成交易|交易未完成/.test(record.rateError || '')) {
    return '等待买家确认'
  }
  return statusText(record.rateEnabled, record.rateStatus)
}

const statusClass = (enabled: number, status: number) => {
  if (enabled !== 1) return 'status--disabled'
  if (status === 1) return 'status--success'
  if (status === 2) return 'status--failed'
  if (status === 3) return 'status--skipped'
  if (status === 4) return 'status--waiting'
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

const exceptionTypeMeta = (type: ExceptionType) => ({
  DELIVERY: { label: '自动发货', className: 'failure-type--delivery' },
  RATE: { label: '自动评价', className: 'failure-type--rate' },
  RED_FLOWER: { label: '小红花', className: 'failure-type--flower' },
  POLISH: { label: '商品擦亮', className: 'failure-type--polish' },
  ALL: { label: '待处理异常', className: 'failure-type--all' }
}[type])

const exceptionOperationName = (record: ExceptionCenterRecord) => {
  if (!record.retryable && record.status === 'REVIEW_REQUIRED') return '需人工核对'
  if (record.type === 'DELIVERY') return '重试发货'
  if (record.type === 'RATE') return '重试评价'
  if (record.type === 'RED_FLOWER') return '重试小红花'
  return '重试擦亮'
}

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
        <p>与订单管理共享近 30 天的非退款订单；查看任务进度，并在“待处理异常”中统一重试发货、评价、小红花和擦亮。</p>
      </div>
      <div class="page-actions">
        <button class="refresh-button" :disabled="loading || !!batchAction" @click="loadRecords">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
        <button class="check-button" :disabled="loading || !!batchAction" @click="batchRate('CHECK')">
          {{ batchAction === 'CHECK' ? '检查中…' : '一键检查' }}
        </button>
        <button class="batch-rate-button" :disabled="loading || !!batchAction" @click="batchRate('RATE')">
          {{ batchAction === 'RATE' ? '评价中…' : '一键评价' }}
        </button>
        <button class="batch-flower-button" :disabled="loading || !!batchAction" @click="batchRedFlower">
          {{ batchAction === 'RED_FLOWER' ? '请求中…' : '一键求小红花' }}
        </button>
      </div>
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
        <strong>{{ exceptionSummary.total }}</strong>
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
          { value: 'FAILED', label: '待处理异常' }
        ]" :key="item.value" class="filter-status"
          :class="{ 'filter-status--active': selectedStatus === item.value }"
          @click="changeFilter(item.value as AutomationFilterStatus)">
          {{ item.label }}
        </button>
      </div>
    </div>

    <div class="hint">
      在订单管理点击“同步订单”后，刷新本页即可看到同一批近 30 天订单。小红花只会在确认发货成功后处理；自动评价先查询待评价列表，再由平台评价接口最终核验。失败任务统一在“待处理异常”筛选中处理；等待买家确认、已完成或无需处理的订单不会计入异常。
    </div>

    <div class="table-card">
      <div v-if="loading" class="loading-state">正在加载执行记录…</div>
      <div v-else-if="selectedStatus === 'FAILED' && exceptionRecords.length === 0" class="empty-state">
        暂无待处理异常
      </div>
      <div v-else-if="selectedStatus !== 'FAILED' && records.length === 0" class="empty-state">
        暂无符合条件的自动化订单记录
      </div>
      <div v-else class="table-scroll">
        <table v-if="selectedStatus === 'FAILED'" class="failure-table">
          <thead>
            <tr>
              <th>类型</th>
              <th>对象</th>
              <th>原因</th>
              <th>发生时间</th>
              <th>处理</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in exceptionRecords" :key="exceptionRecordKey(record)">
              <td>
                <span class="failure-type" :class="exceptionTypeMeta(record.type).className">
                  {{ exceptionTypeMeta(record.type).label }}
                </span>
              </td>
              <td class="failure-target">
                <strong>{{ record.goodsTitle || record.orderId || '未命名记录' }}</strong>
                <small>{{ record.accountName || `账号 ${record.accountId}` }}<template v-if="record.buyerUserName"> · {{ record.buyerUserName }}</template></small>
                <small v-if="record.orderId">订单号：{{ record.orderId }}</small>
                <small v-else-if="record.xyGoodsId">商品 ID：{{ record.xyGoodsId }}</small>
              </td>
              <td class="failure-reason" :title="record.reason">{{ record.reason || '未返回失败原因' }}</td>
              <td class="failure-time">{{ formatTime(record.occurredAt) }}</td>
              <td>
                <button class="retry-button" :class="{ 'retry-button--disabled': !record.retryable }"
                  :disabled="!record.retryable || exceptionRetryingKey === exceptionRecordKey(record)"
                  @click="retryException(record)">
                  {{ exceptionRetryingKey === exceptionRecordKey(record) ? '处理中…' : exceptionOperationName(record) }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
        <table v-else>
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
                <div class="order-time">下单时间：{{ formatTime(record.orderCreateTime) }}</div>
                <div v-if="record.tradeStatusText" class="order-time">交易状态：{{ record.tradeStatusText }}</div>
              </td>
              <td>
                <span class="status" :class="statusClass(record.rateEnabled, record.rateStatus)">
                  {{ rateStatusText(record) }}
                </span>
                <p v-if="record.rateTime" class="cell-time">{{ formatTime(record.rateTime) }}</p>
                <p v-if="record.rateError && record.rateStatus === 3" class="cell-time" :title="record.rateError">{{ record.rateError }}</p>
                <p v-else-if="record.rateError && record.rateStatus === 4" class="status-note" :title="record.rateError">{{ record.rateError }}</p>
                <p v-else-if="record.rateError" class="error-text" :title="record.rateError">{{ record.rateError }}</p>
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
      <span>第 {{ page }} / {{ totalPages }} 页，共 {{ displayedTotal }} 条</span>
      <button :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
    </footer>
  </section>
</template>

<style scoped>
.automation-center { color: #1d2939; max-width: 1440px; margin: 0 auto; }
.page-header { display:flex; justify-content:space-between; gap:20px; align-items:flex-start; margin-bottom:24px; }
.page-header h1 { margin:0; font-size:26px; letter-spacing:-.5px; }
.page-header p { margin:8px 0 0; color:#667085; font-size:14px; }
.page-actions { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:9px; }
.refresh-button, .check-button, .batch-rate-button, .batch-flower-button, .retry-button, .pagination button { border:1px solid #d0d5dd; background:#fff; border-radius:7px; padding:8px 13px; color:#344054; cursor:pointer; font-size:13px; font-weight:600; }
.refresh-button:hover, .pagination button:not(:disabled):hover { border-color:#98a2b3; background:#f9fafb; }
.check-button { border-color:#f2b900; background:#fffaf0; color:#8a5a00; }
.check-button:hover:not(:disabled) { background:#fff1c2; }
.batch-rate-button { border-color:#e6ac00; background:#f2b900; color:#1f3556; }
.batch-rate-button:hover:not(:disabled) { background:#e6ac00; }
.batch-flower-button { border-color:#12b76a; background:#ecfdf3; color:#067647; }
.batch-flower-button:hover:not(:disabled) { background:#d1fadf; }
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
.status--skipped { color:#475467; background:#f2f4f7; }
.status--pending { color:#b54708; background:#fffaeb; }
.status--waiting { color:#475467; background:#f2f4f7; }
.status--disabled { color:#667085; background:#f2f4f7; }
.status-note { color:#667085; font-size:12px; line-height:18px; max-width:240px; margin:7px 0 0; overflow:hidden; text-overflow:ellipsis; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
.error-text { color:#b42318; font-size:12px; line-height:18px; max-width:220px; margin:7px 0 0; overflow:hidden; text-overflow:ellipsis; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }
.retry-time { color:#b54708; font-size:12px; line-height:18px; }
.actions { display:flex; flex-direction:column; align-items:flex-start; gap:8px; min-width:102px; }
.retry-button { border-color:#fdb022; color:#9a6700; background:#fffaeb; padding:6px 10px; }
.retry-button:hover:not(:disabled) { background:#fff1c2; }
.retry-button--disabled { border-color:#e4e7ec; color:#98a2b3; background:#f9fafb; }
.failure-type { display:inline-flex; border-radius:999px; padding:4px 9px; font-size:12px; font-weight:600; white-space:nowrap; }
.failure-type--delivery { color:#b42318; background:#fef3f2; }
.failure-type--rate { color:#b54708; background:#fffaeb; }
.failure-type--flower { color:#8c5a00; background:#fff7d6; }
.failure-type--polish { color:#6941c6; background:#f4f3ff; }
.failure-target { min-width:220px; max-width:310px; }
.failure-target strong, .failure-target small { display:block; }
.failure-target strong { color:#1d2939; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.failure-target small { margin-top:5px; color:#667085; font-size:12px; }
.failure-reason { max-width:340px; color:#b42318; line-height:19px; word-break:break-word; }
.failure-time { color:#667085; white-space:nowrap; font-size:12px; }
.muted { color:#98a2b3; font-size:12px; }
.loading-state, .empty-state { min-height:250px; display:flex; align-items:center; justify-content:center; color:#98a2b3; font-size:14px; }
.pagination { display:flex; align-items:center; justify-content:center; gap:14px; padding:18px 0; color:#667085; font-size:13px; }
@media (max-width: 860px) {
  .page-header, .filter-bar { align-items:stretch; flex-direction:column; }
  .page-actions { justify-content:flex-start; }
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

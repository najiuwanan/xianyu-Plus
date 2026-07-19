<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAccountList } from '@/api/account'
import {
  manualDelivery,
  queryDeliveryRecordList,
  triggerRuleDelivery,
  type DeliveryRecordVO
} from '@/api/order'
import type { Account } from '@/types'
import { formatTime, showError, showSuccess } from '@/utils'
import IconTruck from '@/components/icons/IconTruck.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconPackage from '@/components/icons/IconPackage.vue'
import IconText from '@/components/icons/IconText.vue'

type FilterKey = 'ALL' | 'PENDING' | 'FAILED' | 'DELIVERED'
type DeliveryState = 'PENDING' | 'FAILED' | 'DELIVERED' | 'SKIPPED' | 'REVIEW'

const router = useRouter()
const accounts = ref<Account[]>([])
const selectedAccountId = ref<number>()
const records = ref<DeliveryRecordVO[]>([])
const total = ref(0)
const loading = ref(false)
const actionId = ref<number>()
const pageNum = ref(1)
const pageSize = ref(20)
const activeFilter = ref<FilterKey>('ALL')
const manualTarget = ref<DeliveryRecordVO>()
const manualContent = ref('')
const manualVisible = ref(false)

const filters: Array<{ key: FilterKey; label: string }> = [
  { key: 'ALL', label: '全部记录' },
  { key: 'PENDING', label: '待处理' },
  { key: 'FAILED', label: '发货失败' },
  { key: 'DELIVERED', label: '已发货' }
]

function stateOf(record: DeliveryRecordVO): DeliveryState {
  const status = String(record.deliveryStatus || '').toUpperCase()
  if (status === 'FAILED') return 'FAILED'
  if (status === 'REVIEW_REQUIRED') return 'REVIEW'
  if (status === 'COMPLETED' || record.state === 1) return 'DELIVERED'
  if (status === 'SKIPPED') return 'SKIPPED'
  return 'PENDING'
}

function stateText(record: DeliveryRecordVO) {
  const map: Record<DeliveryState, string> = {
    PENDING: '待发货',
    FAILED: '发货失败',
    DELIVERED: '已发货',
    SKIPPED: '未启用自动发货',
    REVIEW: '等待人工核对'
  }
  return map[stateOf(record)]
}

function stateClass(record: DeliveryRecordVO) {
  return `status--${stateOf(record).toLowerCase()}`
}

const filteredRecords = computed(() => {
  if (activeFilter.value === 'ALL') return records.value
  if (activeFilter.value === 'PENDING') {
    return records.value.filter(item => ['PENDING', 'REVIEW'].includes(stateOf(item)))
  }
  return records.value.filter(item => stateOf(item) === activeFilter.value)
})

const counts = computed(() => ({
  pending: records.value.filter(item => ['PENDING', 'REVIEW'].includes(stateOf(item))).length,
  failed: records.value.filter(item => stateOf(item) === 'FAILED').length,
  delivered: records.value.filter(item => stateOf(item) === 'DELIVERED').length
}))

async function loadAccounts() {
  const response = await getAccountList()
  accounts.value = response.data?.accounts || []
  const firstAccount = accounts.value[0]
  if (!selectedAccountId.value && firstAccount) {
    selectedAccountId.value = firstAccount.id
  }
}

async function loadRecords() {
  if (!selectedAccountId.value) {
    records.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const response = await queryDeliveryRecordList({
      xianyuAccountId: selectedAccountId.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    records.value = response.data?.records || []
    total.value = response.data?.total || 0
  } catch {
    showError('获取发货记录失败')
  } finally {
    loading.value = false
  }
}

async function refresh() {
  await loadAccounts()
  await loadRecords()
}

function setAccount() {
  pageNum.value = 1
  activeFilter.value = 'ALL'
  loadRecords()
}

function openManualDelivery(record: DeliveryRecordVO) {
  manualTarget.value = record
  manualContent.value = record.content || ''
  manualVisible.value = true
}

function closeManualDelivery() {
  manualVisible.value = false
  manualTarget.value = undefined
  manualContent.value = ''
}

async function submitManualDelivery() {
  const target = manualTarget.value
  if (!target?.xianyuAccountId || !target.orderId || !manualContent.value.trim()) {
    showError('请输入发货内容后再提交')
    return
  }
  actionId.value = target.id
  try {
    await manualDelivery({
      xianyuAccountId: target.xianyuAccountId,
      orderId: target.orderId,
      content: manualContent.value.trim()
    })
    showSuccess('手动发货已提交')
    closeManualDelivery()
    await loadRecords()
  } catch {
    showError('手动发货失败，请稍后重试')
  } finally {
    actionId.value = undefined
  }
}

async function retryRuleDelivery(record: DeliveryRecordVO) {
  if (!record.xianyuAccountId || !record.xyGoodsId || !record.orderId) {
    showError('订单信息不完整，无法按规则补发')
    return
  }
  actionId.value = record.id
  try {
    await triggerRuleDelivery({
      xianyuAccountId: record.xianyuAccountId,
      xyGoodsId: record.xyGoodsId,
      orderId: record.orderId
    })
    showSuccess('已按商品配置重新发货')
    await loadRecords()
  } catch {
    showError('按规则补发失败，请检查商品配置或库存')
  } finally {
    actionId.value = undefined
  }
}

function canRetry(record: DeliveryRecordVO) {
  return stateOf(record) === 'FAILED' && !!record.xianyuAccountId && !!record.xyGoodsId && !!record.orderId
}

function canManualDeliver(record: DeliveryRecordVO) {
  return ['PENDING', 'FAILED', 'REVIEW'].includes(stateOf(record)) && !!record.xianyuAccountId && !!record.orderId
}

function previousPage() {
  if (pageNum.value > 1) {
    pageNum.value -= 1
    loadRecords()
  }
}

function nextPage() {
  if (pageNum.value * pageSize.value < total.value) {
    pageNum.value += 1
    loadRecords()
  }
}

onMounted(refresh)
</script>

<template>
  <section class="delivery-center page-shell">
    <header class="page-header">
      <div>
        <div class="page-title-row">
          <span class="title-icon"><IconTruck /></span>
          <h1>发货中心</h1>
        </div>
        <p>集中处理待发货、失败补发和手动发货；商品规则统一在商品列表维护。</p>
      </div>
      <div class="header-actions">
        <button class="btn btn--secondary" @click="refresh"><IconRefresh />刷新记录</button>
        <button class="btn btn--primary" @click="router.push('/goods')"><IconPackage />商品列表</button>
      </div>
    </header>

    <section class="toolbar">
      <label>
        <span>选择账号</span>
        <select v-model="selectedAccountId" @change="setAccount">
          <option v-for="account in accounts" :key="account.id" :value="account.id">
            {{ account.accountNote || `账号_${account.unb}` }}
          </option>
        </select>
      </label>
      <p>发货方式、卡券关联、固定内容和自动确认发货，请到 <button class="text-link" @click="router.push('/goods')">商品列表</button> 设置。</p>
    </section>

    <section class="summary-grid">
      <article class="summary-card summary-card--all">
        <span>当前页记录</span><strong>{{ records.length }}</strong><small>共 {{ total }} 条发货记录</small>
      </article>
      <article class="summary-card summary-card--pending">
        <span>待处理</span><strong>{{ counts.pending }}</strong><small>等待发货或需要人工核对</small>
      </article>
      <article class="summary-card summary-card--failed">
        <span>发货失败</span><strong>{{ counts.failed }}</strong><small>可按规则重试或改为手动发货</small>
      </article>
      <article class="summary-card summary-card--success">
        <span>已发货</span><strong>{{ counts.delivered }}</strong><small>当前页已完成交付</small>
      </article>
    </section>

    <section class="records-card">
      <div class="records-toolbar">
        <div class="filter-tabs">
          <button
            v-for="item in filters"
            :key="item.key"
            class="filter-tab"
            :class="{ active: activeFilter === item.key }"
            @click="activeFilter = item.key"
          >{{ item.label }}</button>
        </div>
        <span class="hint">规则补发会优先使用商品已关联的卡券或固定内容</span>
      </div>

      <div class="table-wrap" :class="{ loading }">
        <table>
          <thead>
            <tr>
              <th>订单</th>
              <th>商品与买家</th>
              <th>发货内容</th>
              <th>交付状态</th>
              <th>失败原因</th>
              <th class="action-col">处理</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="record in filteredRecords" :key="record.id">
              <td>
                <strong>{{ record.orderId || '-' }}</strong>
                <small>{{ formatTime(record.orderCreateTime || record.createTime) }}</small>
              </td>
              <td>
                <strong>{{ record.goodsTitle || '未命名商品' }}</strong>
                <small>买家：{{ record.buyerUserName || '-' }}</small>
              </td>
              <td class="content-cell">{{ record.content || '等待按商品规则发货' }}</td>
              <td><span class="status-pill" :class="stateClass(record)">{{ stateText(record) }}</span></td>
              <td class="reason-cell">{{ record.failReason || record.lastErrorMessage || '-' }}</td>
              <td class="actions-cell">
                <button v-if="canRetry(record)" class="mini-btn mini-btn--primary" :disabled="actionId === record.id" @click="retryRuleDelivery(record)">
                  {{ actionId === record.id ? '处理中…' : '按规则补发' }}
                </button>
                <button v-if="canManualDeliver(record)" class="mini-btn" :disabled="actionId === record.id" @click="openManualDelivery(record)">手动发货</button>
                <span v-if="!canRetry(record) && !canManualDeliver(record)" class="muted">无需处理</span>
              </td>
            </tr>
            <tr v-if="!loading && filteredRecords.length === 0">
              <td colspan="6">
                <div class="empty-state"><IconText /><span>暂无符合条件的发货记录</span></div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="pagination">
        <span>第 {{ pageNum }} 页，共 {{ total }} 条</span>
        <div>
          <button class="page-btn" :disabled="pageNum <= 1" @click="previousPage">上一页</button>
          <button class="page-btn" :disabled="pageNum * pageSize >= total" @click="nextPage">下一页</button>
        </div>
      </footer>
    </section>

    <div v-if="manualVisible" class="modal-mask" @click.self="closeManualDelivery">
      <section class="manual-dialog">
        <header><div><h2>手动发货</h2><p>{{ manualTarget?.goodsTitle || '订单发货' }} · {{ manualTarget?.orderId }}</p></div><button class="close-btn" @click="closeManualDelivery">×</button></header>
        <label class="content-label">发货内容<textarea v-model="manualContent" maxlength="1000" placeholder="输入将发送给买家的内容，如卡密、网盘链接或取货说明" /></label>
        <p class="dialog-tip">手动发货会直接发送此内容；若需要自动匹配卡券，请关闭窗口后使用“按规则补发”。</p>
        <footer><button class="btn btn--secondary" @click="closeManualDelivery">取消</button><button class="btn btn--primary" :disabled="actionId === manualTarget?.id" @click="submitManualDelivery">确认发货</button></footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.page-shell{padding:28px 32px 34px;color:#1d2b4b}.page-header{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;margin-bottom:20px}.page-title-row{display:flex;align-items:center;gap:12px}.page-title-row h1{margin:0;font-size:26px;letter-spacing:-.5px}.page-header p{margin:7px 0 0;color:#71809a;font-size:14px}.title-icon{display:grid;place-items:center;width:38px;height:38px;border-radius:12px;background:#edf5ff;color:#2787f5}.title-icon svg{width:20px}.header-actions{display:flex;gap:10px}.btn{height:38px;padding:0 15px;border-radius:11px;border:1px solid #d8e3f2;background:#fff;color:#2775d8;font-weight:600;cursor:pointer;display:inline-flex;align-items:center;gap:7px}.btn svg{width:17px}.btn--primary{color:#fff;background:linear-gradient(135deg,#258cf7,#3378e8);border-color:transparent;box-shadow:0 8px 18px rgba(38,126,236,.2)}.btn:disabled,.page-btn:disabled{opacity:.5;cursor:not-allowed}.toolbar{padding:16px 18px;border:1px solid #e6edf6;border-radius:14px;background:#fff;display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:16px}.toolbar label{display:grid;gap:7px;font-size:13px;color:#61718c}.toolbar select{height:36px;min-width:190px;padding:0 12px;border:1px solid #dbe5f1;border-radius:9px;color:#1d2b4b;background:#fff}.toolbar p{margin:0;color:#7c8aa2;font-size:13px}.text-link{border:0;background:transparent;color:#297df0;font-weight:600;cursor:pointer;padding:0}.summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:16px}.summary-card{background:#fff;border:1px solid #e8edf4;border-radius:14px;padding:18px}.summary-card span,.summary-card small{display:block;color:#71809a;font-size:13px}.summary-card strong{display:block;margin:7px 0 5px;font-size:28px}.summary-card--pending strong{color:#bd7100}.summary-card--failed strong{color:#e04444}.summary-card--success strong{color:#20a25e}.records-card{background:#fff;border:1px solid #e7edf5;border-radius:16px;overflow:hidden}.records-toolbar{padding:14px 18px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #eef2f7}.filter-tabs{display:flex;gap:6px}.filter-tab{border:0;padding:7px 12px;border-radius:8px;background:transparent;color:#73819a;cursor:pointer}.filter-tab.active{background:#edf5ff;color:#277be6;font-weight:700}.hint{font-size:12px;color:#8b98ad}.table-wrap{overflow:auto;min-height:270px}.table-wrap.loading{opacity:.65}table{width:100%;border-collapse:collapse;min-width:940px}th{text-align:left;padding:12px 16px;background:#fafcff;color:#65748d;font-size:12px;font-weight:600}td{padding:14px 16px;border-top:1px solid #eef2f7;vertical-align:middle;font-size:13px}td strong,td small{display:block}td strong{font-weight:600;color:#22314c}td small{margin-top:4px;color:#8290a5;font-size:12px}.content-cell{max-width:205px;color:#55647a;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.reason-cell{max-width:245px;color:#d65555;line-height:1.5}.status-pill{display:inline-flex;border-radius:99px;padding:4px 9px;font-size:12px;font-weight:600}.status--pending{background:#fff5df;color:#b76a00}.status--review{background:#fff4e9;color:#bd720d}.status--failed{background:#fff0f0;color:#dc4e4e}.status--delivered{background:#e9faef;color:#239b55}.status--skipped{background:#f1f4f8;color:#71809a}.action-col{text-align:right}.actions-cell{white-space:nowrap;text-align:right}.mini-btn{height:30px;padding:0 10px;border:1px solid #d4e2f3;border-radius:8px;background:#fff;color:#3978c7;cursor:pointer;margin-left:6px}.mini-btn--primary{background:#edf5ff;color:#2377df;border-color:#cfe2fc}.muted{color:#98a5b7;font-size:12px}.empty-state{height:210px;display:grid;place-content:center;justify-items:center;gap:10px;color:#94a0b3}.empty-state svg{width:35px;height:35px;opacity:.45}.pagination{padding:13px 18px;border-top:1px solid #eef2f7;display:flex;justify-content:space-between;align-items:center;color:#8491a5;font-size:13px}.page-btn{height:31px;margin-left:7px;padding:0 11px;border:1px solid #dbe6f3;border-radius:8px;background:#fff;color:#3977c4;cursor:pointer}.modal-mask{position:fixed;inset:0;z-index:100;background:rgba(20,32,52,.42);display:grid;place-items:center;padding:20px}.manual-dialog{width:min(560px,100%);background:#fff;border-radius:16px;box-shadow:0 18px 56px rgba(13,35,73,.24);overflow:hidden}.manual-dialog header{padding:18px 20px;border-bottom:1px solid #edf1f6;display:flex;justify-content:space-between}.manual-dialog h2{font-size:18px;margin:0}.manual-dialog header p{margin:5px 0 0;color:#8290a5;font-size:12px}.close-btn{border:0;background:transparent;font-size:27px;color:#8490a2;cursor:pointer}.content-label{display:grid;gap:9px;padding:18px 20px 8px;font-size:13px;color:#586780;font-weight:600}.content-label textarea{min-height:145px;resize:vertical;padding:11px;border:1px solid #dce5f0;border-radius:10px;font:inherit;color:#24334e}.dialog-tip{margin:0 20px;color:#8995a8;font-size:12px;line-height:1.6}.manual-dialog footer{padding:16px 20px;display:flex;justify-content:flex-end;gap:9px}.manual-dialog footer .btn{height:36px}@media(max-width:1050px){.summary-grid{grid-template-columns:repeat(2,1fr)}.page-header,.toolbar{align-items:flex-start;flex-direction:column}.header-actions{width:100%}.header-actions .btn{flex:1;justify-content:center}}@media(max-width:640px){.page-shell{padding:18px 14px}.summary-grid{grid-template-columns:1fr}.records-toolbar{align-items:flex-start;gap:10px;flex-direction:column}.hint{display:none}}
</style>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, inject, defineComponent, h } from 'vue'
import { useRoute } from 'vue-router'
import { useOrderManager } from './useOrderManager'
import './orders.css'
import '@/styles/header-selectors.css'

import IconClipboard from '@/components/icons/IconClipboard.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconFilter from '@/components/icons/IconFilter.vue'
import IconChevronDown from '@/components/icons/IconChevronDown.vue'
import IconChevronLeft from '@/components/icons/IconChevronLeft.vue'
import IconChevronRight from '@/components/icons/IconChevronRight.vue'
import OrderTable from './components/OrderTable.vue'

const route = useRoute()
const {
  loading,
  syncingOrders,
  orderList,
  total,
  accounts,
  queryParams,
  totalPages,
  loadAccounts,
  loadOrders,
  handleSyncOrders,
  handleAccountChange,
  handleReset,
  handlePageChange,
  handleSizeChange,
  copySId,
  handleConfirmShipment,
  handleRuleDelivery
} = useOrderManager()

const showFilterSheet = ref(false)
const isMobile = ref(false)
const filterKeyword = ref('')
const showConfirmDialog = ref(false)
const confirmTargetOrder = ref<any>(null)
const showRuleDeliveryDialog = ref(false)
const ruleDeliveryTargetOrder = ref<any>(null)

const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
}

const applyRouteFilters = () => {
  const accountId = Number(route.query.accountId)
  if (Number.isInteger(accountId) && accounts.value.some(account => account.id === accountId)) {
    queryParams.xianyuAccountId = accountId
  }
  const goodsId = typeof route.query.goodsId === 'string' ? route.query.goodsId : ''
  if (goodsId) queryParams.xyGoodsId = goodsId
}

const setHeaderContent = inject<(content: any) => void>('setHeaderContent')
const openFilterSheet = () => {
  filterKeyword.value = queryParams.keyword || ''
  showFilterSheet.value = true
}

const HeaderSelectors = defineComponent({
  setup() {
    return () => h('div', { class: 'header-selectors' }, [
      h('div', { class: 'header-select-wrap' }, [
        h('select', {
          class: 'header-select',
          onChange: (event: Event) => {
            const value = (event.target as HTMLSelectElement).value
            queryParams.xianyuAccountId = value ? Number.parseInt(value, 10) : undefined
            handleAccountChange()
          }
        }, [
          h('option', { value: '', disabled: true, selected: !queryParams.xianyuAccountId }, '账号'),
          ...accounts.value.map(account => h('option', {
            value: String(account.id),
            selected: queryParams.xianyuAccountId === account.id
          }, account.accountNote || account.unb))
        ]),
        h(IconChevronDown, { class: 'header-select-icon' })
      ]),
      h('button', {
        class: ['header-refresh-btn', { 'header-refresh-btn--loading': loading.value }],
        disabled: loading.value,
        onClick: loadOrders
      }, [h(IconRefresh, { class: 'header-refresh-icon' })]),
      h('button', { class: 'header-filter-btn', onClick: openFilterSheet }, [
        h(IconFilter, { class: 'header-filter-icon' })
      ])
    ])
  }
})

onMounted(async () => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  if (setHeaderContent) setHeaderContent(HeaderSelectors)
  await loadAccounts()
  applyRouteFilters()
  if (setHeaderContent) setHeaderContent(HeaderSelectors)
  void loadOrders()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
})

const applyFilter = () => {
  queryParams.keyword = filterKeyword.value || undefined
  queryParams.pageNum = 1
  showFilterSheet.value = false
  void loadOrders()
}

const resetFilter = () => {
  filterKeyword.value = ''
  handleReset()
  showFilterSheet.value = false
}

const getPageButtons = () => {
  const buttons: number[] = []
  const maxVisible = 5
  let start = Math.max(1, queryParams.pageNum! - Math.floor(maxVisible / 2))
  const end = Math.min(totalPages.value, start + maxVisible - 1)
  start = Math.max(1, end - maxVisible + 1)
  for (let page = start; page <= end; page++) buttons.push(page)
  return buttons
}

const changePageSize = (event: Event) => {
  const size = Number((event.target as HTMLSelectElement).value)
  if ([20, 50, 100].includes(size)) handleSizeChange(size)
}

const openConfirmDialog = (order: any) => {
  confirmTargetOrder.value = order
  showConfirmDialog.value = true
}

const executeConfirmShipment = async () => {
  if (confirmTargetOrder.value) await handleConfirmShipment(confirmTargetOrder.value)
  showConfirmDialog.value = false
  confirmTargetOrder.value = null
}

const openRuleDeliveryDialog = (order: any) => {
  ruleDeliveryTargetOrder.value = order
  showRuleDeliveryDialog.value = true
}

const executeRuleDelivery = async () => {
  if (!ruleDeliveryTargetOrder.value) return
  const success = await handleRuleDelivery(ruleDeliveryTargetOrder.value)
  if (success) {
    showRuleDeliveryDialog.value = false
    ruleDeliveryTargetOrder.value = null
  }
}
</script>

<template>
  <div class="orders">
    <div class="orders__header">
      <div class="orders__title-row">
        <div class="orders__title-icon"><IconClipboard /></div>
        <h1 class="orders__title">订单管理</h1>
      </div>
      <div class="orders__actions">
        <div class="orders__select-wrap">
          <select v-model="queryParams.xianyuAccountId" class="orders__select" @change="handleAccountChange">
            <option :value="undefined" disabled>选择账号</option>
            <option v-for="account in accounts" :key="account.id" :value="account.id">
              {{ account.accountNote || account.unb || `账号${account.id}` }}
            </option>
          </select>
          <span class="orders__select-icon"><IconChevronDown /></span>
        </div>
        <template v-if="!isMobile">
          <div class="orders__input-wrap">
            <input v-model="queryParams.keyword" class="orders__input" placeholder="商品名称/规格/买家/发货内容" @keyup.enter="loadOrders">
          </div>
          <button class="btn btn--primary" @click="loadOrders"><IconSearch /><span>查询</span></button>
          <button class="btn btn--ghost" @click="handleReset">重置</button>
          <span v-if="total > 0" class="orders__count">共 {{ total }} 条</span>
        </template>
        <button class="btn btn--secondary" :class="{ 'btn--loading': syncingOrders }" :disabled="syncingOrders || loading" @click="handleSyncOrders">
          <IconRefresh /><span class="mobile-hidden">同步订单</span>
        </button>
        <button class="btn btn--secondary" :class="{ 'btn--loading': loading }" :disabled="loading" @click="loadOrders">
          <IconRefresh /><span class="mobile-hidden">刷新</span>
        </button>
        <button v-if="isMobile" class="btn btn--secondary" @click="openFilterSheet"><IconFilter /><span>筛选</span></button>
      </div>
    </div>

    <div class="orders__body orders__body--full">
      <div class="orders__content">
        <div class="orders__table-wrap">
          <OrderTable
            :order-list="orderList"
            :loading="loading"
            @copy-sid="copySId"
            @confirm-shipment="openConfirmDialog"
            @rule-delivery="openRuleDeliveryDialog"
            @refresh="loadOrders"
          />
        </div>
        <div v-if="total > 0" class="orders__pagination">
          <div class="orders__pagination-summary">
            <span>共 {{ total }} 条订单</span>
            <span>第 {{ queryParams.pageNum }} / {{ totalPages || 1 }} 页</span>
          </div>
          <div class="orders__pagination-controls">
            <label class="orders__page-size">
              <span>每页</span>
              <select :value="queryParams.pageSize" @change="changePageSize">
                <option :value="20">20 条</option>
                <option :value="50">50 条</option>
                <option :value="100">100 条</option>
              </select>
            </label>
            <div v-if="totalPages > 1" class="orders__page-buttons">
              <button class="orders__page-btn" :class="{ 'orders__page-btn--disabled': queryParams.pageNum! <= 1 }" @click="handlePageChange(queryParams.pageNum! - 1)"><IconChevronLeft /></button>
              <template v-for="page in getPageButtons()" :key="page">
                <button class="orders__page-btn" :class="{ 'orders__page-btn--active': page === queryParams.pageNum }" @click="handlePageChange(page)">{{ page }}</button>
              </template>
              <button class="orders__page-btn" :class="{ 'orders__page-btn--disabled': queryParams.pageNum! >= totalPages }" @click="handlePageChange(queryParams.pageNum! + 1)"><IconChevronRight /></button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <Transition name="overlay-fade">
      <div v-if="showFilterSheet" class="orders__filter-overlay" @click="showFilterSheet = false">
        <div class="orders__filter-sheet" :class="{ 'orders__filter-sheet--open': showFilterSheet }" @click.stop>
          <div class="orders__filter-sheet-handle"></div>
          <h3 class="orders__filter-sheet-title">筛选条件</h3>
          <div class="orders__filter-group">
            <label class="orders__filter-label">关键词</label>
            <input v-model="filterKeyword" class="orders__filter-input" placeholder="商品名称/规格/买家/发货内容">
          </div>
          <div class="orders__filter-actions">
            <button class="btn btn--secondary" @click="resetFilter">重置</button>
            <button class="btn btn--primary" @click="applyFilter">查询</button>
          </div>
        </div>
      </div>
    </Transition>

    <Transition name="overlay-fade">
      <div v-if="showConfirmDialog" class="orders__dialog-overlay" @click.self="showConfirmDialog = false">
        <div class="orders__dialog">
          <div class="orders__dialog-header"><h3 class="orders__dialog-title">确认发货</h3></div>
          <div class="orders__dialog-body"><p class="orders__dialog-text">确认订单「{{ confirmTargetOrder?.orderId }}」已发货吗？</p></div>
          <div class="orders__dialog-footer">
            <button class="orders__dialog-btn orders__dialog-btn--cancel" @click="showConfirmDialog = false">取消</button>
            <button class="orders__dialog-btn orders__dialog-btn--confirm" @click="executeConfirmShipment">确认</button>
          </div>
        </div>
      </div>
    </Transition>

    <Transition name="overlay-fade">
      <div v-if="showRuleDeliveryDialog" class="orders__dialog-overlay" @click.self="showRuleDeliveryDialog = false">
        <div class="orders__dialog orders__dialog--rule-delivery">
          <div class="orders__dialog-header"><h3 class="orders__dialog-title">按规则发货</h3></div>
          <div class="orders__dialog-body">
            <p class="orders__dialog-text">将为订单「{{ ruleDeliveryTargetOrder?.orderId }}」重新按当前商品规则发货。</p>
            <p class="orders__dialog-hint">会复用卡券管理绑定、固定内容、规格匹配和发货模板；库存仍不足时不会发送，也不会错误扣减库存。</p>
          </div>
          <div class="orders__dialog-footer">
            <button class="orders__dialog-btn orders__dialog-btn--cancel" :disabled="ruleDeliveryTargetOrder?.manualDelivering" @click="showRuleDeliveryDialog = false">取消</button>
            <button class="orders__dialog-btn orders__dialog-btn--rule" :disabled="ruleDeliveryTargetOrder?.manualDelivering" @click="executeRuleDelivery">
              {{ ruleDeliveryTargetOrder?.manualDelivering ? '发货中…' : '确认按规则发货' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.overlay-fade-enter-active, .overlay-fade-leave-active { transition: opacity .2s ease; }
.overlay-fade-enter-from, .overlay-fade-leave-to { opacity: 0; }
</style>

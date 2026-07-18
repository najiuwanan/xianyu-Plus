<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { formatTime } from '@/utils'
import { showSuccess, showError, showInfo } from '@/utils'
import { getOrderDetail, getOrderTimeline, type OrderTimelineEvent } from '@/api/order'
import { getOrderAutomationAvailableActions, retryOrderAutomation, type AutomationAction, type OrderAutomationAvailableActions } from '@/api/order-automation'
import type { DeliveryRecordItem } from '../useOrderManager'

import IconEmpty from '@/components/icons/IconEmpty.vue'
import IconCopy from '@/components/icons/IconCopy.vue'
import IconTruck from '@/components/icons/IconTruck.vue'
import IconUser from '@/components/icons/IconUser.vue'
import IconClock from '@/components/icons/IconClock.vue'
import IconShoppingBag from '@/components/icons/IconShoppingBag.vue'
import IconEye from '@/components/icons/IconImage.vue'

interface Props {
  orderList: DeliveryRecordItem[]
  loading?: boolean
}

interface Emits {
  (e: 'copySid', sid: string): void
  (e: 'confirmShipment', item: DeliveryRecordItem): void
  (e: 'ruleDelivery', item: DeliveryRecordItem): void
  (e: 'viewDetail', item: DeliveryRecordItem): void
  (e: 'refresh'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()
const router = useRouter()

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailData = ref<any>(null)
const detailSkuText = ref('')
const detailFromServer = ref(false)
const detailTimeline = ref<OrderTimelineEvent[]>([])
const detailTimelineLoading = ref(false)
const runningCompensationKey = ref<string | null>(null)
const openedActionMenuKey = ref<string | null>(null)

const orderKey = (order: DeliveryRecordItem) => `${order.xianyuAccountId || ''}:${order.orderId || ''}`

const loadTimeline = async (order: DeliveryRecordItem) => {
  if (!order.orderId || !order.xianyuAccountId) return
  detailTimelineLoading.value = true
  try {
    const res = await getOrderTimeline({ xianyuAccountId: order.xianyuAccountId, orderId: order.orderId })
    if (res.code === 200 || res.code === 0) {
      detailTimeline.value = res.data?.events || []
    }
  } catch {
    detailTimeline.value = []
  } finally {
    detailTimelineLoading.value = false
  }
}

const openAutomationCenter = async () => {
  detailVisible.value = false
  await router.push('/order-automation')
}

const handleViewDetail = async (order: DeliveryRecordItem, fromServer: boolean = false) => {
  if (!order.orderId || !order.xianyuAccountId) return
  detailLoading.value = true
  detailVisible.value = true
  detailData.value = null
  detailSkuText.value = ''
  detailFromServer.value = fromServer
  detailTimeline.value = []
  detailTimelineLoading.value = false
  try {
    const res = await getOrderDetail({ xianyuAccountId: order.xianyuAccountId, orderId: order.orderId, fromServer })
    if (res.code === 200 || res.code === 0) {
      const parsed = JSON.parse(res.data || '{}')
      detailData.value = parsed

      if (fromServer) {
        const module = parsed.module || {}
        const orderInfoVO = module.orderInfoVO || {}
        const itemInfo = orderInfoVO.itemInfo || {}
        const merchantItemVO = module.merchantItemVO || {}
        const itemInfoLines: any[] = merchantItemVO.itemInfoLines || []
        const specLine = itemInfoLines.find((l: any) => l.key === '规格')
        const skuInfo = itemInfo.skuInfo || ''
        if (skuInfo || specLine) {
          detailSkuText.value = specLine?.value || skuInfo.split(':').pop() || ''
        } else {
          detailSkuText.value = ''
        }
      } else {
        detailSkuText.value = parsed.skuName || ''
        void loadTimeline(order)
      }
    } else {
      showError(res.msg || '获取订单详情失败')
      detailVisible.value = false
    }
  } catch (e: any) {
    showError('获取订单详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

let clickTimer: ReturnType<typeof setTimeout> | null = null
let lastClickTime = 0
const handleClickDetail = (order: DeliveryRecordItem) => {
  const now = Date.now()
  if (clickTimer) {
    clearTimeout(clickTimer)
    clickTimer = null
  }
  if (now - lastClickTime < 300) {
    handleViewDetail(order, true)
  } else {
    clickTimer = setTimeout(() => {
      handleViewDetail(order, false)
      clickTimer = null
    }, 300)
  }
  lastClickTime = now
}

const isMobile = ref(false)
const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  window.addEventListener('click', closeActionMenu)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
  window.removeEventListener('click', closeActionMenu)
})

const closeActionMenu = () => {
  openedActionMenuKey.value = null
}

const toggleActionMenu = (event: MouseEvent, order: DeliveryRecordItem) => {
  event.stopPropagation()
  const key = orderKey(order)
  openedActionMenuKey.value = openedActionMenuKey.value === key ? null : key
}

const runMenuAction = (event: MouseEvent, action: () => void) => {
  event.stopPropagation()
  closeActionMenu()
  action()
}

const getStatusColor = (state: number) => {
  return state === 1 ? '#30D158' : '#FF453A'
}

const getStatusBg = (state: number) => {
  return state === 1 ? 'rgba(48,209,88,.2)' : 'rgba(255,69,58,.15)'
}

const getStatusText = (state: number) => {
  return state === 1 ? '成功' : '失败'
}

const getDeliveryText = (state: number, deliveryStatus?: string) => {
  if (deliveryStatus === 'SKIPPED') return '非自动发货'
  if (state === 1) return '已发货'
  if (state === 0) return '待发货'
  return '失败'
}

const getDeliveryColor = (state: number, deliveryStatus?: string) => {
  if (deliveryStatus === 'SKIPPED') return 'rgba(28,28,30,.55)'
  if (state === 1) return '#30D158'
  if (state === 0) return '#FF9F0A'
  return '#FF453A'
}

const getDeliveryBg = (state: number, deliveryStatus?: string) => {
  if (deliveryStatus === 'SKIPPED') return 'rgba(120,120,128,.12)'
  if (state === 1) return 'rgba(48,209,88,.2)'
  if (state === 0) return 'rgba(255,159,10,.18)'
  return 'rgba(255,69,58,.15)'
}

const getTradeStatusColor = (status?: string) => {
  if (status === 'COMPLETED') return '#30D158'
  if (status === 'REFUNDING') return '#FF9F0A'
  if (status === 'REFUNDED') return '#FF453A'
  if (status === 'PENDING_PAYMENT' || status === 'PENDING_SHIPMENT') return '#FF9F0A'
  if (status === 'SHIPPED') return '#007AFF'
  return 'rgba(28,28,30,.55)'
}

const getTradeStatusBg = (status?: string) => {
  if (status === 'COMPLETED') return 'rgba(48,209,88,.2)'
  if (status === 'REFUNDING' || status === 'PENDING_PAYMENT' || status === 'PENDING_SHIPMENT') return 'rgba(255,159,10,.18)'
  if (status === 'REFUNDED') return 'rgba(255,69,58,.15)'
  if (status === 'SHIPPED') return 'rgba(0,122,255,.12)'
  return 'rgba(120,120,128,.12)'
}

const getTradeStatusText = (order: DeliveryRecordItem) => order.tradeStatusText || '未同步'

const canConfirmShipment = (order: DeliveryRecordItem) => {
  return order.state === 1
    && order.deliveryStatus !== 'SKIPPED'
    && !['REFUNDING', 'REFUNDED', 'CLOSED'].includes(order.tradeStatus || '')
}

const canRuleDelivery = (order: DeliveryRecordItem) => {
  return order.deliveryStatus === 'FAILED'
    && Boolean(order.orderId && order.xianyuAccountId && order.xyGoodsId)
    && !['REFUNDING', 'REFUNDED', 'CLOSED'].includes(order.tradeStatus || '')
}

const runCompensation = async (order: DeliveryRecordItem, action: AutomationAction) => {
  if (!order.orderId || !order.xianyuAccountId) {
    showInfo('该订单缺少订单号或账号信息，暂不能执行此操作')
    return
  }
  const key = `${orderKey(order)}:${action}`
  runningCompensationKey.value = key
  try {
    const actionResponse = await getOrderAutomationAvailableActions({
      accountId: order.xianyuAccountId,
      orderId: order.orderId
    })
    if (actionResponse.code !== 0 && actionResponse.code !== 200) {
      throw new Error(actionResponse.msg || '检查补偿条件失败')
    }
    const availableActions: OrderAutomationAvailableActions = actionResponse.data || {
      rateAvailable: false,
      redFlowerAvailable: false
    }
    const available = action === 'RED_FLOWER'
      ? availableActions.redFlowerAvailable : availableActions.rateAvailable
    const reason = action === 'RED_FLOWER'
      ? availableActions.redFlowerReason : availableActions.rateReason
    if (!available) {
      showInfo(reason || `${action === 'RED_FLOWER' ? '补小红花' : '补评价'}当前不可执行`)
      return
    }
    const response = await retryOrderAutomation({
      accountId: order.xianyuAccountId,
      orderId: order.orderId,
      action
    })
    if (response.code !== 0 && response.code !== 200) {
      throw new Error(response.msg || response.data?.message || '补偿操作失败')
    }
    showSuccess(response.data?.message || (action === 'RED_FLOWER' ? '补小红花成功' : '补评价成功'))
    emit('refresh')
  } catch (error: any) {
    showError(`${action === 'RED_FLOWER' ? '补小红花' : '补评价'}失败：${error.message || '请稍后重试'}`)
  } finally {
    if (runningCompensationKey.value === key) runningCompensationKey.value = null
  }
}

const ruleDeliveryReason = (order: DeliveryRecordItem) => {
  if (canRuleDelivery(order)) return '按商品关联的卡券规则补发'
  if (['REFUNDING', 'REFUNDED', 'CLOSED'].includes(order.tradeStatus || '')) return '退款或关闭交易不能发货'
  if (order.state === 1) return '订单已经发货完成'
  return '仅自动发货失败且已匹配卡券规则的订单可手动补发'
}

const confirmShipmentReason = (order: DeliveryRecordItem) => {
  if (canConfirmShipment(order)) return '向闲鱼确认该订单已发货'
  if (['REFUNDING', 'REFUNDED', 'CLOSED'].includes(order.tradeStatus || '')) return '退款或关闭交易不能确认发货'
  return order.state === 1 ? '当前订单不满足确认发货条件' : '请先完成发货后再确认'
}

const getConfirmText = (state: number) => {
  return state === 1 ? '已确认' : '未确认'
}

const getConfirmColor = (state: number) => {
  return state === 1 ? '#30D158' : 'rgba(28,28,30,.55)'
}

const getConfirmBg = (state: number) => {
  return state === 1 ? 'rgba(48,209,88,.2)' : 'rgba(120,120,128,.12)'
}

type StatusPresentation = {
  text: string
  tone: 'success' | 'warning' | 'danger' | 'muted'
  reason?: string
}

const getDeliveryMethod = (order: DeliveryRecordItem) => {
  const channel = (order.deliveryChannel || '').toUpperCase()
  if (channel.includes('MANUAL')) return '人工补发'
  if (channel.includes('AUTO') || channel.includes('WS')) return '自动发货'
  return order.deliveryStatus === 'SKIPPED' ? '未启用自动发货' : '自动发货'
}

const getDeliveryPresentation = (order: DeliveryRecordItem): StatusPresentation => {
  const status = (order.deliveryStatus || '').toUpperCase()
  const reason = order.lastErrorMessage || order.failReason
  if (status === 'COMPLETED' || order.state === 1) return { text: '已发货', tone: 'success' }
  if (status === 'REVIEW_REQUIRED') return { text: '待人工核对', tone: 'warning', reason }
  if (status === 'FAILED' || order.state === -1) return { text: '发货失败', tone: 'danger', reason }
  if (status === 'SKIPPED') return { text: '未自动发货', tone: 'muted', reason }
  if (status === 'PROCESSING') return { text: '发货处理中', tone: 'warning' }
  if (status === 'RETRY_WAIT') return { text: '等待重试', tone: 'warning', reason }
  return { text: '等待发货', tone: 'warning' }
}

const getRatePresentation = (order: DeliveryRecordItem): StatusPresentation => {
  if (order.rateEnabled !== 1) return { text: '未启用', tone: 'muted' }
  switch (order.rateStatus) {
    case 1: return { text: '已评价', tone: 'success' }
    case 2: return { text: '评价失败', tone: 'danger', reason: order.rateError }
    case 3: return { text: '无需评价', tone: 'muted', reason: order.rateError }
    case 4: return { text: '等待确认', tone: 'warning', reason: order.rateError }
    default: return { text: '待评价', tone: 'warning' }
  }
}

const getRedFlowerPresentation = (order: DeliveryRecordItem): StatusPresentation => {
  if (order.redFlowerEnabled !== 1) return { text: '未启用', tone: 'muted' }
  switch (order.redFlowerStatus) {
    case 1: return { text: '已求花', tone: 'success' }
    case 2: return { text: '求花失败', tone: 'danger', reason: order.redFlowerError }
    default:
      return order.confirmState === 1 && order.state === 1
        ? { text: '待求花', tone: 'warning' }
        : { text: '待确认发货', tone: 'muted' }
  }
}
</script>

<template>
  <div v-if="isMobile" class="card-list" :class="{ 'card-list--loading': loading }">
    <div
      v-for="order in orderList"
      :key="order.id"
      class="order-card"
    >
      <div class="order-card__header">
        <span class="order-card__id">{{ order.orderId || '-' }}</span>
        <div class="order-card__status-group">
          <span
            class="order-card__status"
            :style="{
              color: getDeliveryColor(order.state, order.deliveryStatus),
              background: getDeliveryBg(order.state, order.deliveryStatus)
            }"
          >
            {{ getDeliveryText(order.state, order.deliveryStatus) }}
          </span>
          <span
            class="order-card__status"
            :style="{
              color: getTradeStatusColor(order.tradeStatus),
              background: getTradeStatusBg(order.tradeStatus)
            }"
          >
            {{ getTradeStatusText(order) }}
          </span>
          <span v-if="order.state === -1 && order.failReason" class="order-card__fail-reason">{{ order.failReason }}</span>
          <span
            class="order-card__status"
            :style="{
              color: getConfirmColor(order.confirmState || 0),
              background: getConfirmBg(order.confirmState || 0)
            }"
          >
            {{ getConfirmText(order.confirmState || 0) }}
          </span>
        </div>
      </div>

      <div class="order-card__body">
        <div class="order-card__row">
          <IconShoppingBag />
          <span class="order-card__label">商品</span>
          <span class="order-card__value text-ellipsis-10" :title="order.goodsTitle || '-'">{{ order.goodsTitle || '-' }}</span>
        </div>
        <div v-if="order.skuName" class="order-card__row">
          <span class="order-card__label"></span>
          <span class="order-card__label">规格</span>
          <span class="order-card__value order-card__sku">{{ order.skuName }}</span>
        </div>
        <div class="order-card__row">
          <IconUser />
          <span class="order-card__label">买家</span>
          <span class="order-card__value">{{ order.buyerUserName || '-' }}</span>
        </div>
        <div class="order-card__row">
          <IconClock />
          <span class="order-card__label">下单时间</span>
          <span class="order-card__value">{{ formatTime(order.orderCreateTime || order.createTime) }}</span>
        </div>
      </div>

      <div class="order-card__footer">
        <button
          class="order-card__action order-card__action--detail"
          :disabled="!order.orderId"
          @click="handleClickDetail(order)"
        >
          <IconEye />
          <span>详情</span>
          <span class="detail-tooltip">单击查询本地，双击查询闲鱼服务器</span>
        </button>
        <div class="order-card__more-wrap" @click.stop>
          <button class="order-card__action order-card__action--menu" type="button" @click="toggleActionMenu($event, order)">
            <span>更多操作</span><span class="order-action-caret">⌄</span>
          </button>
          <Transition name="action-menu">
            <div v-if="openedActionMenuKey === orderKey(order)" class="order-action-menu order-action-menu--mobile" role="menu">
              <button type="button" role="menuitem" :disabled="!order.orderId" @click="runMenuAction($event, () => emit('copySid', order.orderId || ''))"><IconCopy /> 复制订单 ID</button>
              <button type="button" role="menuitem" :disabled="!canRuleDelivery(order)" :title="ruleDeliveryReason(order)" @click="runMenuAction($event, () => emit('ruleDelivery', order))"><IconTruck /> {{ order.manualDelivering ? '发货中' : '手动发货' }}</button>
              <button type="button" role="menuitem" :disabled="!canConfirmShipment(order)" :title="confirmShipmentReason(order)" @click="runMenuAction($event, () => emit('confirmShipment', order))"><IconTruck /> {{ order.confirming ? '处理中' : '确认发货' }}</button>
              <div class="order-action-menu__divider"></div>
              <button type="button" role="menuitem" :disabled="runningCompensationKey === `${orderKey(order)}:RATE_CHECK`" title="会先检查闲鱼待评价列表，再决定是否评价" @click="runMenuAction($event, () => runCompensation(order, 'RATE_CHECK'))">{{ runningCompensationKey === `${orderKey(order)}:RATE_CHECK` ? '检查中' : '补评价' }}</button>
              <button type="button" role="menuitem" :disabled="runningCompensationKey === `${orderKey(order)}:RED_FLOWER`" title="会先检查是否已确认发货及交易状态" @click="runMenuAction($event, () => runCompensation(order, 'RED_FLOWER'))">{{ runningCompensationKey === `${orderKey(order)}:RED_FLOWER` ? '检查中' : '补小红花' }}</button>
            </div>
          </Transition>
        </div>
      </div>
    </div>

    <div v-if="!loading && orderList.length === 0" class="empty-state">
      <div class="empty-state__icon"><IconEmpty /></div>
      <p class="empty-state__text">暂无发货记录</p>
    </div>
  </div>

  <div v-else class="table-container" :class="{ 'table-container--loading': loading }">
    <table class="table" v-if="orderList.length > 0">
      <thead class="table__head">
        <tr>
          <th class="table__th table__th--order">订单</th>
          <th class="table__th table__th--product">商品与买家</th>
          <th class="table__th table__th--trade">交易</th>
          <th class="table__th table__th--delivery">发货</th>
          <th class="table__th table__th--automation">自动化</th>
          <th class="table__th table__th--actions">操作</th>
        </tr>
      </thead>
      <tbody class="table__body">
        <tr v-for="order in orderList" :key="order.id" class="table__tr">
          <td class="table__td table__td--order">
            <div class="order-cell">
              <span class="order-id">{{ order.orderId || '-' }}</span>
              <span class="order-cell__time">{{ formatTime(order.orderCreateTime || order.createTime) }}</span>
            </div>
          </td>
          <td class="table__td table__td--product">
            <div class="order-title-cell">
              <span class="order-title-cell__name text-ellipsis-10" :title="order.goodsTitle || '-'">{{ order.goodsTitle || '-' }}</span>
              <span v-if="order.skuName" class="order-title-cell__meta">规格：{{ order.skuName }}</span>
              <span class="order-title-cell__meta">买家：{{ order.buyerUserName || '-' }}</span>
            </div>
          </td>
          <td class="table__td table__td--trade">
            <span
              class="status-tag"
              :style="{
                color: getTradeStatusColor(order.tradeStatus),
                background: getTradeStatusBg(order.tradeStatus)
              }"
            >
              {{ getTradeStatusText(order) }}
            </span>
            <span
              class="status-tag status-tag--secondary"
              :style="{
                color: getConfirmColor(order.confirmState || 0),
                background: getConfirmBg(order.confirmState || 0)
              }"
            >
              {{ getConfirmText(order.confirmState || 0) }}
            </span>
          </td>
          <td class="table__td table__td--delivery">
            <div class="delivery-cell">
              <span class="status-chip" :class="`status-chip--${getDeliveryPresentation(order).tone}`">
                {{ getDeliveryPresentation(order).text }}
              </span>
              <span class="delivery-cell__method">{{ getDeliveryMethod(order) }}</span>
              <span v-if="getDeliveryPresentation(order).reason" class="delivery-cell__reason" :title="getDeliveryPresentation(order).reason">
                {{ getDeliveryPresentation(order).reason }}
              </span>
            </div>
          </td>
          <td class="table__td table__td--automation">
            <div class="automation-cell">
              <div class="automation-cell__line" :title="getRatePresentation(order).reason">
                <span class="automation-cell__label">评价</span>
                <span class="status-chip" :class="`status-chip--${getRatePresentation(order).tone}`">{{ getRatePresentation(order).text }}</span>
              </div>
              <div class="automation-cell__line" :title="getRedFlowerPresentation(order).reason">
                <span class="automation-cell__label">小红花</span>
                <span class="status-chip" :class="`status-chip--${getRedFlowerPresentation(order).tone}`">{{ getRedFlowerPresentation(order).text }}</span>
              </div>
            </div>
          </td>
          <td class="table__td table__td--actions">
            <div class="table__action-group">
              <button
                class="table__action table__action--detail"
                :disabled="!order.orderId"
                @click="handleClickDetail(order)"
              >
                <IconEye />
                <span>详情</span>
                <span class="detail-tooltip">单击查询本地，双击查询闲鱼服务器</span>
              </button>
              <div class="table__action-menu-wrap" @click.stop>
                <button class="table__action table__action--menu" type="button" @click="toggleActionMenu($event, order)">
                  <span>更多操作</span><span class="order-action-caret">⌄</span>
                </button>
                <Transition name="action-menu">
                  <div v-if="openedActionMenuKey === orderKey(order)" class="order-action-menu" role="menu">
                    <button type="button" role="menuitem" :disabled="!order.orderId" @click="runMenuAction($event, () => emit('copySid', order.orderId || ''))"><IconCopy /> 复制订单 ID</button>
                    <button type="button" role="menuitem" :disabled="!canRuleDelivery(order)" :title="ruleDeliveryReason(order)" @click="runMenuAction($event, () => emit('ruleDelivery', order))"><IconTruck /> {{ order.manualDelivering ? '发货中' : '手动发货' }}</button>
                    <button type="button" role="menuitem" :disabled="!canConfirmShipment(order)" :title="confirmShipmentReason(order)" @click="runMenuAction($event, () => emit('confirmShipment', order))"><IconTruck /> {{ order.confirming ? '处理中' : '确认发货' }}</button>
                    <div class="order-action-menu__divider"></div>
                    <button type="button" role="menuitem" :disabled="runningCompensationKey === `${orderKey(order)}:RATE_CHECK`" title="会先检查闲鱼待评价列表，再决定是否评价" @click="runMenuAction($event, () => runCompensation(order, 'RATE_CHECK'))">{{ runningCompensationKey === `${orderKey(order)}:RATE_CHECK` ? '检查中' : '补评价' }}</button>
                    <button type="button" role="menuitem" :disabled="runningCompensationKey === `${orderKey(order)}:RED_FLOWER`" title="会先检查是否已确认发货及交易状态" @click="runMenuAction($event, () => runCompensation(order, 'RED_FLOWER'))">{{ runningCompensationKey === `${orderKey(order)}:RED_FLOWER` ? '检查中' : '补小红花' }}</button>
                  </div>
                </Transition>
              </div>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="!loading && orderList.length === 0" class="empty-state">
      <div class="empty-state__icon"><IconEmpty /></div>
      <p class="empty-state__text">暂无发货记录</p>
    </div>
  </div>

  <!-- Order Detail Dialog -->
  <Transition name="overlay-fade">
    <div v-if="detailVisible" class="detail-overlay" @click.self="detailVisible = false">
      <div class="detail-dialog">
        <div class="detail-dialog__header">
          <h3 class="detail-dialog__title">订单详情</h3>
          <button class="detail-dialog__close" @click="detailVisible = false">&times;</button>
        </div>
        <div class="detail-dialog__body">
          <div v-if="detailLoading" class="detail-dialog__loading">
            <div class="detail-dialog__spinner"></div>
            <span>加载中...</span>
          </div>
          <template v-else-if="detailData">
            <div class="detail-dialog__section">
              <div class="detail-dialog__rows">
                <template v-if="detailFromServer && detailData.module">
                  <div v-if="detailData.module.merchantCommonData?.orderId" class="detail-dialog__row">
                    <span class="detail-dialog__label">订单ID</span>
                    <span class="detail-dialog__value">{{ detailData.module.merchantCommonData.orderId }}</span>
                  </div>
                  <div v-if="detailData.module.merchantCommonData?.orderStatus" class="detail-dialog__row">
                    <span class="detail-dialog__label">状态</span>
                    <span class="detail-dialog__value">{{ detailData.module.merchantCommonData.orderStatus }}</span>
                  </div>
                  <div v-if="detailData.module.merchantCommonData?.createTime" class="detail-dialog__row">
                    <span class="detail-dialog__label">下单时间</span>
                    <span class="detail-dialog__value">{{ detailData.module.merchantCommonData.createTime }}</span>
                  </div>
                  <div v-if="detailData.module.merchantCommonData?.paySuccessTime" class="detail-dialog__row">
                    <span class="detail-dialog__label">付款时间</span>
                    <span class="detail-dialog__value">{{ detailData.module.merchantCommonData.paySuccessTime }}</span>
                  </div>
                  <div v-if="detailData.module.merchantItemVO?.title" class="detail-dialog__row">
                    <span class="detail-dialog__label">商品</span>
                    <span class="detail-dialog__value">{{ detailData.module.merchantItemVO.title }}</span>
                  </div>
                  <div v-if="detailSkuText" class="detail-dialog__row detail-dialog__row--highlight">
                    <span class="detail-dialog__label">规格</span>
                    <span class="detail-dialog__value detail-dialog__sku">{{ detailSkuText }}</span>
                  </div>
                  <div v-if="detailData.module.merchantPriceVO?.totalPrice" class="detail-dialog__row">
                    <span class="detail-dialog__label">金额</span>
                    <span class="detail-dialog__value">¥{{ detailData.module.merchantPriceVO.totalPrice }}</span>
                  </div>
                  <div v-if="detailData.module.merchantPriceVO?.buyNum" class="detail-dialog__row">
                    <span class="detail-dialog__label">数量</span>
                    <span class="detail-dialog__value">{{ detailData.module.merchantPriceVO.buyNum }}</span>
                  </div>
                  <div v-if="detailData.module.merchantBuyerVO?.userNick" class="detail-dialog__row">
                    <span class="detail-dialog__label">买家</span>
                    <span class="detail-dialog__value">{{ detailData.module.merchantBuyerVO.userNick }}</span>
                  </div>
                </template>
                <template v-else-if="!detailFromServer">
                  <div class="detail-dialog__tag detail-dialog__tag--local">本地数据</div>
                  <div v-if="detailData.orderId" class="detail-dialog__row">
                    <span class="detail-dialog__label">订单ID</span>
                    <span class="detail-dialog__value">{{ detailData.orderId }}</span>
                  </div>
                  <div v-if="detailData.goodsTitle" class="detail-dialog__row">
                    <span class="detail-dialog__label">商品</span>
                    <span class="detail-dialog__value">{{ detailData.goodsTitle }}</span>
                  </div>
                  <div v-if="detailSkuText" class="detail-dialog__row detail-dialog__row--highlight">
                    <span class="detail-dialog__label">规格</span>
                    <span class="detail-dialog__value detail-dialog__sku">{{ detailSkuText }}</span>
                  </div>
                  <div v-if="detailData.orderCreateTime" class="detail-dialog__row">
                    <span class="detail-dialog__label">下单时间</span>
                    <span class="detail-dialog__value">{{ detailData.orderCreateTime }}</span>
                  </div>
                  <div v-if="detailData.paySuccessTime" class="detail-dialog__row">
                    <span class="detail-dialog__label">付款时间</span>
                    <span class="detail-dialog__value">{{ detailData.paySuccessTime }}</span>
                  </div>
                  <div v-if="detailData.totalPrice" class="detail-dialog__row">
                    <span class="detail-dialog__label">金额</span>
                    <span class="detail-dialog__value">¥{{ detailData.totalPrice }}</span>
                  </div>
                  <div v-if="detailData.buyNum" class="detail-dialog__row">
                    <span class="detail-dialog__label">数量</span>
                    <span class="detail-dialog__value">{{ detailData.buyNum }}</span>
                  </div>
                  <div v-if="detailData.buyerUserName" class="detail-dialog__row">
                    <span class="detail-dialog__label">买家</span>
                    <span class="detail-dialog__value">{{ detailData.buyerUserName }}</span>
                  </div>
                  <div v-if="detailData.consignTime" class="detail-dialog__row">
                    <span class="detail-dialog__label">发货时间</span>
                    <span class="detail-dialog__value">{{ detailData.consignTime }}</span>
                  </div>
                  <div v-if="detailData.content" class="detail-dialog__row">
                    <span class="detail-dialog__label">发货内容</span>
                    <span class="detail-dialog__value detail-dialog__content">{{ detailData.content }}</span>
                  </div>
                  <div class="detail-dialog__row">
                    <span class="detail-dialog__label">发货状态</span>
                    <span class="detail-dialog__value" :style="{ color: detailData.state === 1 ? '#34c759' : '#ff3b30' }">{{ detailData.state === 1 ? '成功' : '失败' }}</span>
                  </div>
                  <div v-if="detailData.failReason" class="detail-dialog__row">
                    <span class="detail-dialog__label">失败原因</span>
                    <span class="detail-dialog__value detail-dialog__fail">{{ detailData.failReason }}</span>
                  </div>
                  <div class="detail-dialog__row">
                    <span class="detail-dialog__label">确认状态</span>
                    <span class="detail-dialog__value">{{ detailData.confirmState === 1 ? '已确认' : '未确认' }}</span>
                  </div>
                  <div v-if="detailData.createTime" class="detail-dialog__row">
                    <span class="detail-dialog__label">记录时间</span>
                    <span class="detail-dialog__value">{{ detailData.createTime }}</span>
                  </div>
                </template>
                <div v-if="detailFromServer && !detailData.module" class="detail-dialog__raw">
                  <pre>{{ JSON.stringify(detailData, null, 2) }}</pre>
                </div>
              </div>
              <section v-if="!detailFromServer" class="order-timeline">
                <div class="order-timeline__header">
                  <div>
                    <h4>订单生命周期</h4>
                    <p>本地同步、发货、评价和小红花的处理记录</p>
                  </div>
                </div>
                <div v-if="detailTimelineLoading" class="order-timeline__loading">正在加载生命周期…</div>
                <div v-else-if="detailTimeline.length" class="order-timeline__list">
                  <article v-for="event in detailTimeline" :key="`${event.type}-${event.title}`" class="order-timeline__event">
                    <span class="order-timeline__dot" :class="`order-timeline__dot--${event.status.toLowerCase()}`"></span>
                    <div class="order-timeline__content">
                      <div class="order-timeline__title-row">
                        <strong>{{ event.title }}</strong>
                        <span class="order-timeline__status" :class="`order-timeline__status--${event.status.toLowerCase()}`">
                          {{ event.status === 'SUCCESS' ? '成功' : event.status === 'FAILED' ? '失败' : event.status === 'WARNING' ? '需关注' : event.status === 'SKIPPED' ? '未执行' : event.status === 'PENDING' ? '等待中' : '已记录' }}
                        </span>
                      </div>
                      <p v-if="event.description">{{ event.description }}</p>
                      <div class="order-timeline__meta">
                        <time v-if="event.occurredAt">{{ event.occurredAt }}</time>
                        <button v-if="event.retryable" type="button" @click="openAutomationCenter">去自动化中心处理</button>
                      </div>
                    </div>
                  </article>
                </div>
                <div v-else class="order-timeline__empty">暂无可追溯的自动化记录</div>
              </section>
            </div>
          </template>
          <div v-else class="detail-dialog__empty">暂无数据</div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.overlay-fade-enter-active,
.overlay-fade-leave-active {
  transition: opacity 0.2s ease;
}
.overlay-fade-enter-from,
.overlay-fade-leave-to {
  opacity: 0;
}
.card-list,
.table-container {
  --c-bg: transparent;
  --c-surface: rgba(255,255,255,0.55);
  --c-surface-hover: rgba(255,255,255,0.72);
  --c-border: rgba(255,255,255,0.75);
  --c-border-strong: rgba(60,60,67,.12);
  --c-text-1: #1c1c1e;
  --c-text-2: rgba(28,28,30,.55);
  --c-text-3: rgba(28,28,30,.55);
  --c-accent: #0A84FF;
  --c-danger: #FF453A;
  --c-success: #30D158;
  --c-r-sm: 10px;
  --c-r-md: 14px;
  --c-ease: 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  padding-bottom: 24px;
  min-height: 100%;
}

.order-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--c-r-md);
  overflow: hidden;
  transition: all var(--c-ease);
  box-shadow: 0 8px 32px rgba(0,0,0,0.10), 0 1.5px 4px rgba(0,0,0,0.06);
  backdrop-filter: blur(28px) saturate(1.8);
  -webkit-backdrop-filter: blur(28px) saturate(1.8);
}

.order-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 0.5px solid var(--c-border-strong);
}

.order-card__id {
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text-1);
  font-family: 'SF Mono', 'Menlo', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
}

.order-card__status-group {
  display: flex;
  gap: 4px;
}

.order-card__status {
  display: inline-flex;
  align-items: center;
  font-size: 11px;
  font-weight: 600;
  padding: 3px 8px;
  border-radius: 12px;
  line-height: 1;
  flex-shrink: 0;
}

.order-card__body {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.order-card__row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  min-height: 20px;
}

.order-card__row svg {
  width: 13px;
  height: 13px;
  color: var(--c-text-3);
  flex-shrink: 0;
}

.order-card__label {
  color: var(--c-text-3);
  flex-shrink: 0;
  min-width: 32px;
}

.order-card__value {
  color: var(--c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-card__footer {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-top: 0.5px solid var(--c-border-strong);
}

.order-card__more-wrap {
  position: relative;
  display: flex;
  flex: 1;
}

.order-card__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex: 1;
  height: 32px;
  font-size: 12px;
  font-weight: 500;
  border-radius: var(--c-r-sm);
  border: 1px solid;
  cursor: pointer;
  transition: all var(--c-ease);
  -webkit-tap-highlight-color: transparent;
  background: transparent;
}

.order-card__action svg {
  width: 13px;
  height: 13px;
}

.order-card__action--copy {
  color: var(--c-accent);
  border-color: rgba(0, 122, 255, 0.2);
}

@media (hover: hover) {
  .order-card__action--copy:hover {
    background: rgba(0, 122, 255, 0.06);
  }
}

.order-card__action--ship {
  color: var(--c-success);
  border-color: rgba(52, 199, 89, 0.2);
}

.order-card__action--rule-delivery,
.table__action--rule-delivery {
  color: #b26a00;
  border-color: rgba(255, 159, 10, .32);
  background: rgba(255, 159, 10, .05);
}

@media (hover: hover) {
  .order-card__action--rule-delivery:hover,
  .table__action--rule-delivery:hover { background: rgba(255, 159, 10, .12); }
}

@media (hover: hover) {
  .order-card__action--ship:hover {
    background: rgba(52, 199, 89, 0.06);
  }
}

.order-card__action--loading {
  opacity: 0.6;
  pointer-events: none;
}

.order-card__action:active {
  transform: scale(0.97);
}

.order-card__action:disabled,
.table__action:disabled {
  cursor: not-allowed;
  opacity: .42;
}

.table-container {
  min-height: 0;
  height: 100%;
}

.table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 13px;
}

.table__head {
  position: sticky;
  top: 0;
  z-index: 10;
}

.table__th {
  text-align: left;
  padding: 12px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #1c1c1e;
  letter-spacing: .4px;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(16px) saturate(1.6);
  -webkit-backdrop-filter: blur(16px) saturate(1.6);
  border-bottom: 1px solid rgba(60,60,67,.12);
  white-space: nowrap;
  user-select: none;
}

.table__th--center {
  text-align: center;
}

.table__th--order { width: 17%; }
.table__th--product { width: 24%; }
.table__th--trade { width: 12%; }
.table__th--delivery { width: 18%; }
.table__th--automation { width: 16%; }

.table__th--actions {
  width: 13%;
  text-align: center;
}

.table__tr {
  transition: background var(--c-ease);
}

.table__tr:not(:last-child) .table__td {
  border-bottom: 0.5px solid var(--c-border-strong);
}

@media (hover: hover) {
  .table__tr:hover .table__td {
    background: rgba(255,255,255,0.15);
  }
}

.table__td {
  padding: 11px 14px;
  color: var(--c-text-1);
  vertical-align: middle;
  background: transparent;
  transition: background var(--c-ease);
  line-height: 1.5;
}

.table__td--center {
  text-align: center;
}

.table__td--actions {
  min-width: 124px;
  text-align: center;
}

.table__action-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.table__action-menu-wrap {
  position: relative;
}

.order-id {
  font-size: 12px;
  font-family: 'SF Mono', 'Menlo', monospace;
  color: var(--c-text-2);
}

.order-cell,
.delivery-cell,
.automation-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.order-cell__time,
.order-title-cell__meta,
.delivery-cell__method {
  overflow: hidden;
  color: var(--c-text-3);
  font-size: 11px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-title-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.order-title-cell__name {
  font-weight: 500;
  color: var(--c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.text-ellipsis-10 {
  max-width: 10em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
}

.table__td-placeholder {
  color: var(--c-text-3);
}

.buyer-name {
  font-size: 13px;
  color: var(--c-text-2);
}

.content-text {
  font-size: 12px;
  color: var(--c-text-2);
}

.time-text {
  font-size: 12px;
  color: var(--c-text-2);
  font-variant-numeric: tabular-nums;
}

.status-tag {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 20px;
  line-height: 1;
}

.status-tag--secondary {
  margin-top: 5px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  width: fit-content;
  max-width: 100%;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 650;
  line-height: 1.25;
  white-space: nowrap;
}

.status-chip--success { color: #168b49; background: rgba(48, 209, 88, .16); }
.status-chip--warning { color: #a46600; background: rgba(255, 159, 10, .16); }
.status-chip--danger { color: #d83c32; background: rgba(255, 69, 58, .13); }
.status-chip--muted { color: #667085; background: rgba(120, 120, 128, .12); }

.delivery-cell__reason {
  display: -webkit-box;
  overflow: hidden;
  color: #d83c32;
  font-size: 11px;
  line-height: 1.35;
  text-overflow: ellipsis;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.automation-cell {
  gap: 6px;
}

.automation-cell__line {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 7px;
}

.automation-cell__label {
  flex: 0 0 38px;
  color: var(--c-text-3);
  font-size: 11px;
}

.automation-cell__line .status-chip {
  overflow: hidden;
  text-overflow: ellipsis;
}

.fail-reason {
  display: block;
  font-size: 11px;
  color: #ff3b30;
  margin-top: 2px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-card__fail-reason {
  display: block;
  font-size: 11px;
  color: #ff3b30;
  margin-top: 2px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sku-name-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 500;
  border-radius: 4px;
  color: #ff9500;
  background: rgba(255, 149, 0, 0.1);
  white-space: nowrap;
}

.order-card__sku {
  font-size: 11px;
  color: #ff9500;
}

.table__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 30px;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 6px;
  border: 1px solid rgba(52, 199, 89, 0.2);
  color: var(--c-success);
  background: transparent;
  cursor: pointer;
  transition: all var(--c-ease);
  -webkit-tap-highlight-color: transparent;
}

.table__action svg {
  width: 13px;
  height: 13px;
}

@media (hover: hover) {
  .table__action--ship:hover {
    background: rgba(52, 199, 89, 0.06);
  }
}

.table__action--detail {
  border-color: rgba(0, 122, 255, 0.2);
  color: var(--c-accent);
}

@media (hover: hover) {
  .table__action--detail:hover {
    background: rgba(0, 122, 255, 0.06);
  }
}

.order-card__action--detail {
  color: var(--c-accent);
  border-color: rgba(0, 122, 255, 0.2);
  position: relative;
}

.table__action--detail {
  position: relative;
}

.detail-tooltip {
  position: absolute;
  bottom: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 400;
  color: #fff;
  background: rgba(0, 0, 0, 0.75);
  border-radius: 4px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.15s ease;
  z-index: 10;
}

.table__action--detail:hover .detail-tooltip,
.order-card__action--detail:hover .detail-tooltip {
  opacity: 1;
  transition-delay: 0.2s;
}

@media (hover: hover) {
  .order-card__action--detail:hover {
    background: rgba(0, 122, 255, 0.06);
  }
}

.table__action--loading {
  opacity: 0.6;
  pointer-events: none;
}

.table__action--more,
.order-card__action--more {
  border-color: rgba(88, 86, 214, .22);
  color: #5856d6;
}

.table__action--menu,
.order-card__action--menu {
  border-color: rgba(60,60,67,.15);
  color: var(--c-text-2);
  background: rgba(60,60,67,.05);
}

.order-action-caret {
  margin-left: 2px;
  font-size: 15px;
  line-height: 1;
  transform: translateY(-1px);
}

.order-action-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 30;
  width: 172px;
  padding: 6px;
  border: 1px solid rgba(60,60,67,.14);
  border-radius: 10px;
  background: rgba(255,255,255,.98);
  box-shadow: 0 14px 28px rgba(30,42,60,.16);
}

.order-action-menu--mobile {
  top: auto;
  bottom: calc(100% + 8px);
}

.order-action-menu button {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #34445b;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.order-action-menu button:hover:not(:disabled) {
  color: #332900;
  background: rgba(255,191,0,.12);
}

.order-action-menu button:disabled {
  color: #a6adb7;
  cursor: not-allowed;
}

.order-action-menu button svg {
  width: 14px;
  height: 14px;
}

.order-action-menu__divider {
  height: 1px;
  margin: 5px 3px;
  background: rgba(60,60,67,.11);
}

.action-menu-enter-active,
.action-menu-leave-active {
  transition: opacity .14s ease, transform .14s ease;
}

.action-menu-enter-from,
.action-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.table__action--flower,
.order-card__action--flower {
  border-color: rgba(244, 114, 182, .3);
  color: #c02677;
  background: rgba(244, 114, 182, .04);
}

@media (hover: hover) {
  .table__action--flower:hover:not(:disabled),
  .order-card__action--flower:hover:not(:disabled) { background: rgba(244, 114, 182, .11); }
}

.table__action:active {
  transform: scale(0.95);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 16px;
  gap: 12px;
}

.empty-state__icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-3);
  opacity: 0.35;
}

.empty-state__icon svg {
  width: 36px;
  height: 36px;
}

.empty-state__text {
  font-size: 14px;
  color: var(--c-text-3);
}

.card-list--loading,
.table-container--loading {
  opacity: 0.5;
  pointer-events: none;
}

/* Detail Dialog */
.detail-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.20);
  z-index: 950;
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.detail-dialog {
  width: 100%;
  max-width: 480px;
  max-height: 80vh;
  background: rgba(255,255,255,0.72);
  backdrop-filter: blur(40px) saturate(2);
  -webkit-backdrop-filter: blur(40px) saturate(2);
  border: 1px solid rgba(255,255,255,0.75);
  border-radius: 20px;
  box-shadow: 0 16px 48px rgba(0,0,0,0.16), 0 2px 8px rgba(0,0,0,0.08);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.detail-dialog__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 0.5px solid var(--c-border-strong);
}

.detail-dialog__title {
  font-size: 16px;
  font-weight: 600;
  color: #1c1c1e;
  margin: 0;
}

.detail-dialog__close {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #86868b;
  background: none;
  border: none;
  cursor: pointer;
  border-radius: 6px;
}

.detail-dialog__close:hover {
  background: rgba(0, 0, 0, 0.04);
}

.detail-dialog__body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
  min-height: 120px;
}

.detail-dialog__loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px 0;
  color: #86868b;
  font-size: 13px;
}

.detail-dialog__spinner {
  width: 24px;
  height: 24px;
  border: 2px solid rgba(0, 0, 0, 0.08);
  border-top-color: #007aff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.detail-dialog__rows {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.detail-dialog__row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  font-size: 13px;
}

.detail-dialog__label {
  color: #86868b;
  min-width: 60px;
  flex-shrink: 0;
  line-height: 1.5;
}

.detail-dialog__value {
  color: #1d1d1f;
  word-break: break-all;
  line-height: 1.5;
}

.detail-dialog__row--highlight .detail-dialog__label,
.detail-dialog__row--highlight .detail-dialog__value {
  font-weight: 600;
}

.detail-dialog__sku {
  color: #ff9500;
  background: rgba(255, 149, 0, 0.08);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.detail-dialog__tag {
  display: inline-block;
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 600;
  border-radius: 12px;
  margin-bottom: 12px;
}

.detail-dialog__tag--local {
  color: #007aff;
  background: rgba(0, 122, 255, 0.1);
}

.detail-dialog__content {
  word-break: break-all;
  white-space: pre-wrap;
  font-size: 12px;
  color: var(--c-text-secondary);
}

.detail-dialog__fail {
  color: #ff3b30;
  font-size: 12px;
}

.order-timeline {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid rgba(60, 60, 67, 0.1);
}

.order-timeline__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 14px;
}

.order-timeline__header h4 {
  margin: 0;
  color: #1c1c1e;
  font-size: 14px;
  font-weight: 650;
}

.order-timeline__header p {
  margin: 4px 0 0;
  color: #86868b;
  font-size: 11px;
}

.order-timeline__list {
  display: flex;
  flex-direction: column;
}

.order-timeline__event {
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 8px;
  padding-bottom: 15px;
}

.order-timeline__event:not(:last-child)::before {
  position: absolute;
  top: 15px;
  bottom: -1px;
  left: 7px;
  width: 1px;
  background: rgba(60, 60, 67, 0.16);
  content: '';
}

.order-timeline__dot {
  z-index: 1;
  width: 10px;
  height: 10px;
  margin-top: 4px;
  border: 3px solid #fff;
  border-radius: 50%;
  background: #8e8e93;
  box-shadow: 0 0 0 1px rgba(60, 60, 67, 0.15);
}

.order-timeline__dot--success { background: #34c759; }
.order-timeline__dot--pending { background: #ff9500; }
.order-timeline__dot--failed { background: #ff3b30; }
.order-timeline__dot--warning { background: #ff9500; }
.order-timeline__dot--skipped { background: #8e8e93; }
.order-timeline__dot--info { background: #007aff; }

.order-timeline__content {
  min-width: 0;
}

.order-timeline__title-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.order-timeline__title-row strong {
  overflow: hidden;
  color: #1d1d1f;
  font-size: 13px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-timeline__status {
  flex: 0 0 auto;
  padding: 1px 6px;
  border-radius: 999px;
  font-size: 10px;
  line-height: 1.45;
}

.order-timeline__status--success { color: #16803b; background: rgba(52, 199, 89, 0.13); }
.order-timeline__status--pending,
.order-timeline__status--warning { color: #b15c00; background: rgba(255, 149, 0, 0.14); }
.order-timeline__status--failed { color: #d6302f; background: rgba(255, 59, 48, 0.12); }
.order-timeline__status--skipped { color: #6e6e73; background: rgba(142, 142, 147, 0.13); }
.order-timeline__status--info { color: #0066cf; background: rgba(0, 122, 255, 0.11); }

.order-timeline__content p {
  margin: 3px 0 0;
  color: #6e6e73;
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}

.order-timeline__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 5px;
}

.order-timeline__meta time {
  color: #98989d;
  font-size: 11px;
}

.order-timeline__meta button {
  padding: 0;
  border: 0;
  background: transparent;
  color: #007aff;
  cursor: pointer;
  font-size: 11px;
}

.order-timeline__meta button:hover {
  color: #0056b3;
}

.order-timeline__loading,
.order-timeline__empty {
  padding: 12px 0 4px 26px;
  color: #86868b;
  font-size: 12px;
}

.detail-dialog__raw {
  overflow-x: auto;
}

.detail-dialog__raw pre {
  font-size: 11px;
  color: #6e6e73;
  white-space: pre-wrap;
  word-break: break-all;
}

.detail-dialog__empty {
  text-align: center;
  color: #86868b;
  font-size: 13px;
  padding: 40px 0;
}

@media screen and (max-width: 480px) {
  .card-list {
    padding: 12px;
    gap: 8px;
  }

  .order-card__header {
    padding: 8px 10px;
  }

  .order-card__body {
    padding: 8px 10px;
    gap: 6px;
  }

  .order-card__footer {
    padding: 6px 10px;
  }

  .order-card__action {
    height: 30px;
    font-size: 11px;
  }
}
</style>

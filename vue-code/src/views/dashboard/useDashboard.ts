import { ref, reactive } from 'vue'
import { getDashboardOverview, type DashboardTrendPoint } from '@/api/dashboard'
import { getAccountList } from '@/api/account'
import { queryDeliveryRecordList, type DeliveryRecordVO } from '@/api/order'
import type { Account } from '@/types'

const isMerchantActionOrder = (order: DeliveryRecordVO) => {
  const trade = `${order.tradeStatus || ''} ${order.tradeStatusText || ''}`.toUpperCase()
  if (trade.includes('PENDING_PAYMENT') || trade.includes('待付款') || trade.includes('等待付款')) return false
  const delivery = String(order.deliveryStatus || '').toUpperCase()
  const terminal = ['COMPLETED', 'FINISHED', 'REFUNDED', 'CLOSED'].some(status => trade.includes(status))
  if (['FAILED', 'REVIEW_REQUIRED'].includes(delivery)) return true
  if (['PENDING', 'PROCESSING', 'RETRY_WAIT'].includes(delivery)) return !terminal
  if (String(order.deliveryChannel || '').toUpperCase() === 'PICKUP') {
    return !terminal
  }
  if (trade.includes('PENDING_SHIPMENT') && Number(order.confirmState || 0) !== 1) return true
  return trade.includes('REFUNDING') || trade.includes('退款中') || trade.includes('售后')
}

export function useDashboard() {
  const loading = ref(false)
  const stats = reactive({
    accountCount: 0,
    itemCount: 0,
    sellingItemCount: 0,
    offShelfItemCount: 0,
    soldItemCount: 0,
    todayOrderCount: 0,
    totalOrderCount: 0,
    todayRevenue: 0,
    todayDeliveryCount: 0,
    todayReplyCount: 0,
    merchantActionOrderCount: 0,
    pendingTaskCount: 0,
    reviewRequiredCount: 0,
    failedTaskCount: 0,
    availableKamiCount: 0,
    lowStockConfigCount: 0,
    unreadMessageCount: 0
  })
  const trends = ref<DashboardTrendPoint[]>([])
  const automationExceptionCount = ref(0)
  const accounts = ref<Account[]>([])
  const pendingOrders = ref<DeliveryRecordVO[]>([])
  const pendingOrderCount = ref(0)

  const loadStatistics = async () => {
    loading.value = true
    try {
      const [overviewResult, accountResult, orderResult] = await Promise.allSettled([
        getDashboardOverview(),
        getAccountList(),
        queryDeliveryRecordList({ pageNum: 1, pageSize: 100 })
      ])

      if (overviewResult.status === 'fulfilled') {
        const res = overviewResult.value
        if ((res.code === 0 || res.code === 200) && res.data) {
          Object.assign(stats, res.data.stats || {})
          trends.value = res.data.trends || []
          automationExceptionCount.value = Number(res.data.automationExceptionCount || 0)
          pendingOrderCount.value = Number(res.data.stats?.merchantActionOrderCount || 0)
        }
      }

      if (accountResult.status === 'fulfilled') {
        const res = accountResult.value
        if (res.code === 0 || res.code === 200) accounts.value = res.data?.accounts || []
      }

      if (orderResult.status === 'fulfilled') {
        const records = (orderResult.value.data?.records || []).filter(isMerchantActionOrder)
        pendingOrders.value = records.slice(0, 5)
      }
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    stats,
    trends,
    automationExceptionCount,
    accounts,
    pendingOrders,
    pendingOrderCount,
    loadStatistics
  }
}
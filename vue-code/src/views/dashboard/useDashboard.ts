import { ref, reactive } from 'vue'
import { getDashboardOverview, type DashboardTrendPoint } from '@/api/dashboard'
import { getAccountList } from '@/api/account'
import { queryDeliveryRecordList, type DeliveryRecordVO } from '@/api/order'
import type { Account } from '@/types'

const isMerchantActionOrder = (order: DeliveryRecordVO) => {
  const trade = `${order.tradeStatus || ''} ${order.tradeStatusText || ''}`.toUpperCase()
  return !trade.includes('PENDING_PAYMENT') && !trade.includes('待付款') && !trade.includes('等待付款')
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
        queryDeliveryRecordList({ orderStatus: 0, pageNum: 1, pageSize: 20 })
      ])

      if (overviewResult.status === 'fulfilled') {
        const res = overviewResult.value
        if ((res.code === 0 || res.code === 200) && res.data) {
          Object.assign(stats, res.data.stats || {})
          trends.value = res.data.trends || []
          automationExceptionCount.value = Number(res.data.automationExceptionCount || 0)
        }
      }

      if (accountResult.status === 'fulfilled') {
        const res = accountResult.value
        if (res.code === 0 || res.code === 200) accounts.value = res.data?.accounts || []
      }

      if (orderResult.status === 'fulfilled') {
        const records = (orderResult.value.data?.records || []).filter(isMerchantActionOrder)
        pendingOrders.value = records.slice(0, 5)
        // 订单接口没有按交易状态单独计数，因此首页只显示已同步且已排除待付款的真实商家待办。
        pendingOrderCount.value = records.length
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
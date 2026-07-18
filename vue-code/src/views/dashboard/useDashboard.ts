import { ref, reactive } from 'vue'
import { getDashboardOverview, type DashboardTrendPoint } from '@/api/dashboard'

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

  const loadStatistics = async () => {
    loading.value = true
    try {
      const res = await getDashboardOverview()
      if ((res.code === 0 || res.code === 200) && res.data) {
        Object.assign(stats, res.data.stats || {})
        trends.value = res.data.trends || []
        automationExceptionCount.value = Number(res.data.automationExceptionCount || 0)
      }
    } finally {
      loading.value = false
    }
  }

  return { loading, stats, trends, automationExceptionCount, loadStatistics }
}

import { ref, reactive } from 'vue'
import {
  getDashboardOverview,
  type DashboardAccountHealth,
  type DashboardActivity,
  type DashboardTrendPoint
} from '@/api/dashboard'

export function useDashboard() {
  const loading = ref(false)
  
  const stats = reactive({
    accountCount: 0,
    itemCount: 0,
    sellingItemCount: 0,
    offShelfItemCount: 0,
    soldItemCount: 0,
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

  const accountHealth = ref<DashboardAccountHealth[]>([])
  const trends = ref<DashboardTrendPoint[]>([])
  const activities = ref<DashboardActivity[]>([])
  const accountIssueCount = ref(0)
  const automationExceptionCount = ref(0)

  const loadStatistics = async () => {
    loading.value = true
    try {
      const res = await getDashboardOverview()
      if (res.code === 0 || res.code === 200) {
        if (res.data) {
          Object.assign(stats, res.data.stats || {})
          accountHealth.value = res.data.accountHealth || []
          trends.value = res.data.trends || []
          activities.value = res.data.activities || []
          accountIssueCount.value = Number(res.data.accountIssueCount || 0)
          automationExceptionCount.value = Number(res.data.automationExceptionCount || 0)
        }
      }
    } catch {
      // 请求层统一展示错误
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    stats,
    accountHealth,
    trends,
    activities,
    accountIssueCount,
    automationExceptionCount,
    loadStatistics
  }
}

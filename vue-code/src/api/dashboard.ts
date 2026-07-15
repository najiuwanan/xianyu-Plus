import { request } from '@/utils/request'

export interface DashboardStats {
  accountCount: number
  itemCount: number
  sellingItemCount: number
  offShelfItemCount: number
  soldItemCount: number
  todayRevenue: number
  todayDeliveryCount: number
  todayReplyCount: number
  pendingTaskCount: number
  reviewRequiredCount: number
  failedTaskCount: number
  availableKamiCount: number
  lowStockConfigCount: number
}

/**
 * 获取首页统计数据
 */
export function getDashboardStats() {
  return request<DashboardStats>({
    url: '/dashboard/stats',
    method: 'POST',
    data: {}
  })
}

import { request } from '@/utils/request'

export interface DashboardStats {
  accountCount: number
  itemCount: number
  sellingItemCount: number
  offShelfItemCount: number
  soldItemCount: number
  todayOrderCount: number
  totalOrderCount: number
  todayRevenue: number
  todayDeliveryCount: number
  todayReplyCount: number
  pendingTaskCount: number
  reviewRequiredCount: number
  failedTaskCount: number
  availableKamiCount: number
  lowStockConfigCount: number
  unreadMessageCount: number
}

export interface DashboardTrendPoint {
  dateKey: string
  orderCount: number
  revenue: number
}

export interface DashboardOverview {
  stats: DashboardStats
  automationExceptionCount: number
  trends: DashboardTrendPoint[]
}

/** Data required by the current dashboard page. */
export function getDashboardOverview() {
  return request<DashboardOverview>({
    url: '/dashboard/overview',
    method: 'POST',
    data: {}
  })
}

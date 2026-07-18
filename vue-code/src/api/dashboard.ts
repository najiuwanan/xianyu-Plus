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

export interface DashboardAccountHealth {
  accountId: number
  accountName: string
  accountStatus: number
  accountStatusText: string
  cookieStatus?: number
  cookieStatusText: string
  websocketConnected: boolean
  automationRiskPaused: boolean
  automationRiskPauseReason?: string
  unreadMessageCount: number
  autoRateEnabled: boolean
  autoAskFlowerEnabled: boolean
  needsAttention: boolean
  healthText: string
}

export interface DashboardTrendPoint {
  dateKey: string
  orderCount: number
  revenue: number
}

export interface DashboardActivity {
  module?: string
  content: string
  status?: number
  createdAt?: number
  accountName?: string
}

export interface DashboardOverview {
  stats: DashboardStats
  accountIssueCount: number
  automationExceptionCount: number
  accountHealth: DashboardAccountHealth[]
  trends: DashboardTrendPoint[]
  activities: DashboardActivity[]
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

/** 运营首页需要的账号健康、待办、趋势与最近动态。 */
export function getDashboardOverview() {
  return request<DashboardOverview>({
    url: '/dashboard/overview',
    method: 'POST',
    data: {}
  })
}

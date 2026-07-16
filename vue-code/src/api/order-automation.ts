import { request } from '@/utils/request'

export type AutomationFilterStatus = 'ALL' | 'SUCCESS' | 'FAILED' | 'PENDING'
export type AutomationAction = 'RATE' | 'RED_FLOWER'

export interface OrderAutomationRecord {
  accountId: number
  accountName?: string
  orderId: string
  buyerUserName?: string
  goodsTitle?: string
  orderCreateTime?: string
  confirmState?: number
  rateEnabled: number
  rateStatus: number
  rateTime?: string
  rateError?: string
  redFlowerEnabled: number
  redFlowerStatus: number
  redFlowerTime?: string
  redFlowerError?: string
  redFlowerAttemptCount: number
  redFlowerNextRetryTime?: string
}

export interface OrderAutomationSummary {
  total: number
  completed: number
  failed: number
  pending: number
  rateSuccess: number
  redFlowerSuccess: number
}

export interface QueryOrderAutomationRequest {
  accountId?: number
  status?: AutomationFilterStatus
  page?: number
  pageSize?: number
}

export interface QueryOrderAutomationResponse {
  records: OrderAutomationRecord[]
  total: number
  page: number
  pageSize: number
  summary: OrderAutomationSummary
}

export interface RetryOrderAutomationRequest {
  accountId: number
  orderId: string
  action: AutomationAction
}

export interface RetryOrderAutomationResponse {
  success: boolean
  action: AutomationAction
  message: string
}

export function queryOrderAutomation(data: QueryOrderAutomationRequest) {
  return request<QueryOrderAutomationResponse>({
    url: '/order-automation/query',
    method: 'POST',
    data
  })
}

export function retryOrderAutomation(data: RetryOrderAutomationRequest) {
  return request<RetryOrderAutomationResponse>({
    url: '/order-automation/retry',
    method: 'POST',
    data
  })
}

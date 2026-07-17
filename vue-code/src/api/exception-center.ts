import { request } from '@/utils/request'

export type ExceptionType = 'ALL' | 'DELIVERY' | 'RATE' | 'RED_FLOWER' | 'POLISH'

export interface ExceptionCenterRecord {
  type: Exclude<ExceptionType, 'ALL'>
  recordId: string
  accountId: number
  accountName?: string
  orderId?: string
  xyGoodsId?: string
  goodsTitle?: string
  buyerUserName?: string
  reason?: string
  status?: string
  retryable: boolean
  occurredAt?: string
}

export interface ExceptionCenterSummary {
  total: number
  delivery: number
  rate: number
  redFlower: number
  polish: number
  reviewRequired: number
}

export interface ExceptionCenterQueryResponse {
  records: ExceptionCenterRecord[]
  total: number
  page: number
  pageSize: number
  summary: ExceptionCenterSummary
}

export function queryExceptionCenter(data: {
  accountId?: number
  type?: ExceptionType
  page?: number
  pageSize?: number
}) {
  return request<ExceptionCenterQueryResponse>({
    url: '/exception-center/query',
    method: 'POST',
    data
  })
}

export function retryExceptionCenter(data: {
  accountId: number
  type: Exclude<ExceptionType, 'ALL'>
  recordId: string
}) {
  return request<{ success: boolean; message: string }>({
    url: '/exception-center/retry',
    method: 'POST',
    data
  })
}

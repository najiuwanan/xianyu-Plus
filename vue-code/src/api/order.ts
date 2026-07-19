import { request } from '@/utils/request'

export interface DeliveryRecordQueryReq {
  xianyuAccountId?: number
  xyGoodsId?: string
  orderStatus?: number
  keyword?: string
  pageNum?: number
  pageSize?: number
}

export interface DeliveryRecordVO {
  id: number
  xianyuAccountId?: number
  xyGoodsId: string
  goodsTitle?: string
  buyerUserName?: string
  sid?: string
  content?: string
  state: number
  failReason?: string
  confirmState: number
  orderId?: string
  skuName?: string
  orderCreateTime?: string
  paySuccessTime?: string
  consignTime?: string
  totalPrice?: string
  buyNum?: number
  deliveryStatus?: string
  deliveryChannel?: string
  lastErrorMessage?: string
  tradeStatus?: string
  tradeStatusText?: string
  rateEnabled?: number
  rateStatus?: number
  rateError?: string
  redFlowerEnabled?: number
  redFlowerStatus?: number
  redFlowerError?: string
  createTime: string
}

export interface DeliveryRecordPageResult {
  records: DeliveryRecordVO[]
  total: number
  pageNum: number
  pageSize: number
}

export interface OrderTimelineEvent {
  type: string
  title: string
  description?: string
  status: 'SUCCESS' | 'PENDING' | 'FAILED' | 'WARNING' | 'SKIPPED' | 'INFO'
  occurredAt?: string
  retryable?: boolean
  retryAction?: 'DELIVERY' | 'RATE' | 'RED_FLOWER'
}

export interface OrderTimelineResult {
  orderId: string
  events: OrderTimelineEvent[]
}

export function queryDeliveryRecordList(data: DeliveryRecordQueryReq) {
  return request<DeliveryRecordPageResult>({
    url: '/items/autoDeliveryRecords',
    method: 'POST',
    data
  })
}

export function confirmShipment(data: { xianyuAccountId: number; orderId: string }) {
  return request<string>({
    url: '/order/confirmShipment',
    method: 'POST',
    data
  })
}

export function getOrderDetail(data: { xianyuAccountId: number; orderId: string; fromServer?: boolean }) {
  return request<string>({
    url: '/order/detail',
    method: 'POST',
    data
  })
}

export function getOrderTimeline(data: { xianyuAccountId: number; orderId: string }) {
  return request<OrderTimelineResult>({
    url: '/order/timeline',
    method: 'POST',
    data
  })
}

export function manualDelivery(data: { xianyuAccountId: number; orderId: string; content: string }) {
  return request<string>({
    url: '/autoDelivery/manual',
    method: 'POST',
    data
  })
}

/** 按商品当前的自动发货配置执行一次补发（卡券、固定内容、图片与模板规则均复用）。 */
export function triggerRuleDelivery(data: { xianyuAccountId: number; xyGoodsId: string; orderId: string; freshKami?: boolean }) {
  return request<string>({
    url: '/autoDelivery/trigger',
    method: 'POST',
    data: { ...data, needHumanLikeDelay: false }
  })
}

export interface OrderHistorySyncResult {
  soldCount: number
  refundCount: number
  syncedCount: number
  skippedCount: number
}

export function syncOrderHistory(xianyuAccountId: number) {
  return request<OrderHistorySyncResult>({
    url: '/order/syncHistory',
    method: 'POST',
    data: { xianyuAccountId }
  })
}

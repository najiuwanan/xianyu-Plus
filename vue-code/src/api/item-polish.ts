import { request } from '@/utils/request'

export interface ItemPolishConfig {
  id: number
  xianyuAccountId: number
  enabled: number
  scheduleTime: string
  lastScheduledDate?: string
  lastRunAt?: string
  lastRunTotal: number
  lastRunSuccess: number
  lastRunFailed: number
  lastRunMessage?: string
}

export interface ItemPolishRecord {
  id: number
  xianyuAccountId: number
  xyGoodsId: string
  goodsTitle?: string
  triggerType: 'MANUAL' | 'SCHEDULED'
  success: number
  message?: string
  createTime: string
}

export interface ItemPolishOverview {
  config: ItemPolishConfig
  records: ItemPolishRecord[]
  onSaleCount: number
  running: boolean
}

export function getItemPolishOverview(accountId: number, recordLimit = 30) {
  return request<ItemPolishOverview>({
    url: '/item-polish/overview',
    method: 'GET',
    params: { accountId, recordLimit }
  })
}

export function saveItemPolishConfig(data: { accountId: number; enabled: number; scheduleTime: string }) {
  return request<ItemPolishConfig>({
    url: '/item-polish/config',
    method: 'POST',
    data
  })
}

export function runItemPolish(accountId: number) {
  return request<{ started: boolean; message: string }>({
    url: '/item-polish/run',
    method: 'POST',
    data: { accountId }
  })
}

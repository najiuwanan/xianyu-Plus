import { request } from '@/utils/request'

export interface BuyerBlacklistEntry {
  id: number
  xianyuAccountId?: number | null
  accountNote?: string
  buyerUserId: string
  buyerUserName?: string
  reason?: string
  enabled: number
  createTime?: string
  updateTime?: string
}

export interface BuyerBlacklistPayload {
  id?: number
  xianyuAccountId?: number | null
  buyerUserId: string
  buyerUserName?: string
  reason?: string
  enabled?: number
}

export const getBuyerBlacklist = (data: { xianyuAccountId?: number; keyword?: string } = {}) =>
  request<BuyerBlacklistEntry[]>({ url: '/blacklist/list', method: 'POST', data })

export const saveBuyerBlacklist = (data: BuyerBlacklistPayload) =>
  request<BuyerBlacklistEntry>({ url: '/blacklist/save', method: 'POST', data })

export const deleteBuyerBlacklist = (id: number) =>
  request<string>({ url: '/blacklist/delete', method: 'POST', data: { id } })

export const checkBuyerBlacklist = (data: { xianyuAccountId: number; buyerUserId: string }) =>
  request<BuyerBlacklistEntry | null>({ url: '/blacklist/check', method: 'POST', data })

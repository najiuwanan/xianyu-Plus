import { request } from '@/utils/request'
import type { ApiResponse, Account } from '@/types'

// 获取账号列表
export function getAccountList() {
  return request<{ accounts: Account[]; total?: number }>({
    url: '/account/list',
    method: 'POST',
    data: {}
  })
}

// 添加账号
export function addAccount(data: Partial<Account>) {
  return request({
    url: '/account/add',
    method: 'POST',
    data
  })
}

// 更新账号
export function updateAccount(data: Partial<Account>) {
  return request({
    url: '/account/update',
    method: 'POST',
    data: {
      ...data,
      accountId: data.id
    }
  })
}

// 临时下线或恢复账号
export function setAccountEnabled(data: { accountId: number; enabled: boolean }) {
  return request<string>({
    url: '/account/setEnabled',
    method: 'POST',
    data
  })
}

export function resumeAccountAutomation(data: { accountId: number }) {
  return request<string>({
    url: '/account/resumeAutomation',
    method: 'POST',
    data
  })
}

// 删除账号
export function deleteAccount(data: { id: number }) {
  return request({
    url: '/account/delete',
    method: 'POST',
    data: {
      accountId: data.id
    }
  })
}

// 手动添加账号
export function manualAddAccount(data: { accountNote: string; cookie: string }) {
  return request({
    url: '/account/manualAdd',
    method: 'POST',
    data
  })
}

// 刷新并缓存账号头像；接口不可用时仍保留前端文字头像
export function refreshAccountAvatar(data: { accountId: number }) {
  return request<string>({
    url: '/account/refreshAvatar',
    method: 'POST',
    data
  })
}

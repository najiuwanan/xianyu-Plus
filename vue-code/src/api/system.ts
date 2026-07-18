import { request } from '@/utils/request'

export interface SystemUpdateStatus {
  versionTracked: boolean
  updateAvailable: boolean
  message: string
  currentCommit?: string
  latestCommit?: string
  latestMessage?: string
  updateUrl?: string
  checkedAt?: string
}

/** 获取当前用户信息 */
export function getCurrentUser() {
  return request<{ username: string; lastLoginTime: string }>({
    url: '/system/currentUser',
    method: 'post'
  })
}

/** 修改密码 */
export function changePassword(data: { oldPassword: string; newPassword: string; confirmPassword: string }) {
  return request<null>({
    url: '/system/changePassword',
    method: 'post',
    data
  })
}

/** 检查当前容器相对于 GitHub main 分支是否有更新。 */
export function getSystemUpdateStatus(refresh = false) {
  return request<SystemUpdateStatus>({
    url: `/system/update-status${refresh ? '?refresh=true' : ''}`,
    method: 'get'
  })
}

/** 获取模型列表 */
export function fetchModels(data: { apiKey: string; baseUrl: string }) {
  return request<{ models: string[] }>({
    url: '/system/fetchModels',
    method: 'post',
    data
  })
}

/** 测试 AI 连接 */
export function testAi(data: { apiKey: string; baseUrl: string; model: string }) {
  return request<string>({
    url: '/system/testAi',
    method: 'post',
    data
  })
}

/** 测试 Embedding 连接 */
export function testEmbedding(data: { apiKey: string; baseUrl: string; model: string }) {
  return request<string>({
    url: '/system/testEmbedding',
    method: 'post',
    data
  })
}

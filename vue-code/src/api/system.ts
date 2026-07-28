import { request } from '@/utils/request'

export interface SystemUpdateStatus {
  versionTracked: boolean
  updateAvailable: boolean
  message: string
  currentCommit?: string
  latestCommit?: string
  currentVersion?: string
  latestVersion?: string
  latestMessage?: string
  updateHighlights?: string[]
  updateUrl?: string
  checkedAt?: string
}
export interface OnlineUpdateStatus {
  available: boolean
  active: boolean
  canRetry: boolean
  taskId?: string
  version?: string
  status: 'IDLE' | 'REQUESTED' | 'CHECKING' | 'DOWNLOADING' | 'VERIFYING' | 'DRAINING' | 'INSTALLING' | 'RESTARTING' | 'HEALTH_CHECKING' | 'SUCCESS' | 'FAILED'
  progress: number
  message?: string
  downloadedBytes: number
  totalBytes: number
  requestedAt?: string
  updatedAt?: string
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
export function getOnlineUpdateStatus() {
  return request<OnlineUpdateStatus>({
    url: '/system/online-update-status',
    method: 'get'
  })
}

export function requestOnlineUpdate() {
  return request<OnlineUpdateStatus>({
    url: '/system/online-update',
    method: 'post'
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

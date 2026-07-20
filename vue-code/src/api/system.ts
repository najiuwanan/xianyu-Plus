import { getAuthToken, request } from '@/utils/request'

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

export interface OnlineUpdateExecution {
  enabled: boolean
  requestId?: string
  state: 'IDLE' | 'QUEUED' | 'RUNNING' | 'RESTARTING' | 'SUCCEEDED' | 'FAILED'
  stage: string
  progress: number
  message: string
  estimatedDowntimeSeconds: number
  startedAt?: string
  updatedAt?: string
  targetCommit?: string
  logs: string[]
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

/** 触发固定流程的在线更新。 */
export function startOnlineUpdate() {
  return request<OnlineUpdateExecution>({
    url: '/system/online-update/start',
    method: 'post'
  })
}

/**
 * 静默轮询更新进度。应用重启时网络失败是预期状态，不能触发全局错误提示。
 */
export async function pollOnlineUpdateStatus(): Promise<OnlineUpdateExecution> {
  const token = getAuthToken()
  const response = await fetch('/api/system/online-update/status', {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    signal: AbortSignal.timeout(8000)
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const payload = await response.json()
  if (payload.code !== 0 && payload.code !== 200) throw new Error(payload.msg || '读取更新进度失败')
  return payload.data as OnlineUpdateExecution
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

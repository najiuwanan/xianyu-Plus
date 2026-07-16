import { request } from '@/utils/request'

export interface RuntimeLogTail {
  lines: string[]
  available: boolean
  message: string
}

export interface LogRetentionConfig {
  days: number
  configured: boolean
}

export interface LogCleanupResult {
  retentionDays: number
  fileLogDirectoriesDeleted: number
  operationLogsDeleted: number
}

/** Read the newest part of today's application log without changing it. */
export function getRuntimeLogTail(lines = 200) {
  return request<RuntimeLogTail>({
    url: '/runtime-log/tail',
    method: 'GET',
    params: { lines }
  })
}

export function getLogRetention() {
  return request<LogRetentionConfig>({
    url: '/runtime-log/retention',
    method: 'GET'
  })
}

export function saveLogRetention(days: number) {
  return request<LogCleanupResult>({
    url: '/runtime-log/retention',
    method: 'POST',
    data: { days }
  })
}

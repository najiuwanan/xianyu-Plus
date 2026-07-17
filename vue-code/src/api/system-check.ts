import { request } from '@/utils/request'

export type SystemCheckStatus = 'PASS' | 'WARN' | 'FAIL'

export interface SystemCheckItem {
  id: string
  title: string
  status: SystemCheckStatus
  summary: string
  detail: string
  path: string
}

export interface SystemCheckOverview {
  generatedAt: string
  passCount: number
  warnCount: number
  failCount: number
  items: SystemCheckItem[]
}

export function getSystemCheckOverview() {
  return request<SystemCheckOverview>({
    url: '/system-check/overview',
    method: 'POST'
  })
}

import { request } from '@/utils/request'

export interface RuntimeLogTail {
  lines: string[]
  available: boolean
  message: string
}

/** Read the newest part of today's application log without changing it. */
export function getRuntimeLogTail(lines = 200) {
  return request<RuntimeLogTail>({
    url: '/runtime-log/tail',
    method: 'GET',
    params: { lines }
  })
}

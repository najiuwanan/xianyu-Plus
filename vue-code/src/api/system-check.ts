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

export interface PublishCapabilityProperty {
  propertyId: string
  propertyName: string
  optionCount: number
  optionExamples: string[]
}

export interface PublishCapabilityResult {
  passed: boolean
  status: SystemCheckStatus
  summary: string
  detail: string
  categoryId: string
  categoryName: string
  channelCategoryId: string
  taobaoCategoryId: string
  categoryApiReady: boolean
  dynamicPropertiesReady: boolean
  locationApiReady: boolean
  realPublishTested: boolean
  propertyCount: number
  properties: PublishCapabilityProperty[]
}

export function getSystemCheckOverview() {
  return request<SystemCheckOverview>({
    url: '/system-check/overview',
    method: 'POST'
  })
}

export function checkPublishCapability(data: { accountId: number; title: string }) {
  return request<PublishCapabilityResult>({
    url: '/system-check/publish-capability',
    method: 'POST',
    data
  })
}

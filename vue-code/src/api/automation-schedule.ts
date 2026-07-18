import { request } from '@/utils/request'

export interface AutomationScheduleTask {
  taskKey: string
  name: string
  description: string
  intervalSeconds: number
  defaultIntervalSeconds: number
  minIntervalSeconds: number
}

export function getAutomationScheduleTasks() {
  return request<AutomationScheduleTask[]>({
    url: '/automation-schedule/list',
    method: 'POST',
    data: {}
  })
}

export function saveAutomationScheduleTasks(tasks: Array<Pick<AutomationScheduleTask, 'taskKey' | 'intervalSeconds'>>) {
  return request<AutomationScheduleTask[]>({
    url: '/automation-schedule/save',
    method: 'POST',
    data: { tasks }
  })
}

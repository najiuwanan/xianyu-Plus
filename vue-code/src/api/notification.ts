import { request } from '@/utils/request'

export interface NotificationChannel {
  id?: number
  type: string
  name: string
  config: string
  status: number
  createdAt?: string
  updatedAt?: string
}

/** 获取通知渠道列表 */
export function getNotificationChannels() {
  return request<NotificationChannel[]>({
    url: '/notification/list',
    method: 'get'
  })
}

/** 保存或更新通知渠道 */
export function saveNotificationChannel(data: NotificationChannel) {
  return request<boolean>({
    url: '/notification/save',
    method: 'post',
    data
  })
}

/** 删除通知渠道 */
export function deleteNotificationChannel(id: number) {
  return request<boolean>({
    url: `/notification/delete/${id}`,
    method: 'delete'
  })
}

/** 测试通知渠道 */
export function testNotificationChannel(data: NotificationChannel) {
  return request<string>({
    url: '/notification/test',
    method: 'post',
    data
  })
}

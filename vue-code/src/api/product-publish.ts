import { request } from '@/utils/request'

export interface ProductPublishImage {
  url: string
  width: number
  height: number
}

export interface ProductPublishProperty {
  propertyId: string
  valueKey: string
}

export interface ProductPublishRequest {
  accountId: number
  requestId: string
  title: string
  description: string
  price: number
  originalPrice?: number
  quantity: number
  deliveryMode: 'FREE' | 'FLAT' | 'NONE' | 'SELF_PICKUP'
  postFee?: number
  acknowledged: boolean
  confirmation: string
  images: ProductPublishImage[]
  properties: ProductPublishProperty[]
}

export interface ProductPublishResult {
  success: boolean
  itemId: string
  message: string
}

export function publishProduct(data: ProductPublishRequest) {
  return request<ProductPublishResult>({ url: '/product-publish', method: 'POST', data })
}

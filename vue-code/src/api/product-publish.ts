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
  address: {
    locationKey: string
    lookupLongitude?: number
    lookupLatitude?: number
    customPoiName?: string
  }
  images: ProductPublishImage[]
  properties: ProductPublishProperty[]
}

export interface ProductPublishLocation {
  key: string
  source: 'SELECTED' | 'COMMON' | 'NEARBY'
  selected: boolean
  province: string
  city: string
  district: string
  divisionId: string
  poiId: string
  poiName: string
  longitude: string
  latitude: string
  displayName: string
}

export interface ProductPublishResult {
  success: boolean
  itemId: string
  message: string
}

export function publishProduct(data: ProductPublishRequest) {
  return request<ProductPublishResult>({ url: '/product-publish', method: 'POST', data })
}

export function getPublishLocations(data: { accountId: number; longitude?: number; latitude?: number }) {
  return request<ProductPublishLocation[]>({ url: '/product-publish/locations', method: 'POST', data })
}

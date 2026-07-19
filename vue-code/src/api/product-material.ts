import { request } from '@/utils/request'
import type { ProductPublishImage } from './product-publish'

export interface ProductMaterial {
  id: number
  materialName: string
  title: string
  description: string
  price: number
  originalPrice?: number
  quantity: number
  deliveryMode: 'FREE' | 'FLAT' | 'NONE' | 'SELF_PICKUP'
  postFee?: number
  images: ProductPublishImage[]
  createTime: string
  updateTime: string
}

export type ProductMaterialSave = Omit<ProductMaterial, 'id' | 'createTime' | 'updateTime'> & { id?: number }

export interface CopywritingRequest {
  mode: 'GENERATE' | 'POLISH' | 'VARIATION'
  title: string
  description?: string
  style: 'NATURAL' | 'CONCISE' | 'DETAILED' | 'PROMOTIONAL'
  facts?: string
  price?: number
  variationIndex?: number
  images: ProductPublishImage[]
}

export function listProductMaterials(keyword = '') {
  return request<ProductMaterial[]>({ url: '/product-materials/list', method: 'POST', data: { keyword } })
}

export function getProductMaterial(id: number) {
  return request<ProductMaterial>({ url: '/product-materials/get', method: 'POST', data: { id } })
}

export function saveProductMaterial(data: ProductMaterialSave) {
  return request<ProductMaterial>({ url: '/product-materials/save', method: 'POST', data })
}

export function deleteProductMaterial(id: number) {
  return request<string>({ url: '/product-materials/delete', method: 'POST', data: { id } })
}

export function generateProductCopywriting(data: CopywritingRequest) {
  return request<{ description: string; imageUsed: boolean }>({ url: '/product-materials/ai-copywriting', method: 'POST', data, timeout: 120000 })
}

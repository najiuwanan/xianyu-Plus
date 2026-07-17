import { request } from '@/utils/request';

export interface KamiConfig {
  id: number;
  xianyuAccountId?: number | null;
  aliasName: string;
  /** 1=本地库存，2=外部 API，3=固定内容 */
  sourceType?: number;
  fixedContent?: string;
  relatedGoodsCount?: number;
  apiUrl?: string;
  apiMethod?: 'GET' | 'POST' | string;
  apiHeaders?: string;
  apiRequestTemplate?: string;
  apiResultPath?: string;
  apiTimeoutSeconds?: number;
  alertEnabled?: number;
  alertThresholdType?: number;
  alertThresholdValue?: number;
  alertEmail?: string;
  totalCount: number;
  usedCount: number;
  availableCount: number;
  createTime: string;
  updateTime: string;
}

export interface KamiItem {
  id: number;
  kamiConfigId: number;
  kamiContent: string;
  status: number;
  orderId: string | null;
  usedTime: string | null;
  sortOrder: number;
  createTime: string;
}

export interface SaveKamiConfigReq {
  id?: number;
  xianyuAccountId?: number | null;
  aliasName?: string;
  sourceType?: number;
  fixedContent?: string;
  apiUrl?: string;
  apiMethod?: 'GET' | 'POST';
  apiHeaders?: string;
  apiRequestTemplate?: string;
  apiResultPath?: string;
  apiTimeoutSeconds?: number;
  alertEnabled?: number;
  alertThresholdType?: number;
  alertThresholdValue?: number;
  alertEmail?: string;
}

export interface QueryKamiItemsReq {
  kamiConfigId: number;
  status?: number;
  keyword?: string;
}

export function saveKamiConfig(data: SaveKamiConfigReq) {
  return request<KamiConfig>({
    url: '/kami-config/save',
    method: 'POST',
    data
  });
}

export function getKamiConfigs() {
  return request<KamiConfig[]>({
    url: '/kami-config/list',
    method: 'POST'
  });
}

export function getKamiConfigById(id: number) {
  return request<KamiConfig>({
    url: '/kami-config/detail',
    method: 'POST',
    params: { id }
  });
}

export function deleteKamiConfig(id: number) {
  return request({
    url: '/kami-config/delete',
    method: 'POST',
    params: { id }
  });
}

export interface KamiApiTestReq {
  apiUrl: string;
  apiMethod?: 'GET' | 'POST';
  apiHeaders?: string;
  apiRequestTemplate?: string;
  apiResultPath?: string;
  apiTimeoutSeconds?: number;
}

export interface KamiApiTestResult {
  statusCode: number;
  content: string;
  message: string;
}

export function testKamiApi(data: KamiApiTestReq) {
  return request<KamiApiTestResult>({
    url: '/kami-config/test-api',
    method: 'POST',
    data
  });
}

export interface KamiRelatedGoods {
  xianyuAccountId: number;
  xianyuGoodsId: number;
  xyGoodsId: string;
  accountNote?: string;
  goodsTitle?: string;
  coverPic?: string;
  soldPrice?: string;
  status?: number;
  associated?: boolean;
  willReplace?: boolean;
}

export function getKamiRelatedGoods(kamiConfigId: number) {
  return request<KamiRelatedGoods[]>({
    url: '/kami-config/related-goods/list',
    method: 'POST',
    params: { kamiConfigId }
  });
}

export function saveKamiRelatedGoods(data: { kamiConfigId: number; goods: KamiRelatedGoods[] }) {
  return request<number>({
    url: '/kami-config/related-goods/save',
    method: 'POST',
    data
  });
}

export function addKamiItem(data: { kamiConfigId: number; kamiContent: string }) {
  return request<KamiItem>({
    url: '/kami-config/item/add',
    method: 'POST',
    data
  });
}

export function batchImportKamiItems(data: { kamiConfigId: number; kamiContents: string }) {
  return request<number>({
    url: '/kami-config/item/batchImport',
    method: 'POST',
    data
  });
}

export function getKamiItemsByConfigId(kamiConfigId: number) {
  return request<KamiItem[]>({
    url: '/kami-config/item/list',
    method: 'POST',
    params: { kamiConfigId }
  });
}

export function queryKamiItems(data: QueryKamiItemsReq) {
  return request<KamiItem[]>({
    url: '/kami-config/item/query',
    method: 'POST',
    data
  });
}

export function deleteKamiItem(id: number) {
  return request({
    url: '/kami-config/item/delete',
    method: 'POST',
    params: { id }
  });
}

export function resetKamiItem(id: number) {
  return request({
    url: '/kami-config/item/reset',
    method: 'POST',
    params: { id }
  });
}

export function exportKamiItems(data: { kamiConfigId: number; includeUnused: boolean; includeUsed: boolean }) {
  return request<KamiItem[]>({
    url: '/kami-config/item/export',
    method: 'POST',
    data
  });
}

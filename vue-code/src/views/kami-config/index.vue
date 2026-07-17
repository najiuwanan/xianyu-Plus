<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, inject } from 'vue'
import { toast } from '@/utils/toast'
import { showConfirm } from '@/utils/confirm'
import {
  getKamiConfigs,
  saveKamiConfig,
  deleteKamiConfig,
  queryKamiItems,
  addKamiItem,
  batchImportKamiItems,
  deleteKamiItem,
  resetKamiItem,
  exportKamiItems,
  testKamiApi,
  getKamiRelatedGoods,
  saveKamiRelatedGoods,
  type KamiConfig,
  type KamiItem,
  type KamiRelatedGoods
} from '@/api/kami-config'

const kamiConfigs = ref<KamiConfig[]>([])
const configLoading = ref(false)

const selectedConfigId = ref<number | null>(null)
const kamiItems = ref<KamiItem[]>([])
const itemsLoading = ref(false)

const showCreateDialog = ref(false)
const createForm = ref({
  aliasName: '',
  sourceType: 1,
  apiUrl: '',
  apiMethod: 'POST' as 'GET' | 'POST',
  apiHeaders: '',
  apiRequestTemplate: '{\n  "orderId": "{{orderId}}",\n  "goodsId": "{{goodsId}}",\n  "quantity": "{{quantity}}"\n}',
  apiResultPath: '',
  apiTimeoutSeconds: 10
})
const createLoading = ref(false)

const showApiDialog = ref(false)
const apiSaving = ref(false)
const apiTesting = ref(false)
const apiTestResult = ref('')
const apiForm = ref({
  sourceType: 2,
  fixedContent: '',
  apiUrl: '',
  apiMethod: 'POST' as 'GET' | 'POST',
  apiHeaders: '',
  apiRequestTemplate: '{\n  "orderId": "{{orderId}}",\n  "goodsId": "{{goodsId}}",\n  "quantity": "{{quantity}}"\n}',
  apiResultPath: '',
  apiTimeoutSeconds: 10
})

const showRelatedGoodsDialog = ref(false)
const relatedGoods = ref<KamiRelatedGoods[]>([])
const relatedGoodsLoading = ref(false)
const relatedGoodsSaving = ref(false)
const relatedGoodsKeyword = ref('')
const selectedRelatedGoodsKeys = ref<string[]>([])

const showImportDialog = ref(false)
const importContent = ref('')
const importLoading = ref(false)

const showAddDialog = ref(false)
const addContent = ref('')
const addLoading = ref(false)

const showAlertDialog = ref(false)
const alertForm = ref({
  alertEnabled: 0,
  alertThresholdType: 1,
  alertThresholdValue: 10,
  alertEmail: ''
})
const alertLoading = ref(false)

const showExportDialog = ref(false)
const exportStatus = ref<{ unused: boolean; used: boolean }>({ unused: true, used: true })

const isMobile = ref(false)
const rulesExpanded = ref(false)

const filterStatus = ref<number | undefined>(undefined)
const filterKeyword = ref('')

const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
}

// 卡券库不再属于单个账号，进入页面时清除其他页面遗留的账号筛选器。
const setHeaderContent = inject<(content: any) => void>('setHeaderContent')

const selectedConfig = computed(() => {
  return kamiConfigs.value.find(c => c.id === selectedConfigId.value)
})

const isApiSource = computed(() => selectedConfig.value?.sourceType === 2)
const isFixedSource = computed(() => selectedConfig.value?.sourceType === 3)
const isLocalSource = computed(() => selectedConfig.value?.sourceType !== 2 && selectedConfig.value?.sourceType !== 3)

const sourceLabel = (sourceType?: number) => {
  if (sourceType === 2) return '外部 API'
  if (sourceType === 3) return '固定内容'
  return '本地库存'
}

const relatedGoodsKey = (goods: Pick<KamiRelatedGoods, 'xianyuAccountId' | 'xyGoodsId'>) =>
  `${goods.xianyuAccountId}:${goods.xyGoodsId}`

const filteredRelatedGoods = computed(() => {
  const keyword = relatedGoodsKeyword.value.trim().toLowerCase()
  if (!keyword) return relatedGoods.value
  return relatedGoods.value.filter(goods =>
    [goods.goodsTitle, goods.xyGoodsId, goods.accountNote].some(value => value?.toLowerCase().includes(keyword))
  )
})

const selectedRelatedGoods = computed(() => {
  const selected = new Set(selectedRelatedGoodsKeys.value)
  return relatedGoods.value.filter(goods => selected.has(relatedGoodsKey(goods)))
})

const contentPreview = (content?: string) => {
  const normalized = (content || '').replace(/\s+/g, ' ').trim()
  return normalized.length > 48 ? `${normalized.slice(0, 48)}…` : normalized || '尚未配置内容'
}

const loadKamiConfigs = async () => {
  configLoading.value = true
  try {
    const res = await getKamiConfigs()
    if (res.code === 200) {
      kamiConfigs.value = res.data || []
      if (kamiConfigs.value.length > 0 && !selectedConfigId.value && !isMobile.value) {
        selectedConfigId.value = kamiConfigs.value[0]!.id
        if (kamiConfigs.value[0]!.sourceType === 1 || !kamiConfigs.value[0]!.sourceType) loadKamiItems()
      } else if (kamiConfigs.value.length === 0) {
        selectedConfigId.value = null
        kamiItems.value = []
      }
    }
  } catch (e) {
    console.error('加载卡券库失败', e)
  } finally {
    configLoading.value = false
  }
}

const loadKamiItems = async () => {
  if (!selectedConfigId.value || !isLocalSource.value) {
    kamiItems.value = []
    return
  }
  itemsLoading.value = true
  try {
    const res = await queryKamiItems({
      kamiConfigId: selectedConfigId.value,
      status: filterStatus.value,
      keyword: filterKeyword.value || undefined
    })
    if (res.code === 200) {
      kamiItems.value = res.data || []
    }
  } catch (e) {
    console.error('加载卡券列表失败', e)
  } finally {
    itemsLoading.value = false
  }
}

const selectConfig = (config: KamiConfig) => {
  selectedConfigId.value = config.id
  filterStatus.value = undefined
  filterKeyword.value = ''
  if (config.sourceType !== 1) {
    kamiItems.value = []
  } else {
    loadKamiItems()
  }
}

const handleCreate = async () => {
  createLoading.value = true
  try {
    const requestedSource = createForm.value.sourceType
    const deferredSource = requestedSource !== 1
    const res = await saveKamiConfig({
      aliasName: createForm.value.aliasName || '未命名',
      // 固定内容和外部 API 都需在下一步填写必填配置，因此先建立空的本地库。
      sourceType: deferredSource ? 1 : createForm.value.sourceType
    })
    if (res.code === 200) {
      toast.success('创建成功')
      showCreateDialog.value = false
      createForm.value = {
        aliasName: '', sourceType: 1, apiUrl: '', apiMethod: 'POST', apiHeaders: '',
        apiRequestTemplate: '{\n  "orderId": "{{orderId}}",\n  "goodsId": "{{goodsId}}",\n  "quantity": "{{quantity}}"\n}',
        apiResultPath: '', apiTimeoutSeconds: 10
      }
      await loadKamiConfigs()
      if (res.data?.id) {
        selectedConfigId.value = res.data.id
        if (deferredSource) {
          apiForm.value = {
            sourceType: requestedSource,
            fixedContent: '',
            apiUrl: '',
            apiMethod: 'POST',
            apiHeaders: '',
            apiRequestTemplate: '{\n  "orderId": "{{orderId}}",\n  "goodsId": "{{goodsId}}",\n  "quantity": "{{quantity}}"\n}',
            apiResultPath: '',
            apiTimeoutSeconds: 10
          }
          showApiDialog.value = true
        } else {
          loadKamiItems()
        }
      }
    } else {
      toast.error(res.msg || '创建失败')
    }
  } catch (e) {
    toast.error('创建失败')
  } finally {
    createLoading.value = false
  }
}

const openApiDialog = () => {
  if (!selectedConfig.value) return
  const config = selectedConfig.value
  apiForm.value = {
    sourceType: config.sourceType || 1,
    fixedContent: config.fixedContent || '',
    apiUrl: config.apiUrl || '',
    apiMethod: (config.apiMethod === 'GET' ? 'GET' : 'POST'),
    apiHeaders: config.apiHeaders || '',
    apiRequestTemplate: config.apiRequestTemplate || '{\n  "orderId": "{{orderId}}",\n  "goodsId": "{{goodsId}}",\n  "quantity": "{{quantity}}"\n}',
    apiResultPath: config.apiResultPath || '',
    apiTimeoutSeconds: config.apiTimeoutSeconds || 10
  }
  apiTestResult.value = ''
  showApiDialog.value = true
}

const handleTestApi = async () => {
  if (apiForm.value.sourceType !== 2) {
    toast.warning('请先选择“外部 API 自动取卡”')
    return
  }
  apiTesting.value = true
  apiTestResult.value = ''
  try {
    const res = await testKamiApi(apiForm.value)
    if (res.code === 200) {
      apiTestResult.value = `成功（HTTP ${res.data?.statusCode || 200}）：${res.data?.content || '未返回内容'}`
      toast.success('接口测试成功')
    } else {
      apiTestResult.value = `失败：${res.msg || '接口测试失败'}`
      toast.error(res.msg || '接口测试失败')
    }
  } catch (e) {
    apiTestResult.value = '失败：请求未完成，请检查接口地址和网络。'
    toast.error('接口测试失败')
  } finally {
    apiTesting.value = false
  }
}

const handleSaveApi = async () => {
  if (!selectedConfig.value) return
  apiSaving.value = true
  try {
    const res = await saveKamiConfig({
      id: selectedConfig.value.id,
      aliasName: selectedConfig.value.aliasName,
      sourceType: apiForm.value.sourceType,
      ...(apiForm.value.sourceType === 3 ? {
        fixedContent: apiForm.value.fixedContent
      } : {}),
      ...(apiForm.value.sourceType === 2 ? {
        apiUrl: apiForm.value.apiUrl,
        apiMethod: apiForm.value.apiMethod,
        apiHeaders: apiForm.value.apiHeaders,
        apiRequestTemplate: apiForm.value.apiRequestTemplate,
        apiResultPath: apiForm.value.apiResultPath,
        apiTimeoutSeconds: apiForm.value.apiTimeoutSeconds
      } : {})
    })
    if (res.code === 200) {
      toast.success(`${sourceLabel(apiForm.value.sourceType)}卡券配置已保存`)
      showApiDialog.value = false
      kamiItems.value = []
      await loadKamiConfigs()
      if (apiForm.value.sourceType === 1) loadKamiItems()
    } else {
      toast.error(res.msg || '保存失败')
    }
  } catch (e) {
    toast.error('保存失败')
  } finally {
    apiSaving.value = false
  }
}

const openRelatedGoodsDialog = async () => {
  if (!selectedConfig.value) return
  showRelatedGoodsDialog.value = true
  relatedGoodsLoading.value = true
  relatedGoodsKeyword.value = ''
  try {
    const res = await getKamiRelatedGoods(selectedConfig.value.id)
    if (res.code === 200) {
      relatedGoods.value = res.data || []
      selectedRelatedGoodsKeys.value = relatedGoods.value
        .filter(goods => goods.associated)
        .map(goods => relatedGoodsKey(goods))
    } else {
      toast.error(res.msg || '加载关联商品失败')
    }
  } catch (e) {
    toast.error('加载关联商品失败')
  } finally {
    relatedGoodsLoading.value = false
  }
}

const removeRelatedGoods = (goods: KamiRelatedGoods) => {
  const key = relatedGoodsKey(goods)
  selectedRelatedGoodsKeys.value = selectedRelatedGoodsKeys.value.filter(item => item !== key)
}

const handleSaveRelatedGoods = async () => {
  if (!selectedConfig.value) return
  relatedGoodsSaving.value = true
  try {
    const res = await saveKamiRelatedGoods({
      kamiConfigId: selectedConfig.value.id,
      goods: selectedRelatedGoods.value
    })
    if (res.code === 200) {
      toast.success(res.msg || '关联商品已保存')
      showRelatedGoodsDialog.value = false
      await loadKamiConfigs()
    } else {
      toast.error(res.msg || '保存关联商品失败')
    }
  } catch (e) {
    toast.error('保存关联商品失败')
  } finally {
    relatedGoodsSaving.value = false
  }
}

const handleDeleteConfig = async (config: KamiConfig) => {
  try {
    await showConfirm(
      `确定删除卡券库「${config.aliasName || config.id}」及其所有卡券？`,
      '删除确认'
    )
    const res = await deleteKamiConfig(config.id)
    if (res.code === 200) {
      toast.success('删除成功')
      if (selectedConfigId.value === config.id) {
        selectedConfigId.value = null
        kamiItems.value = []
      }
      loadKamiConfigs()
    } else {
      toast.error(res.msg || '删除失败')
    }
  } catch {}
}

const handleAddKami = async () => {
  if (!addContent.value.trim()) {
    toast.warning('请输入卡券内容')
    return
  }
  addLoading.value = true
  try {
    const res = await addKamiItem({
      kamiConfigId: selectedConfigId.value!,
      kamiContent: addContent.value.trim()
    })
    if (res.code === 200) {
      toast.success('添加成功')
      showAddDialog.value = false
      addContent.value = ''
      loadKamiItems()
      loadKamiConfigs()
    } else {
      toast.error(res.msg || '添加失败')
    }
  } catch (e) {
    toast.error('添加失败')
  } finally {
    addLoading.value = false
  }
}

const handleBatchImport = async () => {
  if (!importContent.value.trim()) {
    toast.warning('请输入卡券内容')
    return
  }
  importLoading.value = true
  try {
    const res = await batchImportKamiItems({
      kamiConfigId: selectedConfigId.value!,
      kamiContents: importContent.value
    })
    if (res.code === 200) {
      toast.success(res.msg || '导入成功')
      showImportDialog.value = false
      importContent.value = ''
      loadKamiItems()
      loadKamiConfigs()
    } else {
      toast.error(res.msg || '导入失败')
    }
  } catch (e) {
    toast.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

const handleDeleteItem = async (item: KamiItem) => {
  try {
    await showConfirm('确定删除该卡券？', '删除确认')
    const res = await deleteKamiItem(item.id)
    if (res.code === 200) {
      toast.success('删除成功')
      loadKamiItems()
      loadKamiConfigs()
    } else {
      toast.error(res.msg || '删除失败')
    }
  } catch {}
}

const handleResetItem = async (item: KamiItem) => {
  try {
    await showConfirm('确定重置该卡券为未使用状态？', '重置确认')
    const res = await resetKamiItem(item.id)
    if (res.code === 200) {
      toast.success('重置成功')
      loadKamiItems()
      loadKamiConfigs()
    } else {
      toast.error(res.msg || '重置失败')
    }
  } catch {}
}

const handleFilterChange = () => {
  loadKamiItems()
}

const openAlertDialog = () => {
  if (!selectedConfig.value) return
  alertForm.value = {
    alertEnabled: selectedConfig.value.alertEnabled || 0,
    alertThresholdType: selectedConfig.value.alertThresholdType || 1,
    alertThresholdValue: selectedConfig.value.alertThresholdValue || 10,
    alertEmail: selectedConfig.value.alertEmail || ''
  }
  showAlertDialog.value = true
}

const handleSaveAlert = async () => {
  if (!selectedConfigId.value) return
  alertLoading.value = true
  try {
    const res = await saveKamiConfig({
      id: selectedConfigId.value,
      aliasName: selectedConfig.value?.aliasName,
      alertEnabled: alertForm.value.alertEnabled,
      alertThresholdType: alertForm.value.alertThresholdType,
      alertThresholdValue: alertForm.value.alertThresholdValue,
      alertEmail: alertForm.value.alertEmail
    })
    if (res.code === 200) {
      toast.success('设置保存成功')
      showAlertDialog.value = false
      loadKamiConfigs()
    } else {
      toast.error(res.msg || '保存失败')
    }
  } catch (e) {
    toast.error('保存失败')
  } finally {
    alertLoading.value = false
  }
}

const openExportDialog = () => {
  exportStatus.value = { unused: true, used: true }
  showExportDialog.value = true
}

const handleExport = async () => {
  if (!selectedConfigId.value) return
  if (!exportStatus.value.unused && !exportStatus.value.used) {
    toast.warning('请至少选择一种状态')
    return
  }

  try {
    const res = await exportKamiItems({
      kamiConfigId: selectedConfigId.value,
      includeUnused: exportStatus.value.unused,
      includeUsed: exportStatus.value.used
    })
    const allItems = res.data || []

    if (allItems.length === 0) {
      toast.warning('没有可导出的数据')
      return
    }

    const configName = selectedConfig.value?.aliasName || `配置${selectedConfigId.value}`
    const timestamp = new Date().toISOString().slice(0, 19).replace(/[:-]/g, '').replace('T', '_')

    const header = '序号\t卡券内容\t状态\t订单ID\t使用时间\t添加时间\n'
    const rows = allItems.map(item =>
      `${item.sortOrder}\t${item.kamiContent}\t${item.status === 0 ? '未使用' : '已使用'}\t${item.orderId || ''}\t${item.usedTime || ''}\t${item.createTime}`
    ).join('\n')
    const content = header + rows
    const blob = new Blob(['\ufeff' + content], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${configName}_${timestamp}.txt`
    a.click()
    URL.revokeObjectURL(url)
    toast.success(`已导出 ${allItems.length} 条数据`)
    showExportDialog.value = false
  } catch (e) {
    toast.error('导出失败')
  }
}

onMounted(() => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  if (setHeaderContent) setHeaderContent(null)
  loadKamiConfigs()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
})
</script>

<template>
  <div class="kami-page">

    <!-- ===== 手机端 ===== -->
    <template v-if="isMobile">

      <!-- 配置列表视图 -->
      <div v-if="!selectedConfigId" class="kami-mobile">
        <header class="kami-mobile__header">
          <div class="kami-mobile__header-top">
            <h1 class="kami-page__title">卡券管理</h1>
            <button class="btn-primary btn-sm" @click="showCreateDialog = true">
              新建
            </button>
          </div>
        </header>

        <div class="kami-mobile__list">
          <div v-if="configLoading" class="kami-page__empty">加载中...</div>
          <div v-else-if="kamiConfigs.length === 0" class="kami-page__empty">暂无卡券库，点击右上角新建</div>
          <div
            v-for="config in kamiConfigs"
            :key="config.id"
            class="config-card"
            @click="selectConfig(config)"
          >
            <div class="config-card__name">{{ config.aliasName || `卡券库#${config.id}` }}</div>
            <div class="config-card__stats">
              <template v-if="config.sourceType === 2">
                <span class="tag tag--info">外部 API</span>
                <span class="config-card__stat">按订单实时取卡</span>
              </template>
              <template v-else-if="config.sourceType === 3">
                <span class="tag tag--fixed">固定内容</span>
                <span class="config-card__stat">{{ contentPreview(config.fixedContent) }}</span>
              </template>
              <template v-else>
                <span class="config-card__stat">总量 {{ config.totalCount }}</span>
                <span class="config-card__stat used">已用 {{ config.usedCount }}</span>
                <span class="config-card__stat avail">可用 {{ config.availableCount }}</span>
              </template>
              <span v-if="config.sourceType === 1 && config.alertEnabled === 1" class="tag tag--warning" style="margin-left: 4px;">预警</span>
              <span class="config-card__stat">关联 {{ config.relatedGoodsCount || 0 }} 商品</span>
            </div>
            <button
              class="config-card__del btn-danger btn-text btn-sm"
              @click.stop="handleDeleteConfig(config)"
            >删除</button>
          </div>
        </div>
      </div>

      <!-- 卡券详情视图 -->
      <div v-else class="kami-mobile">
        <header class="kami-mobile__header">
          <div class="kami-mobile__header-top">
            <button class="kami-mobile__back" @click="selectedConfigId = null; kamiItems = []">
              ← 返回
            </button>
            <span class="kami-mobile__config-name">{{ selectedConfig?.aliasName || `卡券库#${selectedConfigId}` }}</span>
          </div>
          <div class="kami-mobile__detail-actions">
            <button class="btn-default btn-sm" @click="openRelatedGoodsDialog">关联商品 {{ selectedConfig?.relatedGoodsCount || 0 }}</button>
            <button class="btn-default btn-sm" @click="openApiDialog">来源配置</button>
            <template v-if="isLocalSource">
              <button class="btn-default btn-sm" @click="showAddDialog = true">添加</button>
              <button class="btn-primary btn-sm" @click="showImportDialog = true">批量导入</button>
              <button class="btn-success btn-sm" @click="openExportDialog">导出</button>
              <button class="btn-warning btn-sm" @click="openAlertDialog">预警</button>
            </template>
          </div>
        </header>

        <div v-if="isApiSource" class="api-source-panel">
          <strong>外部 API 自动取卡</strong>
          <p>买家付款后系统会按订单请求供应商接口。成功返回的卡密会缓存，重新发货时不会重复取卡。</p>
          <button class="btn-primary btn-sm" @click="openApiDialog">查看 / 修改 API 配置</button>
        </div>

        <div v-else-if="isFixedSource" class="api-source-panel api-source-panel--fixed">
          <strong>固定内容发货</strong>
          <p>{{ selectedConfig?.fixedContent || '尚未配置固定发货内容' }}</p>
          <button class="btn-primary btn-sm" @click="openApiDialog">查看 / 修改固定内容</button>
        </div>

        <div v-if="isLocalSource" class="kami-mobile__filters">
          <select
            v-model="filterStatus"
            class="native-select"
            style="flex: 1;"
            @change="handleFilterChange"
          >
            <option :value="undefined">全部状态</option>
            <option :value="0">未使用</option>
            <option :value="1">已使用</option>
          </select>
          <input
            v-model="filterKeyword"
            class="native-input"
            placeholder="搜索卡券"
            style="flex: 2;"
            @keyup.enter="handleFilterChange"
          />
          <button class="btn-default" @click="handleFilterChange">搜索</button>
        </div>

        <div v-if="isLocalSource" class="kami-mobile__items">
          <div v-if="itemsLoading" class="kami-page__empty">加载中...</div>
          <div v-else-if="kamiItems.length === 0" class="kami-page__empty">暂无卡券</div>
          <div
            v-for="item in kamiItems"
            :key="item.id"
            class="kami-item-card"
            :class="{ 'kami-item-card--used': item.status === 1 }"
          >
            <div class="kami-item-card__content">{{ item.kamiContent }}</div>
            <div class="kami-item-card__meta">
              <span :class="item.status === 0 ? 'tag tag--success' : 'tag tag--info'">
                {{ item.status === 0 ? '未使用' : '已使用' }}
              </span>
              <span v-if="item.usedTime" class="kami-item-card__time">{{ item.usedTime }}</span>
            </div>
            <div class="kami-item-card__actions">
              <button v-if="item.status === 1" class="btn-warning btn-text btn-sm" @click="handleResetItem(item)">重置</button>
              <button class="btn-danger btn-text btn-sm" @click="handleDeleteItem(item)">删除</button>
            </div>
          </div>
        </div>
      </div>

    </template>

    <!-- ===== 桌面端 ===== -->
    <template v-else>
      <header class="kami-page__header">
        <h1 class="kami-page__title">卡券管理</h1>
        <div class="kami-page__actions">
          <span class="kami-page__shared-hint">所有账号共享同一套卡券库存</span>
          <button class="btn-primary" @click="showCreateDialog = true">
            新建卡券库
          </button>
        </div>
      </header>

      <div class="kami-page__body">
        <div class="kami-page__sidebar">
          <div v-if="configLoading" class="kami-page__empty">加载中...</div>
          <div v-else-if="kamiConfigs.length === 0" class="kami-page__empty">暂无卡券库，点击右上角新建</div>
          <div
            v-for="config in kamiConfigs"
            :key="config.id"
            class="config-card"
            :class="{ 'config-card--active': selectedConfigId === config.id }"
            @click="selectConfig(config)"
          >
            <div class="config-card__name">{{ config.aliasName || `卡券库#${config.id}` }}</div>
            <div class="config-card__stats">
              <template v-if="config.sourceType === 2">
                <span class="tag tag--info">外部 API</span>
                <span class="config-card__stat">按订单实时取卡</span>
              </template>
              <template v-else-if="config.sourceType === 3">
                <span class="tag tag--fixed">固定内容</span>
                <span class="config-card__stat">{{ contentPreview(config.fixedContent) }}</span>
              </template>
              <template v-else>
                <span class="config-card__stat">总量 {{ config.totalCount }}</span>
                <span class="config-card__stat used">已用 {{ config.usedCount }}</span>
                <span class="config-card__stat avail">可用 {{ config.availableCount }}</span>
              </template>
              <span v-if="config.sourceType === 1 && config.alertEnabled === 1" class="tag tag--warning" style="margin-left: 4px;">预警</span>
              <span class="config-card__stat">关联 {{ config.relatedGoodsCount || 0 }} 商品</span>
            </div>
            <button
              class="config-card__del btn-danger btn-text btn-sm"
              @click.stop="handleDeleteConfig(config)"
            >删除</button>
          </div>
        </div>

        <div class="kami-page__main">
          <div v-if="!selectedConfig" class="kami-page__empty-main">请选择左侧卡券库</div>
          <template v-else>
            <div class="kami-detail__header">
              <h2>{{ selectedConfig.aliasName || `卡券库#${selectedConfig.id}` }}</h2>
              <div class="kami-detail__actions">
                <button class="btn-default" @click="openRelatedGoodsDialog">关联商品 {{ selectedConfig.relatedGoodsCount || 0 }}</button>
                <button class="btn-default" @click="openApiDialog">来源配置</button>
                <template v-if="isLocalSource">
                  <button class="btn-default" @click="showAddDialog = true">添加卡券</button>
                  <button class="btn-primary" @click="showImportDialog = true">批量导入</button>
                  <button class="btn-success" @click="openExportDialog">导出</button>
                  <button class="btn-warning" @click="openAlertDialog">预警配置</button>
                </template>
              </div>
            </div>

            <div v-if="isApiSource" class="api-source-panel">
              <strong>外部 API 自动取卡</strong>
              <p>当前卡券库不保存本地卡密。每笔订单会请求一次供应商接口，接口成功内容会按订单缓存，消息重试不会重复扣卡。</p>
              <button class="btn-primary" @click="openApiDialog">查看 / 修改 API 配置</button>
            </div>

            <div v-else-if="isFixedSource" class="api-source-panel api-source-panel--fixed">
              <strong>固定内容发货</strong>
              <p>{{ selectedConfig.fixedContent || '尚未配置固定发货内容' }}</p>
              <button class="btn-primary" @click="openApiDialog">查看 / 修改固定内容</button>
            </div>

            <div v-if="isLocalSource" class="kami-detail__filters">
              <select
                v-model="filterStatus"
                class="native-select"
                style="width: 120px; margin-right: 8px;"
                @change="handleFilterChange"
              >
                <option :value="undefined">全部状态</option>
                <option :value="0">未使用</option>
                <option :value="1">已使用</option>
              </select>
              <input
                v-model="filterKeyword"
                class="native-input"
                placeholder="搜索卡券内容"
                style="width: 200px; margin-right: 8px;"
                @keyup.enter="handleFilterChange"
              />
              <button class="btn-default" @click="handleFilterChange">搜索</button>
            </div>

            <div v-if="isLocalSource" class="kami-detail__table">
              <div v-if="itemsLoading" class="kami-page__empty">加载中...</div>
              <template v-else>
                <div v-if="kamiItems.length === 0" class="kami-page__empty">暂无卡券</div>
                <table v-else class="kami-table">
                  <thead>
                    <tr>
                      <th>序号</th>
                      <th>卡券内容</th>
                      <th>状态</th>
                      <th>订单ID</th>
                      <th>使用时间</th>
                      <th>添加时间</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in kamiItems" :key="item.id" :class="{ 'kami-table__row--used': item.status === 1 }">
                      <td class="kami-table__cell--num">{{ item.sortOrder }}</td>
                      <td class="kami-table__cell--content">{{ item.kamiContent }}</td>
                      <td>
                        <span class="kami-table__status" :class="item.status === 0 ? 'kami-table__status--unused' : 'kami-table__status--used'">
                          {{ item.status === 0 ? '未使用' : '已使用' }}
                        </span>
                      </td>
                      <td class="kami-table__cell--id">{{ item.orderId || '-' }}</td>
                      <td class="kami-table__cell--time">{{ item.usedTime || '-' }}</td>
                      <td class="kami-table__cell--time">{{ item.createTime }}</td>
                      <td>
                        <div class="kami-table__actions">
                          <button v-if="item.status === 1" class="kami-table__action-btn kami-table__action-btn--reset" @click="handleResetItem(item)">重置</button>
                          <button class="kami-table__action-btn kami-table__action-btn--delete" @click="handleDeleteItem(item)">删除</button>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </template>
            </div>
          </template>
        </div>
      </div>
    </template>

    <!-- ===== 弹窗（共用） ===== -->
    <Teleport to="body">
      <!-- 新建卡券库 -->
      <Transition name="modal">
        <div v-if="showCreateDialog" class="modal-overlay" @click.self="showCreateDialog = false">
          <div class="modal-container">
            <div class="modal-header">
              <h2 class="modal-title">新建卡券库</h2>
              <button class="modal-close" @click="showCreateDialog = false">×</button>
            </div>
            <div class="modal-body">
              <div class="form-row">
                <label class="form-label">别名</label>
                <input v-model="createForm.aliasName" class="form-input" placeholder="请输入别名" maxlength="50" />
              </div>
              <div class="form-row">
                <label class="form-label">卡券来源</label>
                <div class="form-radio-group">
                  <label class="form-radio" :class="{ 'is-active': createForm.sourceType === 1 }">
                    <input type="radio" :value="1" v-model="createForm.sourceType" />本地库存
                  </label>
                  <label class="form-radio" :class="{ 'is-active': createForm.sourceType === 2 }">
                    <input type="radio" :value="2" v-model="createForm.sourceType" />外部 API 自动取卡
                  </label>
                  <label class="form-radio" :class="{ 'is-active': createForm.sourceType === 3 }">
                    <input type="radio" :value="3" v-model="createForm.sourceType" />固定内容发货
                  </label>
                </div>
              </div>
              <p v-if="createForm.sourceType === 2" class="form-hint">创建后可在该卡券库中继续填写 API 地址、请求参数和返回内容路径。</p>
              <p v-else-if="createForm.sourceType === 3" class="form-hint">创建后填写一次网盘链接、教程或说明；每笔订单都会发送同样的内容，不扣库存。</p>
            </div>
            <div class="modal-footer">
              <button class="btn btn-secondary" @click="showCreateDialog = false">取消</button>
              <button class="btn btn-primary" :class="{ 'is-loading': createLoading }" :disabled="createLoading" @click="handleCreate">确定</button>
            </div>
          </div>
        </div>
      </Transition>

      <!-- 添加卡券 -->
      <Transition name="modal">
        <div v-if="showAddDialog" class="modal-overlay" @click.self="showAddDialog = false">
          <div class="modal-container">
            <div class="modal-header">
              <h2 class="modal-title">添加卡券</h2>
              <button class="modal-close" @click="showAddDialog = false">×</button>
            </div>
            <div class="modal-body">
              <textarea v-model="addContent" class="form-textarea" :rows="3" placeholder="请输入卡券内容"></textarea>
            </div>
            <div class="modal-footer">
              <button class="btn btn-secondary" @click="showAddDialog = false">取消</button>
              <button class="btn btn-primary" :class="{ 'is-loading': addLoading }" :disabled="addLoading" @click="handleAddKami">确定</button>
            </div>
          </div>
        </div>
      </Transition>

      <!-- 批量导入卡券 -->
      <Transition name="modal">
        <div v-if="showImportDialog" class="modal-overlay" @click.self="showImportDialog = false">
          <div class="modal-container modal-container--lg">
            <div class="modal-header">
              <h2 class="modal-title">批量导入卡券</h2>
              <button class="modal-close" @click="showImportDialog = false">×</button>
            </div>
            <div class="modal-body">
              <p class="form-hint">每行一条卡券，重复内容不会跳过</p>
              <textarea v-model="importContent" class="form-textarea" :rows="10" placeholder="卡券1&#10;卡券2&#10;卡券3"></textarea>
            </div>
            <div class="modal-footer">
              <button class="btn btn-secondary" @click="showImportDialog = false">取消</button>
              <button class="btn btn-primary" :class="{ 'is-loading': importLoading }" :disabled="importLoading" @click="handleBatchImport">导入</button>
            </div>
          </div>
        </div>
      </Transition>

      <!-- 卡券来源 / 外部 API 配置 -->
      <Transition name="modal">
        <div v-if="showApiDialog" class="modal-overlay" @click.self="showApiDialog = false">
          <div class="modal-container modal-container--lg">
            <div class="modal-header">
              <h2 class="modal-title">卡券来源配置</h2>
              <button class="modal-close" @click="showApiDialog = false">×</button>
            </div>
            <div class="modal-body api-config-form">
              <div class="form-row">
                <label class="form-label">卡券来源</label>
                <div class="form-radio-group">
                  <label class="form-radio" :class="{ 'is-active': apiForm.sourceType === 1 }">
                    <input type="radio" :value="1" v-model="apiForm.sourceType" />本地库存卡券
                  </label>
                  <label class="form-radio" :class="{ 'is-active': apiForm.sourceType === 2 }">
                    <input type="radio" :value="2" v-model="apiForm.sourceType" />外部 API 自动取卡
                  </label>
                  <label class="form-radio" :class="{ 'is-active': apiForm.sourceType === 3 }">
                    <input type="radio" :value="3" v-model="apiForm.sourceType" />固定内容发货
                  </label>
                </div>
              </div>

              <template v-if="apiForm.sourceType === 2">
                <p class="form-hint api-config-form__intro">付款后系统会请求一次供应商接口；成功取到的卡密会绑定订单缓存，重新发货不会重复取卡。</p>
                <div class="form-row">
                  <label class="form-label">接口地址</label>
                  <input v-model="apiForm.apiUrl" class="form-input" placeholder="https://supplier.example.com/api/card" />
                </div>
                <div class="form-row form-row--inline">
                  <label class="form-label">请求方式</label>
                  <select v-model="apiForm.apiMethod" class="native-select" style="width: 130px;">
                    <option value="POST">POST（JSON 请求体）</option>
                    <option value="GET">GET（URL 参数）</option>
                  </select>
                  <label class="form-label api-config-form__timeout">超时</label>
                  <input v-model.number="apiForm.apiTimeoutSeconds" class="form-input form-input--num" type="number" min="3" max="30" />
                  <span class="form-suffix">秒，3–30</span>
                </div>
                <div class="form-row">
                  <label class="form-label">请求头（可选）</label>
                  <textarea v-model="apiForm.apiHeaders" class="form-textarea" :rows="3" placeholder='{"Authorization":"Bearer YOUR_TOKEN"}'></textarea>
                  <p class="form-hint">必须是 JSON 对象。可放 API 密钥，例如 Authorization、X-Api-Key。</p>
                </div>
                <div class="form-row">
                  <label class="form-label">请求参数</label>
                  <textarea v-model="apiForm.apiRequestTemplate" class="form-textarea" :rows="7" placeholder='{"orderId":"&#123;&#123;orderId&#125;&#125;","quantity":"&#123;&#123;quantity&#125;&#125;"}'></textarea>
                  <p v-pre class="form-hint">POST 会作为 JSON 请求体发送，GET 会转为 URL 参数。可用变量：{{orderId}}、{{goodsId}}、{{buyerName}}、{{skuId}}、{{quantity}}、{{accountId}}。</p>
                </div>
                <div class="form-row">
                  <label class="form-label">返回内容路径</label>
                  <input v-model="apiForm.apiResultPath" class="form-input" placeholder="例如 data.card；留空会自动尝试 content、card、kami" />
                  <p class="form-hint">填写接口响应中实际卡密所在字段。例：返回 {"data":{"card":"abc"}} 时填写 data.card。</p>
                </div>
                <p class="form-hint api-config-form__warning">测试接口会真实请求供应商。若供应商没有测试环境，请不要对正式出卡接口点击测试，避免提前出卡。</p>
                <div v-if="apiTestResult" class="api-config-form__test-result">{{ apiTestResult }}</div>
              </template>

              <template v-else-if="apiForm.sourceType === 3">
                <p class="form-hint api-config-form__intro">适合网盘链接、教程、提取码说明等内容。保存一次后，每笔已关联商品的订单都会发送同样内容，不需要设置库存数量。</p>
                <div class="form-row">
                  <label class="form-label">固定发货内容</label>
                  <textarea v-model="apiForm.fixedContent" class="form-textarea" :rows="7" maxlength="200" placeholder="例如：网盘链接：https://...&#10;提取码：1234&#10;如有问题请联系我。"></textarea>
                  <p class="form-hint">最多 200 个字符，受闲鱼虚拟发货内容限制。</p>
                </div>
              </template>

              <p v-else class="form-hint">切回本地库存后，商品仍使用这套卡券库中已导入的卡密。原有 API 配置会保留，但不会再请求接口。</p>
            </div>
            <div class="modal-footer">
              <button class="btn btn-secondary" @click="showApiDialog = false">取消</button>
              <button v-if="apiForm.sourceType === 2" class="btn btn-secondary" :disabled="apiTesting" @click="handleTestApi">{{ apiTesting ? '测试中…' : '测试接口' }}</button>
              <button class="btn btn-primary" :class="{ 'is-loading': apiSaving }" :disabled="apiSaving" @click="handleSaveApi">保存配置</button>
            </div>
          </div>
        </div>
      </Transition>

      <!-- 关联商品 -->
      <Transition name="modal">
        <div v-if="showRelatedGoodsDialog" class="modal-overlay" @click.self="showRelatedGoodsDialog = false">
          <div class="modal-container modal-container--wide">
            <div class="modal-header">
              <div>
                <h2 class="modal-title">关联商品</h2>
                <p class="form-hint">关联后会自动开启商品的自动发货，并使用当前卡券库作为发货来源。</p>
              </div>
              <button class="modal-close" @click="showRelatedGoodsDialog = false">×</button>
            </div>
            <div class="modal-body related-goods">
              <div class="related-goods__warning">若商品原本有其他自动发货配置，保存关联后会由「{{ selectedConfig?.aliasName || '当前卡券库' }}」接管；取消关联则会关闭该商品由本卡券库提供的自动发货。</div>
              <div class="related-goods__grid">
                <section class="related-goods__column">
                  <div class="related-goods__column-head">
                    <strong>可选商品</strong>
                    <span>{{ filteredRelatedGoods.length }} 个</span>
                  </div>
                  <input v-model="relatedGoodsKeyword" class="form-input related-goods__search" placeholder="搜索商品名、商品 ID 或账号备注" />
                  <div v-if="relatedGoodsLoading" class="related-goods__empty">加载中…</div>
                  <div v-else-if="filteredRelatedGoods.length === 0" class="related-goods__empty">没有匹配的商品</div>
                  <label v-else v-for="goods in filteredRelatedGoods" :key="relatedGoodsKey(goods)" class="related-goods__item">
                    <input v-model="selectedRelatedGoodsKeys" type="checkbox" :value="relatedGoodsKey(goods)" />
                    <img v-if="goods.coverPic" :src="goods.coverPic" class="related-goods__cover" alt="" />
                    <span v-else class="related-goods__cover related-goods__cover--empty">商品</span>
                    <span class="related-goods__info">
                      <strong>{{ goods.goodsTitle || `商品 ${goods.xyGoodsId}` }}</strong>
                      <small>{{ goods.accountNote || '未知账号' }} · ID: {{ goods.xyGoodsId }}<template v-if="goods.soldPrice"> · ¥{{ goods.soldPrice }}</template></small>
                      <em v-if="goods.willReplace && !goods.associated">已有发货配置，关联后将接管</em>
                    </span>
                  </label>
                </section>
                <section class="related-goods__column related-goods__column--selected">
                  <div class="related-goods__column-head">
                    <strong>已关联商品</strong>
                    <span>{{ selectedRelatedGoods.length }} 个</span>
                  </div>
                  <div v-if="selectedRelatedGoods.length === 0" class="related-goods__empty">请在左侧勾选商品</div>
                  <div v-else v-for="goods in selectedRelatedGoods" :key="relatedGoodsKey(goods)" class="related-goods__selected-item">
                    <span>
                      <strong>{{ goods.goodsTitle || `商品 ${goods.xyGoodsId}` }}</strong>
                      <small>{{ goods.accountNote || '未知账号' }} · ID: {{ goods.xyGoodsId }}</small>
                    </span>
                    <button class="btn-danger btn-text btn-sm" @click="removeRelatedGoods(goods)">移除</button>
                  </div>
                </section>
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn btn-secondary" @click="showRelatedGoodsDialog = false">取消</button>
              <button class="btn btn-primary" :class="{ 'is-loading': relatedGoodsSaving }" :disabled="relatedGoodsSaving || relatedGoodsLoading" @click="handleSaveRelatedGoods">保存关联（{{ selectedRelatedGoods.length }} 个商品）</button>
            </div>
          </div>
        </div>
      </Transition>

      <!-- 预警配置 -->
      <Transition name="modal">
        <div v-if="showAlertDialog" class="modal-overlay" @click.self="showAlertDialog = false">
          <div class="modal-container">
            <div class="modal-header">
              <h2 class="modal-title">预警配置</h2>
              <button class="modal-close" @click="showAlertDialog = false">×</button>
            </div>
            <div class="modal-body">
              <div class="form-row">
                <label class="form-label">开启预警</label>
                <label class="form-switch">
                  <input type="checkbox" :checked="alertForm.alertEnabled === 1" @change="alertForm.alertEnabled = alertForm.alertEnabled === 1 ? 0 : 1" />
                  <span class="form-switch-track"></span>
                </label>
              </div>
              <div class="form-row">
                <label class="form-label">阈值类型</label>
                <div class="form-radio-group">
                  <label class="form-radio" :class="{ 'is-active': alertForm.alertThresholdType === 1 }">
                    <input type="radio" :value="1" v-model="alertForm.alertThresholdType" />数量
                  </label>
                  <label class="form-radio" :class="{ 'is-active': alertForm.alertThresholdType === 2 }">
                    <input type="radio" :value="2" v-model="alertForm.alertThresholdType" />百分比
                  </label>
                </div>
              </div>
              <div class="form-row">
                <label class="form-label">阈值数值</label>
                <input type="number" v-model.number="alertForm.alertThresholdValue" class="form-input form-input--num" :min="1" :max="alertForm.alertThresholdType === 2 ? 100 : 99999" />
                <span class="form-suffix">{{ alertForm.alertThresholdType === 1 ? '可用卡券低于此数量时预警' : '可用比例低于此百分比时预警' }}</span>
              </div>
              <div class="form-row">
                <label class="form-label">预警邮箱</label>
                <input v-model="alertForm.alertEmail" class="form-input" placeholder="留空则使用系统设置的邮箱" />
              </div>
            </div>
            <div class="modal-footer">
              <button class="btn btn-secondary" @click="showAlertDialog = false">取消</button>
              <button class="btn btn-primary" :class="{ 'is-loading': alertLoading }" :disabled="alertLoading" @click="handleSaveAlert">保存</button>
            </div>
          </div>
        </div>
      </Transition>

      <!-- 导出卡券 -->
      <Transition name="modal">
        <div v-if="showExportDialog" class="modal-overlay" @click.self="showExportDialog = false">
          <div class="modal-container">
            <div class="modal-header">
              <h2 class="modal-title">导出卡券</h2>
              <button class="modal-close" @click="showExportDialog = false">×</button>
            </div>
            <div class="modal-body">
              <div class="form-row">
                <label class="form-label">导出状态</label>
                <div class="form-checkbox-group">
                  <label class="form-checkbox">
                    <input type="checkbox" v-model="exportStatus.unused" />未使用
                  </label>
                  <label class="form-checkbox">
                    <input type="checkbox" v-model="exportStatus.used" />已使用
                  </label>
                </div>
              </div>
              <p class="form-hint form-hint--indent">导出为Excel格式（.txt文件，Excel可直接打开）</p>
            </div>
            <div class="modal-footer">
              <button class="btn btn-secondary" @click="showExportDialog = false">取消</button>
              <button class="btn btn-primary" @click="handleExport">导出</button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<style scoped>
.kami-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px;
  background: rgba(255,255,255,0.55);
  overflow: hidden;
  box-sizing: border-box;
}

/* ===== 桌面端 ===== */
.kami-page__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.kami-page__title {
  font-size: 20px;
  font-weight: 600;
  color: #1c1c1e;
  margin: 0;
}
.kami-page__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kami-page__shared-hint {
  font-size: 13px;
  color: rgba(28,28,30,.55);
}
.kami-page__body {
  flex: 1;
  display: flex;
  gap: 16px;
  min-height: 0;
  overflow: hidden;
}
.kami-page__sidebar {
  width: 260px;
  flex-shrink: 0;
  overflow-y: auto;
  border-right: 1px solid rgba(60,60,67,.12);
  padding-right: 12px;
}
.kami-page__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.kami-page__empty,
.kami-page__empty-main {
  color: rgba(28,28,30,.55);
  font-size: 14px;
  text-align: center;
  padding: 40px 0;
}
.config-card {
  padding: 12px;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}
.config-card:hover {
  border-color: rgba(0,122,255,0.3);
}
.config-card--active {
  border-color: #0A84FF;
  background: rgba(10,132,255,0.06);
}
.config-card__name {
  font-size: 14px;
  font-weight: 600;
  color: #1c1c1e;
  margin-bottom: 6px;
}
.config-card__stats {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: rgba(28,28,30,.55);
  margin-bottom: 4px;
  flex-wrap: wrap;
}
.config-card__stat.used { color: #FF9F0A; }
.config-card__stat.avail { color: #30D158; }
.config-card__del {
  position: absolute;
  top: 8px;
  right: 8px;
}
.kami-detail__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.kami-detail__header h2 {
  font-size: 16px;
  font-weight: 600;
  color: #1c1c1e;
  margin: 0;
}
.kami-detail__actions {
  display: flex;
  gap: 8px;
}
.kami-detail__filters {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  flex-shrink: 0;
}
.kami-detail__table {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.kami-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: auto;
}

.kami-table th {
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(16px) saturate(1.6);
  -webkit-backdrop-filter: blur(16px) saturate(1.6);
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #1c1c1e;
  letter-spacing: .4px;
  text-align: left;
  border-bottom: 1px solid rgba(60,60,67,.12);
  white-space: nowrap;
  position: sticky;
  top: 0;
  z-index: 1;
}

.kami-table td {
  padding: 10px 16px;
  font-size: 13px;
  color: #1c1c1e;
  border-bottom: 1px solid rgba(60,60,67,.08);
}

.kami-table tbody tr:hover {
  background: rgba(255,255,255,0.38);
}

.kami-table__row--used {
  opacity: .6;
}

.kami-table__cell--num {
  font-size: 12px;
  color: rgba(28,28,30,.55);
}

.kami-table__cell--content {
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.kami-table__cell--id {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: 'SF Mono', 'Menlo', monospace;
  font-size: 12px;
}

.kami-table__cell--time {
  white-space: nowrap;
  font-size: 12px;
  color: rgba(28,28,30,.55);
}

.kami-table__status {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 500;
}

.kami-table__status--unused {
  background: rgba(48,209,88,0.12);
  color: #30D158;
}

.kami-table__status--used {
  background: rgba(120,120,128,0.12);
  color: rgba(28,28,30,.55);
}

.kami-table__actions {
  display: flex;
  gap: 8px;
}

.kami-table__action-btn {
  padding: 4px 12px;
  border: none;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 590;
  cursor: pointer;
  transition: opacity .15s, transform .12s;
  font-family: inherit;
}

.kami-table__action-btn:active { opacity: .80; transform: scale(.96); }

.kami-table__action-btn--reset {
  color: #FF9F0A;
  background: rgba(255,159,10,0.12);
}

.kami-table__action-btn--delete {
  color: #FF453A;
  background: rgba(255,69,58,0.12);
}

/* ===== 手机端 ===== */
.kami-mobile {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.kami-mobile__header {
  flex-shrink: 0;
  padding: 0 0 12px;
  border-bottom: 1px solid rgba(60,60,67,.12);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kami-mobile__header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kami-mobile__select {
  width: 100%;
}

.kami-mobile__back {
  background: none;
  border: none;
  color: #0A84FF;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  -webkit-tap-highlight-color: transparent;
}

.kami-mobile__config-name {
  font-size: 15px;
  font-weight: 600;
  color: #1c1c1e;
  flex: 1;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 8px;
}

.kami-mobile__detail-actions {
  display: flex;
  gap: 6px;
}

.kami-mobile__filters {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-shrink: 0;
  padding: 10px 0;
  border-bottom: 1px solid rgba(60,60,67,.12);
}

.kami-mobile__list {
  flex: 1;
  overflow-y: auto;
  padding-top: 12px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.kami-mobile__list::-webkit-scrollbar { display: none; }

.kami-mobile__items {
  flex: 1;
  overflow-y: auto;
  padding-top: 8px;
  scrollbar-width: none;
  -ms-overflow-style: none;
}
.kami-mobile__items::-webkit-scrollbar { display: none; }

/* 卡密条目卡片 */
.kami-item-card {
  padding: 10px 12px;
  border-bottom: 0.5px solid rgba(60,60,67,.12);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kami-item-card:nth-child(even) {
  background: rgba(255,255,255,0.15);
}
.kami-item-card--used {
  opacity: 0.6;
}
.kami-item-card__content {
  font-size: 13px;
  font-weight: 500;
  color: #1c1c1e;
  word-break: break-all;
}
.kami-item-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kami-item-card__time {
  font-size: 11px;
  color: rgba(28,28,30,.55);
}
.kami-item-card__actions {
  display: flex;
  gap: 4px;
}

/* ===== 弹窗样式 ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.20);
  backdrop-filter: blur(28px) saturate(1.8);
  -webkit-backdrop-filter: blur(28px) saturate(1.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: 24px;
}

.modal-container {
  background: rgba(255,255,255,0.72);
  backdrop-filter: blur(40px) saturate(2);
  -webkit-backdrop-filter: blur(40px) saturate(2);
  border: 1px solid rgba(255,255,255,0.75);
  border-radius: 20px;
  width: 100%;
  max-width: 400px;
  max-height: 85vh;
  box-shadow: 0 16px 48px rgba(0,0,0,0.16), 0 2px 8px rgba(0,0,0,0.08);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.modal-container--lg {
  max-width: 720px;
}

.modal-container--wide {
  max-width: 980px;
}

.api-source-panel {
  margin: 12px 0;
  padding: 18px;
  border: 1px solid rgba(10,132,255,.20);
  border-radius: 12px;
  background: rgba(10,132,255,.06);
  color: #1c1c1e;
}
.api-source-panel strong { display: block; font-size: 14px; }
.api-source-panel p {
  margin: 8px 0 14px;
  font-size: 13px;
  line-height: 1.6;
  color: rgba(28,28,30,.66);
}
.api-source-panel--fixed {
  border-color: rgba(175,82,222,.22);
  background: rgba(175,82,222,.07);
}
.api-config-form .form-row {
  align-items: flex-start;
  flex-wrap: wrap;
}
.api-config-form .form-label { padding-top: 8px; }
.api-config-form .form-textarea,
.api-config-form .form-hint,
.api-config-form .form-input:not(.form-input--num) {
  flex: 1 1 calc(100% - 80px);
}
.api-config-form .form-hint { margin-left: 80px; }
.api-config-form__intro { line-height: 1.6; }
.api-config-form__timeout { margin-left: 16px; }
.api-config-form__test-result {
  white-space: pre-wrap;
  word-break: break-all;
  color: #0a7b35;
  background: rgba(48,209,88,.10);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
}

.related-goods__warning {
  padding: 10px 12px;
  border-radius: 9px;
  background: rgba(255,159,10,.10);
  color: #8d5d00;
  font-size: 12px;
  line-height: 1.55;
}

.related-goods__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 410px;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 12px;
  overflow: hidden;
}

.related-goods__column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: rgba(255,255,255,.35);
}

.related-goods__column + .related-goods__column {
  border-left: 1px solid rgba(60,60,67,.12);
}

.related-goods__column--selected { background: rgba(48,209,88,.035); }
.related-goods__column-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px 8px;
  color: #1c1c1e;
  font-size: 13px;
}
.related-goods__column-head span { color: #30a857; font-size: 12px; }
.related-goods__search { margin: 0 12px 10px; width: calc(100% - 24px); flex: none; }
.related-goods__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-top: 1px solid rgba(60,60,67,.08);
  cursor: pointer;
}
.related-goods__item:hover { background: rgba(10,132,255,.045); }
.related-goods__item input { flex: none; margin: 0; }
.related-goods__cover {
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 8px;
  object-fit: cover;
  background: rgba(60,60,67,.08);
}
.related-goods__cover--empty { display: inline-flex; align-items: center; justify-content: center; color: rgba(28,28,30,.45); font-size: 10px; }
.related-goods__info { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.related-goods__info strong, .related-goods__selected-item strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: #1c1c1e; }
.related-goods__info small, .related-goods__selected-item small { color: rgba(28,28,30,.52); font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.related-goods__info em { color: #e58600; font-size: 11px; font-style: normal; }
.related-goods__selected-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid rgba(60,60,67,.08);
}
.related-goods__selected-item > span { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.related-goods__empty { display: flex; flex: 1; min-height: 100px; align-items: center; justify-content: center; color: rgba(28,28,30,.45); font-size: 13px; padding: 16px; text-align: center; }

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  flex-shrink: 0;
}

.modal-title {
  font-size: 15px;
  font-weight: 600;
  color: #1c1c1e;
  margin: 0;
}

.modal-close {
  width: 26px;
  height: 26px;
  border-radius: 7px;
  border: none;
  background: transparent;
  color: rgba(28,28,30,.55);
  font-size: 18px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}

.modal-close:hover {
  background: rgba(60,60,67,.12);
  color: #1c1c1e;
}

.modal-body {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
  min-height: 0;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 20px;
  flex-shrink: 0;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-label {
  font-size: 13px;
  color: #1c1c1e;
  font-weight: 500;
  min-width: 70px;
  flex-shrink: 0;
}

.form-input {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 8px;
  font-size: 13px;
  background: rgba(255,255,255,0.55);
  color: #1c1c1e;
  transition: border-color 0.15s ease;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #0A84FF;
}

.form-input--num {
  width: 100px;
  flex: none;
}

.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.5;
  resize: vertical;
  background: rgba(255,255,255,0.55);
  color: #1c1c1e;
  font-family: inherit;
  box-sizing: border-box;
}

.form-textarea:focus {
  outline: none;
  border-color: #0A84FF;
}

.form-hint {
  font-size: 12px;
  color: rgba(28,28,30,.55);
  margin: 0;
}

.form-hint--indent {
  margin-left: 70px;
}

.form-suffix {
  font-size: 12px;
  color: rgba(28,28,30,.55);
}

.form-switch {
  position: relative;
  display: inline-block;
  width: 40px;
  height: 24px;
  cursor: pointer;
}

.form-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.form-switch-track {
  position: absolute;
  inset: 0;
  background: #e5e5e5;
  border-radius: 12px;
  transition: background 0.2s ease;
}

.form-switch-track::after {
  content: '';
  position: absolute;
  width: 20px;
  height: 20px;
  left: 2px;
  top: 2px;
  background: rgba(255,255,255,0.55);
  border-radius: 50%;
  transition: transform 0.2s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.form-switch input:checked + .form-switch-track {
  background: #30D158;
}

.form-switch input:checked + .form-switch-track::after {
  transform: translateX(16px);
}

.form-radio-group {
  display: flex;
  gap: 12px;
}

.form-radio {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #1c1c1e;
  cursor: pointer;
}

.form-radio input {
  margin: 0;
}

.form-checkbox-group {
  display: flex;
  gap: 16px;
}

.form-checkbox {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #1c1c1e;
  cursor: pointer;
}

.form-checkbox input {
  margin: 0;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 590;
  cursor: pointer;
  transition: opacity .15s, transform .12s, box-shadow .15s;
  border: none;
  font-family: inherit;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
}

.btn:active { opacity: .80; transform: scale(.96); }

.btn-secondary {
  color: #0A84FF;
  background: rgba(255,255,255,0.70);
  backdrop-filter: blur(16px) saturate(1.6);
  -webkit-backdrop-filter: blur(16px) saturate(1.6);
  border: 1px solid rgba(255,255,255,0.85);
  box-shadow: 0 8px 32px rgba(0,0,0,0.08), 0 1.5px 4px rgba(0,0,0,0.04);
}

@media (hover: hover) {
  .btn-secondary:hover {
    background: rgba(255,255,255,0.80);
  }
}

.btn-primary {
  background: rgba(10,132,255,0.85);
  backdrop-filter: blur(20px) saturate(1.8);
  -webkit-backdrop-filter: blur(20px) saturate(1.8);
  color: #fff;
  border: 1px solid rgba(255,255,255,0.35);
  box-shadow: 0 4px 16px rgba(10,132,255,0.35), 0 8px 32px rgba(0,0,0,0.08), 0 1.5px 4px rgba(0,0,0,0.04);
}

@media (hover: hover) {
  .btn-primary:hover:not(:disabled) {
    background: rgba(10,132,255,0.95);
    box-shadow: 0 6px 20px rgba(10,132,255,0.45), 0 8px 32px rgba(0,0,0,0.08), 0 1.5px 4px rgba(0,0,0,0.04);
  }
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn.is-loading {
  opacity: 0.6;
  pointer-events: none;
}

/* Transitions */
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-active .modal-container,
.modal-leave-active .modal-container {
  transition: transform 0.3s cubic-bezier(0.32, 0.94, 0.6, 1), opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

.modal-enter-from .modal-container,
.modal-leave-to .modal-container {
  transform: scale(0.92) translateY(8px);
  opacity: 0;
}

.btn-primary, .btn-default, .btn-success, .btn-warning, .btn-danger, .btn-text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 100px;
  font-size: 13px;
  font-weight: 590;
  cursor: pointer;
  transition: opacity .15s, transform .12s;
  border: none;
  font-family: inherit;
  user-select: none;
  white-space: nowrap;
}
.btn-primary:active, .btn-default:active, .btn-success:active, .btn-warning:active, .btn-danger:active { opacity: .80; transform: scale(.96); }
.btn-primary { background: rgba(10,132,255,0.85); color: #fff; border: 1px solid rgba(255,255,255,0.35); box-shadow: 0 4px 16px rgba(10,132,255,0.35), 0 8px 32px rgba(0,0,0,0.08); }
.btn-default { background: rgba(255,255,255,0.70); color: #0A84FF; border: 1px solid rgba(255,255,255,0.85); box-shadow: 0 8px 32px rgba(0,0,0,0.08); }
.btn-success { background: rgba(48,209,88,0.85); color: #fff; border: 1px solid rgba(255,255,255,0.35); }
.btn-warning { background: rgba(255,159,10,0.85); color: #fff; border: 1px solid rgba(255,255,255,0.35); }
.btn-danger { color: #FF453A; background: rgba(255,69,58,0.15); border: 1px solid rgba(255,69,58,0.2); }
.btn-text { background: transparent; color: #0A84FF; padding: 4px 8px; }
.btn-sm { padding: 4px 12px; font-size: 12px; }
.btn-primary:disabled, .btn-default:disabled { opacity: 0.5; cursor: not-allowed; }

.tag { display: inline-flex; align-items: center; padding: 2px 10px; border-radius: 100px; font-size: 12px; font-weight: 500; }
.tag--success { background: rgba(48,209,88,0.12); color: #30D158; }
.tag--warning { background: rgba(255,159,10,0.12); color: #FF9F0A; }
.tag--info { background: rgba(120,120,128,0.12); color: rgba(28,28,30,.55); }

.native-select {
  padding: 8px 12px;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 8px;
  background: rgba(255,255,255,0.55);
  color: #1c1c1e;
  font-size: 13px;
  outline: none;
  cursor: pointer;
  font-family: inherit;
}
.native-select:focus { border-color: #0A84FF; }

.native-input {
  padding: 8px 12px;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 8px;
  background: rgba(255,255,255,0.55);
  color: #1c1c1e;
  font-size: 13px;
  outline: none;
  font-family: inherit;
  box-sizing: border-box;
}
.native-input:focus { border-color: #0A84FF; }

@media (max-width: 700px) {
  .modal-overlay { padding: 10px; }
  .modal-container--wide { max-height: 92vh; }
  .related-goods__grid { grid-template-columns: 1fr; min-height: 0; }
  .related-goods__column { max-height: 34vh; overflow-y: auto; }
  .related-goods__column + .related-goods__column { border-left: none; border-top: 1px solid rgba(60,60,67,.12); }
}
</style>

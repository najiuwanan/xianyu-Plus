import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAccountList } from '@/api/account'
import { getGoodsList, updateAutoDeliveryStatus, updateAutoConfirmShipment } from '@/api/goods'
import {
  getAutoDeliveryConfig,
  saveOrUpdateAutoDeliveryConfig,
  getAutoDeliveryConfigsByGoodsId,
  getGoodsSkuList,
  type AutoDeliveryConfig,
  type SaveAutoDeliveryConfigReq,
  type GetAutoDeliveryConfigReq,
  type GoodsSku
} from '@/api/auto-delivery-config'
import { showSuccess, showInfo } from '@/utils'
import {
  getKamiConfigs,
  type KamiConfig
} from '@/api/kami-config'
import type { Account } from '@/types'
import type { GoodsItemWithConfig } from '@/api/goods'

const copyToClipboard = (text: string) => {
  navigator.clipboard.writeText(text).then(() => {
    showSuccess('已复制到剪贴板')
  }).catch(() => {
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    showSuccess('已复制到剪贴板')
  })
}

export function useAutoDelivery() {
  const route = useRoute()
  const router = useRouter()

  const gotoConnection = () => router.push('/connection')
  ;(window as any).__gotoConnection = gotoConnection

  const saving = ref(false)
  const accounts = ref<Account[]>([])
  const selectedAccountId = ref<number | null>(null)
  const goodsList = ref<GoodsItemWithConfig[]>([])
  const selectedGoods = ref<GoodsItemWithConfig | null>(null)
  const currentConfig = ref<AutoDeliveryConfig | null>(null)

  const skuList = ref<GoodsSku[]>([])
  const selectedSkuId = ref<string | null>(null)
  const skuConfigs = ref<Map<string, AutoDeliveryConfig>>(new Map())

  const goodsCurrentPage = ref(1)
  const goodsTotal = ref(0)
  const goodsLoading = ref(false)
  const goodsListRef = ref<HTMLElement | null>(null)
  const onlyOnSale = ref(true)

  const detailDialogVisible = ref(false)
  const selectedGoodsId = ref<string>('')

  const configForm = ref({
    deliveryMode: 1,
    autoDeliveryContent: '',
    kamiConfigIds: '',
    kamiDeliveryTemplate: '',
    autoDeliveryImageUrl: '',
    autoConfirmShipment: 0,
    autoAskFlower: 0,
    autoAskFlowerText: ''
  })

  const kamiConfigOptions = ref<KamiConfig[]>([])

  const selectedKamiConfigId = computed({
    get: () => configForm.value.kamiConfigIds || '',
    set: (val: string) => { configForm.value.kamiConfigIds = val }
  })

  const selectedKamiConfig = computed(() =>
    kamiConfigOptions.value.find(item => String(item.id) === String(selectedKamiConfigId.value))
  )

  const isApiKamiSelected = computed(() => selectedKamiConfig.value?.sourceType === 2)
  const isFixedKamiSelected = computed(() => selectedKamiConfig.value?.sourceType === 3)

  const hasMultipleSku = computed(() => skuList.value.length > 1)

  const isMobile = ref(false)
  const mobileView = ref<'goods' | 'config'>('goods')

  const apiHintUrl = computed(() => '/api/order/list')

  const apiHintParams = computed(() => {
    const params: Record<string, any> = {
      xianyuAccountId: selectedAccountId.value || undefined,
      xyGoodsId: selectedGoods.value?.item.xyGoodId || undefined,
      orderStatus: 2,
      pageNum: 1,
      pageSize: 20
    }
    return params
  })

  const apiHintParamsJson = computed(() => JSON.stringify(apiHintParams.value, null, 2))

  const confirmShipmentUrl = computed(() => '/api/order/confirmShipment')

  const confirmShipmentParams = computed(() => {
    const params: Record<string, any> = {
      xianyuAccountId: selectedAccountId.value || undefined,
      orderId: '订单ID'
    }
    return params
  })

  const confirmShipmentParamsJson = computed(() => JSON.stringify(confirmShipmentParams.value, null, 2))

  const copyApiUrl = () => { copyToClipboard(apiHintUrl.value) }
  const copyApiParams = () => { copyToClipboard(apiHintParamsJson.value) }
  const copyConfirmShipmentUrl = () => { copyToClipboard(confirmShipmentUrl.value) }
  const copyConfirmShipmentParams = () => { copyToClipboard(confirmShipmentParamsJson.value) }

  const checkScreenSize = () => {
    isMobile.value = window.innerWidth < 768
    if (!isMobile.value) { mobileView.value = 'goods' }
  }

  const goBackToGoods = () => { mobileView.value = 'goods' }

  const formatTime = (time: string) => {
    if (!time) return '-'
    return time.replace('T', ' ').substring(0, 19)
  }

  const formatPrice = (price: string) => { return price ? `¥${price}` : '-' }

  const getStatusText = (status: number) => {
    const map: Record<number, string> = { 0: '在售', 1: '已下架', 2: '已售出' }
    return map[status] || '未知'
  }

  const getStatusClass = (status: number) => {
    const map: Record<number, string> = { 0: 'on-sale', 1: 'off-shelf', 2: 'sold' }
    return map[status] || 'off-shelf'
  }

  const loadAccounts = async () => {
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []

        const accountIdFromQuery = route.query.accountId
        if (accountIdFromQuery) {
          const accountId = parseInt(accountIdFromQuery as string)
          if (accounts.value.some(acc => acc.id === accountId)) {
            selectedAccountId.value = accountId
            await loadGoods()
            return
          }
        }

        if (accounts.value.length > 0 && !selectedAccountId.value) {
          selectedAccountId.value = accounts.value[0]?.id || null
          await loadGoods()
        }
      }
    } catch (error: any) {
      console.error('加载账号列表失败:', error)
    }
  }

  const loadGoods = async () => {
    if (!selectedAccountId.value) {
      showInfo('请先选择账号')
      return
    }

    goodsLoading.value = true
    try {
      const params = {
        xianyuAccountId: selectedAccountId.value,
        onlyOnSale: onlyOnSale.value,
        pageNum: goodsCurrentPage.value,
        pageSize: 20
      }

      const response = await getGoodsList(params)
      if (response.code === 0 || response.code === 200) {
        if (goodsCurrentPage.value === 1) {
          goodsList.value = response.data?.itemsWithConfig || []
        } else {
          goodsList.value.push(...(response.data?.itemsWithConfig || []))
        }
        goodsTotal.value = response.data?.totalCount || 0

        const goodsIdFromQuery = route.query.goodsId
        if (goodsIdFromQuery && goodsCurrentPage.value === 1) {
          const targetGoods = goodsList.value.find(g => g.item.xyGoodId === goodsIdFromQuery)
          if (targetGoods) {
            await selectGoods(targetGoods)
            return
          }
        }

        if (goodsCurrentPage.value === 1 && goodsList.value.length > 0 && !selectedGoods.value && !isMobile.value) {
          await selectGoods(goodsList.value[0]!)
        }

        checkAndLoadMore()
      } else {
        throw new Error(response.msg || '获取商品列表失败')
      }
    } catch (error: any) {
      console.error('加载商品列表失败:', error)
      goodsList.value = []
    } finally {
      goodsLoading.value = false
    }
  }

  const checkAndLoadMore = () => {
    nextTick(() => {
      if (!goodsListRef.value) return
      const { scrollHeight, clientHeight } = goodsListRef.value
      if (scrollHeight <= clientHeight && goodsList.value.length < goodsTotal.value) {
        goodsCurrentPage.value++
        loadGoods()
      }
    })
  }

  const handleGoodsScroll = () => {
    if (!goodsListRef.value || goodsLoading.value) return
    const { scrollTop, scrollHeight, clientHeight } = goodsListRef.value
    if (scrollTop + clientHeight >= scrollHeight - 50) {
      if (goodsList.value.length < goodsTotal.value) {
        goodsCurrentPage.value++
        loadGoods()
      }
    }
  }

  const handleAccountChange = () => {
    selectedGoods.value = null
    currentConfig.value = null
    goodsCurrentPage.value = 1
    loadGoods()
  }

  const loadSkuList = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      skuList.value = []
      return
    }
    try {
      const res = await getGoodsSkuList(selectedAccountId.value, selectedGoods.value.item.xyGoodId)
      if (res.code === 200 || res.code === 0) {
        skuList.value = (res.data || []).sort((a, b) => {
          if (a.propertySortOrder !== b.propertySortOrder) return (a.propertySortOrder ?? 0) - (b.propertySortOrder ?? 0)
          return (a.valueSortOrder ?? 0) - (b.valueSortOrder ?? 0)
        })
      } else {
        skuList.value = []
      }
    } catch {
      skuList.value = []
    }
  }

  const loadAllSkuConfigs = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      skuConfigs.value = new Map()
      return
    }
    try {
      const res = await getAutoDeliveryConfigsByGoodsId({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId
      })
      if (res.code === 200 || res.code === 0) {
        const configs = res.data || []
        const map = new Map<string, AutoDeliveryConfig>()
        configs.forEach(c => {
          const key = c.skuId || ''
          map.set(key, c)
        })
        skuConfigs.value = map
      } else {
        skuConfigs.value = new Map()
      }
    } catch {
      skuConfigs.value = new Map()
    }
  }

  const selectGoods = async (goods: GoodsItemWithConfig) => {
    selectedGoods.value = goods

    await loadSkuList()

    if (skuList.value.length > 1) {
      selectedSkuId.value = skuList.value[0]?.skuId || null
      await loadAllSkuConfigs()
    } else {
      selectedSkuId.value = null
      skuConfigs.value = new Map()
    }

    await loadConfig()
    await loadKamiConfigOptions()

    if (isMobile.value) { mobileView.value = 'config' }
  }

  const handleSkuChange = async () => {
    await loadConfig()
  }

  const loadKamiConfigOptions = async () => {
    try {
      const res = await getKamiConfigs()
      if (res.code === 200) {
        kamiConfigOptions.value = res.data || []
      }
    } catch {}
  }

  const loadConfig = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) return

    try {
      const baseReq: GetAutoDeliveryConfigReq = {
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        skuId: null
      }
      const baseResponse = await getAutoDeliveryConfig(baseReq)
      if (baseResponse.code === 0 || baseResponse.code === 200) {
        if (baseResponse.data) {
          configForm.value.autoConfirmShipment = baseResponse.data.autoConfirmShipment || 0
        } else {
          configForm.value.autoConfirmShipment = 0
        }
      }

      const req: GetAutoDeliveryConfigReq = {
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        skuId: selectedSkuId.value
      }

      const response = await getAutoDeliveryConfig(req)
      if (response.code === 0 || response.code === 200) {
        currentConfig.value = response.data || null
        if (response.data) {
          configForm.value.deliveryMode = response.data.deliveryMode || 1
          configForm.value.autoDeliveryContent = response.data.autoDeliveryContent || ''
          configForm.value.kamiConfigIds = response.data.kamiConfigIds || ''
          configForm.value.kamiDeliveryTemplate = response.data.kamiDeliveryTemplate || ''
          configForm.value.autoDeliveryImageUrl = response.data.autoDeliveryImageUrl || ''
          if (response.data.autoConfirmShipment != null) {
            configForm.value.autoConfirmShipment = response.data.autoConfirmShipment
          }
          if (response.data.autoAskFlower != null) {
            configForm.value.autoAskFlower = response.data.autoAskFlower
          }
          configForm.value.autoAskFlowerText = response.data.autoAskFlowerText || ''
        } else {
          configForm.value.deliveryMode = 1
          configForm.value.autoDeliveryContent = ''
          configForm.value.kamiConfigIds = ''
          configForm.value.kamiDeliveryTemplate = ''
          configForm.value.autoDeliveryImageUrl = ''
          configForm.value.autoAskFlower = 0
          configForm.value.autoAskFlowerText = ''
        }
      } else {
        throw new Error(response.msg || '获取配置失败')
      }
    } catch (error: any) {
      console.error('加载配置失败:', error)
      currentConfig.value = null
    }
  }

  const saveConfig = async () => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }

    if (configForm.value.deliveryMode === 1 && !configForm.value.autoDeliveryContent.trim()) {
      showInfo('请输入自动发货内容')
      return
    }
    if (configForm.value.deliveryMode === 2 && !configForm.value.kamiConfigIds) {
      showInfo('请绑定卡券库')
      return
    }

    saving.value = true
    try {
      const skuName = selectedSkuId.value
        ? (skuList.value.find(s => s.skuId === selectedSkuId.value)?.valueText || '')
        : ''

      const req: SaveAutoDeliveryConfigReq = {
        xianyuAccountId: selectedAccountId.value,
        xianyuGoodsId: selectedGoods.value.item.id,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        deliveryMode: configForm.value.deliveryMode,
        skuId: selectedSkuId.value,
        skuName,
        autoDeliveryContent: configForm.value.autoDeliveryContent.trim(),
        kamiConfigIds: configForm.value.kamiConfigIds,
        kamiDeliveryTemplate: configForm.value.kamiDeliveryTemplate.trim(),
        autoDeliveryImageUrl: configForm.value.autoDeliveryImageUrl.trim(),
        autoConfirmShipment: configForm.value.autoConfirmShipment,
        autoAskFlower: configForm.value.autoAskFlower,
        autoAskFlowerText: configForm.value.autoAskFlowerText.trim()
      }

      const response = await saveOrUpdateAutoDeliveryConfig(req)
      if (response.code === 0 || response.code === 200) {
        showSuccess('保存配置成功')
        currentConfig.value = response.data || null
        if (hasMultipleSku.value) {
          await loadAllSkuConfigs()
        }
      } else {
        throw new Error(response.msg || '保存配置失败')
      }
    } catch (error: any) {
      console.error('保存配置失败:', error)
    } finally {
      saving.value = false
    }
  }

  const toggleAutoDelivery = async (value: boolean) => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }

    try {
      const response = await updateAutoDeliveryStatus({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        xianyuAutoDeliveryOn: value ? 1 : 0
      })

      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动发货${value ? '开启' : '关闭'}成功`)
        if (selectedGoods.value) {
          selectedGoods.value.xianyuAutoDeliveryOn = value ? 1 : 0
        }
        const goodsItem = goodsList.value.find(item => item.item.xyGoodId === selectedGoods.value?.item.xyGoodId)
        if (goodsItem) {
          goodsItem.xianyuAutoDeliveryOn = value ? 1 : 0
        }
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      if (selectedGoods.value) {
        selectedGoods.value.xianyuAutoDeliveryOn = value ? 0 : 1
      }
    }
  }

  const toggleAutoConfirmShipment = async (value: boolean) => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }

    try {
      const response = await updateAutoConfirmShipment({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: selectedGoods.value.item.xyGoodId,
        autoConfirmShipment: value ? 1 : 0
      })

      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动确认发货${value ? '开启' : '关闭'}成功`)
        configForm.value.autoConfirmShipment = value ? 1 : 0
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      configForm.value.autoConfirmShipment = value ? 0 : 1
    }
  }

  const viewGoodsDetail = () => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }
    selectedGoodsId.value = selectedGoods.value.item.xyGoodId
    detailDialogVisible.value = true
  }

  const goToAutoReply = () => {
    if (!selectedGoods.value || !selectedAccountId.value) {
      showInfo('请先选择商品')
      return
    }
    router.push({
      path: '/auto-reply',
      query: {
        accountId: String(selectedAccountId.value),
        goodsId: selectedGoods.value.item.xyGoodId
      }
    })
  }

  const goToDeliveryRecords = () => {
    if (!selectedAccountId.value || !selectedGoods.value) {
      showInfo('请先选择商品')
      return
    }
    router.push({
      path: '/orders',
      query: {
        accountId: String(selectedAccountId.value),
        goodsId: selectedGoods.value.item.xyGoodId
      }
    })
  }

  const toggleOnlyOnSale = () => {
    onlyOnSale.value = !onlyOnSale.value
    goodsCurrentPage.value = 1
    selectedGoods.value = null
    loadGoods()
  }

  onMounted(() => {
    loadAccounts()
    checkScreenSize()
    window.addEventListener('resize', checkScreenSize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', checkScreenSize)
  })

  return {
    saving,
    accounts,
    selectedAccountId,
    goodsList,
    selectedGoods,
    currentConfig,
    configForm,
    skuList,
    selectedSkuId,
    skuConfigs,
    hasMultipleSku,
    goodsCurrentPage,
    goodsTotal,
    goodsLoading,
    goodsListRef,
    onlyOnSale,
    detailDialogVisible,
    selectedGoodsId,
    isMobile,
    mobileView,
    apiHintUrl,
    apiHintParamsJson,
    confirmShipmentUrl,
    confirmShipmentParamsJson,
    kamiConfigOptions,
    selectedKamiConfigId,
    isApiKamiSelected,
    isFixedKamiSelected,

    loadAccounts,
    loadGoods,
    handleAccountChange,
    selectGoods,
    saveConfig,
    toggleAutoDelivery,
    toggleAutoConfirmShipment,
    viewGoodsDetail,
    goToAutoReply,
    goToDeliveryRecords,
    handleSkuChange,
    copyApiUrl,
    copyApiParams,
    copyConfirmShipmentUrl,
    copyConfirmShipmentParams,
    handleGoodsScroll,
    goBackToGoods,
    toggleOnlyOnSale,
    formatTime,
    formatPrice,
    getStatusText,
    getStatusClass,
    checkScreenSize
  }
}

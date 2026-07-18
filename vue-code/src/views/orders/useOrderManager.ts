import { ref, reactive, computed } from 'vue'
import { queryDeliveryRecordList, confirmShipment, syncOrderHistory, triggerRuleDelivery } from '@/api/order'
import { getAccountList } from '@/api/account'
import type { DeliveryRecordVO, DeliveryRecordQueryReq } from '@/api/order'
import type { Account } from '@/types'
import { showSuccess, showError, showConfirm, showInfo } from '@/utils'
import { formatTime } from '@/utils'

export interface DeliveryRecordItem extends DeliveryRecordVO {
  confirming?: boolean
  manualDelivering?: boolean
}

export function useOrderManager() {
  const loading = ref(false)
  const syncingOrders = ref(false)
  const orderList = ref<DeliveryRecordItem[]>([])
  const total = ref(0)
  const accounts = ref<Account[]>([])

  const queryParams = reactive<DeliveryRecordQueryReq>({
    pageNum: 1,
    pageSize: 20
  })

  const dialogs = reactive({
    confirmShipment: false,
    filter: false
  })

  const confirmTarget = ref<DeliveryRecordItem | null>(null)

  const totalPages = computed(() => Math.ceil(total.value / (queryParams.pageSize || 20)))

  const loadAccounts = async () => {
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []
        if (accounts.value.length > 0 && !queryParams.xianyuAccountId) {
          queryParams.xianyuAccountId = accounts.value[0]?.id
        }
      }
    } catch (error: any) {
      console.error('加载账号列表失败:', error)
    }
  }

  const handleAccountChange = () => {
    queryParams.pageNum = 1
    queryParams.keyword = undefined
    loadOrders()
  }

  const getStatusColor = (state: number) => {
    return state === 1 ? '#34c759' : '#ff3b30'
  }

  const getStatusBg = (state: number) => {
    return state === 1 ? 'rgba(52, 199, 89, 0.1)' : 'rgba(255, 59, 48, 0.1)'
  }

  const getStatusText = (state: number) => {
    return state === 1 ? '成功' : '失败'
  }

  const loadOrders = async () => {
    loading.value = true
    try {
      const response = await queryDeliveryRecordList(queryParams)
      orderList.value = (response.data?.records || []).map(item => ({
        ...item,
        confirming: false,
        manualDelivering: false
      }))
      total.value = response.data?.total || 0
    } catch (error: any) {
      console.error('查询订单失败:', error)
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('查询订单失败: ' + (error.message || '未知错误'))
      }
      orderList.value = []
    } finally {
      loading.value = false
    }
  }

  const handleSyncOrders = async () => {
    if (!queryParams.xianyuAccountId) {
      showInfo('请先选择账号')
      return
    }

    syncingOrders.value = true
    try {
      const response = await syncOrderHistory(queryParams.xianyuAccountId)
      if (response.code !== 0 && response.code !== 200) {
        throw new Error(response.msg || '同步订单失败')
      }
      const data = response.data
      const skippedText = data?.skippedCount ? `，已忽略 ${data.skippedCount} 笔 30 天前订单` : ''
      showSuccess(`已同步近 30 天 ${data?.syncedCount || 0} 笔订单，其中退款订单 ${data?.refundCount || 0} 笔${skippedText}`)
      queryParams.pageNum = 1
      await loadOrders()
    } catch (error: any) {
      showError('同步订单失败: ' + (error.message || '未知错误'))
    } finally {
      syncingOrders.value = false
    }
  }

  const handleReset = () => {
    queryParams.keyword = undefined
    queryParams.pageNum = 1
    loadOrders()
  }

  const handlePageChange = (page: number) => {
    queryParams.pageNum = page
    loadOrders()
  }

  const handleSizeChange = (size: number) => {
    queryParams.pageSize = size
    queryParams.pageNum = 1
    loadOrders()
  }

  const copySId = (sid: string) => {
    navigator.clipboard.writeText(sid).then(() => {
      showSuccess('已复制')
    }).catch(() => {
      showError('复制失败')
    })
  }

  const handleConfirmShipment = async (row: DeliveryRecordItem) => {
    if (!row.orderId) {
      showError('订单ID为空')
      return
    }
    try {
      row.confirming = true
      await confirmShipment({
        xianyuAccountId: (row as any).xianyuAccountId,
        orderId: row.orderId
      })

      showSuccess('确认发货成功')
      loadOrders()
    } catch (error: any) {
      showError('确认发货失败: ' + (error.message || '未知错误'))
    } finally {
      row.confirming = false
    }
  }

  const handleRuleDelivery = async (row: DeliveryRecordItem) => {
    if (!row.orderId || !row.xianyuAccountId || !row.xyGoodsId) {
      showError('订单信息不完整，无法按规则发货')
      return false
    }
    try {
      row.manualDelivering = true
      const response = await triggerRuleDelivery({
        xianyuAccountId: row.xianyuAccountId,
        xyGoodsId: row.xyGoodsId,
        orderId: row.orderId
      })
      if (response.code !== 0 && response.code !== 200) {
        throw new Error(response.msg || '按规则发货失败')
      }
      showSuccess(response.data || '已按当前卡券与发货规则完成补发')
      await loadOrders()
      return true
    } catch (error: any) {
      showError('按规则发货失败：' + (error.message || '请检查卡券库存和发货配置'))
      return false
    } finally {
      row.manualDelivering = false
    }
  }

  return {
    loading,
    syncingOrders,
    orderList,
    total,
    accounts,
    queryParams,
    dialogs,
    confirmTarget,
    totalPages,
    loadAccounts,
    loadOrders,
    handleSyncOrders,
    handleAccountChange,
    handleReset,
    handlePageChange,
    handleSizeChange,
    copySId,
    handleConfirmShipment,
    handleRuleDelivery,
    getStatusColor,
    getStatusBg,
    getStatusText,
    formatTime
  }
}

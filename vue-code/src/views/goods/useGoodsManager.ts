import { ref, reactive, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getAccountList } from '@/api/account'
import {
  getGoodsList,
  refreshGoods,
  getGoodsDetail,
  updateAutoDeliveryStatus,
  updateAutoReplyStatus,
  deleteItem,
  syncSingleItem,
  getSyncProgress,
  checkSyncing,
  batchUpdateGoodsConfig
} from '@/api/goods'
import { showSuccess, showError, showInfo, showConfirm } from '@/utils'
import { getGoodsStatusText, formatPrice, formatTime } from '@/utils'
import type { Account } from '@/types'
import type { BatchUpdateGoodsConfigReq, GoodsItemWithConfig, SyncProgressResponse } from '@/api/goods'

export function useGoodsManager() {
  const router = useRouter()

  const loading = ref(false)
  const refreshing = ref(false)
  const accounts = ref<Account[]>([])
  const selectedAccountId = ref<number | null>(null)
  const statusFilter = ref<string>('')
  const goodsList = ref<GoodsItemWithConfig[]>([])
  const currentPage = ref(1)
  const pageSize = ref(20)
  const total = ref(0)
  const selectedGoodsIds = ref<string[]>([])
  const batchUpdating = ref(false)

  const dialogs = reactive({
    detail: false,
    deleteConfirm: false,
    filter: false
  })

  const selectedGoodsId = ref<string>('')
  const selectedGoods = ref<GoodsItemWithConfig | null>(null)
  const deleteTarget = ref<{ id: string; title: string } | null>(null)

  const syncProgress = ref<SyncProgressResponse | null>(null)
  const syncing = ref(false)
  let syncProgressTimer: ReturnType<typeof setInterval> | null = null

  const stopSyncPolling = () => {
    if (syncProgressTimer) {
      clearInterval(syncProgressTimer)
      syncProgressTimer = null
    }
  }

  const pollSyncProgress = async (syncId: string) => {
    try {
      const response = await getSyncProgress(syncId)
      if (response.code === 0 || response.code === 200) {
        if (response.data) {
          syncProgress.value = response.data
          if (response.data.isCompleted || !response.data.isRunning) {
            stopSyncPolling()
            syncing.value = false
            refreshing.value = false
            if (response.data.verificationRequired) {
              showInfo(`商品基础信息已同步；闲鱼要求安全验证，${response.data.deferredCount || 1} 个商品详情暂未补充。请在闲鱼客户端确认账号状态后，在账号管理使用“凭证更新”重新扫码，再重试同步。`)
            } else if (response.data.successCount && response.data.successCount > 0) {
              showSuccess(`详情同步完成: 成功${response.data.successCount}个, 失败${response.data.failedCount}个`)
            } else if (response.data.failedCount > 0) {
              showInfo(`商品基础信息已同步，但有${response.data.failedCount}个详情暂未补全`)
            }
            await loadGoods()
          }
        }
      }
    } catch (error) {
      console.error('获取同步进度失败:', error)
    }
  }

  const startSyncPolling = (syncId: string) => {
    stopSyncPolling()
    syncing.value = true
    syncProgressTimer = setInterval(() => {
      void pollSyncProgress(syncId)
    }, 1000)
    void pollSyncProgress(syncId)
  }

  onUnmounted(() => {
    stopSyncPolling()
  })

  // Computed
  const totalPages = computed(() => Math.ceil(total.value / pageSize.value))
  const accountName = computed(() => {
    if (!selectedAccountId.value) return ''
    const acc = accounts.value.find(a => a.id === selectedAccountId.value)
    return acc?.accountNote || acc?.unb || ''
  })
  const selectedGoodsCount = computed(() => selectedGoodsIds.value.length)

  // 加载账号列表
  const loadAccounts = async () => {
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []
        if (accounts.value.length > 0 && !selectedAccountId.value) {
          selectedAccountId.value = accounts.value[0]?.id || null
          await loadGoods()
        }
      }
    } catch (error: any) {
      console.error('加载账号列表失败:', error)
    }
  }

  // 加载商品列表
  const loadGoods = async () => {
    if (!selectedAccountId.value) {
      showInfo('请先选择账号')
      return
    }

    loading.value = true
    try {
      const params: any = {
        xianyuAccountId: selectedAccountId.value,
        pageNum: currentPage.value,
        pageSize: pageSize.value
      }
      if (statusFilter.value !== '') {
        params.status = parseInt(statusFilter.value)
      }
      const response = await getGoodsList(params)
      if (response.code === 0 || response.code === 200) {
        goodsList.value = response.data?.itemsWithConfig || []
        total.value = response.data?.totalCount || 0
      }
    } catch (error: any) {
      console.error('加载商品列表失败:', error)
      goodsList.value = []
    } finally {
      loading.value = false
    }
  }

  // 刷新商品数据
  const handleRefresh = async () => {
    if (!selectedAccountId.value) {
      showInfo('请先选择账号')
      return
    }
    refreshing.value = true
    try {
      const response = await refreshGoods(selectedAccountId.value)
      if (response.code === 0 || response.code === 200) {
        if (response.data && response.data.success) {
          // 基础商品列表已经同步入库，先显示它；详情补全在后台进行，不再让页面等待全部请求结束。
          await loadGoods()
          if (response.data.syncId) {
            showSuccess(`已同步${response.data.successCount || 0}个商品，正在补全详情`)
            startSyncPolling(response.data.syncId)
          } else {
            showSuccess(`已同步${response.data.successCount || 0}个商品`)
            refreshing.value = false
          }
        } else {
          showError(response.data?.message || '刷新商品数据失败')
          refreshing.value = false
        }
      } else {
        throw new Error(response.msg || '刷新商品数据失败')
      }
    } catch (error: any) {
      console.error('刷新商品数据失败:', error)
      showError(error?.message || '刷新商品数据失败，请稍后重试')
      refreshing.value = false
    }
  }

  // 账号变更
  const handleAccountChange = () => {
    currentPage.value = 1
    clearGoodsSelection()
    loadGoods()
  }

  const toggleGoodsSelection = (xyGoodsId: string, selected: boolean) => {
    const ids = new Set(selectedGoodsIds.value)
    if (selected) {
      ids.add(xyGoodsId)
    } else {
      ids.delete(xyGoodsId)
    }
    selectedGoodsIds.value = Array.from(ids)
  }

  const togglePageSelection = (selected: boolean) => {
    const ids = new Set(selectedGoodsIds.value)
    goodsList.value.forEach((goods) => {
      if (selected) {
        ids.add(goods.item.xyGoodId)
      } else {
        ids.delete(goods.item.xyGoodId)
      }
    })
    selectedGoodsIds.value = Array.from(ids)
  }

  const clearGoodsSelection = () => {
    selectedGoodsIds.value = []
  }

  const updateSelectedGoodsConfig = async (options: Omit<BatchUpdateGoodsConfigReq, 'xianyuAccountId' | 'xyGoodsIds'>) => {
    if (!selectedAccountId.value || selectedGoodsIds.value.length === 0) {
      showInfo('请先选择要批量配置的商品')
      return false
    }

    batchUpdating.value = true
    try {
      const response = await batchUpdateGoodsConfig({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsIds: selectedGoodsIds.value,
        ...options
      })
      if (response.code !== 0 && response.code !== 200) {
        throw new Error(response.msg || '批量配置失败')
      }
      showSuccess(response.data?.message || '批量配置成功')
      clearGoodsSelection()
      await loadGoods()
      return true
    } catch (error: any) {
      console.error('批量配置商品失败:', error)
      showError(error?.message || '批量配置失败，请稍后重试')
      return false
    } finally {
      batchUpdating.value = false
    }
  }

  // 状态筛选
  const handleStatusFilter = () => {
    currentPage.value = 1
    loadGoods()
  }

  // 分页
  const handlePageChange = (page: number) => {
    currentPage.value = page
    loadGoods()
  }

  // 查看详情
  const viewDetail = (xyGoodId: string) => {
    selectedGoodsId.value = xyGoodId
    dialogs.detail = true
  }

  // 配置自动发货
  const configAutoDelivery = (item: GoodsItemWithConfig) => {
    router.push({
      path: '/auto-delivery',
      query: {
        accountId: selectedAccountId.value?.toString(),
        goodsId: item.item.xyGoodId
      }
    })
  }

  // 切换自动发货
  const toggleAutoDelivery = async (item: GoodsItemWithConfig, value: boolean) => {
    if (!selectedAccountId.value) return
    try {
      const response = await updateAutoDeliveryStatus({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId,
        xianyuAutoDeliveryOn: value ? 1 : 0
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动发货${value ? '开启' : '关闭'}成功`)
        item.xianyuAutoDeliveryOn = value ? 1 : 0
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      item.xianyuAutoDeliveryOn = value ? 0 : 1
    }
  }

  // 切换自动回复
  const toggleAutoReply = async (item: GoodsItemWithConfig, value: boolean) => {
    if (!selectedAccountId.value) return
    try {
      const response = await updateAutoReplyStatus({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: item.item.xyGoodId,
        xianyuAutoReplyOn: value ? 1 : 0
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动回复${value ? '开启' : '关闭'}成功`)
        item.xianyuAutoReplyOn = value ? 1 : 0
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: any) {
      console.error('操作失败:', error)
      item.xianyuAutoReplyOn = value ? 0 : 1
    }
  }

  // 删除商品
  const confirmDelete = (xyGoodId: string, title: string) => {
    deleteTarget.value = { id: xyGoodId, title }
    dialogs.deleteConfirm = true
  }

  const executeDelete = async () => {
    if (!selectedAccountId.value || !deleteTarget.value) return
    try {
      const response = await deleteItem({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: deleteTarget.value.id
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess('商品删除成功')
        dialogs.deleteConfirm = false
        deleteTarget.value = null
        await loadGoods()
      } else {
        throw new Error(response.msg || '删除失败')
      }
    } catch (error: any) {
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!error.messageShown) {
        showError('删除失败: ' + error.message)
      }
    }
  }

  const syncSingleGoods = async (xyGoodId: string) => {
    if (!selectedAccountId.value) return
    try {
      const response = await syncSingleItem({
        xianyuAccountId: selectedAccountId.value,
        xyGoodsId: xyGoodId
      })
      if ((response.code === 0 || response.code === 200) && response.data?.success) {
        showSuccess('同步成功')
        loadGoods()
      } else {
        throw new Error(response.data?.message || response.msg || '同步失败')
      }
    } catch (error: any) {
      console.error('同步失败:', error)
      showError(error.message || '同步失败')
    }
  }

  return {
    loading,
    refreshing,
    syncing,
    syncProgress,
    accounts,
    selectedAccountId,
    statusFilter,
    goodsList,
    currentPage,
    pageSize,
    total,
    totalPages,
    accountName,
    selectedGoodsIds,
    selectedGoodsCount,
    batchUpdating,
    dialogs,
    selectedGoodsId,
    selectedGoods,
    deleteTarget,
    loadAccounts,
    loadGoods,
    handleRefresh,
    handleAccountChange,
    toggleGoodsSelection,
    togglePageSelection,
    clearGoodsSelection,
    updateSelectedGoodsConfig,
    handleStatusFilter,
    handlePageChange,
    viewDetail,
    configAutoDelivery,
    toggleAutoDelivery,
    toggleAutoReply,
    confirmDelete,
    executeDelete,
    getGoodsStatusText,
    formatPrice,
    formatTime,
    syncSingleGoods
  }
}

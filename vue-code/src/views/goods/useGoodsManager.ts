import { ref, reactive, computed, onUnmounted } from 'vue'
import { getAccountList } from '@/api/account'
import {
  getGoodsList,
  refreshGoods,
  updateAutoDeliveryStatus,
  updateAutoReplyStatus,
  deleteItem,
  getSyncProgress,
  batchUpdateGoodsConfig
} from '@/api/goods'
import { showSuccess, showError, showInfo } from '@/utils'
import { getGoodsStatusText, formatPrice, formatTime } from '@/utils'
import type { Account } from '@/types'
import type { BatchUpdateGoodsConfigReq, GoodsItemWithConfig, SyncProgressResponse } from '@/api/goods'

export function useGoodsManager() {
  const SYNC_PROGRESS_STALE_MS = 60_000
  const SYNC_PROGRESS_MAX_MS = 120_000
  const errorMessage = (error: unknown, fallback: string) => error instanceof Error ? error.message : fallback
  const messageWasShown = (error: unknown) => typeof error === 'object'
    && error !== null
    && 'messageShown' in error
    && Boolean(error.messageShown)
  const loading = ref(false)
  const refreshing = ref(false)
  const accounts = ref<Account[]>([])
  // 商品列表固定以“所有账号”为初始范围，0 表示不传账号筛选条件。
  const selectedAccountId = ref<number | null>(0)
  const statusFilter = ref<string>('')
  const goodsList = ref<GoodsItemWithConfig[]>([])
  const currentPage = ref(1)
  const pageSize = ref(20)
  const total = ref(0)
  const selectedGoodsTargets = ref<Record<string, { accountId: number; goodsId: string }>>({})
  const batchUpdating = ref(false)

  const dialogs = reactive({
    detail: false,
    deleteConfirm: false,
    filter: false
  })

  const selectedGoodsId = ref<string>('')
  const selectedGoodsAccountId = ref<number | null>(null)
  const selectedGoods = ref<GoodsItemWithConfig | null>(null)
  const deleteTarget = ref<{ accountId: number; id: string; title: string } | null>(null)

  const syncProgress = ref<SyncProgressResponse | null>(null)
  const syncing = ref(false)
  let syncProgressTimer: ReturnType<typeof setInterval> | null = null
  let syncingAllAccounts = false
  let syncJobs: Array<{
    syncId: string
    accountName: string
    progress?: SyncProgressResponse
    pollFailures: number
    abandoned: boolean
    startedAt: number
    lastChangedAt: number
    lastCompletedCount: number
  }> = []

  const stopSyncPolling = () => {
    if (syncProgressTimer) {
      clearInterval(syncProgressTimer)
      syncProgressTimer = null
    }
  }

  const pollSyncProgress = async () => {
    await Promise.all(syncJobs.filter(job => !job.abandoned && !(job.progress?.isCompleted || job.progress?.isRunning === false)).map(async (job) => {
      try {
        const response = await getSyncProgress(job.syncId)
        if ((response.code === 0 || response.code === 200) && response.data) {
          const completedCount = response.data.completedCount || 0
          if (completedCount !== job.lastCompletedCount || response.data.isCompleted || response.data.isRunning === false) {
            job.lastChangedAt = Date.now()
            job.lastCompletedCount = completedCount
          }
          job.progress = response.data
          job.pollFailures = 0
        } else {
          job.pollFailures++
        }
      } catch (error) {
        job.pollFailures++
        console.error(`获取账号 ${job.accountName} 的同步进度失败:`, error)
      }
      const now = Date.now()
      if (
        job.pollFailures >= 5
        || now - job.startedAt > SYNC_PROGRESS_MAX_MS
        || (job.progress?.isRunning && now - job.lastChangedAt > SYNC_PROGRESS_STALE_MS)
      ) {
        job.abandoned = true
      }
    }))

    const progresses = syncJobs.flatMap(job => job.progress ? [job.progress] : [])
    if (progresses.length > 0) {
      const first = progresses[0]!
      syncProgress.value = {
        ...first,
        totalCount: progresses.reduce((sum, item) => sum + item.totalCount, 0),
        completedCount: progresses.reduce((sum, item) => sum + item.completedCount, 0),
        successCount: progresses.reduce((sum, item) => sum + item.successCount, 0),
        failedCount: progresses.reduce((sum, item) => sum + item.failedCount, 0),
        deferredCount: progresses.reduce((sum, item) => sum + item.deferredCount, 0),
        verificationRequired: progresses.some(item => item.verificationRequired),
        isCompleted: syncJobs.every(job => job.abandoned || job.progress?.isCompleted || job.progress?.isRunning === false),
        isRunning: syncJobs.some(job => !job.abandoned && job.progress?.isRunning !== false && !job.progress?.isCompleted)
      }
    }

    const completed = syncJobs.length > 0 && syncJobs.every(job =>
      job.abandoned || job.progress?.isCompleted || job.progress?.isRunning === false)
    if (!completed) return

    stopSyncPolling()
    syncing.value = false
    refreshing.value = false
    const progress = syncProgress.value
    const abandonedCount = syncJobs.filter(job => job.abandoned).length
    const syncScope = syncingAllAccounts ? '所有账号' : (syncJobs[0]?.accountName || '当前账号')
    if (progress?.verificationRequired) {
      showInfo(`${syncScope}的商品基础信息已同步；有 ${progress.deferredCount || 1} 个商品因闲鱼安全验证暂未补全详情。`)
    } else if (abandonedCount > 0) {
      showInfo(`商品同步已完成，但有 ${abandonedCount} 个账号的详情进度暂时无法确认。`)
    } else if (progress) {
      showSuccess(`${syncScope}的详情同步完成：成功 ${progress.successCount} 个，失败 ${progress.failedCount} 个`)
    }
    await loadGoods()
  }

  const startSyncPolling = (jobs: Array<{ syncId: string; accountName: string }>, allAccounts: boolean) => {
    stopSyncPolling()
    syncingAllAccounts = allAccounts
    const now = Date.now()
    syncJobs = jobs.map(job => ({
      ...job,
      pollFailures: 0,
      abandoned: false,
      startedAt: now,
      lastChangedAt: now,
      lastCompletedCount: -1
    }))
    syncing.value = true
    syncProgressTimer = setInterval(() => {
      void pollSyncProgress()
    }, 1000)
    void pollSyncProgress()
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
  const selectedGoodsIds = computed(() => Object.keys(selectedGoodsTargets.value))
  const selectedGoodsCount = computed(() => selectedGoodsIds.value.length)
  const goodsKey = (item: GoodsItemWithConfig) => `${item.item.xianyuAccountId}:${item.item.xyGoodId}`
  const accountDisplayName = (accountId: number) => {
    const account = accounts.value.find(item => item.id === accountId)
    return account?.accountNote || account?.unb || `账号 ${accountId}`
  }

  // 加载账号列表
  const loadAccounts = async () => {
    try {
      const response = await getAccountList()
      if (response.code === 0 || response.code === 200) {
        accounts.value = response.data?.accounts || []
        await loadGoods()
      }
    } catch (error: unknown) {
      console.error('加载账号列表失败:', error)
    }
  }

  // 加载商品列表
  const loadGoods = async () => {
    if (selectedAccountId.value === null) {
      showInfo('请先选择账号')
      return
    }

    loading.value = true
    try {
      const params: Parameters<typeof getGoodsList>[0] = {
        xianyuAccountId: selectedAccountId.value === 0 ? undefined : selectedAccountId.value,
        onlyOnSale: false,
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
    } catch (error: unknown) {
      console.error('加载商品列表失败:', error)
      goodsList.value = []
    } finally {
      loading.value = false
    }
  }

  // 刷新商品数据
  const handleRefresh = async () => {
    if (selectedAccountId.value === null) {
      showInfo('请先选择账号')
      return
    }
    const targetAccounts = selectedAccountId.value === 0
      ? accounts.value.filter(account => account.status === 1)
      : accounts.value.filter(account => account.id === selectedAccountId.value)
    if (targetAccounts.length === 0) {
      showInfo('没有可同步的正常账号')
      return
    }
    refreshing.value = true
    syncProgress.value = null
    try {
      const jobs: Array<{ syncId: string; accountName: string }> = []
      let successAccounts = 0
      let successItems = 0
      const failedAccounts: Array<{ name: string; reason: string }> = []
      for (const account of targetAccounts) {
        const accountName = account.accountNote || account.unb || `账号 ${account.id}`
        try {
          const response = await refreshGoods(account.id)
          if ((response.code === 0 || response.code === 200) && response.data?.success) {
            successAccounts++
            successItems += response.data.successCount || 0
            if (response.data.syncId) jobs.push({ syncId: response.data.syncId, accountName })
          } else {
            failedAccounts.push({
              name: accountName,
              reason: response.msg || '商品列表同步未完成'
            })
          }
        } catch (error) {
          console.error(`同步账号 ${accountName} 失败:`, error)
          failedAccounts.push({ name: accountName, reason: errorMessage(error, '请求失败') })
        }
      }
      await loadGoods()
      if (successAccounts > 0) {
        const failureText = failedAccounts.length > 0 ? `，${failedAccounts.length} 个账号失败` : ''
        showSuccess(`已同步 ${successAccounts} 个账号、${successItems} 个商品${failureText}`)
      } else {
        const failures = failedAccounts
          .map(({ name, reason }) => `${name}：${reason}`)
          .join('；')
        showError(failures || '商品同步失败，请稍后重试')
      }
      if (jobs.length > 0) {
        startSyncPolling(jobs, selectedAccountId.value === 0)
      } else {
        refreshing.value = false
      }
    } catch (error: unknown) {
      console.error('刷新商品数据失败:', error)
      showError(errorMessage(error, '刷新商品数据失败，请稍后重试'))
      refreshing.value = false
    }
  }

  // 账号变更
  const handleAccountChange = () => {
    currentPage.value = 1
    clearGoodsSelection()
    loadGoods()
  }

  const toggleGoodsSelection = (item: GoodsItemWithConfig, selected: boolean) => {
    const targets = { ...selectedGoodsTargets.value }
    const key = goodsKey(item)
    if (selected) {
      targets[key] = { accountId: item.item.xianyuAccountId, goodsId: item.item.xyGoodId }
    } else {
      delete targets[key]
    }
    selectedGoodsTargets.value = targets
  }

  const togglePageSelection = (selected: boolean) => {
    const targets = { ...selectedGoodsTargets.value }
    goodsList.value.forEach((goods) => {
      const key = goodsKey(goods)
      if (selected) {
        targets[key] = { accountId: goods.item.xianyuAccountId, goodsId: goods.item.xyGoodId }
      } else {
        delete targets[key]
      }
    })
    selectedGoodsTargets.value = targets
  }

  const clearGoodsSelection = () => {
    selectedGoodsTargets.value = {}
  }

  const updateSelectedGoodsConfig = async (
    options: Omit<BatchUpdateGoodsConfigReq, 'xianyuAccountId' | 'xyGoodsIds'>
  ) => {
    const targets = Object.values(selectedGoodsTargets.value)
    if (targets.length === 0) {
      showInfo('请先选择要批量配置的商品')
      return false
    }

    batchUpdating.value = true
    try {
      const grouped = new Map<number, string[]>()
      targets.forEach(target => grouped.set(target.accountId, [
        ...(grouped.get(target.accountId) || []), target.goodsId
      ]))
      for (const [accountId, xyGoodsIds] of grouped) {
        const response = await batchUpdateGoodsConfig({ xianyuAccountId: accountId, xyGoodsIds, ...options })
        if (response.code !== 0 && response.code !== 200) {
          throw new Error(`${accountDisplayName(accountId)}：${response.msg || '批量配置失败'}`)
        }
      }
      showSuccess(`已完成 ${grouped.size} 个账号、${targets.length} 个商品的批量配置`)
      clearGoodsSelection()
      await loadGoods()
      return true
    } catch (error: unknown) {
      console.error('批量配置商品失败:', error)
      showError(errorMessage(error, '批量配置失败，请稍后重试'))
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
  const viewDetail = (item: GoodsItemWithConfig) => {
    selectedGoodsId.value = item.item.xyGoodId
    selectedGoodsAccountId.value = item.item.xianyuAccountId
    dialogs.detail = true
  }

  // 切换自动发货
  const toggleAutoDelivery = async (item: GoodsItemWithConfig, value: boolean) => {
    const accountId = item.item.xianyuAccountId
    try {
      const response = await updateAutoDeliveryStatus({
        xianyuAccountId: accountId,
        xyGoodsId: item.item.xyGoodId,
        xianyuAutoDeliveryOn: value ? 1 : 0
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动发货${value ? '开启' : '关闭'}成功`)
        item.xianyuAutoDeliveryOn = value ? 1 : 0
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: unknown) {
      console.error('操作失败:', error)
      item.xianyuAutoDeliveryOn = value ? 0 : 1
    }
  }

  // 切换自动回复
  const toggleAutoReply = async (item: GoodsItemWithConfig, value: boolean) => {
    const accountId = item.item.xianyuAccountId
    try {
      const response = await updateAutoReplyStatus({
        xianyuAccountId: accountId,
        xyGoodsId: item.item.xyGoodId,
        xianyuAutoReplyOn: value ? 1 : 0
      })
      if (response.code === 0 || response.code === 200) {
        showSuccess(`自动回复${value ? '开启' : '关闭'}成功`)
        item.xianyuAutoReplyOn = value ? 1 : 0
      } else {
        throw new Error(response.msg || '操作失败')
      }
    } catch (error: unknown) {
      console.error('操作失败:', error)
      item.xianyuAutoReplyOn = value ? 0 : 1
    }
  }

  // 删除商品
  const confirmDelete = (item: GoodsItemWithConfig) => {
    deleteTarget.value = {
      accountId: item.item.xianyuAccountId,
      id: item.item.xyGoodId,
      title: item.item.title
    }
    dialogs.deleteConfirm = true
  }

  const executeDelete = async () => {
    if (!deleteTarget.value) return
    try {
      const response = await deleteItem({
        xianyuAccountId: deleteTarget.value.accountId,
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
    } catch (error: unknown) {
      // 只有在错误消息未显示过时才弹出提示（避免重复显示）
      if (!messageWasShown(error)) {
        showError('删除失败: ' + errorMessage(error, '未知错误'))
      }
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
    selectedGoodsAccountId,
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
    toggleAutoDelivery,
    toggleAutoReply,
    confirmDelete,
    executeDelete,
    getGoodsStatusText,
    formatPrice,
    formatTime
  }
}

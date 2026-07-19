<script setup lang="ts">
import { onMounted, ref, reactive, computed, inject, defineComponent, h, type Component } from 'vue'
import { useRouter } from 'vue-router'
import { useGoodsManager } from './useGoodsManager'
import { getKamiConfigs } from '@/api/kami-config'
import type { KamiConfig } from '@/api/kami-config'
import type { GoodsItemWithConfig } from '@/api/goods'
import './goods.css'
import '@/styles/header-selectors.css'

import IconShoppingBag from '@/components/icons/IconShoppingBag.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconChevronDown from '@/components/icons/IconChevronDown.vue'
import IconChevronLeft from '@/components/icons/IconChevronLeft.vue'
import IconChevronRight from '@/components/icons/IconChevronRight.vue'

import GoodsTable from './components/GoodsTable.vue'
import GoodsDetail from './components/GoodsDetail.vue'
import GoodsConfigDialog from './components/GoodsConfigDialog.vue'

const router = useRouter()

const {
  loading,
  refreshing,
  syncing,
  syncProgress,
  accounts,
  selectedAccountId,
  statusFilter,
  goodsList,
  currentPage,
  total,
  totalPages,
  selectedGoodsIds,
  selectedGoodsCount,
  batchUpdating,
  dialogs,
  selectedGoodsId,
  selectedGoodsAccountId,
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
  syncSingleGoods
} = useGoodsManager()

defineOptions({ name: 'GoodsIndex' })

// 商品页是配置入口：每个商品的发货、AI 与关键词开关都从这里统一进入。
const configDialogVisible = ref(false)
const configTarget = ref<GoodsItemWithConfig | null>(null)
const configTargetAccountId = computed(() => configTarget.value?.item.xianyuAccountId ?? null)
const accountNames = computed<Record<number, string>>(() => Object.fromEntries(
  accounts.value.map(account => [account.id, account.accountNote || account.unb || `账号 ${account.id}`])
))

const openGoodsConfig = (item: GoodsItemWithConfig) => {
  configTarget.value = item
  configDialogVisible.value = true
}

const handleGoodsConfigSaved = () => {
  void loadGoods()
}

const openKeywordRules = () => {
  const item = configTarget.value
  if (!item) return
  configDialogVisible.value = false
  router.push({
    path: '/auto-reply',
    query: {
      accountId: String(item.item.xianyuAccountId),
      goodsId: item.item.xyGoodId
    }
  })
}

// 下拉刷新相关状态
const pullRefreshState = ref<'idle' | 'pulling' | 'ready' | 'refreshing'>('idle')
const pullDistance = ref(0)
const tableWrapRef = ref<HTMLElement | null>(null)
const startY = ref(0)
const isMobile = ref(false)

// 检测是否为手机模式
const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
}

// 计算下拉刷新的显示距离
const pullRefreshDistance = computed(() => {
  const maxDistance = 80
  return Math.min(pullDistance.value, maxDistance)
})

// 处理触摸开始
const handleTouchStart = (e: TouchEvent) => {
  if (!isMobile.value || !tableWrapRef.value) return
  
  // 只在列表顶部时才允许下拉
  if (tableWrapRef.value.scrollTop === 0) {
    startY.value = e.touches[0]?.clientY ?? 0
    pullDistance.value = 0
    pullRefreshState.value = 'idle'
  }
}

// 处理触摸移动
const handleTouchMove = (e: TouchEvent) => {
  if (!isMobile.value || !tableWrapRef.value || startY.value === 0) return
  
  if (tableWrapRef.value.scrollTop === 0) {
    const currentY = e.touches[0]?.clientY ?? 0
    const distance = currentY - startY.value
    
    if (distance > 0) {
      e.preventDefault()
      pullDistance.value = distance
      
      if (distance < 60) {
        pullRefreshState.value = 'pulling'
      } else {
        pullRefreshState.value = 'ready'
      }
    }
  }
}

// 处理触摸结束
const handleTouchEnd = async () => {
  if (!isMobile.value) return
  
  if (pullRefreshState.value === 'ready' && pullDistance.value >= 60) {
    pullRefreshState.value = 'refreshing'
    await handleRefresh()
    // 动画反弹
    pullDistance.value = 0
    pullRefreshState.value = 'idle'
  } else {
    // 回弹动画
    pullDistance.value = 0
    pullRefreshState.value = 'idle'
  }
  
  startY.value = 0
}

// 获取导航栏内容设置函数
const setHeaderContent = inject<(content: Component) => void>('setHeaderContent')

// 创建导航栏选择器组件
const HeaderSelectors = defineComponent({
  setup() {
    return () => h('div', { class: 'header-selectors' }, [
      h('div', { class: 'header-select-wrap' }, [
        h('select', {
          class: 'header-select',
          value: selectedAccountId.value,
          onChange: (e: Event) => {
            const target = e.target as HTMLSelectElement
            selectedAccountId.value = target.value ? parseInt(target.value) : null
            handleAccountChange()
          }
        }, [
          h('option', { value: '', disabled: true }, '选择账号'),
          h('option', { value: '0' }, '所有账号'),
          ...accounts.value.map(acc => 
            h('option', { value: acc.id.toString() }, acc.accountNote || acc.unb)
          )
        ]),
        h(IconChevronDown, { class: 'header-select-icon' })
      ]),
      h('div', { class: 'header-select-wrap' }, [
        h('select', {
          class: 'header-select',
          value: statusFilter.value,
          onChange: (e: Event) => {
            const target = e.target as HTMLSelectElement
            statusFilter.value = target.value
            handleStatusFilter()
          }
        }, [
          h('option', { value: '' }, '全部状态'),
          h('option', { value: '0' }, '在售'),
          h('option', { value: '1' }, '已下架'),
          h('option', { value: '2' }, '已售出')
        ]),
        h(IconChevronDown, { class: 'header-select-icon' })
      ]),
      h('button', {
        class: ['header-refresh-btn', { 'header-refresh-btn--loading': refreshing.value || syncing.value }],
        disabled: refreshing.value || syncing.value || selectedAccountId.value === null,
        onClick: handleRefresh
      }, [
        h(IconRefresh, { class: 'header-refresh-icon' })
      ])
    ])
  }
})

onMounted(() => {
  loadAccounts()
  checkMobile()
  window.addEventListener('resize', checkMobile)
  
  // 只在手机模式下设置导航栏内容
  if (setHeaderContent) {
    setHeaderContent(HeaderSelectors)
  }
})

// 分页按钮列表
const getPageButtons = () => {
  const buttons: number[] = []
  const maxVisible = 5
  let start = Math.max(1, currentPage.value - Math.floor(maxVisible / 2))
  const end = Math.min(totalPages.value, start + maxVisible - 1)
  start = Math.max(1, end - maxVisible + 1)
  for (let i = start; i <= end; i++) {
    buttons.push(i)
  }
  return buttons
}

const batchDialogVisible = ref(false)
const loadingKamiConfigs = ref(false)
const kamiConfigs = ref<KamiConfig[]>([])
const batchForm = reactive({
  xianyuAutoDeliveryOn: '' as '' | '1' | '0',
  xianyuAutoReplyOn: '' as '' | '1' | '0',
  xianyuKeywordReplyOn: '' as '' | '1' | '0',
  kamiConfigId: '' as '' | number
})

const availableKamiConfigs = computed(() => kamiConfigs.value.filter((config) =>
  config.xianyuAccountId == null || (selectedAccountId.value !== 0 && config.xianyuAccountId === selectedAccountId.value)
))

const sourceTypeText = (config: KamiConfig) => {
  if (config.sourceType === 3) return '固定内容'
  if (config.sourceType === 2) return 'API'
  return '本地卡密'
}

const resetBatchForm = () => {
  batchForm.xianyuAutoDeliveryOn = ''
  batchForm.xianyuAutoReplyOn = ''
  batchForm.xianyuKeywordReplyOn = ''
  batchForm.kamiConfigId = ''
}

const openBatchDialog = async () => {
  if (selectedGoodsCount.value === 0) return
  resetBatchForm()
  batchDialogVisible.value = true
  loadingKamiConfigs.value = true
  try {
    const response = await getKamiConfigs()
    if (response.code === 0 || response.code === 200) {
      kamiConfigs.value = response.data || []
    }
  } finally {
    loadingKamiConfigs.value = false
  }
}

const handleBatchKamiChange = () => {
  if (batchForm.kamiConfigId !== '') {
    batchForm.xianyuAutoDeliveryOn = '1'
  }
}

const submitBatchUpdate = async () => {
  const hasOperation = batchForm.xianyuAutoDeliveryOn !== ''
    || batchForm.xianyuAutoReplyOn !== ''
    || batchForm.xianyuKeywordReplyOn !== ''
    || batchForm.kamiConfigId !== ''
  if (!hasOperation) return

  const updated = await updateSelectedGoodsConfig({
    xianyuAutoDeliveryOn: batchForm.xianyuAutoDeliveryOn === ''
      ? undefined
      : Number(batchForm.xianyuAutoDeliveryOn),
    xianyuAutoReplyOn: batchForm.xianyuAutoReplyOn === ''
      ? undefined
      : Number(batchForm.xianyuAutoReplyOn),
    xianyuKeywordReplyOn: batchForm.xianyuKeywordReplyOn === ''
      ? undefined
      : Number(batchForm.xianyuKeywordReplyOn),
    kamiConfigId: batchForm.kamiConfigId === '' ? undefined : Number(batchForm.kamiConfigId)
  })
  if (updated) {
    batchDialogVisible.value = false
  }
}
</script>

<template>
  <div class="goods">
    <!-- Header -->
    <div class="goods__header">
      <div class="goods__title-row desktop-only">
        <div class="goods__title-icon">
          <IconShoppingBag />
        </div>
        <h1 class="goods__title">商品配置中心</h1>
      </div>

      <div class="goods__actions">
        <template v-if="!isMobile">
          <div class="goods__select-wrap">
            <select
              v-model="selectedAccountId"
              class="goods__select"
              @change="handleAccountChange"
            >
              <option :value="null" disabled>选择账号</option>
              <option :value="0">所有账号</option>
              <option v-for="acc in accounts" :key="acc.id" :value="acc.id">
                {{ acc.accountNote || acc.unb }}
              </option>
            </select>
            <span class="goods__select-icon">
              <IconChevronDown />
            </span>
          </div>

          <div class="goods__select-wrap">
            <select
              v-model="statusFilter"
              class="goods__select"
              @change="handleStatusFilter"
            >
              <option value="">全部状态</option>
              <option value="0">在售</option>
              <option value="1">已下架</option>
              <option value="2">已售出</option>
            </select>
            <span class="goods__select-icon">
              <IconChevronDown />
            </span>
          </div>
        </template>

        <button
          class="btn btn--primary desktop-only"
          :class="{ 'btn--loading': refreshing || syncing }"
          :disabled="refreshing || syncing || selectedAccountId === null"
          @click="handleRefresh"
        >
          <IconRefresh />
          <span class="mobile-hidden">同步闲鱼商品</span>
        </button>

        <span v-if="total > 0 && !isMobile" class="goods__count">
          共 {{ total }} 件
        </span>

        <div v-if="syncing && syncProgress" class="goods__sync-progress">
          <span class="goods__sync-text">
            详情同步: {{ syncProgress.completedCount }}/{{ syncProgress.totalCount }}
          </span>
          <div class="goods__sync-bar">
            <div 
              class="goods__sync-bar-fill" 
              :style="{ width: `${syncProgress.totalCount > 0 ? (syncProgress.completedCount / syncProgress.totalCount) * 100 : 0}%` }"
            ></div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="selectedGoodsCount > 0" class="goods__batch-toolbar">
      <span>已选择 {{ selectedGoodsCount }} 个商品</span>
      <div class="goods__batch-actions">
        <button class="btn btn--secondary" @click="clearGoodsSelection">取消选择</button>
        <button class="btn btn--primary" @click="openBatchDialog">批量配置</button>
      </div>
    </div>

    <!-- Content Card -->
    <div class="goods__content">
      <!-- Pull Refresh Indicator (Mobile Only) -->
      <div 
        v-if="isMobile && pullDistance > 0"
        class="goods__pull-refresh"
        :style="{ height: `${pullRefreshDistance}px` }"
        :class="{
          'goods__pull-refresh--pulling': pullRefreshState === 'pulling',
          'goods__pull-refresh--ready': pullRefreshState === 'ready',
          'goods__pull-refresh--refreshing': pullRefreshState === 'refreshing'
        }"
      >
        <div class="goods__pull-refresh-content">
          <div class="goods__pull-refresh-icon">
            <IconRefresh />
          </div>
          <div class="goods__pull-refresh-text">
            {{ pullRefreshState === 'pulling' ? '下拉刷新' : pullRefreshState === 'ready' ? '释放刷新' : '刷新中...' }}
          </div>
        </div>
      </div>

      <!-- Table/Cards -->
      <div 
        ref="tableWrapRef"
        class="goods__table-wrap"
        @touchstart="handleTouchStart"
        @touchmove="handleTouchMove"
        @touchend="handleTouchEnd"
      >
        <GoodsTable
          :goods-list="goodsList"
          :loading="loading"
          :selected-goods-ids="selectedGoodsIds"
          :account-names="accountNames"
          :show-account="selectedAccountId === 0"
          @view="viewDetail"
          @sync="syncSingleGoods"
          @toggle-auto-delivery="toggleAutoDelivery"
          @toggle-auto-reply="toggleAutoReply"
          @configure="openGoodsConfig"
          @delete="confirmDelete"
          @toggle-select="toggleGoodsSelection"
          @toggle-select-page="togglePageSelection"
        />
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="goods__pagination">
        <button
          class="goods__page-btn"
          :class="{ 'goods__page-btn--disabled': currentPage <= 1 }"
          @click="handlePageChange(currentPage - 1)"
        >
          <IconChevronLeft />
        </button>

        <template v-for="page in getPageButtons()" :key="page">
          <button
            class="goods__page-btn"
            :class="{ 'goods__page-btn--active': page === currentPage }"
            @click="handlePageChange(page)"
          >
            {{ page }}
          </button>
        </template>

        <button
          class="goods__page-btn"
          :class="{ 'goods__page-btn--disabled': currentPage >= totalPages }"
          @click="handlePageChange(currentPage + 1)"
        >
          <IconChevronRight />
        </button>

        <span class="goods__page-info">{{ currentPage }} / {{ totalPages }}</span>
      </div>
    </div>

    <!-- Detail Dialog -->
    <GoodsDetail
      v-model="dialogs.detail"
      :goods-id="selectedGoodsId"
      :account-id="selectedGoodsAccountId"
      @refresh="loadGoods"
      @configure="openGoodsConfig"
    />

    <GoodsConfigDialog
      v-model="configDialogVisible"
      :item="configTarget"
      :account-id="configTargetAccountId"
      @saved="handleGoodsConfigSaved"
      @open-keyword-rules="openKeywordRules"
    />


    <!-- Delete Confirm -->
    <Transition name="overlay-fade">
      <div v-if="dialogs.deleteConfirm" class="goods__dialog-overlay" @click.self="dialogs.deleteConfirm = false">
        <div class="goods__dialog">
          <div class="goods__dialog-header">
            <h3 class="goods__dialog-title">删除商品</h3>
          </div>
          <div class="goods__dialog-body">
            <p class="goods__dialog-text">
              确定要删除「{{ deleteTarget?.title }}」吗？该商品属于
              {{ deleteTarget ? (accountNames[deleteTarget.accountId] || `账号 ${deleteTarget.accountId}`) : '' }}，此操作不可恢复。
            </p>
          </div>
          <div class="goods__dialog-footer">
            <button
              class="goods__dialog-btn goods__dialog-btn--cancel"
              @click="dialogs.deleteConfirm = false"
            >
              取消
            </button>
            <button
              class="goods__dialog-btn goods__dialog-btn--danger"
              @click="executeDelete"
            >
              删除
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <Transition name="overlay-fade">
      <div v-if="batchDialogVisible" class="goods__dialog-overlay" @click.self="batchDialogVisible = false">
        <div class="goods__dialog goods__batch-dialog">
          <div class="goods__dialog-header goods__batch-dialog-header">
            <h3 class="goods__dialog-title">批量配置商品</h3>
            <p>将对已选择的 {{ selectedGoodsCount }} 个商品生效</p>
          </div>
          <div class="goods__batch-dialog-body">
            <label class="goods__batch-field">
              <span>自动发货</span>
              <select v-model="batchForm.xianyuAutoDeliveryOn">
                <option value="">保持不变</option>
                <option value="1">开启</option>
                <option value="0">关闭</option>
              </select>
            </label>
            <label class="goods__batch-field">
              <span>商品专属 AI</span>
              <select v-model="batchForm.xianyuAutoReplyOn">
                <option value="">保持不变</option>
                <option value="1">开启</option>
                <option value="0">关闭</option>
              </select>
            </label>
            <label class="goods__batch-field">
              <span>关键词回复</span>
              <select v-model="batchForm.xianyuKeywordReplyOn">
                <option value="">保持不变</option>
                <option value="1">开启</option>
                <option value="0">关闭</option>
              </select>
            </label>
            <label class="goods__batch-field">
              <span>默认发货来源</span>
              <select
                v-model="batchForm.kamiConfigId"
                :disabled="loadingKamiConfigs"
                @change="handleBatchKamiChange"
              >
                <option value="">保持现有来源</option>
                <option v-for="config in availableKamiConfigs" :key="config.id" :value="config.id">
                  {{ config.aliasName }}（{{ sourceTypeText(config) }}）
                </option>
              </select>
            </label>
            <p v-if="loadingKamiConfigs" class="goods__batch-hint">正在读取卡券管理中的发货来源…</p>
            <p v-else-if="availableKamiConfigs.length === 0" class="goods__batch-hint">暂无可用卡券。可先到“卡券管理”新建本地库存、API 或固定内容来源。</p>
            <p v-if="batchForm.kamiConfigId !== ''" class="goods__batch-warning">
              关联后会启用自动发货，并仅替换默认发货来源；已有的多规格发货规则保持不变。
            </p>
          </div>
          <div class="goods__dialog-footer">
            <button class="goods__dialog-btn goods__dialog-btn--cancel" :disabled="batchUpdating" @click="batchDialogVisible = false">取消</button>
            <button
              class="goods__dialog-btn goods__dialog-btn--confirm"
              :disabled="batchUpdating || (batchForm.xianyuAutoDeliveryOn === '' && batchForm.xianyuAutoReplyOn === '' && batchForm.xianyuKeywordReplyOn === '' && batchForm.kamiConfigId === '')"
              @click="submitBatchUpdate"
            >
              {{ batchUpdating ? '保存中…' : '确认配置' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.overlay-fade-enter-active,
.overlay-fade-leave-active {
  transition: opacity 0.2s ease;
}

.overlay-fade-enter-from,
.overlay-fade-leave-to {
  opacity: 0;
}
</style>

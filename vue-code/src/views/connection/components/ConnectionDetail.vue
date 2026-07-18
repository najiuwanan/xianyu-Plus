<script setup lang="ts">
import { ref, watch, computed, onBeforeUnmount } from 'vue'
import { showConfirm } from '@/utils/confirm'
import { toast } from '@/utils/toast'
import { getConnectionStatus, startConnection, stopConnection } from '@/api/websocket'
import { updateAccount } from '@/api/account'
import { showSuccess, showError, showInfo } from '@/utils'
import CredentialModal from './CredentialModal.vue'
import ManualUpdateCookieModal from './ManualUpdateCookieModal.vue'
import QRUpdateDialog from './QRUpdateDialog.vue'
import CaptchaGuideDialog from './CaptchaGuideDialog.vue'

import IconKey from '@/components/icons/IconKey.vue'
import IconPlay from '@/components/icons/IconPlay.vue'
import IconStop from '@/components/icons/IconStop.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconCheck from '@/components/icons/IconCheck.vue'
import IconAlert from '@/components/icons/IconAlert.vue'
import IconLink from '@/components/icons/IconLink.vue'

interface ConnectionStatus {
  xianyuAccountId: number
  connected: boolean
  status: string
  cookieStatus?: number
  cookieText?: string
  mH5Tk?: string
  mh5Tk?: string
  websocketToken?: string
  tokenExpireTime?: number
  autoDeliveryOn?: boolean
  autoReplyOn?: boolean
}

interface Props {
  accountId: number | null
  accountName?: string
  autoConnectOnStartup?: number
}

const props = defineProps<Props>()

const connectionStatus = ref<ConnectionStatus | null>(null)
const statusLoading = ref(false)
let statusInterval: number | null = null

const showManualUpdateCookieDialog = ref(false)
const showQRUpdateDialog = ref(false)
const showCaptchaGuideDialog = ref(false)
const captchaUrl = ref('')
const showCredentialDialog = ref(false)
const autoConnectOnStartup = ref(props.autoConnectOnStartup !== 0)

watch(() => props.autoConnectOnStartup, (value) => {
  autoConnectOnStartup.value = value !== 0
})

const handleAutoConnectChange = async () => {
  if (!props.accountId) return
  const previousValue = !autoConnectOnStartup.value
  try {
    const response = await updateAccount({
      id: props.accountId,
      autoConnectOnStartup: autoConnectOnStartup.value ? 1 : 0
    })
    if (response.code !== 0 && response.code !== 200) {
      throw new Error(response.msg || '保存失败')
    }
    showSuccess(autoConnectOnStartup.value
      ? '已开启：服务器重启后会自动恢复该账号连接'
      : '已关闭：服务器重启后不会自动连接该账号')
  } catch (error: any) {
    autoConnectOnStartup.value = previousValue
    showError(`保存开机连接设置失败：${error.message || '未知错误'}`)
  }
}

const loadConnectionStatus = async (silent = false) => {
  if (!props.accountId) return
  if (!silent) statusLoading.value = true
  try {
    const response = await getConnectionStatus(props.accountId)
    if (response.code === 0 || response.code === 200) {
      connectionStatus.value = response.data as ConnectionStatus
    } else {
      throw new Error(response.msg || '获取连接状态失败')
    }
  } catch (error: any) {
    console.error('加载状态失败:', error.message)
  } finally {
    statusLoading.value = false
  }
}

const handleStartConnection = async () => {
  if (!props.accountId) return
  statusLoading.value = true
  try {
    const response = await startConnection(props.accountId)
    if (response.code === 0 || response.code === 200) {
      await loadConnectionStatus()
      toast.info('1、请勿使用闲鱼网页版进行消息回复，避免触发风控；2、首次运行可能出现短暂掉线或自动刷新失败，请保持服务持续运行后重试。')
    } else if (response.code === 1001 && response.data?.needCaptcha) {
      captchaUrl.value = response.data.captchaUrl || ''
      showCaptchaGuideDialog.value = true
    } else {
      throw new Error(response.msg || '启动连接失败')
    }
  } catch (error: any) {
    if (error !== 'cancel' && error !== 'close') {
      showError('启动连接失败: ' + error.message)
    }
  } finally {
    statusLoading.value = false
  }
}

const handleStopConnection = async () => {
  if (!props.accountId) return
  try {
    await showConfirm(
      '断开连接后将无法接收消息和执行自动化流程，确定要断开连接吗？',
      '确认断开连接'
    )
  } catch { return }

  statusLoading.value = true
  try {
    const response = await stopConnection(props.accountId)
    if (response.code === 0 || response.code === 200) {
      showSuccess('连接已断开')
      await loadConnectionStatus()
    } else {
      throw new Error(response.msg || '断开连接失败')
    }
  } catch (error: any) {
    showError('断开连接失败: ' + error.message)
  } finally {
    statusLoading.value = false
  }
}

const handleRefresh = async () => {
  await loadConnectionStatus()
  showInfo('状态已刷新')
}

const handleManualUpdateCookieSuccess = async () => {
  await loadConnectionStatus()
}

const handleQRUpdateSuccess = async () => {
  await loadConnectionStatus()
}

const handleCaptchaSuccess = async () => {
  await loadConnectionStatus()
}

const canSyncGoods = computed(() => connectionStatus.value?.cookieStatus === 1)
const canAutoReply = computed(() => connectionStatus.value?.connected === true)

watch(() => props.accountId, (newId) => {
  if (newId) {
    loadConnectionStatus()
    if (statusInterval) clearInterval(statusInterval)
    statusInterval = window.setInterval(() => {
      if (props.accountId) {
        loadConnectionStatus(true)
      }
    }, 10000)
  } else {
    connectionStatus.value = null
    if (statusInterval) {
      clearInterval(statusInterval)
      statusInterval = null
    }
  }
}, { immediate: true })

onBeforeUnmount(() => {
  if (statusInterval) clearInterval(statusInterval)
})
</script>

<template>
  <div class="detail-panel">
    <div v-if="!accountId" class="detail-empty">
      <div class="detail-empty__icon"><IconLink /></div>
      <p class="detail-empty__text">请选择一个账号查看连接状态</p>
    </div>

    <div v-else class="detail-scroll" :class="{ 'detail-scroll--loading': statusLoading }">
      <div v-if="connectionStatus" class="detail-body">
        <div v-if="accountName" class="detail-account-name">{{ accountName }}</div>
        <div class="status-cards">
          <div class="status-card" :class="canSyncGoods ? 'status-card--success' : 'status-card--danger'">
            <div class="status-card__icon">
              <component :is="canSyncGoods ? IconCheck : IconAlert" />
            </div>
            <div class="status-card__content">
              <span class="status-card__title">Cookie 状态</span>
              <span class="status-card__desc">{{ canSyncGoods ? '有效' : '无效' }}</span>
            </div>
            <button class="btn btn--ghost btn--small" @click="showCredentialDialog = true">
              <IconKey /><span>凭证更新</span>
            </button>
          </div>

          <div class="status-card" :class="canAutoReply ? 'status-card--success' : 'status-card--danger'">
            <div class="status-card__icon">
              <component :is="canAutoReply ? IconCheck : IconAlert" />
            </div>
            <div class="status-card__content">
              <span class="status-card__title">Websocket 状态</span>
              <span class="status-card__desc">{{ canAutoReply ? '已连接' : '未连接' }}</span>
            </div>
            <button
              v-if="connectionStatus.connected === true"
              class="btn btn--stop btn--small"
              @click="handleStopConnection"
            >
              <IconStop /><span>断开</span>
            </button>
            <button
              v-else
              class="btn btn--start btn--small"
              @click="handleStartConnection"
            >
              <IconPlay /><span>连接</span>
            </button>
          </div>

          <div class="status-card" :class="autoConnectOnStartup ? 'status-card--success' : 'status-card--neutral'">
            <div class="status-card__icon">
              <component :is="autoConnectOnStartup ? IconCheck : IconAlert" />
            </div>
            <div class="status-card__content">
              <span class="status-card__title">开机自动连接</span>
              <span class="status-card__desc">{{ autoConnectOnStartup ? '启动后自动恢复并重试' : '本账号不自动恢复' }}</span>
            </div>
            <label class="startup-toggle" title="服务器或容器启动后自动恢复实时连接">
              <input v-model="autoConnectOnStartup" type="checkbox" :disabled="statusLoading" @change="handleAutoConnectChange">
              <span></span>
            </label>
          </div>

          <div class="status-card" :class="connectionStatus.autoDeliveryOn ? 'status-card--success' : 'status-card--danger'">
            <div class="status-card__icon">
              <component :is="connectionStatus.autoDeliveryOn ? IconCheck : IconAlert" />
            </div>
            <div class="status-card__content">
              <span class="status-card__title">自动发货</span>
              <span class="status-card__desc">{{ connectionStatus.autoDeliveryOn ? (connectionStatus.connected ? 'WS 发货' : '凭证发货') : '未开启' }}</span>
            </div>
          </div>

          <div class="status-card" :class="connectionStatus.autoReplyOn ? 'status-card--success' : 'status-card--danger'">
            <div class="status-card__icon">
              <component :is="connectionStatus.autoReplyOn ? IconCheck : IconAlert" />
            </div>
            <div class="status-card__content">
              <span class="status-card__title">自动回复</span>
              <span class="status-card__desc">{{ connectionStatus.autoReplyOn ? '已开启' : '未开启' }}</span>
            </div>
          </div>
        </div>

        <div class="action-bar">
          <button class="btn btn--ghost btn--small" @click="handleRefresh" :disabled="statusLoading">
            <IconRefresh /><span>刷新状态</span>
          </button>
        </div>

      </div>
    </div>

    <CredentialModal
      v-model="showCredentialDialog"
      :connection-status="connectionStatus"
      @qr-update="showQRUpdateDialog = true"
      @manual-update="showManualUpdateCookieDialog = true"
    />

    <ManualUpdateCookieModal
      v-if="connectionStatus"
      v-model="showManualUpdateCookieDialog"
      :account-id="accountId || 0"
      :current-cookie="connectionStatus.cookieText || ''"
      @success="handleManualUpdateCookieSuccess"
    />
    <QRUpdateDialog
      v-model="showQRUpdateDialog"
      :account-id="accountId || 0"
      @success="handleQRUpdateSuccess"
    />
    <CaptchaGuideDialog
      v-model="showCaptchaGuideDialog"
      :account-id="props.accountId || 0"
      :captcha-url="captchaUrl"
      @success="handleCaptchaSuccess"
    />
  </div>
</template>

<style scoped>
.detail-panel {
  --c-bg: transparent;
  --c-surface: #ffffff;
  --c-border: rgba(0, 0, 0, 0.06);
  --c-text-1: #1c1c1e;
  --c-text-2: rgba(28,28,30,.55);
  --c-text-3: rgba(28,28,30,.55);
  --c-accent: #0A84FF;
  --c-danger: #FF453A;
  --c-success: #30D158;
  --c-warning: #FF9F0A;
  --c-r-sm: 8px;
  --c-r-md: 12px;
  --c-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.status-card--neutral {
  border-color: rgba(142, 142, 147, .25);
  background: rgba(142, 142, 147, .08);
}

.startup-toggle {
  width: 42px;
  height: 24px;
  position: relative;
  flex: 0 0 auto;
  cursor: pointer;
}

.startup-toggle input {
  position: absolute;
  opacity: 0;
}

.startup-toggle span {
  position: absolute;
  inset: 0;
  border-radius: 999px;
  background: #d1d1d6;
  transition: .2s ease;
}

.startup-toggle span::after {
  content: '';
  position: absolute;
  width: 18px;
  height: 18px;
  left: 3px;
  top: 3px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,.28);
  transition: .2s ease;
}

.startup-toggle input:checked + span { background: #30D158; }
.startup-toggle input:checked + span::after { transform: translateX(18px); }
.startup-toggle input:disabled + span { opacity: .55; cursor: not-allowed; }

.detail-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: var(--c-text-3);
}

.detail-empty__icon {
  width: 48px;
  height: 48px;
  opacity: 0.3;
}

.detail-empty__icon svg {
  width: 36px;
  height: 36px;
}

.detail-empty__text {
  font-size: 14px;
  margin: 0;
}

.detail-scroll {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  scrollbar-width: none;
}

.detail-scroll::-webkit-scrollbar {
  display: none;
}

.detail-scroll--loading {
  opacity: 0.5;
  pointer-events: none;
}

.detail-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 20px 16px;
}

.status-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  width: 100%;
}

.status-card {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid;
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(28px) saturate(1.8);
  -webkit-backdrop-filter: blur(28px) saturate(1.8);
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.status-card--success {
  border-color: rgba(52, 199, 89, 0.3);
  background: rgba(52, 199, 89, 0.08);
}

.status-card--success:hover {
  border-color: rgba(52, 199, 89, 0.4);
  background: rgba(52, 199, 89, 0.12);
}

.status-card--danger {
  border-color: rgba(255, 59, 48, 0.3);
  background: rgba(255, 59, 48, 0.08);
}

.status-card--danger:hover {
  border-color: rgba(255, 59, 48, 0.4);
  background: rgba(255, 59, 48, 0.12);
}

.status-card__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.status-card--success .status-card__icon {
  background: rgba(52, 199, 89, 0.2);
  color: var(--c-success);
}

.status-card--danger .status-card__icon {
  background: rgba(255, 59, 48, 0.2);
  color: var(--c-danger);
}

.status-card__icon svg { width: 20px; height: 20px; }

.status-card__content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  min-width: 0;
}

.status-card__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text-1);
  letter-spacing: -0.01em;
}

.status-card__desc {
  font-size: 11px;
  color: var(--c-text-3);
  line-height: 1.4;
}

.detail-account-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--c-text-1);
  margin-bottom: -8px;
}

.status-card--success .status-card__title { color: var(--c-success); }
.status-card--danger .status-card__title { color: var(--c-danger); }

.status-card .btn {
  flex-shrink: 0;
}

.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 16px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 12px;
  border: none;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
  -webkit-tap-highlight-color: transparent;
  letter-spacing: -0.01em;
}

.btn svg { width: 16px; height: 16px; }

.btn--small {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
}

.btn--small svg { width: 14px; height: 14px; }

.btn--start {
  background: var(--c-success);
  color: white;
  box-shadow: 0 4px 12px rgba(52, 199, 89, 0.3);
}

.btn--start:hover {
  box-shadow: 0 6px 16px rgba(52, 199, 89, 0.4);
  transform: translateY(-1px);
}

.btn--start:active { transform: scale(0.97); }

.btn--stop {
  background: var(--c-danger);
  color: white;
  box-shadow: 0 4px 12px rgba(255, 59, 48, 0.3);
}

.btn--stop:hover {
  box-shadow: 0 6px 16px rgba(255, 59, 48, 0.4);
  transform: translateY(-1px);
}

.btn--stop:active { transform: scale(0.97); }

.btn--ghost {
  background: rgba(0, 122, 255, 0.08);
  color: var(--c-accent);
  border-color: rgba(0, 122, 255, 0.2);
}

.btn--ghost:hover {
  background: rgba(0, 122, 255, 0.15);
}

.btn--ghost:active { transform: scale(0.97); }

.btn--ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

</style>

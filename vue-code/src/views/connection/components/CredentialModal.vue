<script setup lang="ts">
import IconCookie from '@/components/icons/IconCookie.vue'
import IconKey from '@/components/icons/IconKey.vue'
import IconQrCode from '@/components/icons/IconQrCode.vue'
import IconClose from '@/components/icons/IconClose.vue'

interface ConnectionStatus {
  xianyuAccountId?: number
  connected?: boolean
  status?: string
  cookieStatus?: number
  cookieConfigured?: boolean
  mh5TkConfigured?: boolean
  websocketTokenConfigured?: boolean
  cookieText?: string
  mh5Tk?: string
  websocketToken?: string
  tokenExpireTime?: number
  tokenExpiryKnown?: boolean
  tokenLastRefreshTime?: number
  tokenRenewalState?: string
  tokenRenewalMessage?: string
  tokenRenewalUpdatedAt?: number
  tokenRenewalNextRetryAt?: number
  captchaRequired?: boolean
  captchaUrl?: string
}

interface Props {
  modelValue: boolean
  connectionStatus: ConnectionStatus | null
  verificationChecking?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'qr-update'): void
  (e: 'manual-update'): void
  (e: 'refresh-reconnect'): void
  (e: 'verify-security'): void
  (e: 'verification-complete'): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const getCookieStatusColor = (status?: number) => {
  if (status === 1) return '#30D158'
  if (status === 2) return '#FF9F0A'
  if (status === 3) return '#FF453A'
  return 'rgba(28,28,30,.55)'
}

const getCookieStatusText = (status?: number) => {
  if (status === 1) return '有效'
  if (status === 2) return '过期'
  if (status === 3) return '失效'
  return '未知'
}

const renewalInProgressStates = new Set([
  'REFRESH_PENDING', 'RETRY_WAIT', 'REFRESHING_COOKIE', 'REFRESHING_TOKEN', 'RECONNECTING'
])

const getTokenStatusText = (configured?: boolean, timestamp?: number, renewalState?: string) => {
  if (!configured) return '未设置'
  if (renewalState === 'VERIFICATION_REQUIRED') return '需要验证'
  if (renewalInProgressStates.has(renewalState || '')) return '续期中'
  if (!timestamp || timestamp < 1577836800000) return '待刷新'
  return Date.now() > timestamp ? '已过期' : '有效'
}

const getTokenStatusColor = (configured?: boolean, timestamp?: number, renewalState?: string) => {
  if (!configured) return 'rgba(28,28,30,.55)'
  if (renewalState === 'VERIFICATION_REQUIRED' || renewalState === 'REFRESH_FAILED' || renewalState === 'RECONNECT_FAILED') return '#FF453A'
  if (renewalInProgressStates.has(renewalState || '')) return '#FF9F0A'
  if (!timestamp || timestamp < 1577836800000) return '#FF9F0A'
  return Date.now() > timestamp ? '#FF453A' : '#30D158'
}

const getRenewalLabel = (state?: string) => {
  const labels: Record<string, string> = {
    IDLE: '等待自动续期',
    REFRESH_PENDING: '准备续期',
    RETRY_WAIT: '等待重试',
    REFRESHING_COOKIE: '正在刷新 Cookie',
    REFRESHING_TOKEN: '正在刷新 Token',
    RECONNECTING: '正在重新连接',
    SUCCESS: '最近续期成功',
    VERIFICATION_REQUIRED: '需要安全验证',
    REFRESH_FAILED: '续期失败',
    RECONNECT_FAILED: '重连失败'
  }
  return labels[state || 'IDLE'] || '等待自动续期'
}

const getRemainingText = (timestamp?: number) => {
  if (!timestamp || timestamp < 1577836800000) return '等待刷新后重新计算'
  const remaining = timestamp - Date.now()
  if (remaining <= 0) return '已到期，等待自动续期'
  const hours = Math.floor(remaining / 3600000)
  const minutes = Math.max(0, Math.floor((remaining % 3600000) / 60000))
  return `${hours} 小时 ${minutes} 分钟后到期`
}
const getConfiguredStatusText = (configured?: boolean) => {
  return configured ? '已配置' : '未设置'
}

const getConfiguredStatusColor = (configured?: boolean) => {
  return configured ? '#30D158' : 'rgba(28,28,30,.55)'
}

const formatTimestamp = (timestamp?: number) => {
  if (!timestamp) return '未设置'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  }).replace(/\//g, '-')
}

const copyCredential = async (value?: string) => {
  if (!value) return
  await navigator.clipboard.writeText(value)
}
const handleClose = () => {
  emit('update:modelValue', false)
}

const handleQRUpdate = () => {
  emit('qr-update')
}

const handleManualUpdate = () => {
  emit('manual-update')
}

const handleRefreshReconnect = () => {
  emit('refresh-reconnect')
}

const handleVerifySecurity = () => {
  emit('verify-security')
}

const handleVerificationComplete = () => {
  emit('verification-complete')
}
</script>

<template>
  <Transition name="modal-fade">
    <div v-if="modelValue" class="modal-overlay" @click="handleClose">
      <div class="modal-container" @click.stop>
        <!-- Header -->
        <div class="modal-header">
          <h2 class="modal-title">凭证更新</h2>
          <button class="modal-close" @click="handleClose">
            <IconClose />
          </button>
        </div>

        <!-- Content -->
        <div class="modal-content">
          <!-- Action Buttons -->
          <div class="action-buttons">
            <button class="btn btn--primary" @click="handleQRUpdate">
              <IconQrCode />
              <span>扫码更新</span>
            </button>
            <button class="btn btn--secondary" @click="handleManualUpdate">
              <IconCookie />
              <span>手动更新Cookie</span>
            </button>
            <button class="btn btn--secondary" @click="handleRefreshReconnect">
              <IconKey />
              <span>刷新并重连</span>
            </button>
          </div>

          <!-- Credential Items -->
          <div class="credential-list">
            <!-- Cookie -->
            <div class="credential-item">
              <div class="credential-item__header">
                <div class="credential-item__left">
                  <div class="credential-item__icon credential-item__icon--cookie">
                    <IconCookie />
                  </div>
                  <span class="credential-item__name">Cookie 凭证</span>
                </div>
                <span class="credential-item__status" :style="{ color: connectionStatus?.cookieConfigured ? getCookieStatusColor(connectionStatus?.cookieStatus) : 'rgba(28,28,30,.55)' }">
                  {{ connectionStatus?.cookieConfigured ? getCookieStatusText(connectionStatus?.cookieStatus) : '未设置' }}
                </span>
              </div>
              <div class="credential-item__value" :class="{ 'credential-item__value--empty': !connectionStatus?.cookieConfigured }">{{ connectionStatus?.cookieText || '未设置' }}</div><button v-if="connectionStatus?.cookieText" class="copy-button" @click="copyCredential(connectionStatus.cookieText)">复制</button>
            </div>

            <!-- WebSocket Token -->
            <div class="credential-item">
              <div class="credential-item__header">
                <div class="credential-item__left">
                  <div class="credential-item__icon credential-item__icon--token">
                    <IconKey />
                  </div>
                  <span class="credential-item__name">WebSocket Token</span>
                </div>
                <span class="credential-item__status" :style="{ color: getTokenStatusColor(connectionStatus?.websocketTokenConfigured, connectionStatus?.tokenExpireTime, connectionStatus?.tokenRenewalState) }">
                  {{ getTokenStatusText(connectionStatus?.websocketTokenConfigured, connectionStatus?.tokenExpireTime, connectionStatus?.tokenRenewalState) }}
                </span>
              </div>
              <div class="credential-item__value" :class="{ 'credential-item__value--empty': !connectionStatus?.websocketTokenConfigured }">{{ connectionStatus?.websocketToken || '未设置' }}</div><button v-if="connectionStatus?.websocketToken" class="copy-button" @click="copyCredential(connectionStatus.websocketToken)">复制</button>
              <div v-if="connectionStatus?.websocketTokenConfigured" class="credential-item__expire">
                <div>过期时间：{{ connectionStatus?.tokenExpiryKnown ? formatTimestamp(connectionStatus.tokenExpireTime) : '等待刷新' }}</div>
                <div>剩余时间：{{ getRemainingText(connectionStatus?.tokenExpireTime) }}</div>
                <div>上次刷新：{{ connectionStatus?.tokenLastRefreshTime ? formatTimestamp(connectionStatus.tokenLastRefreshTime) : '暂无成功记录' }}</div>
              </div>
              <div class="renewal-status" :class="`renewal-status--${(connectionStatus?.tokenRenewalState || 'IDLE').toLowerCase()}`">
                <strong>{{ getRenewalLabel(connectionStatus?.tokenRenewalState) }}</strong>
                <span>{{ connectionStatus?.tokenRenewalMessage || '系统将在需要时自动续期' }}</span>
                <small v-if="connectionStatus?.tokenRenewalNextRetryAt">下次尝试：{{ formatTimestamp(connectionStatus.tokenRenewalNextRetryAt) }}</small>
              </div>
              <div v-if="connectionStatus?.captchaRequired || connectionStatus?.tokenRenewalState === 'VERIFICATION_REQUIRED'" class="verification-actions">
                <strong>需要你完成一次平台验证</strong>
                <p v-if="connectionStatus?.captchaUrl">点击“立即验证”后会打开闲鱼官方页面。完成滑块并关闭验证窗口，系统会自动检测、刷新 Token 并恢复连接。</p>
                <p v-else>当前验证地址已失效或尚未获取，请先点击上方“刷新并重连”获取最新验证地址。</p>
                <div class="verification-actions__buttons">
                  <button class="btn btn--primary" :disabled="!connectionStatus?.captchaUrl || verificationChecking" @click="handleVerifySecurity">
                    {{ verificationChecking ? '检测中…' : '立即验证' }}
                  </button>
                  <button class="btn btn--secondary" :disabled="verificationChecking" @click="handleVerificationComplete">
                    {{ verificationChecking ? '检测中…' : '我已完成，检测并重连' }}
                  </button>
                </div>
              </div>
            </div>

            <!-- H5 Token -->
            <div class="credential-item">
              <div class="credential-item__header">
                <div class="credential-item__left">
                  <div class="credential-item__icon credential-item__icon--h5">
                    <IconKey />
                  </div>
                  <span class="credential-item__name">H5 Token (_m_h5_tk)</span>
                </div>
                <span class="credential-item__status" :style="{ color: getConfiguredStatusColor(connectionStatus?.mh5TkConfigured) }">
                  {{ getConfiguredStatusText(connectionStatus?.mh5TkConfigured) }}
                </span>
              </div>
              <div class="credential-item__value" :class="{ 'credential-item__value--empty': !connectionStatus?.mh5TkConfigured }">{{ connectionStatus?.mh5Tk || '未设置' }}</div><button v-if="connectionStatus?.mh5Tk" class="copy-button" @click="copyCredential(connectionStatus.mh5Tk)">复制</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.20);
  backdrop-filter: blur(28px) saturate(1.8);
  -webkit-backdrop-filter: blur(28px) saturate(1.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.modal-container {
  background: rgba(255,255,255,0.72);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  max-height: 85vh;
  animation: slideUp 0.3s cubic-bezier(0.25, 0.1, 0.25, 1);
}

@keyframes slideUp {
  from {
    transform: translateY(20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 0.5px solid rgba(60,60,67,.12);
  background: rgba(255,255,255,0.72);
  backdrop-filter: blur(28px) saturate(1.8);
  -webkit-backdrop-filter: blur(28px) saturate(1.8);
  flex-shrink: 0;
}

.modal-title {
  font-size: 18px;
  font-weight: 600;
  color: #1c1c1e;
  margin: 0;
  letter-spacing: -0.01em;
}

.modal-close {
  background: none;
  border: none;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(28,28,30,.55);
  cursor: pointer;
  border-radius: 8px;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
}

.modal-close:hover {
  background: rgba(60,60,67,.12);
  color: #1c1c1e;
}

.modal-close svg {
  width: 20px;
  height: 20px;
}

.modal-content {
  flex: 1;
  overflow-y: auto;
  scrollbar-width: none;
  padding: 24px;
}

.modal-content::-webkit-scrollbar {
  display: none;
}

.action-buttons {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  font-size: 15px;
  font-weight: 600;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
  -webkit-tap-highlight-color: transparent;
  letter-spacing: -0.01em;
  flex: 1;
}

.btn svg {
  width: 18px;
  height: 18px;
}

.btn--primary {
  background: #0A84FF;
  color: white;
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.3);
}

.btn--primary:hover {
  background: #0066d6;
  box-shadow: 0 6px 16px rgba(0, 122, 255, 0.4);
}

.btn--primary:active {
  transform: scale(0.96);
}

.btn--secondary {
  background: rgba(60,60,67,.12);
  color: #1c1c1e;
  box-shadow: none;
}

.btn--secondary:hover {
  background: rgba(0, 0, 0, 0.1);
}

.btn--secondary:active {
  transform: scale(0.96);
}

.credential-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.credential-item {
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(28px) saturate(1.8);
  -webkit-backdrop-filter: blur(28px) saturate(1.8);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 20px;
  padding: 16px;
  transition: all 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
}

.credential-item:hover {
  background: rgba(255, 255, 255, 0.6);
  border-color: rgba(255, 255, 255, 0.6);
}

.credential-item__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 0.5px solid rgba(60,60,67,.12);
}

.credential-item__left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.credential-item__icon {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.credential-item__icon svg {
  width: 16px;
  height: 16px;
}

.credential-item__icon--cookie {
  background: rgba(255, 149, 0, 0.15);
  color: #FF9F0A;
}

.credential-item__icon--token {
  background: rgba(52, 199, 89, 0.15);
  color: #30D158;
}

.credential-item__icon--h5 {
  background: rgba(0, 122, 255, 0.15);
  color: #0A84FF;
}

.credential-item__name {
  font-size: 15px;
  font-weight: 600;
  color: #1c1c1e;
  letter-spacing: -0.01em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.credential-item__status {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 8px;
  background: rgba(60,60,67,.12);
  flex-shrink: 0;
}

.credential-item__value {
  font-family: 'SF Mono', 'Menlo', 'Monaco', monospace;
  font-size: 12px;
  color: rgba(28,28,30,.55);
  word-break: break-all;
  line-height: 1.6;
  padding: 10px;
  background: rgba(255,255,255,0.38);
  border-radius: 10px;
  border: 1px solid rgba(60,60,67,.12);
}

.credential-item__value--empty {
  color: rgba(28,28,30,.55);
  font-style: italic;
  background: rgba(255,255,255,0.15);
}

.copy-button { margin-top: 8px; padding: 6px 10px; border: 0; border-radius: 8px; color: #0A84FF; background: rgba(10,132,255,.1); cursor: pointer; }

.credential-item__meta {
  display: inline-block;
  margin-left: 8px;
  color: rgba(28,28,30,.55);
  font-size: 11px;
  font-weight: 500;
}

.credential-item__expire {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 0.5px solid rgba(60,60,67,.12);
  font-size: 12px;
  line-height: 1.75;
  color: rgba(28,28,30,.55);
}

.renewal-status {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  color: #637085;
  background: rgba(120,120,128,.08);
}

.renewal-status strong { font-size: 12px; }
.renewal-status span, .renewal-status small { font-size: 11px; line-height: 1.5; }
.renewal-status--success { color: #168b49; background: rgba(52,199,89,.10); }
.renewal-status--verification_required,
.renewal-status--refresh_failed,
.renewal-status--reconnect_failed { color: #c7352d; background: rgba(255,59,48,.09); }
.renewal-status--refresh_pending,
.renewal-status--retry_wait,
.renewal-status--refreshing_cookie,
.renewal-status--refreshing_token,
.renewal-status--reconnecting { color: #a86200; background: rgba(255,159,10,.12); }

.verification-actions {
  margin-top: 10px;
  padding: 12px;
  border: 1px solid rgba(255,159,10,.28);
  border-radius: 12px;
  color: #7a4b00;
  background: rgba(255,159,10,.08);
}

.verification-actions strong { display: block; font-size: 13px; }
.verification-actions p { margin: 6px 0 10px; font-size: 12px; line-height: 1.6; }
.verification-actions__buttons { display: flex; gap: 8px; }
.verification-actions__buttons .btn { padding: 9px 12px; font-size: 12px; }
.verification-actions__buttons .btn:disabled { cursor: not-allowed; opacity: .5; }

/* 手机端适配 */
@media screen and (max-width: 767px) {
  .modal-container {
    width: 90%;
    max-height: 90vh;
    border-radius: 20px;
  }

  .modal-header {
    padding: 16px;
  }

  .modal-title {
    font-size: 16px;
  }

  .modal-content {
    padding: 16px;
  }

  .action-buttons {
    flex-direction: column;
    gap: 10px;
    margin-bottom: 16px;
  }

  .btn {
    padding: 12px 14px;
    font-size: 14px;
  }

  .verification-actions__buttons {
    flex-direction: column;
  }

  .credential-item {
    padding: 12px;
  }

  .credential-item__header {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
    margin-bottom: 10px;
    padding-bottom: 10px;
  }

  .credential-item__left {
    width: 100%;
  }

  .credential-item__status {
    align-self: flex-start;
    font-size: 11px;
    padding: 3px 8px;
  }

  .credential-item__icon {
    width: 28px;
    height: 28px;
  }

  .credential-item__icon svg {
    width: 14px;
    height: 14px;
  }

  .credential-item__name {
    font-size: 13px;
  }

  .credential-item__value {
    font-size: 11px;
    padding: 8px;
    line-height: 1.5;
  }

  .copy-button { margin-top: 8px; padding: 6px 10px; border: 0; border-radius: 8px; color: #0A84FF; background: rgba(10,132,255,.1); cursor: pointer; }

.credential-item__meta {
    font-size: 10px;
  }

  .credential-item__expire {
    margin-top: 8px;
    padding-top: 8px;
    font-size: 11px;
  }
}

/* 平板端适配 */
@media screen and (min-width: 768px) and (max-width: 1023px) {
  .modal-container {
    width: 70%;
    max-height: 85vh;
  }

  .modal-content {
    padding: 20px;
  }

  .credential-item {
    padding: 14px;
  }

  .credential-item__name {
    font-size: 14px;
  }

  .credential-item__value {
    font-size: 11px;
  }
}

/* 电脑端适配 */
@media screen and (min-width: 1024px) {
  .modal-container {
    width: 60%;
    max-height: 85vh;
  }

  .modal-content {
    padding: 32px;
  }
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
</style>

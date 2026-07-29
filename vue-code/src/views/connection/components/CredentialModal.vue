<script setup lang="ts">
import { computed, ref } from 'vue'
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
  tokenExpireTime?: number | string
  tokenExpiryKnown?: boolean
  tokenLastRefreshTime?: number | string
  tokenRenewalState?: string
  tokenRenewalMessage?: string
  tokenRenewalUpdatedAt?: number | string
  tokenRenewalNextRetryAt?: number | string
  captchaRequired?: boolean
  captchaUrl?: string
}

interface Props {
  modelValue: boolean
  connectionStatus: ConnectionStatus | null
  accountName?: string
  accountUnb?: string
  repairing?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'repair-connection'): void
  (e: 'qr-update'): void
  (e: 'manual-update'): void
}

type CredentialKey = 'cookie' | 'websocket' | 'h5'

const expandedCredential = ref<CredentialKey | null>(null)
const showAdvancedActions = ref(false)

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const verificationRequired = computed(() =>
  props.connectionStatus?.captchaRequired === true
  || props.connectionStatus?.tokenRenewalState === 'VERIFICATION_REQUIRED'
)

const repairActionLabel = computed(() => {
  if (props.repairing) return '正在修复连接…'
  return verificationRequired.value ? '继续验证并修复' : '修复连接'
})

const repairHint = computed(() => {
  if (verificationRequired.value) return '平台要求安全验证。系统会打开闲鱼IM，完成后返回本页即可继续扫码更新。'
  if (props.connectionStatus?.connected) return '当前连接正常；需要时点击一次，系统会自动检查Token并恢复连接。'
  return '系统会先自动刷新Token并重连；自动处理未完成时才引导扫码更新。'
})

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

const normalizeTimestamp = (timestamp?: number | string) => {
  if (timestamp === undefined || timestamp === null || timestamp === '') return undefined
  const value = typeof timestamp === 'string' ? Number(timestamp) : timestamp
  return Number.isFinite(value) ? value : undefined
}

const getTokenStatusText = (configured?: boolean, timestamp?: number | string, renewalState?: string) => {
  if (!configured) return '未设置'
  if (renewalState === 'VERIFICATION_REQUIRED') return '需要验证'
  if (renewalInProgressStates.has(renewalState || '')) return '续期中'
  const value = normalizeTimestamp(timestamp)
  if (!value || value < 1577836800000) return '待刷新'
  return Date.now() > value ? '已过期' : '有效'
}

const getTokenStatusColor = (configured?: boolean, timestamp?: number | string, renewalState?: string) => {
  if (!configured) return 'rgba(28,28,30,.55)'
  if (renewalState === 'VERIFICATION_REQUIRED' || renewalState === 'REFRESH_FAILED' || renewalState === 'RECONNECT_FAILED') return '#FF453A'
  if (renewalInProgressStates.has(renewalState || '')) return '#FF9F0A'
  const value = normalizeTimestamp(timestamp)
  if (!value || value < 1577836800000) return '#FF9F0A'
  return Date.now() > value ? '#FF453A' : '#30D158'
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

const getRemainingText = (timestamp?: number | string) => {
  const value = normalizeTimestamp(timestamp)
  if (!value || value < 1577836800000) return '等待刷新'
  const remaining = value - Date.now()
  if (remaining <= 0) return '已到期，等待自动续期'
  const hours = Math.floor(remaining / 3600000)
  const minutes = Math.max(0, Math.floor((remaining % 3600000) / 60000))
  return `剩余 ${hours}小时${minutes}分钟`
}

const getConfiguredStatusText = (configured?: boolean) => {
  return configured ? '已配置' : '未设置'
}

const getConfiguredStatusColor = (configured?: boolean) => {
  return configured ? '#30D158' : 'rgba(28,28,30,.55)'
}

const formatTimestamp = (timestamp?: number | string) => {
  const value = normalizeTimestamp(timestamp)
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--'
  return date.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  }).replace(/\//g, '-')
}

const toggleCredential = (key: CredentialKey) => {
  expandedCredential.value = expandedCredential.value === key ? null : key
}

const copyCredential = async (value?: string) => {
  if (!value) return
  await navigator.clipboard.writeText(value)
}
const handleClose = () => {
  emit('update:modelValue', false)
}

const handleRepairConnection = () => {
  if (!props.repairing) emit('repair-connection')
}

const handleQRUpdate = () => {
  emit('qr-update')
}

const handleManualUpdate = () => {
  emit('manual-update')
}

const toggleAdvancedActions = () => {
  showAdvancedActions.value = !showAdvancedActions.value
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
          <div class="repair-panel" :class="{ 'repair-panel--verification': verificationRequired }">
            <div class="repair-panel__copy">
              <strong>{{ verificationRequired ? '需要完成平台验证' : '一键修复连接' }}</strong>
              <p>{{ repairHint }}</p>
            </div>
            <button class="btn btn--primary repair-panel__button" :disabled="repairing" @click="handleRepairConnection">
              <IconKey />
              <span>{{ repairActionLabel }}</span>
            </button>
          </div>

          <div class="advanced-actions">
            <button class="advanced-actions__toggle" type="button" @click="toggleAdvancedActions">
              {{ showAdvancedActions ? '收起高级操作' : '高级操作' }}
            </button>
            <div v-if="showAdvancedActions" class="advanced-actions__content">
              <button class="btn btn--secondary" @click="handleQRUpdate">
                <IconQrCode />
                <span>直接扫码更新</span>
              </button>
              <button class="btn btn--secondary" @click="handleManualUpdate">
                <IconCookie />
                <span>手动更新Cookie</span>
              </button>
            </div>
          </div>

          <div class="credential-table">
            <div class="credential-table__header">
              <span>凭证名称</span>
              <span>状态</span>
              <span>凭证内容</span>
              <span>有效期</span>
              <span>操作</span>
            </div>

            <div class="credential-row">
              <div class="credential-name">
                <span class="credential-icon credential-icon--cookie"><IconCookie /></span>
                <strong>Cookie 凭证</strong>
              </div>
              <span class="credential-status" :style="{ color: connectionStatus?.cookieConfigured ? getCookieStatusColor(connectionStatus?.cookieStatus) : 'rgba(28,28,30,.55)' }">
                {{ connectionStatus?.cookieConfigured ? getCookieStatusText(connectionStatus?.cookieStatus) : '未设置' }}
              </span>
              <span class="credential-preview" :class="{ 'credential-preview--empty': !connectionStatus?.cookieText }">{{ connectionStatus?.cookieText || '未设置' }}</span>
              <span class="credential-validity">{{ connectionStatus?.cookieConfigured ? '长期有效' : '--' }}</span>
              <div class="credential-actions">
                <button :disabled="!connectionStatus?.cookieText" @click="copyCredential(connectionStatus?.cookieText)">复制</button>
                <button :disabled="!connectionStatus?.cookieText" @click="toggleCredential('cookie')">{{ expandedCredential === 'cookie' ? '收起' : '展开' }}</button>
              </div>
            </div>
            <div v-if="expandedCredential === 'cookie'" class="credential-detail">
              <span>Cookie 完整内容</span>
              <pre>{{ connectionStatus?.cookieText || '未设置' }}</pre>
            </div>

            <div class="credential-row">
              <div class="credential-name">
                <span class="credential-icon credential-icon--token"><IconKey /></span>
                <strong>WebSocket Token</strong>
              </div>
              <span class="credential-status" :style="{ color: getTokenStatusColor(connectionStatus?.websocketTokenConfigured, connectionStatus?.tokenExpireTime, connectionStatus?.tokenRenewalState) }">
                {{ getTokenStatusText(connectionStatus?.websocketTokenConfigured, connectionStatus?.tokenExpireTime, connectionStatus?.tokenRenewalState) }}
              </span>
              <span class="credential-preview" :class="{ 'credential-preview--empty': !connectionStatus?.websocketToken }">{{ connectionStatus?.websocketToken || '未设置' }}</span>
              <span class="credential-validity">{{ connectionStatus?.tokenExpiryKnown ? getRemainingText(connectionStatus?.tokenExpireTime) : '等待刷新' }}</span>
              <div class="credential-actions">
                <button :disabled="!connectionStatus?.websocketToken" @click="copyCredential(connectionStatus?.websocketToken)">复制</button>
                <button :disabled="!connectionStatus?.websocketToken" @click="toggleCredential('websocket')">{{ expandedCredential === 'websocket' ? '收起' : '展开' }}</button>
              </div>
            </div>
            <div v-if="expandedCredential === 'websocket'" class="credential-detail">
              <span>WebSocket Token 完整内容</span>
              <pre>{{ connectionStatus?.websocketToken || '未设置' }}</pre>
              <div class="credential-detail__meta">
                <span>过期时间：{{ connectionStatus?.tokenExpiryKnown ? formatTimestamp(connectionStatus?.tokenExpireTime) : '--' }}</span>
                <span>上次刷新：{{ formatTimestamp(connectionStatus?.tokenLastRefreshTime) }}</span>
              </div>
            </div>

            <div class="credential-row">
              <div class="credential-name">
                <span class="credential-icon credential-icon--h5"><IconKey /></span>
                <strong>H5 Token (_m_h5_tk)</strong>
              </div>
              <span class="credential-status" :style="{ color: getConfiguredStatusColor(connectionStatus?.mh5TkConfigured) }">
                {{ getConfiguredStatusText(connectionStatus?.mh5TkConfigured) }}
              </span>
              <span class="credential-preview" :class="{ 'credential-preview--empty': !connectionStatus?.mh5Tk }">{{ connectionStatus?.mh5Tk || '未设置' }}</span>
              <span class="credential-validity">{{ connectionStatus?.mh5TkConfigured ? '自动维护' : '--' }}</span>
              <div class="credential-actions">
                <button :disabled="!connectionStatus?.mh5Tk" @click="copyCredential(connectionStatus?.mh5Tk)">复制</button>
                <button :disabled="!connectionStatus?.mh5Tk" @click="toggleCredential('h5')">{{ expandedCredential === 'h5' ? '收起' : '展开' }}</button>
              </div>
            </div>
            <div v-if="expandedCredential === 'h5'" class="credential-detail">
              <span>H5 Token 完整内容</span>
              <pre>{{ connectionStatus?.mh5Tk || '未设置' }}</pre>
            </div>
          </div>

          <div class="renewal-status" :class="`renewal-status--${(connectionStatus?.tokenRenewalState || 'IDLE').toLowerCase()}`">
            <strong>{{ getRenewalLabel(connectionStatus?.tokenRenewalState) }}</strong>
            <span>{{ connectionStatus?.tokenRenewalMessage || '系统将在需要时自动续期' }}</span>
            <small v-if="connectionStatus?.tokenRenewalNextRetryAt">下次尝试：{{ formatTimestamp(connectionStatus.tokenRenewalNextRetryAt) }}</small>
          </div>

          <div v-if="verificationRequired" class="verification-actions">
            <div class="verification-actions__copy">
              <strong>当前账号需要平台验证</strong>
              <p>账号：{{ accountName || '未设置备注' }} · UNB：{{ accountUnb || '--' }}</p>
              <p>点击上方“继续验证并修复”，完成闲鱼IM验证后返回本页，系统会自动弹出扫码更新。</p>
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

.repair-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 10px;
  padding: 16px;
  border: 1px solid rgba(10,132,255,.20);
  border-radius: 14px;
  background: rgba(10,132,255,.07);
}

.repair-panel--verification {
  border-color: rgba(255,159,10,.30);
  background: rgba(255,159,10,.09);
}

.repair-panel__copy { min-width: 0; flex: 1; }
.repair-panel__copy strong { display: block; color: #1c1c1e; font-size: 15px; }
.repair-panel__copy p { margin: 5px 0 0; color: #637085; font-size: 12px; line-height: 1.55; }
.repair-panel__button { min-width: 180px; flex: 0 0 auto !important; }
.repair-panel__button:disabled { cursor: wait; opacity: .62; }

.advanced-actions {
  margin-bottom: 18px;
  text-align: right;
}

.advanced-actions__toggle {
  padding: 5px 8px;
  border: 0;
  color: #637085;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
}

.advanced-actions__toggle:hover { color: #0A84FF; }
.advanced-actions__content { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.advanced-actions__content .btn { max-width: 190px; padding: 9px 12px; font-size: 12px; }

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

.credential-table {
  overflow: hidden;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 14px;
  background: rgba(255,255,255,.46);
}

.credential-table__header,
.credential-row {
  display: grid;
  grid-template-columns: minmax(180px,1.2fr) 90px minmax(240px,2fr) minmax(150px,1fr) 140px;
  align-items: center;
  column-gap: 14px;
}

.credential-table__header {
  min-height: 42px;
  padding: 0 18px;
  color: #1c1c1e;
  background: rgba(248,249,251,.9);
  border-bottom: 1px solid rgba(60,60,67,.12);
  font-size: 12px;
  font-weight: 700;
}

.credential-row {
  min-height: 72px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(60,60,67,.10);
  transition: background .2s ease;
}

.credential-row:hover { background: rgba(255,255,255,.62); }
.credential-row:nth-last-child(1) { border-bottom: 0; }

.credential-name {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.credential-name strong {
  overflow: hidden;
  color: #1c1c1e;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.credential-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 10px;
  flex: 0 0 auto;
}

.credential-icon svg { width: 17px; height: 17px; }
.credential-icon--cookie { color: #FF9F0A; background: rgba(255,149,0,.14); }
.credential-icon--token { color: #30D158; background: rgba(52,199,89,.14); }
.credential-icon--h5 { color: #0A84FF; background: rgba(10,132,255,.12); }

.credential-status {
  justify-self: start;
  padding: 4px 9px;
  border-radius: 8px;
  background: rgba(60,60,67,.09);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.credential-preview {
  overflow: hidden;
  color: #596577;
  font-family: 'SF Mono','Menlo','Monaco',monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.credential-preview--empty { color: rgba(28,28,30,.4); font-style: italic; }
.credential-validity { color: #637085; font-size: 12px; white-space: nowrap; }

.credential-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.credential-actions button {
  min-width: 56px;
  padding: 7px 10px;
  border: 1px solid rgba(10,132,255,.18);
  border-radius: 9px;
  color: #0A84FF;
  background: rgba(255,255,255,.7);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.credential-actions button:hover:not(:disabled) { background: rgba(10,132,255,.08); }
.credential-actions button:disabled { color: rgba(28,28,30,.28); border-color: rgba(60,60,67,.10); cursor: not-allowed; }

.credential-detail {
  padding: 12px 18px 14px;
  border-bottom: 1px solid rgba(60,60,67,.10);
  background: rgba(10,132,255,.035);
}

.credential-detail > span { color: #637085; font-size: 11px; font-weight: 700; }

.credential-detail pre {
  max-height: 150px;
  margin: 8px 0 0;
  padding: 10px 12px;
  overflow: auto;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 9px;
  color: #4c596b;
  background: rgba(255,255,255,.72);
  font-family: 'SF Mono','Menlo','Monaco',monospace;
  font-size: 11px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-all;
}

.credential-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  margin-top: 9px;
  color: #637085;
  font-size: 11px;
}

.renewal-status {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 10px;
  margin-top: 14px;
  padding: 11px 14px;
  border-radius: 11px;
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
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 14px;
  padding: 13px 14px;
  border: 1px solid rgba(255,159,10,.28);
  border-radius: 12px;
  color: #7a4b00;
  background: rgba(255,159,10,.08);
}

.verification-actions--idle {
  border-color: rgba(120,120,128,.16);
  color: #637085;
  background: rgba(120,120,128,.06);
}

.verification-actions__copy { min-width: 0; flex: 1; }
.verification-actions strong { display: block; font-size: 13px; }
.verification-actions p { margin: 4px 0 0; font-size: 11px; line-height: 1.55; }
.verification-actions__buttons { display: flex; flex: 0 0 auto; gap: 8px; }
.verification-actions__buttons .btn { min-width: 112px; padding: 9px 12px; font-size: 12px; flex: 0 0 auto; }
.verification-actions__buttons .btn:disabled { cursor: not-allowed; opacity: .45; }

@media screen and (max-width: 767px) {
  .modal-container { width: calc(100% - 24px); max-height: 92vh; border-radius: 16px; }
  .modal-header { padding: 15px 16px; }
  .modal-title { font-size: 16px; }
  .modal-content { padding: 14px; }
  .repair-panel { align-items: stretch; flex-direction: column; gap: 12px; }
  .repair-panel__button { width: 100%; min-width: 0; }
  .advanced-actions__content { flex-direction: column; }
  .advanced-actions__content .btn { width: 100%; max-width: none; }
  .btn { padding: 10px 12px; font-size: 13px; }
  .credential-table__header { display: none; }
  .credential-row {
    grid-template-columns: minmax(0,1fr) auto;
    grid-template-areas:
      'name status'
      'preview preview'
      'validity actions';
    gap: 9px 12px;
    min-height: 0;
    padding: 14px;
  }
  .credential-name { grid-area: name; }
  .credential-status { grid-area: status; }
  .credential-preview { grid-area: preview; padding: 8px 10px; border-radius: 8px; background: rgba(120,120,128,.06); }
  .credential-validity { grid-area: validity; align-self: center; }
  .credential-actions { grid-area: actions; }
  .credential-detail { padding: 10px 14px 12px; }
  .verification-actions { align-items: stretch; flex-direction: column; gap: 10px; }
  .verification-actions__buttons { width: 100%; }
  .verification-actions__buttons .btn { min-width: 0; flex: 1; }
}

@media screen and (min-width: 768px) and (max-width: 1023px) {
  .modal-container { width: calc(100% - 32px); max-height: 90vh; }
  .modal-content { padding: 18px; }
  .credential-table__header,
  .credential-row { grid-template-columns: 170px 78px minmax(150px,1fr) 125px 120px; column-gap: 10px; }
  .credential-table__header, .credential-row { padding-left: 14px; padding-right: 14px; }
  .credential-name strong { font-size: 13px; }
}

@media screen and (min-width: 1024px) {
  .modal-container { width: min(1080px, calc(100vw - 64px)); max-height: calc(100vh - 64px); }
  .modal-content { padding: 20px; }
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

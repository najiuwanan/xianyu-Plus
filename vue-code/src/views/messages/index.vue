<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { getAccountList } from '@/api/account'
import {
  getChatSessions,
  getContextMessages,
  sendMessage as sendMessageApi,
  updateChatTakeover,
  type ChatMessage,
  type ChatSession
} from '@/api/message'
import { sendImageMessage as sendImageMessageApi } from '@/api/image'
import type { Account } from '@/types'
import { showError, showSuccess } from '@/utils'
import IconImage from '@/components/icons/IconImage.vue'
import IconMessage from '@/components/icons/IconMessage.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconSend from '@/components/icons/IconSend.vue'
import IconUser from '@/components/icons/IconUser.vue'
import MultiImageUploader from '@/components/MultiImageUploader.vue'

const accounts = ref<Account[]>([])
const selectedAccountId = ref<number | null>(null)
const sessions = ref<ChatSession[]>([])
const selectedSession = ref<ChatSession | null>(null)
const messages = ref<ChatMessage[]>([])
const searchText = ref('')
const draft = ref('')
const imageUrls = ref('')
const showImageUploader = ref(false)
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const updatingTakeover = ref(false)
const messageListRef = ref<HTMLElement | null>(null)
const takeoverMinutes = ref(10)
const setHeaderContent = inject<(content: any) => void>('setHeaderContent')

const selectedAccount = computed(() =>
  accounts.value.find(account => Number(account.id) === selectedAccountId.value) || null
)

const filteredSessions = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  if (!keyword) return sessions.value
  return sessions.value.filter(session =>
    [session.buyerUserName, session.buyerUserId, session.lastMessage, session.xyGoodsId]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword)
  )
})

const hasActiveTakeover = computed(() => {
  const value = selectedSession.value?.takeoverEndTime
  return Boolean(value && new Date(value.replace(' ', 'T')).getTime() > Date.now())
})

const buyerUserId = computed(() => {
  if (selectedSession.value?.buyerUserId) return selectedSession.value.buyerUserId
  const accountUnb = selectedAccount.value?.unb
  return messages.value.find(message => message.senderUserId && message.senderUserId !== accountUnb)?.senderUserId || ''
})

const formatTime = (value?: string | number) => {
  if (!value) return ''
  const date = typeof value === 'number' || /^\d+$/.test(String(value))
    ? new Date(Number(value))
    : new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return String(value)
  const isToday = date.toDateString() === new Date().toDateString()
  return isToday
    ? date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

const formatTakeoverEnd = (value?: string) => {
  if (!value) return ''
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

const isMine = (message: ChatMessage) => {
  if ([999, 997, 888, 887].includes(message.contentType)) return true
  return Boolean(selectedAccount.value?.unb && message.senderUserId === selectedAccount.value.unb)
}

const isImageMessage = (message: ChatMessage) => [997, 887].includes(message.contentType)

const getImageUrl = (message: ChatMessage) => {
  const value = (message.msgContent || '').replace(/^\[图片\]/, '')
  return /^https?:\/\//.test(value) ? value : ''
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messageListRef.value) messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  })
}

const ensureSuccess = (response: any) => {
  if (response?.code !== 0 && response?.code !== 200) {
    throw new Error(response?.msg || '请求失败')
  }
}

const loadAccounts = async () => {
  try {
    const response = await getAccountList()
    ensureSuccess(response)
    accounts.value = response.data?.accounts || []
    if (!selectedAccountId.value && accounts.value.length) {
      selectedAccountId.value = Number(accounts.value[0]!.id)
    }
    await loadSessions()
  } catch (error: any) {
    showError(error.message || '加载账号失败')
  }
}

const loadSessions = async (silent = false) => {
  if (!selectedAccountId.value) {
    sessions.value = []
    selectedSession.value = null
    messages.value = []
    return
  }
  if (!silent) loadingSessions.value = true
  try {
    const response = await getChatSessions(selectedAccountId.value)
    ensureSuccess(response)
    const currentSid = selectedSession.value?.sid
    sessions.value = response.data || []
    const current = currentSid ? sessions.value.find(session => session.sid === currentSid) : null
    if (current) {
      selectedSession.value = current
    } else if (sessions.value.length) {
      selectedSession.value = sessions.value[0]!
      await loadMessages()
    } else {
      selectedSession.value = null
      messages.value = []
    }
  } catch (error: any) {
    if (!silent) showError(error.message || '加载会话失败')
  } finally {
    loadingSessions.value = false
  }
}

const loadMessages = async (silent = false) => {
  if (!selectedAccountId.value || !selectedSession.value?.sid) return
  if (!silent) loadingMessages.value = true
  try {
    const response = await getContextMessages({
      xianyuAccountId: selectedAccountId.value,
      sid: selectedSession.value.sid,
      limit: 80
    })
    ensureSuccess(response)
    const nextMessages = Array.isArray(response.data) ? [...response.data].reverse() : []
    const changed = JSON.stringify(nextMessages) !== JSON.stringify(messages.value)
    messages.value = nextMessages
    if (changed) scrollToBottom()
  } catch (error: any) {
    if (!silent) showError(error.message || '加载聊天记录失败')
  } finally {
    loadingMessages.value = false
  }
}

const selectAccount = async () => {
  selectedSession.value = null
  messages.value = []
  await loadSessions()
}

const selectSession = async (session: ChatSession) => {
  if (selectedSession.value?.sid === session.sid) return
  selectedSession.value = session
  messages.value = []
  await loadMessages()
}

const refreshAll = async () => {
  await loadSessions()
  await loadMessages(true)
  showSuccess('客服消息已刷新')
}

const sendReply = async () => {
  if (!selectedAccountId.value || !selectedSession.value || sending.value) return
  const urls = imageUrls.value.split(',').map(item => item.trim()).filter(Boolean)
  const text = draft.value.trim()
  if (!text && !urls.length) return
  const toId = buyerUserId.value.replace('@goofish', '')
  if (!toId) {
    showError('未能识别买家账号，暂时无法发送')
    return
  }

  sending.value = true
  try {
    const cid = selectedSession.value.sid.replace('@goofish', '')
    for (const imageUrl of urls) {
      const response = await sendImageMessageApi({
        xianyuAccountId: selectedAccountId.value,
        cid,
        toId,
        imageUrl,
        width: 800,
        height: 800,
        xyGoodsId: selectedSession.value.xyGoodsId
      })
      ensureSuccess(response)
    }
    if (text) {
      const response = await sendMessageApi({
        xianyuAccountId: selectedAccountId.value,
        cid,
        toId,
        text,
        xyGoodsId: selectedSession.value.xyGoodsId
      })
      ensureSuccess(response)
    }
    draft.value = ''
    imageUrls.value = ''
    showImageUploader.value = false
    await loadMessages(true)
    await loadSessions(true)
    showSuccess('发送成功，已进入人工接管')
  } catch (error: any) {
    showError(error.message || '发送失败，请检查连接状态')
  } finally {
    sending.value = false
  }
}

const toggleTakeover = async () => {
  if (!selectedAccountId.value || !selectedSession.value || updatingTakeover.value) return
  updatingTakeover.value = true
  try {
    const response = await updateChatTakeover({
      xianyuAccountId: selectedAccountId.value,
      sid: selectedSession.value.sid,
      xyGoodsId: selectedSession.value.xyGoodsId,
      enabled: !hasActiveTakeover.value,
      durationMinutes: takeoverMinutes.value
    })
    ensureSuccess(response)
    await loadSessions(true)
    showSuccess(response.data || (!hasActiveTakeover.value ? '已人工接管' : '已恢复自动回复'))
  } catch (error: any) {
    showError(error.message || '更新人工接管状态失败')
  } finally {
    updatingTakeover.value = false
  }
}

const onComposerKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendReply()
  }
}

let messageTimer: ReturnType<typeof setInterval> | null = null
let sessionTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  setHeaderContent?.(null)
  await loadAccounts()
  messageTimer = setInterval(() => {
    if (document.visibilityState === 'visible') loadMessages(true)
  }, 2500)
  sessionTimer = setInterval(() => {
    if (document.visibilityState === 'visible') loadSessions(true)
  }, 5000)
})

onBeforeUnmount(() => {
  if (messageTimer) clearInterval(messageTimer)
  if (sessionTimer) clearInterval(sessionTimer)
  setHeaderContent?.(null)
})
</script>

<template>
  <div class="customer-service">
    <header class="customer-service__header">
      <div class="customer-service__title-wrap">
        <div class="customer-service__title-icon"><IconMessage /></div>
        <div>
          <h1>在线客服</h1>
          <p>实时查看买家会话，手动回复时会自动暂停该会话的 AI 回复。</p>
        </div>
      </div>
      <div class="customer-service__toolbar">
        <select v-model="selectedAccountId" class="customer-service__select" @change="selectAccount">
          <option v-for="account in accounts" :key="account.id" :value="Number(account.id)">
            {{ account.accountNote || account.unb }}
          </option>
        </select>
        <button class="btn btn--secondary" :disabled="loadingSessions" @click="refreshAll">
          <IconRefresh /> 刷新
        </button>
      </div>
    </header>

    <main class="customer-service__workspace">
      <aside class="session-panel">
        <div class="session-panel__search">
          <input v-model="searchText" type="search" placeholder="搜索买家、商品或消息">
        </div>
        <div class="session-panel__meta">
          <span>会话</span>
          <span>{{ filteredSessions.length }}</span>
        </div>
        <div class="session-panel__list">
          <button
            v-for="session in filteredSessions"
            :key="session.sid"
            class="session-item"
            :class="{ 'session-item--active': selectedSession?.sid === session.sid }"
            @click="selectSession(session)"
          >
            <div class="session-item__avatar">{{ (session.buyerUserName || '买').slice(0, 1) }}</div>
            <div class="session-item__body">
              <div class="session-item__top">
                <strong>{{ session.buyerUserName || '未知买家' }}</strong>
                <time>{{ formatTime(session.lastMessageTime) }}</time>
              </div>
              <p>{{ session.lastMessage || '暂无文字消息' }}</p>
              <span v-if="session.takeoverEndTime" class="session-item__tag">人工中</span>
            </div>
          </button>
          <div v-if="!loadingSessions && !filteredSessions.length" class="session-panel__empty">暂无会话消息</div>
        </div>
      </aside>

      <section class="chat-panel">
        <template v-if="selectedSession">
          <header class="chat-panel__header">
            <div class="chat-panel__buyer">
              <div class="chat-panel__avatar">{{ (selectedSession.buyerUserName || '买').slice(0, 1) }}</div>
              <div>
                <h2>{{ selectedSession.buyerUserName || '未知买家' }}</h2>
                <p>{{ hasActiveTakeover ? `人工接管至 ${formatTakeoverEnd(selectedSession.takeoverEndTime)}` : 'AI 自动回复可按商品设置执行' }}</p>
              </div>
            </div>
            <span class="chat-panel__state" :class="{ 'chat-panel__state--manual': hasActiveTakeover }">
              {{ hasActiveTakeover ? '人工接管中' : '自动回复中' }}
            </span>
          </header>

          <div ref="messageListRef" class="chat-panel__messages">
            <div v-if="loadingMessages" class="chat-panel__loading">正在加载聊天记录…</div>
            <template v-else>
              <div v-if="!messages.length" class="chat-panel__empty">暂时没有可展示的聊天记录</div>
              <article
                v-for="message in messages"
                :key="message.id"
                class="chat-message"
                :class="{ 'chat-message--mine': isMine(message), 'chat-message--system': !isMine(message) && message.contentType !== 1 }"
              >
                <div v-if="message.contentType === 1 || isMine(message)" class="chat-message__avatar">
                  <IconUser />
                </div>
                <div class="chat-message__content">
                  <div class="chat-message__meta">
                    <span>{{ isMine(message) ? '我' : message.senderUserName || '买家' }}</span>
                    <time>{{ formatTime(message.messageTime) }}</time>
                  </div>
                  <img v-if="isImageMessage(message) && getImageUrl(message)" :src="getImageUrl(message)" class="chat-message__image" alt="聊天图片">
                  <p v-else>{{ message.msgContent }}</p>
                </div>
              </article>
            </template>
          </div>

          <footer class="chat-panel__composer">
            <MultiImageUploader
              v-if="showImageUploader && selectedAccountId"
              v-model="imageUrls"
              :account-id="selectedAccountId"
              class="chat-panel__uploader"
            />
            <div class="chat-panel__composer-row">
              <textarea
                v-model="draft"
                rows="2"
                placeholder="输入回复，Enter 发送，Shift + Enter 换行"
                @keydown="onComposerKeydown"
              />
              <div class="chat-panel__composer-actions">
                <button class="chat-panel__image-button" :class="{ 'is-active': showImageUploader || imageUrls }" title="发送图片" @click="showImageUploader = !showImageUploader">
                  <IconImage />
                </button>
                <button class="chat-panel__send-button" :disabled="sending || (!draft.trim() && !imageUrls.trim())" @click="sendReply">
                  <IconSend />
                  {{ sending ? '发送中' : '发送' }}
                </button>
              </div>
            </div>
          </footer>
        </template>
        <div v-else class="chat-panel__placeholder">选择左侧会话，即可开始在线客服。</div>
      </section>

      <aside class="detail-panel">
        <template v-if="selectedSession">
          <section>
            <h3>会话信息</h3>
            <dl>
              <div><dt>买家</dt><dd>{{ selectedSession.buyerUserName || '未知买家' }}</dd></div>
              <div><dt>买家 ID</dt><dd>{{ buyerUserId || '-' }}</dd></div>
              <div><dt>商品 ID</dt><dd>{{ selectedSession.xyGoodsId || '-' }}</dd></div>
              <div><dt>当前账号</dt><dd>{{ selectedAccount?.accountNote || selectedAccount?.unb || '-' }}</dd></div>
            </dl>
          </section>
          <section class="detail-panel__takeover">
            <h3>人工接管</h3>
            <p>{{ hasActiveTakeover ? `该会话已暂停 AI 回复，${formatTakeoverEnd(selectedSession.takeoverEndTime)} 后自动恢复。` : '接管后，该会话的新消息不会触发 AI 或关键词自动回复。' }}</p>
            <div v-if="!hasActiveTakeover" class="detail-panel__duration">
              <label for="takeover-minutes">接管时长</label>
              <select id="takeover-minutes" v-model.number="takeoverMinutes">
                <option :value="10">10 分钟</option>
                <option :value="30">30 分钟</option>
                <option :value="60">1 小时</option>
                <option :value="240">4 小时</option>
              </select>
            </div>
            <button class="detail-panel__takeover-button" :class="{ 'detail-panel__takeover-button--release': hasActiveTakeover }" :disabled="updatingTakeover" @click="toggleTakeover">
              {{ updatingTakeover ? '处理中…' : hasActiveTakeover ? '恢复自动回复' : '开始人工接管' }}
            </button>
          </section>
        </template>
        <div v-else class="detail-panel__empty">选择会话后显示买家、商品与处理状态。</div>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.customer-service { height: calc(100vh - 64px); min-height: 620px; padding: 24px; color: #1c1c1e; display: flex; flex-direction: column; gap: 18px; box-sizing: border-box; }
.customer-service__header { display:flex; justify-content:space-between; align-items:center; gap:16px; flex-shrink:0; }
.customer-service__title-wrap, .customer-service__toolbar, .chat-panel__buyer, .chat-panel__composer-row, .chat-panel__composer-actions { display:flex; align-items:center; }
.customer-service__title-wrap { gap:11px; }
.customer-service__title-icon { display:grid; place-items:center; width:40px; height:40px; border-radius:12px; color:#0a84ff; background:rgba(10,132,255,.11); }
.customer-service__title-icon svg { width:22px; }
h1, h2, h3, p, dl { margin:0; }
h1 { font-size:24px; line-height:1.2; }
.customer-service__title-wrap p { margin-top:4px; color:rgba(28,28,30,.56); font-size:12px; }
.customer-service__toolbar { gap:9px; }
.customer-service__select, .detail-panel select { height:36px; border:1px solid rgba(60,60,67,.18); border-radius:9px; padding:0 10px; background:#fff; color:#1c1c1e; }
.customer-service__workspace { min-height:0; flex:1; display:grid; grid-template-columns:280px minmax(360px, 1fr) 260px; overflow:hidden; background:rgba(255,255,255,.9); border:1px solid rgba(60,60,67,.12); border-radius:16px; box-shadow:0 7px 28px rgba(0,0,0,.05); }
.session-panel, .detail-panel { background:rgba(248,249,251,.75); min-width:0; }
.session-panel { display:flex; flex-direction:column; border-right:1px solid rgba(60,60,67,.1); }
.session-panel__search { padding:14px 13px 9px; }
.session-panel__search input { width:100%; height:36px; box-sizing:border-box; border:1px solid rgba(60,60,67,.14); border-radius:9px; background:#fff; padding:0 10px; outline:none; font:inherit; font-size:13px; }
.session-panel__search input:focus { border-color:rgba(10,132,255,.65); }
.session-panel__meta { display:flex; justify-content:space-between; padding:0 16px 10px; color:rgba(28,28,30,.48); font-size:12px; }
.session-panel__list { flex:1; overflow-y:auto; }
.session-item { display:flex; width:100%; gap:10px; padding:12px 13px; text-align:left; border:0; border-top:1px solid rgba(60,60,67,.06); background:transparent; cursor:pointer; color:inherit; font:inherit; }
.session-item:hover { background:rgba(10,132,255,.05); }
.session-item--active { background:rgba(10,132,255,.11); box-shadow:inset 3px 0 #0a84ff; }
.session-item__avatar, .chat-panel__avatar { display:grid; place-items:center; flex-shrink:0; width:36px; height:36px; border-radius:50%; background:#d9ecff; color:#0a84ff; font-size:14px; font-weight:700; }
.session-item__body { min-width:0; flex:1; }
.session-item__top { display:flex; justify-content:space-between; gap:8px; align-items:center; }
.session-item__top strong { font-size:13px; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
.session-item__top time { flex-shrink:0; color:rgba(28,28,30,.45); font-size:11px; }
.session-item p { overflow:hidden; margin-top:4px; color:rgba(28,28,30,.56); font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
.session-item__tag { display:inline-block; margin-top:5px; padding:1px 6px; border-radius:999px; color:#b55f00; background:rgba(255,149,0,.14); font-size:10px; }
.session-panel__empty, .chat-panel__placeholder, .detail-panel__empty { padding:32px 16px; color:rgba(28,28,30,.45); font-size:13px; text-align:center; }
.chat-panel { min-width:0; display:flex; flex-direction:column; background:linear-gradient(180deg, #fff 0%, #f7f9fc 100%); }
.chat-panel__header { display:flex; min-height:66px; padding:0 20px; align-items:center; justify-content:space-between; gap:12px; border-bottom:1px solid rgba(60,60,67,.1); }
.chat-panel__buyer { gap:10px; min-width:0; }
.chat-panel__avatar { width:38px; height:38px; }
.chat-panel__buyer h2 { font-size:15px; }
.chat-panel__buyer p { margin-top:3px; color:rgba(28,28,30,.5); font-size:12px; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
.chat-panel__state { flex-shrink:0; padding:4px 9px; border-radius:999px; background:rgba(48,209,88,.14); color:#16813a; font-size:12px; }
.chat-panel__state--manual { background:rgba(255,149,0,.14); color:#b55f00; }
.chat-panel__messages { flex:1; overflow-y:auto; padding:22px; }
.chat-panel__loading, .chat-panel__empty { height:100%; display:grid; place-items:center; color:rgba(28,28,30,.48); font-size:13px; }
.chat-message { display:flex; align-items:flex-end; gap:8px; margin-bottom:16px; }
.chat-message--mine { flex-direction:row-reverse; }
.chat-message--system { justify-content:center; }
.chat-message__avatar { width:30px; height:30px; color:#fff; background:#4aa3ff; }
.chat-message--mine .chat-message__avatar { background:#30a657; }
.chat-message__avatar svg { width:15px; }
.chat-message__content { max-width:72%; }
.chat-message__meta { display:flex; align-items:center; gap:6px; margin:0 3px 4px; color:rgba(28,28,30,.45); font-size:11px; }
.chat-message--mine .chat-message__meta { justify-content:flex-end; }
.chat-message__content p { padding:9px 12px; border-radius:13px 13px 13px 3px; background:#fff; box-shadow:0 2px 8px rgba(0,0,0,.06); font-size:13px; line-height:1.55; word-break:break-word; white-space:pre-wrap; }
.chat-message--mine .chat-message__content p { border-radius:13px 13px 3px 13px; background:#dff5e5; }
.chat-message--system .chat-message__content p { border-radius:999px; background:rgba(60,60,67,.08); box-shadow:none; color:rgba(28,28,30,.55); font-size:11px; }
.chat-message__image { display:block; max-width:240px; max-height:200px; border-radius:10px; object-fit:cover; }
.chat-panel__composer { padding:11px 14px; border-top:1px solid rgba(60,60,67,.1); background:#fff; }
.chat-panel__uploader { margin-bottom:8px; }
.chat-panel__composer-row { align-items:stretch; gap:9px; }
.chat-panel__composer textarea { min-width:0; flex:1; resize:none; border:1px solid rgba(60,60,67,.16); border-radius:10px; padding:8px 10px; font:inherit; font-size:13px; outline:none; }
.chat-panel__composer textarea:focus { border-color:rgba(10,132,255,.65); }
.chat-panel__composer-actions { gap:6px; }
.chat-panel__image-button, .chat-panel__send-button { display:inline-flex; align-items:center; justify-content:center; gap:5px; border:0; border-radius:9px; cursor:pointer; font:inherit; }
.chat-panel__image-button { width:38px; color:rgba(28,28,30,.64); background:#f0f2f5; }
.chat-panel__image-button.is-active { color:#0a84ff; background:rgba(10,132,255,.12); }
.chat-panel__image-button svg { width:18px; }
.chat-panel__send-button { min-width:65px; color:#fff; background:#0a84ff; padding:0 11px; font-size:13px; }
.chat-panel__send-button svg { width:15px; }
.chat-panel__send-button:disabled { cursor:not-allowed; opacity:.45; }
.detail-panel { border-left:1px solid rgba(60,60,67,.1); padding:18px; overflow-y:auto; }
.detail-panel section + section { margin-top:24px; padding-top:22px; border-top:1px solid rgba(60,60,67,.1); }
.detail-panel h3 { margin-bottom:14px; font-size:14px; }
.detail-panel dl { display:grid; gap:12px; }
.detail-panel dl div { display:grid; gap:3px; }
.detail-panel dt { color:rgba(28,28,30,.48); font-size:11px; }
.detail-panel dd { overflow:hidden; margin:0; color:#263b50; font-size:12px; text-overflow:ellipsis; word-break:break-all; }
.detail-panel__takeover p { color:rgba(28,28,30,.58); font-size:12px; line-height:1.6; }
.detail-panel__duration { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-top:15px; font-size:12px; }
.detail-panel__duration select { height:32px; font-size:12px; }
.detail-panel__takeover-button { width:100%; height:36px; margin-top:14px; border:0; border-radius:9px; color:#fff; background:#ff9500; cursor:pointer; font:inherit; font-size:13px; }
.detail-panel__takeover-button--release { background:#0a84ff; }
.detail-panel__takeover-button:disabled { opacity:.55; cursor:not-allowed; }
@media (max-width: 1100px) { .customer-service__workspace { grid-template-columns:250px minmax(360px, 1fr); } .detail-panel { display:none; } }
@media (max-width: 720px) { .customer-service { height:auto; min-height:calc(100vh - 54px); padding:14px; } .customer-service__header { align-items:flex-start; flex-direction:column; } .customer-service__toolbar { width:100%; } .customer-service__select { flex:1; min-width:0; } .customer-service__workspace { min-height:720px; grid-template-columns:1fr; grid-template-rows:250px minmax(470px, 1fr); } .session-panel { border-right:0; border-bottom:1px solid rgba(60,60,67,.1); } .session-panel__list { display:flex; overflow-x:auto; overflow-y:hidden; } .session-item { min-width:230px; border-top:0; border-right:1px solid rgba(60,60,67,.08); } .chat-panel__messages { padding:16px 12px; } .chat-message__content { max-width:82%; } }
</style>

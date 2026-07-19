<script setup lang="ts">
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { getAccountList } from '@/api/account'
import {
  getChatSessions,
  getContextMessages,
  markChatSessionRead,
  addChatBuyerTag,
  removeChatBuyerTag,
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
const sessionFilter = ref<'ALL' | 'UNREAD' | 'TAKEOVER'>('ALL')
const tagFilter = ref('')
const tagDraft = ref('')
const draft = ref('')
const imageUrls = ref('')
const showImageUploader = ref(false)
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const sending = ref(false)
const updatingTakeover = ref(false)
const updatingTag = ref(false)
const messageListRef = ref<HTMLElement | null>(null)
const takeoverMinutes = ref(10)
const setHeaderContent = inject<(content: any) => void>('setHeaderContent')

const selectedAccount = computed(() =>
  accounts.value.find(account => Number(account.id) === selectedAccountId.value) || null
)

const getSessionTags = (session?: ChatSession | null) => (session?.buyerTags || '')
  .split(',')
  .map(tag => tag.trim())
  .filter(Boolean)

const allTags = computed(() => [...new Set(sessions.value.flatMap(getSessionTags))].sort((a, b) => a.localeCompare(b, 'zh-CN')))

const unreadTotal = computed(() => sessions.value.reduce((total, session) => total + Number(session.unreadCount || 0), 0))

const selectedSessionTags = computed(() => getSessionTags(selectedSession.value))

const normalizeComparableText = (value: unknown) =>
  String(value ?? '').replace(/[\s【】\[\]()（）"'“”‘’]/g, '').trim()

const isUsableDisplayName = (candidate: unknown, content?: unknown) => {
  const name = String(candidate ?? '').trim()
  if (!name || name.length > 40 || ['未知买家', '工作台通知', '系统通知'].includes(name)) return false
  const normalizedName = normalizeComparableText(name)
  const normalizedContent = normalizeComparableText(content)
  // 部分历史消息会把正文或正文开头误写进昵称字段，例如“我完成了评价”被写成“我”。
  // 这类数据不能作为买家名展示，统一回退到买家 ID。
  const looksLikeMessageFragment = normalizedName.length <= 2
    && normalizedContent.length > normalizedName.length
    && normalizedContent.startsWith(normalizedName)
  return Boolean(normalizedName && normalizedName !== normalizedContent && !looksLikeMessageFragment)
}

const getBuyerDisplayName = (session?: ChatSession | null) => {
  const buyerName = String(session?.buyerUserName || '').trim()
  const lastMessage = String(session?.lastMessage || '').trim()
  if (isUsableDisplayName(buyerName, lastMessage)) {
    return buyerName
  }
  const buyerId = String(session?.buyerUserId || '').trim()
  return buyerId ? `买家 ${buyerId}` : '未知买家'
}

const getMessageSenderName = (message: ChatMessage) => {
  if (isMine(message)) return '我'
  const senderName = String(message.senderUserName || '').trim()
  if (isUsableDisplayName(senderName, message.msgContent)) {
    return senderName
  }
  return getBuyerDisplayName(selectedSession.value)
}

const getAccountAvatar = (account: Account) => String(account.avatarUrl || '').trim()

const getSessionAvatar = (session?: ChatSession | null) => {
  const avatar = String(session?.buyerAvatarUrl || session?.buyerAvatar || '').trim()
  return /^https?:\/\//i.test(avatar) ? avatar : ''
}

const filteredSessions = computed(() => {
  const keyword = searchText.value.trim().toLowerCase()
  return sessions.value.filter(session => {
    const matchesKeyword = !keyword || [session.buyerUserName, session.buyerUserId, session.goodsTitle, session.lastMessage, session.xyGoodsId]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
      .includes(keyword)
    const matchesFilter = sessionFilter.value === 'ALL'
      || (sessionFilter.value === 'UNREAD' && Number(session.unreadCount || 0) > 0)
      || (sessionFilter.value === 'TAKEOVER' && Boolean(session.takeoverEndTime && new Date(session.takeoverEndTime.replace(' ', 'T')).getTime() > Date.now()))
    const matchesTag = !tagFilter.value || getSessionTags(session).includes(tagFilter.value)
    return matchesKeyword && matchesFilter && matchesTag
  })
})

const updateLocalReadState = (sid: string) => {
  sessions.value = sessions.value.map(session => session.sid === sid
    ? { ...session, unreadCount: 0 }
    : session)
  if (selectedSession.value?.sid === sid) {
    selectedSession.value = { ...selectedSession.value, unreadCount: 0 }
  }
}

const markSelectedSessionRead = async () => {
  const session = selectedSession.value
  if (!selectedAccountId.value || !session?.sid || !Number(session.unreadCount || 0)) return
  try {
    const response = await markChatSessionRead({
      xianyuAccountId: selectedAccountId.value,
      sid: session.sid
    })
    ensureSuccess(response)
    updateLocalReadState(session.sid)
  } catch (error) {
    console.warn('标记客服会话已读失败', error)
  }
}

const applyLocalBuyerTag = (buyerId: string, tagName: string, shouldAdd: boolean) => {
  sessions.value = sessions.value.map(session => {
    if (session.buyerUserId !== buyerId) return session
    const tags = new Set(getSessionTags(session))
    if (shouldAdd) tags.add(tagName)
    else tags.delete(tagName)
    return { ...session, buyerTags: [...tags].sort((a, b) => a.localeCompare(b, 'zh-CN')).join(',') }
  })
  if (selectedSession.value?.buyerUserId === buyerId) {
    selectedSession.value = sessions.value.find(session => session.sid === selectedSession.value?.sid) || selectedSession.value
  }
}

const addBuyerTag = async () => {
  const buyerId = buyerUserId.value
  const tagName = tagDraft.value.trim()
  if (!selectedAccountId.value || !buyerId || !tagName || updatingTag.value) return
  if (tagName.includes(',')) {
    showError('标签不能包含英文逗号')
    return
  }
  updatingTag.value = true
  try {
    const response = await addChatBuyerTag({
      xianyuAccountId: selectedAccountId.value,
      buyerUserId: buyerId,
      tagName
    })
    ensureSuccess(response)
    applyLocalBuyerTag(buyerId, tagName, true)
    tagDraft.value = ''
    showSuccess('买家标签已添加')
  } catch (error: any) {
    showError(error.message || '添加标签失败')
  } finally {
    updatingTag.value = false
  }
}

const removeBuyerTag = async (tagName: string) => {
  const buyerId = buyerUserId.value
  if (!selectedAccountId.value || !buyerId || updatingTag.value) return
  updatingTag.value = true
  try {
    const response = await removeChatBuyerTag({
      xianyuAccountId: selectedAccountId.value,
      buyerUserId: buyerId,
      tagName
    })
    ensureSuccess(response)
    applyLocalBuyerTag(buyerId, tagName, false)
    if (tagFilter.value === tagName && !allTags.value.includes(tagName)) tagFilter.value = ''
    showSuccess('买家标签已删除')
  } catch (error: any) {
    showError(error.message || '删除标签失败')
  } finally {
    updatingTag.value = false
  }
}

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
    await markSelectedSessionRead()
  } catch (error: any) {
    if (!silent) showError(error.message || '加载聊天记录失败')
  } finally {
    loadingMessages.value = false
  }
}

const selectAccount = async (accountId?: number) => {
  const nextAccountId = Number(accountId ?? selectedAccountId.value)
  if (!nextAccountId) return
  const changed = selectedAccountId.value !== nextAccountId
  selectedAccountId.value = nextAccountId
  if (changed) {
    selectedSession.value = null
    messages.value = []
    tagFilter.value = ''
    sessionFilter.value = 'ALL'
  }
  await loadSessions()
}

const selectSession = async (session: ChatSession) => {
  if (selectedSession.value?.sid === session.sid) {
    await markSelectedSessionRead()
    return
  }
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
        <span v-if="unreadTotal > 0" class="customer-service__unread">{{ unreadTotal }} 条未读</span>
        <select v-model="selectedAccountId" class="customer-service__select" @change="selectAccount()">
          <option v-for="account in accounts" :key="account.id" :value="Number(account.id)">
            {{ account.accountNote || account.unb }}
          </option>
        </select>
        <button class="customer-service__refresh-button" :class="{ 'is-loading': loadingSessions }" :disabled="loadingSessions" @click="refreshAll">
          <IconRefresh />
          <span>{{ loadingSessions ? '刷新中' : '刷新会话' }}</span>
        </button>
      </div>
    </header>

    <main class="customer-service__workspace">
      <aside class="account-panel">
        <div class="account-panel__header">
          <div>
            <strong>账号列表</strong>
            <span>{{ accounts.length }}</span>
          </div>
          <button type="button" title="刷新账号与会话" :disabled="loadingSessions" @click="loadAccounts">
            <IconRefresh />
          </button>
        </div>
        <div class="account-panel__list">
          <button
            v-for="account in accounts"
            :key="account.id"
            type="button"
            class="account-item"
            :class="{ 'account-item--active': Number(account.id) === selectedAccountId }"
            @click="selectAccount(Number(account.id))"
          >
            <div class="account-item__avatar">
              <img v-if="getAccountAvatar(account)" :src="getAccountAvatar(account)" :alt="account.accountNote || account.unb">
              <span v-else>{{ (account.accountNote || account.unb || '闲').slice(0, 1) }}</span>
            </div>
            <div class="account-item__body">
              <strong>{{ account.accountNote || account.unb || '未命名账号' }}</strong>
              <span>UNB: {{ account.unb || '-' }}</span>
              <em :class="{ 'is-online': Number(account.status) === 1 }">{{ Number(account.status) === 1 ? '账号正常' : '已停用' }}</em>
            </div>
          </button>
          <div v-if="!accounts.length" class="account-panel__empty">暂无可用账号</div>
        </div>
      </aside>

      <aside class="session-panel">
        <div class="session-panel__search">
          <input v-model="searchText" type="search" placeholder="搜索买家、商品或消息">
        </div>
        <div class="session-panel__filters">
          <button :class="{ 'is-active': sessionFilter === 'ALL' }" @click="sessionFilter = 'ALL'">全部</button>
          <button :class="{ 'is-active': sessionFilter === 'UNREAD' }" @click="sessionFilter = 'UNREAD'">
            未读<span v-if="unreadTotal > 0">{{ unreadTotal }}</span>
          </button>
          <button :class="{ 'is-active': sessionFilter === 'TAKEOVER' }" @click="sessionFilter = 'TAKEOVER'">人工接管</button>
        </div>
        <div v-if="allTags.length" class="session-panel__tag-filter">
          <select v-model="tagFilter" aria-label="按买家标签筛选">
            <option value="">全部标签</option>
            <option v-for="tag in allTags" :key="tag" :value="tag">{{ tag }}</option>
          </select>
        </div>
        <div class="session-panel__meta">
          <span>会话</span>
          <span>{{ filteredSessions.length }} / {{ sessions.length }}</span>
        </div>
        <div class="session-panel__list">
          <button
            v-for="session in filteredSessions"
            :key="session.sid"
            class="session-item"
            :class="{ 'session-item--active': selectedSession?.sid === session.sid }"
            @click="selectSession(session)"
          >
            <div class="session-item__avatar">
              <img v-if="getSessionAvatar(session)" :src="getSessionAvatar(session)" :alt="getBuyerDisplayName(session)">
              <span v-else>{{ getBuyerDisplayName(session).slice(0, 1) }}</span>
            </div>
            <div class="session-item__body">
              <div class="session-item__top">
                <strong>{{ getBuyerDisplayName(session) }}</strong>
                <span class="session-item__right">
                  <time>{{ formatTime(session.lastMessageTime) }}</time>
                  <b v-if="Number(session.unreadCount || 0) > 0" class="session-item__unread">{{ session.unreadCount }}</b>
                </span>
              </div>
              <p>{{ session.lastMessage || '暂无文字消息' }}</p>
              <div v-if="session.takeoverEndTime || getSessionTags(session).length" class="session-item__tags">
                <span v-if="session.takeoverEndTime" class="session-item__tag session-item__tag--manual">人工中</span>
                <span v-for="tag in getSessionTags(session)" :key="tag" class="session-item__tag">{{ tag }}</span>
              </div>
            </div>
          </button>
          <div v-if="!loadingSessions && !filteredSessions.length" class="session-panel__empty">暂无会话消息</div>
        </div>
      </aside>

      <section class="chat-panel">
        <template v-if="selectedSession">
          <header class="chat-panel__header">
            <div class="chat-panel__buyer">
              <div class="chat-panel__avatar">
                <img v-if="getSessionAvatar(selectedSession)" :src="getSessionAvatar(selectedSession)" :alt="getBuyerDisplayName(selectedSession)">
                <span v-else>{{ getBuyerDisplayName(selectedSession).slice(0, 1) }}</span>
              </div>
              <div>
                <h2>{{ getBuyerDisplayName(selectedSession) }}</h2>
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
                <div class="chat-message__avatar">
                  <img v-if="!isMine(message) && getSessionAvatar(selectedSession)" :src="getSessionAvatar(selectedSession)" :alt="getMessageSenderName(message)">
                  <IconUser v-else />
                </div>
                <div class="chat-message__content">
                  <div class="chat-message__meta">
                  <span>{{ getMessageSenderName(message) }}</span>
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
              <div><dt>买家</dt><dd>{{ getBuyerDisplayName(selectedSession) }}</dd></div>
              <div><dt>买家 ID</dt><dd>{{ buyerUserId || '-' }}</dd></div>
              <div><dt>商品标题</dt><dd :title="selectedSession.goodsTitle || ''">{{ selectedSession.goodsTitle || '未同步商品标题' }}</dd></div>
              <div><dt>商品 ID</dt><dd>{{ selectedSession.xyGoodsId || '-' }}</dd></div>
              <div><dt>当前账号</dt><dd>{{ selectedAccount?.accountNote || selectedAccount?.unb || '-' }}</dd></div>
            </dl>
          </section>
          <section class="detail-panel__tags">
            <h3>买家标签</h3>
            <p>标签仅用于 XianYuPlus 内部筛选，不会发送给买家。</p>
            <div class="detail-panel__tag-list">
              <span v-for="tag in selectedSessionTags" :key="tag" class="detail-panel__tag">
                {{ tag }}
                <button type="button" :disabled="updatingTag" :aria-label="`删除标签 ${tag}`" @click="removeBuyerTag(tag)">×</button>
              </span>
              <span v-if="!selectedSessionTags.length" class="detail-panel__tag-empty">暂未设置标签</span>
            </div>
            <form class="detail-panel__tag-form" @submit.prevent="addBuyerTag">
              <input v-model="tagDraft" maxlength="20" placeholder="例如：老客户、待跟进" :disabled="!buyerUserId || updatingTag">
              <button type="submit" :disabled="!tagDraft.trim() || !buyerUserId || updatingTag">{{ updatingTag ? '处理中' : '添加' }}</button>
            </form>
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
.customer-service__unread { display:inline-flex; align-items:center; height:28px; padding:0 9px; border-radius:999px; color:#b42318; background:#fef3f2; font-size:12px; font-weight:700; white-space:nowrap; }
.customer-service__select, .detail-panel select { height:36px; border:1px solid rgba(60,60,67,.18); border-radius:9px; padding:0 10px; background:#fff; color:#1c1c1e; }
.customer-service__select { display:none; }
.customer-service__refresh-button { height:36px; display:inline-flex; align-items:center; justify-content:center; gap:6px; padding:0 13px; border:1px solid rgba(10,132,255,.28); border-radius:9px; color:#0969c7; background:linear-gradient(180deg, #fff 0%, #f4f9ff 100%); box-shadow:0 3px 10px rgba(10,132,255,.10); cursor:pointer; font:inherit; font-size:12px; font-weight:600; white-space:nowrap; transition:transform .15s ease, box-shadow .15s ease, background .15s ease; }
.customer-service__refresh-button svg { width:15px; height:15px; }
.customer-service__refresh-button:hover:not(:disabled) { background:#eef7ff; box-shadow:0 5px 14px rgba(10,132,255,.18); transform:translateY(-1px); }
.customer-service__refresh-button:active:not(:disabled) { transform:translateY(0); box-shadow:0 2px 6px rgba(10,132,255,.12); }
.customer-service__refresh-button:disabled { cursor:not-allowed; opacity:.62; }
.customer-service__refresh-button.is-loading svg { animation:customer-service-spin .72s linear infinite; }
@keyframes customer-service-spin { to { transform:rotate(360deg); } }
.customer-service__workspace { min-height:0; flex:1; display:grid; grid-template-columns:196px 280px minmax(360px, 1fr) 260px; overflow:hidden; background:rgba(255,255,255,.9); border:1px solid rgba(60,60,67,.12); border-radius:16px; box-shadow:0 7px 28px rgba(0,0,0,.05); }
.account-panel, .session-panel, .chat-panel, .detail-panel { min-height:0; background:rgba(248,249,251,.75); min-width:0; }
.account-panel { display:flex; flex-direction:column; border-right:1px solid rgba(60,60,67,.1); background:#fbfcfe; }
.account-panel__header { display:flex; align-items:center; justify-content:space-between; min-height:58px; padding:0 14px; border-bottom:1px solid rgba(60,60,67,.1); }
.account-panel__header > div { display:flex; align-items:center; gap:8px; }
.account-panel__header strong { color:#20293a; font-size:14px; }
.account-panel__header span { display:inline-grid; min-width:20px; height:20px; padding:0 6px; place-items:center; border-radius:999px; color:#3173d8; background:#edf3ff; font-size:12px; font-weight:700; box-sizing:border-box; }
.account-panel__header button { display:grid; width:30px; height:30px; padding:0; place-items:center; border:0; border-radius:9px; color:#5285c7; background:transparent; cursor:pointer; }
.account-panel__header button:hover:not(:disabled) { background:#edf5ff; }
.account-panel__header button:disabled { cursor:not-allowed; opacity:.5; }
.account-panel__header button svg { width:16px; height:16px; }
.account-panel__list { flex:1; min-height:0; overflow-y:auto; padding:10px; }
.account-item { display:flex; width:100%; align-items:flex-start; gap:10px; padding:12px 10px; border:1px solid transparent; border-radius:12px; color:inherit; background:transparent; cursor:pointer; font:inherit; text-align:left; transition:.2s ease; }
.account-item:hover { background:#f4f8ff; }
.account-item--active { border-color:#b9dcff; background:linear-gradient(135deg,#eff7ff,#f1fbf5); box-shadow:0 5px 14px rgba(48,134,235,.12); }
.account-item__avatar { display:grid; flex:0 0 36px; width:36px; height:36px; overflow:hidden; place-items:center; border-radius:50%; color:#226ac9; background:linear-gradient(135deg,#d7eaff,#a9ceff); font-weight:700; }
.account-item__avatar img, .session-item__avatar img, .chat-panel__avatar img, .chat-message__avatar img { display:block; width:100%; height:100%; object-fit:cover; }
.account-item__body { display:grid; min-width:0; gap:3px; }
.account-item__body strong { overflow:hidden; color:#233044; font-size:14px; text-overflow:ellipsis; white-space:nowrap; }
.account-item__body span { overflow:hidden; color:#8994a7; font-size:11px; text-overflow:ellipsis; white-space:nowrap; }
.account-item__body em { color:#a95d56; font-size:11px; font-style:normal; }
.account-item__body em::before { display:inline-block; width:6px; height:6px; margin-right:4px; border-radius:50%; background:#f17972; content:''; }
.account-item__body em.is-online { color:#21935e; }
.account-item__body em.is-online::before { background:#30c776; }
.account-panel__empty { padding:32px 12px; color:#97a3b7; font-size:13px; text-align:center; }
.session-panel { display:flex; flex-direction:column; border-right:1px solid rgba(60,60,67,.1); }
.session-panel__search { padding:14px 13px 9px; }
.session-panel__search input { width:100%; height:36px; box-sizing:border-box; border:1px solid rgba(60,60,67,.14); border-radius:9px; background:#fff; padding:0 10px; outline:none; font:inherit; font-size:13px; }
.session-panel__search input:focus { border-color:rgba(10,132,255,.65); }
.session-panel__filters { display:flex; gap:6px; padding:0 13px 9px; overflow-x:auto; }
.session-panel__filters button { flex:0 0 auto; border:0; border-radius:999px; padding:5px 8px; color:rgba(28,28,30,.58); background:rgba(60,60,67,.08); cursor:pointer; font:inherit; font-size:11px; }
.session-panel__filters button.is-active { color:#075fae; background:rgba(10,132,255,.14); font-weight:700; }
.session-panel__filters button span { display:inline-grid; min-width:14px; height:14px; margin-left:3px; place-items:center; border-radius:999px; color:#fff; background:#ff3b30; font-size:9px; }
.session-panel__tag-filter { padding:0 13px 9px; }
.session-panel__tag-filter select { width:100%; height:31px; border:1px solid rgba(60,60,67,.13); border-radius:8px; padding:0 8px; color:rgba(28,28,30,.72); background:#fff; font:inherit; font-size:12px; }
.session-panel__meta { display:flex; justify-content:space-between; padding:0 16px 10px; color:rgba(28,28,30,.48); font-size:12px; }
.session-panel__list { flex:1; overflow-y:auto; }
.session-item { display:flex; width:100%; gap:10px; padding:12px 13px; text-align:left; border:0; border-top:1px solid rgba(60,60,67,.06); background:transparent; cursor:pointer; color:inherit; font:inherit; }
.session-item:hover { background:rgba(10,132,255,.05); }
.session-item--active { background:rgba(10,132,255,.11); box-shadow:inset 3px 0 #0a84ff; }
.session-item__avatar, .chat-panel__avatar { display:grid; place-items:center; flex-shrink:0; width:36px; height:36px; overflow:hidden; border-radius:50%; background:#d9ecff; color:#0a84ff; font-size:14px; font-weight:700; }
.session-item__body { min-width:0; flex:1; }
.session-item__top { display:flex; justify-content:space-between; gap:8px; align-items:center; }
.session-item__top strong { font-size:13px; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
.session-item__right { display:flex; align-items:center; gap:5px; flex-shrink:0; }
.session-item__top time { color:rgba(28,28,30,.45); font-size:11px; }
.session-item__unread { display:grid; min-width:17px; height:17px; padding:0 3px; place-items:center; border-radius:999px; color:#fff; background:#ff3b30; font-size:10px; line-height:1; }
.session-item p { overflow:hidden; margin-top:4px; color:rgba(28,28,30,.56); font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
.session-item__tags { display:flex; gap:4px; margin-top:5px; overflow:hidden; white-space:nowrap; }
.session-item__tag { display:inline-block; max-width:92px; overflow:hidden; padding:1px 6px; border-radius:999px; color:#52606d; background:rgba(60,60,67,.1); font-size:10px; text-overflow:ellipsis; }
.session-item__tag--manual { color:#b55f00; background:rgba(255,149,0,.14); }
.session-panel__empty, .chat-panel__placeholder, .detail-panel__empty { padding:32px 16px; color:rgba(28,28,30,.45); font-size:13px; text-align:center; }
.chat-panel { display:flex; flex-direction:column; background:linear-gradient(180deg, #fff 0%, #f7f9fc 100%); }
.chat-panel__header { display:flex; min-height:66px; padding:0 20px; align-items:center; justify-content:space-between; gap:12px; border-bottom:1px solid rgba(60,60,67,.1); }
.chat-panel__buyer { gap:10px; min-width:0; }
.chat-panel__avatar { width:38px; height:38px; }
.chat-panel__buyer h2 { font-size:15px; }
.chat-panel__buyer p { margin-top:3px; color:rgba(28,28,30,.5); font-size:12px; overflow:hidden; white-space:nowrap; text-overflow:ellipsis; }
.chat-panel__state { flex-shrink:0; padding:4px 9px; border-radius:999px; background:rgba(48,209,88,.14); color:#16813a; font-size:12px; }
.chat-panel__state--manual { background:rgba(255,149,0,.14); color:#b55f00; }
.chat-panel__messages { min-height:0; flex:1; overflow-y:auto; padding:22px; }
.chat-panel__loading, .chat-panel__empty { height:100%; display:grid; place-items:center; color:rgba(28,28,30,.48); font-size:13px; }
.chat-message { display:flex; align-items:flex-end; gap:8px; margin-bottom:16px; }
.chat-message--mine { flex-direction:row-reverse; }
.chat-message--system { justify-content:center; }
.chat-message__avatar { display:grid; flex:0 0 30px; width:30px; height:30px; overflow:hidden; place-items:center; border-radius:50%; color:#fff; background:#4aa3ff; }
.chat-message--mine .chat-message__avatar { background:#30a657; }
.chat-message__avatar svg { width:15px; }
.chat-message__content { max-width:72%; }
.chat-message__meta { display:flex; align-items:center; gap:6px; margin:0 3px 4px; color:rgba(28,28,30,.45); font-size:11px; }
.chat-message--mine .chat-message__meta { justify-content:flex-end; }
.chat-message__content p { padding:9px 12px; border-radius:13px 13px 13px 3px; background:#fff; box-shadow:0 2px 8px rgba(0,0,0,.06); font-size:13px; line-height:1.55; word-break:break-word; white-space:pre-wrap; }
.chat-message--mine .chat-message__content p { border-radius:13px 13px 3px 13px; background:#dff5e5; }
.chat-message--system .chat-message__content p { border-radius:999px; background:rgba(60,60,67,.08); box-shadow:none; color:rgba(28,28,30,.55); font-size:11px; }
.chat-message__image { display:block; max-width:240px; max-height:200px; border-radius:10px; object-fit:cover; }
.chat-panel__composer { flex-shrink:0; padding:11px 14px; border-top:1px solid rgba(60,60,67,.1); background:#fff; }
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
.detail-panel__tags p { color:rgba(28,28,30,.58); font-size:12px; line-height:1.55; }
.detail-panel__tag-list { display:flex; min-height:24px; flex-wrap:wrap; gap:6px; margin-top:12px; }
.detail-panel__tag { display:inline-flex; align-items:center; gap:4px; max-width:100%; padding:4px 7px; border-radius:999px; color:#075fae; background:rgba(10,132,255,.12); font-size:11px; }
.detail-panel__tag button { width:15px; height:15px; padding:0; border:0; border-radius:50%; color:#075fae; background:rgba(10,132,255,.16); cursor:pointer; font-size:13px; line-height:1; }
.detail-panel__tag button:disabled { cursor:not-allowed; opacity:.5; }
.detail-panel__tag-empty { color:rgba(28,28,30,.43); font-size:12px; }
.detail-panel__tag-form { display:flex; gap:6px; margin-top:12px; }
.detail-panel__tag-form input { min-width:0; flex:1; height:32px; box-sizing:border-box; border:1px solid rgba(60,60,67,.16); border-radius:8px; padding:0 8px; outline:none; font:inherit; font-size:12px; }
.detail-panel__tag-form input:focus { border-color:rgba(10,132,255,.65); }
.detail-panel__tag-form button { flex:0 0 auto; border:0; border-radius:8px; padding:0 9px; color:#fff; background:#0a84ff; cursor:pointer; font:inherit; font-size:12px; }
.detail-panel__tag-form button:disabled { cursor:not-allowed; opacity:.45; }
.detail-panel__takeover p { color:rgba(28,28,30,.58); font-size:12px; line-height:1.6; }
.detail-panel__duration { display:flex; align-items:center; justify-content:space-between; gap:8px; margin-top:15px; font-size:12px; }
.detail-panel__duration select { height:32px; font-size:12px; }
.detail-panel__takeover-button { width:100%; height:36px; margin-top:14px; border:0; border-radius:9px; color:#fff; background:#ff9500; cursor:pointer; font:inherit; font-size:13px; }
.detail-panel__takeover-button--release { background:#0a84ff; }
.detail-panel__takeover-button:disabled { opacity:.55; cursor:not-allowed; }
@media (max-width: 1420px) { .customer-service__workspace { grid-template-columns:196px 280px minmax(360px, 1fr); } .detail-panel { display:none; } }
@media (max-width: 1080px) { .customer-service__select { display:block; } .customer-service__workspace { grid-template-columns:250px minmax(360px, 1fr); } .account-panel { display:none; } }
@media (max-width: 720px) { .customer-service { height:auto; min-height:calc(100vh - 54px); padding:14px; } .customer-service__header { align-items:flex-start; flex-direction:column; } .customer-service__toolbar { width:100%; } .customer-service__select { flex:1; min-width:0; } .customer-service__workspace { min-height:720px; grid-template-columns:1fr; grid-template-rows:250px minmax(470px, 1fr); } .session-panel { border-right:0; border-bottom:1px solid rgba(60,60,67,.1); } .session-panel__list { display:flex; overflow-x:auto; overflow-y:hidden; } .session-item { min-width:230px; border-top:0; border-right:1px solid rgba(60,60,67,.08); } .chat-panel__messages { padding:16px 12px; } .chat-message__content { max-width:82%; } }
</style>

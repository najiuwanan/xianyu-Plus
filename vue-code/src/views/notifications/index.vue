<template>
  <div class="notifications-container">
    <div class="header">
      <h1 class="title">通知渠道</h1>
      <p class="subtitle">管理系统通知渠道，支持邮件、钉钉、飞书、Bark、Webhook、PushPlus 等多种方式</p>
    </div>

    <div class="section-title">
      <svg style="width:18px;height:18px;margin-right:8px;" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"></circle><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path></svg>
      选择通知方式
    </div>
    <p class="section-desc">点击下方按钮选择通知渠道类型新增，同一类型可创建多个</p>

    <div class="channel-grid">
      <div v-for="type in channelTypes" :key="type.id" class="channel-card">
        <div class="channel-icon">{{ type.icon }}</div>
        <div class="channel-name">{{ type.name }}</div>
        <div class="channel-desc">{{ type.desc }}</div>
        <button class="add-btn" @click="type.id === 'email' ? openEmailConfigModal() : openConfigModal(type)">
          {{ type.id === 'email' && emailConfigured ? '已配置 · 管理' : '+ 配置' }}
        </button>
      </div>
    </div>

    <div class="section-title" style="margin-top: 40px;" v-if="configuredChannels.length > 0">
      已配置的渠道
    </div>
    <div class="configured-list" v-if="configuredChannels.length > 0">
      <div v-for="ch in configuredChannels" :key="ch.id" class="configured-card">
        <div class="conf-info">
          <div class="conf-name">{{ ch.name }}</div>
          <div class="conf-type">{{ getChannelTypeName(ch.type) }}</div>
        </div>
        <div class="conf-actions">
          <label class="switch">
            <input type="checkbox" :checked="ch.status === 1" @change="toggleStatus(ch)" />
            <span class="slider"></span>
          </label>
          <button class="test-btn" @click="testChannel(ch)" :disabled="testingId === ch.id">
            {{ testingId === ch.id ? '测试中...' : '测试' }}
          </button>
          <button class="edit-btn" @click="editChannel(ch)">编辑</button>
          <button class="delete-btn" @click="deleteChannel(ch.id!)">删除</button>
        </div>
      </div>
    </div>

    <!-- 配置弹窗 -->
    <div v-if="showModal" class="modal-overlay" @click="showModal = false">
      <div class="modal-content" @click.stop>
        <h2>{{ editingChannel.id ? '编辑' : '新增' }} {{ currentType?.name }}</h2>
        <div class="form-group">
          <label>通知名称</label>
          <input v-model="editingChannel.name" type="text" placeholder="给这个通知渠道起个名字" />
        </div>
        
        <!-- 钉钉/飞书/Webhook -->
        <div class="form-group" v-if="['dingtalk', 'feishu', 'webhook'].includes(editingChannel.type)">
          <label>Webhook URL</label>
          <input v-model="formConfig.webhook" type="text" placeholder="https://..." />
        </div>

        <template v-if="['dingtalk', 'feishu'].includes(editingChannel.type)">
          <div class="form-group">
            <label>签名密钥（可选）</label>
            <input v-model="formConfig.secret" type="password" autocomplete="new-password" placeholder="机器人开启“加签/签名校验”时填写" />
            <p class="form-hint">未开启签名校验可留空。密钥只保存到本项目的通知渠道配置中。</p>
          </div>
          <div class="form-group">
            <label>安全关键词（可选）</label>
            <input v-model="formConfig.keyword" type="text" placeholder="机器人开启关键词校验时填写，例如 XianYuPlus" />
            <p class="form-hint">填写后会自动附加到钉钉或飞书通知正文开头。</p>
          </div>
        </template>

        <!-- Bark -->
        <template v-if="editingChannel.type === 'bark'">
          <div class="form-group">
            <label>Server URL (可选)</label>
            <input v-model="formConfig.server" type="text" placeholder="https://api.day.app" />
          </div>
          <div class="form-group">
            <label>Device Key</label>
            <input v-model="formConfig.key" type="text" placeholder="填入 Bark App 提供的 Key" />
          </div>
        </template>

        <!-- PushPlus -->
        <template v-if="editingChannel.type === 'pushplus'">
          <div class="form-group">
            <label>Token</label>
            <input v-model="formConfig.token" type="text" placeholder="填入 PushPlus 的 Token" />
          </div>
        </template>

        <div class="form-divider">接收哪些通知？</div>
        <div class="checkbox-group">
          <div class="notify-item">
            <label class="checkbox-label">
              <input type="checkbox" v-model="formConfig.notifyAutoDelivery" /> 新订单通知（每笔仅一次）
            </label>
            <div class="template-config" v-if="formConfig.notifyAutoDelivery">
              <div class="template-config__header">
                <label>自定义正文模板</label>
                <button type="button" @click="restoreTemplate('AUTO_DELIVERY')">恢复示例</button>
              </div>
              <p class="template-variables">可用变量：{accountNote}、{accountId}、{orderId}、{goodsName}、{buyerName}</p>
              <textarea v-model="formConfig.templates.AUTO_DELIVERY.content" rows="5"></textarea>
            </div>
          </div>
          
          <div class="notify-item">
            <label class="checkbox-label">
              <input type="checkbox" v-model="formConfig.notifyAccountOffline" /> 账号异常/掉线通知
            </label>
            <div class="template-config" v-if="formConfig.notifyAccountOffline">
              <div class="template-config__header">
                <label>自定义正文模板</label>
                <button type="button" @click="restoreTemplate('ACCOUNT_OFFLINE')">恢复示例</button>
              </div>
              <p class="template-variables">可用变量：{reason}、{accountId}、{accountNote}</p>
              <textarea v-model="formConfig.templates.ACCOUNT_OFFLINE.content" rows="4"></textarea>
            </div>
          </div>
          
          <div class="notify-item">
            <label class="checkbox-label">
              <input type="checkbox" v-model="formConfig.notifyNewMessage" /> 收到新消息需人工介入
            </label>
            <div class="template-config" v-if="formConfig.notifyNewMessage">
              <div class="template-config__header">
                <label>自定义正文模板</label>
                <button type="button" @click="restoreTemplate('NEW_MESSAGE')">恢复示例</button>
              </div>
              <p class="template-variables">可用变量：{goodsName}、{buyerName}、{msgContent}、{reason}</p>
              <textarea v-model="formConfig.templates.NEW_MESSAGE.content" rows="5"></textarea>
            </div>
          </div>

          <div class="notify-item">
            <label class="checkbox-label">
              <input type="checkbox" v-model="formConfig.notifyAutomationException" /> 自动化失败/人工核对提醒
            </label>
            <div class="template-config" v-if="formConfig.notifyAutomationException">
              <div class="template-config__header">
                <label>自定义正文模板</label>
                <button type="button" @click="restoreTemplate('AUTOMATION_EXCEPTION')">恢复示例</button>
              </div>
              <p class="template-variables">可用变量：{action}、{reason}、{accountId}、{accountNote}、{orderId}、{goodsName}、{buyerName}</p>
              <textarea v-model="formConfig.templates.AUTOMATION_EXCEPTION.content" rows="6"></textarea>
              <p class="template-hint">开启后，自动发货、评价、小红花和商品擦亮出现异常时会推送；同一账号同一类异常 5 分钟内会合并一次，避免消息轰炸。</p>
            </div>
          </div>
        </div>

        <div class="modal-actions">
          <button class="btn-cancel" @click="showModal = false">取消</button>
          <button class="btn-save" @click="saveChannel" :disabled="saving">
            {{ saving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 邮件通知使用既有的 SMTP 配置，避免影响现有的业务邮件发送逻辑。 -->
    <div v-if="showEmailModal" class="modal-overlay" @click="showEmailModal = false">
      <div class="modal-content email-modal" @click.stop>
        <div class="email-modal__header">
          <div>
            <h2>邮件通知</h2>
            <p>配置 SMTP 后，可接收系统重要提醒。</p>
          </div>
          <span v-if="emailConfigured" class="email-status">已配置</span>
        </div>

        <div class="form-group">
          <label>SMTP 服务器</label>
          <input v-model="emailSmtpHost" type="text" placeholder="例如：smtp.qq.com" :disabled="emailSaving" />
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>SMTP 端口</label>
            <input v-model="emailSmtpPort" type="text" placeholder="465" :disabled="emailSaving" />
          </div>
          <div class="form-group form-group--switch">
            <label>启用 SSL</label>
            <label class="switch">
              <input v-model="emailSmtpSsl" type="checkbox" :disabled="emailSaving" />
              <span class="slider"></span>
            </label>
          </div>
        </div>
        <div class="form-group">
          <label>用户名</label>
          <input v-model="emailSmtpUsername" type="text" placeholder="发件邮箱账号" :disabled="emailSaving" />
        </div>
        <div class="form-group">
          <label>密码 / 授权码</label>
          <div class="password-input">
            <input v-model="emailSmtpPassword" :type="showEmailPassword ? 'text' : 'password'" placeholder="邮箱密码或 SMTP 授权码" :disabled="emailSaving" />
            <button type="button" @click="showEmailPassword = !showEmailPassword">{{ showEmailPassword ? '隐藏' : '显示' }}</button>
          </div>
          <p class="form-hint">QQ 邮箱请填写 SMTP 授权码，而不是登录密码。</p>
        </div>
        <div class="form-group">
          <label>收件人邮箱</label>
          <input v-model="emailSmtpFrom" type="text" placeholder="接收系统通知的邮箱地址" :disabled="emailSaving" />
        </div>

        <div class="form-divider">提醒事件</div>
        <p class="form-hint email-event-hint">卡券库存预警、缺货和自动发货失败等邮件会继续按原有规则发送。</p>
        <div class="email-event-list">
          <div class="email-event">
            <span>
              <strong>消息监听掉线提醒</strong>
              <small>WebSocket 多次重连失败时发送邮件</small>
            </span>
            <label class="switch">
              <input v-model="wsDisconnectNotifyEnabled" type="checkbox" :disabled="emailSaving" />
              <span class="slider"></span>
            </label>
          </div>
          <div class="email-event">
            <span>
              <strong>Cookie 过期 / 风控验证提醒</strong>
              <small>Cookie 无法续期或需要人工验证时发送邮件</small>
            </span>
            <label class="switch">
              <input v-model="cookieExpireNotifyEnabled" type="checkbox" :disabled="emailSaving" />
              <span class="slider"></span>
            </label>
          </div>
        </div>

        <div class="modal-actions">
          <button class="btn-test" type="button" @click="handleTestEmail" :disabled="!emailConfigured || emailTesting || emailSaving" title="请先保存完整的邮件配置">
            {{ emailTesting ? '发送中...' : '发送测试邮件' }}
          </button>
          <button class="btn-cancel" @click="showEmailModal = false">取消</button>
          <button class="btn-save" @click="saveEmailConfig" :disabled="emailSaving">
            {{ emailSaving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getNotificationChannels, saveNotificationChannel, deleteNotificationChannel, testNotificationChannel, type NotificationChannel } from '@/api/notification'
import { getSetting, saveSetting, testEmail } from '@/api/setting'
import { toast } from '@/utils/toast'

const channelTypes = [
  { id: 'email', name: '邮件通知', desc: 'SMTP 邮件系统通知', icon: '✉️' },
  { id: 'dingtalk', name: '钉钉通知', desc: '钉钉机器人消息', icon: '🔔' },
  { id: 'feishu', name: '飞书通知', desc: '飞书机器人消息', icon: '✈️' },
  { id: 'bark', name: 'Bark通知', desc: 'iOS推送通知', icon: '📱' },
  { id: 'webhook', name: 'Webhook', desc: '自定义HTTP请求', icon: '🔗' },
  { id: 'pushplus', name: 'PushPlus', desc: '微信公众号推送', icon: '💬' }
]

const configuredChannels = ref<NotificationChannel[]>([])
const showModal = ref(false)
const currentType = ref<typeof channelTypes[0] | null>(null)
const editingChannel = ref<NotificationChannel>({ type: '', name: '', config: '{}', status: 1 })
const formConfig = ref<any>({})
const saving = ref(false)
const testingId = ref<number | null>(null)
const showEmailModal = ref(false)
const emailSmtpHost = ref('')
const emailSmtpPort = ref('465')
const emailSmtpUsername = ref('')
const emailSmtpPassword = ref('')
const emailSmtpFrom = ref('')
const emailSmtpSsl = ref(true)
const wsDisconnectNotifyEnabled = ref(false)
const cookieExpireNotifyEnabled = ref(false)
const emailConfigured = ref(false)
const emailSaving = ref(false)
const emailTesting = ref(false)
const showEmailPassword = ref(false)

const EMAIL_SMTP_HOST_KEY = 'email_smtp_host'
const EMAIL_SMTP_PORT_KEY = 'email_smtp_port'
const EMAIL_SMTP_USERNAME_KEY = 'email_smtp_username'
const EMAIL_SMTP_PASSWORD_KEY = 'email_smtp_password'
const EMAIL_SMTP_FROM_KEY = 'email_smtp_from'
const EMAIL_SMTP_SSL_KEY = 'email_smtp_ssl'
const EMAIL_WS_DISCONNECT_NOTIFY_KEY = 'email_notify_ws_disconnect_enabled'
const EMAIL_COOKIE_EXPIRE_NOTIFY_KEY = 'email_notify_cookie_expire_enabled'

const templateExamples = {
  AUTO_DELIVERY: '账号：{accountNote}（ID：{accountId}）\n订单号：{orderId}\n商品：{goodsName}\n买家：{buyerName}',
  ACCOUNT_OFFLINE: '账号：{accountNote}（ID：{accountId}）\n原因：{reason}',
  NEW_MESSAGE: '商品：{goodsName}\n买家：{buyerName}\n买家消息：\n{msgContent}\n原因：{reason}',
  AUTOMATION_EXCEPTION: '类型：{action}\n账号：{accountNote}（ID：{accountId}）\n订单号：{orderId}\n商品：{goodsName}\n买家：{buyerName}\n原因：{reason}'
} as const

type NotificationEventType = keyof typeof templateExamples

const createDefaultFormConfig = () => ({
  notifyAutoDelivery: true,
  notifyAccountOffline: true,
  notifyNewMessage: true,
  // 新增异常提醒默认关闭，避免已有渠道在升级后未经确认就产生推送。
  notifyAutomationException: false,
  templates: {
    AUTO_DELIVERY: { content: templateExamples.AUTO_DELIVERY },
    ACCOUNT_OFFLINE: { content: templateExamples.ACCOUNT_OFFLINE },
    NEW_MESSAGE: { content: templateExamples.NEW_MESSAGE },
    AUTOMATION_EXCEPTION: { content: templateExamples.AUTOMATION_EXCEPTION }
  }
})

const restoreTemplate = (eventType: NotificationEventType) => {
  formConfig.value.templates[eventType].content = templateExamples[eventType]
}

const normalizeFormConfig = (config: unknown) => {
  const defaults = createDefaultFormConfig()
  const source = config && typeof config === 'object' ? config as Record<string, any> : {}
  const templates = source.templates && typeof source.templates === 'object' ? source.templates : {}
  const normalized: Record<string, any> = {
    ...defaults,
    ...source,
    templates: {
      AUTO_DELIVERY: { ...defaults.templates.AUTO_DELIVERY, ...templates.AUTO_DELIVERY },
      ACCOUNT_OFFLINE: { ...defaults.templates.ACCOUNT_OFFLINE, ...templates.ACCOUNT_OFFLINE },
      NEW_MESSAGE: { ...defaults.templates.NEW_MESSAGE, ...templates.NEW_MESSAGE },
      AUTOMATION_EXCEPTION: { ...defaults.templates.AUTOMATION_EXCEPTION, ...templates.AUTOMATION_EXCEPTION }
    }
  }
  ;(Object.keys(templateExamples) as NotificationEventType[]).forEach((eventType) => {
    if (typeof normalized.templates[eventType].content !== 'string' || !normalized.templates[eventType].content.trim()) {
      normalized.templates[eventType].content = templateExamples[eventType]
    }
  })
  if (!normalized.webhook && normalized.url) {
    normalized.webhook = normalized.url
  }
  return normalized
}

const loadChannels = async () => {
  try {
    const res = await getNotificationChannels()
    if (res.code === 200 && res.data) {
      configuredChannels.value = res.data
    }
  } catch (e: any) {
    toast.error('加载通知渠道失败: ' + e.message)
  }
}

const loadEmailConfig = async () => {
  try {
    const [hostRes, portRes, userRes, passRes, fromRes, sslRes, wsDisconnectRes, cookieExpireRes] = await Promise.all([
      getSetting({ settingKey: EMAIL_SMTP_HOST_KEY }),
      getSetting({ settingKey: EMAIL_SMTP_PORT_KEY }),
      getSetting({ settingKey: EMAIL_SMTP_USERNAME_KEY }),
      getSetting({ settingKey: EMAIL_SMTP_PASSWORD_KEY }),
      getSetting({ settingKey: EMAIL_SMTP_FROM_KEY }),
      getSetting({ settingKey: EMAIL_SMTP_SSL_KEY }),
      getSetting({ settingKey: EMAIL_WS_DISCONNECT_NOTIFY_KEY }),
      getSetting({ settingKey: EMAIL_COOKIE_EXPIRE_NOTIFY_KEY })
    ])

    emailSmtpHost.value = hostRes.code === 200 ? hostRes.data?.settingValue || '' : ''
    emailSmtpPort.value = portRes.code === 200 ? portRes.data?.settingValue || '465' : '465'
    emailSmtpUsername.value = userRes.code === 200 ? userRes.data?.settingValue || '' : ''
    emailSmtpPassword.value = passRes.code === 200 ? passRes.data?.settingValue || '' : ''
    emailSmtpFrom.value = fromRes.code === 200 ? fromRes.data?.settingValue || '' : ''
    emailSmtpSsl.value = sslRes.code !== 200 || !sslRes.data?.settingValue || ['1', 'true'].includes(sslRes.data.settingValue)
    wsDisconnectNotifyEnabled.value = wsDisconnectRes.code === 200 && ['1', 'true'].includes(wsDisconnectRes.data?.settingValue || '')
    cookieExpireNotifyEnabled.value = cookieExpireRes.code === 200 && ['1', 'true'].includes(cookieExpireRes.data?.settingValue || '')
    emailConfigured.value = Boolean(emailSmtpHost.value && emailSmtpPort.value && emailSmtpUsername.value && emailSmtpPassword.value && emailSmtpFrom.value)
  } catch (error) {
    console.error('加载邮件通知配置失败', error)
    toast.error('加载邮件通知配置失败')
  }
}

const openEmailConfigModal = async () => {
  await loadEmailConfig()
  showEmailModal.value = true
}

const saveEmailConfig = async () => {
  if (!emailSmtpHost.value.trim() || !emailSmtpPort.value.trim() || !emailSmtpUsername.value.trim() || !emailSmtpPassword.value.trim() || !emailSmtpFrom.value.trim()) {
    toast.warning('请完整填写 SMTP 配置和收件人邮箱')
    return
  }

  emailSaving.value = true
  try {
    const results = await Promise.all([
      saveSetting({ settingKey: EMAIL_SMTP_HOST_KEY, settingValue: emailSmtpHost.value.trim(), settingDesc: 'SMTP服务器地址' }),
      saveSetting({ settingKey: EMAIL_SMTP_PORT_KEY, settingValue: emailSmtpPort.value.trim(), settingDesc: 'SMTP服务器端口' }),
      saveSetting({ settingKey: EMAIL_SMTP_USERNAME_KEY, settingValue: emailSmtpUsername.value.trim(), settingDesc: 'SMTP登录用户名' }),
      saveSetting({ settingKey: EMAIL_SMTP_PASSWORD_KEY, settingValue: emailSmtpPassword.value.trim(), settingDesc: 'SMTP登录密码/授权码' }),
      saveSetting({ settingKey: EMAIL_SMTP_FROM_KEY, settingValue: emailSmtpFrom.value.trim(), settingDesc: '接收系统通知的收件人邮箱地址' }),
      saveSetting({ settingKey: EMAIL_SMTP_SSL_KEY, settingValue: emailSmtpSsl.value ? '1' : '0', settingDesc: '是否启用SSL（1启用，0关闭）' }),
      saveSetting({ settingKey: EMAIL_WS_DISCONNECT_NOTIFY_KEY, settingValue: wsDisconnectNotifyEnabled.value ? '1' : '0', settingDesc: 'WebSocket断开且无法重连时邮件通知开关' }),
      saveSetting({ settingKey: EMAIL_COOKIE_EXPIRE_NOTIFY_KEY, settingValue: cookieExpireNotifyEnabled.value ? '1' : '0', settingDesc: 'Cookie过期或风控验证时邮件通知开关' })
    ])
    if (results.every(result => result.code === 200)) {
      emailConfigured.value = true
      toast.success('邮件通知配置已保存')
      showEmailModal.value = false
    } else {
      toast.error('部分邮件通知配置保存失败，请重试')
    }
  } catch (error) {
    console.error('保存邮件通知配置失败', error)
    toast.error('保存邮件通知配置失败')
  } finally {
    emailSaving.value = false
  }
}

const handleTestEmail = async () => {
  emailTesting.value = true
  try {
    const result = await testEmail()
    if (result.code === 200) {
      toast.success('测试邮件已发送，请检查收件箱')
    } else {
      toast.error(result.msg || '测试邮件发送失败')
    }
  } catch (error: any) {
    console.error('测试邮件发送失败', error)
    toast.error(error.message || '测试邮件发送失败')
  } finally {
    emailTesting.value = false
  }
}

const getChannelTypeName = (typeId: string) => {
  return channelTypes.find(t => t.id === typeId)?.name || typeId
}

const openConfigModal = (type: typeof channelTypes[0]) => {
  currentType.value = type
  editingChannel.value = { type: type.id, name: `${type.name} 1`, config: '{}', status: 1 }
  formConfig.value = createDefaultFormConfig()
  showModal.value = true
}

const editChannel = (ch: NotificationChannel) => {
  currentType.value = channelTypes.find(t => t.id === ch.type) || null
  editingChannel.value = { ...ch }
  try {
    formConfig.value = normalizeFormConfig(JSON.parse(ch.config || '{}'))
  } catch {
    formConfig.value = createDefaultFormConfig()
  }
  showModal.value = true
}

const saveChannel = async () => {
  if (!editingChannel.value.name.trim()) {
    toast.warning('请输入通知名称')
    return
  }

  // Handle specific URL mapping
  if (['dingtalk', 'feishu', 'webhook'].includes(editingChannel.value.type)) {
    if (!formConfig.value.webhook && !formConfig.value.url) {
      toast.warning('请输入 Webhook URL')
      return
    }
    // unify variable name for backend
    if (editingChannel.value.type === 'webhook' && formConfig.value.webhook) {
        formConfig.value.url = formConfig.value.webhook
    } else if (formConfig.value.url) {
        formConfig.value.webhook = formConfig.value.url
    }
  }

  saving.value = true
  editingChannel.value.config = JSON.stringify(formConfig.value)
  
  try {
    const res = await saveNotificationChannel(editingChannel.value)
    if (res.code === 200) {
      toast.success('保存成功')
      showModal.value = false
      loadChannels()
    } else {
      toast.error(res.msg || '保存失败')
    }
  } catch (e: any) {
    toast.error('保存异常: ' + e.message)
  } finally {
    saving.value = false
  }
}

const toggleStatus = async (ch: NotificationChannel) => {
  const newStatus = ch.status === 1 ? 0 : 1
  try {
    const res = await saveNotificationChannel({ ...ch, status: newStatus })
    if (res.code === 200) {
      ch.status = newStatus
      toast.success(newStatus === 1 ? '已开启' : '已关闭')
    }
  } catch (e: any) {
    toast.error('切换状态失败: ' + e.message)
  }
}

const deleteChannel = async (id: number) => {
  if (!confirm('确定要删除该通知渠道吗？')) return
  try {
    const res = await deleteNotificationChannel(id)
    if (res.code === 200) {
      toast.success('删除成功')
      loadChannels()
    }
  } catch (e: any) {
    toast.error('删除失败: ' + e.message)
  }
}

const testChannel = async (ch: NotificationChannel) => {
  testingId.value = ch.id || -1
  try {
    const res = await testNotificationChannel(ch)
    if (res.code === 200) {
      toast.success('测试消息已发送，请检查接收端')
    } else {
      toast.error(res.msg || '测试失败')
    }
  } catch (e: any) {
    toast.error('测试异常: ' + e.message)
  } finally {
    testingId.value = null
  }
}

onMounted(() => {
  loadChannels()
  loadEmailConfig()
})
</script>

<style scoped>
.notifications-container {
  padding: 30px;
  max-width: 1200px;
  margin: 0 auto;
}
.header {
  margin-bottom: 30px;
}
.title {
  font-size: 24px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}
.subtitle {
  color: #6b7280;
  font-size: 14px;
}
.section-title {
  display: flex;
  align-items: center;
  font-size: 18px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}
.section-desc {
  color: #9ca3af;
  font-size: 13px;
  margin-bottom: 24px;
}
.channel-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px;
}
.channel-card {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  transition: all 0.2s;
}
.channel-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 6px -1px rgba(59, 130, 246, 0.1);
}
.channel-icon {
  font-size: 32px;
  margin-bottom: 12px;
}
.channel-name {
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}
.channel-desc {
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 16px;
}
.add-btn {
  background: transparent;
  color: #10b981;
  border: 1px solid #10b981;
  padding: 6px 16px;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.add-btn:hover {
  background: #10b981;
  color: white;
}

/* Configured List */
.configured-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}
.configured-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  padding: 16px 24px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
}
.conf-name {
  font-weight: 600;
  color: #374151;
  font-size: 15px;
}
.conf-type {
  color: #6b7280;
  font-size: 13px;
  margin-top: 4px;
}
.conf-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}
.test-btn, .edit-btn, .delete-btn {
  background: none;
  border: none;
  font-size: 13px;
  cursor: pointer;
}
.test-btn { color: #3b82f6; }
.edit-btn { color: #6b7280; }
.delete-btn { color: #ef4444; }

/* Switch Toggle */
.switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
}
.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}
.slider {
  position: absolute;
  cursor: pointer;
  top: 0; left: 0; right: 0; bottom: 0;
  background-color: #ccc;
  transition: .4s;
  border-radius: 24px;
}
.slider:before {
  position: absolute;
  content: "";
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  transition: .4s;
  border-radius: 50%;
}
input:checked + .slider {
  background-color: #10b981;
}
input:checked + .slider:before {
  transform: translateX(20px);
}

/* Modal */
.modal-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 2000;
}
.modal-content {
  background: white;
  padding: 30px;
  border-radius: 8px;
  width: 760px;
  max-width: 90%;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
}
.modal-content h2 {
  margin-top: 0;
  margin-bottom: 20px;
  font-size: 18px;
}
.form-group {
  margin-bottom: 16px;
}
.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: #374151;
}
.form-group input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  box-sizing: border-box;
}
.form-hint {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}
.modal-content:not(.email-modal) .modal-actions {
  position: sticky;
  bottom: -30px;
  z-index: 1;
  margin: 20px -30px -30px;
  padding: 16px 30px;
  border-top: 1px solid #e5e7eb;
  background: white;
}
.btn-cancel, .btn-save {
  padding: 8px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}
.btn-cancel {
  background: white;
  border: 1px solid #d1d5db;
  color: #374151;
}
.btn-save {
  background: #3b82f6;
  border: none;
  color: white;
}
.btn-save:disabled {
  opacity: 0.7;
}

/* Checkbox group */
.form-divider {
  margin-top: 20px;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  border-top: 1px solid #e5e7eb;
  padding-top: 16px;
}
.checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-color);
  cursor: pointer;
}
.checkbox-label input[type="checkbox"] {
  cursor: pointer;
}
.notify-item {
  margin-bottom: 12px;
}
.template-config {
  margin-top: 8px;
  margin-left: 24px;
  background-color: var(--bg-color-light);
  padding: 12px;
  border-radius: 6px;
  border: 1px dashed var(--border-color);
}
.template-config label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
.template-config__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.template-config__header label {
  margin-bottom: 0;
}
.template-config__header button {
  flex: 0 0 auto;
  padding: 3px 8px;
  border: 1px solid #93c5fd;
  border-radius: 4px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  cursor: pointer;
}
.template-config__header button:hover {
  background: #dbeafe;
}
.template-variables {
  margin: 8px 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
}
.template-hint { margin: 8px 0 0; color: #98a2b3; font-size: 12px; line-height: 1.5; }
.template-config textarea {
  width: 100%;
  padding: 8px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background-color: var(--bg-color);
  color: var(--text-color);
  font-family: inherit;
  font-size: 13px;
  resize: vertical;
}

.email-modal {
  width: 560px;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
}
.email-modal__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}
.email-modal__header h2 {
  margin-bottom: 6px;
}
.email-modal__header p {
  margin: 0 0 20px;
  color: #6b7280;
  font-size: 13px;
}
.email-status {
  flex: 0 0 auto;
  padding: 4px 9px;
  border-radius: 999px;
  background: #dcfce7;
  color: #16a34a;
  font-size: 12px;
  font-weight: 600;
}
.form-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px;
  gap: 16px;
}
.form-group--switch {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}
.form-group--switch .switch {
  margin-top: 2px;
}
.password-input {
  display: flex;
}
.password-input input {
  border-radius: 4px 0 0 4px;
}
.password-input button {
  flex: 0 0 auto;
  border: 1px solid #d1d5db;
  border-left: 0;
  border-radius: 0 4px 4px 0;
  padding: 0 12px;
  background: #f9fafb;
  color: #4b5563;
  cursor: pointer;
  font-size: 12px;
}
.email-event-hint {
  margin-top: -4px;
  margin-bottom: 10px;
}
.email-event-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.email-event {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
}
.email-event strong,
.email-event small {
  display: block;
}
.email-event strong {
  color: #374151;
  font-size: 13px;
}
.email-event small {
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
}
.btn-test {
  margin-right: auto;
  border: 1px solid #3b82f6;
  border-radius: 4px;
  padding: 8px 12px;
  background: white;
  color: #2563eb;
  cursor: pointer;
  font-size: 14px;
}
.btn-test:disabled {
  opacity: .6;
  cursor: not-allowed;
}
@media (max-width: 520px) {
  .modal-content {
    padding: 20px;
    max-width: calc(100% - 24px);
  }
  .modal-content:not(.email-modal) .modal-actions {
    bottom: -20px;
    margin: 20px -20px -20px;
    padding: 14px 20px;
  }
  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .form-group--switch {
    flex-direction: row;
    align-items: center;
    gap: 12px;
  }
  .form-group--switch .switch {
    margin-top: 0;
  }
  .email-event {
    align-items: flex-start;
  }
}
</style>

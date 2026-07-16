<template>
  <div class="notifications-container">
    <div class="header">
      <h1 class="title">通知渠道</h1>
      <p class="subtitle">管理消息通知渠道，支持钉钉、飞书、Bark、Webhook、PushPlus等多种方式</p>
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
        <button class="add-btn" @click="openConfigModal(type)">+ 配置</button>
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
              <input type="checkbox" v-model="formConfig.notifyAutoDelivery" /> 自动发货成功通知
            </label>
            <div class="template-config" v-if="formConfig.notifyAutoDelivery">
              <label>自定义正文模板 (可用变量: {orderId}, {goodsName}, {buyerName}, {content})</label>
              <textarea v-model="formConfig.templates.AUTO_DELIVERY.content" placeholder="不填则使用系统默认模板..." rows="3"></textarea>
            </div>
          </div>
          
          <div class="notify-item">
            <label class="checkbox-label">
              <input type="checkbox" v-model="formConfig.notifyAccountOffline" /> 账号异常/掉线通知
            </label>
            <div class="template-config" v-if="formConfig.notifyAccountOffline">
              <label>自定义正文模板 (可用变量: {reason}, {accountId}, {accountNote})</label>
              <textarea v-model="formConfig.templates.ACCOUNT_OFFLINE.content" placeholder="不填则使用系统默认模板..." rows="2"></textarea>
            </div>
          </div>
          
          <div class="notify-item">
            <label class="checkbox-label">
              <input type="checkbox" v-model="formConfig.notifyNewMessage" /> 收到新消息需人工介入
            </label>
            <div class="template-config" v-if="formConfig.notifyNewMessage">
              <label>自定义正文模板 (可用变量: {goodsName}, {buyerName}, {msgContent}, {reason})</label>
              <textarea v-model="formConfig.templates.NEW_MESSAGE.content" placeholder="不填则使用系统默认模板..." rows="3"></textarea>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getNotificationChannels, saveNotificationChannel, deleteNotificationChannel, testNotificationChannel, type NotificationChannel } from '@/api/notification'
import { toast } from '@/utils/toast'

const channelTypes = [
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

const createDefaultFormConfig = () => ({
  notifyAutoDelivery: true,
  notifyAccountOffline: true,
  notifyNewMessage: true,
  templates: {
    AUTO_DELIVERY: { content: '' },
    ACCOUNT_OFFLINE: { content: '' },
    NEW_MESSAGE: { content: '' }
  }
})

const normalizeFormConfig = (config: unknown) => {
  const defaults = createDefaultFormConfig()
  const source = config && typeof config === 'object' ? config as Record<string, any> : {}
  const templates = source.templates && typeof source.templates === 'object' ? source.templates : {}

  return {
    ...defaults,
    ...source,
    templates: {
      AUTO_DELIVERY: { ...defaults.templates.AUTO_DELIVERY, ...templates.AUTO_DELIVERY },
      ACCOUNT_OFFLINE: { ...defaults.templates.ACCOUNT_OFFLINE, ...templates.ACCOUNT_OFFLINE },
      NEW_MESSAGE: { ...defaults.templates.NEW_MESSAGE, ...templates.NEW_MESSAGE }
    }
  }
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
  width: 400px;
  max-width: 90%;
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
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
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
</style>

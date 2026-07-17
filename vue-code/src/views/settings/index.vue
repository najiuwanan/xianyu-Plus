<script setup lang="ts">
import { ref, onMounted, markRaw } from 'vue'
import { fetchModels, testAi, testEmbedding } from '@/api/system'
import { getSetting, saveSetting } from '@/api/setting'
import { getAIStatus } from '@/api/ai'
import { getBackupModules, exportBackup, importBackup, getLogDates, downloadLog, type BackupModule } from '@/api/backup'
import { getLogRetention, saveLogRetention } from '@/api/runtime-log'
import { toast } from '@/utils/toast'
import { showConfirm } from '@/utils/confirm'
import IconRobot from '@/components/icons/IconRobot.vue'
import IconChat from '@/components/icons/IconChat.vue'
import IconBackup from '@/components/icons/IconBackup.vue'
import IconInfo from '@/components/icons/IconInfo.vue'
import IconLog from '@/components/icons/IconLog.vue'

// 当前选中的菜单
const activeMenu = ref('ai')

// 系统提示词
const SYS_PROMPT_KEY = 'sys_prompt'
const DEFAULT_SYS_PROMPT = '作为闲鱼虚拟商品店铺客服，结合商品和知识库信息简短、准确回复。信息不足时明确说明需要补充的内容，不编造卡密、库存、价格或售后承诺。'
const sysPromptValue = ref('')
const sysPromptSaving = ref(false)
const sysPromptLoaded = ref(false)

// 相似度阈值
const SIMILARITY_THRESHOLD_KEY = 'similarity_threshold'
const DEFAULT_SIMILARITY_THRESHOLD = 0.1
const similarityThreshold = ref(DEFAULT_SIMILARITY_THRESHOLD)
const similarityThresholdSaving = ref(false)

// AI API Key 配置
const AI_API_KEY_SETTING = 'ai_api_key'
const AI_BASE_URL_SETTING = 'ai_base_url'
const AI_MODEL_SETTING = 'ai_model'
const AI_PROVIDER_SETTING = 'ai_provider'
const DEFAULT_BASE_URL = 'https://dashscope.aliyuncs.com/compatible-mode'
const DEFAULT_MODEL = 'deepseek-v3'

type AiProvider = 'openai-compatible' | 'gemini' | 'dashscope-compatible' | 'deepseek'

const AI_PROVIDERS: Array<{
  value: AiProvider
  label: string
  baseUrl: string
  defaultModel: string
  description: string
}> = [
  {
    value: 'openai-compatible',
    label: 'OpenAI 兼容（自定义）',
    baseUrl: '',
    defaultModel: '',
    description: '适用于 OpenAI、OpenRouter、硅基流动、智谱等兼容 OpenAI 协议的服务。地址由你自行填写。'
  },
  {
    value: 'gemini',
    label: 'Google Gemini',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    defaultModel: 'gemini-3.5-flash',
    description: '使用 Gemini 官方 OpenAI 兼容接口，选择后会自动填入正确地址。'
  },
  {
    value: 'dashscope-compatible',
    label: '阿里云百炼（兼容模式）',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode',
    defaultModel: 'qwen-plus',
    description: '适用于通义千问、百炼平台中的 DeepSeek 等 OpenAI 兼容模型。'
  },
  {
    value: 'deepseek',
    label: 'DeepSeek 官方',
    baseUrl: 'https://api.deepseek.com',
    defaultModel: 'deepseek-v4-flash',
    description: '使用 DeepSeek 官方 API；不要与 Gemini 或百炼的地址混用。'
  }
]

const aiProvider = ref<AiProvider>('dashscope-compatible')

const aiApiKey = ref('')
const aiBaseUrl = ref(DEFAULT_BASE_URL)
const aiModel = ref(DEFAULT_MODEL)
const aiApiKeySaving = ref(false)
const showApiKey = ref(false)

const fetchingModels = ref(false)
const availableModels = ref<string[]>([])

// Embedding 模型配置（可选，默认共用 AI 配置）
const EMBEDDING_API_KEY_SETTING = 'ai_embedding_api_key'
const EMBEDDING_BASE_URL_SETTING = 'ai_embedding_base_url'
const EMBEDDING_MODEL_SETTING = 'ai_embedding_model'
const DEFAULT_EMBEDDING_MODEL = 'text-embedding-v3'

const embeddingApiKey = ref('')
const embeddingBaseUrl = ref('')
const embeddingModel = ref(DEFAULT_EMBEDDING_MODEL)
const embeddingSaving = ref(false)
const showEmbeddingApiKey = ref(false)
const showEmbeddingConfig = ref(false)

const fetchingEmbeddingModels = ref(false)
const availableEmbeddingModels = ref<string[]>([])

async function handleFetchModels(type: 'chat' | 'embedding') {
  const apiKey = type === 'chat' ? aiApiKey.value.trim() : embeddingApiKey.value.trim() || aiApiKey.value.trim()
  const baseUrl = type === 'chat' ? aiBaseUrl.value.trim() : embeddingBaseUrl.value.trim() || aiBaseUrl.value.trim()
  
  if (!apiKey) {
    toast.warning('请先输入 API Key')
    return
  }
  if (!baseUrl) {
    toast.warning('请先输入 Base URL')
    return
  }

  if (type === 'chat') {
    fetchingModels.value = true
  } else {
    fetchingEmbeddingModels.value = true
  }

  try {
    const res = await fetchModels({ apiKey, baseUrl })
    if (res.code === 200 && res.data && res.data.models) {
      if (type === 'chat') {
        availableModels.value = res.data.models
      } else {
        availableEmbeddingModels.value = res.data.models
      }
      toast.success('获取模型成功，请在下拉框中选择')
    } else {
      toast.error(res.msg || '获取模型失败')
    }
  } catch (e: any) {
    toast.error('请求失败: ' + (e.message || '未知错误'))
  } finally {
    if (type === 'chat') {
      fetchingModels.value = false
    } else {
      fetchingEmbeddingModels.value = false
    }
  }
}

const testingChat = ref(false)
const testingEmbedding = ref(false)
const showChatDropdown = ref(false)
const showEmbeddingDropdown = ref(false)

function getProvider(provider: AiProvider) {
  return AI_PROVIDERS.find(item => item.value === provider) ?? AI_PROVIDERS[0]!
}

function isAiProvider(value: string | undefined): value is AiProvider {
  return AI_PROVIDERS.some(item => item.value === value)
}

function inferProviderFromBaseUrl(baseUrl: string): AiProvider {
  const url = baseUrl.toLowerCase()
  if (url.includes('generativelanguage.googleapis.com')) return 'gemini'
  if (url.includes('dashscope.aliyuncs.com')) return 'dashscope-compatible'
  if (url.includes('api.deepseek.com')) return 'deepseek'
  return 'openai-compatible'
}

function handleProviderChange() {
  const provider = getProvider(aiProvider.value)
  availableModels.value = []
  showChatDropdown.value = false

  if (provider.baseUrl) {
    aiBaseUrl.value = provider.baseUrl
    aiModel.value = provider.defaultModel
  }
}

function hideChatDropdownDelay() {
  setTimeout(() => { showChatDropdown.value = false }, 200)
}
function hideEmbeddingDropdownDelay() {
  setTimeout(() => { showEmbeddingDropdown.value = false }, 200)
}
function selectChatModel(m: string) {
  aiModel.value = m
  showChatDropdown.value = false
}
function selectEmbeddingModel(m: string) {
  embeddingModel.value = m
  showEmbeddingDropdown.value = false
}

async function handleTestChat() {
  const apiKey = aiApiKey.value.trim()
  const baseUrl = aiBaseUrl.value.trim()
  const model = aiModel.value.trim()
  if (!apiKey || !baseUrl || !model) {
    toast.warning('请先填写完整 API Key、Base URL 和 模型名称')
    return
  }
  testingChat.value = true
  try {
    const res = await testAi({ apiKey, baseUrl, model })
    if (res.code === 200) {
      toast.success('🎉 对话模型测试成功！连接正常')
    } else {
      toast.error(res.msg || '连接失败')
    }
  } catch (e: any) {
    toast.error('请求异常: ' + (e.message || '未知错误'))
  } finally {
    testingChat.value = false
  }
}

async function handleTestEmbedding() {
  const apiKey = embeddingApiKey.value.trim() || aiApiKey.value.trim()
  const baseUrl = embeddingBaseUrl.value.trim() || aiBaseUrl.value.trim()
  const model = embeddingModel.value.trim()
  if (!apiKey || !baseUrl || !model) {
    toast.warning('请先填写完整 Embedding 配置')
    return
  }
  testingEmbedding.value = true
  try {
    const res = await testEmbedding({ apiKey, baseUrl, model })
    if (res.code === 200) {
      toast.success('🎉 Embedding 测试成功！连接正常')
    } else {
      toast.error(res.msg || '连接失败')
    }
  } catch (e: any) {
    toast.error('请求异常: ' + (e.message || '未知错误'))
  } finally {
    testingEmbedding.value = false
  }
}


// AI 状态
const aiStatus = ref({
  enabled: false,
  available: false,
  apiKeyConfigured: false,
  message: '',
  baseUrl: '',
  model: ''
})

// 菜单配置
const menuItems = [
  { key: 'ai', label: 'AI 服务配置', icon: markRaw(IconRobot) },
  { key: 'prompt', label: 'AI客服配置', icon: markRaw(IconChat) },
  { key: 'backup', label: '备份与恢复', icon: markRaw(IconBackup) },
  { key: 'logs', label: '日志清理', icon: markRaw(IconLog) },
  { key: 'about', label: '关于', icon: markRaw(IconInfo) }
]

const LOG_RETENTION_OPTIONS = [1, 3, 5, 7, 30]
const logRetentionDays = ref(7)
const logRetentionConfigured = ref(false)
const logRetentionLoaded = ref(false)
const logRetentionSaving = ref(false)

async function loadLogRetention() {
  if (logRetentionLoaded.value) return
  try {
    const res = await getLogRetention()
    if (res.code === 200 && res.data) {
      logRetentionDays.value = res.data.days || 7
      logRetentionConfigured.value = res.data.configured === true
      logRetentionLoaded.value = true
    }
  } catch (e) {
    console.error('获取日志保留设置失败:', e)
  }
}

async function handleSaveLogRetention() {
  logRetentionSaving.value = true
  try {
    const res = await saveLogRetention(logRetentionDays.value)
    if (res.code === 200) {
      logRetentionConfigured.value = true
      logRetentionLoaded.value = true
      const result = res.data
      toast.success(`日志保留设置已保存，已清理 ${result?.fileLogDirectoriesDeleted || 0} 个日志目录和 ${result?.operationLogsDeleted || 0} 条操作日志`)
    }
  } catch (e) {
    console.error('保存日志保留设置失败:', e)
  } finally {
    logRetentionSaving.value = false
  }
}

function handleSettingsMenuSelect(key: string) {
  activeMenu.value = key
  if (key === 'backup') handleBackupMenuEnter()
  if (key === 'logs') loadLogRetention()
}

onMounted(async () => {
  // 加载系统提示词配置
  try {
    const res = await getSetting({ settingKey: SYS_PROMPT_KEY })
    if (res.code === 200 && res.data) {
      sysPromptValue.value = res.data.settingValue || ''
      sysPromptLoaded.value = true
    }
  } catch (e) {
    console.error('获取系统提示词配置失败:', e)
  }

  // 加载相似度阈值配置
  try {
    const res = await getSetting({ settingKey: SIMILARITY_THRESHOLD_KEY })
    if (res.code === 200 && res.data && res.data.settingValue) {
      similarityThreshold.value = parseFloat(res.data.settingValue) || DEFAULT_SIMILARITY_THRESHOLD
    }
  } catch (e) {
    console.error('获取相似度阈值配置失败:', e)
  }

  // 加载 AI 配置
  await loadAIConfig()
  // 加载 Embedding 配置
  await loadEmbeddingConfig()
  // 加载 AI 状态
  await loadAIStatus()
})

async function loadAIConfig() {
  try {
    const [providerRes, apiKeyRes, baseUrlRes, modelRes] = await Promise.all([
      getSetting({ settingKey: AI_PROVIDER_SETTING }),
      getSetting({ settingKey: AI_API_KEY_SETTING }),
      getSetting({ settingKey: AI_BASE_URL_SETTING }),
      getSetting({ settingKey: AI_MODEL_SETTING })
    ])

    if (apiKeyRes.code === 200 && apiKeyRes.data) {
      aiApiKey.value = apiKeyRes.data.settingValue || ''
    }
    if (baseUrlRes.code === 200 && baseUrlRes.data && baseUrlRes.data.settingValue) {
      aiBaseUrl.value = baseUrlRes.data.settingValue
    }
    if (modelRes.code === 200 && modelRes.data && modelRes.data.settingValue) {
      aiModel.value = modelRes.data.settingValue
    }
    const savedProvider = providerRes.code === 200 ? providerRes.data?.settingValue : undefined
    aiProvider.value = isAiProvider(savedProvider)
      ? savedProvider
      : inferProviderFromBaseUrl(aiBaseUrl.value)

    // 兼容此前只填写 Gemini 域名的配置：旧地址会被请求成 /v1/models 并导致认证失败。
    if (!isAiProvider(savedProvider)
      && aiProvider.value === 'gemini'
      && aiBaseUrl.value.replace(/\/$/, '') === 'https://generativelanguage.googleapis.com') {
      const gemini = getProvider('gemini')
      aiBaseUrl.value = gemini.baseUrl
      if (aiModel.value === DEFAULT_MODEL) {
        aiModel.value = gemini.defaultModel
      }
    }
  } catch (e) {
    console.error('获取AI配置失败:', e)
  }
}

async function loadEmbeddingConfig() {
  try {
    const [apiKeyRes, baseUrlRes, modelRes] = await Promise.all([
      getSetting({ settingKey: EMBEDDING_API_KEY_SETTING }),
      getSetting({ settingKey: EMBEDDING_BASE_URL_SETTING }),
      getSetting({ settingKey: EMBEDDING_MODEL_SETTING })
    ])

    if (apiKeyRes.code === 200 && apiKeyRes.data) {
      embeddingApiKey.value = apiKeyRes.data.settingValue || ''
    }
    if (baseUrlRes.code === 200 && baseUrlRes.data && baseUrlRes.data.settingValue) {
      embeddingBaseUrl.value = baseUrlRes.data.settingValue
    }
    if (modelRes.code === 200 && modelRes.data && modelRes.data.settingValue) {
      embeddingModel.value = modelRes.data.settingValue
    }
  } catch (e) {
    console.error('获取Embedding配置失败:', e)
  }
}

async function loadAIStatus() {
  try {
    const res = await getAIStatus()
    const data = await res.json()
    if (data.code === 200 && data.data) {
      aiStatus.value = data.data
    }
  } catch (e) {
    console.error('获取AI状态失败:', e)
  }
}

async function handleSaveSysPrompt() {
  if (!sysPromptValue.value.trim()) {
    toast.warning('系统提示词不能为空')
    return
  }
  sysPromptSaving.value = true
  try {
    const res = await saveSetting({
      settingKey: SYS_PROMPT_KEY,
      settingValue: sysPromptValue.value,
      settingDesc: 'AI智能回复的系统提示词'
    })
    if (res.code === 200) {
      toast.success('系统提示词保存成功')
      sysPromptLoaded.value = true
    }
  } finally {
    sysPromptSaving.value = false
  }
}

function handleResetSysPrompt() {
  sysPromptValue.value = DEFAULT_SYS_PROMPT
}

async function handleSaveSimilarityThreshold() {
  if (similarityThreshold.value < 0 || similarityThreshold.value > 1) {
    toast.warning('相似度阈值必须在 0 到 1 之间')
    return
  }
  similarityThresholdSaving.value = true
  try {
    const res = await saveSetting({
      settingKey: SIMILARITY_THRESHOLD_KEY,
      settingValue: similarityThreshold.value.toString(),
      settingDesc: 'RAG向量搜索的相似度阈值（0-1之间，值越小匹配越宽松）'
    })
    if (res.code === 200) {
      toast.success('相似度阈值保存成功')
    }
  } catch (e) {
    console.error('保存相似度阈值失败:', e)
    toast.error('保存相似度阈值失败')
  } finally {
    similarityThresholdSaving.value = false
  }
}

function handleResetSimilarityThreshold() {
  similarityThreshold.value = DEFAULT_SIMILARITY_THRESHOLD
}

async function handleSaveAIConfig() {
  if (!aiApiKey.value.trim()) {
    toast.warning('API Key 不能为空')
    return
  }
  if (!aiBaseUrl.value.trim()) {
    toast.warning('API Base URL 不能为空')
    return
  }
  if (!aiModel.value.trim()) {
    toast.warning('模型名称不能为空')
    return
  }

  aiApiKeySaving.value = true
  try {
    // 保存服务商类型和三项 AI 配置
    const [providerRes, keyRes, urlRes, modelRes] = await Promise.all([
      saveSetting({
        settingKey: AI_PROVIDER_SETTING,
        settingValue: aiProvider.value,
        settingDesc: 'AI 服务商类型，用于自动应用兼容的 API 地址'
      }),
      saveSetting({
        settingKey: AI_API_KEY_SETTING,
        settingValue: aiApiKey.value.trim(),
        settingDesc: 'AI服务的API Key（配置后立即生效，无需重启）'
      }),
      saveSetting({
        settingKey: AI_BASE_URL_SETTING,
        settingValue: aiBaseUrl.value.trim(),
        settingDesc: 'AI服务的API Base URL'
      }),
      saveSetting({
        settingKey: AI_MODEL_SETTING,
        settingValue: aiModel.value.trim(),
        settingDesc: 'AI对话模型名称'
      })
    ])

    if (providerRes.code === 200 && keyRes.code === 200 && urlRes.code === 200 && modelRes.code === 200) {
      toast.success('AI 配置保存成功，已立即生效')
      // 刷新 AI 状态
      await loadAIStatus()
    }
  } catch (e) {
    console.error('保存AI配置失败:', e)
    toast.error('保存AI配置失败')
  } finally {
    aiApiKeySaving.value = false
  }
}

function handleResetAIConfig() {
  aiProvider.value = 'dashscope-compatible'
  aiApiKey.value = ''
  aiBaseUrl.value = DEFAULT_BASE_URL
  aiModel.value = DEFAULT_MODEL
  availableModels.value = []
  showChatDropdown.value = false
}

async function handleSaveEmbeddingConfig() {
  embeddingSaving.value = true
  try {
    // 保存三个配置（可以为空，空值表示使用 AI 对话配置）
    const [keyRes, urlRes, modelRes] = await Promise.all([
      saveSetting({
        settingKey: EMBEDDING_API_KEY_SETTING,
        settingValue: embeddingApiKey.value.trim(),
        settingDesc: 'Embedding模型API Key（留空则使用AI对话的API Key）'
      }),
      saveSetting({
        settingKey: EMBEDDING_BASE_URL_SETTING,
        settingValue: embeddingBaseUrl.value.trim(),
        settingDesc: 'Embedding模型API Base URL（留空则使用AI对话的Base URL）'
      }),
      saveSetting({
        settingKey: EMBEDDING_MODEL_SETTING,
        settingValue: embeddingModel.value.trim(),
        settingDesc: 'Embedding模型名称'
      })
    ])

    if (keyRes.code === 200 && urlRes.code === 200 && modelRes.code === 200) {
      toast.success('Embedding 配置保存成功，重启服务后生效')
    }
  } catch (e) {
    console.error('保存Embedding配置失败:', e)
    toast.error('保存Embedding配置失败')
  } finally {
    embeddingSaving.value = false
  }
}

function handleResetEmbeddingConfig() {
  embeddingApiKey.value = ''
  embeddingBaseUrl.value = ''
  embeddingModel.value = DEFAULT_EMBEDDING_MODEL
}

// 备份与恢复
const backupModules = ref<BackupModule[]>([])
const backupSelectedModules = ref<string[]>([])
const backupLoaded = ref(false)
const backupExporting = ref(false)
const backupImporting = ref(false)
const backupExportProgress = ref(0)
const backupImportProgress = ref(0)

const logDates = ref<string[]>([])
const logSelectedDate = ref('')
const logDownloading = ref(false)
const logDatesLoaded = ref(false)

async function loadBackupModules() {
  if (backupLoaded.value) return
  try {
    const res = await getBackupModules()
    if (res.code === 200 && res.data) {
      backupModules.value = res.data
      backupSelectedModules.value = res.data.map((m: BackupModule) => m.moduleKey)
      backupLoaded.value = true
    }
  } catch (e) {
    console.error('获取备份模块列表失败:', e)
  }
}

async function loadLogDates() {
  if (logDatesLoaded.value) return
  try {
    const res = await getLogDates()
    if (res.code === 200 && res.data) {
      logDates.value = res.data
      const today = new Date().toISOString().slice(0, 10)
      logSelectedDate.value = res.data.includes(today) ? today : (res.data.length > 0 ? res.data[res.data.length - 1]! : '')
      logDatesLoaded.value = true
    }
  } catch (e) {
    console.error('获取日志日期列表失败:', e)
  }
}

async function handleDownloadLog() {
  if (!logSelectedDate.value) return
  logDownloading.value = true
  try {
    const blob = await downloadLog(logSelectedDate.value)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `logs-${logSelectedDate.value}.zip`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch (e) {
    console.error('下载日志失败:', e)
    toast.error('下载日志失败')
  } finally {
    setTimeout(() => { logDownloading.value = false }, 2000)
  }
}

function toggleBackupModule(key: string) {
  const idx = backupSelectedModules.value.indexOf(key)
  if (idx >= 0) {
    backupSelectedModules.value.splice(idx, 1)
  } else {
    backupSelectedModules.value.push(key)
  }
}

function toggleAllBackupModules() {
  if (backupSelectedModules.value.length === backupModules.value.length) {
    backupSelectedModules.value = []
  } else {
    backupSelectedModules.value = backupModules.value.map(m => m.moduleKey)
  }
}

async function handleExportBackup() {
  if (backupSelectedModules.value.length === 0) {
    toast.warning('请至少选择一个模块')
    return
  }
  backupExporting.value = true
  backupExportProgress.value = 0
  try {
    const total = backupSelectedModules.value.length
    const progressStep = 100 / total
    for (let i = 0; i < total; i++) {
      backupExportProgress.value = Math.round((i + 1) * progressStep)
      await new Promise(r => setTimeout(r, 100))
    }
    const res = await exportBackup({ modules: backupSelectedModules.value })
    if (res.code === 200 && res.data) {
      backupExportProgress.value = 100
      const jsonStr = res.data.jsonData
      const blob = new Blob([jsonStr], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      const now = new Date()
      const ts = `${now.getFullYear()}${String(now.getMonth()+1).padStart(2,'0')}${String(now.getDate()).padStart(2,'0')}_${String(now.getHours()).padStart(2,'0')}${String(now.getMinutes()).padStart(2,'0')}${String(now.getSeconds()).padStart(2,'0')}`
      a.download = `xianyu_backup_${ts}.json`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success('备份导出成功')
    } else {
      toast.error(res.msg || '导出失败')
    }
  } catch (e: any) {
    console.error('导出备份失败:', e)
    toast.error(e.message || '导出失败')
  } finally {
    backupExporting.value = false
    backupExportProgress.value = 0
  }
}

const importFileInput = ref<HTMLInputElement | null>(null)
const importJsonData = ref('')
const importFileName = ref('')

function triggerImportFile() {
  importFileInput.value?.click()
}

function handleImportFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  importFileName.value = file.name
  const reader = new FileReader()
  reader.onload = (ev) => {
    importJsonData.value = ev.target?.result as string
  }
  reader.readAsText(file)
}

async function handleImportBackup() {
  if (!importJsonData.value) {
    toast.warning('请先选择备份文件')
    return
  }
  if (backupSelectedModules.value.length === 0) {
    toast.warning('请至少选择一个模块')
    return
  }

  try {
    await showConfirm(
      '导入数据将覆盖当前选中模块的已有数据，是否继续？',
      '确认导入'
    )
  } catch {
    return
  }

  backupImporting.value = true
  backupImportProgress.value = 0
  try {
    const total = backupSelectedModules.value.length
    const progressStep = 100 / (total + 1)
    for (let i = 0; i < total; i++) {
      backupImportProgress.value = Math.round((i + 1) * progressStep)
      await new Promise(r => setTimeout(r, 100))
    }
    const res = await importBackup({ jsonData: importJsonData.value, modules: backupSelectedModules.value })
    if (res.code === 200 && res.data) {
      backupImportProgress.value = 100
      const result = res.data
      if (result.failedModules && result.failedModules.length > 0) {
        toast.warning(`导入完成：${result.successCount}/${result.totalCount} 成功，失败模块：${result.failedModules.join(', ')}`)
      } else {
        toast.success(`导入成功：${result.successCount} 个模块`)
      }
      importJsonData.value = ''
      importFileName.value = ''
      if (importFileInput.value) importFileInput.value.value = ''
    } else {
      toast.error(res.msg || '导入失败')
    }
  } catch (e: any) {
    console.error('导入备份失败:', e)
    toast.error(e.message || '导入失败')
  } finally {
    backupImporting.value = false
    backupImportProgress.value = 0
  }
}

function handleBackupMenuEnter() {
  loadBackupModules()
  loadLogDates()
}
</script>

<template>
  <div class="settings">
    <!-- 左侧菜单 -->
    <div class="settings__sidebar">
      <div class="settings__sidebar-title">设置</div>
      <div class="settings__menu">
        <div
          v-for="item in menuItems"
          :key="item.key"
          class="settings__menu-item"
          :class="{ 'settings__menu-item--active': activeMenu === item.key }"
          @click="handleSettingsMenuSelect(item.key)"
        >
          <span class="settings__menu-icon"><component :is="item.icon" /></span>
          <span class="settings__menu-label">{{ item.label }}</span>
        </div>
      </div>
    </div>

    <!-- 右侧内容 -->
    <div class="settings__content">
      <!-- AI 服务配置（包含 Embedding 配置和系统提示词） -->
      <div v-if="activeMenu === 'ai'" class="settings__panel">
        <div class="settings__panel-title">AI 服务配置</div>

        <!-- AI 状态指示 -->
        <div class="settings__ai-status">
          <div class="settings__ai-status-row">
            <span class="settings__info-label">服务状态</span>
            <span class="settings__ai-status-badge" :class="aiStatus.available ? 'settings__ai-status-badge--ok' : 'settings__ai-status-badge--off'">
              {{ aiStatus.available ? '可用' : '不可用' }}
            </span>
          </div>
          <div v-if="aiStatus.message" class="settings__ai-status-row">
            <span class="settings__info-label">状态说明</span>
            <span class="settings__info-value settings__ai-status-msg">{{ aiStatus.message }}</span>
          </div>
          <div v-if="aiStatus.baseUrl" class="settings__ai-status-row">
            <span class="settings__info-label">Base URL</span>
            <span class="settings__info-value">{{ aiStatus.baseUrl }}</span>
          </div>
          <div v-if="aiStatus.model" class="settings__ai-status-row">
            <span class="settings__info-label">模型</span>
            <span class="settings__info-value">{{ aiStatus.model }}</span>
          </div>
        </div>

        <!-- AI 对话配置 -->
        <div class="settings__section">
          <div class="settings__section-title">对话模型配置</div>
          <p class="settings__desc">配置 AI 对话服务，配置后立即生效，无需重启服务</p>
          <div class="settings__form">
            <div class="settings__field">
              <label class="settings__label">服务商类型</label>
              <select
                v-model="aiProvider"
                class="settings__input"
                :disabled="aiApiKeySaving"
                @change="handleProviderChange"
              >
                <option v-for="provider in AI_PROVIDERS" :key="provider.value" :value="provider.value">
                  {{ provider.label }}
                </option>
              </select>
              <p class="settings__hint">{{ getProvider(aiProvider).description }}</p>
            </div>

            <div class="settings__field">
              <label class="settings__label">
                API Key
                <span class="settings__label-hint">（请填写所选服务商的官方 API Key，不要使用其他服务商的密钥）</span>
              </label>
              <div class="settings__input-wrap">
                <input
                  v-model="aiApiKey"
                  :type="showApiKey ? 'text' : 'password'"
                  class="settings__input"
                  placeholder="请输入 AI API Key"
                  :disabled="aiApiKeySaving"
                />
                <button class="settings__eye-btn" @click="showApiKey = !showApiKey" tabindex="-1">
                  {{ showApiKey ? '隐藏' : '显示' }}
                </button>
              </div>
            </div>

            <div class="settings__field">
              <label class="settings__label">API Base URL</label>
              <input
                v-model="aiBaseUrl"
                type="text"
                class="settings__input"
                placeholder="AI 服务的 API Base URL"
                :disabled="aiApiKeySaving"
              />
            </div>

            <div class="settings__field">
              <label class="settings__label" style="display: flex; justify-content: space-between; align-items: center;">
                <span>模型名称</span>
                <button 
                  class="settings__btn settings__btn--secondary" 
                  style="min-height: 28px; height: 28px; font-size: 13px; padding: 0 10px;"
                  :disabled="fetchingModels"
                  @click="handleFetchModels('chat')"
                >
                  {{ fetchingModels ? '获取中...' : '一键获取模型' }}
                </button>
              </label>
              <div style="position: relative; width: 100%;">
                <input
                  v-model="aiModel"
                  type="text"
                  class="settings__input"
                  placeholder="AI 对话模型"
                  :disabled="aiApiKeySaving"
                  @focus="showChatDropdown = true"
                  @blur="hideChatDropdownDelay"
                />
                <div v-show="showChatDropdown && availableModels.length > 0" style="position: absolute; top: 100%; left: 0; width: 100%; max-height: 200px; overflow-y: auto; background: white; border: 1px solid #dcdfe6; border-radius: 4px; box-shadow: 0 2px 12px 0 rgba(0,0,0,.1); z-index: 1000; margin-top: 4px; padding: 6px 0;">
                  <div 
                    v-for="m in availableModels" 
                    :key="m" 
                    @mousedown="selectChatModel(m)" 
                    style="padding: 8px 15px; cursor: pointer; color: #333; font-size: 14px; transition: background 0.2s;"
                    onmouseover="this.style.backgroundColor='#f5f7fa'"
                    onmouseout="this.style.backgroundColor='transparent'"
                  >
                    {{ m }}
                  </div>
                </div>
              </div>
            </div>

            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--secondary"
                :disabled="aiApiKeySaving || testingChat"
                @click="handleTestChat"
              >
                {{ testingChat ? '测试中...' : '测试连接' }}
              </button>
              <button
                class="settings__btn settings__btn--secondary"
                :disabled="aiApiKeySaving"
                @click="handleResetAIConfig"
              >
                恢复默认
              </button>
              <button
                class="settings__btn settings__btn--primary"
                :disabled="aiApiKeySaving"
                @click="handleSaveAIConfig"
              >
                {{ aiApiKeySaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 知识库 Embedding 配置 -->
        <div class="settings__section">
          <div class="settings__section-header">
            <div class="settings__section-title">知识库检索模型（高级）</div>
            <button
              class="settings__toggle-btn"
              @click="showEmbeddingConfig = !showEmbeddingConfig"
            >
              {{ showEmbeddingConfig ? '收起' : '高级配置' }}
            </button>
          </div>
          <p class="settings__desc">
            仅在自动回复使用“知识库资料”时需要。它会把资料转换为可检索内容，帮助 AI 根据商品资料作答；不使用知识库时无需调整。默认共用对话模型的配置。
            <strong>注意：修改后需要重启服务才能生效。</strong>
          </p>
          <div class="settings__form">
            <!-- 折叠内容 -->
            <div v-if="showEmbeddingConfig" class="settings__collapse-content">
              <div class="settings__field">
                <label class="settings__label">API Key <span class="settings__label-hint">(留空则使用对话模型的 API Key)</span></label>
                <div class="settings__input-wrap">
                  <input
                    v-model="embeddingApiKey"
                    :type="showEmbeddingApiKey ? 'text' : 'password'"
                    class="settings__input"
                    placeholder="留空则使用对话模型的 API Key"
                    :disabled="embeddingSaving"
                  />
                  <button class="settings__eye-btn" @click="showEmbeddingApiKey = !showEmbeddingApiKey" tabindex="-1">
                    {{ showEmbeddingApiKey ? '隐藏' : '显示' }}
                  </button>
                </div>
              </div>

              <div class="settings__field">
                <label class="settings__label">API Base URL <span class="settings__label-hint">(留空则使用对话模型的 Base URL)</span></label>
                <input
                  v-model="embeddingBaseUrl"
                  type="text"
                  class="settings__input"
                  placeholder="留空则使用对话模型的 Base URL"
                  :disabled="embeddingSaving"
                />
              </div>
            </div>

            <div class="settings__field">
              <label class="settings__label" style="display: flex; justify-content: space-between; align-items: center;">
                <span>模型名称</span>
                <button 
                  class="settings__btn settings__btn--secondary" 
                  style="min-height: 28px; height: 28px; font-size: 13px; padding: 0 10px;"
                  :disabled="fetchingEmbeddingModels"
                  @click="handleFetchModels('embedding')"
                >
                  {{ fetchingEmbeddingModels ? '获取中...' : '一键获取模型' }}
                </button>
              </label>
              <div style="position: relative; width: 100%;">
                <input
                  v-model="embeddingModel"
                  type="text"
                  class="settings__input"
                  placeholder="Embedding 模型名称，如 text-embedding-v3"
                  :disabled="embeddingSaving"
                  @focus="showEmbeddingDropdown = true"
                  @blur="hideEmbeddingDropdownDelay"
                />
                <div v-show="showEmbeddingDropdown && availableEmbeddingModels.length > 0" style="position: absolute; top: 100%; left: 0; width: 100%; max-height: 200px; overflow-y: auto; background: white; border: 1px solid #dcdfe6; border-radius: 4px; box-shadow: 0 2px 12px 0 rgba(0,0,0,.1); z-index: 1000; margin-top: 4px; padding: 6px 0;">
                  <div 
                    v-for="m in availableEmbeddingModels" 
                    :key="m" 
                    @mousedown="selectEmbeddingModel(m)" 
                    style="padding: 8px 15px; cursor: pointer; color: #333; font-size: 14px; transition: background 0.2s;"
                    onmouseover="this.style.backgroundColor='#f5f7fa'"
                    onmouseout="this.style.backgroundColor='transparent'"
                  >
                    {{ m }}
                  </div>
                </div>
              </div>
            </div>

            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--secondary"
                :disabled="embeddingSaving || testingEmbedding"
                @click="handleTestEmbedding"
              >
                {{ testingEmbedding ? '测试中...' : '测试连接' }}
              </button>
              <button
                class="settings__btn settings__btn--secondary"
                :disabled="embeddingSaving"
                @click="handleResetEmbeddingConfig"
              >
                恢复默认
              </button>
              <button
                class="settings__btn settings__btn--primary"
                :disabled="embeddingSaving"
                @click="handleSaveEmbeddingConfig"
              >
                {{ embeddingSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- AI客服配置 -->
      <div v-if="activeMenu === 'prompt'" class="settings__panel">
        <div class="settings__panel-title">AI客服配置</div>

        <!-- 系统提示词 -->
        <div class="settings__section">
          <div class="settings__section-title">系统提示词</div>
          <p class="settings__desc">配置 AI 智能回复的系统提示词，用于设定 AI 的角色和行为规则</p>
          <div class="settings__form">
            <textarea
              v-model="sysPromptValue"
              class="settings__textarea"
              placeholder="请输入系统提示词"
              :disabled="sysPromptSaving"
              rows="8"
            ></textarea>
            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--secondary"
                :disabled="sysPromptSaving"
                @click="handleResetSysPrompt"
              >
                恢复默认
              </button>
              <button
                class="settings__btn settings__btn--primary"
                :disabled="sysPromptSaving"
                @click="handleSaveSysPrompt"
              >
                {{ sysPromptSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 相似度阈值 -->
        <div class="settings__section">
          <div class="settings__section-title">相似度阈值</div>
          <p class="settings__desc">
            配置 RAG 向量搜索的相似度阈值。值越小，匹配越宽松，会返回更多相关度较低的结果；值越大，匹配越严格，只返回高度相关的结果。
          </p>
          <div class="settings__form">
            <div class="settings__field">
              <label class="settings__label">相似度阈值 (0-1)</label>
              <input
                v-model.number="similarityThreshold"
                type="number"
                class="settings__input"
                placeholder="0.1"
                :disabled="similarityThresholdSaving"
                min="0"
                max="1"
                step="0.01"
              />
              <p class="settings__hint">推荐值：0.1（宽松）到 0.5（严格）之间</p>
            </div>
            <div class="settings__actions">
              <button
                class="settings__btn settings__btn--secondary"
                :disabled="similarityThresholdSaving"
                @click="handleResetSimilarityThreshold"
              >
                恢复默认
              </button>
              <button
                class="settings__btn settings__btn--primary"
                :disabled="similarityThresholdSaving"
                @click="handleSaveSimilarityThreshold"
              >
                {{ similarityThresholdSaving ? '保存中...' : '保存' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 备份与恢复 -->
      <div v-if="activeMenu === 'backup'" class="settings__panel">
        <div class="settings__panel-title">备份与恢复</div>

        <div class="settings__section">
          <div class="settings__section-title">选择备份模块</div>
          <p class="settings__desc">选择需要导出或导入的数据模块，默认全部选择</p>
          <div class="settings__backup-modules">
            <div class="settings__backup-module-all">
              <label class="settings__checkbox-label" @click.prevent="toggleAllBackupModules">
                <span class="settings__checkbox" :class="{ 'settings__checkbox--checked': backupSelectedModules.length === backupModules.length && backupModules.length > 0 }">
                  <span v-if="backupSelectedModules.length === backupModules.length && backupModules.length > 0" class="settings__checkbox-tick">✓</span>
                </span>
                全选
              </label>
            </div>
            <div v-for="mod in backupModules" :key="mod.moduleKey" class="settings__backup-module-item">
              <label class="settings__checkbox-label" @click.prevent="toggleBackupModule(mod.moduleKey)">
                <span class="settings__checkbox" :class="{ 'settings__checkbox--checked': backupSelectedModules.includes(mod.moduleKey) }">
                  <span v-if="backupSelectedModules.includes(mod.moduleKey)" class="settings__checkbox-tick">✓</span>
                </span>
                {{ mod.moduleName }}
              </label>
            </div>
          </div>
        </div>

        <div class="settings__section">
          <div class="settings__section-title">导出备份</div>
          <p class="settings__desc">将选中模块的数据导出为 JSON 文件</p>
          <div v-if="backupExporting" class="settings__progress-wrap">
            <div class="settings__progress-bar">
              <div class="settings__progress-fill" :style="{ width: backupExportProgress + '%' }"></div>
            </div>
            <span class="settings__progress-text">{{ backupExportProgress }}%</span>
          </div>
          <div class="settings__actions">
            <button
              class="settings__btn settings__btn--primary"
              :disabled="backupExporting || backupSelectedModules.length === 0"
              @click="handleExportBackup"
            >
              {{ backupExporting ? '导出中...' : '导出备份' }}
            </button>
          </div>
        </div>

        <div class="settings__section">
          <div class="settings__section-title">导入恢复</div>
          <p class="settings__desc">从 JSON 备份文件中恢复数据（将覆盖当前选中模块的已有数据）</p>
          <input
            ref="importFileInput"
            type="file"
            accept=".json"
            class="settings__file-input"
            @change="handleImportFileChange"
          />
          <div class="settings__import-file-row">
            <button
              class="settings__btn settings__btn--secondary"
              :disabled="backupImporting"
              @click="triggerImportFile"
            >
              选择文件
            </button>
            <span v-if="importFileName" class="settings__import-file-name">{{ importFileName }}</span>
            <span v-else class="settings__import-file-hint">未选择文件</span>
          </div>
          <div v-if="backupImporting" class="settings__progress-wrap">
            <div class="settings__progress-bar">
              <div class="settings__progress-fill" :style="{ width: backupImportProgress + '%' }"></div>
            </div>
            <span class="settings__progress-text">{{ backupImportProgress }}%</span>
          </div>
          <div class="settings__actions">
            <button
              class="settings__btn settings__btn--danger"
              :disabled="backupImporting || !importJsonData || backupSelectedModules.length === 0"
              @click="handleImportBackup"
            >
              {{ backupImporting ? '导入中...' : '导入恢复' }}
            </button>
          </div>
        </div>

        <div class="settings__section">
          <div class="settings__section-title">日志打包下载</div>
          <p class="settings__desc">选择日期，将该天的日志文件打包为 ZIP 下载</p>
          <div class="settings__log-pack-row">
            <select v-model="logSelectedDate" class="settings__log-select">
              <option v-for="d in logDates" :key="d" :value="d">{{ d }}</option>
            </select>
            <button
              class="settings__btn settings__btn--primary"
              :disabled="!logSelectedDate || logDownloading"
              @click="handleDownloadLog"
            >
              {{ logDownloading ? '打包中...' : '下载日志' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 日志清理 -->
      <div v-if="activeMenu === 'logs'" class="settings__panel">
        <div class="settings__panel-title">日志清理</div>

        <div class="settings__section settings__section--first">
          <div class="settings__section-title">项目日志保留时间</div>
          <p class="settings__desc">
            选择后会立即清理超过保留时间的项目日志，并在每天凌晨 3:30 自动清理一次。
            清理范围包括应用运行日志和操作日志，不会删除飞牛系统或其他应用的日志。
          </p>
          <div class="settings__retention-options">
            <button
              v-for="days in LOG_RETENTION_OPTIONS"
              :key="days"
              type="button"
              class="settings__retention-option"
              :class="{ 'settings__retention-option--active': logRetentionDays === days }"
              :disabled="logRetentionSaving"
              @click="logRetentionDays = days"
            >
              {{ days }} 天
            </button>
          </div>
          <p class="settings__hint">
            {{ logRetentionConfigured ? `当前已启用：保留最近 ${logRetentionDays} 天。` : '尚未保存，当前不会自动删除历史日志。' }}
          </p>
          <div class="settings__actions">
            <button
              class="settings__btn settings__btn--primary"
              :disabled="logRetentionSaving"
              @click="handleSaveLogRetention"
            >
              {{ logRetentionSaving ? '保存并清理中...' : '保存并立即清理' }}
            </button>
          </div>
        </div>

        <div class="settings__warning-box">
          <div class="settings__warning-icon">ℹ️</div>
          <div class="settings__warning-content">
            <strong>容器控制台日志</strong>
            <p>项目已限制为最多 3 个 20MB 文件，避免 Docker 控制台日志持续占用空间。</p>
          </div>
        </div>
      </div>

      <!-- 关于 -->
      <div v-if="activeMenu === 'about'" class="settings__panel">
        <div class="settings__panel-title">关于</div>

        <!-- 产品定位 -->
        <div class="settings__section">
          <div class="settings__section-title">闲鱼Plus（XianYuPlus）</div>
          <p class="settings__desc">个人私有部署的闲鱼虚拟商品管理与自动化助手，支持商品管理、卡券库存、自动发货、自动评价和异常待办。</p>
        </div>

        <!-- 许可证与使用限制 -->
        <div class="settings__section">
          <div class="settings__section-title">许可证与使用限制</div>
          <p class="settings__desc">本项目采用 PolyForm Noncommercial License 1.0.0，仅授权个人学习、技术研究、实验和其他非商业用途。</p>

          <div class="settings__warning-box">
            <div class="settings__warning-icon">⚠️</div>
            <div class="settings__warning-content">
              <strong>禁止商业用途</strong>
              <ul>
                <li>禁止销售、收费部署、托管服务、SaaS、代运营和收费培训</li>
                <li>禁止通过广告、订阅、佣金或增值服务直接或间接获利</li>
                <li>禁止删除、隐藏或误导许可证、版权和免责声明信息</li>
              </ul>
            </div>
          </div>
        </div>

        <!-- 免责声明 -->
        <div class="settings__section">
          <div class="settings__section-title">免责声明</div>
          <p class="settings__desc">本项目仅供技术学习和研究，不代表闲鱼平台，也未获得相关平台的官方授权或认可。</p>

          <div class="settings__warning-box">
            <div class="settings__warning-icon">⚠️</div>
            <div class="settings__warning-content">
              <strong>使用前须知</strong>
              <ul>
                <li>使用行为必须遵守法律法规、平台服务协议和账号使用规则</li>
                <li>严禁用于欺诈、骚扰、垃圾信息、虚假交易、恶意营销或规避平台安全机制</li>
                <li>自动化操作可能触发验证、登录失效、账号限制或封禁</li>
                <li>Cookie、Token、密码、API Key 和卡密必须妥善保管并定期备份</li>
                <li>项目按“现状”提供，相关账号、数据、交易和业务风险由实际使用方承担</li>
              </ul>
            </div>
          </div>
        </div>

        <!-- 更新教程 -->
        <div class="settings__section">
          <div class="settings__section-title">更新教程</div>
          <p class="settings__desc">更新前建议先完成 MySQL 备份；飞牛 OS 和 Linux 可直接使用项目内置的一键更新脚本。</p>

          <div class="settings__tutorial">
            <div class="settings__tutorial-step">
              <div class="settings__step-number">1</div>
              <div class="settings__step-content">
                <div class="settings__step-title">一键更新并重启</div>
                <div class="settings__code-block">
                  <code>cd ~/xianyu-Plus &amp;&amp; chmod +x update.sh &amp;&amp; ./update.sh</code>
                </div>
              </div>
            </div>
          </div>

          <div class="settings__warning-box">
            <div class="settings__warning-icon">⚠️</div>
            <div class="settings__warning-content">
              <strong>重要提示：</strong>
              <ul>
                <li>更新前使用 <code>mysqldump</code> 备份数据库</li>
                <li>不要删除 Compose 创建的 <code>mysql-data</code> 卷</li>
                <li>数据库结构由 Flyway 在启动时校验和升级</li>
              </ul>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<style scoped>
.settings {
  display: flex;
  gap: 24px;
  height: 100%;
  min-height: 0;
}

/* 左侧菜单 */
.settings__sidebar {
  width: 200px;
  flex-shrink: 0;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(28px) saturate(1.8);
  -webkit-backdrop-filter: blur(28px) saturate(1.8);
  border: 1px solid rgba(255,255,255,0.75);
  border-radius: 14px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.10), 0 1.5px 4px rgba(0,0,0,0.06);
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.settings__sidebar-title {
  font-size: 16px;
  font-weight: 600;
  color: #1c1c1e;
  padding: 0 12px;
  margin-bottom: 16px;
}

.settings__menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.settings__menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  color: rgba(28,28,30,.55);
}

.settings__menu-item:hover {
  background: rgba(255,255,255,0.38);
}

.settings__menu-item--active {
  background: rgba(60,60,67,.12);
  color: #1c1c1e;
  font-weight: 500;
}

.settings__menu-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.settings__menu-icon svg {
  width: 18px;
  height: 18px;
}

.settings__menu-label {
  font-size: 14px;
}

/* 右侧内容 */
.settings__content {
  flex: 1;
  min-width: 0;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(28px) saturate(1.8);
  -webkit-backdrop-filter: blur(28px) saturate(1.8);
  border: 1px solid rgba(255,255,255,0.75);
  border-radius: 14px;
  box-shadow: 0 8px 32px rgba(0,0,0,0.10), 0 1.5px 4px rgba(0,0,0,0.06);
  padding: 24px;
  overflow-y: auto;
  position: relative;
}

.settings__content::before {
  content: '';
  position: absolute;
  top: 0;
  left: 10%;
  right: 10%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.9) 30%, rgba(255,255,255,0.9) 70%, transparent);
  border-radius: 1px;
  pointer-events: none;
}

.settings__panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings__panel-title {
  font-size: 18px;
  font-weight: 600;
  color: #1c1c1e;
}

.settings__section {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 0.5px solid rgba(60,60,67,.12);
}

.settings__section--first {
  margin-top: 0;
  padding-top: 0;
  border-top: none;
}

.settings__retention-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.settings__retention-option {
  min-width: 68px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid rgba(60,60,67,.14);
  border-radius: 9px;
  color: #475467;
  background: rgba(255,255,255,.66);
  cursor: pointer;
  font-size: 13px;
  transition: all .18s ease;
}

.settings__retention-option:hover:not(:disabled) {
  border-color: #0a84ff;
  color: #0a84ff;
}

.settings__retention-option--active {
  border-color: #0a84ff;
  color: #fff;
  background: #0a84ff;
  box-shadow: 0 4px 12px rgba(10,132,255,.24);
}

.settings__retention-option:disabled {
  opacity: .55;
  cursor: not-allowed;
}

.settings__section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.settings__section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1c1c1e;
  margin-bottom: 0;
}

.settings__toggle-btn {
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 590;
  color: #0A84FF;
  background: rgba(255,255,255,0.70);
  backdrop-filter: blur(16px) saturate(1.6);
  -webkit-backdrop-filter: blur(16px) saturate(1.6);
  border: 1px solid rgba(255,255,255,0.85);
  border-radius: 100px;
  cursor: pointer;
  transition: opacity .15s, transform .12s, box-shadow .15s;
  box-shadow: 0 8px 32px rgba(0,0,0,0.08), 0 1.5px 4px rgba(0,0,0,0.04);
}

.settings__toggle-btn:hover {
  background: rgba(255,255,255,0.80);
}

.settings__toggle-btn:active { opacity: .80; transform: scale(.96); }

.settings__collapse-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.settings__desc {
  font-size: 13px;
  color: rgba(28,28,30,.55);
  margin: 0;
}

/* Loading */
.settings__loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px;
  color: rgba(28,28,30,.55);
  font-size: 13px;
}

.settings__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(0, 0, 0, 0.12);
  border-top-color: #1c1c1e;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Info */
.settings__info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.settings__info-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.settings__info-label {
  font-size: 13px;
  color: rgba(28,28,30,.55);
  min-width: 100px;
  flex-shrink: 0;
}

.settings__info-value {
  font-size: 14px;
  color: #1c1c1e;
  font-weight: 500;
}

/* Form */
.settings__form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.settings__label {
  font-size: 13px;
  font-weight: 500;
  color: #1c1c1e;
}

.settings__label-hint {
  font-size: 12px;
  font-weight: 400;
  color: rgba(28,28,30,.55);
}

.settings__link {
  color: #0066cc;
  text-decoration: none;
}

.settings__link:hover {
  text-decoration: underline;
}

.settings__input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.settings__input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  font-size: 14px;
  color: #1c1c1e;
  background: rgba(255,255,255,0.15);
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 8px;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.settings__input:focus {
  border-color: #1c1c1e;
  background: rgba(255,255,255,0.55);
}

.settings__input::placeholder {
  color: rgba(28,28,30,.55);
}

.settings__input:disabled {
  opacity: 0.5;
}

.settings__eye-btn {
  position: absolute;
  right: 10px;
  background: none;
  border: none;
  font-size: 12px;
  color: rgba(28,28,30,.55);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  transition: color 0.2s;
}

.settings__eye-btn:hover {
  color: #1c1c1e;
}

.settings__textarea {
  width: 100%;
  min-height: 200px;
  padding: 12px 14px;
  font-size: 14px;
  line-height: 1.6;
  color: #1c1c1e;
  background: rgba(255,255,255,0.15);
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 8px;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
  resize: vertical;
  font-family: inherit;
}

.settings__textarea:focus {
  border-color: #1c1c1e;
  background: rgba(255,255,255,0.55);
}

.settings__textarea::placeholder {
  color: rgba(28,28,30,.55);
}

.settings__textarea:disabled {
  opacity: 0.5;
}

/* Actions */
.settings__actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 8px;
}

.settings__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 36px;
  padding: 0 18px;
  font-size: 13px;
  font-weight: 590;
  border-radius: 100px;
  border: none;
  cursor: pointer;
  transition: opacity .15s, transform .12s, box-shadow .15s;
  white-space: nowrap;
  min-width: 40px;
  -webkit-tap-highlight-color: transparent;
  font-family: inherit;
  user-select: none;
}

.settings__btn--primary {
  color: #fff;
  background: rgba(10,132,255,0.85);
  backdrop-filter: blur(20px) saturate(1.8);
  -webkit-backdrop-filter: blur(20px) saturate(1.8);
  border: 1px solid rgba(255,255,255,0.35);
  box-shadow: 0 4px 16px rgba(10,132,255,0.35), 0 8px 32px rgba(0,0,0,0.08), 0 1.5px 4px rgba(0,0,0,0.04);
}

@media (hover: hover) {
  .settings__btn--primary:hover {
    background: rgba(10,132,255,0.95);
    box-shadow: 0 6px 20px rgba(10,132,255,0.45), 0 8px 32px rgba(0,0,0,0.08), 0 1.5px 4px rgba(0,0,0,0.04);
  }
}

.settings__btn--primary:active { opacity: .80; transform: scale(.96); }

.settings__btn--secondary {
  color: #0A84FF;
  background: rgba(255,255,255,0.70);
  backdrop-filter: blur(16px) saturate(1.6);
  -webkit-backdrop-filter: blur(16px) saturate(1.6);
  border: 1px solid rgba(255,255,255,0.85);
  box-shadow: 0 8px 32px rgba(0,0,0,0.08), 0 1.5px 4px rgba(0,0,0,0.04);
}

@media (hover: hover) {
  .settings__btn--secondary:hover {
    background: rgba(255,255,255,0.80);
  }
}

.settings__btn--secondary:active { opacity: .80; transform: scale(.96); }

.settings__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.settings__btn--danger {
  color: #FF453A;
  background: rgba(255,69,58,0.15);
  backdrop-filter: blur(16px) saturate(1.6);
  -webkit-backdrop-filter: blur(16px) saturate(1.6);
  border: 1px solid rgba(255,69,58,0.2);
  box-shadow: 0 8px 32px rgba(0,0,0,0.08), 0 1.5px 4px rgba(0,0,0,0.04);
}

@media (hover: hover) {
  .settings__btn--danger:hover {
    background: rgba(255,69,58,0.22);
    border-color: rgba(255,69,58,0.35);
  }
}

.settings__btn--danger:active { opacity: .80; transform: scale(.96); }

/* AI Status */
.settings__ai-status {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  background: rgba(255,255,255,0.15);
  border-radius: 8px;
}

.settings__ai-status-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.settings__ai-status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 500;
}

.settings__ai-status-badge--ok {
  background: #e6f7e6;
  color: #52c41a;
}

.settings__ai-status-badge--off {
  background: rgba(255,255,255,0.55)1f0;
  color: #ff4d4f;
}

.settings__ai-status-msg {
  font-weight: 400;
  color: rgba(28,28,30,.55);
}

/* Logout */
.settings__logout {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.settings__logout-text {
  font-size: 14px;
  color: rgba(28,28,30,.55);
  margin: 0 0 12px 0;
}

/* QR Code */
.settings__qrcode-wrapper {
  display: flex;
  justify-content: center;
  padding: 20px 0;
}

.settings__qrcode {
  max-width: 300px;
  width: 100%;
  height: auto;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* Responsive */
@media (max-width: 768px) {
  .settings {
    flex-direction: column;
    gap: 16px;
  }

  .settings__sidebar {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .settings__sidebar-title {
    width: 100%;
    margin-bottom: 8px;
  }

  .settings__menu {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px;
  }

  .settings__menu-item {
    padding: 8px 12px;
  }

  .settings__content {
    padding: 16px;
  }

  .settings__qrcode {
    max-width: 250px;
  }
}

@media (max-width: 480px) {
  .settings__info-row,
  .settings__ai-status-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .settings__actions {
    flex-direction: column;
  }

  .settings__btn {
    width: 100%;
  }

  .settings__qrcode {
    max-width: 200px;
  }
}

/* 更新教程样式 */
.settings__tutorial {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}

.settings__tutorial-step {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.settings__step-number {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}

.settings__step-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings__step-title {
  font-size: 14px;
  font-weight: 600;
  color: #1c1c1e;
}

.settings__code-block {
  background: #f5f5f7;
  border: 1px solid rgba(60,60,67,.08);
  border-radius: 8px;
  padding: 12px 16px;
  overflow-x: auto;
}

.settings__code-block pre {
  margin: 0;
  padding: 0;
  background: transparent;
  font-family: inherit;
}

.settings__code-block code {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace;
  font-size: 13px;
  color: #1c1c1e;
  display: block;
}

.settings__step-tip {
  font-size: 12px;
  color: rgba(28,28,30,.55);
  margin: 0;
  padding: 8px 12px;
  background: #f0f9ff;
  border-left: 3px solid #0ea5e9;
  border-radius: 4px;
}

.settings__warning-box {
  display: flex;
  gap: 12px;
  margin-top: 20px;
  padding: 16px;
  background: rgba(255,255,255,0.55)9f0;
  border: 1px solid #ffedd5;
  border-radius: 8px;
}

.settings__warning-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.settings__warning-content {
  flex: 1;
  font-size: 13px;
  color: #1c1c1e;
}

.settings__warning-content strong {
  display: block;
  margin-bottom: 8px;
  color: #c2410c;
}

.settings__warning-content ul {
  margin: 0;
  padding-left: 20px;
}

.settings__warning-content li {
  margin-bottom: 6px;
  color: rgba(28,28,30,.55);
}

.settings__warning-content li:last-child {
  margin-bottom: 0;
}

.settings__warning-content code {
  background: rgba(60,60,67,.12);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', 'Fira Code', monospace;
}

.settings__section-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.settings__status-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 500;
  background: rgba(60,60,67,.12);
  color: rgba(28,28,30,.55);
}

.settings__status-badge--success {
  background: rgba(48,209,88,0.12);
  color: #30D158;
}

.settings__switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  cursor: pointer;
}

.settings__switch input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.settings__switch-track {
  width: 51px;
  height: 31px;
  background: rgba(120,120,128,.24);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,.3);
  border-radius: 100px;
  transition: background 0.2s;
  flex-shrink: 0;
}

.settings__switch-thumb {
  position: absolute;
  left: 2px;
  top: 2px;
  width: 27px;
  height: 27px;
  background: linear-gradient(160deg, rgba(255,255,255,1) 0%, rgba(240,240,242,1) 100%);
  border-radius: 50%;
  box-shadow: 0 2px 8px rgba(0,0,0,.22), inset 0 1px 0 rgba(255,255,255,.8);
  transition: transform .22s cubic-bezier(.34,1.56,.64,1);
  pointer-events: none;
}

.settings__switch input:checked + .settings__switch-track {
  background: rgba(48,209,88,0.85);
  border-color: rgba(255,255,255,.4);
}

.settings__switch input:checked + .settings__switch-track + .settings__switch-thumb {
  transform: translateX(20px);
}

.settings__switch input:disabled + .settings__switch-track {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 通知开关表格 */
.settings__notify-table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 8px;
  overflow: hidden;
  margin-top: 12px;
}

.settings__notify-th {
  background: rgba(255,255,255,0.15);
  padding: 12px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #1c1c1e;
  text-align: left;
  border-bottom: 0.5px solid rgba(60,60,67,.12);
}

.settings__notify-th:last-child {
  text-align: center;
  width: 80px;
}

.settings__notify-tr {
  transition: background 0.2s;
}

.settings__notify-tr:hover {
  background: rgba(255,255,255,0.15);
}

.settings__notify-td {
  padding: 14px 16px;
  font-size: 13px;
  color: #1c1c1e;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.settings__notify-tr:last-child .settings__notify-td {
  border-bottom: none;
}

.settings__notify-td:last-child {
  text-align: center;
}

.settings__notify-td--desc {
  color: rgba(28,28,30,.55);
  font-size: 12px;
}

/* 备份与恢复 */
.settings__backup-modules {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.settings__backup-module-all {
  width: 100%;
  margin-bottom: 4px;
}

.settings__backup-module-item {
}

.settings__checkbox-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: #1c1c1e;
  background: rgba(255,255,255,0.15);
  border: 1px solid rgba(60,60,67,.08);
  transition: all 0.2s;
  user-select: none;
}

.settings__checkbox-label:hover {
  background: rgba(255,255,255,0.38);
  border-color: rgba(60,60,67,.12);
}

.settings__checkbox {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1.5px solid rgba(60,60,67,.2);
  flex-shrink: 0;
  transition: all 0.2s;
}

.settings__checkbox--checked {
  background: rgba(10,132,255,0.85);
  border-color: #1c1c1e;
}

.settings__checkbox-tick {
  color: rgba(255,255,255,0.55);
  font-size: 11px;
  line-height: 1;
}

.settings__progress-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}

.settings__progress-bar {
  flex: 1;
  height: 6px;
  background: rgba(60,60,67,.12);
  border-radius: 3px;
  overflow: hidden;
}

.settings__progress-fill {
  height: 100%;
  background: rgba(10,132,255,0.85);
  border-radius: 3px;
  transition: width 0.3s ease;
}

.settings__progress-text {
  font-size: 12px;
  color: rgba(28,28,30,.55);
  min-width: 36px;
  text-align: right;
}

.settings__file-input {
  display: none;
}

.settings__import-file-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}

.settings__import-file-name {
  font-size: 13px;
  color: #1c1c1e;
  font-weight: 500;
}

.settings__import-file-hint {
  font-size: 13px;
  color: rgba(28,28,30,.55);
}

.settings__log-pack-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}

.settings__log-select {
  flex: 1;
  max-width: 200px;
  padding: 8px 12px;
  font-size: 13px;
  border: 1px solid #d2d2d7;
  border-radius: 8px;
  background: rgba(255,255,255,0.55);
  color: #1c1c1e;
  outline: none;
  appearance: none;
  cursor: pointer;
}

.settings__log-select:focus {
  border-color: #0A84FF;
  box-shadow: 0 0 0 3px rgba(0, 122, 255, 0.12);
}

@media (max-width: 768px) {
  .settings__backup-modules {
    flex-direction: column;
  }
}
</style>

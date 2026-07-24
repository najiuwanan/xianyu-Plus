<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getRuntimeLogTail } from '@/api/runtime-log'
import IconLog from '@/components/icons/IconLog.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import { toast } from '@/utils/toast'

const runtimeLogLines = ref<string[]>([])
const runtimeLogLoading = ref(false)
const runtimeLogPaused = ref(false)
const runtimeLogMessage = ref('')
const runtimeLogUpdatedAt = ref('')
const refreshInterval = ref(2000)
const levelFilter = ref('ALL')
const accountFilter = ref('')
const moduleFilter = ref('')
const pendingLineCount = ref(0)
const logContent = ref<HTMLElement | null>(null)

let refreshTimer: number | undefined
let requestInFlight = false
let initialized = false

const intervalOptions = [
  { value: 2000, label: '2 秒' },
  { value: 5000, label: '5 秒' },
  { value: 10000, label: '10 秒' },
  { value: 30000, label: '30 秒' }
]

const visibleLines = computed(() => runtimeLogLines.value.filter(line => {
  const normalized = line.toLowerCase()
  const levelMatched = levelFilter.value === 'ALL' || new RegExp(`\\b${levelFilter.value}\\b`, 'i').test(line)
  const accountMatched = !accountFilter.value.trim() || normalized.includes(accountFilter.value.trim().toLowerCase())
  const moduleMatched = !moduleFilter.value.trim() || normalized.includes(moduleFilter.value.trim().toLowerCase())
  return levelMatched && accountMatched && moduleMatched
}))

const isViewingLatest = () => (logContent.value?.scrollTop || 0) <= 24

const mergeIncomingLines = async (incomingNewestFirst: string[]) => {
  if (!initialized) {
    runtimeLogLines.value = incomingNewestFirst
    initialized = true
    return
  }

  const existing = new Set(runtimeLogLines.value)
  const appended: string[] = []
  for (const line of incomingNewestFirst) {
    if (existing.has(line)) break
    appended.push(line)
  }
  if (!appended.length) return

  const stayAtLatest = isViewingLatest()
  runtimeLogLines.value = [...appended, ...runtimeLogLines.value].slice(0, 500)
  if (stayAtLatest) {
    await nextTick()
    if (logContent.value) logContent.value.scrollTop = 0
  } else {
    pendingLineCount.value += appended.length
  }
}

const loadRuntimeLogs = async (manual = false) => {
  if (requestInFlight) return
  requestInFlight = true
  runtimeLogLoading.value = true
  try {
    const response = await getRuntimeLogTail(500)
    if (response.code === 0 || response.code === 200) {
      await mergeIncomingLines([...(response.data?.lines || [])].reverse())
      runtimeLogMessage.value = response.data?.message || ''
      runtimeLogUpdatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
      if (manual) await goToLatest()
    }
  } catch (error) {
    console.error('加载实时运行日志失败:', error)
  } finally {
    runtimeLogLoading.value = false
    requestInFlight = false
  }
}

const restartAutoRefresh = () => {
  if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    if (!runtimeLogPaused.value) void loadRuntimeLogs()
  }, refreshInterval.value)
}

const toggleAutoRefresh = () => {
  runtimeLogPaused.value = !runtimeLogPaused.value
  if (!runtimeLogPaused.value) void loadRuntimeLogs()
}

const goToLatest = async () => {
  pendingLineCount.value = 0
  await nextTick()
  if (logContent.value) logContent.value.scrollTop = 0
}

const handleScroll = () => {
  if (isViewingLatest()) pendingLineCount.value = 0
}

const clearDisplay = () => {
  runtimeLogLines.value = []
  pendingLineCount.value = 0
  initialized = false
  runtimeLogMessage.value = '已清空当前页面显示，服务器上的日志文件不会受影响。'
}

const copyVisibleLogs = async () => {
  if (!visibleLines.value.length) return
  try {
    await navigator.clipboard.writeText(visibleLines.value.join('\n'))
    toast.success('已复制当前筛选结果')
  } catch {
    toast.error('复制失败，请检查浏览器剪贴板权限')
  }
}

watch(refreshInterval, restartAutoRefresh)

onMounted(() => {
  void loadRuntimeLogs()
  restartAutoRefresh()
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) window.clearInterval(refreshTimer)
})
</script>

<template>
  <div class="runtime-log-page">
    <div class="runtime-log-page__header">
      <div class="runtime-log-page__title-row">
        <div class="runtime-log-page__title-icon"><IconLog /></div>
        <div>
          <h1>实时日志</h1>
          <p>最新内容固定在顶部；查看历史日志时不会被自动刷新打断。</p>
        </div>
      </div>
      <div class="runtime-log-page__actions">
        <span class="runtime-log-page__status" :class="{ 'runtime-log-page__status--paused': runtimeLogPaused }">
          <i></i>{{ runtimeLogPaused ? '已暂停' : `每 ${refreshInterval / 1000} 秒刷新` }}
        </span>
        <select v-model="refreshInterval" aria-label="刷新间隔">
          <option v-for="option in intervalOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
        <button class="runtime-log-page__button runtime-log-page__button--ghost" @click="toggleAutoRefresh">{{ runtimeLogPaused ? '继续刷新' : '暂停刷新' }}</button>
        <button class="runtime-log-page__button" :disabled="runtimeLogLoading" @click="loadRuntimeLogs(true)"><IconRefresh />刷新</button>
      </div>
    </div>

    <section class="runtime-log-page__card">
      <div class="runtime-log-page__filters">
        <select v-model="levelFilter" aria-label="日志级别"><option value="ALL">全部级别</option><option value="ERROR">错误</option><option value="WARN">警告</option><option value="INFO">信息</option><option value="DEBUG">调试</option></select>
        <input v-model="accountFilter" placeholder="筛选账号 / UNB / 备注" aria-label="账号筛选">
        <input v-model="moduleFilter" placeholder="筛选模块或关键字" aria-label="模块筛选">
        <button type="button" class="runtime-log-page__button runtime-log-page__button--ghost" :disabled="!visibleLines.length" @click="copyVisibleLogs">复制</button>
        <button type="button" class="runtime-log-page__button runtime-log-page__button--ghost" @click="clearDisplay">清空显示</button>
      </div>
      <div class="runtime-log-page__card-header">
        <span>最新 {{ visibleLines.length }} 条日志</span>
        <span v-if="runtimeLogUpdatedAt">上次刷新：{{ runtimeLogUpdatedAt }}</span>
      </div>
      <button v-if="pendingLineCount" type="button" class="runtime-log-page__pending" @click="goToLatest">有 {{ pendingLineCount }} 条新日志 · 回到最新</button>
      <div ref="logContent" class="runtime-log-page__content" @scroll="handleScroll">
        <div v-if="runtimeLogLoading && !runtimeLogLines.length" class="runtime-log-page__empty">正在读取运行日志…</div>
        <pre v-else-if="visibleLines.length">{{ visibleLines.join('\n') }}</pre>
        <div v-else class="runtime-log-page__empty">{{ runtimeLogMessage || '当前筛选条件下暂无运行日志。' }}</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.runtime-log-page { height: 100%; min-height: 0; display: flex; flex-direction: column; padding: 24px; box-sizing: border-box; color: var(--xy-ink); }
.runtime-log-page__header, .runtime-log-page__title-row, .runtime-log-page__actions, .runtime-log-page__filters, .runtime-log-page__card-header, .runtime-log-page__status, .runtime-log-page__button { display: flex; align-items: center; }
.runtime-log-page__header { justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.runtime-log-page__title-row { gap: 12px; }.runtime-log-page__title-icon { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 10px; color: var(--xy-blue); background: var(--xy-blue-soft); }.runtime-log-page__title-icon svg { width: 20px; height: 20px; }.runtime-log-page h1 { margin: 0; font-size: 21px; }.runtime-log-page p { margin: 3px 0 0; color: var(--xy-muted); font-size: 13px; }
.runtime-log-page__actions { gap: 8px; flex-wrap: wrap; justify-content: flex-end; }.runtime-log-page select, .runtime-log-page input { height: 34px; box-sizing: border-box; border: 1px solid #d5deea; border-radius: 8px; padding: 0 9px; color: #3d536f; background: #fff; font: inherit; font-size: 12px; }
.runtime-log-page__status { gap: 5px; padding: 5px 9px; border-radius: 999px; color: #16803a; background: rgba(48,209,88,.14); font-size: 12px; font-weight: 600; }.runtime-log-page__status i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }.runtime-log-page__status--paused { color: #9d6500; background: rgba(255,159,10,.14); }
.runtime-log-page__button { height: 34px; gap: 5px; padding: 0 12px; border: 1px solid #d5deea; border-radius: 9px; color: var(--xy-blue); background: #fff; cursor: pointer; font-size: 13px; font-weight: 650; }.runtime-log-page__button svg { width: 15px; height: 15px; }.runtime-log-page__button--ghost { border-color: transparent; background: transparent; }.runtime-log-page__button:disabled { opacity: .55; cursor: not-allowed; }
.runtime-log-page__card { position: relative; flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; border: 1px solid #dfe7f0; border-radius: 15px; background: #fff; box-shadow: 0 8px 28px rgba(20, 45, 76, .07); }.runtime-log-page__filters { gap: 8px; flex-wrap: wrap; padding: 11px 16px; border-bottom: 1px solid #e8edf3; background: #fbfcfe; }.runtime-log-page__filters input { width: min(220px, 100%); }
.runtime-log-page__card-header { justify-content: space-between; gap: 12px; padding: 11px 16px; border-bottom: 1px solid #e8edf3; color: var(--xy-muted); font-size: 12px; }.runtime-log-page__card-header span:first-child { color: var(--xy-ink); font-weight: 700; font-size: 13px; }.runtime-log-page__pending { position: absolute; z-index: 2; top: 104px; left: 50%; transform: translateX(-50%); border: 1px solid #9fc6fa; border-radius: 999px; padding: 7px 12px; color: #1156aa; background: #edf6ff; box-shadow: 0 5px 14px rgba(20,83,150,.14); font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; }
.runtime-log-page__content { flex: 1; min-height: 0; overflow: auto; background: #0b1726; }.runtime-log-page pre { margin: 0; min-width: max-content; padding: 14px 16px; color: #d7e4f2; font: 12px/1.6 Consolas, monospace; white-space: pre-wrap; word-break: break-word; }.runtime-log-page__empty { min-height: 180px; display: grid; place-items: center; padding: 16px; color: #a8bdd1; font-size: 13px; text-align: center; }
@media (max-width: 768px) { .runtime-log-page { padding: 16px; }.runtime-log-page__header { display: block; }.runtime-log-page__actions { margin-top: 12px; justify-content: flex-start; }.runtime-log-page__card-header { align-items: flex-start; flex-direction: column; }.runtime-log-page__filters { align-items: stretch; flex-direction: column; }.runtime-log-page__filters input { width: 100%; }.runtime-log-page__pending { top: 156px; } }
</style>
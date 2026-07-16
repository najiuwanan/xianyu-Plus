<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getRuntimeLogTail } from '@/api/runtime-log'
import IconLog from '@/components/icons/IconLog.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'

const runtimeLogLines = ref<string[]>([])
const runtimeLogLoading = ref(false)
const runtimeLogPaused = ref(false)
const runtimeLogMessage = ref('')
const runtimeLogUpdatedAt = ref('')

let refreshTimer: number | undefined
let requestInFlight = false

const loadRuntimeLogs = async () => {
  if (requestInFlight) return

  requestInFlight = true
  runtimeLogLoading.value = true
  try {
    const response = await getRuntimeLogTail()
    if (response.code === 0 || response.code === 200) {
      runtimeLogLines.value = response.data?.lines || []
      runtimeLogMessage.value = response.data?.message || ''
      runtimeLogUpdatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    }
  } catch (error) {
    console.error('加载实时运行日志失败:', error)
  } finally {
    runtimeLogLoading.value = false
    requestInFlight = false
  }
}

const toggleAutoRefresh = () => {
  runtimeLogPaused.value = !runtimeLogPaused.value
  if (!runtimeLogPaused.value) {
    loadRuntimeLogs()
  }
}

const clearDisplay = () => {
  runtimeLogLines.value = []
  runtimeLogMessage.value = '已清空当前页面显示，服务器上的日志文件不会受影响。'
}

onMounted(() => {
  loadRuntimeLogs()
  refreshTimer = window.setInterval(() => {
    if (!runtimeLogPaused.value) {
      loadRuntimeLogs()
    }
  }, 2000)
})

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) {
    window.clearInterval(refreshTimer)
  }
})
</script>

<template>
  <div class="runtime-log-page">
    <div class="runtime-log-page__header">
      <div class="runtime-log-page__title-row">
        <div class="runtime-log-page__title-icon"><IconLog /></div>
        <div>
          <h1>实时日志</h1>
          <p>查看应用最新运行记录，用于排查连接、自动发货和自动回复等问题。</p>
        </div>
      </div>
      <div class="runtime-log-page__actions">
        <span
          class="runtime-log-page__status"
          :class="{ 'runtime-log-page__status--paused': runtimeLogPaused }"
        >
          <i></i>{{ runtimeLogPaused ? '已暂停' : '每 2 秒刷新' }}
        </span>
        <button class="runtime-log-page__button runtime-log-page__button--ghost" @click="toggleAutoRefresh">
          {{ runtimeLogPaused ? '继续刷新' : '暂停刷新' }}
        </button>
        <button class="runtime-log-page__button" :disabled="runtimeLogLoading" @click="loadRuntimeLogs">
          <IconRefresh />刷新
        </button>
        <button class="runtime-log-page__button runtime-log-page__button--ghost" @click="clearDisplay">清空显示</button>
      </div>
    </div>

    <section class="runtime-log-page__card">
      <div class="runtime-log-page__card-header">
        <span>最新 200 行应用日志</span>
        <span v-if="runtimeLogUpdatedAt">上次刷新：{{ runtimeLogUpdatedAt }}</span>
      </div>
      <div class="runtime-log-page__content">
        <div v-if="runtimeLogLoading && runtimeLogLines.length === 0" class="runtime-log-page__empty">
          正在读取运行日志…
        </div>
        <pre v-else-if="runtimeLogLines.length">{{ runtimeLogLines.join('\n') }}</pre>
        <div v-else class="runtime-log-page__empty">
          {{ runtimeLogMessage || '当前暂无运行日志。' }}
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.runtime-log-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 24px;
  box-sizing: border-box;
  color: #1c1c1e;
}

.runtime-log-page__header,
.runtime-log-page__title-row,
.runtime-log-page__actions,
.runtime-log-page__card-header,
.runtime-log-page__status,
.runtime-log-page__button {
  display: flex;
  align-items: center;
}

.runtime-log-page__header {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.runtime-log-page__title-row { gap: 12px; }
.runtime-log-page__title-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.52);
}
.runtime-log-page__title-icon svg { width: 20px; height: 20px; }
.runtime-log-page h1 { margin: 0; font-size: 21px; }
.runtime-log-page p { margin: 3px 0 0; color: #667085; font-size: 13px; }

.runtime-log-page__actions { gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.runtime-log-page__status {
  gap: 5px;
  padding: 5px 9px;
  border-radius: 999px;
  color: #16803a;
  background: rgba(48, 209, 88, 0.14);
  font-size: 12px;
  font-weight: 600;
}
.runtime-log-page__status i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.runtime-log-page__status--paused { color: #9d6500; background: rgba(255, 159, 10, 0.14); }

.runtime-log-page__button {
  height: 34px;
  gap: 5px;
  padding: 0 12px;
  border: 1px solid rgba(60, 60, 67, 0.12);
  border-radius: 9px;
  color: #0a84ff;
  background: rgba(255, 255, 255, 0.68);
  cursor: pointer;
  font-size: 13px;
}
.runtime-log-page__button svg { width: 15px; height: 15px; }
.runtime-log-page__button--ghost { background: transparent; border-color: transparent; }
.runtime-log-page__button:disabled { opacity: .55; cursor: not-allowed; }

.runtime-log-page__card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.54);
  box-shadow: 0 8px 28px rgba(0, 0, 0, .07);
}

.runtime-log-page__card-header {
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(60, 60, 67, 0.10);
  color: #667085;
  font-size: 12px;
}
.runtime-log-page__card-header span:first-child { color: #1c1c1e; font-weight: 600; font-size: 14px; }
.runtime-log-page__content { flex: 1; min-height: 0; overflow: auto; background: #17212f; }
.runtime-log-page pre { margin: 0; min-width: max-content; padding: 14px 16px; color: #d7e4f2; font: 12px/1.6 "SFMono-Regular", Consolas, monospace; white-space: pre-wrap; word-break: break-word; }
.runtime-log-page__empty { min-height: 180px; display: grid; place-items: center; padding: 16px; color: #a8bdd1; font-size: 13px; text-align: center; }

@media (max-width: 768px) {
  .runtime-log-page { padding: 16px; }
  .runtime-log-page__header { display: block; }
  .runtime-log-page__actions { margin-top: 12px; justify-content: flex-start; }
  .runtime-log-page__card-header { align-items: flex-start; flex-direction: column; }
}
</style>

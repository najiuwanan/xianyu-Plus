<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAutomationScheduleTasks, saveAutomationScheduleTasks, type AutomationScheduleTask } from '@/api/automation-schedule'
import { toast } from '@/utils/toast'

defineOptions({ name: 'AutomationSchedulePage' })

const loading = ref(false)
const saving = ref(false)
const tasks = ref<AutomationScheduleTask[]>([])

async function loadTasks() {
  loading.value = true
  try {
    const response = await getAutomationScheduleTasks()
    tasks.value = Array.isArray(response.data) ? response.data : []
  } finally {
    loading.value = false
  }
}

async function saveTasks() {
  for (const task of tasks.value) {
    const seconds = Number(task.intervalSeconds)
    if (!Number.isInteger(seconds) || seconds < task.minIntervalSeconds) {
      toast.warning(`${task.name} 最短只能设置为 ${task.minIntervalSeconds} 秒`)
      return
    }
  }
  saving.value = true
  try {
    const response = await saveAutomationScheduleTasks(tasks.value.map(task => ({
      taskKey: task.taskKey,
      intervalSeconds: Number(task.intervalSeconds)
    })))
    tasks.value = Array.isArray(response.data) ? response.data : tasks.value
    toast.success('定时任务设置已保存，下一轮扫描立即按新间隔执行')
  } finally {
    saving.value = false
  }
}

function restoreDefault(task: AutomationScheduleTask) {
  task.intervalSeconds = task.defaultIntervalSeconds
}

onMounted(loadTasks)
</script>

<template>
  <div class="automation-schedule-page">
    <header class="page-header">
      <div>
        <h1>定时任务</h1>
        <p>设置业务自动化的检查间隔。保存后立即生效，无需重启服务。</p>
      </div>
      <div class="page-header__actions">
        <button class="button button--secondary" :disabled="loading || saving" @click="loadTasks">{{ loading ? '刷新中…' : '刷新' }}</button>
        <button class="button button--primary" :disabled="loading || saving || !tasks.length" @click="saveTasks">{{ saving ? '保存中…' : '保存设置' }}</button>
      </div>
    </header>

    <section class="schedule-notice">
      <strong>说明：</strong>这里只显示发货、评价、求小红花、擦亮和回复等业务任务。Cookie 刷新、登录保活、日志清理等系统维护任务保持安全默认值，不建议手动调快。
    </section>

    <section class="task-grid" :aria-busy="loading">
      <article v-for="task in tasks" :key="task.taskKey" class="task-card">
        <div class="task-card__body">
          <h2>{{ task.name }}</h2>
          <p>{{ task.description }}</p>
        </div>
        <div class="task-card__setting">
          <label :for="`task-${task.taskKey}`">执行间隔</label>
          <div class="interval-control">
            <input :id="`task-${task.taskKey}`" v-model.number="task.intervalSeconds" type="number" :min="task.minIntervalSeconds" step="1">
            <span>秒</span>
          </div>
          <small>最短 {{ task.minIntervalSeconds }} 秒，默认 {{ task.defaultIntervalSeconds }} 秒</small>
          <button class="reset-link" type="button" @click="restoreDefault(task)">恢复默认</button>
        </div>
      </article>
      <div v-if="!loading && !tasks.length" class="empty-state">暂时无法读取定时任务设置，请刷新重试。</div>
    </section>
  </div>
</template>

<style scoped>
.automation-schedule-page { max-width: 1560px; margin: 0 auto; padding: 30px 32px 42px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 18px; }
.page-header h1 { margin: 0; color: #14213b; font-size: 26px; line-height: 1.25; }
.page-header p { margin: 7px 0 0; color: #687792; font-size: 14px; }
.page-header__actions { display: flex; gap: 10px; flex-shrink: 0; }
.schedule-notice { border: 1px solid #ffe2a2; background: #fff9e9; border-radius: 10px; color: #806021; font-size: 14px; line-height: 1.7; padding: 13px 16px; margin-bottom: 18px; }
.task-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.task-card { display: flex; justify-content: space-between; gap: 24px; min-height: 154px; padding: 20px 22px; background: #fff; border: 1px solid #e8ecf3; border-radius: 12px; box-shadow: 0 6px 20px rgba(42, 60, 90, .04); }
.task-card__body { min-width: 0; }
.task-card h2 { margin: 0; color: #1c2d4b; font-size: 17px; }
.task-card p { margin: 11px 0 0; color: #71809a; font-size: 13px; line-height: 1.7; }
.task-card__setting { width: 174px; flex-shrink: 0; }
.task-card__setting label { display: block; margin-bottom: 7px; color: #4c5b75; font-size: 13px; font-weight: 600; }
.interval-control { display: flex; align-items: center; gap: 8px; }
.interval-control input { width: 112px; height: 36px; padding: 0 10px; border: 1px solid #cfd8e7; border-radius: 7px; color: #1e3151; font-size: 15px; outline: none; }
.interval-control input:focus { border-color: #f0b400; box-shadow: 0 0 0 3px rgba(240, 180, 0, .13); }
.interval-control span, .task-card small { color: #8290a7; font-size: 12px; }
.task-card small { display: block; margin-top: 8px; white-space: nowrap; }
.reset-link { margin-top: 7px; padding: 0; border: 0; background: transparent; color: #2879e7; cursor: pointer; font-size: 12px; }
.empty-state { grid-column: 1 / -1; padding: 52px 20px; border: 1px dashed #dce3ee; border-radius: 12px; color: #8a96aa; text-align: center; }
@media (max-width: 980px) { .automation-schedule-page { padding: 24px 18px; } .task-grid { grid-template-columns: 1fr; } }
@media (max-width: 620px) { .page-header, .task-card { flex-direction: column; } .page-header__actions { width: 100%; } .page-header__actions .button { flex: 1; } .task-card__setting { width: 100%; } }
</style>

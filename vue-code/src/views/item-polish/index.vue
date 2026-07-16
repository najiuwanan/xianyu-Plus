<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAccountList } from '@/api/account'
import { getItemPolishOverview, runItemPolish, saveItemPolishConfig, type ItemPolishOverview } from '@/api/item-polish'
import type { Account } from '@/types'
import { showError, showInfo, showSuccess } from '@/utils'

const accounts = ref<Account[]>([])
const selectedAccountId = ref<number | null>(null)
const overview = ref<ItemPolishOverview | null>(null)
const loading = ref(false)
const saving = ref(false)
const starting = ref(false)
const enabled = ref(false)
const scheduleTime = ref('09:00')

const selectedAccountName = computed(() => {
  const account = accounts.value.find(item => Number(item.id) === selectedAccountId.value)
  return account?.accountNote || account?.unb || '未选择账号'
})

const formatTime = (value?: string) => {
  if (!value) return '暂无'
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

const loadOverview = async (silent = false) => {
  if (!selectedAccountId.value) return
  if (!silent) loading.value = true
  try {
    const response = await getItemPolishOverview(selectedAccountId.value)
    if (response.code !== 0 && response.code !== 200) throw new Error(response.msg || '加载失败')
    overview.value = response.data || null
    if (overview.value?.config) {
      enabled.value = overview.value.config.enabled === 1
      scheduleTime.value = overview.value.config.scheduleTime || '09:00'
    }
  } catch (error: any) {
    if (!silent) showError(`加载自动擦亮配置失败：${error.message || '未知错误'}`)
  } finally {
    loading.value = false
  }
}

const loadAccounts = async () => {
  try {
    const response = await getAccountList()
    if (response.code !== 0 && response.code !== 200) throw new Error(response.msg || '加载账号失败')
    accounts.value = response.data?.accounts || []
    if (!selectedAccountId.value && accounts.value.length > 0) {
      selectedAccountId.value = Number(accounts.value[0]!.id)
    }
    await loadOverview()
  } catch (error: any) {
    showError(`加载账号失败：${error.message || '未知错误'}`)
  }
}

const saveConfig = async () => {
  if (!selectedAccountId.value) return
  saving.value = true
  try {
    const response = await saveItemPolishConfig({
      accountId: selectedAccountId.value,
      enabled: enabled.value ? 1 : 0,
      scheduleTime: scheduleTime.value
    })
    if (response.code !== 0 && response.code !== 200) throw new Error(response.msg || '保存失败')
    showSuccess(enabled.value ? `已开启每天 ${scheduleTime.value} 自动擦亮` : '自动擦亮已关闭')
    await loadOverview(true)
  } catch (error: any) {
    showError(`保存失败：${error.message || '请检查执行时间'}`)
  } finally {
    saving.value = false
  }
}

const runNow = async () => {
  if (!selectedAccountId.value || starting.value || overview.value?.running) return
  starting.value = true
  try {
    const response = await runItemPolish(selectedAccountId.value)
    if (response.code !== 0 && response.code !== 200) throw new Error(response.msg || '启动失败')
    showInfo(response.data?.message || '擦亮任务已开始')
    await loadOverview(true)
    window.setTimeout(() => loadOverview(true), 2500)
  } catch (error: any) {
    showError(`启动擦亮失败：${error.message || '未知错误'}`)
  } finally {
    starting.value = false
  }
}

const handleAccountChange = async () => {
  await loadOverview()
}

onMounted(loadAccounts)
</script>

<template>
  <div class="polish-page">
    <header class="polish-page__header">
      <div>
        <h1>自动擦亮</h1>
        <p>每天定时擦亮已同步的在售商品，保持商品曝光；每件商品会间隔随机时间执行。</p>
      </div>
      <div class="polish-page__actions">
        <select v-model="selectedAccountId" class="polish-select" @change="handleAccountChange">
          <option v-for="account in accounts" :key="account.id" :value="Number(account.id)">
            {{ account.accountNote || account.unb }}
          </option>
        </select>
        <button class="btn btn--secondary" :disabled="loading" @click="loadOverview()">刷新</button>
        <button class="btn btn--primary" :disabled="!selectedAccountId || starting || overview?.running" @click="runNow">
          {{ overview?.running ? '正在擦亮…' : starting ? '启动中…' : '立即擦亮' }}
        </button>
      </div>
    </header>

    <section v-if="selectedAccountId" class="polish-layout" :class="{ 'is-loading': loading }">
      <div class="polish-card polish-card--config">
        <div class="card-title-row">
          <div>
            <h2>执行设置</h2>
            <p>账号：{{ selectedAccountName }}</p>
          </div>
          <label class="switch-row">
            <input v-model="enabled" type="checkbox">
            <span class="switch-track"><span class="switch-thumb"></span></span>
            <span>{{ enabled ? '已开启' : '已关闭' }}</span>
          </label>
        </div>

        <div class="form-row">
          <label for="polish-time">每天执行时间</label>
          <input id="polish-time" v-model="scheduleTime" class="time-input" type="time" :disabled="!enabled">
          <span class="form-hint">只处理本地已同步且仍为“在售”的商品；每件间隔 1–3 秒。</span>
        </div>
        <div class="config-actions">
          <button class="btn btn--primary" :disabled="saving" @click="saveConfig">{{ saving ? '保存中…' : '保存设置' }}</button>
        </div>
      </div>

      <div class="polish-summary">
        <div class="summary-card">
          <span>可擦亮商品</span>
          <strong>{{ overview?.onSaleCount ?? 0 }}</strong>
          <small>本地已同步的在售商品</small>
        </div>
        <div class="summary-card summary-card--success">
          <span>上次成功</span>
          <strong>{{ overview?.config.lastRunSuccess ?? 0 }}</strong>
          <small>共 {{ overview?.config.lastRunTotal ?? 0 }} 件</small>
        </div>
        <div class="summary-card" :class="{ 'summary-card--danger': (overview?.config.lastRunFailed ?? 0) > 0 }">
          <span>上次失败</span>
          <strong>{{ overview?.config.lastRunFailed ?? 0 }}</strong>
          <small>{{ formatTime(overview?.config.lastRunAt) }}</small>
        </div>
      </div>

      <div class="polish-card polish-card--history">
        <div class="card-title-row">
          <div>
            <h2>执行记录</h2>
            <p>{{ overview?.config.lastRunMessage || '暂未执行擦亮任务' }}</p>
          </div>
          <span v-if="overview?.running" class="running-tag">正在执行</span>
        </div>
        <div class="history-wrap">
          <table v-if="overview?.records.length" class="history-table">
            <thead>
              <tr><th>时间</th><th>商品</th><th>触发方式</th><th>结果</th><th>说明</th></tr>
            </thead>
            <tbody>
              <tr v-for="record in overview.records" :key="record.id">
                <td>{{ formatTime(record.createTime) }}</td>
                <td><strong>{{ record.goodsTitle || '未命名商品' }}</strong><small>ID: {{ record.xyGoodsId }}</small></td>
                <td>{{ record.triggerType === 'SCHEDULED' ? '定时' : '手动' }}</td>
                <td><span class="result-tag" :class="record.success === 1 ? 'result-tag--success' : 'result-tag--danger'">{{ record.success === 1 ? '成功' : '失败' }}</span></td>
                <td class="record-message">{{ record.message || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-history">暂无执行记录。可先点击“立即擦亮”验证账号是否可用。</div>
        </div>
      </div>
    </section>

    <div v-else class="empty-state">请先添加闲鱼账号，再配置自动擦亮。</div>
  </div>
</template>

<style scoped>
.polish-page { padding: 28px; color: #1c1c1e; }
.polish-page__header, .card-title-row, .polish-page__actions, .switch-row, .config-actions { display: flex; align-items: center; }
.polish-page__header { justify-content: space-between; gap: 20px; margin-bottom: 22px; }
h1, h2, p { margin: 0; }
h1 { font-size: 25px; letter-spacing: -.4px; }
.polish-page__header p, .card-title-row p, .form-hint, small { color: rgba(28, 28, 30, .55); font-size: 13px; }
.polish-page__header p { margin-top: 6px; }
.polish-page__actions { gap: 10px; flex-wrap: wrap; }
.polish-select, .time-input { height: 38px; min-width: 150px; border: 1px solid rgba(60,60,67,.18); border-radius: 9px; padding: 0 10px; background: #fff; color: #1c1c1e; }
.polish-layout { display: grid; grid-template-columns: 1fr; gap: 18px; transition: opacity .2s; }
.polish-layout.is-loading { opacity: .6; pointer-events: none; }
.polish-card, .summary-card { background: rgba(255,255,255,.9); border: 1px solid rgba(60,60,67,.12); border-radius: 14px; box-shadow: 0 5px 18px rgba(0,0,0,.04); }
.polish-card { padding: 22px; }
.card-title-row { justify-content: space-between; gap: 16px; }
h2 { font-size: 17px; }
.card-title-row p { margin-top: 5px; }
.switch-row { gap: 8px; color: #355070; font-size: 14px; cursor: pointer; user-select: none; }
.switch-row input { position: absolute; opacity: 0; }
.switch-track { width: 42px; height: 24px; border-radius: 999px; background: #d8dde6; padding: 3px; transition: .2s; }
.switch-thumb { display:block; width:18px; height:18px; border-radius:50%; background:#fff; box-shadow:0 1px 3px rgba(0,0,0,.25); transition:.2s; }
.switch-row input:checked + .switch-track { background: #30d158; }
.switch-row input:checked + .switch-track .switch-thumb { transform: translateX(18px); }
.form-row { display: grid; grid-template-columns: 120px 150px 1fr; gap: 14px; align-items: center; margin-top: 23px; }
.form-row label { font-weight: 600; font-size: 14px; }
.time-input:disabled { color: rgba(28,28,30,.35); background: #f4f5f7; }
.config-actions { justify-content: flex-end; margin-top: 22px; }
.polish-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.summary-card { padding: 18px 20px; display: flex; flex-direction: column; gap: 5px; }
.summary-card span { color: rgba(28,28,30,.6); font-size: 14px; }
.summary-card strong { font-size: 28px; }
.summary-card--success strong { color: #1e9b48; }
.summary-card--danger strong { color: #e33f37; }
.history-wrap { overflow-x: auto; margin-top: 17px; }
.history-table { width: 100%; border-collapse: collapse; min-width: 760px; font-size: 13px; }
.history-table th { text-align: left; color: rgba(28,28,30,.55); padding: 10px 12px; border-bottom: 1px solid rgba(60,60,67,.12); font-weight: 500; }
.history-table td { padding: 12px; border-bottom: 1px solid rgba(60,60,67,.08); vertical-align: top; }
.history-table td small { display: block; margin-top: 3px; }
.record-message { color: rgba(28,28,30,.68); max-width: 340px; word-break: break-word; }
.result-tag, .running-tag { border-radius: 999px; padding: 3px 9px; font-size: 12px; white-space: nowrap; }
.result-tag--success, .running-tag { color: #187b38; background: rgba(48,209,88,.15); }
.result-tag--danger { color: #c83129; background: rgba(255,69,58,.13); }
.empty-history, .empty-state { color: rgba(28,28,30,.48); text-align: center; padding: 34px 12px; }
@media (max-width: 800px) {
  .polish-page { padding: 18px 15px; }
  .polish-page__header { display: block; }
  .polish-page__actions { margin-top: 16px; }
  .polish-select { flex: 1; }
  .form-row { grid-template-columns: 1fr; gap: 8px; }
  .form-hint { line-height: 1.5; }
  .polish-summary { grid-template-columns: 1fr; gap: 10px; }
  .polish-card { padding: 17px; }
}
</style>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import IconAlert from '@/components/icons/IconAlert.vue'
import IconClipboard from '@/components/icons/IconClipboard.vue'
import IconMessage from '@/components/icons/IconMessage.vue'
import IconPackage from '@/components/icons/IconPackage.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconTruck from '@/components/icons/IconTruck.vue'
import { useDashboard } from './useDashboard'

const router = useRouter()
const {
  loading,
  stats,
  accountHealth,
  trends,
  activities,
  accountIssueCount,
  automationExceptionCount,
  loadStatistics
} = useDashboard()

const todoCount = computed(() =>
  Number(stats.unreadMessageCount || 0)
  + Number(stats.pendingTaskCount || 0)
  + Number(stats.reviewRequiredCount || 0)
  + Number(automationExceptionCount.value || 0)
  + Number(stats.lowStockConfigCount || 0)
  + Number(accountIssueCount.value || 0)
)

const healthyAccountCount = computed(() =>
  accountHealth.value.filter(item => !item.needsAttention && item.accountStatus === 1).length
)

const recentTrend = computed(() => {
  const pointByDate = new Map(trends.value.map(item => [item.dateKey, item]))
  const days = Array.from({ length: 7 }, (_, index) => {
    const date = new Date()
    date.setHours(0, 0, 0, 0)
    date.setDate(date.getDate() - (6 - index))
    const dateKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    const source = pointByDate.get(dateKey)
    return {
      dateKey,
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      orderCount: Number(source?.orderCount || 0),
      revenue: Number(source?.revenue || 0)
    }
  })
  const max = Math.max(...days.map(item => item.orderCount), 1)
  return days.map(item => ({
    ...item,
    height: item.orderCount ? Math.max(10, Math.round((item.orderCount / max) * 100)) : 4
  }))
})

const sevenDayOrderCount = computed(() => recentTrend.value.reduce((sum, item) => sum + item.orderCount, 0))
const sevenDayRevenue = computed(() => recentTrend.value.reduce((sum, item) => sum + item.revenue, 0))

const todoRows = computed(() => [
  { label: '未读买家消息', detail: '买家消息等待人工查看与回复', count: Number(stats.unreadMessageCount || 0), action: '去处理', path: '/messages', tone: stats.unreadMessageCount ? 'danger' : 'success', icon: IconMessage },
  { label: '等待自动发货', detail: '已付款订单将由自动化任务持续处理', count: Number(stats.pendingTaskCount || 0), action: '查看订单', path: '/orders', tone: stats.pendingTaskCount ? 'warning' : 'success', icon: IconTruck },
  { label: '需要人工核对', detail: '请确认无法自动完成的订单或任务', count: Number(stats.reviewRequiredCount || 0), action: '去核对', path: '/exception-center', tone: stats.reviewRequiredCount ? 'warning' : 'success', icon: IconClipboard },
  { label: '自动化异常', detail: '发货、评价、小红花与擦亮失败记录', count: Number(automationExceptionCount.value || 0), action: '看原因', path: '/exception-center', tone: automationExceptionCount.value ? 'danger' : 'success', icon: IconAlert },
  { label: '卡券库存预警', detail: '库存不足可能影响后续自动发货', count: Number(stats.lowStockConfigCount || 0), action: '去补充', path: '/kami-config', tone: stats.lowStockConfigCount ? 'warning' : 'success', icon: IconPackage }
])

const automationRows = computed(() => [
  { label: '自动发货', value: Number(stats.pendingTaskCount || 0), emptyText: '暂无待发货订单', activeText: '笔订单待处理', path: '/orders', tone: stats.pendingTaskCount ? 'warning' : 'success' },
  { label: '人工核对', value: Number(stats.reviewRequiredCount || 0), emptyText: '暂无待核对任务', activeText: '项需要核对', path: '/exception-center', tone: stats.reviewRequiredCount ? 'warning' : 'success' },
  { label: '异常处理', value: Number(automationExceptionCount.value || 0), emptyText: '自动化运行正常', activeText: '项异常待处理', path: '/exception-center', tone: automationExceptionCount.value ? 'danger' : 'success' },
  { label: '卡券库存', value: Number(stats.lowStockConfigCount || 0), emptyText: '库存状态正常', activeText: '项库存预警', path: '/kami-config', tone: stats.lowStockConfigCount ? 'warning' : 'success' }
])

const money = (value: number) => Number(value || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const go = (path: string) => router.push(path)
const goConnection = (accountId?: number) => router.push(accountId ? `/connection/${accountId}` : '/connection')

const formatActivityTime = (timestamp?: number) => {
  if (!timestamp) return '刚刚'
  const value = timestamp < 100000000000 ? timestamp * 1000 : timestamp
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  const today = new Date()
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
  }
  return `${date.getMonth() + 1}/${date.getDate()} ${date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })}`
}

const activityStatusClass = (status?: number) => {
  if (status === -1) return 'activity-dot--failed'
  if (status === 0) return 'activity-dot--warning'
  return 'activity-dot--success'
}

onMounted(loadStatistics)
</script>

<template>
  <div class="merchant-dashboard" :aria-busy="loading">
    <header class="merchant-dashboard__header">
      <div>
        <h1>运营总览</h1>
        <p>订单、消息与自动化状态，集中处理。</p>
      </div>
      <div class="dashboard-header__actions">
        <button class="button button--secondary" :disabled="loading" @click="loadStatistics">
          <IconRefresh />{{ loading ? '刷新中…' : '刷新数据' }}
        </button>
        <button class="button button--primary" @click="go('/orders')">
          <IconClipboard />订单管理
        </button>
      </div>
    </header>

    <section class="metric-grid" aria-label="今日经营指标">
      <article class="metric-card metric-card--revenue">
        <span class="metric-card__icon metric-card__icon--amber">¥</span>
        <div><span>今日成交额</span><strong>¥{{ money(stats.todayRevenue) }}</strong><small>成功交付订单的金额</small></div>
      </article>
      <article class="metric-card">
        <span class="metric-card__icon metric-card__icon--green"><IconTruck /></span>
        <div><span>今日自动发货</span><strong>{{ stats.todayDeliveryCount }}</strong><small>笔订单已完成交付</small></div>
      </article>
      <article class="metric-card">
        <span class="metric-card__icon metric-card__icon--blue"><IconMessage /></span>
        <div><span>未读客服消息</span><strong>{{ stats.unreadMessageCount }}</strong><small>买家尚未人工查看的消息</small></div>
      </article>
      <article class="metric-card">
        <span class="metric-card__icon metric-card__icon--red"><IconAlert /></span>
        <div><span>待处理异常</span><strong>{{ todoCount }}</strong><small>待办、异常、库存与账号状态</small></div>
      </article>
    </section>

    <section class="dashboard-workbench">
      <section class="dashboard-panel dashboard-panel--todo">
        <div class="panel-heading">
          <div><h2>待办事项</h2><p>{{ todoCount }} 项需要优先关注</p></div>
          <button class="text-button" @click="go('/exception-center')">查看异常中心 <span>›</span></button>
        </div>
        <div class="todo-table" role="table" aria-label="运营待办事项">
          <div class="todo-table__head" role="row"><span>事项</span><span>状态</span><span>数量</span><span>操作</span></div>
          <button v-for="item in todoRows" :key="item.label" class="todo-row" type="button" @click="go(item.path)">
            <span class="todo-row__name"><span class="todo-row__icon" :class="`todo-row__icon--${item.tone}`"><component :is="item.icon" /></span><span><strong>{{ item.label }}</strong><small>{{ item.detail }}</small></span></span>
            <span class="todo-row__status"><i :class="`status-dot status-dot--${item.tone}`"></i>{{ item.count ? '待处理' : '正常' }}</span>
            <strong class="todo-row__count">{{ item.count }}</strong>
            <span class="todo-row__action">{{ item.action }}</span>
          </button>
        </div>
      </section>

      <section class="dashboard-panel dashboard-panel--automation">
        <div class="panel-heading"><div><h2>自动化健康状态</h2><p>根据当前待办与异常任务实时汇总</p></div></div>
        <div class="automation-list">
          <button v-for="item in automationRows" :key="item.label" class="automation-row" type="button" @click="go(item.path)">
            <span class="automation-row__name">{{ item.label }}</span>
            <span class="automation-row__detail" :class="`automation-row__detail--${item.tone}`">{{ item.value ? `${item.value} ${item.activeText}` : item.emptyText }}</span>
            <span class="automation-row__badge" :class="`automation-row__badge--${item.tone}`">{{ item.value ? '关注' : '正常' }}</span>
            <span class="automation-row__arrow">›</span>
          </button>
        </div>
        <div class="account-health-summary">
          <span class="status-dot" :class="accountIssueCount ? 'status-dot--danger' : 'status-dot--success'"></span>
          {{ accountIssueCount ? `${accountIssueCount} 个账号连接或凭证异常` : `${healthyAccountCount} 个账号连接状态正常` }}
          <button @click="go('/connection')">查看账号</button>
        </div>
      </section>
    </section>

    <div class="dashboard-columns">
      <section class="dashboard-panel dashboard-panel--chart">
        <div class="panel-heading"><div><h2>近 7 日成功交付</h2><p>{{ sevenDayOrderCount }} 笔订单，¥{{ money(sevenDayRevenue) }}</p></div></div>
        <div class="chart-legend"><span><i></i>成功交付订单数</span><span>每根柱形代表对应日期的交付数量</span></div>
        <div class="trend-chart" aria-label="近七天成功交付订单趋势">
          <div v-for="item in recentTrend" :key="item.dateKey" class="trend-chart__item" :title="`${item.dateKey}：${item.orderCount} 笔，¥ ${money(item.revenue)}`">
            <strong>{{ item.orderCount }}</strong><div class="trend-chart__track"><i :style="{ height: `${item.height}%` }"></i></div><span>{{ item.label }}</span>
          </div>
        </div>
      </section>

      <section class="dashboard-panel dashboard-panel--activities">
        <div class="panel-heading"><div><h2>最近动态</h2><p>最近 10 条系统操作记录</p></div><button class="text-button" @click="go('/operation-log')">查看全部 <span>›</span></button></div>
        <div v-if="activities.length" class="activity-list">
          <button v-for="(activity, index) in activities" :key="`${activity.createdAt || index}-${activity.content}`" class="activity-row" type="button" @click="go('/operation-log')">
            <span class="activity-dot" :class="activityStatusClass(activity.status)"></span><time>{{ formatActivityTime(activity.createdAt) }}</time><div class="activity-row__content"><strong>{{ activity.content }}</strong><small>{{ activity.accountName || '系统' }}{{ activity.module ? ` · ${activity.module}` : '' }}</small></div><span class="activity-row__arrow">›</span>
          </button>
        </div>
        <div v-else class="empty-state">暂时没有可展示的最近动态</div>
      </section>
    </div>

    <section v-if="accountHealth.length" class="dashboard-panel account-health-panel">
      <div class="panel-heading"><div><h2>账号健康</h2><p>实时连接状态；{{ healthyAccountCount }} 个账号运行正常，{{ accountIssueCount }} 个需要关注</p></div><button class="text-button" @click="go('/connection')">连接管理 <span>›</span></button></div>
      <div class="account-health-list">
        <button v-for="account in accountHealth" :key="account.accountId" class="account-health-row" :class="{ 'account-health-row--attention': account.needsAttention }" @click="goConnection(account.accountId)">
          <div class="account-health-row__main"><strong>{{ account.accountName || `账号 ${account.accountId}` }}</strong><small>{{ account.healthText }}</small></div>
          <div class="health-badges"><span class="health-badge" :class="account.cookieStatus === 1 ? 'health-badge--success' : 'health-badge--muted'">{{ account.cookieStatusText }}</span><span class="health-badge" :class="account.websocketConnected ? 'health-badge--success' : (account.accountStatus === 0 ? 'health-badge--muted' : 'health-badge--warning')">{{ account.websocketConnected ? '实时连接正常' : '实时未连接' }}</span><span v-if="account.automationRiskPaused" class="health-badge health-badge--danger">自动化已暂停</span><span v-else class="health-badge health-badge--muted">{{ account.accountStatusText }}</span><span v-if="account.unreadMessageCount" class="health-badge health-badge--blue">{{ account.unreadMessageCount }} 条未读</span></div>
          <span class="account-health-row__action">处理 ›</span>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped src="./dashboard.css"></style>

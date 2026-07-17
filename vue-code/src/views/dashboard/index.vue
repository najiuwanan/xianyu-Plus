<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
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
  <main class="merchant-dashboard" :aria-busy="loading">
    <header class="merchant-dashboard__header">
      <div>
        <h1>运营总览</h1>
        <p>账号、待办与自动化状态集中查看，发现异常后可直接处理。</p>
      </div>
      <button class="button button--secondary" :disabled="loading" @click="loadStatistics">
        {{ loading ? '刷新中…' : '刷新数据' }}
      </button>
    </header>

    <section class="metric-grid" aria-label="今日经营指标">
      <article class="metric-card metric-card--primary">
        <span>今日成交额</span>
        <strong>¥ {{ money(stats.todayRevenue) }}</strong>
        <small>成功交付订单的金额</small>
      </article>
      <article class="metric-card">
        <span>今日自动发货</span>
        <strong>{{ stats.todayDeliveryCount }}</strong>
        <small>笔订单已完成交付</small>
      </article>
      <article class="metric-card metric-card--message">
        <span>未读客服消息</span>
        <strong>{{ stats.unreadMessageCount }}</strong>
        <small>买家尚未人工查看的消息</small>
      </article>
      <article class="metric-card metric-card--attention">
        <span>需要关注</span>
        <strong>{{ todoCount }}</strong>
        <small>待办、异常、库存与账号状态</small>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-heading">
        <div>
          <h2>今日待办</h2>
          <p>{{ todoCount }} 项需要关注</p>
        </div>
      </div>
      <div class="todo-list">
        <button class="todo-row" @click="go('/messages')">
          <span class="status-dot status-dot--blue"></span>
          <span class="todo-row__label">买家未读消息</span>
          <strong>{{ stats.unreadMessageCount }}</strong>
          <span class="todo-row__action">进入客服</span>
        </button>
        <button class="todo-row" @click="go('/orders')">
          <span class="status-dot status-dot--blue"></span>
          <span class="todo-row__label">等待自动发货</span>
          <strong>{{ stats.pendingTaskCount }}</strong>
          <span class="todo-row__action">查看订单</span>
        </button>
        <button class="todo-row" @click="go('/exception-center')">
          <span class="status-dot status-dot--orange"></span>
          <span class="todo-row__label">需要人工核对</span>
          <strong>{{ stats.reviewRequiredCount }}</strong>
          <span class="todo-row__action">立即核对</span>
        </button>
        <button class="todo-row" @click="go('/exception-center')">
          <span class="status-dot status-dot--red"></span>
          <span class="todo-row__label">自动化异常</span>
          <strong>{{ automationExceptionCount }}</strong>
          <span class="todo-row__action">处理异常</span>
        </button>
        <button class="todo-row" @click="go('/kami-config')">
          <span class="status-dot status-dot--orange"></span>
          <span class="todo-row__label">卡券库存预警</span>
          <strong>{{ stats.lowStockConfigCount }}</strong>
          <span class="todo-row__action">补充库存</span>
        </button>
        <button class="todo-row" @click="go('/connection')">
          <span class="status-dot" :class="accountIssueCount ? 'status-dot--red' : 'status-dot--green'"></span>
          <span class="todo-row__label">账号连接或凭证异常</span>
          <strong>{{ accountIssueCount }}</strong>
          <span class="todo-row__action">查看账号</span>
        </button>
      </div>
    </section>

    <div class="dashboard-columns">
      <section class="dashboard-panel dashboard-panel--chart">
        <div class="panel-heading">
          <div>
            <h2>近 7 日成功交付</h2>
            <p>{{ sevenDayOrderCount }} 笔订单，¥ {{ money(sevenDayRevenue) }}</p>
          </div>
        </div>
        <div class="trend-chart" aria-label="近七天成功交付订单趋势">
          <div v-for="item in recentTrend" :key="item.dateKey" class="trend-chart__item" :title="`${item.dateKey}：${item.orderCount} 笔，¥ ${money(item.revenue)}`">
            <strong>{{ item.orderCount }}</strong>
            <div class="trend-chart__track">
              <i :style="{ height: `${item.height}%` }"></i>
            </div>
            <span>{{ item.label }}</span>
          </div>
        </div>
      </section>

      <section class="dashboard-panel">
        <div class="panel-heading">
          <div>
            <h2>最近动态</h2>
            <p>最近 10 条系统操作记录</p>
          </div>
          <button class="text-button" @click="go('/operation-log')">查看全部</button>
        </div>
        <div v-if="activities.length" class="activity-list">
          <div v-for="(activity, index) in activities" :key="`${activity.createdAt || index}-${activity.content}`" class="activity-row">
            <span class="activity-dot" :class="activityStatusClass(activity.status)"></span>
            <div class="activity-row__content">
              <strong>{{ activity.content }}</strong>
              <small>{{ activity.accountName || '系统' }}{{ activity.module ? ` · ${activity.module}` : '' }}</small>
            </div>
            <time>{{ formatActivityTime(activity.createdAt) }}</time>
          </div>
        </div>
        <div v-else class="empty-state">暂时没有可展示的最近动态</div>
      </section>
    </div>

    <section class="dashboard-panel account-health-panel">
      <div class="panel-heading">
        <div>
          <h2>账号健康</h2>
          <p>实时连接状态；{{ healthyAccountCount }} 个账号运行正常，{{ accountIssueCount }} 个需要关注</p>
        </div>
        <button class="text-button" @click="go('/connection')">连接管理</button>
      </div>
      <div v-if="accountHealth.length" class="account-health-list">
        <button v-for="account in accountHealth" :key="account.accountId" class="account-health-row" :class="{ 'account-health-row--attention': account.needsAttention }" @click="goConnection(account.accountId)">
          <div class="account-health-row__main">
            <strong>{{ account.accountName || `账号 ${account.accountId}` }}</strong>
            <small>{{ account.healthText }}</small>
          </div>
          <div class="health-badges">
            <span class="health-badge" :class="account.cookieStatus === 1 ? 'health-badge--success' : 'health-badge--muted'">{{ account.cookieStatusText }}</span>
            <span class="health-badge" :class="account.websocketConnected ? 'health-badge--success' : (account.accountStatus === 0 ? 'health-badge--muted' : 'health-badge--warning')">
              {{ account.websocketConnected ? '实时连接正常' : '实时未连接' }}
            </span>
            <span v-if="account.automationRiskPaused" class="health-badge health-badge--danger">自动化已暂停</span>
            <span v-else class="health-badge health-badge--muted">{{ account.accountStatusText }}</span>
            <span v-if="account.unreadMessageCount" class="health-badge health-badge--blue">{{ account.unreadMessageCount }} 条未读</span>
          </div>
          <span class="account-health-row__action">处理</span>
        </button>
      </div>
      <div v-else class="empty-state">还没有添加闲鱼账号</div>
    </section>

    <section class="dashboard-panel quick-actions-panel">
      <div class="panel-heading">
        <div>
          <h2>常用操作</h2>
          <p>快速进入常用配置页面</p>
        </div>
      </div>
      <div class="quick-actions">
        <button class="button button--secondary" @click="go('/kami-config')">卡券管理</button>
        <button class="button button--secondary" @click="go('/auto-delivery')">发货配置</button>
        <button class="button button--secondary" @click="go('/auto-reply')">回复配置</button>
        <button class="button button--secondary" @click="go('/exception-center')">异常中心</button>
      </div>
    </section>
  </main>
</template>

<style scoped src="./dashboard.css"></style>

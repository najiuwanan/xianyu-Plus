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
  trends,
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
const dailyAverageOrderCount = computed(() => Math.round(sevenDayOrderCount.value / 7))

const todoRows = computed(() => [
  { label: '未读买家消息', detail: '买家消息等待人工查看与回复', count: Number(stats.unreadMessageCount || 0), action: '去处理', path: '/messages', tone: stats.unreadMessageCount ? 'danger' : 'success', icon: IconMessage },
  { label: '等待自动发货', detail: '已付款订单将由自动化任务持续处理', count: Number(stats.pendingTaskCount || 0), action: '查看订单', path: '/orders', tone: stats.pendingTaskCount ? 'warning' : 'success', icon: IconTruck },
  { label: '需要人工核对', detail: '请确认无法自动完成的订单或任务', count: Number(stats.reviewRequiredCount || 0), action: '去核对', path: '/exception-center', tone: stats.reviewRequiredCount ? 'warning' : 'success', icon: IconClipboard },
  { label: '自动化异常', detail: '发货、评价、小红花与擦亮失败记录', count: Number(automationExceptionCount.value || 0), action: '看原因', path: '/exception-center', tone: automationExceptionCount.value ? 'warning' : 'success', icon: IconAlert },
  { label: '卡券库存预警', detail: '库存不足可能影响后续自动发货', count: Number(stats.lowStockConfigCount || 0), action: '去补充', path: '/kami-config', tone: stats.lowStockConfigCount ? 'warning' : 'success', icon: IconPackage }
])

const money = (value: number) => Number(value || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const go = (path: string) => router.push(path)

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
        <span class="metric-card__icon metric-card__icon--amber"><IconAlert /></span>
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

    </section>

    <section class="dashboard-panel dashboard-panel--chart">
      <div class="panel-heading panel-heading--chart">
        <div><h2>近 7 日成功交付</h2><p>已完成 {{ sevenDayOrderCount }} 笔订单，成交 ¥{{ money(sevenDayRevenue) }}</p></div>
        <div class="chart-summary" aria-label="近七日交付汇总">
          <span><small>日均交付</small><strong>{{ dailyAverageOrderCount }} 笔</strong></span>
          <span><small>近 7 日成交额</small><strong>¥{{ money(sevenDayRevenue) }}</strong></span>
        </div>
      </div>
        <div class="chart-legend"><span><i></i>成功交付订单数</span><span>每根柱形代表对应日期的交付数量</span></div>
        <div class="trend-chart" aria-label="近七天成功交付订单趋势">
          <div v-for="item in recentTrend" :key="item.dateKey" class="trend-chart__item" :title="`${item.dateKey}：${item.orderCount} 笔，¥ ${money(item.revenue)}`">
            <strong>{{ item.orderCount }}</strong><div class="trend-chart__track"><i :style="{ height: `${item.height}%` }"></i></div><span>{{ item.label }}</span>
          </div>
        </div>
    </section>
  </div>
</template>

<style scoped src="./dashboard.css"></style>

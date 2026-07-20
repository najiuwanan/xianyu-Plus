<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import IconAlert from '@/components/icons/IconAlert.vue'
import IconClipboard from '@/components/icons/IconClipboard.vue'
import IconMessage from '@/components/icons/IconMessage.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconTruck from '@/components/icons/IconTruck.vue'
import { useDashboard } from './useDashboard'

const router = useRouter()
const {
  loading,
  stats,
  trends,
  automationExceptionCount,
  loadStatistics
} = useDashboard()

const exceptionCount = computed(() => Number(automationExceptionCount.value || 0))
const trendDays = ref<7 | 30>(7)
const trendTitle = computed(() => `近 ${trendDays.value} 日成功交付`)
const trendAriaLabel = computed(() => `近 ${trendDays.value} 天成功交付订单趋势`)

const recentTrend = computed(() => {
  const pointByDate = new Map(trends.value.map(item => [item.dateKey, item]))
  const days = Array.from({ length: trendDays.value }, (_, index) => {
    const date = new Date()
    date.setHours(0, 0, 0, 0)
    date.setDate(date.getDate() - (trendDays.value - 1 - index))
    const dateKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    const source = pointByDate.get(dateKey)
    return {
      dateKey,
      label: `${date.getMonth() + 1}/${date.getDate()}`,
      showLabel: trendDays.value === 7 || index === 0 || index === trendDays.value - 1 || index % 5 === 0,
      orderCount: Number(source?.orderCount || 0),
      revenue: Number(source?.revenue || 0)
    }
  })
  const max = Math.max(...days.map(item => item.orderCount), 1)
  return days.map(item => ({
    ...item,
    ratio: item.orderCount / max
  }))
})

const chartPoints = computed(() => {
  const total = recentTrend.value.length
  return recentTrend.value.map((item, index) => ({
    ...item,
    x: total <= 1 ? 50 : 1 + (index / (total - 1)) * 98,
    y: 82 - item.ratio * 66
  }))
})

const smoothPath = (points: Array<{ x: number; y: number }>) => {
  const first = points[0]
  if (!first) return ''
  return points.slice(1).reduce((path, point, index) => {
    const previous = points[index]!
    const controlX = (previous.x + point.x) / 2
    return `${path} C ${controlX} ${previous.y}, ${controlX} ${point.y}, ${point.x} ${point.y}`
  }, `M ${first.x} ${first.y}`)
}

const trendLinePath = computed(() => smoothPath(chartPoints.value))
const trendAreaPath = computed(() => {
  const points = chartPoints.value
  const first = points[0]
  const last = points[points.length - 1]
  if (!first || !last) return ''
  return `${smoothPath(points)} L ${last.x} 82 L ${first.x} 82 Z`
})

const deliveryOrderCount = computed(() => recentTrend.value.reduce((sum, item) => sum + item.orderCount, 0))
const deliveryRevenue = computed(() => recentTrend.value.reduce((sum, item) => sum + item.revenue, 0))
const dailyAverageOrderCount = computed(() => Math.round(deliveryOrderCount.value / trendDays.value))

const money = (value: number) => Number(value || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const go = (path: string) => router.push(path)

onMounted(() => {
  loadStatistics()
})
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
        <span class="metric-card__icon metric-card__icon--blue"><IconClipboard /></span>
        <div><span>今日订单数</span><strong>{{ stats.todayOrderCount }}</strong><small>按闲鱼下单时间统计</small></div>
      </article>
      <article class="metric-card">
        <span class="metric-card__icon metric-card__icon--purple">Σ</span>
        <div><span>总订单数</span><strong>{{ stats.totalOrderCount }}</strong><small>当前已同步订单总数</small></div>
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
        <div><span>自动化异常</span><strong>{{ exceptionCount }}</strong><small>发货、评价、求花与擦亮失败</small></div>
      </article>
    </section>

    <section class="dashboard-panel dashboard-panel--chart">
      <div class="panel-heading panel-heading--chart">
        <div><h2>{{ trendTitle }}</h2><p>已完成 {{ deliveryOrderCount }} 笔订单，成交 ¥{{ money(deliveryRevenue) }}</p></div>
        <div class="chart-actions">
          <div class="trend-period-toggle" role="group" aria-label="交付统计周期">
            <button type="button" :class="{ 'is-active': trendDays === 7 }" @click="trendDays = 7">近 7 日</button>
            <button type="button" :class="{ 'is-active': trendDays === 30 }" @click="trendDays = 30">近 30 日</button>
          </div>
          <div class="chart-summary" :aria-label="`${trendTitle}汇总`">
            <span><small>日均交付</small><strong>{{ dailyAverageOrderCount }} 笔</strong></span>
            <span><small>{{ trendTitle }}成交额</small><strong>¥{{ money(deliveryRevenue) }}</strong></span>
          </div>
        </div>
      </div>
        <div class="chart-legend"><span><i></i>成功交付订单趋势</span><span>{{ trendDays === 30 ? '30 日视图每 5 日显示一个日期刻度' : '悬停折线节点可查看当日订单与成交额' }}</span></div>
        <div class="trend-line-chart" :class="{ 'trend-line-chart--30': trendDays === 30 }" :aria-label="trendAriaLabel">
          <svg class="trend-line-chart__svg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
            <defs>
              <linearGradient id="deliveryTrendArea" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#ffbd16" stop-opacity=".32" />
                <stop offset="100%" stop-color="#ffbd16" stop-opacity=".02" />
              </linearGradient>
            </defs>
            <line v-for="gridY in [16, 38, 60, 82]" :key="gridY" x1="1" :y1="gridY" x2="99" :y2="gridY" class="trend-line-chart__grid" />
            <path :d="trendAreaPath" class="trend-line-chart__area" />
            <path :d="trendLinePath" class="trend-line-chart__line" />
          </svg>
          <button
            v-for="point in chartPoints"
            :key="point.dateKey"
            type="button"
            class="trend-line-chart__point"
            :style="{ left: `${point.x}%`, top: `calc((100% - 28px) * ${point.y / 100})` }"
            :aria-label="`${point.dateKey}，成功交付 ${point.orderCount} 笔，成交额 ${money(point.revenue)} 元`"
          >
            <strong v-if="trendDays === 7 || point.orderCount > 0">{{ point.orderCount }}</strong>
            <i></i>
            <span class="trend-line-chart__tooltip"><b>{{ point.dateKey }}</b><em>{{ point.orderCount }} 笔订单</em><em>¥{{ money(point.revenue) }}</em></span>
          </button>
          <div class="trend-line-chart__labels" :style="{ gridTemplateColumns: `repeat(${recentTrend.length}, minmax(0, 1fr))` }">
            <span v-for="item in recentTrend" :key="item.dateKey" :class="{ 'is-hidden': !item.showLabel }">{{ item.label }}</span>
          </div>
        </div>
    </section>
  </div>
</template>

<style scoped src="./dashboard.css"></style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import IconAlert from '@/components/icons/IconAlert.vue'
import IconClipboard from '@/components/icons/IconClipboard.vue'
import IconMessage from '@/components/icons/IconMessage.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconTruck from '@/components/icons/IconTruck.vue'
import type { DeliveryRecordVO } from '@/api/order'
import type { Account } from '@/types'
import { useDashboard } from './useDashboard'

const router = useRouter()
const {
  loading,
  stats,
  trends,
  automationExceptionCount,
  accounts,
  pendingOrders,
  pendingOrderCount,
  loadStatistics
} = useDashboard()

const exceptionCount = computed(() => Number(automationExceptionCount.value || 0))
const trendDays = ref<7 | 30>(7)
const trendTitle = computed(() => `近 ${trendDays.value} 日成功交付`)
const trendAriaLabel = computed(() => `近 ${trendDays.value} 天成功交付订单趋势`)
const accountById = computed(() => new Map(accounts.value.map(account => [account.id, account])))
const visibleAccounts = computed(() => accounts.value.slice(0, 3))

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
  return days.map(item => ({ ...item, ratio: item.orderCount / max }))
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

const reminders = computed(() => [
  { key: 'orders', count: pendingOrderCount.value, text: '笔商家待处理订单', tone: 'yellow', path: '/orders' },
  { key: 'messages', count: Number(stats.unreadMessageCount || 0), text: '条未读买家消息', tone: 'blue', path: '/messages' },
  { key: 'review', count: Number(stats.reviewRequiredCount || 0), text: '项任务等待人工复核', tone: 'red', path: '/order-automation' },
  { key: 'exceptions', count: exceptionCount.value, text: '项自动化异常需要处理', tone: 'red', path: '/order-automation' },
  { key: 'kami', count: Number(stats.lowStockConfigCount || 0), text: '个商品卡密库存不足', tone: 'gray', path: '/kami-config' }
].filter(item => item.count > 0))

const money = (value: number) => Number(value || 0).toLocaleString('zh-CN', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
})

const accountLabel = (accountId?: number) => {
  const account = accountId ? accountById.value.get(accountId) : undefined
  return account?.accountNote || account?.unb || (accountId ? `账号 ${accountId}` : '未关联账号')
}

const accountIdentity = (account: Account) => `UNB：${account.unb || '-'} · ID：${account.id}`
const accountState = (account: Account) => {
  if (account.automationRiskPaused === 1) return { text: '风控暂停', tone: 'warning' }
  if (account.status === 1) return { text: '正常', tone: 'success' }
  return { text: '未启用', tone: 'muted' }
}

const orderState = (order: DeliveryRecordVO) => {
  const deliveryChannel = (order.deliveryChannel || '').toUpperCase()
  const deliveryStatus = (order.deliveryStatus || '').toUpperCase()
  if (deliveryChannel === 'PICKUP') return { text: '自提待交付', tone: 'muted' }
  if (deliveryStatus === 'REVIEW_REQUIRED') return { text: '待人工核对', tone: 'warning' }
  if (deliveryStatus === 'FAILED' || order.state === -1) return { text: '发货异常', tone: 'danger' }
  if (deliveryStatus === 'PROCESSING' || deliveryStatus === 'RETRY_WAIT') return { text: '发货处理中', tone: 'warning' }
  return { text: '待发货', tone: 'warning' }
}

const orderDescription = (order: DeliveryRecordVO) => order.skuName ? `${order.goodsTitle || '商品信息同步中'} · ${order.skuName}` : (order.goodsTitle || '商品信息同步中')
const go = (path: string) => router.push(path)
const goOrder = (order: DeliveryRecordVO) => router.push({ path: '/orders', query: order.xianyuAccountId ? { accountId: String(order.xianyuAccountId) } : {} })

onMounted(() => {
  loadStatistics()
})
</script>

<template>
  <div class="merchant-dashboard" :aria-busy="loading">
    <header class="merchant-dashboard__header">
      <div>
        <h1>运营总览</h1>
        <p>优先处理商家需要动作的订单、消息与异常。</p>
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
        <div><span>今日成交额</span><strong>¥{{ money(stats.todayRevenue) }}</strong><small>已完成交付订单的金额</small></div>
      </article>
      <article class="metric-card metric-card--action" @click="go('/orders')">
        <span class="metric-card__icon metric-card__icon--amber"><IconClipboard /></span>
        <div><span>待处理订单</span><strong>{{ pendingOrderCount }}</strong><small>待发货、自提待交付、异常或人工介入</small></div>
      </article>
      <article class="metric-card metric-card--action" @click="go('/messages')">
        <span class="metric-card__icon metric-card__icon--blue"><IconMessage /></span>
        <div><span>未读消息</span><strong>{{ stats.unreadMessageCount }}</strong><small>需要人工查看的买家消息</small></div>
      </article>
      <article class="metric-card metric-card--action" @click="go('/order-automation')">
        <span class="metric-card__icon metric-card__icon--red"><IconAlert /></span>
        <div><span>异常提醒</span><strong>{{ exceptionCount }}</strong><small>自动化执行中的异常与重试</small></div>
      </article>
    </section>

    <section class="dashboard-content-grid" aria-label="商家待办与账号状态">
      <article class="dashboard-panel dashboard-panel--orders">
        <div class="panel-heading">
          <div><h2>待处理订单 <small>({{ pendingOrderCount }})</small></h2><p>仅展示已同步的商家待办；买家待付款不会计入。</p></div>
          <button class="button button--compact" type="button" @click="go('/orders')">查看全部</button>
        </div>
        <div v-if="pendingOrders.length" class="todo-table-wrap">
          <table class="todo-table">
            <thead><tr><th>买家</th><th>商品信息</th><th>账号</th><th>订单状态</th><th>金额</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="order in pendingOrders" :key="order.id">
                <td><strong>{{ order.buyerUserName || '买家信息同步中' }}</strong><small>{{ order.orderCreateTime || order.createTime || '-' }}</small></td>
                <td><strong class="todo-table__goods">{{ orderDescription(order) }}</strong></td>
                <td><span class="account-chip">{{ accountLabel(order.xianyuAccountId) }}</span></td>
                <td><span class="status-chip" :class="`status-chip--${orderState(order).tone}`">{{ orderState(order).text }}</span></td>
                <td><strong>¥{{ order.totalPrice || '-' }}</strong></td>
                <td><button class="table-action" type="button" @click="goOrder(order)">去处理</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="dashboard-empty"><IconClipboard /><strong>当前没有待处理订单</strong><span>新的商家待办会在同步后显示在这里。</span></div>
      </article>

      <aside class="dashboard-side-column">
        <article class="dashboard-panel dashboard-panel--accounts">
          <div class="panel-heading"><div><h2>账号状态</h2><p>使用账号备注区分多账号。</p></div><button class="dashboard-link" type="button" @click="go('/accounts')">管理账号</button></div>
          <div v-if="visibleAccounts.length" class="account-status-list">
            <button v-for="account in visibleAccounts" :key="account.id" type="button" class="account-status-item" @click="go('/accounts')">
              <span class="account-avatar">{{ (account.accountNote || account.unb || '账').slice(0, 1) }}</span>
              <span class="account-status-item__main"><strong>{{ account.accountNote || '添加备注' }}</strong><small>{{ accountIdentity(account) }}</small></span>
              <span class="account-state" :class="`account-state--${accountState(account).tone}`">{{ accountState(account).text }}</span>
            </button>
          </div>
          <div v-else class="side-empty">尚未接入账号</div>
          <button v-if="accounts.length > visibleAccounts.length" class="accounts-more" type="button" @click="go('/accounts')">查看全部账号（{{ accounts.length }}）</button>
        </article>

        <article class="dashboard-panel dashboard-panel--reminders">
          <div class="panel-heading"><div><h2>今日提醒</h2><p>真实待办与库存风险汇总。</p></div><button class="dashboard-link" type="button" @click="go('/order-automation')">查看更多</button></div>
          <div v-if="reminders.length" class="reminder-list">
            <button v-for="item in reminders" :key="item.key" type="button" class="reminder-item" @click="go(item.path)">
              <i :class="`reminder-dot reminder-dot--${item.tone}`"></i><span>有 {{ item.count }} {{ item.text }}</span><strong>去处理</strong>
            </button>
          </div>
          <div v-else class="side-empty">当前没有需要处理的提醒</div>
        </article>
      </aside>
    </section>

    <section class="dashboard-panel dashboard-panel--chart">
      <div class="panel-heading panel-heading--chart">
        <div><h2>{{ trendTitle }}</h2><p>已完成 {{ deliveryOrderCount }} 笔订单，成交 ¥{{ money(deliveryRevenue) }}</p></div>
        <div class="chart-actions">
          <div class="trend-period-toggle" role="group" aria-label="交付统计周期">
            <button type="button" :class="{ 'is-active': trendDays === 7 }" @click="trendDays = 7">近 7 日</button>
            <button type="button" :class="{ 'is-active': trendDays === 30 }" @click="trendDays = 30">近 30 日</button>
          </div>
          <div class="chart-summary" :aria-label="`${trendTitle}汇总`"><span><small>日均交付</small><strong>{{ dailyAverageOrderCount }} 笔</strong></span><span><small>{{ trendTitle }}成交额</small><strong>¥{{ money(deliveryRevenue) }}</strong></span></div>
        </div>
      </div>
      <div class="chart-legend"><span><i></i>成功交付订单趋势</span><span>{{ trendDays === 30 ? '30 日视图每 5 日显示一个日期刻度' : '悬停折线节点可查看当日订单与成交额' }}</span></div>
      <div class="trend-line-chart" :class="{ 'trend-line-chart--30': trendDays === 30 }" :aria-label="trendAriaLabel">
        <svg class="trend-line-chart__svg" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true"><defs><linearGradient id="deliveryTrendArea" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#FFDA44" stop-opacity=".32" /><stop offset="100%" stop-color="#FFDA44" stop-opacity=".02" /></linearGradient></defs><line v-for="gridY in [16, 38, 60, 82]" :key="gridY" x1="1" :y1="gridY" x2="99" :y2="gridY" class="trend-line-chart__grid" /><path :d="trendAreaPath" class="trend-line-chart__area" /><path :d="trendLinePath" class="trend-line-chart__line" /></svg>
        <button v-for="point in chartPoints" :key="point.dateKey" type="button" class="trend-line-chart__point" :style="{ left: `${point.x}%`, top: `calc((100% - 28px) * ${point.y / 100})` }" :aria-label="`${point.dateKey}，成功交付 ${point.orderCount} 笔，成交额 ${money(point.revenue)} 元`"><strong v-if="trendDays === 7 || point.orderCount > 0">{{ point.orderCount }}</strong><i></i><span class="trend-line-chart__tooltip"><b>{{ point.dateKey }}</b><em>{{ point.orderCount }} 笔订单</em><em>¥{{ money(point.revenue) }}</em></span></button>
        <div class="trend-line-chart__labels" :style="{ gridTemplateColumns: `repeat(${recentTrend.length}, minmax(0, 1fr))` }"><span v-for="item in recentTrend" :key="item.dateKey" :class="{ 'is-hidden': !item.showLabel }">{{ item.label }}</span></div>
      </div>
    </section>
  </div>
</template>

<style scoped src="./dashboard.css"></style>
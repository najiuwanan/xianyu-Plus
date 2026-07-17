<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSystemCheckOverview, type SystemCheckItem, type SystemCheckOverview } from '@/api/system-check'
import { toast } from '@/utils/toast'

const router = useRouter()
const loading = ref(false)
const overview = ref<SystemCheckOverview | null>(null)

const checks = computed<SystemCheckItem[]>(() => overview.value?.items || [])
const checkedAt = computed(() => {
  if (!overview.value?.generatedAt) return '尚未检查'
  return String(overview.value.generatedAt).replace('T', ' ').slice(0, 19)
})

const load = async () => {
  loading.value = true
  try {
    const result = await getSystemCheckOverview()
    overview.value = result.data || null
  } catch (error: any) {
    if (!error?.messageShown) toast.error(error?.message || '系统自检失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const goTo = (item: SystemCheckItem) => {
  if (item.path) router.push(item.path)
}

onMounted(load)
</script>

<template>
  <main class="system-check">
    <section class="system-check__hero">
      <div>
        <h1>系统自检</h1>
        <p>快速查看账号连接、AI、通知渠道、卡券库存和自动化任务是否已就绪。</p>
      </div>
      <div class="system-check__actions">
        <span>检查时间：{{ checkedAt }}</span>
        <button type="button" class="system-check__refresh" :disabled="loading" @click="load">
          {{ loading ? '检查中…' : '重新检查' }}
        </button>
      </div>
    </section>

    <section class="system-check__summary" v-if="overview">
      <article class="summary-card summary-card--pass"><strong>{{ overview.passCount }}</strong><span>正常</span></article>
      <article class="summary-card summary-card--warn"><strong>{{ overview.warnCount }}</strong><span>需要关注</span></article>
      <article class="summary-card summary-card--fail"><strong>{{ overview.failCount }}</strong><span>异常</span></article>
    </section>

    <section class="system-check__grid" v-if="!loading || checks.length">
      <button v-for="item in checks" :key="item.id" class="check-card" :class="`check-card--${item.status.toLowerCase()}`" @click="goTo(item)">
        <span class="check-card__status">{{ item.status === 'PASS' ? '✓' : item.status === 'WARN' ? '!' : '×' }}</span>
        <span class="check-card__body">
          <span class="check-card__head"><strong>{{ item.title }}</strong><em>{{ item.status === 'PASS' ? '正常' : item.status === 'WARN' ? '需关注' : '异常' }}</em></span>
          <span class="check-card__summary">{{ item.summary }}</span>
          <span class="check-card__detail">{{ item.detail }}</span>
        </span>
        <span class="check-card__arrow">›</span>
      </button>
    </section>
    <div v-else class="system-check__loading">正在检查系统状态…</div>
  </main>
</template>

<style scoped>
.system-check { max-width: 1280px; margin: 0 auto; padding: 28px 30px 48px; color: #1d2939; }
.system-check__hero { display:flex; align-items:flex-start; justify-content:space-between; gap:20px; margin-bottom:22px; }
h1 { margin:0; font-size:28px; letter-spacing:.01em; } p { margin:8px 0 0; color:#667085; font-size:14px; }
.system-check__actions { display:flex; align-items:center; gap:12px; color:#98a2b3; font-size:13px; white-space:nowrap; }
.system-check__refresh { border:1px solid #cbd5e1; border-radius:8px; padding:9px 16px; background:#fff; color:#1565d8; cursor:pointer; font-weight:600; }
.system-check__refresh:disabled { opacity:.6; cursor:wait; }
.system-check__summary { display:grid; grid-template-columns:repeat(3, minmax(0,1fr)); gap:16px; margin-bottom:18px; }
.summary-card { display:flex; align-items:baseline; gap:8px; padding:16px 20px; border:1px solid #eaecf0; border-radius:12px; background:#fff; box-shadow:0 1px 2px rgba(16,24,40,.03); }
.summary-card strong { font-size:28px; }.summary-card span { color:#667085; font-size:14px; }.summary-card--pass strong{color:#12b76a}.summary-card--warn strong{color:#f79009}.summary-card--fail strong{color:#f04438}
.system-check__grid { display:grid; grid-template-columns:repeat(2, minmax(0,1fr)); gap:16px; }
.check-card { display:flex; width:100%; gap:14px; align-items:flex-start; text-align:left; padding:18px; border:1px solid #eaecf0; border-radius:12px; background:#fff; cursor:pointer; transition:.18s ease; }
.check-card:hover { transform:translateY(-1px); box-shadow:0 8px 20px rgba(16,24,40,.08); border-color:#b2ddff; }.check-card__status { display:grid; place-items:center; width:30px; height:30px; border-radius:50%; font-size:18px; font-weight:700; flex:0 0 auto; }.check-card--pass .check-card__status{background:#dcfae6;color:#12b76a}.check-card--warn .check-card__status{background:#fef0c7;color:#dc6803}.check-card--fail .check-card__status{background:#fee4e2;color:#d92d20}
.check-card__body{display:flex; flex:1; min-width:0; flex-direction:column; gap:6px}.check-card__head{display:flex; justify-content:space-between; gap:10px}.check-card__head strong{font-size:16px}.check-card__head em{font-style:normal; color:#667085; font-size:12px}.check-card__summary{font-size:14px;color:#344054}.check-card__detail{font-size:12px;line-height:1.5;color:#98a2b3}.check-card__arrow{font-size:26px;line-height:26px;color:#98a2b3}.system-check__loading{padding:70px 0;text-align:center;color:#98a2b3}
@media(max-width:760px){.system-check{padding:20px 16px}.system-check__hero{flex-direction:column}.system-check__actions{width:100%;justify-content:space-between}.system-check__summary,.system-check__grid{grid-template-columns:1fr}.summary-card{padding:12px 16px}}
</style>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { getAccountList } from '@/api/account'
import { deleteBuyerBlacklist, getBuyerBlacklist, saveBuyerBlacklist, type BuyerBlacklistEntry } from '@/api/blacklist'
import type { Account } from '@/types'
import { showError, showSuccess } from '@/utils'

const accounts = ref<Account[]>([])
const entries = ref<BuyerBlacklistEntry[]>([])
const loading = ref(false)
const saving = ref(false)
const showDialog = ref(false)
const keyword = ref('')
const accountFilter = ref<number | undefined>()
const form = reactive({
  xianyuAccountId: null as number | null,
  buyerUserId: '',
  buyerUserName: '',
  reason: '',
  enabled: 1
})

const load = async () => {
  loading.value = true
  try {
    const response = await getBuyerBlacklist({ xianyuAccountId: accountFilter.value, keyword: keyword.value.trim() || undefined })
    entries.value = response.data || []
  } catch (error: any) {
    showError(error.message || '加载黑名单失败')
  } finally {
    loading.value = false
  }
}

const loadAccounts = async () => {
  const response = await getAccountList()
  accounts.value = response.data?.accounts || []
}

const openCreate = () => {
  Object.assign(form, { xianyuAccountId: null, buyerUserId: '', buyerUserName: '', reason: '', enabled: 1 })
  showDialog.value = true
}

const save = async () => {
  if (!form.buyerUserId.trim()) {
    showError('请输入买家 ID')
    return
  }
  saving.value = true
  try {
    await saveBuyerBlacklist({
      xianyuAccountId: form.xianyuAccountId,
      buyerUserId: form.buyerUserId.trim(),
      buyerUserName: form.buyerUserName.trim(),
      reason: form.reason.trim(),
      enabled: form.enabled
    })
    showDialog.value = false
    showSuccess('黑名单已生效，相关自动回复和发货将立即停止')
    await load()
  } catch (error: any) {
    showError(error.message || '保存黑名单失败')
  } finally {
    saving.value = false
  }
}

const remove = async (entry: BuyerBlacklistEntry) => {
  if (!window.confirm(`确定解除买家 ${entry.buyerUserName || entry.buyerUserId} 的黑名单吗？`)) return
  try {
    await deleteBuyerBlacklist(entry.id)
    showSuccess('已解除黑名单')
    await load()
  } catch (error: any) {
    showError(error.message || '解除黑名单失败')
  }
}

const toggle = async (entry: BuyerBlacklistEntry) => {
  try {
    await saveBuyerBlacklist({
      xianyuAccountId: entry.xianyuAccountId,
      buyerUserId: entry.buyerUserId,
      buyerUserName: entry.buyerUserName,
      reason: entry.reason,
      enabled: entry.enabled === 1 ? 0 : 1
    })
    await load()
  } catch (error: any) {
    showError(error.message || '更新状态失败')
  }
}

onMounted(async () => {
  await Promise.all([loadAccounts(), load()])
})
</script>

<template>
  <main class="blacklist-page">
    <header class="blacklist-header">
      <div><h1>买家黑名单</h1><p>黑名单会同时拦截关键词回复、AI 回复、自动发货与人工补发。</p></div>
      <button class="primary" @click="openCreate">＋ 添加黑名单</button>
    </header>

    <section class="guard-card">
      <strong>四层安全拦截</strong>
      <span>消息进入</span><i>→</i><span>延时回复</span><i>→</i><span>订单任务</span><i>→</i><span>卡密发送</span>
    </section>

    <section class="panel">
      <div class="toolbar">
        <select v-model="accountFilter" @change="load">
          <option :value="undefined">所有账号范围</option>
          <option v-for="account in accounts" :key="account.id" :value="Number(account.id)">{{ account.accountNote || account.unb }}</option>
        </select>
        <input v-model="keyword" placeholder="搜索买家 ID、昵称或拉黑原因" @keyup.enter="load">
        <button @click="load">查询</button>
      </div>

      <div v-if="loading" class="empty">正在加载…</div>
      <div v-else-if="!entries.length" class="empty"><strong>暂无黑名单</strong><span>添加后立即对新消息、排队任务和手动发货生效。</span></div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>买家</th><th>适用账号</th><th>原因</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="entry in entries" :key="entry.id">
              <td><strong>{{ entry.buyerUserName || '未记录昵称' }}</strong><small>ID：{{ entry.buyerUserId }}</small></td>
              <td><span class="scope" :class="{ global: !entry.xianyuAccountId }">{{ entry.xianyuAccountId ? (entry.accountNote || `账号 ${entry.xianyuAccountId}`) : '所有账号' }}</span></td>
              <td>{{ entry.reason || '未填写原因' }}</td>
              <td><button class="status" :class="{ off: entry.enabled !== 1 }" @click="toggle(entry)">{{ entry.enabled === 1 ? '拦截中' : '已停用' }}</button></td>
              <td>{{ entry.updateTime || entry.createTime || '-' }}</td>
              <td><button class="danger" @click="remove(entry)">解除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="showDialog" class="mask" @click.self="showDialog = false">
      <form class="dialog" @submit.prevent="save">
        <header><div><h2>添加买家黑名单</h2><p>建议使用买家 ID 精确拉黑，昵称仅用于识别。</p></div><button type="button" @click="showDialog = false">×</button></header>
        <label>适用范围<select v-model="form.xianyuAccountId"><option :value="null">所有账号（推荐）</option><option v-for="account in accounts" :key="account.id" :value="Number(account.id)">{{ account.accountNote || account.unb }}</option></select></label>
        <label>买家 ID<input v-model="form.buyerUserId" maxlength="100" placeholder="必填，例如 2217263933060"></label>
        <label>买家昵称<input v-model="form.buyerUserName" maxlength="200" placeholder="选填，方便人工识别"></label>
        <label>拉黑原因<textarea v-model="form.reason" maxlength="500" rows="4" placeholder="例如：恶意下单、欺诈风险、重复骚扰"></textarea></label>
        <footer><button type="button" @click="showDialog = false">取消</button><button class="primary" :disabled="saving" type="submit">{{ saving ? '保存中…' : '确认拉黑' }}</button></footer>
      </form>
    </div>
  </main>
</template>

<style scoped>
.blacklist-page{padding:28px 32px;color:#1f2937}.blacklist-header{display:flex;align-items:center;justify-content:space-between;margin-bottom:18px}.blacklist-header h1{margin:0;font-size:25px}.blacklist-header p,.dialog p{margin:5px 0 0;color:#7b8494;font-size:13px}.primary{border:0;border-radius:10px;background:#1677ff;color:#fff;padding:10px 17px;font-weight:700;cursor:pointer}.guard-card{display:flex;align-items:center;gap:12px;margin-bottom:16px;padding:16px 20px;border:1px solid #ffd9d5;border-radius:14px;background:#fff8f7;color:#9f2e26}.guard-card strong{margin-right:auto}.guard-card span{padding:6px 10px;border-radius:999px;background:#fff}.guard-card i{font-style:normal}.panel{min-height:420px;border:1px solid #e8ebf0;border-radius:16px;background:#fff;box-shadow:0 8px 28px rgba(31,41,55,.04)}.toolbar{display:flex;gap:10px;padding:16px;border-bottom:1px solid #eef0f3}.toolbar select,.toolbar input,.dialog input,.dialog select,.dialog textarea{box-sizing:border-box;border:1px solid #dfe3e9;border-radius:9px;background:#fff;padding:9px 11px;font:inherit}.toolbar select{width:190px}.toolbar input{flex:1;max-width:420px}.toolbar button,.dialog footer button{border:1px solid #d9e0e8;border-radius:9px;background:#fff;padding:9px 16px;cursor:pointer}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse}th,td{padding:14px 16px;border-bottom:1px solid #f0f1f3;text-align:left;font-size:13px}th{color:#667085;background:#fbfbfc}td strong,td small{display:block}td small{margin-top:4px;color:#98a2b3}.scope{display:inline-flex;padding:4px 9px;border-radius:999px;background:#eef5ff;color:#2468b4}.scope.global{background:#fff0d6;color:#9a5b00}.status,.danger{border:0;border-radius:8px;padding:6px 10px;cursor:pointer}.status{background:#e7f8ee;color:#16864b}.status.off{background:#f0f1f3;color:#7b8494}.danger{background:#fff1f0;color:#d92d20}.empty{display:flex;min-height:330px;align-items:center;justify-content:center;flex-direction:column;gap:8px;color:#98a2b3}.mask{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;background:rgba(15,23,42,.38)}.dialog{width:min(500px,calc(100vw - 32px));padding:22px;border-radius:16px;background:#fff;box-shadow:0 24px 70px rgba(15,23,42,.25)}.dialog header,.dialog footer{display:flex;align-items:center;justify-content:space-between}.dialog h2{margin:0}.dialog header>button{border:0;background:none;font-size:25px;cursor:pointer}.dialog label{display:grid;gap:7px;margin-top:15px;color:#475467;font-size:13px;font-weight:600}.dialog textarea{resize:vertical}.dialog footer{justify-content:flex-end;gap:10px;margin-top:20px}.dialog footer .primary{border:0;background:#d92d20}@media(max-width:800px){.blacklist-page{padding:18px}.blacklist-header,.guard-card,.toolbar{align-items:stretch;flex-direction:column}.guard-card i{display:none}.table-wrap{overflow-x:auto}table{min-width:850px}}
</style>

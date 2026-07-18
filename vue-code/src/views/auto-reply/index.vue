<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAccountList } from '@/api/account'
import { getGoodsList, type GoodsItem } from '@/api/goods'
import {
  addKeywordContent,
  addKeywordRule,
  deleteKeywordContent,
  deleteKeywordRule,
  ensureFallbackRule,
  getKeywordReplyRules,
  type KeywordReplyContent,
  type KeywordReplyRule,
  updateKeyword,
  updateKeywordContent,
  updateKeywordRuleMatchMode
} from '@/api/keywordReply'
import { showError, showSuccess } from '@/utils'
import type { Account } from '@/types'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const accounts = ref<Account[]>([])
const goods = ref<GoodsItem[]>([])
const rules = ref<KeywordReplyRule[]>([])
const selectedAccountId = ref<number | null>(null)
const selectedGoodsId = ref<string>('')

const ruleDialogVisible = ref(false)
const ruleDialogMode = ref<'create' | 'edit'>('create')
const editingRule = ref<KeywordReplyRule | null>(null)
const ruleKeyword = ref('')
const ruleMatchMode = ref(0)

const contentDialogVisible = ref(false)
const editingContent = ref<KeywordReplyContent | null>(null)
const contentRuleId = ref<string | number | null>(null)
const replyText = ref('')
const replyImageUrl = ref('')

const selectedGoods = computed(() =>
  goods.value.find((item) => String(item.xyGoodId) === selectedGoodsId.value)
)

const selectedAccount = computed(() =>
  accounts.value.find((account) => account.id === selectedAccountId.value)
)

const scopeDescription = computed(() => {
  if (!selectedGoods.value) return '请选择一个商品后管理其关键词回复。'
  return `当前规则仅作用于「${selectedGoods.value.title || selectedGoods.value.xyGoodId}」。未命中关键词时，将继续按该商品的 AI 与固定资料配置处理。`
})

const matchModeText = (mode?: number) => {
  if (mode === 1) return '精确匹配'
  if (mode === 2) return '开头匹配'
  return '包含匹配'
}

const syncQuery = () => {
  void router.replace({
    path: '/auto-reply',
    query: {
      accountId: selectedAccountId.value ? String(selectedAccountId.value) : undefined,
      goodsId: selectedGoodsId.value || undefined
    }
  })
}

const loadRules = async () => {
  if (!selectedAccountId.value || !selectedGoodsId.value) {
    rules.value = []
    return
  }
  loading.value = true
  try {
    const response = await getKeywordReplyRules({
      xianyuAccountId: selectedAccountId.value,
      xyGoodsId: selectedGoodsId.value
    })
    rules.value = response.data || []
  } catch (error) {
    rules.value = []
    showError('获取关键词规则失败')
  } finally {
    loading.value = false
  }
}

const loadGoods = async (preferredGoodsId?: string) => {
  if (!selectedAccountId.value) {
    goods.value = []
    selectedGoodsId.value = ''
    return
  }
  loading.value = true
  try {
    const response = await getGoodsList({
      xianyuAccountId: selectedAccountId.value,
      pageNum: 1,
      pageSize: 500
    })
    goods.value = response.data?.itemsWithConfig?.map((item) => item.item) || []
    const expectedId = preferredGoodsId || String(route.query.goodsId || '')
    const exists = goods.value.some((item) => String(item.xyGoodId) === expectedId)
    selectedGoodsId.value = exists ? expectedId : String(goods.value[0]?.xyGoodId || '')
    syncQuery()
    await loadRules()
  } catch (error) {
    goods.value = []
    selectedGoodsId.value = ''
    rules.value = []
    showError('获取商品列表失败')
  } finally {
    loading.value = false
  }
}

const loadAccounts = async () => {
  loading.value = true
  try {
    const response = await getAccountList()
    accounts.value = response.data?.accounts || []
    const accountFromQuery = Number(route.query.accountId)
    const validAccount = accounts.value.find((item) => item.id === accountFromQuery)
    selectedAccountId.value = validAccount?.id || accounts.value[0]?.id || null
    await loadGoods(String(route.query.goodsId || ''))
  } catch (error) {
    showError('获取账号列表失败')
  } finally {
    loading.value = false
  }
}

const handleAccountChange = async () => {
  await loadGoods()
}

const handleGoodsChange = async () => {
  syncQuery()
  await loadRules()
}

const openCreateRule = () => {
  if (!selectedAccountId.value || !selectedGoodsId.value) {
    showError('请先选择账号和商品')
    return
  }
  ruleDialogMode.value = 'create'
  editingRule.value = null
  ruleKeyword.value = ''
  ruleMatchMode.value = 0
  ruleDialogVisible.value = true
}

const openEditRule = (rule: KeywordReplyRule) => {
  ruleDialogMode.value = 'edit'
  editingRule.value = rule
  ruleKeyword.value = rule.keyword
  ruleMatchMode.value = rule.matchMode || 0
  ruleDialogVisible.value = true
}

const saveRule = async () => {
  const keyword = ruleKeyword.value.trim()
  if (!keyword) {
    showError('请输入关键词')
    return
  }
  try {
    if (ruleDialogMode.value === 'create') {
      await addKeywordRule({
        xianyuAccountId: selectedAccountId.value as number,
        xyGoodsId: selectedGoodsId.value,
        keyword
      })
    } else if (editingRule.value) {
      await updateKeyword({ ruleId: editingRule.value.id, keyword })
      await updateKeywordRuleMatchMode({ ruleId: editingRule.value.id, matchMode: ruleMatchMode.value })
    }
    ruleDialogVisible.value = false
    showSuccess(ruleDialogMode.value === 'create' ? '关键词规则已创建' : '关键词规则已保存')
    await loadRules()
  } catch (error) {
    showError('保存关键词规则失败')
  }
}

const saveMatchMode = async (rule: KeywordReplyRule, event: Event) => {
  const matchMode = Number((event.target as HTMLSelectElement).value)
  try {
    await updateKeywordRuleMatchMode({ ruleId: rule.id, matchMode })
    rule.matchMode = matchMode
    showSuccess('匹配方式已更新')
  } catch (error) {
    showError('更新匹配方式失败')
  }
}

const removeRule = async (rule: KeywordReplyRule) => {
  if (!window.confirm(`确定删除关键词「${rule.keyword}」及其回复内容吗？`)) return
  try {
    await deleteKeywordRule({ ruleId: rule.id })
    showSuccess('关键词规则已删除')
    await loadRules()
  } catch (error) {
    showError('删除关键词规则失败')
  }
}

const createFallback = async () => {
  if (!selectedAccountId.value || !selectedGoodsId.value) return
  try {
    await ensureFallbackRule({
      xianyuAccountId: selectedAccountId.value,
      xyGoodsId: selectedGoodsId.value
    })
    showSuccess('兜底回复规则已准备好')
    await loadRules()
  } catch (error) {
    showError('创建兜底回复失败')
  }
}

const openCreateContent = (rule: KeywordReplyRule, imageOnly = false) => {
  contentRuleId.value = rule.id
  editingContent.value = null
  replyText.value = ''
  replyImageUrl.value = imageOnly ? 'https://' : ''
  contentDialogVisible.value = true
}

const openEditContent = (rule: KeywordReplyRule, content: KeywordReplyContent) => {
  contentRuleId.value = rule.id
  editingContent.value = content
  replyText.value = content.replyText || ''
  replyImageUrl.value = content.replyImageUrl || ''
  contentDialogVisible.value = true
}

const saveContent = async () => {
  const text = replyText.value.trim()
  const imageUrl = replyImageUrl.value.trim()
  if (!text && !imageUrl) {
    showError('请填写文字回复或图片链接')
    return
  }
  try {
    if (editingContent.value) {
      await updateKeywordContent({
        contentId: editingContent.value.id,
        replyText: text || undefined,
        replyImageUrl: imageUrl || undefined
      })
    } else {
      await addKeywordContent({
        ruleId: contentRuleId.value as number,
        replyText: text || undefined,
        replyImageUrl: imageUrl || undefined
      })
    }
    contentDialogVisible.value = false
    showSuccess('回复内容已保存')
    await loadRules()
  } catch (error) {
    showError('保存回复内容失败')
  }
}

const removeContent = async (content: KeywordReplyContent) => {
  if (!window.confirm('确定删除这条回复内容吗？')) return
  try {
    await deleteKeywordContent({ contentId: content.id })
    showSuccess('回复内容已删除')
    await loadRules()
  } catch (error) {
    showError('删除回复内容失败')
  }
}

watch(
  () => route.query,
  (query) => {
    const accountId = Number(query.accountId)
    if (accountId && accountId !== selectedAccountId.value) {
      selectedAccountId.value = accountId
      void loadGoods(String(query.goodsId || ''))
    }
  }
)

onMounted(loadAccounts)
</script>

<template>
  <div class="keyword-center-page">
    <header class="page-heading">
      <div>
        <p class="eyebrow">关键词规则</p>
        <h1>关键词回复中心</h1>
        <p>为不同商品设置专属关键词与回复内容；关键词未命中时，才会继续使用商品 AI 回复与系统 AI。</p>
      </div>
      <div class="heading-actions">
        <button class="btn btn-secondary" :disabled="loading" @click="loadRules">刷新规则</button>
        <button class="btn btn-primary" :disabled="!selectedGoodsId" @click="openCreateRule">新增关键词</button>
      </div>
    </header>

    <section class="scope-card">
      <div class="scope-field">
        <label>闲鱼账号</label>
        <select v-model.number="selectedAccountId" :disabled="loading" @change="handleAccountChange">
          <option v-for="account in accounts" :key="account.id" :value="account.id">
            {{ account.accountNote || account.unb }}（{{ account.unb }}）
          </option>
        </select>
      </div>
      <div class="scope-field product-field">
        <label>关联商品</label>
        <select v-model="selectedGoodsId" :disabled="loading || !goods.length" @change="handleGoodsChange">
          <option v-if="!goods.length" value="">暂无已同步商品</option>
          <option v-for="goodsItem in goods" :key="goodsItem.xyGoodId" :value="String(goodsItem.xyGoodId)">
            {{ goodsItem.title || goodsItem.xyGoodId }}
          </option>
        </select>
      </div>
      <div class="scope-summary">
        <span class="summary-label">当前范围</span>
        <strong>{{ selectedAccount?.accountNote || selectedAccount?.unb || '未选择账号' }}</strong>
        <span>{{ selectedGoods?.title || '未选择商品' }}</span>
      </div>
    </section>

    <section class="guide-card">
      <div class="guide-icon">i</div>
      <div>
        <strong>回复优先级</strong>
        <p>商品关键词回复 → 商品固定资料 / 专属 AI → 系统 AI 客服配置。{{ scopeDescription }}</p>
      </div>
    </section>

    <section class="rules-panel">
      <div class="panel-heading">
        <div>
          <h2>关键词规则</h2>
          <p>{{ rules.length }} 条规则 · 每条规则可添加多条文字或图片回复</p>
        </div>
        <button class="btn btn-soft" :disabled="!selectedGoodsId" @click="createFallback">添加兜底回复</button>
      </div>

      <div v-if="loading" class="state-box">正在加载关键词规则…</div>
      <div v-else-if="!selectedGoodsId" class="state-box">请先在上方选择一个已同步商品。</div>
      <div v-else-if="!rules.length" class="empty-box">
        <strong>这个商品还没有关键词规则</strong>
        <span>可以从“新增关键词”开始，例如：价格、下单、售后。</span>
        <button class="btn btn-primary" @click="openCreateRule">新增第一条关键词</button>
      </div>
      <div v-else class="rules-grid">
        <article v-for="rule in rules" :key="rule.id" class="rule-card" :class="{ fallback: rule.isFallback }">
          <div class="rule-card-header">
            <div>
              <span class="rule-type">{{ rule.isFallback ? '兜底回复' : '关键词' }}</span>
              <h3>{{ rule.isFallback ? '未命中关键词时回复' : rule.keyword }}</h3>
            </div>
            <div class="rule-actions">
              <button class="text-btn" @click="openEditRule(rule)">编辑</button>
              <button class="text-btn danger" @click="removeRule(rule)">删除</button>
            </div>
          </div>

          <div v-if="!rule.isFallback" class="match-row">
            <span>匹配方式</span>
            <select :value="rule.matchMode || 0" @change="saveMatchMode(rule, $event)">
              <option :value="0">包含匹配</option>
              <option :value="1">精确匹配</option>
              <option :value="2">开头匹配</option>
            </select>
            <small>{{ matchModeText(rule.matchMode) }}</small>
          </div>

          <div class="reply-list">
            <div v-if="!rule.contents?.length" class="empty-reply">暂未添加回复内容</div>
            <div v-for="content in rule.contents" :key="content.id" class="reply-item">
              <div class="reply-main">
                <img v-if="content.replyImageUrl" :src="content.replyImageUrl" alt="回复图片" />
                <p v-if="content.replyText">{{ content.replyText }}</p>
                <span v-if="content.replyImageUrl && !content.replyText">图片回复</span>
              </div>
              <div class="reply-actions">
                <button class="text-btn" @click="openEditContent(rule, content)">编辑</button>
                <button class="text-btn danger" @click="removeContent(content)">删除</button>
              </div>
            </div>
          </div>

          <footer class="rule-footer">
            <button class="btn btn-soft" @click="openCreateContent(rule)">+ 文字回复</button>
            <button class="btn btn-soft" @click="openCreateContent(rule, true)">+ 图片回复</button>
          </footer>
        </article>
      </div>
    </section>

    <Teleport to="body">
      <div v-if="ruleDialogVisible" class="dialog-mask" @click.self="ruleDialogVisible = false">
        <section class="dialog-card small-dialog">
          <header>
            <div>
              <h2>{{ ruleDialogMode === 'create' ? '新增关键词规则' : '编辑关键词规则' }}</h2>
              <p>规则将仅对当前关联商品生效。</p>
            </div>
            <button class="close-btn" @click="ruleDialogVisible = false">×</button>
          </header>
          <main>
            <label>关键词</label>
            <input v-model="ruleKeyword" maxlength="100" placeholder="例如：价格、怎么下单、售后" @keyup.enter="saveRule" />
            <template v-if="ruleDialogMode === 'edit'">
              <label>匹配方式</label>
              <select v-model.number="ruleMatchMode">
                <option :value="0">包含匹配</option>
                <option :value="1">精确匹配</option>
                <option :value="2">开头匹配</option>
              </select>
            </template>
          </main>
          <footer>
            <button class="btn btn-secondary" @click="ruleDialogVisible = false">取消</button>
            <button class="btn btn-primary" @click="saveRule">保存规则</button>
          </footer>
        </section>
      </div>

      <div v-if="contentDialogVisible" class="dialog-mask" @click.self="contentDialogVisible = false">
        <section class="dialog-card">
          <header>
            <div>
              <h2>{{ editingContent ? '编辑回复内容' : '新增回复内容' }}</h2>
              <p>可单独填写文字，或填写一张图片链接，也可以同时填写。</p>
            </div>
            <button class="close-btn" @click="contentDialogVisible = false">×</button>
          </header>
          <main>
            <label>文字回复</label>
            <textarea v-model="replyText" rows="6" maxlength="2000" placeholder="输入买家收到关键词时的回复内容" />
            <label>图片链接</label>
            <input v-model="replyImageUrl" type="url" maxlength="1000" placeholder="https://example.com/reply-image.jpg" />
            <img v-if="replyImageUrl && replyImageUrl !== 'https://'" class="image-preview" :src="replyImageUrl" alt="图片回复预览" />
          </main>
          <footer>
            <button class="btn btn-secondary" @click="contentDialogVisible = false">取消</button>
            <button class="btn btn-primary" @click="saveContent">保存内容</button>
          </footer>
        </section>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.keyword-center-page { min-height: 100%; color: #183152; }
.page-heading, .panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.page-heading { margin-bottom: 18px; }
.eyebrow { margin: 0 0 4px; color: #f0a400; font-size: 13px; font-weight: 700; }
h1, h2, h3, p { margin: 0; }
h1 { font-size: 27px; line-height: 1.2; letter-spacing: -.4px; }
.page-heading p { margin-top: 8px; color: #6c7d99; font-size: 14px; }
.heading-actions { display: flex; gap: 10px; }
.btn { border: 1px solid transparent; border-radius: 10px; padding: 9px 15px; cursor: pointer; font-weight: 650; line-height: 1; transition: .18s ease; }
.btn:disabled { opacity: .48; cursor: not-allowed; }
.btn-primary { color: #173052; background: linear-gradient(135deg, #ffd641, #f5b800); box-shadow: 0 7px 16px rgba(234, 171, 0, .2); }
.btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 10px 20px rgba(234, 171, 0, .28); }
.btn-secondary { border-color: #d5dfed; background: #fff; color: #315378; }
.btn-soft { border-color: #cdddfa; background: #f5f9ff; color: #1870df; }
.scope-card { display: grid; grid-template-columns: minmax(185px, .7fr) minmax(300px, 1.35fr) minmax(210px, 1fr); gap: 14px; align-items: end; padding: 16px; margin-bottom: 14px; border: 1px solid #e4eaf2; border-radius: 16px; background: #fff; box-shadow: 0 8px 24px rgba(25, 54, 93, .035); }
.scope-field { display: flex; flex-direction: column; gap: 7px; }
label { color: #526681; font-size: 13px; font-weight: 700; }
select, input, textarea { box-sizing: border-box; width: 100%; border: 1px solid #d9e2ee; border-radius: 9px; background: #fff; color: #213a5d; font: inherit; outline: none; transition: border .15s, box-shadow .15s; }
select, input { min-height: 39px; padding: 0 11px; }
textarea { padding: 10px 11px; resize: vertical; line-height: 1.55; }
select:focus, input:focus, textarea:focus { border-color: #6fa9fa; box-shadow: 0 0 0 3px rgba(56, 139, 253, .12); }
.scope-summary { min-height: 39px; padding: 8px 12px; border-radius: 10px; background: #f8fafc; display: flex; flex-direction: column; justify-content: center; gap: 2px; overflow: hidden; }
.scope-summary .summary-label { color: #94a3b8; font-size: 11px; }
.scope-summary strong, .scope-summary > span:last-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.scope-summary strong { font-size: 13px; }
.scope-summary > span:last-child { color: #6b7e99; font-size: 12px; }
.guide-card { display: flex; gap: 11px; padding: 13px 15px; margin-bottom: 16px; border: 1px solid #cfe1ff; border-radius: 13px; background: #f5f9ff; }
.guide-icon { flex: 0 0 22px; width: 22px; height: 22px; border-radius: 50%; background: #d9eaff; color: #277ad9; text-align: center; line-height: 22px; font-weight: 800; }
.guide-card strong { font-size: 13px; }
.guide-card p { margin-top: 3px; color: #627795; font-size: 13px; line-height: 1.5; }
.rules-panel { padding: 20px; border: 1px solid #e5ebf3; border-radius: 18px; background: #fff; box-shadow: 0 10px 28px rgba(25, 54, 93, .035); }
.panel-heading { padding-bottom: 17px; margin-bottom: 15px; border-bottom: 1px solid #edf1f6; align-items: center; }
.panel-heading h2 { font-size: 18px; }
.panel-heading p { margin-top: 4px; color: #8392a8; font-size: 13px; }
.state-box, .empty-box { min-height: 220px; display: flex; align-items: center; justify-content: center; color: #7c8da5; }
.empty-box { flex-direction: column; gap: 10px; }
.empty-box strong { color: #425a7a; font-size: 16px; }
.empty-box span { font-size: 13px; }
.rules-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(355px, 1fr)); gap: 15px; }
.rule-card { overflow: hidden; border: 1px solid #e2e9f2; border-radius: 14px; background: #fff; }
.rule-card.fallback { border-color: #f8d777; background: linear-gradient(180deg, #fffcf2, #fff); }
.rule-card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; padding: 16px 16px 12px; }
.rule-type { display: inline-block; margin-bottom: 4px; padding: 2px 7px; border-radius: 5px; background: #eaf3ff; color: #3678c7; font-size: 11px; font-weight: 700; }
.fallback .rule-type { background: #fff0c8; color: #a86500; }
.rule-card h3 { max-width: 240px; font-size: 16px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rule-actions, .reply-actions { display: flex; gap: 8px; }
.text-btn { padding: 0; border: 0; background: transparent; color: #347fe0; cursor: pointer; font-size: 13px; }
.text-btn.danger { color: #ef6b6b; }
.match-row { display: flex; align-items: center; gap: 8px; padding: 0 16px 12px; color: #74849b; font-size: 12px; }
.match-row select { width: auto; min-height: 31px; padding: 0 7px; font-size: 12px; }
.match-row small { color: #9aa8ba; }
.reply-list { display: flex; flex-direction: column; gap: 8px; padding: 0 12px 12px; }
.empty-reply { padding: 22px 12px; border: 1px dashed #dbe4ef; border-radius: 9px; color: #9aa8b7; text-align: center; font-size: 13px; }
.reply-item { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; padding: 10px; border-radius: 10px; background: #f8fafc; }
.reply-main { min-width: 0; display: flex; align-items: flex-start; gap: 9px; color: #4e6380; font-size: 13px; line-height: 1.55; }
.reply-main p { white-space: pre-wrap; overflow-wrap: anywhere; }
.reply-main img { width: 44px; height: 44px; flex: 0 0 44px; border-radius: 7px; object-fit: cover; }
.reply-actions { padding-top: 2px; white-space: nowrap; }
.rule-footer { display: flex; gap: 8px; padding: 12px 16px 16px; }
.rule-footer .btn { padding: 7px 10px; font-size: 12px; }
.dialog-mask { position: fixed; z-index: 2000; inset: 0; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(13, 27, 46, .48); }
.dialog-card { width: min(570px, 100%); overflow: hidden; border-radius: 16px; background: #fff; box-shadow: 0 22px 65px rgba(3, 18, 40, .24); }
.dialog-card.small-dialog { width: min(450px, 100%); }
.dialog-card header, .dialog-card footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 18px 20px; }
.dialog-card header { border-bottom: 1px solid #edf1f5; }
.dialog-card h2 { font-size: 18px; }
.dialog-card header p { margin-top: 5px; color: #7e8fa6; font-size: 13px; }
.dialog-card main { display: flex; flex-direction: column; gap: 8px; padding: 20px; }
.dialog-card main label:not(:first-child) { margin-top: 8px; }
.dialog-card footer { justify-content: flex-end; border-top: 1px solid #edf1f5; }
.close-btn { width: 30px; height: 30px; border: 0; border-radius: 8px; background: #f3f6f9; color: #6c7d94; cursor: pointer; font-size: 22px; line-height: 1; }
.image-preview { max-width: 160px; max-height: 160px; margin-top: 4px; border-radius: 9px; object-fit: cover; }
@media (max-width: 1000px) { .scope-card { grid-template-columns: 1fr 1fr; } .scope-summary { grid-column: span 2; } }
@media (max-width: 680px) { .page-heading, .panel-heading { align-items: stretch; flex-direction: column; } .heading-actions { display: grid; grid-template-columns: 1fr 1fr; } .scope-card { grid-template-columns: 1fr; } .scope-summary { grid-column: auto; } .rules-panel { padding: 14px; } .rules-grid { grid-template-columns: 1fr; } .rule-card h3 { max-width: 180px; } }
</style>

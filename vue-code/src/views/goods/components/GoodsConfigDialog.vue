<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { getKamiConfigs, type KamiConfig } from '@/api/kami-config'
import { batchUpdateGoodsConfig, type GoodsItemWithConfig } from '@/api/goods'
import { getFixedMaterial, saveFixedMaterial } from '@/api/ai'
import { showError, showSuccess } from '@/utils'

interface Props {
  modelValue: boolean
  item: GoodsItemWithConfig | null
  accountId: number | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
  openKeywordRules: []
}>()

const loading = ref(false)
const saving = ref(false)
const kamiConfigs = ref<KamiConfig[]>([])
const form = reactive({
  deliveryEnabled: false,
  kamiConfigId: '' as '' | number,
  aiEnabled: false,
  keywordEnabled: false,
  aiPrompt: '',
  fixedMaterial: ''
})

const itemTitle = computed(() => props.item?.item.title || '商品配置')
const availableKamiConfigs = computed(() => kamiConfigs.value.filter((config) =>
  config.xianyuAccountId == null || config.xianyuAccountId === props.accountId
))

const close = () => {
  if (!saving.value) emit('update:modelValue', false)
}

const loadConfig = async () => {
  if (!props.modelValue || !props.item || !props.accountId) return
  loading.value = true
  form.deliveryEnabled = props.item.xianyuAutoDeliveryOn === 1
  form.aiEnabled = props.item.xianyuAutoReplyOn === 1
  form.keywordEnabled = props.item.xianyuKeywordReplyOn === 1
  form.aiPrompt = ''
  form.fixedMaterial = ''
  form.kamiConfigId = props.item.kamiConfigId ?? ''
  try {
    const [kamiResponse, materialResponse] = await Promise.all([
      getKamiConfigs(),
      getFixedMaterial({ accountId: props.accountId, goodsId: props.item.item.xyGoodId })
    ])
    if (kamiResponse.code === 0 || kamiResponse.code === 200) {
      kamiConfigs.value = kamiResponse.data || []
    }
    if (materialResponse.ok) {
      const material = await materialResponse.json()
      if (material.code === 0 || material.code === 200) {
        form.fixedMaterial = material.data?.fixedMaterial || ''
        form.aiPrompt = material.data?.aiPrompt || ''
      }
    }
  } catch (error) {
    console.error('加载商品配置失败', error)
    showError('商品配置加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!props.item || !props.accountId || saving.value) return
  saving.value = true
  try {
    const result = await batchUpdateGoodsConfig({
      xianyuAccountId: props.accountId,
      xyGoodsIds: [props.item.item.xyGoodId],
      xianyuAutoDeliveryOn: form.deliveryEnabled ? 1 : 0,
      xianyuAutoReplyOn: form.aiEnabled ? 1 : 0,
      xianyuKeywordReplyOn: form.keywordEnabled ? 1 : 0,
      kamiConfigId: form.deliveryEnabled && form.kamiConfigId !== '' ? Number(form.kamiConfigId) : undefined
    })
    if (result.code !== 0 && result.code !== 200) throw new Error(result.msg || '保存商品配置失败')

    const materialResponse = await saveFixedMaterial({
      accountId: props.accountId,
      goodsId: props.item.item.xyGoodId,
      aiPrompt: form.aiPrompt.trim(),
      fixedMaterial: form.fixedMaterial.trim()
    })
    if (!materialResponse.ok) throw new Error('保存商品 AI 资料失败')
    const material = await materialResponse.json()
    if (material.code !== 0 && material.code !== 200) throw new Error(material.msg || '保存商品 AI 资料失败')

    showSuccess('商品配置已保存')
    emit('saved')
    emit('update:modelValue', false)
  } catch (error: any) {
    console.error('保存商品配置失败', error)
    showError(error?.message || '保存商品配置失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

watch(() => [props.modelValue, props.item?.item.xyGoodId, props.accountId], loadConfig, { immediate: true })
</script>

<template>
  <Teleport to="body">
    <Transition name="goods-config-fade">
      <div v-if="modelValue" class="goods-config-mask" @click.self="close">
        <section class="goods-config-dialog" role="dialog" aria-modal="true" :aria-label="`${itemTitle} 配置`">
          <header class="goods-config-dialog__header">
            <div>
              <p class="goods-config-dialog__eyebrow">商品配置中心</p>
              <h2>{{ itemTitle }}</h2>
              <p>把发货、商品专属 AI 与关键词回复放在同一个地方管理。</p>
            </div>
            <button class="goods-config-dialog__close" type="button" aria-label="关闭" @click="close">×</button>
          </header>

          <div v-if="loading" class="goods-config-dialog__loading">正在加载商品配置…</div>
          <div v-else class="goods-config-dialog__content">
            <section class="config-section">
              <div class="config-section__title">
                <div>
                  <h3>自动发货</h3>
                  <p>开启后，此商品下单将按关联的卡券或现有发货配置执行。</p>
                </div>
                <label class="switch">
                  <input v-model="form.deliveryEnabled" type="checkbox" />
                  <span></span>
                </label>
              </div>
              <label v-if="form.deliveryEnabled" class="field">
                <span>关联卡券</span>
                <select v-model="form.kamiConfigId">
                  <option value="">保留现有发货配置</option>
                  <option v-for="config in availableKamiConfigs" :key="config.id" :value="config.id">
                    {{ config.aliasName }}（{{ config.availableCount }} 可用）
                  </option>
                </select>
              </label>
            </section>

            <section class="config-section">
              <div class="config-section__title">
                <div>
                  <h3>商品专属 AI 回复</h3>
                  <p>优先使用这份提示词和固定资料；未填写时才回退到系统 AI 设置。</p>
                </div>
                <label class="switch">
                  <input v-model="form.aiEnabled" type="checkbox" />
                  <span></span>
                </label>
              </div>
              <template v-if="form.aiEnabled">
                <label class="field">
                  <span>AI 提示词</span>
                  <textarea v-model="form.aiPrompt" rows="3" placeholder="例如：你是本商品的售前客服；只回答与商品、购买方式和售后有关的问题。"></textarea>
                </label>
                <label class="field">
                  <span>固定资料</span>
                  <textarea v-model="form.fixedMaterial" rows="4" placeholder="例如：规格、使用说明、发货说明、注意事项。"></textarea>
                </label>
              </template>
            </section>

            <section class="config-section config-section--keyword">
              <div class="config-section__title">
                <div>
                  <h3>关键词回复</h3>
                  <p>命中此商品关键词时优先回复；规则统一在“关键词回复”中维护。</p>
                </div>
                <label class="switch">
                  <input v-model="form.keywordEnabled" type="checkbox" />
                  <span></span>
                </label>
              </div>
              <button class="text-action" type="button" @click="emit('openKeywordRules')">管理本商品关键词规则 →</button>
            </section>

            <section class="config-tip">
              <strong>回复优先级</strong>
              <span>关键词规则 → 商品专属 AI（结合固定资料）→ 系统 AI 兜底</span>
            </section>
          </div>

          <footer class="goods-config-dialog__footer">
            <button class="btn btn--secondary" type="button" @click="close">取消</button>
            <button class="btn btn--primary" :disabled="loading || saving" type="button" @click="save">
              {{ saving ? '保存中…' : '保存配置' }}
            </button>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.goods-config-mask { position: fixed; inset: 0; z-index: 2100; display: grid; place-items: center; padding: 24px; background: rgba(18, 26, 40, .46); backdrop-filter: blur(4px); }
.goods-config-dialog { width: min(760px, 100%); max-height: min(820px, calc(100vh - 48px)); overflow: auto; border: 1px solid rgba(255,255,255,.65); border-radius: 20px; background: #fffdf8; box-shadow: 0 24px 64px rgba(21, 36, 58, .25); }
.goods-config-dialog__header { display: flex; justify-content: space-between; gap: 20px; padding: 24px 28px 20px; border-bottom: 1px solid #eee7d9; }
.goods-config-dialog__eyebrow { margin: 0 0 6px; color: #a56b00; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.goods-config-dialog h2 { margin: 0; color: #172844; font-size: 21px; }
.goods-config-dialog__header p:not(.goods-config-dialog__eyebrow) { margin: 8px 0 0; color: #758097; font-size: 13px; }
.goods-config-dialog__close { width: 34px; height: 34px; border: 0; border-radius: 10px; background: #f4f5f7; color: #677087; cursor: pointer; font-size: 24px; line-height: 30px; }
.goods-config-dialog__content { display: grid; gap: 14px; padding: 20px 28px; }
.goods-config-dialog__loading { padding: 56px; color: #667085; text-align: center; }
.config-section { padding: 18px; border: 1px solid #e6eaf0; border-radius: 14px; background: #fff; }
.config-section__title { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.config-section h3 { margin: 0; color: #1d2d48; font-size: 15px; }
.config-section p { margin: 6px 0 0; color: #758097; font-size: 13px; line-height: 1.55; }
.field { display: grid; gap: 8px; margin-top: 16px; color: #536079; font-size: 13px; font-weight: 600; }
.field select, .field textarea { width: 100%; box-sizing: border-box; border: 1px solid #dce3ec; border-radius: 10px; background: #fbfcfe; color: #253651; font: inherit; padding: 10px 12px; outline: none; resize: vertical; }
.field select:focus, .field textarea:focus { border-color: #4e9aff; box-shadow: 0 0 0 3px rgba(78,154,255,.13); }
.switch { position: relative; flex: none; width: 44px; height: 24px; cursor: pointer; }
.switch input { opacity: 0; width: 0; height: 0; }
.switch span { position: absolute; inset: 0; border-radius: 99px; background: #d7dde7; transition: .2s; }
.switch span::after { content: ''; position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%; background: white; box-shadow: 0 1px 4px rgba(0,0,0,.2); transition: .2s; }
.switch input:checked + span { background: #31c66a; }
.switch input:checked + span::after { transform: translateX(20px); }
.text-action { margin-top: 14px; border: 0; background: transparent; color: #1a79e8; font: inherit; cursor: pointer; padding: 0; }
.config-tip { display: flex; gap: 12px; flex-wrap: wrap; padding: 12px 14px; border-radius: 10px; background: #fff6dc; color: #86620d; font-size: 13px; }
.goods-config-dialog__footer { display: flex; justify-content: flex-end; gap: 10px; padding: 18px 28px 24px; border-top: 1px solid #eee7d9; }
.btn { min-width: 92px; height: 38px; border-radius: 10px; padding: 0 16px; font: inherit; cursor: pointer; }
.btn--secondary { border: 1px solid #d8e0eb; background: white; color: #49617f; }
.btn--primary { border: 0; background: linear-gradient(135deg, #ffbf00, #f3a800); color: #292013; font-weight: 700; box-shadow: 0 7px 16px rgba(240,174,0,.24); }
.btn:disabled { opacity: .55; cursor: not-allowed; }
.goods-config-fade-enter-active, .goods-config-fade-leave-active { transition: opacity .18s ease; }
.goods-config-fade-enter-from, .goods-config-fade-leave-to { opacity: 0; }
@media (max-width: 620px) { .goods-config-mask { padding: 0; align-items: end; } .goods-config-dialog { width: 100%; max-height: 92vh; border-radius: 20px 20px 0 0; } .goods-config-dialog__header, .goods-config-dialog__content, .goods-config-dialog__footer { padding-left: 18px; padding-right: 18px; } }
</style>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { getKamiConfigs, type KamiConfig } from '@/api/kami-config'
import {
  batchUpdateGoodsConfig,
  getProductDefaultReplyConfig,
  syncSingleGoods,
  updateAutoConfirmShipment,
  updateProductDefaultReplyConfig,
  type GoodsItemWithConfig
} from '@/api/goods'
import {
  deleteAutoDeliverySkuConfig,
  getAutoDeliveryConfigsByGoodsId,
  getGoodsSkuList,
  saveOrUpdateAutoDeliveryConfig,
  updateGoodsSkuPreferences,
  type AutoDeliveryConfig,
  type GoodsSku
} from '@/api/auto-delivery-config'
import { getFixedMaterial, saveFixedMaterial } from '@/api/ai'
import { uploadImage } from '@/api/image'
import { showError, showInfo, showSuccess } from '@/utils'

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
const syncingSkus = ref(false)
const skuSyncMessage = ref('')
const skuSyncFailed = ref(false)
const defaultReplyImageUploading = ref(false)
const defaultReplyImageInput = ref<HTMLInputElement | null>(null)
const kamiConfigs = ref<KamiConfig[]>([])
const KEEP_EXISTING_SKU_RULE = '__KEEP_EXISTING__' as const

type SkuKamiSelection = '' | number | typeof KEEP_EXISTING_SKU_RULE
interface SkuDeliveryRow {
  skuId: string
  platformName: string
  displayName: string
  price: number
  quantity: number
  kamiSelection: SkuKamiSelection
  existingConfig: AutoDeliveryConfig | null
}

const skuRows = ref<SkuDeliveryRow[]>([])
const form = reactive({
  deliveryEnabled: false,
  kamiConfigId: '' as '' | number,
  autoConfirmShipment: false,
  aiEnabled: false,
  keywordEnabled: false,
  productDefaultReplyEnabled: false,
  productDefaultReplyMode: 1,
  productDefaultReplyText: '',
  productDefaultReplyImageUrl: '',
  aiPrompt: '',
  fixedMaterial: '',
  bargainEnabled: false,
  bargainFloorPrice: null as number | null,
  bargainStepAmount: null as number | null,
  bargainMaxRounds: 3,
  bargainStyle: 'BALANCED' as 'FIRM' | 'BALANCED' | 'CLOSE',
  bargainFloorReply: '',
  bargainInstructions: ''
})

const itemTitle = computed(() => props.item?.item.title || '商品配置')
const hasMultipleSkus = computed(() => skuRows.value.length > 1)
const availableKamiConfigs = computed(() => kamiConfigs.value.filter((config) =>
  config.xianyuAccountId == null || config.xianyuAccountId === props.accountId
))
const availableKamiConfigIds = computed(() => new Set(
  availableKamiConfigs.value.map((config) => Number(config.id))
))

const skuPlatformName = (sku: GoodsSku) => sku.valueText || sku.propertyText || `规格 ${sku.skuId || sku.id}`

const skuPriceLabel = (price: number) => Number.isFinite(Number(price))
  ? `¥${(Number(price) / 100).toFixed(2)}`
  : '价格未知'

const kamiAvailabilityLabel = (config: KamiConfig) => {
  if (config.sourceType === 3) return '固定内容（不限量）'
  if (config.sourceType === 2) return 'API 实时获取'
  return `${config.availableCount} 可用`
}

const applySkuRows = (skus: GoodsSku[], deliveryConfigs: AutoDeliveryConfig[]) => {
  const exactConfigs = new Map(deliveryConfigs
    .filter((config) => config.skuId)
    .map((config) => [String(config.skuId), config]))
  skuRows.value = skus
    .filter((sku) => sku.skuId != null && String(sku.skuId).trim() !== '')
    .map((sku) => {
      const skuId = String(sku.skuId)
      const existingConfig = exactConfigs.get(skuId) || null
      const configuredKamiIds = existingConfig?.kamiConfigIds
        ?.split(',').map((value) => value.trim()).filter(Boolean) || []
      let kamiSelection: SkuKamiSelection = ''
      if (existingConfig) {
        const configuredKamiId = Number(configuredKamiIds[0])
        kamiSelection = existingConfig.deliveryMode === 2
          && configuredKamiIds.length === 1
          && Number.isFinite(configuredKamiId)
          && availableKamiConfigIds.value.has(configuredKamiId)
          ? configuredKamiId
          : KEEP_EXISTING_SKU_RULE
      }
      const platformName = skuPlatformName(sku)
      return {
        skuId,
        platformName,
        displayName: sku.displayName?.trim() || platformName,
        price: Number(sku.price),
        quantity: Number(sku.quantity || 0),
        kamiSelection,
        existingConfig
      }
    })
}

const skuCountMessage = () => {
  if (skuRows.value.length > 1) {
    return `已识别 ${skuRows.value.length} 个规格，可以分别指定卡密库。`
  }
  if (skuRows.value.length === 1) {
    return '当前只识别到 1 个规格，商品将使用默认发货规则。'
  }
  return '暂未同步到商品规格；如果闲鱼商品实际有多个规格，请重新同步。'
}

const syncSkuDetails = async () => {
  if (!props.item || !props.accountId || syncingSkus.value) return
  syncingSkus.value = true
  skuSyncFailed.value = false
  skuSyncMessage.value = '正在从闲鱼重新同步商品规格…'
  try {
    const syncResponse = await syncSingleGoods({
      xianyuAccountId: props.accountId,
      xyGoodsId: props.item.item.xyGoodId
    })
    if (syncResponse.code !== 0 && syncResponse.code !== 200) {
      throw new Error(syncResponse.msg || '商品规格同步失败')
    }
    if (!syncResponse.data?.success) {
      skuSyncFailed.value = true
      skuSyncMessage.value = syncResponse.data?.message || '商品详情同步未完成，请检查账号凭证后重试。'
      showError(skuSyncMessage.value)
      return
    }

    const [skuResponse, configResponse] = await Promise.all([
      getGoodsSkuList(props.accountId, props.item.item.xyGoodId),
      getAutoDeliveryConfigsByGoodsId({
        xianyuAccountId: props.accountId,
        xyGoodsId: props.item.item.xyGoodId
      })
    ])
    if (skuResponse.code !== 0 && skuResponse.code !== 200) {
      throw new Error(skuResponse.msg || '读取商品规格失败')
    }
    if (configResponse.code !== 0 && configResponse.code !== 200) {
      throw new Error(configResponse.msg || '读取规格发货配置失败')
    }
    applySkuRows(skuResponse.data || [], configResponse.data || [])
    skuSyncMessage.value = skuCountMessage()
    if (hasMultipleSkus.value) {
      showSuccess(skuSyncMessage.value)
    } else {
      showInfo(skuSyncMessage.value)
    }
    emit('saved')
  } catch (error: any) {
    skuSyncFailed.value = true
    skuSyncMessage.value = error?.message || '商品规格同步失败，请稍后重试。'
    showError(skuSyncMessage.value)
  } finally {
    syncingSkus.value = false
  }
}

const close = () => {
  if (!saving.value) emit('update:modelValue', false)
}

const loadConfig = async () => {
  if (!props.modelValue || !props.item || !props.accountId) return
  loading.value = true
  form.deliveryEnabled = props.item.xianyuAutoDeliveryOn === 1
  form.autoConfirmShipment = false
  form.aiEnabled = props.item.xianyuAutoReplyOn === 1
  form.keywordEnabled = props.item.xianyuKeywordReplyOn === 1
  form.productDefaultReplyEnabled = props.item.productDefaultReplyOn === 1
  form.productDefaultReplyMode = props.item.productDefaultReplyMode === 2 ? 2 : 1
  form.productDefaultReplyText = ''
  form.productDefaultReplyImageUrl = ''
  form.aiPrompt = ''
  form.fixedMaterial = ''
  form.bargainEnabled = false
  form.bargainFloorPrice = null
  form.bargainStepAmount = null
  form.bargainMaxRounds = 3
  form.bargainStyle = 'BALANCED'
  form.bargainFloorReply = ''
  form.bargainInstructions = ''
  form.kamiConfigId = props.item.kamiConfigId ?? ''
  skuRows.value = []
  skuSyncMessage.value = ''
  skuSyncFailed.value = false
  try {
    const [kamiResponse, materialResponse, deliveryConfigResponse, defaultReplyResponse, skuResponse] = await Promise.all([
      getKamiConfigs(),
      getFixedMaterial({ accountId: props.accountId, goodsId: props.item.item.xyGoodId }),
      getAutoDeliveryConfigsByGoodsId({ xianyuAccountId: props.accountId, xyGoodsId: props.item.item.xyGoodId }),
      getProductDefaultReplyConfig({ xianyuAccountId: props.accountId, xyGoodsId: props.item.item.xyGoodId }),
      getGoodsSkuList(props.accountId, props.item.item.xyGoodId)
    ])
    if (kamiResponse.code === 0 || kamiResponse.code === 200) {
      kamiConfigs.value = kamiResponse.data || []
    }
    if (materialResponse.ok) {
      const material = await materialResponse.json()
      if (material.code === 0 || material.code === 200) {
        form.fixedMaterial = material.data?.fixedMaterial || ''
        form.aiPrompt = material.data?.aiPrompt || ''
        form.bargainEnabled = material.data?.aiBargainOn === 1
        form.bargainFloorPrice = material.data?.aiBargainFloorPrice ?? null
        form.bargainStepAmount = material.data?.aiBargainStepAmount ?? null
        form.bargainMaxRounds = material.data?.aiBargainMaxRounds ?? 3
        form.bargainStyle = material.data?.aiBargainStyle || 'BALANCED'
        form.bargainFloorReply = material.data?.aiBargainFloorReply || ''
        form.bargainInstructions = material.data?.aiBargainInstructions || ''
      }
    }
    const deliveryConfigs = deliveryConfigResponse.code === 0 || deliveryConfigResponse.code === 200
      ? (deliveryConfigResponse.data || [])
      : []
    const defaultConfig = deliveryConfigs.find((config) => config.skuId == null)
    form.autoConfirmShipment = defaultConfig?.autoConfirmShipment === 1

    if (skuResponse.code === 0 || skuResponse.code === 200) {
      applySkuRows(skuResponse.data || [], deliveryConfigs)
      skuSyncMessage.value = skuCountMessage()
    } else {
      skuSyncFailed.value = true
      skuSyncMessage.value = skuResponse.msg || '商品规格读取失败，请重新同步。'
    }
    if ((defaultReplyResponse.code === 0 || defaultReplyResponse.code === 200) && defaultReplyResponse.data) {
      form.productDefaultReplyEnabled = defaultReplyResponse.data.productDefaultReplyOn === 1
      form.productDefaultReplyMode = defaultReplyResponse.data.productDefaultReplyMode === 2 ? 2 : 1
      form.productDefaultReplyText = defaultReplyResponse.data.productDefaultReplyText || ''
      form.productDefaultReplyImageUrl = defaultReplyResponse.data.productDefaultReplyImageUrl || ''
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
  const defaultReplyText = form.productDefaultReplyText.trim()
  const defaultReplyImageUrl = form.productDefaultReplyImageUrl.trim()
  if (form.productDefaultReplyEnabled && !defaultReplyText && !defaultReplyImageUrl) {
    showError('开启商品默认回复后，请填写文字或上传一张图片')
    return
  }
  for (const sku of skuRows.value) {
    if (sku.displayName.trim().length > 200) {
      showError(`规格“${sku.platformName}”的显示名不能超过 200 个字符`)
      return
    }
  }
  const listPrice = Number(props.item.item.soldPrice)
  if (form.bargainEnabled) {
    if (!form.bargainFloorPrice || form.bargainFloorPrice <= 0) {
      showError('开启 AI 议价后，请填写大于 0 的最低成交价')
      return
    }
    if (!form.bargainStepAmount || form.bargainStepAmount <= 0) {
      showError('每轮让价金额必须大于 0')
      return
    }
    if (Number.isFinite(listPrice) && form.bargainFloorPrice > listPrice) {
      showError('最低成交价不能高于商品当前标价')
      return
    }
    if (form.bargainMaxRounds < 1 || form.bargainMaxRounds > 10) {
      showError('最大议价轮数必须在 1 到 10 之间')
      return
    }
  }
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

    const confirmResult = await updateAutoConfirmShipment({
      xianyuAccountId: props.accountId,
      xyGoodsId: props.item.item.xyGoodId,
      autoConfirmShipment: form.deliveryEnabled && form.autoConfirmShipment ? 1 : 0
    })
    if (confirmResult.code !== 0 && confirmResult.code !== 200) {
      throw new Error(confirmResult.msg || '保存自动确认发货设置失败')
    }

    if (skuRows.value.length > 0) {
      const preferenceResult = await updateGoodsSkuPreferences({
        xianyuAccountId: props.accountId,
        xyGoodsId: props.item.item.xyGoodId,
        items: skuRows.value.map((sku) => ({
          skuId: sku.skuId,
          displayName: sku.displayName.trim() === sku.platformName ? '' : sku.displayName.trim()
        }))
      })
      if (preferenceResult.code !== 0 && preferenceResult.code !== 200) {
        throw new Error(preferenceResult.msg || '保存规格显示名失败')
      }

      for (const sku of skuRows.value) {
        if (sku.kamiSelection === KEEP_EXISTING_SKU_RULE) continue
        if (sku.kamiSelection === '') {
          if (sku.existingConfig) {
            const deleteResult = await deleteAutoDeliverySkuConfig(props.accountId, props.item.item.xyGoodId, sku.skuId)
            if (deleteResult.code !== 0 && deleteResult.code !== 200) {
              throw new Error(deleteResult.msg || `恢复“${sku.displayName}”默认规则失败`)
            }
          }
          continue
        }
        const existing = sku.existingConfig
        const skuResult = await saveOrUpdateAutoDeliveryConfig({
          xianyuAccountId: props.accountId,
          xianyuGoodsId: props.item.item.id,
          xyGoodsId: props.item.item.xyGoodId,
          deliveryMode: 2,
          skuId: sku.skuId,
          skuName: sku.displayName.trim() || sku.platformName,
          autoDeliveryContent: existing?.autoDeliveryContent || '',
          kamiConfigIds: String(sku.kamiSelection),
          kamiDeliveryTemplate: existing?.kamiDeliveryTemplate || '{kmKey}',
          autoDeliveryImageUrl: existing?.autoDeliveryImageUrl || '',
          autoConfirmShipment: form.deliveryEnabled && form.autoConfirmShipment ? 1 : 0,
          autoAskFlower: existing?.autoAskFlower,
          autoAskFlowerText: existing?.autoAskFlowerText
        })
        if (skuResult.code !== 0 && skuResult.code !== 200) {
          throw new Error(skuResult.msg || `保存“${sku.displayName}”规格卡密失败`)
        }
      }
    }

    const materialResponse = await saveFixedMaterial({
      accountId: props.accountId,
      goodsId: props.item.item.xyGoodId,
      aiPrompt: form.aiPrompt.trim(),
      fixedMaterial: form.fixedMaterial.trim(),
      aiBargainOn: form.bargainEnabled ? 1 : 0,
      aiBargainFloorPrice: form.bargainFloorPrice,
      aiBargainStepAmount: form.bargainStepAmount,
      aiBargainMaxRounds: form.bargainMaxRounds,
      aiBargainStyle: form.bargainStyle,
      aiBargainFloorReply: form.bargainFloorReply.trim(),
      aiBargainInstructions: form.bargainInstructions.trim()
    })
    if (!materialResponse.ok) throw new Error('保存商品 AI 资料失败')
    const material = await materialResponse.json()
    if (material.code !== 0 && material.code !== 200) throw new Error(material.msg || '保存商品 AI 资料失败')

    const defaultReplyResult = await updateProductDefaultReplyConfig({
      xianyuAccountId: props.accountId,
      xyGoodsId: props.item.item.xyGoodId,
      productDefaultReplyOn: form.productDefaultReplyEnabled ? 1 : 0,
      productDefaultReplyMode: form.productDefaultReplyMode,
      productDefaultReplyText: defaultReplyText || undefined,
      productDefaultReplyImageUrl: defaultReplyImageUrl || undefined
    })
    if (defaultReplyResult.code !== 0 && defaultReplyResult.code !== 200) {
      throw new Error(defaultReplyResult.msg || '保存商品默认回复失败')
    }

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

const chooseDefaultReplyImage = () => defaultReplyImageInput.value?.click()

const uploadDefaultReplyImage = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !props.accountId) return
  if (!file.type.startsWith('image/')) {
    showError('请选择图片文件')
    input.value = ''
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    showError('图片不能超过 10MB')
    input.value = ''
    return
  }
  defaultReplyImageUploading.value = true
  try {
    const result = await uploadImage(props.accountId, file)
    if ((result.code === 0 || result.code === 200) && result.data) {
      form.productDefaultReplyImageUrl = result.data
      showSuccess('默认回复图片已上传')
    } else {
      throw new Error(result.msg || '图片上传失败')
    }
  } catch (error: unknown) {
    showError(error instanceof Error ? error.message : '图片上传失败')
  } finally {
    defaultReplyImageUploading.value = false
    input.value = ''
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
              <p class="goods-config-dialog__eyebrow">商品配置</p>
              <h2>{{ itemTitle }}</h2>
              <p>把发货、默认回复、商品专属 AI 与关键词回复放在同一个地方管理。</p>
            </div>
            <button class="goods-config-dialog__close" type="button" aria-label="关闭" @click="close">×</button>
          </header>

          <div v-if="loading" class="goods-config-dialog__loading">正在加载商品配置…</div>
          <div v-else class="goods-config-dialog__content">
            <section class="config-section">
              <div class="config-section__title">
                <div>
                  <h3>商品默认回复</h3>
                  <p>“仅首次”按账号 + 商品 + 买家持续去重，跨日期也不会重置；需要每次触发请选择“每条消息都回复”。</p>
                </div>
                <label class="switch">
                  <input v-model="form.productDefaultReplyEnabled" type="checkbox" />
                  <span></span>
                </label>
              </div>
              <template v-if="form.productDefaultReplyEnabled">
                <label class="field">
                  <span>回复频率</span>
                  <select v-model.number="form.productDefaultReplyMode">
                    <option :value="1">仅首次回复（跨日期不重置）</option>
                    <option :value="2">每条消息都回复</option>
                  </select>
                </label>
                <label class="field">
                  <span>默认回复文字</span>
                  <textarea v-model="form.productDefaultReplyText" rows="3" maxlength="2000" placeholder="例如：您好，商品在售。下方图片包含使用/下单说明，有问题请继续留言。"></textarea>
                </label>
                <div class="default-reply-image">
                  <div>
                    <span class="field-label">默认回复图片</span>
                    <p>上传后会自动转存到当前闲鱼账号的图片服务，买家会收到这张图片。</p>
                  </div>
                  <input ref="defaultReplyImageInput" class="default-reply-image__input" type="file" accept="image/jpeg,image/png,image/gif,image/webp" @change="uploadDefaultReplyImage" />
                  <button class="text-action" type="button" :disabled="defaultReplyImageUploading" @click="chooseDefaultReplyImage">
                    {{ defaultReplyImageUploading ? '上传中…' : (form.productDefaultReplyImageUrl ? '重新上传图片' : '上传图片') }}
                  </button>
                  <button v-if="form.productDefaultReplyImageUrl" class="text-action text-action--danger" type="button" :disabled="defaultReplyImageUploading" @click="form.productDefaultReplyImageUrl = ''">移除图片</button>
                  <img v-if="form.productDefaultReplyImageUrl" :src="form.productDefaultReplyImageUrl" class="default-reply-image__preview" alt="默认回复图片预览" />
                </div>
              </template>
            </section>

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
                <span>{{ hasMultipleSkus ? '商品默认卡券' : '关联卡券' }}</span>
                <select v-model="form.kamiConfigId">
                  <option value="">保留现有发货配置</option>
                  <option v-for="config in availableKamiConfigs" :key="config.id" :value="config.id">
                    {{ config.aliasName }}（{{ kamiAvailabilityLabel(config) }}）
                  </option>
                </select>
              </label>

              <div class="sku-mapping">
                <div class="sku-mapping__heading">
                  <div>
                    <strong>按规格指定卡密</strong>
                    <p>同步闲鱼商品规格后，每个规格可使用独立卡密库；未单独指定时沿用商品默认卡券。</p>
                  </div>
                  <div class="sku-mapping__actions">
                    <span>{{ skuRows.length }} 个规格</span>
                    <button
                      class="sku-mapping__sync"
                      type="button"
                      :disabled="syncingSkus || loading"
                      @click="syncSkuDetails"
                    >
                      {{ syncingSkus ? '同步中…' : '重新同步规格' }}
                    </button>
                  </div>
                </div>

                <div v-if="!form.deliveryEnabled" class="sku-mapping__empty">
                  <strong>多规格发货当前未启用</strong>
                  <p>先开启上方“自动发货”，识别到多个规格后即可分别指定卡密库。</p>
                </div>
                <div v-else-if="hasMultipleSkus" class="sku-mapping__list">
                  <article v-for="sku in skuRows" :key="sku.skuId" class="sku-mapping__item">
                    <div class="sku-mapping__meta">
                      <strong>{{ sku.platformName }}</strong>
                      <span>{{ skuPriceLabel(sku.price) }} · 平台库存 {{ sku.quantity }}</span>
                    </div>
                    <label class="field sku-mapping__field">
                      <span>后台显示名</span>
                      <input v-model="sku.displayName" maxlength="200" :placeholder="sku.platformName" />
                    </label>
                    <label class="field sku-mapping__field">
                      <span>此规格发送</span>
                      <select v-model="sku.kamiSelection">
                        <option value="">使用商品默认卡券</option>
                        <option v-if="sku.existingConfig && sku.kamiSelection === KEEP_EXISTING_SKU_RULE" :value="KEEP_EXISTING_SKU_RULE">
                          保留现有规格规则
                        </option>
                        <option v-for="config in availableKamiConfigs" :key="config.id" :value="config.id">
                          {{ config.aliasName }}（{{ kamiAvailabilityLabel(config) }}）
                        </option>
                      </select>
                    </label>
                  </article>
                </div>
                <div v-else class="sku-mapping__empty">
                  <strong>{{ skuRows.length === 1 ? '目前只识别到 1 个规格' : '暂未识别到商品规格' }}</strong>
                  <p>如果闲鱼端实际设置了多个规格，请点击“重新同步规格”；同步失败原因会直接显示在这里。</p>
                </div>
                <p
                  v-if="skuSyncMessage"
                  class="sku-mapping__status"
                  :class="{ 'sku-mapping__status--error': skuSyncFailed }"
                >
                  {{ skuSyncMessage }}
                </p>
              </div>

              <div v-if="form.deliveryEnabled" class="config-section__title config-section__sub-option">
                <div>
                  <h3>自动确认发货</h3>
                  <p>卡券或发货内容发送成功后，等待约 2–5 秒并自动向闲鱼确认发货。</p>
                </div>
                <label class="switch">
                  <input v-model="form.autoConfirmShipment" type="checkbox" />
                  <span></span>
                </label>
              </div>
            </section>

            <section class="config-section">
              <div class="config-section__title">
                <div>
                  <h3>启用本商品 AI 自动回复</h3>
                  <p>关闭后，本商品绝不会调用系统 AI。开启后优先使用下方资料；未填写时才使用系统 AI 的模型与全局提示词。</p>
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

            <section class="config-section config-section--bargain">
              <div class="config-section__title">
                <div>
                  <h3>AI 议价</h3>
                  <p>只处理本商品的砍价咨询；系统逐轮计算可报价格，并在 AI 回复后再次校验，绝不会自动改价。</p>
                </div>
                <label class="switch">
                  <input v-model="form.bargainEnabled" type="checkbox" />
                  <span></span>
                </label>
              </div>
              <template v-if="form.bargainEnabled">
                <div class="bargain-grid">
                  <label class="field">
                    <span>商品当前标价</span>
                    <input :value="`¥${props.item?.item.soldPrice || '-'}`" disabled />
                  </label>
                  <label class="field">
                    <span>最低成交价 *</span>
                    <input v-model.number="form.bargainFloorPrice" type="number" min="0.01" step="0.01" placeholder="AI 绝不能低于此价格" />
                  </label>
                  <label class="field">
                    <span>每轮最多让价 *</span>
                    <input v-model.number="form.bargainStepAmount" type="number" min="0.01" step="0.01" placeholder="例如 2" />
                  </label>
                  <label class="field">
                    <span>最大议价轮数</span>
                    <input v-model.number="form.bargainMaxRounds" type="number" min="1" max="10" step="1" />
                  </label>
                  <label class="field bargain-grid__wide">
                    <span>议价风格</span>
                    <select v-model="form.bargainStyle">
                      <option value="FIRM">坚定 · 少让价</option>
                      <option value="BALANCED">适中 · 逐步让价</option>
                      <option value="CLOSE">积极成交 · 不突破底价</option>
                    </select>
                  </label>
                </div>
                <label class="field">
                  <span>到达底价后的回复（可选）</span>
                  <textarea v-model="form.bargainFloorReply" rows="2" placeholder="可使用 {price} 表示本轮价格；留空则使用安全默认话术。"></textarea>
                </label>
                <label class="field">
                  <span>补充议价规则（可选）</span>
                  <textarea v-model="form.bargainInstructions" rows="3" placeholder="例如：两件以上可包邮；不赠送额外配件；不要承诺库存。"></textarea>
                </label>
                <p class="bargain-note">每个买家、商品和账号分别记录轮次；24 小时无议价消息后重新开始。买家接受报价后仍需卖家人工处理价格。</p>
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
              <span>黑名单/人工接管 → 商品默认回复（新会话首次）→ AI 议价 → 关键词规则 → 商品专属 AI → 系统 AI 兜底</span>
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
.goods-config-dialog { width: min(760px, 100%); max-height: min(820px, calc(100vh - 48px)); display: flex; flex-direction: column; overflow: hidden; border: 1px solid rgba(255,255,255,.65); border-radius: 20px; background: #fffdf8; box-shadow: 0 24px 64px rgba(21, 36, 58, .25); }
.goods-config-dialog__header { position: relative; z-index: 1; display: flex; flex: 0 0 auto; justify-content: space-between; gap: 20px; padding: 24px 28px 20px; border-bottom: 1px solid #eee7d9; background: #fffdf8; }
.goods-config-dialog__eyebrow { margin: 0 0 6px; color: #a56b00; font-size: 12px; font-weight: 700; letter-spacing: .08em; }
.goods-config-dialog h2 { margin: 0; color: #172844; font-size: 21px; }
.goods-config-dialog__header p:not(.goods-config-dialog__eyebrow) { margin: 8px 0 0; color: #758097; font-size: 13px; }
.goods-config-dialog__close { width: 34px; height: 34px; border: 0; border-radius: 10px; background: #f4f5f7; color: #677087; cursor: pointer; font-size: 24px; line-height: 30px; }
.goods-config-dialog__content { min-height: 0; display: grid; flex: 1 1 auto; gap: 14px; overflow: auto; padding: 20px 28px; overscroll-behavior: contain; }
.goods-config-dialog__loading { min-height: 0; flex: 1 1 auto; overflow: auto; padding: 56px; color: #667085; text-align: center; }
.config-section { padding: 18px; border: 1px solid #e6eaf0; border-radius: 14px; background: #fff; }
.config-section__title { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.config-section__sub-option { margin-top: 14px; padding-top: 14px; border-top: 1px dashed #e5eaf1; }
.sku-mapping { margin-top: 16px; padding-top: 16px; border-top: 1px dashed #e5eaf1; }
.sku-mapping__heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.sku-mapping__heading strong { color: #1d2d48; font-size: 14px; }
.sku-mapping__actions { display: flex; flex: none; align-items: center; gap: 8px; }
.sku-mapping__actions > span { padding: 4px 9px; border-radius: 999px; background: #eef4ff; color: #2563c5; font-size: 12px; white-space: nowrap; }
.sku-mapping__sync { min-height: 30px; padding: 5px 10px; border: 1px solid #b9cef4; border-radius: 8px; background: #fff; color: #2563c5; cursor: pointer; font-size: 12px; font-weight: 600; }
.sku-mapping__sync:hover:not(:disabled) { border-color: #7ea7ea; background: #f5f8ff; }
.sku-mapping__sync:disabled { cursor: not-allowed; opacity: .58; }
.sku-mapping__empty { display: grid; gap: 5px; margin-top: 12px; padding: 13px; border: 1px dashed #cfd9e8; border-radius: 10px; background: #f8fafc; }
.sku-mapping__empty strong { color: #405474; font-size: 13px; }
.sku-mapping__empty p { margin: 0; color: #78869a; font-size: 12px; line-height: 1.6; }
.sku-mapping__status { margin: 10px 0 0; color: #506784; font-size: 12px; line-height: 1.6; }
.sku-mapping__status--error { color: #c2413a; }
.sku-mapping__list { display: grid; gap: 10px; margin-top: 12px; }
.sku-mapping__item { display: grid; grid-template-columns: minmax(140px, .8fr) minmax(150px, 1fr) minmax(190px, 1.2fr); align-items: end; gap: 12px; padding: 13px; border: 1px solid #e5eaf1; border-radius: 11px; background: #fbfcfe; }
.sku-mapping__meta { align-self: center; display: grid; gap: 5px; min-width: 0; }
.sku-mapping__meta strong { overflow: hidden; color: #263a58; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.sku-mapping__meta span { color: #7a879b; font-size: 12px; }
.sku-mapping__field { margin-top: 0; }
.config-section h3 { margin: 0; color: #1d2d48; font-size: 15px; }
.config-section p { margin: 6px 0 0; color: #758097; font-size: 13px; line-height: 1.55; }
.field { display: grid; gap: 8px; margin-top: 16px; color: #536079; font-size: 13px; font-weight: 600; }
.field select, .field textarea, .field input { width: 100%; box-sizing: border-box; border: 1px solid #dce3ec; border-radius: 10px; background: #fbfcfe; color: #253651; font: inherit; padding: 10px 12px; outline: none; resize: vertical; }
.field select:focus, .field textarea:focus, .field input:focus { border-color: #4e9aff; box-shadow: 0 0 0 3px rgba(78,154,255,.13); }
.field input:disabled { color: #768198; background: #f2f4f7; }
.config-section--bargain { border-color: #eadfbf; background: #fffef9; }
.bargain-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 14px; }
.bargain-grid__wide { grid-column: 1 / -1; }
.bargain-note { margin-top: 14px !important; padding: 10px 12px; border-radius: 9px; background: #f4f8ff; color: #58708e !important; }
.switch { position: relative; flex: none; width: 44px; height: 24px; cursor: pointer; }
.switch input { opacity: 0; width: 0; height: 0; }
.switch span { position: absolute; inset: 0; border-radius: 99px; background: #d7dde7; transition: .2s; }
.switch span::after { content: ''; position: absolute; top: 3px; left: 3px; width: 18px; height: 18px; border-radius: 50%; background: white; box-shadow: 0 1px 4px rgba(0,0,0,.2); transition: .2s; }
.switch input:checked + span { background: #31c66a; }
.switch input:checked + span::after { transform: translateX(20px); }
.text-action { margin-top: 14px; border: 0; background: transparent; color: #1a79e8; font: inherit; cursor: pointer; padding: 0; }
.text-action:disabled { opacity: .55; cursor: not-allowed; }
.text-action--danger { margin-left: 14px; color: #e15858; }
.default-reply-image { display: grid; gap: 8px; margin-top: 16px; }
.default-reply-image p { margin: 0; }
.field-label { color: #536079; font-size: 13px; font-weight: 600; }
.default-reply-image__input { display: none; }
.default-reply-image__preview { max-width: min(260px, 100%); max-height: 220px; border: 1px solid #dce3ec; border-radius: 10px; object-fit: cover; }
.config-tip { display: flex; gap: 12px; flex-wrap: wrap; padding: 12px 14px; border-radius: 10px; background: #fff6dc; color: #86620d; font-size: 13px; }
.goods-config-dialog__footer { position: relative; z-index: 1; display: flex; flex: 0 0 auto; justify-content: flex-end; gap: 10px; padding: 16px 28px; border-top: 1px solid #eee7d9; background: #fffdf8; box-shadow: 0 -8px 20px rgba(35, 51, 74, .04); }
.btn { min-width: 92px; height: 38px; border-radius: 10px; padding: 0 16px; font: inherit; cursor: pointer; }
.btn--secondary { border: 1px solid #d8e0eb; background: white; color: #49617f; }
.btn--primary { border: 0; background: linear-gradient(135deg, #ffbf00, #f3a800); color: #292013; font-weight: 700; box-shadow: 0 7px 16px rgba(240,174,0,.24); }
.btn:disabled { opacity: .55; cursor: not-allowed; }
.goods-config-fade-enter-active, .goods-config-fade-leave-active { transition: opacity .18s ease; }
.goods-config-fade-enter-from, .goods-config-fade-leave-to { opacity: 0; }
@media (max-width: 620px) { .goods-config-mask { padding: 0; align-items: end; } .goods-config-dialog { width: 100%; max-height: 92vh; border-radius: 20px 20px 0 0; } .goods-config-dialog__header, .goods-config-dialog__content, .goods-config-dialog__footer { padding-left: 18px; padding-right: 18px; } .goods-config-dialog__footer { padding-top: 14px; padding-bottom: calc(14px + env(safe-area-inset-bottom)); } .bargain-grid { grid-template-columns: 1fr; } .bargain-grid__wide { grid-column: auto; } .sku-mapping__heading { align-items: stretch; flex-direction: column; } .sku-mapping__actions { justify-content: space-between; } .sku-mapping__item { grid-template-columns: 1fr; align-items: stretch; } }
</style>

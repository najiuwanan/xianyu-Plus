<script setup lang="ts">
import { ref, watch } from 'vue'
import { updateAccount } from '@/api/account'
import { getItemPolishOverview, saveItemPolishConfig } from '@/api/item-polish'
import { showSuccess, showError } from '@/utils'
import type { Account } from '@/types'

type AccountEditorSection = 'profile' | 'rate' | 'flower' | 'polish'

interface Props {
  modelValue: boolean
  account?: Account | null
  activeSection?: AccountEditorSection
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const formData = ref({
  id: 0,
  accountNote: '',
  autoRateEnabled: 0,
  autoRateText: '',
  autoAskFlower: 0,
  autoAskFlowerText: '',
  itemPolishEnabled: 0,
  itemPolishScheduleTime: '09:00'
})

const polishConfigLoading = ref(false)
const activeSection = ref<AccountEditorSection>('profile')
let polishConfigRequest = 0

const loadPolishConfig = async (accountId: number) => {
  const requestId = ++polishConfigRequest
  polishConfigLoading.value = true
  try {
    const response = await getItemPolishOverview(accountId, 1)
    if (response.code !== 0 && response.code !== 200) throw new Error(response.msg || '加载失败')
    if (requestId !== polishConfigRequest) return
    formData.value.itemPolishEnabled = response.data?.config?.enabled === 1 ? 1 : 0
    formData.value.itemPolishScheduleTime = response.data?.config?.scheduleTime || '09:00'
  } catch (error: any) {
    if (requestId === polishConfigRequest) {
      showError(`加载自动擦亮设置失败：${error.message || '未知错误'}`)
    }
  } finally {
    if (requestId === polishConfigRequest) polishConfigLoading.value = false
  }
}

watch(() => props.account, (newAccount) => {
  if (newAccount) {
    formData.value = {
      id: newAccount.id,
      accountNote: newAccount.accountNote || '',
      autoRateEnabled: newAccount.autoRateEnabled || 0,
      autoRateText: newAccount.autoRateText || '',
      autoAskFlower: newAccount.autoAskFlower || 0,
      autoAskFlowerText: newAccount.autoAskFlowerText || '',
      itemPolishEnabled: 0,
      itemPolishScheduleTime: '09:00'
    }
    void loadPolishConfig(newAccount.id)
  } else {
    polishConfigRequest += 1
    polishConfigLoading.value = false
    formData.value = {
      id: 0,
      accountNote: '',
      autoRateEnabled: 0,
      autoRateText: '',
      autoAskFlower: 0,
      autoAskFlowerText: '',
      itemPolishEnabled: 0,
      itemPolishScheduleTime: '09:00'
    }
  }
}, { immediate: true })

watch(() => props.activeSection, (section) => {
  activeSection.value = section || 'profile'
}, { immediate: true })

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSubmit = async () => {
  if (!formData.value.accountNote.trim()) {
    showError('请输入账号备注')
    return
  }

  try {
    const response = await updateAccount({
      id: formData.value.id,
      accountNote: formData.value.accountNote,
      autoRateEnabled: formData.value.autoRateEnabled,
      autoRateText: formData.value.autoRateText,
      autoAskFlower: formData.value.autoAskFlower,
      autoAskFlowerText: formData.value.autoAskFlowerText
    })
    if (response.code === 0 || response.code === 200) {
      const polishResponse = await saveItemPolishConfig({
        accountId: formData.value.id,
        enabled: formData.value.itemPolishEnabled,
        scheduleTime: formData.value.itemPolishScheduleTime
      })
      if (polishResponse.code !== 0 && polishResponse.code !== 200) {
        throw new Error(polishResponse.msg || '自动擦亮设置保存失败')
      }
      showSuccess('保存成功')
      handleClose()
      emit('success')
    } else {
      throw new Error(response.msg || '保存失败')
    }
  } catch (error: any) {
    console.error('保存失败:', error)
    showError(`保存失败：${error.message || '请稍后重试'}`)
  }
}
</script>

<template>
  <teleport to="body">
    <div v-if="modelValue" class="modal-overlay" @click="handleClose">
      <div class="modal" @click.stop>
        <div class="modal-header">
          <h2 class="modal-title">{{ props.account ? '编辑账号' : '添加账号' }}</h2>
          <button class="modal-close" type="button" aria-label="关闭" @click="handleClose">×</button>
        </div>

        <div class="modal-body">
          <nav class="editor-tabs" aria-label="账号配置分类">
            <button type="button" :class="{ active: activeSection === 'profile' }" @click="activeSection = 'profile'">账号资料</button>
            <button type="button" :class="{ active: activeSection === 'rate' }" @click="activeSection = 'rate'">自动评价</button>
            <button type="button" :class="{ active: activeSection === 'flower' }" @click="activeSection = 'flower'">自动小红花</button>
            <button type="button" :class="{ active: activeSection === 'polish' }" @click="activeSection = 'polish'">每日擦亮</button>
          </nav>

          <section v-if="activeSection === 'profile'" class="profile-section">
            <label class="field-label" for="account-note">账号备注</label>
            <input
              id="account-note"
              v-model="formData.accountNote"
              type="text"
              class="modal-input"
              placeholder="例如：主账号、店铺一"
            />
            <p class="profile-section__hint">用于在系统内区分账号，不会发送给买家。</p>
          </section>

          <div v-else class="automation-section automation-section--tab">
            <div class="automation-section__header">
              <h3>{{ activeSection === 'rate' ? '自动评价' : activeSection === 'flower' ? '自动小红花' : '每日自动擦亮' }}</h3>
              <p>按账号分别保存，不会影响其他账号。</p>
            </div>

            <section v-if="activeSection === 'rate'" class="automation-card" :class="{ 'automation-card--enabled': formData.autoRateEnabled === 1 }">
              <div class="automation-card__row">
                <span class="automation-card__icon automation-card__icon--rate">评</span>
                <div class="automation-card__info">
                  <strong>自动评价</strong>
                  <span>订单完成后自动评价买家</span>
                </div>
                <label class="switch-toggle">
                  <input type="checkbox" v-model="formData.autoRateEnabled" :true-value="1" :false-value="0" />
                  <span class="slider"></span>
                </label>
              </div>
              <div v-if="formData.autoRateEnabled === 1" class="automation-card__content">
                <label class="field-label" for="auto-rate-text">评价文案</label>
                <textarea
                  id="auto-rate-text"
                  v-model="formData.autoRateText"
                  class="modal-input modal-input--textarea"
                  placeholder="留空则使用默认文案：不错的买家！"
                ></textarea>
              </div>
            </section>

            <section v-if="activeSection === 'flower'" class="automation-card" :class="{ 'automation-card--enabled': formData.autoAskFlower === 1 }">
              <div class="automation-card__row">
                <span class="automation-card__icon automation-card__icon--flower">花</span>
                <div class="automation-card__info">
                  <strong>自动求小红花</strong>
                  <span>确认发货成功后请求买家赠送小红花</span>
                </div>
                <label class="switch-toggle">
                  <input type="checkbox" v-model="formData.autoAskFlower" :true-value="1" :false-value="0" />
                  <span class="slider"></span>
                </label>
              </div>
              <p v-if="formData.autoAskFlower === 1" class="automation-card__hint">
                系统会定时处理近 10 天已确认发货的订单，不会额外向买家发送聊天消息。
              </p>
            </section>

            <section v-if="activeSection === 'polish'" class="automation-card" :class="{ 'automation-card--enabled': formData.itemPolishEnabled === 1 }">
              <div class="automation-card__row">
                <span class="automation-card__icon automation-card__icon--polish">亮</span>
                <div class="automation-card__info">
                  <strong>每日自动擦亮</strong>
                  <span>每天先同步在售商品，再逐件执行擦亮</span>
                </div>
                <label class="switch-toggle">
                  <input type="checkbox" v-model="formData.itemPolishEnabled" :true-value="1" :false-value="0" :disabled="polishConfigLoading" />
                  <span class="slider"></span>
                </label>
              </div>
              <div v-if="formData.itemPolishEnabled === 1" class="automation-card__content automation-card__content--time">
                <label class="field-label" for="item-polish-time">每日执行时间</label>
                <input id="item-polish-time" v-model="formData.itemPolishScheduleTime" type="time" class="modal-input automation-time-input" :disabled="polishConfigLoading" />
                <p class="automation-card__hint automation-card__hint--inline">执行记录与“立即擦亮”保留在一键擦亮页面。</p>
              </div>
              <p v-else class="automation-card__hint">关闭后不会执行每日擦亮；仍可在一键擦亮页面手动执行。</p>
            </section>
          </div>
        </div>

        <div class="modal-footer">
          <button class="modal-btn modal-btn-cancel" @click="handleClose">取消</button>
          <div class="modal-divider"></div>
          <button class="modal-btn modal-btn-primary" :disabled="polishConfigLoading" @click="handleSubmit">{{ polishConfigLoading ? '加载设置中…' : '保存' }}</button>
        </div>
      </div>
    </div>
  </teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease;
}

.modal {
  width: min(480px, calc(100vw - 32px));
  max-height: min(85vh, 680px);
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  animation: scaleIn 0.2s ease;
}

.modal-header {
  padding: 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 0.5px solid rgba(0, 0, 0, 0.1);
}

.modal-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: #000;
  line-height: 1.2;
}

.modal-close {
  width: 28px;
  height: 28px;
  padding: 0;
  border: none;
  border-radius: 50%;
  color: rgba(28,28,30,.55);
  background: rgba(60,60,67,.08);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.modal-body {
  padding: 20px;
  overflow-y: auto;
}

.editor-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
  margin-bottom: 20px;
  padding: 5px;
  border-radius: 12px;
  background: rgba(60,60,67,.06);
}

.editor-tabs button {
  min-height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: rgba(28,28,30,.58);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.editor-tabs button.active {
  background: rgba(255,255,255,.95);
  color: #1c1c1e;
  box-shadow: 0 2px 6px rgba(0,0,0,.08);
}

.profile-section {
  min-height: 152px;
}

.profile-section__hint {
  margin: 9px 0 0;
  color: rgba(28,28,30,.52);
  font-size: 12px;
  line-height: 1.5;
}

.field-label {
  display: block;
  margin-bottom: 8px;
  color: rgba(28,28,30,.55);
  font-size: 13px;
  font-weight: 500;
}

.modal-input {
  width: 100%;
  height: 42px;
  border-radius: 12px;
  border: none;
  padding: 0 12px;
  font-size: 15px;
  background: rgba(0, 0, 0, 0.05);
  color: #000;
  outline: none;
  transition: all 0.2s ease;
  box-sizing: border-box;
}

.modal-input--textarea {
  height: 76px;
  padding: 10px 12px;
  resize: vertical;
  font-family: inherit;
  line-height: 1.5;
}

.modal-input:hover {
  background: rgba(0, 0, 0, 0.08);
}

.modal-input:focus {
  background: rgba(0, 0, 0, 0.08);
}

.modal-input::placeholder {
  color: #999;
}

.automation-section {
  margin-top: 24px;
}

.automation-section--tab {
  margin-top: 0;
}

.automation-section__header {
  margin-bottom: 10px;
}

.automation-section__header h3 {
  margin: 0 0 4px;
  color: #1c1c1e;
  font-size: 15px;
  font-weight: 600;
}

.automation-section__header p {
  margin: 0;
  color: rgba(28,28,30,.55);
  font-size: 12px;
}

.automation-card {
  margin-top: 10px;
  padding: 14px;
  border: 1px solid rgba(60,60,67,.10);
  border-radius: 14px;
  background: rgba(60,60,67,.035);
  transition: border-color .2s ease, background .2s ease;
}

.automation-card--enabled {
  border-color: rgba(52,199,89,.28);
  background: rgba(52,199,89,.055);
}

.automation-card__row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.automation-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.automation-card__icon--rate {
  color: #5856d6;
  background: rgba(88,86,214,.12);
}

.automation-card__icon--flower {
  color: #e87911;
  background: rgba(255,149,0,.14);
}

.automation-card__icon--polish {
  color: #b26a00;
  background: rgba(255,190,0,.16);
}

.automation-card__info {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.automation-card__info strong {
  color: #1c1c1e;
  font-size: 14px;
}

.automation-card__info span,
.automation-card__hint {
  color: rgba(28,28,30,.55);
  font-size: 12px;
  line-height: 1.45;
}

.automation-card__content {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(60,60,67,.10);
}

.automation-card__content--time { display: flex; flex-direction: column; }
.automation-time-input { width: 150px; }

.automation-card__hint {
  margin: 10px 0 0 42px;
}

.automation-card__hint--inline { margin-left: 0; }

.switch-toggle {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  flex-shrink: 0;
}

.switch-toggle input {
  width: 0;
  height: 0;
  opacity: 0;
}

.slider {
  position: absolute;
  inset: 0;
  border-radius: 24px;
  background: #e5e5ea;
  cursor: pointer;
  transition: background .2s ease;
}

.slider::after {
  position: absolute;
  left: 2px;
  top: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 4px rgba(0,0,0,.2);
  content: '';
  transition: transform .2s ease;
}

.switch-toggle input:checked + .slider {
  background: #34c759;
}

.switch-toggle input:checked + .slider::after {
  transform: translateX(20px);
}

.modal-footer {
  display: flex;
  height: 48px;
  border-top: 0.5px solid rgba(0, 0, 0, 0.1);
}

.modal-btn {
  flex: 1;
  border: none;
  background: transparent;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
  -webkit-tap-highlight-color: transparent;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.modal-btn:active {
  opacity: 0.5;
}

.modal-btn-cancel {
  color: #666;
}

.modal-btn-primary {
  color: #007aff;
  font-weight: 500;
}

.modal-btn:disabled { cursor: not-allowed; opacity: .5; }

.modal-divider {
  width: 0.5px;
  background: rgba(0, 0, 0, 0.1);
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes scaleIn {
  from {
    transform: scale(0.9);
    opacity: 0;
  }
  to {
    transform: scale(1);
    opacity: 1;
  }
}
</style>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getAccountStatusText, formatTime } from '@/utils'
import type { Account } from '@/types'

import IconEdit from '@/components/icons/IconEdit.vue'
import IconTrash from '@/components/icons/IconTrash.vue'
import IconEmpty from '@/components/icons/IconEmpty.vue'
import IconClock from '@/components/icons/IconClock.vue'
import IconCheck from '@/components/icons/IconCheck.vue'
import IconAlert from '@/components/icons/IconAlert.vue'
import IconLink from '@/components/icons/IconLink.vue'

interface Props {
  accounts: Account[]
  loading?: boolean
}

interface Emits {
  (e: 'edit', account: Account): void
  (e: 'delete', id: number): void
  (e: 'toggleEnabled', account: Account): void
  (e: 'resumeAutomation', account: Account): void
  (e: 'connection', account: Account): void
}

defineProps<Props>()
const emit = defineEmits<Emits>()

const isMobile = ref(false)
const openedActionMenuId = ref<number | null>(null)
const checkScreenSize = () => {
  isMobile.value = window.innerWidth < 768
}

onMounted(() => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  window.addEventListener('click', closeActionMenu)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
  window.removeEventListener('click', closeActionMenu)
})

const closeActionMenu = () => {
  openedActionMenuId.value = null
}

const toggleActionMenu = (event: MouseEvent, accountId: number) => {
  event.stopPropagation()
  openedActionMenuId.value = openedActionMenuId.value === accountId ? null : accountId
}

const runAction = (event: MouseEvent, action: () => void) => {
  event.stopPropagation()
  closeActionMenu()
  action()
}

const getStatusColor = (status: number) => {
  if (status === 0) return '#8e8e93'
  const info = getAccountStatusText(status)
  switch (info.type) {
    case 'success': return '#34c759'
    case 'warning': return '#ff9500'
    case 'danger': return '#ff3b30'
    default: return '#007aff'
  }
}

const getStatusBg = (status: number) => {
  if (status === 0) return 'rgba(120,120,128,.12)'
  const info = getAccountStatusText(status)
  switch (info.type) {
    case 'success': return 'rgba(52, 199, 89, 0.1)'
    case 'warning': return 'rgba(255, 149, 0, 0.1)'
    case 'danger': return 'rgba(255, 59, 48, 0.1)'
    default: return 'rgba(0, 122, 255, 0.1)'
  }
}

const getStatusRing = (status: number) => {
  if (status === 0) return '0 0 0 4px rgba(120,120,128,.12)'
  const info = getAccountStatusText(status)
  switch (info.type) {
    case 'success': return '0 0 0 4px rgba(52,199,89,.10)'
    case 'warning': return '0 0 0 4px rgba(255,149,0,.12)'
    case 'danger': return '0 0 0 4px rgba(255,59,48,.10)'
    default: return '0 0 0 4px rgba(0,122,255,.10)'
  }
}

const getStatusDescription = (status: number) => {
  if (status === 1) return '账号状态正常'
  if (status === 0) return '实时连接与自动化已暂停'
  if (status === -2) return '请在账号详情完成验证'
  if (status === -1) return '请检查账号连接状态'
  return '请检查账号连接状态'
}

const isEnabled = (value?: number) => value === 1
const isRiskPaused = (account: Account) => account.automationRiskPaused === 1
const canToggleEnabled = (account: Account) => account.status === 1 || account.status === 0
</script>

<template>
  <!-- Mobile: Card View -->
  <div v-if="isMobile" class="card-list" :class="{ 'card-list--loading': loading }">
    <div
      v-for="account in accounts"
      :key="account.id"
      class="account-card"
    >
      <div class="account-card__header">
        <div class="account-card__avatar">
          {{ (account.accountNote || account.unb || '未').charAt(0) }}
        </div>
        <div class="account-card__info">
          <span class="account-card__name">{{ account.accountNote || '未命名账号' }}</span>
          <span class="account-card__unb">UNB: {{ account.unb }}</span>
        </div>
        <span
          class="account-card__status"
          :style="{
            color: getStatusColor(account.status),
            background: getStatusBg(account.status)
          }"
        >
          <component :is="getAccountStatusText(account.status).type === 'success' ? IconCheck : IconAlert" />
          {{ getAccountStatusText(account.status).text }}
        </span>
      </div>

      <div class="account-card__body">
        <div class="account-card__row">
          <span class="account-card__label">ID</span>
          <span class="account-card__value">{{ account.id }}</span>
        </div>
        <div class="account-card__row">
          <div class="account-card__label-icon"><IconClock /></div>
          <span class="account-card__label">创建</span>
          <span class="account-card__value">{{ formatTime(account.createdTime) }}</span>
        </div>
        <div class="account-card__row">
          <div class="account-card__label-icon"><IconClock /></div>
          <span class="account-card__label">更新</span>
          <span class="account-card__value">{{ formatTime(account.updatedTime) }}</span>
        </div>
      </div>

      <div class="account-card__automation">
        <span class="account-card__automation-label">自动化</span>
        <div class="account-card__automation-list">
          <span class="automation-chip" :class="{ 'automation-chip--on': isEnabled(account.autoRateEnabled) }">
            自动评价 {{ isEnabled(account.autoRateEnabled) ? '已开启' : '已关闭' }}
          </span>
          <span class="automation-chip" :class="{ 'automation-chip--on': isEnabled(account.autoAskFlower) }">
            自动求花 {{ isEnabled(account.autoAskFlower) ? '已开启' : '已关闭' }}
          </span>
          <span v-if="isRiskPaused(account)" class="automation-chip automation-chip--risk" :title="account.automationRiskPauseReason">
            自动化已保护暂停
          </span>
          <small v-if="isRiskPaused(account)" class="automation-risk-reason">{{ account.automationRiskPauseReason || '连续自动化失败，等待人工确认' }}</small>
        </div>
      </div>

      <div class="account-card__footer">
        <button class="account-card__btn account-card__btn--connection" @click="emit('connection', account)">
          <IconLink />
          <span>账号详情</span>
        </button>
        <div class="account-card__more-wrap" @click.stop>
          <button class="account-card__btn account-card__btn--more" type="button" @click="toggleActionMenu($event, account.id)">
            <span>更多操作</span>
            <span class="account-card__more-caret">⌄</span>
          </button>
          <Transition name="action-menu">
            <div v-if="openedActionMenuId === account.id" class="account-action-menu account-action-menu--mobile" role="menu">
              <button type="button" role="menuitem" @click="runAction($event, () => emit('edit', account))"><IconEdit /> 设置账号</button>
              <button
                type="button"
                role="menuitem"
                :disabled="!canToggleEnabled(account)"
                :title="canToggleEnabled(account) ? '' : '当前账号需先处理连接或验证问题'"
                @click="runAction($event, () => emit('toggleEnabled', account))"
              >{{ account.status === 0 ? '启用账号' : '禁用账号' }}</button>
              <button type="button" role="menuitem" :disabled="!isRiskPaused(account)" @click="runAction($event, () => emit('resumeAutomation', account))">恢复自动化</button>
              <div class="account-action-menu__divider"></div>
              <button type="button" role="menuitem" class="account-action-menu__danger" @click="runAction($event, () => emit('delete', account.id))"><IconTrash /> 删除账号</button>
            </div>
          </Transition>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="!loading && accounts.length === 0" class="empty-state">
      <div class="empty-state__icon"><IconEmpty /></div>
      <p class="empty-state__text">暂无账号数据</p>
    </div>
  </div>

  <!-- Desktop/Tablet: Table View -->
  <div v-else class="table-container" :class="{ 'table-container--loading': loading }">
    <table class="table" v-if="accounts.length > 0">
      <thead class="table__head">
        <tr>
          <th class="table__th table__th--account">账号</th>
          <th class="table__th table__th--status">账号状态</th>
          <th class="table__th table__th--automation">自动化</th>
          <th class="table__th table__th--time">最近更新</th>
          <th class="table__th table__th--actions">操作</th>
        </tr>
      </thead>
      <tbody class="table__body">
        <tr v-for="account in accounts" :key="account.id" class="table__tr">
          <td class="table__td table__td--account">
            <strong>{{ account.accountNote || '未命名账号' }}</strong>
            <span>UNB：{{ account.unb }} · ID：{{ account.id }}</span>
          </td>
          <td class="table__td table__td--status">
            <div class="account-status">
              <span class="account-status__dot" :style="{ background: getStatusColor(account.status), boxShadow: getStatusRing(account.status) }"></span>
              <div class="account-status__text">
                <strong :style="{ color: getStatusColor(account.status) }">{{ getAccountStatusText(account.status).text }}</strong>
                <span>{{ getStatusDescription(account.status) }}</span>
              </div>
            </div>
          </td>
          <td class="table__td table__td--automation">
            <div class="automation-list">
              <span class="automation-chip" :class="{ 'automation-chip--on': isEnabled(account.autoRateEnabled) }">
                <i></i>自动评价 {{ isEnabled(account.autoRateEnabled) ? '已开启' : '已关闭' }}
              </span>
              <span class="automation-chip" :class="{ 'automation-chip--on': isEnabled(account.autoAskFlower) }">
                <i></i>自动求花 {{ isEnabled(account.autoAskFlower) ? '已开启' : '已关闭' }}
              </span>
              <span v-if="isRiskPaused(account)" class="automation-chip automation-chip--risk" :title="account.automationRiskPauseReason">
                <i></i>自动化已保护暂停
              </span>
              <small v-if="isRiskPaused(account)" class="automation-risk-reason">{{ account.automationRiskPauseReason || '连续自动化失败，等待人工确认' }}</small>
            </div>
          </td>
          <td class="table__td table__td--time">{{ formatTime(account.updatedTime) }}</td>
          <td class="table__td table__td--actions">
            <div class="table__action-group">
              <button class="table__action table__action--connection" @click="emit('connection', account)">
                <IconLink />
                <span>账号详情</span>
              </button>
              <div class="table__action-menu-wrap" @click.stop>
                <button class="table__action table__action--more" type="button" @click="toggleActionMenu($event, account.id)">
                  <span>更多操作</span>
                  <span class="table__action-caret">⌄</span>
                </button>
                <Transition name="action-menu">
                  <div v-if="openedActionMenuId === account.id" class="account-action-menu" role="menu">
                    <button type="button" role="menuitem" @click="runAction($event, () => emit('edit', account))"><IconEdit /> 设置账号</button>
                    <button
                      type="button"
                      role="menuitem"
                      :disabled="!canToggleEnabled(account)"
                      :title="canToggleEnabled(account) ? '' : '当前账号需先处理连接或验证问题'"
                      @click="runAction($event, () => emit('toggleEnabled', account))"
                    >{{ account.status === 0 ? '启用账号' : '禁用账号' }}</button>
                    <button type="button" role="menuitem" :disabled="!isRiskPaused(account)" @click="runAction($event, () => emit('resumeAutomation', account))">恢复自动化</button>
                    <div class="account-action-menu__divider"></div>
                    <button type="button" role="menuitem" class="account-action-menu__danger" @click="runAction($event, () => emit('delete', account.id))"><IconTrash /> 删除账号</button>
                  </div>
                </Transition>
              </div>
            </div>
          </td>
        </tr>
      </tbody>
    </table>

    <!-- Empty State -->
    <div v-if="!loading && accounts.length === 0" class="empty-state">
      <div class="empty-state__icon"><IconEmpty /></div>
      <p class="empty-state__text">暂无账号数据</p>
    </div>
  </div>
</template>

<style scoped>
/* ============================================================
   Shared Tokens (Liquid Glass Design System)
   ============================================================ */
.card-list,
.table-container {
  --c-bg: transparent;
  --c-surface: rgba(255,255,255,0.55);
  --c-surface-hover: rgba(255,255,255,0.72);
  --c-surface-float: rgba(255,255,255,0.85);
  --c-border: rgba(255,255,255,0.75);
  --c-border-in: rgba(255,255,255,0.45);
  --c-border-strong: rgba(60,60,67,.12);
  --c-text-1: #1c1c1e;
  --c-text-2: rgba(28,28,30,.55);
  --c-text-3: rgba(28,28,30,.55);
  --c-accent: #0A84FF;
  --c-danger: #FF453A;
  --c-success: #30D158;
  --c-r-sm: 10px;
  --c-r-md: 14px;
  --c-r-lg: 22px;
  --c-ease: 0.2s cubic-bezier(0.25, 0.1, 0.25, 1);
  --c-blur: blur(28px) saturate(1.8);
  --c-shadow-sm: 0 8px 32px rgba(0,0,0,0.10), 0 1.5px 4px rgba(0,0,0,0.06);
  --c-shadow-md: 0 16px 48px rgba(0,0,0,0.16), 0 2px 8px rgba(0,0,0,0.08);
}

/* ============================================================
   Mobile List View (iOS 26 Glass Card Style)
   ============================================================ */
.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  padding-bottom: 24px;
  min-height: 100%;
  background: transparent;
}

.account-card {
  background: var(--c-surface);
  -webkit-backdrop-filter: var(--c-blur);
  backdrop-filter: var(--c-blur);
  border: 1px solid var(--c-border);
  border-radius: var(--c-r-lg);
  padding: 16px;
  transition: all var(--c-ease);
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.account-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 10%;
  right: 10%;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.9) 30%, rgba(255,255,255,0.9) 70%, transparent);
  border-radius: 1px;
  pointer-events: none;
}

@media (hover: hover) {
  .account-card:hover {
    background: var(--c-surface-hover);
    box-shadow: var(--c-shadow-md);
    border-color: rgba(255,255,255,0.85);
  }
}

.account-card:active {
  transform: scale(0.98);
}

.account-card__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 0;
  padding-bottom: 12px;
  border-bottom: 0.5px solid rgba(60,60,67,.12);
}

.account-card__avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(10,132,255,0.85);
  border: 1px solid rgba(255,255,255,0.35);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  flex-shrink: 0;
  box-shadow: 0 4px 16px rgba(10,132,255,0.35);
}

.account-card__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.account-card__name {
  font-size: 16px;
  font-weight: 600;
  color: var(--c-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.2;
}

.account-card__unb {
  font-size: 13px;
  color: var(--c-text-3);
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-card__status {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 20px;
  line-height: 1;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.3);
}

.account-card__status svg {
  width: 13px;
  height: 13px;
}

.account-card__body {
  margin-bottom: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 16px;
  padding: 12px 0;
  border-top: 0.5px solid rgba(60,60,67,.12);
  border-bottom: 0.5px solid rgba(60,60,67,.12);
}

.account-card__row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  line-height: 1.4;
}

.account-card__label-icon {
  width: 14px;
  height: 14px;
  color: var(--c-text-3);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.account-card__label-icon svg {
  width: 12px;
  height: 12px;
}

.account-card__label {
  color: var(--c-text-3);
  flex-shrink: 0;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.account-card__value {
  color: var(--c-text-1);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.account-card__footer {
  display: flex;
  gap: 10px;
  padding-top: 0;
  border-top: none;
}

.account-card__more-wrap {
  position: relative;
  display: flex;
  flex: 1;
}

.account-card__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex: 1;
  height: 40px;
  font-size: 14px;
  font-weight: 500;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all var(--c-ease);
  -webkit-tap-highlight-color: transparent;
  background: transparent;
}

.account-card__btn svg {
  width: 16px;
  height: 16px;
}

.account-card__btn--edit {
  color: white;
  background: rgba(10,132,255,0.85);
  border: 1px solid rgba(255,255,255,0.35);
  box-shadow: 0 4px 16px rgba(10,132,255,0.35);
}

.account-card__btn--connection {
  color: #2368b7;
  background: rgba(10,132,255,.10);
  border-color: rgba(10,132,255,.24);
}

.account-card__btn--more {
  width: 100%;
  color: var(--c-text-2);
  background: rgba(60,60,67,.06);
  border-color: rgba(60,60,67,.13);
}

.account-card__more-caret,
.table__action-caret {
  margin-left: 2px;
  font-size: 15px;
  line-height: 1;
  transform: translateY(-1px);
}

@media (hover: hover) {
  .account-card__btn--edit:hover {
    background: rgba(10,132,255,0.95);
    box-shadow: 0 6px 20px rgba(10,132,255,0.45);
  }
}

.account-card__btn--edit:active {
  transform: scale(0.97);
}

.account-card__btn--toggle {
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
}

.account-card__btn--disable {
  color: #b26a00;
  background: rgba(255,159,10,.14);
  border-color: rgba(255,159,10,.28);
}

.account-card__btn--enable {
  color: #168a3c;
  background: rgba(48,209,88,.14);
  border-color: rgba(48,209,88,.28);
}

.account-card__btn--delete {
  color: var(--c-danger);
  background: rgba(255,69,58,.15);
  border: 1px solid rgba(255,69,58,.25);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
}

@media (hover: hover) {
  .account-card__btn--delete:hover {
    background: rgba(255,69,58,.22);
    border-color: rgba(255,69,58,.35);
  }
}

.account-card__btn--delete:active {
  transform: scale(0.97);
}

/* ============================================================
   Desktop Table View (Liquid Glass)
   ============================================================ */
.table-container {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

/* Table Head */
.table__head {
  position: sticky;
  top: 0;
  z-index: 10;
}

.table__th {
  text-align: left;
  padding: 12px 16px;
  font-size: 12px;
  font-weight: 600;
  color: #1c1c1e;
  letter-spacing: .4px;
  background: rgba(255,255,255,0.55);
  backdrop-filter: blur(16px) saturate(1.6);
  -webkit-backdrop-filter: blur(16px) saturate(1.6);
  white-space: nowrap;
  user-select: none;
  border-bottom: 1px solid rgba(60,60,67,.12);
}

.table__th--account { min-width: 220px; }
.table__th--status { width: 170px; }
.table__th--automation { width: 250px; }
.table__th--time { width: 164px; }
.table__th--actions { width: 196px; text-align: center; }

/* Table Body */

.table__tr {
  transition: background .12s;
}

.table__tr:not(:last-child) .table__td {
  border-bottom: 0.5px solid rgba(60,60,67,.12);
}

@media (hover: hover) {
  .table__tr:hover .table__td {
    background: rgba(255,255,255,.08);
  }
}

.table__td {
  padding: 12px 16px;
  color: var(--c-text-1);
  white-space: nowrap;
  background: transparent;
  transition: background var(--c-ease);
  line-height: 1.5;
}

.table__td--id {
  color: var(--c-text-3);
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}

.table__td--account {
  min-width: 220px;
}

.table__td--account strong,
.table__td--account span {
  display: block;
  max-width: 250px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table__td--account strong {
  color: var(--c-text-1);
  font-size: 13px;
  font-weight: 600;
}

.table__td--account span {
  margin-top: 2px;
  color: var(--c-text-3);
  font-size: 11px;
}

.table__td--time {
  color: var(--c-text-2);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.table__td--status {
  min-width: 180px;
}

.table__td--automation {
  min-width: 220px;
}

.table__td--actions {
  text-align: center;
}

.account-status {
  display: inline-flex;
  align-items: center;
  gap: 9px;
}

.account-status__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.account-status__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.account-status__text strong {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
}

.account-status__text span {
  color: var(--c-text-3);
  font-size: 11px;
  line-height: 1.25;
}

.automation-list,
.account-card__automation-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.automation-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid rgba(60,60,67,.12);
  border-radius: 999px;
  background: rgba(60,60,67,.05);
  color: var(--c-text-3);
  font-size: 11px;
  font-weight: 500;
  line-height: 1.2;
  white-space: nowrap;
}

.automation-chip i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
  opacity: .7;
}

.automation-chip--on {
  color: #16803d;
  background: rgba(52,199,89,.10);
  border-color: rgba(52,199,89,.22);
}

.automation-chip--risk {
  color: #b54708;
  background: #fffaeb;
  border-color: #fedf89;
}

.automation-risk-reason {
  display: block;
  width: 100%;
  color: #b54708;
  font-size: 11px;
  line-height: 1.4;
}

.account-card__automation {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 0;
}

.account-card__automation-label {
  color: var(--c-text-3);
  font-size: 12px;
  flex-shrink: 0;
}

.account-card__automation-list {
  flex: 1;
}

.table__action-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.table__action-menu-wrap {
  position: relative;
}

/* Status Tag */
.status-tag {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 20px;
  line-height: 1;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.3);
}

/* Action Buttons in Table */
.table__action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  font-size: 12px;
  font-weight: 500;
  border-radius: 8px;
  border: 1px solid var(--c-border-in);
  cursor: pointer;
  transition: all var(--c-ease);
  -webkit-tap-highlight-color: transparent;
  background: var(--c-surface);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  color: var(--c-text-2);
}

.table__action svg {
  width: 14px;
  height: 14px;
}

.table__action--edit {
  color: var(--c-accent);
  border-color: rgba(10,132,255,.25);
  background: rgba(10,132,255,.12);
}

.table__action--connection {
  color: #2368b7;
  border-color: rgba(10,132,255,.25);
  background: rgba(10,132,255,.08);
}

.table__action--more {
  color: var(--c-text-2);
  border-color: rgba(60,60,67,.14);
  background: rgba(60,60,67,.05);
}

.table__action--disable {
  color: #b26a00;
  border-color: rgba(255,159,10,.30);
  background: rgba(255,159,10,.12);
}

.table__action--enable {
  color: #168a3c;
  border-color: rgba(48,209,88,.30);
  background: rgba(48,209,88,.12);
}

@media (hover: hover) {
  .table__action--disable:hover { background: rgba(255,159,10,.20); }
  .table__action--enable:hover { background: rgba(48,209,88,.20); }
}

@media (hover: hover) {
  .table__action--edit:hover {
    background: rgba(10,132,255,.18);
    border-color: rgba(10,132,255,.35);
  }
}

.table__action--delete {
  color: var(--c-danger);
  border-color: rgba(255,69,58,.25);
  background: rgba(255,69,58,.12);
}

@media (hover: hover) {
  .table__action--delete:hover {
    background: rgba(255,69,58,.18);
    border-color: rgba(255,69,58,.35);
  }
}

.table__action:active {
  transform: scale(0.95);
}

.account-action-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 30;
  width: 164px;
  padding: 6px;
  border: 1px solid rgba(60,60,67,.14);
  border-radius: 10px;
  background: rgba(255,255,255,.98);
  box-shadow: 0 14px 28px rgba(30,42,60,.16);
}

.account-action-menu button {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #34445b;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}

.account-action-menu button:hover:not(:disabled) {
  background: rgba(255,191,0,.12);
  color: #332900;
}

.account-action-menu button:disabled {
  color: #a6adb7;
  cursor: not-allowed;
}

.account-action-menu button svg {
  width: 14px;
  height: 14px;
}

.account-action-menu__divider {
  height: 1px;
  margin: 5px 3px;
  background: rgba(60,60,67,.11);
}

.account-action-menu .account-action-menu__danger {
  color: #d83a35;
}

.account-action-menu .account-action-menu__danger:hover {
  background: rgba(255,69,58,.10);
  color: #bd302b;
}

.account-action-menu--mobile {
  top: auto;
  right: 0;
  bottom: calc(100% + 8px);
}

.action-menu-enter-active,
.action-menu-leave-active {
  transition: opacity .14s ease, transform .14s ease;
}

.action-menu-enter-from,
.action-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ============================================================
   Empty State
   ============================================================ */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 16px;
  gap: 12px;
}

.empty-state__icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--c-text-3);
  opacity: 0.35;
}

.empty-state__icon svg {
  width: 36px;
  height: 36px;
}

.empty-state__text {
  font-size: 14px;
  color: var(--c-text-3);
}

/* ============================================================
   Loading State
   ============================================================ */
.card-list--loading,
.table-container--loading {
  opacity: 0.5;
  pointer-events: none;
}

/* ============================================================
   Responsive
   ============================================================ */
@media screen and (max-width: 480px) {
  .card-list {
    padding: 12px;
    gap: 10px;
  }

  .account-card {
    padding: 14px;
  }

  .account-card__avatar {
    width: 40px;
    height: 40px;
    font-size: 16px;
  }

  .account-card__name {
    font-size: 15px;
  }

  .account-card__unb {
    font-size: 12px;
  }

  .account-card__body {
    gap: 10px 12px;
    padding: 10px 0;
  }

  .account-card__row {
    font-size: 12px;
  }

  .account-card__value {
    font-size: 13px;
  }

  .account-card__btn {
    height: 38px;
    font-size: 13px;
  }

  .empty-state {
    padding: 40px 16px;
  }
}
</style>

<script setup lang="ts">
import { inject, defineComponent, h, onMounted } from 'vue'
import { useOperationLog } from './useOperationLog'
import './operation-log.css'
import '@/styles/header-selectors.css'

import IconLog from '@/components/icons/IconLog.vue'
import IconChevronDown from '@/components/icons/IconChevronDown.vue'
import IconChevronLeft from '@/components/icons/IconChevronLeft.vue'
import IconChevronRight from '@/components/icons/IconChevronRight.vue'
import IconClock from '@/components/icons/IconClock.vue'
import IconRefresh from '@/components/icons/IconRefresh.vue'
import IconEmpty from '@/components/icons/IconEmpty.vue'
import IconInfo from '@/components/icons/IconInfo.vue'
import IconTrash from '@/components/icons/IconTrash.vue'

const {
  loading,
  accounts,
  selectedAccountId,
  logs,
  total,
  page,
  pageSize,
  totalPages,
  isMobile,
  mobileView,
  selectedAccountForMobile,
  selectAccount,
  handlePageChange,
  handleRefresh,
  handleClearLogs,
  clearingLogs,
  goBackToAccounts,
  getAccountAvatar,
  getAccountName,
  getLogAccountName,
  getLogAccountUnb,
  getOperationTypeText,
  getOperationTypeClass,
  getStatusText,
  getStatusClass,
  formatTime,
  formatDuration,
  handleAccountSelectChange
} = useOperationLog()

// 导航栏注入
const setHeaderContent = inject<(content: any) => void>('setHeaderContent')

const HeaderSelectors = defineComponent({
  setup() {
    return () => h('div', { class: 'header-selectors' }, [
      h('div', { class: 'header-select-wrap' }, [
        h('select', {
          class: 'header-select',
          onChange: (e: Event) => {
            const val = (e.target as HTMLSelectElement).value
            selectedAccountId.value = val ? parseInt(val) : null
            handleAccountSelectChange()
          }
        }, [
          h('option', { value: '', disabled: true, selected: !selectedAccountId.value }, '账号'),
          ...accounts.value.map(acc =>
            h('option', {
              value: acc.id.toString(),
              selected: selectedAccountId.value === acc.id
            }, acc.accountNote || acc.unb)
          )
        ]),
        h(IconChevronDown, { class: 'header-select-icon' })
      ]),
      h('button', {
        class: ['header-refresh-btn', { 'header-refresh-btn--loading': loading.value }],
        disabled: loading.value,
        onClick: handleRefresh
      }, [
        h(IconRefresh, { class: 'header-refresh-icon' })
      ]),
      h('button', {
        class: 'header-clear-btn',
        disabled: !selectedAccountId.value || clearingLogs.value,
        title: '清空当前账号操作记录',
        onClick: handleClearLogs
      }, [
        h(IconTrash, { class: 'header-clear-icon' })
      ])
    ])
  }
})

onMounted(() => {
  if (setHeaderContent) setHeaderContent(HeaderSelectors)
})
</script>

<template>
  <div class="ol">
    <!-- Header -->
    <div class="ol__header">
      <div class="ol__title-row">
        <div class="ol__title-icon">
          <IconLog />
        </div>
        <h1 class="ol__title">操作记录</h1>
      </div>
      <div class="ol__actions">
        <!-- Account Select -->
        <div class="ol__header-select-wrap">
          <select
            v-model="selectedAccountId"
            class="ol__header-select"
            @change="handleAccountSelectChange"
          >
            <option :value="null" disabled>选择账号</option>
            <option
              v-for="account in accounts"
              :key="account.id"
              :value="account.id"
            >
              {{ getAccountName(account) }}
            </option>
          </select>
          <span class="ol__select-icon">
            <IconChevronDown />
          </span>
        </div>
        <button class="btn btn--secondary" @click="handleRefresh">
          <IconRefresh />
          <span class="mobile-hidden">刷新</span>
        </button>
        <button
          class="btn btn--danger"
          :disabled="!selectedAccountId || clearingLogs"
          @click="handleClearLogs"
        >
          <IconTrash />
          <span>{{ clearingLogs ? '清空中…' : '清空记录' }}</span>
        </button>
      </div>
    </div>

    <!-- Body -->
    <div class="ol__body">
      <!-- Mobile: Account Panel -->
      <div
        v-if="isMobile"
        class="ol__account-panel"
        :class="{ 'ol__account-panel--hidden': mobileView === 'logs' }"
      >
        <div class="ol__account-toolbar">
          <span class="ol__account-toolbar-title">闲鱼账号</span>
          <span v-if="accounts.length > 0" class="ol__account-toolbar-count">共 {{ accounts.length }} 个</span>
        </div>

        <!-- Loading -->
        <div v-if="loading && accounts.length === 0" class="ol__loading">
          <div class="ol__spinner"></div>
          <span>加载中...</span>
        </div>

        <!-- Mobile: Account List -->
        <div v-else class="ol__account-list">
          <div
            v-for="account in accounts"
            :key="account.id"
            class="ol__account-item"
            :class="{ 'ol__account-item--active': selectedAccountId === account.id }"
            @click="selectAccount(account.id, account)"
          >
            <div class="ol__account-avatar">{{ getAccountAvatar(account) }}</div>
            <div class="ol__account-info">
              <div class="ol__account-name">{{ getAccountName(account) }}</div>
              <div class="ol__account-id">ID: {{ account.id }}</div>
            </div>
          </div>

          <!-- Empty -->
          <div v-if="accounts.length === 0" class="ol__empty">
            <IconEmpty />
            <span class="ol__empty-text">暂无账号数据</span>
          </div>
        </div>
      </div>

      <!-- Logs Panel -->
      <div
        class="ol__logs-panel"
        :class="{ 'ol__logs-panel--hidden': isMobile && mobileView === 'accounts' }"
      >
        <!-- Mobile Header -->
        <div v-if="isMobile" class="ol__logs-toolbar">
          <button class="ol__back-btn" @click="goBackToAccounts">
            <IconChevronLeft />
            返回
          </button>
          <div v-if="selectedAccountForMobile" style="display:flex;align-items:center;gap:6px;">
            <div class="ol__account-avatar" style="width:28px;height:28px;font-size:13px;">
              {{ getAccountAvatar(selectedAccountForMobile) }}
            </div>
            <span style="font-size:13px;font-weight:500;color:var(--d-text-primary);max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
              {{ getAccountName(selectedAccountForMobile) }}
            </span>
          </div>
        </div>

        <!-- Desktop Header -->
        <template v-if="!isMobile">
          <div v-if="!selectedAccountId" class="ol__empty" style="border:none;">
            <IconInfo />
            <span class="ol__empty-text">请选择一个账号查看操作记录</span>
          </div>
        </template>

        <template v-if="selectedAccountId">
          <!-- Desktop Logs Table -->
          <div v-if="!isMobile" class="ol__logs-content">
            <!-- Loading -->
            <div v-if="loading" class="ol__loading">
              <div class="ol__spinner"></div>
              <span>加载中...</span>
            </div>

            <!-- Table -->
            <table v-if="!loading && logs.length > 0" class="ol__logs-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>账号</th>
                  <th>操作类型</th>
                  <th>操作描述</th>
                  <th>状态</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="log in logs" :key="log.id">
                  <td style="font-size:12px;color:var(--d-text-tertiary);">{{ log.id }}</td>
                  <td>
                    <div class="ol__log-account">
                      <span class="ol__log-account-name" :title="getLogAccountName(log)">{{ getLogAccountName(log) }}</span>
                      <span class="ol__log-account-unb" :title="`UNB: ${getLogAccountUnb(log)}`">UNB: {{ getLogAccountUnb(log) }}</span>
                    </div>
                  </td>
                  <td>
                    <span class="ol__log-type" :class="`ol__log-type--${getOperationTypeClass(log.operationType)}`">
                      {{ getOperationTypeText(log.operationType) }}
                    </span>
                  </td>
                  <td>
                    <span class="ol__log-desc" :title="log.operationDesc">{{ log.operationDesc || '-' }}</span>
                  </td>
                  <td>
                    <span class="ol__log-status" :class="`ol__log-status--${getStatusClass(log.operationStatus)}`">
                      {{ getStatusText(log.operationStatus) }}
                    </span>
                  </td>
                  <td>
                    <span class="ol__log-time">{{ formatTime(log.createTime) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>

            <!-- Empty -->
            <div v-if="!loading && logs.length === 0" class="ol__empty">
              <IconEmpty />
              <span class="ol__empty-text">暂无操作记录</span>
            </div>
          </div>

          <!-- Mobile Logs List -->
          <div v-if="isMobile" class="ol__logs-content">
            <!-- Loading -->
            <div v-if="loading" class="ol__loading">
              <div class="ol__spinner"></div>
              <span>加载中...</span>
            </div>

            <!-- Log Cards -->
            <template v-if="!loading">
              <div
                v-for="log in logs"
                :key="log.id"
                class="ol__log-card"
              >
                <div class="ol__log-card-header">
                  <span class="ol__log-type" :class="`ol__log-type--${getOperationTypeClass(log.operationType)}`">
                    {{ getOperationTypeText(log.operationType) }}
                  </span>
                  <span class="ol__log-status" :class="`ol__log-status--${getStatusClass(log.operationStatus)}`">
                    {{ getStatusText(log.operationStatus) }}
                  </span>
                </div>
                <div class="ol__log-card-account">
                  <span class="ol__log-account-name">{{ getLogAccountName(log) }}</span>
                  <span class="ol__log-account-unb">UNB: {{ getLogAccountUnb(log) }}</span>
                </div>
                <div class="ol__log-card-desc">{{ log.operationDesc || '-' }}</div>
                <div class="ol__log-card-meta">
                  <span class="ol__log-card-meta-item">
                    <IconClock />
                    {{ formatTime(log.createTime) }}
                  </span>
                  <span v-if="log.durationMs" class="ol__log-card-meta-item">
                    {{ formatDuration(log.durationMs) }}
                  </span>
                </div>
              </div>

              <!-- Empty -->
              <div v-if="logs.length === 0" class="ol__empty">
                <IconEmpty />
                <span class="ol__empty-text">暂无操作记录</span>
              </div>
            </template>
          </div>

          <!-- Pagination -->
          <div v-if="totalPages > 1" class="ol__pagination">
            <button
              class="ol__page-btn"
              :class="{ 'ol__page-btn--disabled': page <= 1 }"
              @click="handlePageChange(page - 1)"
            >
              <IconChevronLeft />
            </button>

            <template v-for="p in (() => {
              const btns: number[] = []
              const max = 5
              let start = Math.max(1, page - Math.floor(max / 2))
              const end = Math.min(totalPages, start + max - 1)
              start = Math.max(1, end - max + 1)
              for (let i = start; i <= end; i++) btns.push(i)
              return btns
            })()" :key="p">
              <button
                class="ol__page-btn"
                :class="{ 'ol__page-btn--active': p === page }"
                @click="handlePageChange(p)"
              >
                {{ p }}
              </button>
            </template>

            <button
              class="ol__page-btn"
              :class="{ 'ol__page-btn--disabled': page >= totalPages }"
              @click="handlePageChange(page + 1)"
            >
              <IconChevronRight />
            </button>

            <span class="ol__page-info">{{ page }} / {{ totalPages }}</span>
          </div>
        </template>
      </div>
    </div>

  </div>
</template>

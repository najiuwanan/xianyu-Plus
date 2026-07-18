<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAccountManager } from './useAccountManager'
import AccountTable from './components/AccountTable.vue'
import AddAccountDialog from './components/AddAccountDialog.vue'
import ManualAddDialog from './components/ManualAddDialog.vue'
import QRLoginDialog from './components/QRLoginDialog.vue'
import DeleteConfirmDialog from './components/DeleteConfirmDialog.vue'
import ConnectionDetail from '@/views/connection/components/ConnectionDetail.vue'
import type { Account } from '@/types'

import IconQrCode from '@/components/icons/IconQrCode.vue'
import IconPlus from '@/components/icons/IconPlus.vue'
import IconSync from '@/components/icons/IconSync.vue'

type AccountEditorSection = 'profile' | 'rate' | 'flower' | 'polish'

const route = useRoute()
const router = useRouter()
const selectedConnectionAccountId = ref<number | null>(null)
const activeEditorSection = ref<AccountEditorSection>('profile')

const {
  loading,
  accounts,
  dialogs,
  currentAccount,
  deleteAccountId,
  loadAccounts,
  showAddDialog,
  showManualAddDialog,
  showQRLoginDialog,
  editAccount,
  deleteAccount,
  toggleAccountEnabled,
  resumeAutomation
} = useAccountManager();

const selectedConnectionAccount = computed(() =>
  accounts.value.find(account => Number(account.id) === selectedConnectionAccountId.value) || null
)

const syncConnectionSelection = () => {
  const requestedId = Number(route.query.accountId)
  if (route.query.connection === '1' && requestedId && accounts.value.some(account => Number(account.id) === requestedId)) {
    selectedConnectionAccountId.value = requestedId
  }
}

const openConnection = (account: Account) => {
  selectedConnectionAccountId.value = Number(account.id)
  router.replace({
    path: '/accounts',
    query: { ...route.query, connection: '1', accountId: String(account.id) }
  })
}

const closeConnection = () => {
  selectedConnectionAccountId.value = null
  const { accountId: _accountId, connection: _connection, ...rest } = route.query
  router.replace({ path: '/accounts', query: rest })
}

const openAccountEditor = (account: Account, section: AccountEditorSection = 'profile') => {
  activeEditorSection.value = section
  editAccount(account)
}

watch([accounts, () => route.query.accountId, () => route.query.connection], syncConnectionSelection, { immediate: true })

void loadAccounts();
</script>

<template>
  <div class="accounts">
    <!-- Page Header -->
    <header class="accounts__header">
      <div class="accounts__title-row">
        <h1 class="accounts__title mobile-hidden">闲鱼账号</h1>
      </div>
      <div class="accounts__actions desktop-only">
        <button class="btn btn--primary" @click="showQRLoginDialog">
          <IconQrCode />
          <span>扫码添加</span>
        </button>
        <button class="btn btn--secondary" @click="showManualAddDialog">
          <IconPlus />
          <span>手动添加</span>
        </button>
        <button
          class="btn btn--ghost"
          :class="{ 'btn--loading': loading }"
          @click="loadAccounts"
          :disabled="loading"
        >
          <IconSync />
          <span>刷新</span>
        </button>
      </div>
    </header>

    <!-- Content Card -->
    <section class="accounts__content" :class="{ 'accounts__content--detail-open': selectedConnectionAccount }">
      <div class="accounts__workspace">
      <div class="accounts__table-wrap">
        <AccountTable
          :accounts="accounts"
          :loading="loading"
          @edit="openAccountEditor"
          @automation-settings="(account, section) => openAccountEditor(account, section)"
          @delete="deleteAccount"
          @toggle-enabled="toggleAccountEnabled"
          @resume-automation="resumeAutomation"
          @connection="openConnection"
        />
      </div>
      <aside v-if="selectedConnectionAccount" class="accounts__connection-panel">
        <header class="accounts__connection-header">
          <div>
            <strong>账号与连接详情</strong>
            <span>{{ selectedConnectionAccount.accountNote || selectedConnectionAccount.unb }}</span>
          </div>
          <button type="button" class="accounts__connection-close" aria-label="关闭账号详情" @click="closeConnection">×</button>
        </header>
        <div class="accounts__connection-body">
          <ConnectionDetail
            :account-id="selectedConnectionAccount.id"
            :account-name="selectedConnectionAccount.accountNote || selectedConnectionAccount.unb"
            :auto-connect-on-startup="selectedConnectionAccount.autoConnectOnStartup"
          />
        </div>
      </aside>
      </div>
    </section>

    <!-- Mobile Bottom Actions -->
    <footer class="accounts__footer mobile-only">
      <button class="btn btn--primary btn--full" @click="showQRLoginDialog">
        <IconQrCode />
        <span>扫码添加</span>
      </button>
      <button class="btn btn--secondary btn--full" @click="showManualAddDialog">
        <IconPlus />
        <span>手动添加</span>
      </button>
    </footer>

    <!-- Dialogs -->
    <AddAccountDialog
      v-model="dialogs.add"
      :account="currentAccount"
      :active-section="activeEditorSection"
      @success="loadAccounts"
    />
    <ManualAddDialog v-model="dialogs.manualAdd" @success="loadAccounts" />
    <QRLoginDialog v-model="dialogs.qrLogin" @success="loadAccounts" />
    <DeleteConfirmDialog
      v-model="dialogs.deleteConfirm"
      :account-id="deleteAccountId"
      @success="loadAccounts"
    />
  </div>
</template>

<style scoped src="./accounts.css"></style>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, provide, ref, shallowRef } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import NavMenu from './NavMenu.vue'
import UserMenu from './UserMenu.vue'
import { getSystemUpdateStatus, pollOnlineUpdateStatus, startOnlineUpdate, type OnlineUpdateExecution, type SystemUpdateStatus } from '@/api/system'

const route = useRoute()

const headerContent = shallowRef<any>(null)
const isMobile = ref(false)
const isTablet = ref(false)
const isDesktop = ref(true)
const drawerVisible = ref(false)
const updateStatus = ref<SystemUpdateStatus | null>(null)
const updateChecking = ref(false)
const versionDialogVisible = ref(false)
const updateExecution = ref<OnlineUpdateExecution | null>(null)
const updateConfirmVisible = ref(false)
const updateStarting = ref(false)
const updateConnectionLost = ref(false)
let updatePollTimer: number | undefined

const activeUpdateStates = ['QUEUED', 'RUNNING', 'RESTARTING']
const updateIsActive = computed(() => !!updateExecution.value && activeUpdateStates.includes(updateExecution.value.state))
const updateStageLabels: Record<string, string> = {
  QUEUED: '排队等待', PREPARING: '环境检查', BACKUP: '备份数据库', DOWNLOADING: '下载代码',
  BUILDING: '构建镜像', DATABASE_CHECK: '数据库检查', RESTARTING: '正在重启',
  HEALTH_CHECK: '健康检查', COMPLETED: '更新完成', FAILED: '更新失败', STATUS_ERROR: '状态异常'
}
const updateStageLabel = computed(() => updateStageLabels[updateExecution.value?.stage || ''] || '准备更新')

const displayVersion = (version?: string) => version ? `V${version.replace(/^[vV]/, '')}` : '未知版本'
const updateSummary = computed(() => {
  if (!updateStatus.value) return '正在检查 GitHub 更新…'
  const current = displayVersion(updateStatus.value.currentVersion)
  const latest = displayVersion(updateStatus.value.latestVersion)
  if (updateStatus.value.currentVersion || updateStatus.value.latestVersion) {
    return `当前 ${current} · 最新 ${latest}${updateStatus.value.updateAvailable ? ' · 可更新' : ''}`
  }
  return updateStatus.value.message
})

const pageTitleMap: Record<string, string> = {
  '/dashboard': '运营总览',
  '/accounts': '账号管理',
  '/goods': '商品列表',
  '/orders': '订单管理',
  '/product-publish': '发布商品',
  '/product-materials': '商品素材库',
  '/messages': '在线客服',
  '/blacklist': '黑名单',
  '/notifications': '通知渠道',
  '/kami-config': '卡券管理',
  '/item-polish': '一键擦亮',
  '/order-automation': '自动化执行中心',
  '/auto-reply': '关键词回复',
  '/operation-log': '操作日志',
  '/runtime-log': '实时日志',
  '/system-check': '系统自检',
  '/settings': '系统设置'
}

const currentPageTitle = computed(() => pageTitleMap[route.path] || 'XianYuPlus')

const setHeaderContent = (content: any) => {
  headerContent.value = content
}

provide('setHeaderContent', setHeaderContent)

const checkScreenSize = () => {
  const width = window.innerWidth
  isMobile.value = width < 768
  isTablet.value = width >= 768 && width < 1024
  isDesktop.value = width >= 1024
  if (isDesktop.value) drawerVisible.value = false
}

const toggleDrawer = () => {
  drawerVisible.value = !drawerVisible.value
}

const closeDrawer = () => {
  drawerVisible.value = false
}

const loadUpdateStatus = async (forceRefresh = false) => {
  updateChecking.value = true
  try {
    const response = await getSystemUpdateStatus(forceRefresh)
    if (response.code === 0 || response.code === 200) {
      updateStatus.value = response.data || null
    }
  } catch {
    updateStatus.value = {
      versionTracked: false,
      updateAvailable: false,
      message: '暂时无法检查 GitHub 更新，请稍后重试'
    }
  } finally {
    updateChecking.value = false
  }
}

const loadOnlineUpdateStatus = async () => {
  try {
    const status = await pollOnlineUpdateStatus()
    const wasActive = updateIsActive.value
    updateExecution.value = status
    updateConnectionLost.value = false
    if (wasActive && status.state === 'SUCCEEDED') {
      await loadUpdateStatus(true)
    }
  } catch {
    if (updateIsActive.value) updateConnectionLost.value = true
  }
}

const beginUpdatePolling = () => {
  if (updatePollTimer) window.clearInterval(updatePollTimer)
  updatePollTimer = window.setInterval(loadOnlineUpdateStatus, 2500)
}

const openVersionDialog = () => {
  versionDialogVisible.value = true
  updateConfirmVisible.value = false
  loadOnlineUpdateStatus().then(() => {
    if (updateIsActive.value) beginUpdatePolling()
  })
}

const confirmOnlineUpdate = async () => {
  updateStarting.value = true
  try {
    const response = await startOnlineUpdate()
    updateExecution.value = response.data || null
    updateConfirmVisible.value = false
    updateConnectionLost.value = false
    beginUpdatePolling()
  } finally {
    updateStarting.value = false
  }
}

onMounted(() => {
  checkScreenSize()
  loadUpdateStatus()
  loadOnlineUpdateStatus().then(() => {
    if (updateIsActive.value) beginUpdatePolling()
  })
  window.addEventListener('resize', checkScreenSize)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
  if (updatePollTimer) window.clearInterval(updatePollTimer)
})
</script>

<template>
  <div class="app-layout">
    <div v-if="isDesktop" class="layout-container">
      <aside class="sidebar">
        <div class="brand">
          <span class="brand__mark" aria-hidden="true">XP</span>
          <span class="brand__copy">
            <strong>XianYuPlus</strong>
            <small>智能经营助手</small>
          </span>
        </div>
        <NavMenu />
      </aside>

      <section class="workspace">
        <header class="workspace-header">
          <div class="workspace-header__spacer"></div>
          <div class="workspace-header__actions">
            <div class="workspace-notice" :class="{ 'workspace-notice--available': updateStatus?.updateAvailable }" aria-live="polite">
              <span class="workspace-notice__icon" aria-hidden="true">{{ updateStatus?.updateAvailable ? '↑' : 'i' }}</span>
              <strong>系统公告</strong>
              <span class="workspace-notice__message" :title="updateStatus?.message">{{ updateSummary }}</span>
              <button v-if="updateStatus" type="button" class="workspace-notice__detail" @click="openVersionDialog">版本详情</button>
              <button type="button" :disabled="updateChecking" @click="loadUpdateStatus(true)">{{ updateChecking ? '检查中…' : '检查更新' }}</button>
            </div>
            <span class="today-status"><span aria-hidden="true">☼</span> 今天，生意顺利</span>
            <UserMenu />
          </div>
        </header>
        <main class="workspace-main">
          <RouterView />
        </main>
      </section>
    </div>

    <template v-else>
      <header class="compact-header">
        <button class="menu-toggle-btn" type="button" aria-label="打开导航菜单" @click="toggleDrawer">
          <span></span><span></span><span></span>
        </button>
        <strong>{{ currentPageTitle }}</strong>
        <div v-if="headerContent" class="header-content-slot"><component :is="headerContent" /></div>
        <UserMenu />
      </header>
      <main class="workspace-main workspace-main--compact">
        <RouterView />
      </main>
    </template>

    <transition name="drawer">
      <div v-if="(isMobile || isTablet) && drawerVisible" class="drawer-overlay" @click="closeDrawer">
        <aside class="drawer-menu" @click.stop>
          <div class="drawer-header">
            <div class="brand brand--drawer">
              <span class="brand__mark" aria-hidden="true">XP</span>
              <span class="brand__copy"><strong>XianYuPlus</strong><small>智能经营助手</small></span>
            </div>
            <button class="drawer-close-btn" type="button" aria-label="关闭导航菜单" @click="closeDrawer">×</button>
          </div>
          <div class="drawer-content"><NavMenu @select="closeDrawer" /></div>
        </aside>
      </div>
    </transition>

    <div v-if="versionDialogVisible && updateStatus" class="version-mask" @click.self="versionDialogVisible = false">
      <section class="version-dialog" role="dialog" aria-modal="true" aria-labelledby="version-dialog-title">
        <header>
          <div><span>版本更新</span><h2 id="version-dialog-title">XianYuPlus {{ displayVersion(updateStatus.latestVersion) }}</h2></div>
          <button type="button" aria-label="关闭" @click="versionDialogVisible = false">×</button>
        </header>
        <div class="version-dialog__versions">
          <div><small>当前版本</small><strong>{{ displayVersion(updateStatus.currentVersion) }}</strong><code v-if="updateStatus.currentCommit">{{ updateStatus.currentCommit }}</code></div>
          <span>→</span>
          <div class="is-latest"><small>GitHub 最新版本</small><strong>{{ displayVersion(updateStatus.latestVersion) }}</strong><code v-if="updateStatus.latestCommit">{{ updateStatus.latestCommit }}</code></div>
        </div>
        <p class="version-dialog__status" :class="{ available: updateStatus.updateAvailable }">{{ updateStatus.message }}</p>
        <div class="version-dialog__changes">
          <h3>{{ updateStatus.updateAvailable ? '本次可以更新的内容' : '当前版本主要内容' }}</h3>
          <ul v-if="updateStatus.updateHighlights?.length">
            <li v-for="item in updateStatus.updateHighlights" :key="item">{{ item }}</li>
          </ul>
          <p v-else>{{ updateStatus.latestMessage || '暂无版本说明' }}</p>
        </div>
        <div v-if="updateConfirmVisible" class="online-update-confirm">
          <div class="online-update-confirm__title"><strong>确认在线更新</strong><span>更新期间请勿关闭设备或停止 Docker</span></div>
          <div class="online-update-confirm__facts">
            <div><small>预计总耗时</small><strong>约 3～10 分钟</strong><span>下载依赖和构建镜像耗时最长</span></div>
            <div><small>预计服务中断</small><strong>约 30～120 秒</strong><span>仅在新镜像构建完成后重启</span></div>
          </div>
          <ul>
            <li>更新前自动备份数据库，并保留最近 5 份备份。</li>
            <li>构建阶段现有服务继续运行；进入“正在重启”后网页可能暂时失联。</li>
            <li>页面会自动重连并继续显示进度，健康检查失败时将尝试恢复旧镜像。</li>
          </ul>
          <div class="online-update-confirm__actions">
            <button type="button" :disabled="updateStarting" @click="updateConfirmVisible = false">暂不更新</button>
            <button type="button" class="is-primary" :disabled="updateStarting" @click="confirmOnlineUpdate">{{ updateStarting ? '正在提交…' : '确认并开始更新' }}</button>
          </div>
        </div>
        <div v-if="updateExecution && (updateIsActive || updateExecution.state === 'SUCCEEDED' || updateExecution.state === 'FAILED')" class="online-update-progress" :class="`is-${updateExecution.state.toLowerCase()}`">
          <div class="online-update-progress__head">
            <div><small>{{ updateStageLabel }}</small><strong>{{ updateExecution.message }}</strong></div>
            <b>{{ updateExecution.progress }}%</b>
          </div>
          <div class="online-update-progress__track"><i :style="{ width: `${updateExecution.progress}%` }"></i></div>
          <p v-if="updateConnectionLost" class="online-update-progress__offline"><span></span>应用正在重启，连接暂时中断；页面会自动重试，无需手动刷新。</p>
          <p v-else-if="updateExecution.state === 'RESTARTING'">预计中断不超过 {{ updateExecution.estimatedDowntimeSeconds }} 秒，正在自动等待服务恢复。</p>
          <details v-if="updateExecution.logs?.length">
            <summary>查看更新日志（最近 {{ updateExecution.logs.length }} 行）</summary>
            <pre>{{ updateExecution.logs.join('\n') }}</pre>
          </details>
        </div>
        <footer>
          <span v-if="!updateExecution?.enabled">首次启用命令：<code>cd ~/xianyu-Plus && ./update.sh</code></span>
          <span v-else>{{ updateIsActive ? '更新正在后台安全执行，请保持设备通电。' : '在线更新代理已就绪' }}</span>
          <a v-if="updateStatus.updateUrl" :href="updateStatus.updateUrl" target="_blank" rel="noopener noreferrer">查看 GitHub</a>
          <button v-if="updateStatus.updateAvailable && updateExecution?.enabled && !updateIsActive && !updateConfirmVisible" type="button" class="online-update-button" @click="updateConfirmVisible = true">立即在线更新</button>
          <button type="button" @click="versionDialogVisible = false">关闭</button>
        </footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.app-layout { height: 100vh; overflow: hidden; background: var(--xy-page); color: var(--xy-ink); }
.layout-container, .workspace { display: flex; min-width: 0; height: 100%; }
.layout-container { width: 100%; }
.workspace { flex: 1; flex-direction: column; overflow: hidden; }

.sidebar { width: 236px; flex: 0 0 236px; display: flex; flex-direction: column; overflow: hidden; background: var(--xy-surface); border-right: 1px solid var(--xy-border); }
.brand { width: 100%; display: flex; align-items: center; gap: 11px; padding: 24px 22px 18px; border: 0; border-bottom: 1px solid var(--xy-border-soft); background: transparent; color: var(--xy-ink); text-align: left; }
.brand__mark { width: 38px; height: 38px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 10px; background: var(--xy-amber); color: #172d4f; font-size: 16px; font-weight: 800; letter-spacing: -1.5px; }
.brand__copy { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.brand__copy strong { color: #182d4f; font-size: 18px; letter-spacing: -0.5px; line-height: 22px; }
.brand__copy small { color: var(--xy-muted); font-size: 11px; line-height: 16px; }

.workspace-header { height: 70px; display: flex; flex: 0 0 70px; align-items: center; justify-content: space-between; padding: 0 32px; border-bottom: 1px solid var(--xy-border-soft); background: rgba(255, 255, 255, .88); }
.workspace-header__actions { min-width: 0; display: flex; align-items: center; gap: 14px; }
.workspace-notice { min-width: 0; max-width: 520px; display: inline-flex; align-items: center; gap: 7px; padding: 5px 7px 5px 9px; border: 1px solid #dce7f7; border-radius: 999px; background: #f7fbff; color: #47627f; font-size: 12px; white-space: nowrap; }
.workspace-notice--available { border-color: #efd07a; background: #fffbec; color: #77590a; }
.workspace-notice__icon { width: 18px; height: 18px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 50%; background: #e7f1ff; color: #2672cf; font-size: 12px; font-weight: 800; }
.workspace-notice--available .workspace-notice__icon { background: #fff0bd; color: #a66d00; }
.workspace-notice strong { flex: 0 0 auto; color: var(--xy-ink); font-size: 12px; }
.workspace-notice__message { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.workspace-notice a, .workspace-notice button { min-height: 24px; padding: 0 7px; border: 1px solid #ccd8e7; border-radius: 999px; background: var(--xy-surface); color: #385879; font-size: 11px; font-weight: 700; line-height: 22px; text-decoration: none; white-space: nowrap; cursor: pointer; }
.workspace-notice .workspace-notice__detail { border-color: #b9d5f6; color: #1768bd; background: #fff; }
.workspace-notice a { border-color: #e4bd47; background: var(--xy-amber); color: #583f00; }
.workspace-notice button:disabled { cursor: not-allowed; opacity: .6; }
.today-status { display: inline-flex; align-items: center; gap: 7px; padding: 7px 12px; border: 1px solid var(--xy-border); border-radius: 999px; color: #4c5d78; font-size: 13px; white-space: nowrap; }
.today-status span { color: var(--xy-amber-deep); font-size: 18px; line-height: 14px; }
.version-mask { position: fixed; inset: 0; z-index: 2000; display: grid; place-items: center; padding: 20px; background: rgba(20, 31, 48, .42); backdrop-filter: blur(3px); }
.version-dialog { width: min(620px, 100%); overflow: hidden; border: 1px solid rgba(255,255,255,.7); border-radius: 20px; background: #fff; box-shadow: 0 28px 80px rgba(20,31,48,.28); }
.version-dialog > header { display: flex; align-items: flex-start; justify-content: space-between; padding: 22px 24px 18px; border-bottom: 1px solid #edf0f4; background: linear-gradient(135deg,#f5f9ff,#fffaf0); }
.version-dialog > header span { color: #2c70c9; font-size: 12px; font-weight: 800; letter-spacing: .08em; }
.version-dialog > header h2 { margin: 4px 0 0; color: #1b2d49; font-size: 23px; }
.version-dialog > header button { border: 0; background: transparent; color: #68758a; font-size: 27px; cursor: pointer; }
.version-dialog__versions { display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; gap: 16px; padding: 20px 24px 10px; }
.version-dialog__versions > div { display: grid; gap: 5px; padding: 14px; border: 1px solid #e5eaf1; border-radius: 13px; background: #fafbfd; }
.version-dialog__versions > div.is-latest { border-color: #f0d27d; background: #fffbec; }
.version-dialog__versions small { color: #7a8799; }.version-dialog__versions strong { color: #1d3557; font-size: 20px; }.version-dialog__versions code { color: #8190a4; font-size: 11px; }
.version-dialog__status { margin: 8px 24px 0; padding: 10px 12px; border-radius: 10px; color: #315f91; background: #edf6ff; font-size: 13px; }.version-dialog__status.available { color: #805900; background: #fff4cf; }
.version-dialog__changes { padding: 18px 24px 20px; }.version-dialog__changes h3 { margin: 0 0 10px; color: #283b57; font-size: 15px; }.version-dialog__changes ul { display: grid; gap: 8px; margin: 0; padding-left: 20px; color: #53627a; font-size: 13px; line-height: 1.55; }.version-dialog__changes p { color: #718096; font-size: 13px; }
.online-update-confirm,.online-update-progress { margin: 0 24px 20px; padding: 16px; border-radius: 13px; }
.online-update-confirm { border: 1px solid #f0cc6d; background: #fffaf0; }.online-update-confirm__title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.online-update-confirm__title strong { color: #684c05; }.online-update-confirm__title span { color: #9a6f00; font-size: 11px; }.online-update-confirm__facts { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin: 13px 0; }.online-update-confirm__facts div { display: grid; gap: 3px; padding: 10px; border: 1px solid #f1dfaa; border-radius: 9px; background: #fff; }.online-update-confirm__facts small { color: #8a7441; }.online-update-confirm__facts strong { color: #3d4d63; font-size: 15px; }.online-update-confirm__facts span { color: #7c8796; font-size: 11px; }.online-update-confirm ul { display: grid; gap: 5px; margin: 0; padding-left: 19px; color: #647087; font-size: 12px; line-height: 1.45; }.online-update-confirm__actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 14px; }.online-update-confirm__actions button { padding: 8px 13px; border: 1px solid #d8dfE9; border-radius: 8px; background: #fff; color: #4d6079; font-weight: 700; cursor: pointer; }.online-update-confirm__actions button.is-primary,.version-dialog > footer .online-update-button { border-color: #dfaa12; background: #f7bb18; color: #563d00; }.online-update-confirm__actions button:disabled { opacity: .6; cursor: wait; }
.online-update-progress { border: 1px solid #cfe0f4; background: #f6faff; }.online-update-progress.is-succeeded { border-color: #a9e6bf; background: #f3fcf6; }.online-update-progress.is-failed { border-color: #f4b8b4; background: #fff6f5; }.online-update-progress__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }.online-update-progress__head div { display: grid; gap: 4px; }.online-update-progress__head small { color: #3474bb; font-weight: 800; }.online-update-progress__head strong { color: #253a58; font-size: 13px; }.online-update-progress__head b { color: #286bb6; font-size: 18px; }.online-update-progress__track { height: 8px; margin: 13px 0 9px; overflow: hidden; border-radius: 99px; background: #dfe9f5; }.online-update-progress__track i { height: 100%; display: block; border-radius: inherit; background: linear-gradient(90deg,#3b8eea,#f2b516); transition: width .35s ease; }.online-update-progress p { margin: 0; color: #65758b; font-size: 12px; }.online-update-progress__offline { display: flex; align-items: center; gap: 7px; color: #986b00 !important; }.online-update-progress__offline span { width: 8px; height: 8px; border-radius: 50%; background: #f0ad00; box-shadow: 0 0 0 4px #fff0bd; animation: update-pulse 1.2s infinite; }.online-update-progress details { margin-top: 12px; }.online-update-progress summary { color: #496784; font-size: 12px; cursor: pointer; }.online-update-progress pre { max-height: 180px; overflow: auto; margin: 8px 0 0; padding: 10px; border-radius: 8px; background: #182538; color: #dce8f6; font: 11px/1.55 ui-monospace,Consolas,monospace; white-space: pre-wrap; }.online-update-progress.is-succeeded .online-update-progress__track i { background: #2db568; }.online-update-progress.is-failed .online-update-progress__track i { background: #db5148; }
@keyframes update-pulse { 50% { opacity: .45; transform: scale(.75); } }
.version-dialog > footer { display: flex; align-items: center; gap: 9px; padding: 14px 24px; border-top: 1px solid #edf0f4; background: #fafbfd; }.version-dialog > footer span { min-width: 0; margin-right: auto; color: #6f7e92; font-size: 11px; }.version-dialog > footer span code { color: #335b87; }.version-dialog > footer a,.version-dialog > footer button { padding: 8px 13px; border: 1px solid #d5deea; border-radius: 9px; background: #fff; color: #315b89; font-size: 12px; font-weight: 700; text-decoration: none; cursor: pointer; }
.workspace-main { flex: 1; min-width: 0; overflow: auto; padding: 28px 32px 36px; background: var(--xy-page); }

.compact-header { height: 60px; display: flex; align-items: center; gap: 12px; padding: 0 18px; border-bottom: 1px solid var(--xy-border); background: var(--xy-surface); }
.compact-header strong { min-width: 0; flex: 1; overflow: hidden; color: var(--xy-ink); font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.workspace-main--compact { padding: 20px; }
.header-content-slot { display: flex; align-items: center; gap: 8px; }
.menu-toggle-btn { width: 38px; height: 38px; display: grid; align-content: center; gap: 4px; padding: 0 10px; border: 1px solid var(--xy-border); border-radius: 8px; background: var(--xy-surface); cursor: pointer; }
.menu-toggle-btn span { height: 2px; border-radius: 2px; background: var(--xy-ink); }

.drawer-overlay { position: fixed; inset: 0; z-index: 1000; background: rgba(22, 34, 55, .36); }
.drawer-menu { width: min(300px, 86vw); height: 100%; display: flex; flex-direction: column; overflow: hidden; background: var(--xy-surface); box-shadow: 16px 0 40px rgba(20, 40, 70, .16); }
.drawer-header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--xy-border-soft); }
.brand--drawer { flex: 1; border: 0; }
.drawer-close-btn { width: 36px; height: 36px; display: grid; place-items: center; margin-right: 16px; border: 1px solid var(--xy-border); border-radius: 8px; background: var(--xy-surface); color: var(--xy-muted); font-size: 22px; line-height: 1; cursor: pointer; }
.drawer-content { flex: 1; overflow: auto; padding: 8px 0 16px; }
.drawer-enter-active, .drawer-leave-active { transition: opacity .2s ease; }
.drawer-enter-active .drawer-menu, .drawer-leave-active .drawer-menu { transition: transform .2s ease; }
.drawer-enter-from, .drawer-leave-to { opacity: 0; }
.drawer-enter-from .drawer-menu, .drawer-leave-to .drawer-menu { transform: translateX(-100%); }

@media (max-width: 1180px) { .workspace-notice { max-width: 340px; } }
@media (max-width: 1023px) { .workspace-main { padding: 24px; } }
@media (max-width: 767px) { .workspace-main, .workspace-main--compact { padding: 16px; } .compact-header { height: 56px; padding: 0 14px; } .header-content-slot { max-width: 52%; overflow: hidden; }.version-dialog { max-height: calc(100vh - 24px); overflow-y: auto; }.version-dialog__versions,.online-update-confirm__facts { grid-template-columns: 1fr; }.version-dialog__versions > span { display: none; }.online-update-confirm__title { align-items: flex-start; flex-direction: column; }.version-dialog > footer { align-items: stretch; flex-wrap: wrap; }.version-dialog > footer span { width: 100%; }.online-update-confirm,.online-update-progress { margin-right: 14px; margin-left: 14px; } }
</style>

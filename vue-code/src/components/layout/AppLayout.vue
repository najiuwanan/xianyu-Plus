<script setup lang="ts">
import { computed, onMounted, onUnmounted, provide, ref, shallowRef } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import NavMenu from './NavMenu.vue'
import UserMenu from './UserMenu.vue'
import { getSystemUpdateStatus, type SystemUpdateStatus } from '@/api/system'

const route = useRoute()

const headerContent = shallowRef<any>(null)
const isMobile = ref(false)
const isTablet = ref(false)
const isDesktop = ref(true)
const drawerVisible = ref(false)
const updateStatus = ref<SystemUpdateStatus | null>(null)
const updateChecking = ref(false)

const pageTitleMap: Record<string, string> = {
  '/dashboard': '运营总览',
  '/accounts': '账号管理',
  '/goods': '商品管理',
  '/orders': '订单管理',
  '/messages': '在线客服',
  '/notifications': '通知渠道',
  '/kami-config': '卡券管理',
  '/auto-delivery': '自动发货',
  '/item-polish': '一键擦亮',
  '/order-automation': '自动化执行中心',
  '/auto-reply': '自动回复',
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

onMounted(() => {
  checkScreenSize()
  loadUpdateStatus()
  window.addEventListener('resize', checkScreenSize)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
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
              <span class="workspace-notice__message">{{ updateStatus?.message || '正在检查 GitHub 更新…' }}</span>
              <a v-if="updateStatus?.updateAvailable && updateStatus.updateUrl" :href="updateStatus.updateUrl" target="_blank" rel="noopener noreferrer">查看更新</a>
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
.workspace-notice a { border-color: #e4bd47; background: var(--xy-amber); color: #583f00; }
.workspace-notice button:disabled { cursor: not-allowed; opacity: .6; }
.today-status { display: inline-flex; align-items: center; gap: 7px; padding: 7px 12px; border: 1px solid var(--xy-border); border-radius: 999px; color: #4c5d78; font-size: 13px; white-space: nowrap; }
.today-status span { color: var(--xy-amber-deep); font-size: 18px; line-height: 14px; }
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
@media (max-width: 767px) { .workspace-main, .workspace-main--compact { padding: 16px; } .compact-header { height: 56px; padding: 0 14px; } .header-content-slot { max-width: 52%; overflow: hidden; } }
</style>

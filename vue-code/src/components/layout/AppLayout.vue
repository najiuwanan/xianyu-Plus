<script setup lang="ts">
import { computed, onMounted, onUnmounted, provide, ref, shallowRef } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import NavMenu from './NavMenu.vue'
import UpdateDialog from './UpdateDialog.vue'
import { checkUpdate } from '@/api/system'
import { getAuthUsername } from '@/utils/request'

const route = useRoute()
const router = useRouter()

declare const __APP_VERSION__: string

const currentVersion = ref(__APP_VERSION__ || '1.0.0')
const hasNewVersion = ref(false)
const updateDialog = ref<InstanceType<typeof UpdateDialog> | null>(null)
const headerContent = shallowRef<any>(null)
const isMobile = ref(false)
const isTablet = ref(false)
const isDesktop = ref(true)
const drawerVisible = ref(false)

const pageTitleMap: Record<string, string> = {
  '/dashboard': '运营总览',
  '/accounts': '账号管理',
  '/connection': '连接管理',
  '/goods': '商品管理',
  '/orders': '订单管理',
  '/messages': '在线客服',
  '/notifications': '通知渠道',
  '/kami-config': '卡券管理',
  '/auto-delivery': '自动发货',
  '/item-polish': '一键擦亮',
  '/order-automation': '自动化执行中心',
  '/exception-center': '异常中心',
  '/auto-reply': '自动回复',
  '/operation-log': '操作日志',
  '/runtime-log': '实时日志',
  '/system-check': '系统自检',
  '/settings': '系统设置'
}

const currentPageTitle = computed(() => pageTitleMap[route.path] || 'XianYuPlus')
const currentUserName = computed(() => getAuthUsername() || '管理员')
const userInitial = computed(() => currentUserName.value.slice(0, 1).toUpperCase() || 'X')

const setHeaderContent = (content: any) => {
  headerContent.value = content
}

provide('setHeaderContent', setHeaderContent)

const loadVersion = async () => {
  try {
    const updateRes = await checkUpdate()
    hasNewVersion.value = updateRes.data?.hasUpdate === true
  } catch {
    // 版本检查失败不影响页面使用。
  }
}

const openUpdateDialog = () => updateDialog.value?.open()
const openSettings = () => router.push('/settings')

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

onMounted(() => {
  checkScreenSize()
  window.addEventListener('resize', checkScreenSize)
  loadVersion()
})

onUnmounted(() => {
  window.removeEventListener('resize', checkScreenSize)
})
</script>

<template>
  <div class="app-layout">
    <div v-if="isDesktop" class="layout-container">
      <aside class="sidebar">
        <button class="brand" type="button" title="查看版本信息" @click="openUpdateDialog">
          <span class="brand__mark" aria-hidden="true">XP</span>
          <span class="brand__copy">
            <strong>XianYuPlus</strong>
            <small>智能经营助手 <i v-if="hasNewVersion" class="brand__update-dot"></i></small>
          </span>
        </button>
        <NavMenu />
        <button class="sidebar__collapse" type="button" title="当前版本" @click="openUpdateDialog">
          <span>‹</span>
          <small>v{{ currentVersion }}</small>
        </button>
      </aside>

      <section class="workspace">
        <header class="workspace-header">
          <div class="workspace-header__spacer"></div>
          <div class="workspace-header__actions">
            <span class="today-status"><span aria-hidden="true">☼</span> 今天，生意顺利</span>
            <button class="user-menu" type="button" title="进入系统设置" @click="openSettings">
              <span class="user-menu__avatar">{{ userInitial }}</span>
              <span class="user-menu__name">{{ currentUserName }}</span>
              <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg>
            </button>
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
      </header>
      <main class="workspace-main workspace-main--compact">
        <RouterView />
      </main>
    </template>

    <transition name="drawer">
      <div v-if="(isMobile || isTablet) && drawerVisible" class="drawer-overlay" @click="closeDrawer">
        <aside class="drawer-menu" @click.stop>
          <div class="drawer-header">
            <button class="brand brand--drawer" type="button" @click="openUpdateDialog">
              <span class="brand__mark" aria-hidden="true">XP</span>
              <span class="brand__copy"><strong>XianYuPlus</strong><small>智能经营助手</small></span>
            </button>
            <button class="drawer-close-btn" type="button" aria-label="关闭导航菜单" @click="closeDrawer">×</button>
          </div>
          <div class="drawer-content"><NavMenu @select="closeDrawer" /></div>
        </aside>
      </div>
    </transition>

    <UpdateDialog ref="updateDialog" />
  </div>
</template>

<style scoped>
.app-layout { height: 100vh; overflow: hidden; background: var(--xy-page); color: var(--xy-ink); }
.layout-container, .workspace { display: flex; min-width: 0; height: 100%; }
.layout-container { width: 100%; }
.workspace { flex: 1; flex-direction: column; overflow: hidden; }

.sidebar { width: 236px; flex: 0 0 236px; display: flex; flex-direction: column; overflow: hidden; background: var(--xy-surface); border-right: 1px solid var(--xy-border); }
.brand { width: 100%; display: flex; align-items: center; gap: 11px; padding: 24px 22px 18px; border: 0; border-bottom: 1px solid var(--xy-border-soft); background: transparent; color: var(--xy-ink); text-align: left; cursor: pointer; }
.brand__mark { width: 38px; height: 38px; display: grid; place-items: center; flex: 0 0 auto; border-radius: 10px; background: var(--xy-amber); color: #172d4f; font-size: 16px; font-weight: 800; letter-spacing: -1.5px; }
.brand__copy { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.brand__copy strong { color: #182d4f; font-size: 18px; letter-spacing: -0.5px; line-height: 22px; }
.brand__copy small { color: var(--xy-muted); font-size: 11px; line-height: 16px; }
.brand__update-dot { width: 6px; height: 6px; display: inline-block; margin-left: 4px; border-radius: 50%; background: var(--xy-danger); vertical-align: middle; }
.sidebar__collapse { display: flex; align-items: center; gap: 8px; margin: auto 16px 16px; padding: 9px 10px; border: 0; border-radius: 8px; background: transparent; color: var(--xy-muted); cursor: pointer; text-align: left; }
.sidebar__collapse:hover { background: var(--xy-amber-soft); color: var(--xy-ink); }
.sidebar__collapse span { font-size: 25px; line-height: 14px; }
.sidebar__collapse small { font-size: 11px; }

.workspace-header { height: 70px; display: flex; flex: 0 0 70px; align-items: center; justify-content: space-between; padding: 0 32px; border-bottom: 1px solid var(--xy-border-soft); background: rgba(255, 255, 255, .88); }
.workspace-header__actions { display: flex; align-items: center; gap: 14px; }
.today-status { display: inline-flex; align-items: center; gap: 7px; padding: 7px 12px; border: 1px solid var(--xy-border); border-radius: 999px; color: #4c5d78; font-size: 13px; white-space: nowrap; }
.today-status span { color: var(--xy-amber-deep); font-size: 18px; line-height: 14px; }
.user-menu { display: inline-flex; align-items: center; gap: 8px; padding: 4px 0 4px 4px; border: 0; background: transparent; color: var(--xy-ink); cursor: pointer; }
.user-menu:hover .user-menu__name { color: var(--xy-amber-deep); }
.user-menu__avatar { width: 34px; height: 34px; display: grid; place-items: center; border: 1px solid #e9c76d; border-radius: 50%; background: #fff3ce; color: #755100; font-size: 13px; font-weight: 700; }
.user-menu__name { max-width: 120px; overflow: hidden; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; transition: color .15s ease; }
.user-menu svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; }
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

@media (max-width: 1023px) { .workspace-main { padding: 24px; } }
@media (max-width: 767px) { .workspace-main, .workspace-main--compact { padding: 16px; } .compact-header { height: 56px; padding: 0 14px; } .header-content-slot { max-width: 52%; overflow: hidden; } }
</style>

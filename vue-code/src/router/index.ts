import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn } from '@/utils/request'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/index.vue'),
      meta: { title: '登录', public: true }
    },
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('@/views/dashboard/index.vue'),
      meta: { title: '面板', icon: '📊' }
    },
    {
      path: '/accounts',
      name: 'accounts',
      component: () => import('@/views/accounts/index.vue'),
      meta: { title: '闲鱼账号', icon: '👤' }
    },
    {
      path: '/connection',
      redirect: { path: '/accounts', query: { connection: '1' } }
    },
    {
      path: '/connection/:id',
      redirect: to => ({
        path: '/accounts',
        query: { ...to.query, connection: '1', accountId: String(to.params.id) }
      })
    },
    {
      path: '/goods',
      name: 'goods',
      component: () => import('@/views/goods/index.vue'),
      meta: { title: '商品列表', icon: '📦' }
    },
    {
      path: '/product-publish',
      name: 'product-publish',
      component: () => import('@/views/product-publish/index.vue'),
      meta: { title: '发布商品', icon: '➕' }
    },
    {
      path: '/product-materials',
      name: 'product-materials',
      component: () => import('@/views/product-materials/index.vue'),
      meta: { title: '商品素材库', icon: '🗂️' }
    },
    {
      path: '/orders',
      name: 'orders',
      component: () => import('@/views/orders/index.vue'),
      meta: { title: '订单管理', icon: '📋' }
    },
    {
      path: '/messages',
      name: 'messages',
      component: () => import('@/views/messages/index.vue'),
      meta: { title: '在线客服', icon: '💬' }
    },
    {
      path: '/blacklist',
      name: 'blacklist',
      component: () => import('@/views/blacklist/index.vue'),
      meta: { title: '黑名单', icon: '⛔' }
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/notifications/index.vue'),
      meta: { title: '通知渠道', icon: '🔔' }
    },
    {
      path: '/auto-delivery',
      redirect: '/orders'
    },
    {
      path: '/item-polish',
      name: 'item-polish',
      component: () => import('@/views/item-polish/index.vue'),
      meta: { title: '一键擦亮', icon: '✨' }
    },
    {
      path: '/order-automation',
      name: 'order-automation',
      component: () => import('@/views/order-automation/index.vue'),
      meta: { title: '自动化执行中心', icon: '✨' }
    },
    {
      path: '/automation-schedule',
      name: 'automation-schedule',
      component: () => import('@/views/automation-schedule/index.vue'),
      meta: { title: '定时任务', icon: '⏱️' }
    },
    {
      path: '/exception-center',
      redirect: '/order-automation'
    },
    {
      path: '/kami-config',
      name: 'kami-config',
      component: () => import('@/views/kami-config/index.vue'),
      meta: { title: '卡券管理', icon: '🔑' }
    },
    {
      path: '/auto-reply',
      name: 'auto-reply',
      component: () => import('@/views/auto-reply/index.vue'),
      meta: { title: '关键词回复', icon: '💭' }
    },
    {
      path: '/operation-log',
      name: 'operation-log',
      component: () => import('@/views/operation-log/index.vue'),
      meta: { title: '操作记录', icon: '📜' }
    },
    {
      path: '/runtime-log',
      name: 'runtime-log',
      component: () => import('@/views/runtime-log/index.vue'),
      meta: { title: '实时日志', icon: '📟' }
    },
    {
      path: '/system-check',
      name: 'system-check',
      component: () => import('@/views/system-check/index.vue'),
      meta: { title: '系统自检', icon: '🩺' }
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@/views/settings/index.vue'),
      meta: { title: '系统设置', icon: '⚙️' }
    },
    {
      path: '/qrlogin',
      name: 'qrlogin',
      component: () => import('@/views/qrlogin/index.vue'),
      meta: { title: '扫码登录', icon: '📱' }
    }
  ]
})

// 路由守卫：未登录跳转登录页
router.beforeEach((to, _from, next) => {
  if (to.meta.public || isLoggedIn()) {
    next()
  } else {
    next('/login')
  }
})

export default router

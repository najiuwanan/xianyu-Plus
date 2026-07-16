<script setup lang="ts">
/**
 * 导航菜单组件 - 电脑端侧边栏和手机端抽屉共用
 */

const emit = defineEmits<{
  select: [index: string]
}>()

const onSelect = (index: string) => {
  emit('select', index)
}
</script>

<template>
  <nav class="nav-menu">
    <router-link to="/dashboard" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/dashboard')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
      <span>仪表盘</span>
    </router-link>
    <router-link to="/accounts" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/accounts')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
      <span>账号管理</span>
    </router-link>
    <router-link to="/connection" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/connection')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
      <span>连接管理</span>
    </router-link>
    <router-link to="/goods" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/goods')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>
      <span>商品管理</span>
    </router-link>
    <router-link to="/orders" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/orders')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
      <span>订单管理</span>
    </router-link>
    <router-link to="/messages" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/messages')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
      <span>消息管理</span>
    </router-link>

    <router-link to="/notifications" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/notifications')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg>
      <span>通知渠道</span>
    </router-link>

    <div class="nav-menu-divider"><span class="nav-menu-divider-text">自动化</span></div>

    <router-link to="/kami-config" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/kami-config')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>
      <span>卡券管理</span>
    </router-link>
    <router-link to="/auto-delivery" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/auto-delivery')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 3h15v13H1z"/><path d="M16 8h4l3 3v5h-7V8z"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>
      <span>自动发货</span>
    </router-link>
    <router-link to="/item-polish" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/item-polish')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3-1.8 5.2L5 10l5.2 1.8L12 17l1.8-5.2L19 10l-5.2-1.8L12 3Z"/><path d="m19 16-.8 2.2L16 19l2.2.8L19 22l.8-2.2L22 19l-2.2-.8L19 16Z"/></svg>
      <span>自动擦亮</span>
    </router-link>
    <router-link to="/order-automation" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/order-automation')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v4"/><path d="m16.24 7.76 2.83-2.83"/><path d="M18 12h4"/><path d="m16.24 16.24 2.83 2.83"/><path d="M12 18v4"/><path d="m7.76 16.24-2.83 2.83"/><path d="M6 12H2"/><path d="m7.76 7.76-2.83-2.83"/><circle cx="12" cy="12" r="4"/></svg>
      <span>自动化执行中心</span>
    </router-link>
    <router-link to="/auto-reply" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/auto-reply')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 2L11 13"/><path d="M22 2L15 22l-4-9-9-4L22 2z"/></svg>
      <span>自动回复</span>
    </router-link>

    <div class="nav-menu-divider"><span class="nav-menu-divider-text">系统</span></div>

    <router-link to="/operation-log" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/operation-log')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      <span>操作日志</span>
    </router-link>
    <router-link to="/runtime-log" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/runtime-log')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v4"/><path d="m16.24 7.76 2.83-2.83"/><path d="M18 12h4"/><path d="m16.24 16.24 2.83 2.83"/><path d="M12 18v4"/><path d="m7.76 16.24-2.83 2.83"/><path d="M6 12H2"/><path d="m7.76 7.76-2.83-2.83"/><circle cx="12" cy="12" r="4"/></svg>
      <span>实时日志</span>
    </router-link>
    <router-link to="/settings" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/settings')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.32l.06.07a2 2 0 0 1-2.83 2.83l-.07-.06a1.65 1.65 0 0 0-1.32-.33 1.65 1.65 0 0 0-1 1.14V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.32.33l-.07.06a2 2 0 0 1-2.83-2.83l.06-.07A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.14-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.32l-.06-.07a2 2 0 0 1 2.83-2.83l.07.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.14V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.14 1.65 1.65 0 0 0 1.32-.33l.07-.06a2 2 0 0 1 2.83 2.83l-.06.07A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.14 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.14 1z"/></svg>
      <span>系统设置</span>
    </router-link>
  </nav>
</template>

<style scoped>
.nav-menu {
  border-right: none;
  background: transparent;
  display: flex;
  flex-direction: column;
}

.nav-menu-item {
  display: flex;
  align-items: center;
  margin: 1px 16px;
  border-radius: 6px;
  color: #667085;
  transition: all 0.2s;
  text-decoration: none;
  height: 40px;
  line-height: 40px;
  padding: 0 12px;
  font-size: 14px;
}

.nav-menu-item:hover {
  background: #f2f4f7;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  color: #344054;
}

.nav-menu-item--active {
  background: #FFC107 !important;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  color: #1F2329;
  box-shadow: 0 4px 12px rgba(255, 193, 7, 0.3);
  font-weight: 600;
}

.nav-menu-divider {
  display: flex;
  align-items: center;
  margin: 8px 16px 4px;
}

.nav-menu-divider::before,
.nav-menu-divider::after {
  content: '';
  flex: 1;
  height: 0.5px;
  background: #e4e7ec;
}

.nav-menu-divider-text {
  padding: 0 8px;
  font-size: 12px;
  color: #98a2b3;
  white-space: nowrap;
}
</style>

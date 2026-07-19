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
    <div class="nav-menu-section-title">运营中心</div>
    <router-link to="/dashboard" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/dashboard')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg>
      <span>仪表盘</span>
    </router-link>
    <router-link to="/accounts" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/accounts')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
      <span>账号管理</span>
    </router-link>
    <router-link to="/goods" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/goods')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>
      <span>商品配置中心</span>
    </router-link>
    <router-link to="/product-publish" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/product-publish')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/><rect x="3" y="3" width="18" height="18" rx="3"/></svg>
      <span>发布商品</span>
    </router-link>
    <router-link to="/orders" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/orders')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
      <span>订单管理</span>
    </router-link>
    <router-link to="/messages" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/messages')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
      <span>在线客服</span>
    </router-link>
    <router-link to="/blacklist" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/blacklist')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="m8 8 8 8"/></svg>
      <span>买家黑名单</span>
    </router-link>

    <router-link to="/notifications" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/notifications')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg>
      <span>通知渠道</span>
    </router-link>

    <div class="nav-menu-divider"><span class="nav-menu-divider-text">自动化中心</span></div>

    <router-link to="/kami-config" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/kami-config')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>
      <span>卡券管理</span>
    </router-link>
    <router-link to="/item-polish" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/item-polish')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m12 3-1.8 5.2L5 10l5.2 1.8L12 17l1.8-5.2L19 10l-5.2-1.8L12 3Z"/><path d="m19 16-.8 2.2L16 19l2.2.8L19 22l.8-2.2L22 19l-2.2-.8L19 16Z"/></svg>
      <span>一键擦亮</span>
    </router-link>
    <router-link to="/order-automation" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/order-automation')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v4"/><path d="m16.24 7.76 2.83-2.83"/><path d="M18 12h4"/><path d="m16.24 16.24 2.83 2.83"/><path d="M12 18v4"/><path d="m7.76 16.24-2.83 2.83"/><path d="M6 12H2"/><path d="m7.76 7.76-2.83-2.83"/><circle cx="12" cy="12" r="4"/></svg>
      <span>自动化执行中心</span>
    </router-link>
    <router-link to="/automation-schedule" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/automation-schedule')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>
      <span>定时任务</span>
    </router-link>
    <router-link to="/auto-reply" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/auto-reply')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 2L11 13"/><path d="M22 2L15 22l-4-9-9-4L22 2z"/></svg>
      <span>关键词回复</span>
    </router-link>

    <div class="nav-menu-divider"><span class="nav-menu-divider-text">系统管理</span></div>

    <router-link to="/operation-log" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/operation-log')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
      <span>操作日志</span>
    </router-link>
    <router-link to="/runtime-log" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/runtime-log')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v4"/><path d="m16.24 7.76 2.83-2.83"/><path d="M18 12h4"/><path d="m16.24 16.24 2.83 2.83"/><path d="M12 18v4"/><path d="m7.76 16.24-2.83 2.83"/><path d="M6 12H2"/><path d="m7.76 7.76-2.83-2.83"/><circle cx="12" cy="12" r="4"/></svg>
      <span>实时日志</span>
    </router-link>
    <router-link to="/system-check" class="nav-menu-item" active-class="nav-menu-item--active" @click="onSelect('/system-check')">
      <svg style="width:18px;height:18px;margin-right:8px;flex-shrink:0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2v20"/><path d="M2 12h20"/><circle cx="12" cy="12" r="4"/><path d="m18 18 3 3"/></svg>
      <span>系统自检</span>
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
  padding: 12px 0 4px;
  overflow-y: auto;
}

.nav-menu-section-title,
.nav-menu-divider-text {
  color: #53627b;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .3px;
}

.nav-menu-section-title { margin: 0 22px 7px; }

.nav-menu-item {
  display: flex;
  align-items: center;
  margin: 1px 12px;
  border-radius: 8px;
  color: #4f5f78;
  transition: background .18s ease, color .18s ease;
  text-decoration: none;
  height: 38px;
  line-height: 38px;
  padding: 0 11px;
  font-size: 14px;
}

.nav-menu-item :deep(svg) { width: 17px !important; height: 17px !important; margin-right: 10px !important; stroke-width: 1.8; }

.nav-menu-item:hover {
  background: #f7f3e7;
  color: #1f3556;
}

.nav-menu-item--active {
  background: #fff0c4 !important;
  color: #1f3556;
  font-weight: 700;
}

.nav-menu-divider {
  display: flex;
  align-items: center;
  margin: 18px 22px 7px;
}

.nav-menu-divider-text {
  padding: 0;
}
</style>

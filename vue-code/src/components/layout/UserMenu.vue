<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { logout } from '@/api/auth'
import { changePassword, getCurrentUser } from '@/api/system'
import { clearAuthToken, getAuthUsername } from '@/utils/request'
import { showConfirm } from '@/utils/confirm'
import { toast } from '@/utils/toast'

const router = useRouter()
const menuRoot = ref<HTMLElement | null>(null)
const menuOpen = ref(false)
const profileVisible = ref(false)
const profileLoading = ref(false)
const passwordVisible = ref(false)
const changingPassword = ref(false)
const loggingOut = ref(false)
const profile = ref({ username: getAuthUsername() || '管理员', lastLoginTime: '' })
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

const userInitial = computed(() => profile.value.username.slice(0, 1).toUpperCase() || 'X')

const closeMenuOnOutsideClick = (event: MouseEvent) => {
  if (menuRoot.value && !menuRoot.value.contains(event.target as Node)) menuOpen.value = false
}

const openProfile = async () => {
  menuOpen.value = false
  profileVisible.value = true
  profileLoading.value = true
  try {
    const response = await getCurrentUser()
    if (response.code === 0 || response.code === 200) {
      profile.value = {
        username: response.data?.username || profile.value.username,
        lastLoginTime: response.data?.lastLoginTime || ''
      }
    }
  } finally {
    profileLoading.value = false
  }
}

const openPassword = () => {
  menuOpen.value = false
  passwordVisible.value = true
}

const resetPasswordModal = () => {
  passwordVisible.value = false
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  showOldPassword.value = false
  showNewPassword.value = false
  showConfirmPassword.value = false
}

const closePassword = () => {
  if (changingPassword.value) return
  resetPasswordModal()
}

const handleChangePassword = async () => {
  if (!oldPassword.value) {
    toast.warning('请输入原密码')
    return
  }
  if (newPassword.value.length < 8 || newPassword.value.length > 72) {
    toast.warning('新密码长度需在 8-72 位之间')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    toast.warning('两次输入的新密码不一致')
    return
  }

  changingPassword.value = true
  try {
    const response = await changePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value
    })
    if (response.code === 0 || response.code === 200) {
      toast.success('密码修改成功')
      resetPasswordModal()
    }
  } finally {
    changingPassword.value = false
  }
}

const handleLogout = async () => {
  menuOpen.value = false
  try {
    await showConfirm('确定要退出当前系统账号吗？', '退出登录')
  } catch {
    return
  }

  loggingOut.value = true
  try {
    await logout()
  } catch {
    // 服务端退出失败时仍清理本地令牌，避免停留在失效会话中。
  } finally {
    clearAuthToken()
    loggingOut.value = false
    toast.success('已退出登录')
    router.replace('/login')
  }
}

const openPasswordFromProfile = () => {
  profileVisible.value = false
  openPassword()
}

onMounted(() => window.addEventListener('click', closeMenuOnOutsideClick))
onBeforeUnmount(() => window.removeEventListener('click', closeMenuOnOutsideClick))
</script>

<template>
  <div ref="menuRoot" class="user-menu-wrap">
    <button class="user-menu" type="button" aria-haspopup="menu" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
      <span class="user-menu__avatar">{{ userInitial }}</span>
      <span class="user-menu__name">{{ profile.username }}</span>
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg>
    </button>

    <Transition name="menu-popover">
      <div v-if="menuOpen" class="user-popover" role="menu">
        <div class="user-popover__identity"><span>{{ userInitial }}</span><div><strong>{{ profile.username }}</strong><small>系统管理员</small></div></div>
        <button type="button" role="menuitem" @click="openProfile"><span>个人资料</span><i>›</i></button>
        <button type="button" role="menuitem" @click="openPassword"><span>修改密码</span><i>›</i></button>
        <div class="user-popover__divider"></div>
        <button type="button" role="menuitem" class="user-popover__logout" :disabled="loggingOut" @click="handleLogout">{{ loggingOut ? '退出中…' : '退出登录' }}</button>
      </div>
    </Transition>
  </div>

  <Teleport to="body">
    <Transition name="account-modal">
      <div v-if="profileVisible" class="account-modal-overlay" @click.self="profileVisible = false">
        <section class="account-modal" role="dialog" aria-modal="true" aria-label="个人资料">
          <header><div><span class="account-modal__badge">{{ userInitial }}</span><div><h2>个人资料</h2><p>当前系统账号信息</p></div></div><button type="button" @click="profileVisible = false">×</button></header>
          <div v-if="profileLoading" class="account-modal__loading">正在读取账号信息…</div>
          <dl v-else class="profile-list"><div><dt>账号名称</dt><dd>{{ profile.username }}</dd></div><div><dt>最后登录</dt><dd>{{ profile.lastLoginTime || '暂无记录' }}</dd></div></dl>
          <footer><button type="button" class="account-modal__secondary" @click="profileVisible = false">关闭</button><button type="button" class="account-modal__primary" @click="openPasswordFromProfile">修改密码</button></footer>
        </section>
      </div>
    </Transition>

    <Transition name="account-modal">
      <div v-if="passwordVisible" class="account-modal-overlay" @click.self="closePassword">
        <section class="account-modal account-modal--password" role="dialog" aria-modal="true" aria-label="修改密码">
          <header><div><span class="account-modal__badge account-modal__badge--key">⌁</span><div><h2>修改密码</h2><p>新密码长度需为 8-72 位</p></div></div><button type="button" :disabled="changingPassword" @click="closePassword">×</button></header>
          <div class="password-form">
            <label>原密码<div><input v-model="oldPassword" :type="showOldPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入原密码" :disabled="changingPassword"><button type="button" @click="showOldPassword = !showOldPassword">{{ showOldPassword ? '隐藏' : '显示' }}</button></div></label>
            <label>新密码<div><input v-model="newPassword" :type="showNewPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="请输入新密码" :disabled="changingPassword"><button type="button" @click="showNewPassword = !showNewPassword">{{ showNewPassword ? '隐藏' : '显示' }}</button></div></label>
            <label>确认新密码<div><input v-model="confirmPassword" :type="showConfirmPassword ? 'text' : 'password'" autocomplete="new-password" placeholder="请再次输入新密码" :disabled="changingPassword" @keydown.enter="handleChangePassword"><button type="button" @click="showConfirmPassword = !showConfirmPassword">{{ showConfirmPassword ? '隐藏' : '显示' }}</button></div></label>
          </div>
          <footer><button type="button" class="account-modal__secondary" :disabled="changingPassword" @click="closePassword">取消</button><button type="button" class="account-modal__primary" :disabled="changingPassword" @click="handleChangePassword">{{ changingPassword ? '保存中…' : '确认修改' }}</button></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.user-menu-wrap { position: relative; }.user-menu { display: inline-flex; align-items: center; gap: 8px; padding: 4px 0 4px 4px; border: 0; background: transparent; color: var(--xy-ink); cursor: pointer; }.user-menu:hover .user-menu__name { color: var(--xy-amber-deep); }.user-menu__avatar, .user-popover__identity > span, .account-modal__badge { width: 34px; height: 34px; display: grid; place-items: center; flex: 0 0 auto; border: 1px solid #e9c76d; border-radius: 50%; background: #fff3ce; color: #755100; font-size: 13px; font-weight: 700; }.user-menu__name { max-width: 120px; overflow: hidden; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; transition: color .15s ease; }.user-menu svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; }
.user-popover { position: absolute; top: calc(100% + 11px); right: 0; z-index: 30; width: 210px; padding: 8px; border: 1px solid var(--xy-border); border-radius: 12px; background: var(--xy-surface); box-shadow: 0 16px 36px rgba(25, 47, 80, .16); }.user-popover__identity { display: flex; align-items: center; gap: 9px; padding: 8px 9px 12px; }.user-popover__identity > span { width: 32px; height: 32px; font-size: 12px; }.user-popover__identity div { min-width: 0; display: flex; flex-direction: column; }.user-popover__identity strong { overflow: hidden; color: var(--xy-ink); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.user-popover__identity small { color: var(--xy-muted); font-size: 11px; }.user-popover button { width: 100%; display: flex; align-items: center; justify-content: space-between; padding: 9px; border: 0; border-radius: 7px; background: transparent; color: #42516a; font-size: 13px; text-align: left; cursor: pointer; }.user-popover button:hover { background: var(--xy-surface-muted); color: var(--xy-ink); }.user-popover button i { color: #9ba4b1; font-size: 18px; font-style: normal; line-height: 12px; }.user-popover__divider { height: 1px; margin: 7px 3px; background: var(--xy-border-soft); }.user-popover .user-popover__logout { color: var(--xy-danger); }.user-popover .user-popover__logout:hover { background: var(--xy-danger-soft); color: #b73e37; }.menu-popover-enter-active, .menu-popover-leave-active { transition: opacity .14s ease, transform .14s ease; }.menu-popover-enter-from, .menu-popover-leave-to { opacity: 0; transform: translateY(-4px); }
</style>

<style>
.account-modal-overlay { position: fixed; inset: 0; z-index: 2200; display: flex; align-items: center; justify-content: center; padding: 20px; background: rgba(23, 40, 66, .36); }.account-modal { width: min(460px, 100%); overflow: hidden; border: 1px solid var(--xy-border); border-radius: 14px; background: var(--xy-surface); box-shadow: 0 24px 70px rgba(18, 37, 66, .2); }.account-modal header { display: flex; align-items: center; justify-content: space-between; padding: 20px 22px 16px; border-bottom: 1px solid var(--xy-border-soft); }.account-modal header > div { display: flex; align-items: center; gap: 10px; }.account-modal h2, .account-modal p { margin: 0; }.account-modal h2 { color: var(--xy-ink); font-size: 17px; font-weight: 720; }.account-modal p { margin-top: 3px; color: var(--xy-muted); font-size: 12px; }.account-modal header > button { width: 30px; height: 30px; border: 0; border-radius: 7px; background: transparent; color: var(--xy-muted); font-size: 22px; line-height: 1; cursor: pointer; }.account-modal header > button:hover { background: var(--xy-surface-muted); color: var(--xy-ink); }.account-modal__badge { width: 36px; height: 36px; }.account-modal__badge--key { border-color: #cbd9ef; background: var(--xy-info-soft); color: var(--xy-info); font-size: 21px; }.account-modal__loading { padding: 46px 22px; color: var(--xy-muted); font-size: 13px; text-align: center; }.profile-list { margin: 0; padding: 10px 22px; }.profile-list > div { display: flex; align-items: center; justify-content: space-between; gap: 16px; min-height: 50px; border-bottom: 1px solid var(--xy-border-soft); }.profile-list > div:last-child { border-bottom: 0; }.profile-list dt { color: var(--xy-muted); font-size: 13px; }.profile-list dd { margin: 0; color: var(--xy-ink); font-size: 13px; font-weight: 650; text-align: right; }.account-modal footer { display: flex; justify-content: flex-end; gap: 9px; padding: 15px 22px; border-top: 1px solid var(--xy-border-soft); }.account-modal footer button { min-height: 34px; padding: 0 13px; border-radius: 7px; font-size: 13px; font-weight: 700; cursor: pointer; }.account-modal__secondary { border: 1px solid #cfd4dc; background: var(--xy-surface); color: #42516a; }.account-modal__primary { border: 1px solid var(--xy-amber); background: var(--xy-amber); color: #1f3556; }.password-form { display: grid; gap: 15px; padding: 20px 22px; }.password-form label { display: grid; gap: 7px; color: #42516a; font-size: 13px; font-weight: 650; }.password-form label > div { position: relative; }.password-form input { width: 100%; height: 38px; padding: 0 52px 0 11px; border: 1px solid #d4d9e0; border-radius: 7px; background: #fff; color: var(--xy-ink); font-size: 13px; outline: none; }.password-form input:focus { border-color: #d39b00; box-shadow: 0 0 0 3px rgba(244, 180, 0, .16); }.password-form label button { position: absolute; top: 50%; right: 5px; padding: 4px 6px; border: 0; background: transparent; color: #64738a; font-size: 11px; transform: translateY(-50%); cursor: pointer; }.account-modal button:disabled { cursor: not-allowed; opacity: .55; }.account-modal-enter-active, .account-modal-leave-active { transition: opacity .16s ease; }.account-modal-enter-active .account-modal, .account-modal-leave-active .account-modal { transition: transform .18s ease, opacity .18s ease; }.account-modal-enter-from, .account-modal-leave-to { opacity: 0; }.account-modal-enter-from .account-modal, .account-modal-leave-to .account-modal { opacity: 0; transform: translateY(8px) scale(.98); }
@media (max-width: 767px) { .user-menu__name { display: none; }.user-menu { gap: 4px; } }
@media (max-width: 520px) { .account-modal-overlay { align-items: flex-end; padding: 0; }.account-modal { width: 100%; border-radius: 14px 14px 0 0; }.account-modal header, .password-form, .profile-list, .account-modal footer { padding-left: 18px; padding-right: 18px; } }
</style>

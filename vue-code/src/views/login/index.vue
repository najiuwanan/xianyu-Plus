<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { checkUserExists, login, register } from '@/api/auth'
import { setAuthToken, isLoggedIn } from '@/utils/request'

// 'checking' -> 'login' -> 'register'
const mode = ref<'checking' | 'login' | 'register'>('checking')
const loading = ref(false)

const username = ref('')
const password = ref('')
const confirmPassword = ref('')

const showPassword = ref(false)
const showConfirmPassword = ref(false)

onMounted(async () => {
  // 已登录则跳转首页
  if (isLoggedIn()) {
    window.location.href = '/dashboard'
    return
  }
  // 检查是否有用户，决定显示登录还是注册
  try {
    const res = await checkUserExists()
    if (res.code === 200 && res.data) {
      // exists=true -> 有用户 -> 登录; exists=false -> 无用户 -> 注册
      mode.value = res.data.exists ? 'login' : 'register'
    } else {
      mode.value = 'login'
    }
  } catch {
    mode.value = 'login'
  }
})

async function handleLogin() {
  if (!username.value.trim()) return
  if (!password.value) return
  loading.value = true
  try {
    const res = await login({ username: username.value.trim(), password: password.value })
    if (res.code === 200 && res.data && res.data.token) {
      setAuthToken(res.data.token, res.data.username)
      window.location.href = '/dashboard'
    } else {
      console.error('[Login] login response invalid:', res)
    }
  } catch (e) {
    console.error('[Login] login failed:', e)
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!username.value.trim()) return
  if (!password.value) return
  if (password.value !== confirmPassword.value) return
  if (username.value.trim().length < 3) return
  if (password.value.length < 8 || password.value.length > 72) return
  loading.value = true
  try {
    const res = await register({
      username: username.value.trim(),
      password: password.value,
      confirmPassword: confirmPassword.value
    })
    if (res.code === 200 && res.data && res.data.token) {
      setAuthToken(res.data.token, res.data.username)
      window.location.href = '/dashboard'
    } else {
      console.error('[Login] register response invalid:', res)
    }
  } catch (e) {
    console.error('[Login] register failed:', e)
  } finally {
    loading.value = false
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !loading.value) {
    if (mode.value === 'login') handleLogin()
    else if (mode.value === 'register') handleRegister()
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <!-- Logo -->
      <div class="login-logo">
        <div class="login-logo-icon">闲</div>
        <div class="login-logo-text">闲鱼Plus</div>
      </div>

      <!-- Loading -->
      <div v-if="mode === 'checking'" class="login-loading">
        <div class="login-spinner"></div>
      </div>

      <!-- Login Form -->
      <div v-else-if="mode === 'login'" class="login-form">
        <h2 class="login-title">登录</h2>
        <p class="login-subtitle">请输入账号密码登录</p>

        <div class="login-field">
          <label class="login-label">账号</label>
          <div class="login-input-wrap">
            <input
              v-model="username"
              type="text"
              class="login-input"
              placeholder="请输入账号"
              autocomplete="username"
              :disabled="loading"
              @keydown="handleKeydown"
            />
          </div>
        </div>

        <div class="login-field">
          <label class="login-label">密码</label>
          <div class="login-input-wrap">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              class="login-input"
              placeholder="请输入密码"
              autocomplete="current-password"
              :disabled="loading"
              @keydown="handleKeydown"
            />
            <button class="login-eye-btn" @click="showPassword = !showPassword" tabindex="-1">
              {{ showPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </div>

        <button class="login-btn" :disabled="loading" @click="handleLogin">
          <span v-if="loading" class="login-btn-spinner"></span>
          {{ loading ? '请稍候...' : '登录' }}
        </button>
      </div>

      <!-- Register Form -->
      <div v-else-if="mode === 'register'" class="login-form">
        <h2 class="login-title">创建账号</h2>
        <p class="login-subtitle">首次使用，请创建管理员账号</p>

        <div class="login-field">
          <label class="login-label">账号</label>
          <div class="login-input-wrap">
            <input
              v-model="username"
              type="text"
              class="login-input"
              placeholder="请输入账号"
              autocomplete="username"
              :disabled="loading"
              @keydown="handleKeydown"
            />
          </div>
        </div>

        <div class="login-field">
          <label class="login-label">密码</label>
          <div class="login-input-wrap">
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              class="login-input"
              placeholder="请输入密码"
              autocomplete="new-password"
              maxlength="72"
              :disabled="loading"
              @keydown="handleKeydown"
            />
            <button class="login-eye-btn" @click="showPassword = !showPassword" tabindex="-1">
              {{ showPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </div>

        <div class="login-field">
          <label class="login-label">确认密码</label>
          <div class="login-input-wrap">
            <input
              v-model="confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              class="login-input"
              placeholder="请再次输入密码"
              autocomplete="new-password"
              maxlength="72"
              :disabled="loading"
              @keydown="handleKeydown"
            />
            <button class="login-eye-btn" @click="showConfirmPassword = !showConfirmPassword" tabindex="-1">
              {{ showConfirmPassword ? '隐藏' : '显示' }}
            </button>
          </div>
        </div>

        <button class="login-btn" :disabled="loading" @click="handleRegister">
          <span v-if="loading" class="login-btn-spinner"></span>
          {{ loading ? '请稍候...' : '创建账号' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f6f8;
  padding: 16px;
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: #ffffff;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.05);
  padding: 48px 36px;
  position: relative;
  overflow: hidden;
}

.login-card::before {
  content: none;
}

/* Logo */
.login-logo {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 32px;
}

.login-logo-icon {
  width: 48px;
  height: 48px;
  background: var(--ab, #FFC107);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1F2329;
  font-size: 26px;
  font-weight: 800;
  box-shadow: 0 4px 12px rgba(255, 193, 7, 0.3);
}

.login-logo-text {
  font-size: 24px;
  font-weight: 700;
  color: #1c1c1e;
  letter-spacing: -0.5px;
}

/* Loading */
.login-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 0;
}

.login-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #d4d4d4;
  border-top-color: #1c1c1e;
  border-radius: 50%;
  animation: login-spin 0.6s linear infinite;
}

@keyframes login-spin {
  to { transform: rotate(360deg); }
}

/* Form */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.login-title {
  font-size: 22px;
  font-weight: 600;
  color: #1c1c1e;
  margin: 0;
  text-align: center;
}

.login-subtitle {
  font-size: 14px;
  color: rgba(28,28,30,.55);
  margin: -12px 0 0;
  text-align: center;
}

.login-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.login-label {
  font-size: 13px;
  font-weight: 500;
  color: #1c1c1e;
}

.login-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.login-input {
  width: 100%;
  height: 44px;
  padding: 0 14px;
  font-size: 15px;
  color: #101828;
  background: #ffffff;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  outline: none;
  transition: all 0.2s;
  box-sizing: border-box;
}

.login-input:focus {
  border-color: var(--ab, #FFC107);
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(255, 193, 7, 0.15);
}

.login-input::placeholder {
  color: rgba(28,28,30,.55);
}

.login-input:disabled {
  opacity: 0.5;
}

.login-eye-btn {
  position: absolute;
  right: 10px;
  background: none;
  border: none;
  font-size: 12px;
  color: rgba(28,28,30,.55);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  transition: color 0.2s;
}

.login-eye-btn:hover {
  color: #1c1c1e;
}

/* Submit Button */
.login-btn {
  width: 100%;
  height: 48px;
  background: var(--ab, #FFC107);
  color: #1F2329;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 8px;
  box-shadow: 0 4px 12px rgba(255, 193, 7, 0.2);
}

.login-btn:hover {
  background: var(--color-primary-hover, #FFB300);
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(255, 193, 7, 0.3);
}

.login-btn:active {
  transform: none;
}

.login-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.login-btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(31, 35, 41, 0.3);
  border-top-color: #1F2329;
  border-radius: 50%;
  animation: login-spin 0.6s linear infinite;
}

/* Responsive: Tablet */
@media (max-width: 768px) {
  .login-card {
    padding: 32px 24px;
  }

  .login-title {
    font-size: 20px;
  }
}

/* Responsive: Small phone */
@media (max-width: 480px) {
  .login-card {
    padding: 24px 20px;
    border-radius: 12px;
  }

  .login-logo-icon {
    width: 36px;
    height: 36px;
    font-size: 20px;
  }

  .login-logo-text {
    font-size: 18px;
  }

  .login-title {
    font-size: 18px;
  }

  .login-input {
    height: 42px;
    font-size: 14px;
  }

  .login-btn {
    height: 44px;
    font-size: 15px;
  }
}
</style>

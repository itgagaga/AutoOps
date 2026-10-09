<!-- @author yangdb -->
<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { clearToken, getCurrentUser, getToken, setToken } from './api'
import LoginView from './components/LoginView.vue'

const DashboardView = defineAsyncComponent(() =>
  import('./components/DashboardView.vue')
)

const user = ref(null)
const initializing = ref(true)
const NAVIGATION_STATE_KEYS = [
  'super_employee_current_menu',
  'super_employee_opened_menus'
]

function resetNavigationState() {
  NAVIGATION_STATE_KEYS.forEach(key => localStorage.removeItem(key))
}

async function restoreSession() {
  if (!getToken()) { initializing.value = false; return }
  try { user.value = await getCurrentUser() } catch { clearToken() }
  finally { initializing.value = false }
}

function loggedIn(result) {
  resetNavigationState()
  setToken(result.token)
  user.value = { id: result.id, username: result.username, displayName: result.displayName, role: result.role, menus: result.menus || [] }
}

function logout() {
  clearToken()
  resetNavigationState()
  user.value = null
}
async function refreshSession() { user.value = await getCurrentUser() }
function expired() { logout() }
onMounted(() => { window.addEventListener('auth-expired', expired); restoreSession() })
onBeforeUnmount(() => window.removeEventListener('auth-expired', expired))
</script>

<template>
  <div v-if="initializing" class="app-loading"><el-icon class="is-loading" :size="30"><Loading /></el-icon></div>
  <DashboardView v-else-if="user" :user="user" @logout="logout" @session-refresh="refreshSession" />
  <LoginView v-else @logged-in="loggedIn" />
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useNotification } from '../useNotification'

const emit = defineEmits(['open-task'])

const {
  notifications,
  unreadCount,
  markAsRead,
  markAllAsRead,
  clearAll
} = useNotification()

const TABS = [
  { key: 'all', label: '全部' },
  { key: 'success', label: '成功' },
  { key: 'error', label: '失败' },
  { key: 'unread', label: '未读' }
]

const activeTab = ref('unread')

const panelVisible = ref(false)
const bellRef = ref(null)
const panelRef = ref(null)

function togglePanel() {
  panelVisible.value = !panelVisible.value
}

function handleMarkAllRead() {
  markAllAsRead()
  closePanel()
}

function handleClearAll() {
  clearAll()
  closePanel()
}

function closePanel() {
  panelVisible.value = false
}

function handleNotificationClick(notif) {
  markAsRead(notif.id)
  if (notif.opsTaskId) {
    emit('open-task', notif.opsTaskId)
  }
  closePanel()
}

function handleClickOutside(event) {
  const target = event.target
  if (panelVisible.value
      && bellRef.value
      && !bellRef.value.contains(target)
      && panelRef.value
      && !panelRef.value.contains(target)) {
    closePanel()
  }
}

function handleKeydown(event) {
  if (event.key === 'Escape' && panelVisible.value) {
    closePanel()
  }
}

function setTab(key) {
  activeTab.value = key
}

const tabCounts = computed(() => ({
  all: notifications.value.length,
  success: notifications.value.filter(n => n.type === 'success').length,
  error: notifications.value.filter(n => n.type === 'error').length,
  unread: notifications.value.filter(n => !n.read).length
}))

const filteredList = computed(() => {
  let list = [...notifications.value]
  switch (activeTab.value) {
    case 'success': list = list.filter(n => n.type === 'success'); break
    case 'error': list = list.filter(n => n.type === 'error'); break
    case 'unread': list = list.filter(n => !n.read); break
  }
  return list.sort((a, b) => b.createdAt - a.createdAt)
})

function formatTime(date) {
  if (!date) return ''
  const d = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(d.getTime())) return ''
  const now = new Date()
  const diff = now - d
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}天前`
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleKeydown)
})

const typeIcon = {
  success: '✓',
  error: '✕',
  warning: '⚠',
  info: 'ℹ'
}

const typeLabel = {
  success: '成功',
  error: '失败',
  warning: '警告',
  info: '通知'
}
</script>

<template>
  <div class="notification-bell-wrapper" ref="bellRef">
    <button
      class="notification-bell"
      :class="{ active: unreadCount > 0 }"
      @click.stop="togglePanel"
      aria-label="通知"
    >
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="3" y="5" width="18" height="14" rx="2" ry="2"></rect>
        <polyline points="3,7 12,13 21,7"></polyline>
      </svg>
      <span v-if="unreadCount > 0" class="bell-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </button>

    <teleport to="body">
      <Transition name="panel-fade">
        <div
          v-if="panelVisible"
          class="notification-panel"
          ref="panelRef"
          @click.stop
        >
          <div class="panel-header">
            <span class="panel-title">通知中心</span>
            <div class="panel-actions">
              <button v-if="unreadCount > 0" class="panel-link-btn" @click="handleMarkAllRead">全部已读</button>
              <button v-if="notifications.length > 0" class="panel-link-btn" @click="handleClearAll">清空</button>
            </div>
          </div>

          <div class="panel-tabs">
            <button
              v-for="tab in TABS"
              :key="tab.key"
              class="panel-tab"
              :class="{ active: activeTab === tab.key }"
              @click="setTab(tab.key)"
            >
              <span class="tab-label">{{ tab.label }}</span>
              <span v-if="tabCounts[tab.key] > 0" class="tab-badge">{{ tabCounts[tab.key] > 99 ? '99+' : tabCounts[tab.key] }}</span>
            </button>
          </div>

          <div class="panel-body">
            <template v-if="filteredList.length > 0">
              <div
                v-for="notif in filteredList"
                :key="notif.id"
                class="notification-item"
                :class="[`notif-${notif.type}`, { unread: !notif.read, read: notif.read }]"
                @click="handleNotificationClick(notif)"
              >
                <div class="notif-icon">{{ typeIcon[notif.type] || 'ℹ' }}</div>
                <div class="notif-content">
                  <div class="notif-title">{{ notif.title }}</div>
                  <div v-if="notif.message" class="notif-message">{{ notif.message }}</div>
                  <div class="notif-time">
                    <span class="notif-type-tag">{{ typeLabel[notif.type] || '通知' }}</span>
                    <span class="notif-time-sep">·</span>
                    <span>{{ formatTime(notif.createdAt) }}</span>
                  </div>
                </div>
                <div v-if="!notif.read" class="notif-indicator" />
              </div>
            </template>
            <div v-else class="panel-empty">
              <svg viewBox="0 0 24 24" width="56" height="56" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" class="empty-icon">
                <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
                <polyline points="22,6 12,13 2,6"></polyline>
              </svg>
              <p v-if="activeTab === 'all'">暂无通知</p>
              <p v-else-if="activeTab === 'unread'">所有通知已读完</p>
              <p v-else-if="activeTab === 'success'">没有成功的通知</p>
              <p v-else-if="activeTab === 'error'">没有失败的通知</p>
              <span v-if="activeTab === 'all'">运维成功或失败时会在这里提醒你</span>
              <span v-else-if="activeTab === 'unread'">当前没有新的未读通知</span>
              <span v-else>切换到其他分类查看更多</span>
            </div>
          </div>
        </div>
      </Transition>
    </teleport>
  </div>
</template>

<style scoped>
.notification-bell-wrapper {
  position: relative;
}

.notification-bell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: rgba(255, 255, 255, 0.85);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.notification-bell:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.notification-bell.active {
  color: #fff;
}

.bell-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  animation: badge-pulse 0.3s ease-out;
}

@keyframes badge-pulse {
  0% { transform: scale(0.5); opacity: 0; }
  50% { transform: scale(1.1); }
  100% { transform: scale(1); opacity: 1; }
}

.notification-panel {
  position: fixed;
  top: 80px;
  right: 24px;
  z-index: 4500;
  width: 380px;
  max-width: calc(100vw - 48px);
  max-height: 520px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2), 0 6px 18px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f3f4f6;
  flex-shrink: 0;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.panel-link-btn {
  border: none;
  background: transparent;
  color: #6b7280;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: color 0.15s, background 0.15s;
}

.panel-link-btn:hover {
  color: #3b82f6;
  background: #f3f4f6;
}

.panel-tabs {
  display: flex;
  gap: 2px;
  padding: 4px 8px 0;
  border-bottom: 1px solid #f3f4f6;
  flex-shrink: 0;
}

.panel-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border: none;
  background: transparent;
  color: #6b7280;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
  border-radius: 6px 6px 0 0;
  margin-bottom: -1px;
}

.panel-tab:hover {
  color: #374151;
  background: #f9fafb;
}

.panel-tab.active {
  color: #1f2937;
  border-bottom-color: #3b82f6;
}

.tab-label {
  line-height: 1;
}

.tab-badge {
  min-width: 18px;
  height: 16px;
  padding: 0 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #e5e7eb;
  color: #6b7280;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
}

.panel-tab.active .tab-badge {
  background: #3b82f6;
  color: #fff;
}

.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
  min-height: 180px;
}

.panel-body::-webkit-scrollbar {
  width: 6px;
}

.panel-body::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background: #e5e7eb;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.15s;
  position: relative;
  border-bottom: 1px solid #f9fafb;
}

.notification-item:last-child {
  border-bottom: none;
}

.notification-item:hover {
  background: #f9fafb;
}

.notification-item.unread {
  background: #f0f9ff;
}

.notification-item.unread:hover {
  background: #e0f2fe;
}

.notification-item.read {
  opacity: 0.75;
}

.notif-icon {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  margin-top: 2px;
}

.notif-success .notif-icon { background: #22c55e; }
.notif-error .notif-icon { background: #ef4444; }
.notif-warning .notif-icon { background: #f59e0b; }
.notif-info .notif-icon { background: #3b82f6; }

.notif-content {
  flex: 1;
  min-width: 0;
}

.notif-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
}

.notif-message {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
  margin-top: 2px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.notif-time {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.notif-type-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 500;
  line-height: 1.4;
  background: #f3f4f6;
  color: #6b7280;
}

.notif-success .notif-type-tag { background: #dcfce7; color: #16a34a; }
.notif-error .notif-type-tag { background: #fee2e2; color: #dc2626; }
.notif-warning .notif-type-tag { background: #fef3c7; color: #d97706; }
.notif-info .notif-type-tag { background: #dbeafe; color: #2563eb; }

.notif-time-sep {
  color: #d1d5db;
}

.notif-indicator {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #3b82f6;
}

.notification-item.read .notif-indicator {
  display: none;
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #9ca3af;
  gap: 10px;
}

.empty-icon {
  color: #e5e7eb;
}

.panel-empty p {
  margin: 0;
  font-size: 14px;
  color: #6b7280;
  font-weight: 500;
}

.panel-empty span {
  font-size: 12px;
  color: #9ca3af;
}

.panel-fade-enter-active,
.panel-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.panel-fade-enter-from,
.panel-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}

@media (max-width: 768px) {
  .notification-bell { width: 32px; height: 32px; }
  .notification-bell svg { width: 18px; height: 18px; }
  .bell-badge { min-width: 16px; height: 16px; font-size: 10px; top: 0; right: 0; }
  .notification-panel { top: 72px; right: 12px; width: 340px; }
}

@media (max-width: 480px) {
  .notification-panel {
    top: 64px;
    right: 8px;
    left: 8px;
    width: auto;
    max-width: none;
  }
  .panel-tab { padding: 6px 8px; font-size: 12px; }
  .tab-badge { min-width: 16px; height: 14px; font-size: 9px; padding: 0 4px; }
}
</style>

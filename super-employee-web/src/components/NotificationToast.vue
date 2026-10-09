<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useNotification } from '../useNotification'

const props = defineProps({
  notification: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['close', 'click'])

const { removeToast, markAsRead } = useNotification()

const visible = ref(false)
const hovered = ref(false)
const closed = ref(false)
const progressActive = ref(false)
const progressKey = ref(0)
let hideTimer = null

onMounted(() => {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => {
      visible.value = true
      startProgress()
      scheduleAutoHide()
    })
  })
})

onBeforeUnmount(() => {
  clearTimeout(hideTimer)
})

const AUTO_HIDE_MS = 10000

function startProgress() {
  progressKey.value++
  progressActive.value = true
}

function pauseProgress() {
  progressActive.value = false
}

function resumeProgress() {
  if (!closed.value && visible.value) {
    progressActive.value = true
  }
}

function scheduleAutoHide() {
  clearTimeout(hideTimer)
  hideTimer = setTimeout(() => {
    if (!hovered.value) {
      slideOut()
    } else {
      scheduleAutoHide()
    }
  }, AUTO_HIDE_MS)
}

function slideOut() {
  if (closed.value) return
  closed.value = true
  progressActive.value = false
  visible.value = false
  setTimeout(() => {
    removeToast(props.notification.id)
    if (props.notification.opsTaskId) {
      markAsRead(props.notification.id)
    }
    emit('close', props.notification)
  }, 400)
}

function handleClick() {
  if (closed.value) return
  if (props.notification.opsTaskId) {
    markAsRead(props.notification.id)
    emit('click', props.notification)
  }
  slideOut()
}

function handleMouseEnter() {
  hovered.value = true
  clearTimeout(hideTimer)
  pauseProgress()
}

function handleMouseLeave() {
  hovered.value = false
  if (!closed.value) {
    resumeProgress()
    scheduleAutoHide()
  }
}

function formatTime(date) {
  if (!date) return ''
  const d = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(d.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const typeIcon = {
  success: '✓',
  error: '✕',
  warning: '⚠',
  info: 'ℹ'
}

const typeClass = {
  success: 'toast-success',
  error: 'toast-error',
  warning: 'toast-warning',
  info: 'toast-info'
}
</script>

<template>
  <Transition name="toast-slide">
    <div
      v-if="visible"
      class="notification-toast"
      :class="[typeClass[notification.type] || 'toast-info']"
      @click="handleClick"
      @mouseenter="handleMouseEnter"
      @mouseleave="handleMouseLeave"
    >
      <div class="toast-icon">
        {{ typeIcon[notification.type] || 'ℹ' }}
      </div>
      <div class="toast-content">
        <div class="toast-title">{{ notification.title }}</div>
        <div v-if="notification.message" class="toast-message">{{ notification.message }}</div>
        <div class="toast-time">{{ formatTime(notification.createdAt) }}</div>
      </div>
      <button class="toast-close" @click.stop="slideOut" aria-label="关闭">×</button>
      <div class="toast-progress-track">
        <div
          class="toast-progress-bar"
          :class="{ 'is-active': progressActive }"
          :key="progressKey"
        ></div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.notification-toast {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 5000;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 360px;
  max-width: calc(100vw - 48px);
  padding: 14px 16px 18px;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18), 0 4px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  overflow: hidden;
}

.notification-toast::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
}

.notification-toast.toast-success::before { background: #22c55e; }
.notification-toast.toast-error::before { background: #ef4444; }
.notification-toast.toast-warning::before { background: #f59e0b; }
.notification-toast.toast-info::before { background: #3b82f6; }

.toast-icon {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
}

.toast-success .toast-icon { background: #22c55e; }
.toast-error .toast-icon { background: #ef4444; }
.toast-warning .toast-icon { background: #f59e0b; }
.toast-info .toast-icon { background: #3b82f6; }

.toast-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.toast-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toast-message {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.toast-time {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 2px;
}

.toast-close {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: #9ca3af;
  font-size: 18px;
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s, color 0.2s;
}

.toast-close:hover {
  background: #f3f4f6;
  color: #374151;
}

.toast-progress-track {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.toast-progress-bar {
  height: 100%;
  width: 100%;
  border-radius: 0 2px 2px 0;
  transform-origin: left center;
  transform: scaleX(1);
  animation-name: toast-progress-shrink;
  animation-duration: 10s;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
  animation-play-state: paused;
}

.toast-progress-bar.is-active {
  animation-play-state: running;
}

.toast-success .toast-progress-bar { background: #22c55e; }
.toast-error .toast-progress-bar { background: #ef4444; }
.toast-warning .toast-progress-bar { background: #f59e0b; }
.toast-info .toast-progress-bar { background: #3b82f6; }

@keyframes toast-progress-shrink {
  from { transform: scaleX(1); }
  to { transform: scaleX(0); }
}

.toast-slide-enter-active,
.toast-slide-leave-active {
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1), opacity 0.4s ease;
}

.toast-slide-enter-from {
  transform: translateX(420px);
  opacity: 0;
}

.toast-slide-leave-to {
  transform: translateX(420px);
  opacity: 0;
}

.notification-toast:hover {
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.22), 0 6px 16px rgba(0, 0, 0, 0.1);
  transform: translateY(-1px);
}

@media (max-width: 768px) {
  .notification-toast {
    right: 12px;
    left: 12px;
    bottom: 12px;
    width: auto;
    max-width: none;
  }
  .toast-icon {
    width: 24px;
    height: 24px;
    font-size: 12px;
  }
  .toast-title {
    font-size: 13px;
  }
  .toast-message {
    font-size: 12px;
  }
}
@media (max-width: 480px) {
  .notification-toast {
    right: 10px;
    left: 10px;
    bottom: 10px;
    padding: 12px 14px 16px;
  }
}
</style>

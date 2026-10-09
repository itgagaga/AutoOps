import { reactive, computed } from 'vue'

const state = reactive({
  notifications: [],
  toasts: [],
  listeners: new Set(),
  lastNotifiedKeys: new Map()
})

let idCounter = 0

const NOTIFICATION_STORAGE_KEY = 'super_employee_notifications'
const LAST_NOTIFIED_TTL = 30 * 60 * 1000
const MAX_NOTIFICATIONS = 100
const STORAGE_RETRY_LIMIT = 3
const READ_NOTIFICATION_TTL = 24 * 60 * 60 * 1000

const STEP_LABELS = {
  'agentscope-log-query': '日志查询',
  'agentscope log query': '日志查询',
  'agentscope-patch-preview': '补丁预览',
  'agentscope-codex-repair': '代码修复',
  'agentscope-verification': '验证执行',
  'agentscope-deployment': '部署执行',
  'agentscope-final': '最终结论',
  'agentscope-error': '执行异常',
  'operator-rejection': '人工驳回',
  '日志查询': '日志查询',
  '补丁预览': '补丁预览',
  'CodeX 修复': '代码修复',
  'Cursor 修复': '代码修复',
  'Claude Code 修复': '代码修复',
  '本地修复': '代码修复',
  '验证执行': '验证执行',
  '部署执行': '部署执行',
  '最终结论': '最终结论',
  '执行异常': '执行异常',
  '人工驳回': '人工驳回',
  '修复确认': '修复确认',
  'JAR 制品构建': 'JAR 制品构建'
}

const STAGE_MESSAGES_SUCCESS = {
  '证据收集': '日志与证据收集完成',
  '故障分析': '根因分析完成',
  '修复处理': '代码修复完成',
  '验证构建': '代码验证通过，制品构建完成',
  '部署执行': '部署执行完成'
}

const STAGE_MESSAGES_FAIL = {
  '证据收集': '日志与证据收集失败',
  '故障分析': '根因分析未能定位问题',
  '修复处理': '代码修复失败',
  '验证构建': '验证未通过',
  '部署执行': '部署执行失败'
}

const SPECIALIST_LABELS = {
  LOG_EXTRACTION: '日志提取 Agent',
  ROOT_CAUSE_ANALYSIS: '根因分析 Agent',
  CODE_REPAIR: '修复规划 Agent',
  TEST_VERIFICATION: '测试验证 Agent'
}

function dedupKey(opsTaskId, status) {
  if (!opsTaskId || !status) return ''
  return `${opsTaskId}__${status}`
}

function isDuplicateNotification(opsTaskId, status) {
  const key = dedupKey(opsTaskId, status)
  if (!key) return false
  const ts = state.lastNotifiedKeys.get(key)
  if (ts && (Date.now() - ts) < LAST_NOTIFIED_TTL) {
    return true
  }
  state.lastNotifiedKeys.set(key, Date.now())
  return false
}

function cleanupLastNotifiedKeys() {
  const now = Date.now()
  for (const [key, ts] of state.lastNotifiedKeys) {
    if (now - ts > LAST_NOTIFIED_TTL * 2) {
      state.lastNotifiedKeys.delete(key)
    }
  }
}

function cleanupExpiredReadNotifications() {
  const now = Date.now()
  const before = state.notifications.length
  state.notifications = state.notifications.filter(n => {
    if (!n.read) return true
    const created = n.createdAt instanceof Date ? n.createdAt.getTime() : new Date(n.createdAt).getTime()
    return (now - created) < READ_NOTIFICATION_TTL
  })
  return state.notifications.length < before
}

function loadFromStorage() {
  try {
    const raw = localStorage.getItem(NOTIFICATION_STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        state.notifications = parsed
          .filter(n => !n.read)
          .map(n => ({
            ...n,
            createdAt: new Date(n.createdAt)
          }))
          .sort((a, b) => b.createdAt - a.createdAt)
      }
    }
  } catch {
    state.notifications = []
  }
  cleanupExpiredReadNotifications()
}

function saveToStorage() {
  const toSave = state.notifications
    .filter(n => !n.read)
    .map(n => ({
      ...n,
      createdAt: n.createdAt instanceof Date ? n.createdAt.toISOString() : n.createdAt
    }))
  let attempts = 0
  while (attempts <= STORAGE_RETRY_LIMIT) {
    try {
      localStorage.setItem(NOTIFICATION_STORAGE_KEY, JSON.stringify(toSave))
      return
    } catch {
      attempts++
      if (toSave.length > 10) {
        toSave.length = Math.max(10, Math.floor(toSave.length * 0.7))
      } else {
        toSave.length = 0
      }
    }
  }
  try {
    localStorage.removeItem(NOTIFICATION_STORAGE_KEY)
  } catch {}
}

function handleStorageEvent(event) {
  if (event.key !== NOTIFICATION_STORAGE_KEY) return
  if (event.newValue === null) {
    state.notifications = []
    return
  }
  try {
    const parsed = JSON.parse(event.newValue)
    if (Array.isArray(parsed)) {
      state.notifications = parsed
        .filter(n => !n.read)
        .map(n => ({
          ...n,
          createdAt: new Date(n.createdAt)
        }))
        .sort((a, b) => b.createdAt - a.createdAt)
    }
  } catch {}
}

function addNotification(notification) {
  const opsTaskId = notification.opsTaskId || null
  const status = notification.status || ''
  if (opsTaskId && status && isDuplicateNotification(opsTaskId, status)) {
    return null
  }
  cleanupLastNotifiedKeys()
  cleanupExpiredReadNotifications()
  idCounter++
  const item = {
    id: `notif_${idCounter}_${Date.now()}`,
    type: notification.type || 'info',
    title: notification.title || '通知',
    message: notification.message || '',
    opsTaskId: opsTaskId,
    status: status,
    read: false,
    createdAt: new Date()
  }
  state.notifications.unshift(item)
  if (state.notifications.length > MAX_NOTIFICATIONS) {
    state.notifications = state.notifications.slice(0, MAX_NOTIFICATIONS)
  }
  saveToStorage()

  state.toasts.push({ ...item })

  state.listeners.forEach(fn => {
    try { fn(item) } catch {}
  })

  return item
}

function removeToast(toastId) {
  const index = state.toasts.findIndex(t => t.id === toastId)
  if (index >= 0) {
    state.toasts.splice(index, 1)
  }
}

function markAsRead(notificationId) {
  const notif = state.notifications.find(n => n.id === notificationId)
  if (notif && !notif.read) {
    notif.read = true
    saveToStorage()
  }
}

function markAllAsRead() {
  let changed = false
  state.notifications.forEach(n => {
    if (!n.read) {
      n.read = true
      changed = true
    }
  })
  if (changed) {
    cleanupExpiredReadNotifications()
    saveToStorage()
  }
}

function removeNotification(notificationId) {
  const index = state.notifications.findIndex(n => n.id === notificationId)
  if (index >= 0) {
    state.notifications.splice(index, 1)
    saveToStorage()
  }
}

function clearAll() {
  if (state.notifications.length === 0) return
  state.notifications = []
  saveToStorage()
}

function subscribe(fn) {
  state.listeners.add(fn)
  return () => state.listeners.delete(fn)
}

const unreadCount = computed(() =>
  state.notifications.filter(n => !n.read).length
)

const unreadNotifications = computed(() =>
  state.notifications.filter(n => !n.read)
)

const allNotifications = computed(() => state.notifications)

function getStepDisplayName(stepName) {
  if (!stepName) return ''
  const raw = String(stepName)
  const specialistMatch = raw.match(/^(LOG_EXTRACTION|ROOT_CAUSE_ANALYSIS|CODE_REPAIR|TEST_VERIFICATION)\s+(已接单|已回传|执行失败)$/i)
  if (specialistMatch) {
    const code = specialistMatch[1].toUpperCase()
    return SPECIALIST_LABELS[code] || raw
  }
  const mapped = STEP_LABELS[raw] || STEP_LABELS[raw.toLowerCase()]
  if (mapped) return mapped
  const engineMatch = raw.match(/^(codex|cursor|claude)(?:\s+code)?(?:\s+cli)?(\s+修复.*)$/i)
  if (engineMatch) {
    const engineName = engineMatch[1] === 'codex' ? 'CodeX' : engineMatch[1][0].toUpperCase() + engineMatch[1].slice(1)
    return `${engineName}${engineMatch[2]}`
  }
  return raw.replace(/^agentscope[- ]?/i, '').replace(/-/g, ' ')
}

function getStageName(stepName) {
  const name = getStepDisplayName(stepName)
  if (/日志|查询|检索|证据|Git/i.test(name)) return '证据收集'
  if (/分析|诊断|根因|研判|定位/i.test(name)) return '故障分析'
  if (/补丁|修复|代码/i.test(name)) return '修复处理'
  if (/验证|测试|构建|制品/i.test(name)) return '验证构建'
  if (/部署|发布|上线/i.test(name)) return '部署执行'
  return name
}

function findFailureStep(timeline) {
  if (!Array.isArray(timeline) || timeline.length === 0) return null
  for (let i = timeline.length - 1; i >= 0; i--) {
    const step = timeline[i]
    if (step && step.success === false) return step
  }
  return timeline[timeline.length - 1] || null
}

function findLatestStep(timeline) {
  if (!Array.isArray(timeline) || timeline.length === 0) return null
  return timeline[timeline.length - 1]
}

function buildTaskNotificationTitle(task, type) {
  const taskName = task.title || '未知任务'
  const incident = task.incident || task
  const timeline = incident?.timeline || task.timeline || []

  if (type === 'FAILED') {
    const failedStep = findFailureStep(timeline)
    if (failedStep) {
      const stepName = getStepDisplayName(failedStep.step)
      const stage = getStageName(failedStep.step)
      const msg = STAGE_MESSAGES_FAIL[stage] || `${stepName}失败`
      return `${msg}：${taskName}`
    }
    const latestStep = findLatestStep(timeline)
    if (latestStep) {
      const stepName = getStepDisplayName(latestStep.step)
      return `${stepName}失败：${taskName}`
    }
    return `运维失败：${taskName}`
  }

  if (type === 'COMPLETED') {
    const latestStep = findLatestStep(timeline)
    if (latestStep) {
      const stage = getStageName(latestStep.step)
      const msg = STAGE_MESSAGES_SUCCESS[stage]
      if (msg) return `${msg}：${taskName}`
      const stepName = getStepDisplayName(latestStep.step)
      return `${stepName}完成：${taskName}`
    }
    return `运维完成：${taskName}`
  }

  return `运维通知：${taskName}`
}

function buildTaskNotificationMessage(task, type) {
  const incident = task.incident || task
  const timeline = incident?.timeline || task.timeline || []
  const context = incident?.context || task.context || {}

  if (type === 'FAILED') {
    const failedStep = findFailureStep(timeline)
    if (failedStep) {
      if (failedStep.summary) return failedStep.summary
      if (failedStep.step && /执行失败/.test(String(failedStep.step))) {
        const specialistMatch = String(failedStep.step).match(
          /^(LOG_EXTRACTION|ROOT_CAUSE_ANALYSIS|CODE_REPAIR|TEST_VERIFICATION)\s+执行失败$/i
        )
        if (specialistMatch) {
          const code = specialistMatch[1].toUpperCase()
          return `${SPECIALIST_LABELS[code]}执行失败，请查看详情`
        }
      }
      const stepName = getStepDisplayName(failedStep.step)
      return `步骤「${stepName}」执行失败`
    }
    if (task.latestSummary) return task.latestSummary
    if (incident?.context?.codexReviewStatus === 'REVERTED') return '修复已被撤销'
    return '运维流程中出现异常，请查看详情'
  }

  if (type === 'COMPLETED') {
    if (incident?.context?.automaticDeploymentSuccessful) {
      return 'Java 补丁已自动备份、部署并启动'
    }
    if (incident?.context?.manualDeploymentVerified) {
      return '人工部署验证已通过，任务已完结'
    }
    if (incident?.status === 'RESOLVED') {
      return '故障已解决'
    }
    if (incident?.context?.codexReviewStatus === 'ACCEPTED') {
      return '修复方案已接受，验证通过'
    }
    if (context?.artifactName) {
      return `制品 ${context.artifactName} 已构建完成`
    }
    const latestStep = findLatestStep(timeline)
    if (latestStep?.summary) return latestStep.summary
    if (task.latestSummary) return task.latestSummary
    return '运维任务执行完成'
  }

  return task.description || task.latestSummary || ''
}

function createOpsTaskNotification(task, type) {
  const typeMap = {
    COMPLETED: 'success',
    FAILED: 'error'
  }
  const title = buildTaskNotificationTitle(task, type)
  const message = buildTaskNotificationMessage(task, type)

  return addNotification({
    type: typeMap[type] || 'info',
    title,
    message,
    opsTaskId: task.id,
    status: type
  })
}

loadFromStorage()

if (typeof window !== 'undefined') {
  window.addEventListener('storage', handleStorageEvent)
}

export function useNotification() {
  return {
    notifications: allNotifications,
    unreadNotifications,
    unreadCount,
    toasts: computed(() => state.toasts),
    addNotification,
    markAsRead,
    markAllAsRead,
    removeNotification,
    removeToast,
    clearAll,
    subscribe,
    createOpsTaskNotification
  }
}
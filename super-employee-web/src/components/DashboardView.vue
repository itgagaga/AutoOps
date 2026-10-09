<!-- @author yangdb -->
<script setup>
import { computed, defineAsyncComponent, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, Check, CircleCheck, CircleClose, Close, DArrowLeft, DArrowRight, Download, Folder, House, Loading, Menu as MenuIcon, Monitor, Plus, Refresh, Search, UserFilled, VideoPlay } from '@element-plus/icons-vue'
import { acceptIncidentRepair, approveIncident, approveRequest, changePassword, completeIncidentArtifactVerification, createOpsTask, decideIncidentBranchMerge, deleteOpsTask, detectDeploymentPath, downloadIncidentArtifact, downloadIncidentDeploymentScripts, getHealth, getIncident, getOpsTask, listApprovals, listDictOptions, listFilesystem, listFilesystemRoots, listIncidents, listOpsTasks, listResources, rejectIncident, rejectRequest, restartOpsTaskAnalysis, revertIncidentRepair, setOpsTaskEnabled, updateOpsTask } from '../api'
import AgentManagementPage from './AgentManagementPage.vue'
import AgentExecutionPage from './AgentExecutionPage.vue'
import ApprovalCenterPage from './ApprovalCenterPage.vue'
import AuditLogPage from './AuditLogPage.vue'
import MenuManagementDialog from './MenuManagementDialog.vue'
import RoleManagementDialog from './RoleManagementDialog.vue'
import ResourceManagementPage from './ResourceManagementPage.vue'
import SidebarMenuItem from './SidebarMenuItem.vue'
import UserManagementDialog from './UserManagementDialog.vue'
import ToolManagementPage from './ToolManagementPage.vue'
import DictManagementPage from './DictManagementPage.vue'
import AgentAuthorizationPage from './AgentAuthorizationPage.vue'
import KnowledgeBasePage from './KnowledgeBasePage.vue'
import SshClientPage from './SshClientPage.vue'
import DbClientPage from './DbClientPage.vue'
import GitClientPage from './GitClientPage.vue'
import OperationsGuideHome from './OperationsGuideHome.vue'
import CodexInstallGuidePage from './CodexInstallGuidePage.vue'
import RemoteDirectoryPicker from './RemoteDirectoryPicker.vue'
import NotificationBell from './NotificationBell.vue'
import NotificationToast from './NotificationToast.vue'
import { useNotification } from '../useNotification'
import { optionValue, toCssMap } from '../dict'
import { useTablePagination } from '../useTablePagination'
import { REPAIR_ENGINE_OPTIONS, repairEngineDisplayName, repairEngineLabel } from '../repairEngine'

const RepairDiffDialog = defineAsyncComponent(() =>
  import('./dashboard/RepairDiffDialog.vue')
)
const IncidentDetailDrawer = defineAsyncComponent(() =>
  import('./dashboard/IncidentDetailDrawer.vue')
)

const props = defineProps({ user: { type: Object, required: true } })
const emit = defineEmits(['logout', 'session-refresh'])
const { toasts, createOpsTaskNotification } = useNotification()
const opsTasks = ref([])
const hosts = ref([])
const selectedOps = ref(null)
const selected = ref(null)
const loading = ref(false)
const submitting = ref(false)
const previousTaskStatuses = new Map()
const detectingDeploymentPath = ref(false)
const deploymentCandidateInput = ref('')
const deploymentDetectionMessage = ref('')
/** 识别返回的候选明细（含 jars），供歧义时人工绑定。 */
const detectionCandidates = ref([])
const createVisible = ref(false)
const editingOpsId = ref('')
const health = ref('检查中')
const formRef = ref()
const passwordVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref()
const passwordForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const passwordRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 64, message: '新密码长度为 8 至 64 位', trigger: 'blur' }
  ],
  confirmPassword: [{
    validator: (_rule, value, callback) => {
      if (!value) callback(new Error('请再次输入新密码'))
      else if (value !== passwordForm.newPassword) callback(new Error('两次输入的新密码不一致'))
      else callback()
    },
    trigger: 'blur'
  }]
}
const mobileNav = ref(false)
const SIDEBAR_KEY = 'super_employee_sidebar_collapsed'
const sidebarCollapsed = ref(localStorage.getItem(SIDEBAR_KEY) === '1' || (typeof window !== 'undefined' && window.innerWidth < 1100 && window.innerWidth > 768))
const currentMenu = ref('HOME')
const openedMenus = ref([])
const pageTabs = ref(null)
const draggingTabCode = ref('')
const dragOverTabCode = ref('')
const tabContextMenu = reactive({ visible: false, code: '', x: 0, y: 0 })
const contentMain = ref(null)
const menuScrollPositions = new Map()
const MENU_STATE_KEY = 'super_employee_opened_menus'
const CURRENT_MENU_KEY = 'super_employee_current_menu'
const FILTERS_STORAGE_KEY = 'super_employee_ops_filters'
const PAGINATION_STORAGE_KEY = 'super_employee_ops_pagination'
const HIDDEN_MENU_CODES = new Set(['SYS_AUDIT', 'OPS_APPROVAL', 'RESOURCE_APPLICATION'])
const VIRTUAL_MENU_ITEMS = {
  CODEX_INSTALL_GUIDE: {
    code: 'CODEX_INSTALL_GUIDE',
    name: '本地修复引擎向导',
    permissionCode: 'AGENT_MANAGEMENT'
  }
}
const isMobile = ref(typeof window !== 'undefined' && window.innerWidth <= 768)
const MOBILE_BREAKPOINT = 768
function handleResize() {
  isMobile.value = window.innerWidth <= MOBILE_BREAKPOINT
  if (isMobile.value && sidebarCollapsed.value === false && window.innerWidth < 1100) {
    sidebarCollapsed.value = true
  }
}
if (typeof window !== 'undefined') window.addEventListener('resize', handleResize)
onUnmounted(() => { if (typeof window !== 'undefined') window.removeEventListener('resize', handleResize) })
const filters = reactive({ keyword: '', status: '', priority: '' })
const form = reactive({
  title: '', description: '', priority: 'P2', hostId: '', workspacePath: '', frontendWorkspacePath: '',
  backendKind: 'MAVEN', clientKind: 'NONE', clientVerifyCommand: '', backendVerifyCommand: '',
  clientDeployPath: '', clientDistDir: 'dist', dockerComposeFile: 'docker-compose.yml', dockerImageName: '',
  repairEngine: 'auto',
  applicationCode: '', repositoryUrl: '', ownerName: '', autoDeploy: false, deploymentPath: '', deploymentPaths: [],
  deploymentJar: '', deploymentTargets: [],
  scheduleEnabled: true, pullIntervalMinutes: 30, logRangeMinutes: 30, logTemplate: 'JOURNAL_LOGS',
  logPath: '', logServiceName: '', autoAnalyze: true
})
const repairEngineOptions = REPAIR_ENGINE_OPTIONS
const backendKindOptions = [
  { value: 'MAVEN', label: 'Java Maven（JAR）', marker: 'hasMaven', hint: '含 pom.xml' },
  { value: 'JAVA_WAR', label: 'Java WAR（Tomcat）', marker: 'hasMaven', hint: '含 pom.xml，packaging=war，前后端不分离老项目' },
  { value: 'GRADLE', label: 'Java Gradle', marker: 'hasGradle', hint: '含 build.gradle' },
  { value: 'NODE', label: 'Node.js', marker: 'hasNode', hint: '含 package.json' },
  { value: 'GO', label: 'Go', marker: 'hasGo', hint: '含 go.mod' },
  { value: 'PYTHON', label: 'Python', marker: 'hasPython', hint: '含 pyproject.toml / requirements.txt' },
  { value: 'DOCKER', label: 'Docker', marker: 'hasDocker', hint: '含 Dockerfile / compose' }
]
const clientKindOptions = [
  { value: 'NONE', label: '无端侧' },
  { value: 'WEB_NPM', label: 'Web（npm）' },
  { value: 'UNI_APP', label: 'uni-app' },
  { value: 'MP_WEIXIN_NATIVE', label: '微信原生小程序' }
]
const backendKindLabel = (kind) => backendKindOptions.find(item => item.value === kind)?.label || kind || '—'
const clientKindLabel = (kind) => clientKindOptions.find(item => item.value === kind)?.label || kind || '—'
const backendMarkerKey = computed(() => backendKindOptions.find(item => item.value === form.backendKind)?.marker || 'hasMaven')
const backendWorkspaceHint = computed(() => backendKindOptions.find(item => item.value === form.backendKind)?.hint || '含工程标记文件')
const artifactFieldLabel = computed(() => {
  if (form.backendKind === 'JAVA_WAR') return 'WAR 相对路径'
  if (form.backendKind === 'GO') return '二进制名'
  if (form.backendKind === 'NODE' || form.backendKind === 'PYTHON') return '发布包名'
  if (form.backendKind === 'DOCKER') return '制品（可选）'
  return 'JAR 相对路径'
})
const clientVerifyPlaceholder = computed(() => {
  if (form.clientKind === 'UNI_APP') return '默认 npm run build:mp-weixin，可覆盖为 npm run build:h5 等'
  if (form.clientKind === 'WEB_NPM') return '默认 npm run build，可覆盖'
  return ''
})
const backendVerifyPlaceholder = computed(() => {
  const map = {
    MAVEN: '默认 mvn test',
    JAVA_WAR: '默认 mvn test（端侧选「无端侧」）',
    GRADLE: '默认 gradle test',
    NODE: '默认 npm test',
    GO: '默认 go test ./...',
    PYTHON: '默认 pytest -q',
    DOCKER: '默认 docker compose build'
  }
  return map[form.backendKind] || '按后端类型默认'
})
const rules = {
  title: [{ required: true, message: '请输入运维任务标题', trigger: 'blur' }],
  description: [{ required: true, message: '请输入运维目标与现象', trigger: 'blur' }],
  hostId: [{ required: true, message: '请选择目标主机', trigger: 'change' }],
  workspacePath: [{ required: true, message: '请配置后端代码工作区', trigger: 'blur' }],
  frontendWorkspacePath: [{
    validator: (_rule, value, callback) => {
      if (form.clientKind && form.clientKind !== 'NONE' && !String(value || '').trim()) {
        callback(new Error('已选择端侧类型，请配置端侧工程目录'))
      } else callback()
    },
    trigger: 'change'
  }],
  applicationCode: [
    { required: true, message: '请输入应用编码', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$/, message: '请输入2至64位字母、数字、点、下划线或短横线', trigger: 'blur' }
  ],
  deploymentPaths: [{
    validator: (_rule, value, callback) => {
      if (form.autoDeploy && (!Array.isArray(value) || !value.length)) callback(new Error('开启自动部署时必须选择候选部署目录'))
      else if (form.autoDeploy && !form.deploymentTargets.length) callback(new Error('请先识别并绑定至少一个部署目标'))
      else callback()
    },
    trigger: 'change'
  }],
  pullIntervalMinutes: [{ required: true, message: '请设置拉日志间隔', trigger: 'change' }],
  logRangeMinutes: [{ required: true, message: '请设置日志时间范围', trigger: 'change' }],
  logPath: [{
    validator: (_rule, value, callback) => {
      if (form.scheduleEnabled && form.logTemplate === 'APP_LOG_FILE' && !String(value || '').trim()) {
        callback(new Error('请选择远端日志路径'))
        return
      }
      callback()
    },
    trigger: 'change'
  }]
}
const logTemplateOptions = [
  { value: 'JOURNAL_LOGS', label: 'journalctl 系统日志' },
  { value: 'APP_LOG_FILE', label: '应用日志文件' },
  { value: 'JAVA_APPLICATION_LOGS', label: 'Java 服务 journal' }
]
const logPullRangeOptions = [
  { value: 30, label: '最近半小时' },
  { value: 60, label: '最近 1 小时' },
  { value: 120, label: '最近 2 小时' },
  { value: 360, label: '最近 6 小时' },
  { value: 720, label: '最近 12 小时' },
  { value: 1440, label: '最近 24 小时' }
]
const logPullIntervalOptions = [
  { value: 5, label: '每 5 分钟' },
  { value: 10, label: '每 10 分钟' },
  { value: 30, label: '每 30 分钟' },
  { value: 60, label: '每 1 小时' }
]
const formatPullRange = (minutes) => logPullRangeOptions.find(item => item.value === Number(minutes))?.label || `${minutes || 30} 分钟`
const formatPullInterval = (minutes) => logPullIntervalOptions.find(item => item.value === Number(minutes))?.label || `每 ${minutes || 30} 分钟`
const pickerVisible = ref(false)
/** backend：选 Maven 工作区；frontend：选端侧工程 */
const pickerTarget = ref('backend')
const browsing = ref(false)
const currentPath = ref('')
const parentPath = ref(null)
const currentHasPom = ref(false)
const currentHasPackageJson = ref(false)
const currentHasMiniProgram = ref(false)
const entries = ref([])
const roots = ref([])
const pickerNeedsMiniProgram = computed(() =>
  pickerTarget.value === 'frontend' && form.clientKind === 'MP_WEIXIN_NATIVE'
)
const currentHasBackend = ref(false)
const pickerSelectable = computed(() => {
  if (pickerTarget.value !== 'frontend') return currentHasBackend.value
  return pickerNeedsMiniProgram.value ? currentHasMiniProgram.value : currentHasPackageJson.value
})
const pickerDialogTitle = computed(() => {
  if (pickerTarget.value !== 'frontend') {
    return `选择后端工作区（${backendWorkspaceHint.value}）`
  }
  return pickerNeedsMiniProgram.value
    ? '选择微信小程序工程（project.config.json / app.json）'
    : '选择端侧工程目录（package.json）'
})
const priorityOptions = ref([
  { code: 'P0', label: 'P0', value: 'P0', cssType: 'danger' },
  { code: 'P1', label: 'P1', value: 'P1', cssType: 'warning' },
  { code: 'P2', label: 'P2', value: 'P2', cssType: '' },
  { code: 'P3', label: 'P3', value: 'P3', cssType: 'info' }
])
const priorityCss = computed(() => toCssMap(priorityOptions.value))
const incidentLabels = {
  NEW: '新建', RUNNING: '分析中', APPROVAL_PENDING: '待审批', DEPLOYING: '部署中',
  RESOLVED: '已解决', REJECTED: '已驳回', FAILED: '失败', TRIAGED: '已分诊',
  DIAGNOSED: '已诊断', TESTING: '测试中', PATCH_READY: '待验证', WAITING_INPUT: '待人工验证'
}
const incidentStatusType = {
  NEW: 'info', RUNNING: '', APPROVAL_PENDING: 'warning', DEPLOYING: 'warning',
  RESOLVED: 'success', REJECTED: 'danger', FAILED: 'danger', TRIAGED: 'info',
  DIAGNOSED: '', TESTING: 'warning', PATCH_READY: 'warning', WAITING_INPUT: 'warning'
}
const opsStatusLabels = {
  CREATED: '已创建', ANALYZING: '分析中', WAITING_APPROVAL: '待审批', COMPLETED: '已完成', FAILED: '失败'
}
const opsStatusType = {
  CREATED: 'info', ANALYZING: '', WAITING_APPROVAL: 'warning', COMPLETED: 'success', FAILED: 'danger'
}
const stats = computed(() => ({
  total: opsTasks.value.length,
  analyzing: opsTasks.value.filter(x => x.status === 'ANALYZING').length,
  pending: opsTasks.value.filter(x => x.status === 'WAITING_APPROVAL').length,
  completed: opsTasks.value.filter(x => x.status === 'COMPLETED').length
}))
const enabledHosts = computed(() => hosts.value.filter(item => item.enabled))
const selectedHostLogPaths = computed(() => {
  const host = enabledHosts.value.find(item => item.id === form.hostId)
  const paths = host?.allowedLogPaths
  const items = Array.isArray(paths)
    ? paths
    : (typeof paths === 'string' ? paths.split('|') : [])
  return items.map(item => String(item || '').trim()).filter(Boolean)
})
const filteredOps = computed(() => opsTasks.value.filter(item => {
  const keyword = filters.keyword.trim().toLowerCase()
  const text = `${item.title || ''} ${item.description || ''} ${item.hostName || ''} ${item.incidentId || ''}`.toLowerCase()
  return (!keyword || text.includes(keyword))
    && (!filters.status || item.status === filters.status)
    && (!filters.priority || item.priority === filters.priority)
}))
const SKELETON_ROWS = 5
const showSkeleton = computed(() => loading.value && opsTasks.value.length === 0)
const showTableLoading = computed(() => loading.value && opsTasks.value.length > 0)
const { page, pageSize, total, paged, onSizeChange } = useTablePagination(filteredOps)

const detailVisible = ref(false)
const detailApprovals = ref([])
const detailIncidents = ref([])
const incidentPage = ref(1)
const incidentPageSize = ref(5)
const pagedDetailIncidents = computed(() => {
  const start = (incidentPage.value - 1) * incidentPageSize.value
  return detailIncidents.value.slice(start, start + incidentPageSize.value)
})
const decidingApprovalId = ref('')
const detailLoading = ref(false)
const detailSecondaryLoading = ref(false)
const detailRefreshing = ref(false)
const detailLiveRefreshing = ref(false)
const detailEnabling = ref(false)
const repairReviewing = ref(false)
const repairDiffVisible = ref(false)
const repairDiff = computed(() => selected.value?.context?.codexActualDiff || '')
function isInternalRepairDeliveryPath(path) {
  const normalized = String(path || '').replace(/\\/g, '/').replace(/^\.\//, '')
  return normalized === '.zhiwei/repair-prompt.txt' || normalized.startsWith('.zhiwei/')
}
const repairFiles = computed(() => (selected.value?.context?.codexChangedFiles || [])
  .filter(path => !isInternalRepairDeliveryPath(path)))
const latestCodexRepairStep = computed(() => [...(selected.value?.timeline || [])]
  .reverse()
  .find(step => isLocalRepairStep(step.step)))
const repairReviewAvailable = computed(() =>
  latestCodexRepairStep.value?.success !== false
  && !!repairDiff.value.trim())
const repairPending = computed(() => repairReviewAvailable.value
  && !!selected.value?.context?.codexRepairId
  && selected.value?.context?.codexReviewStatus === 'PENDING')
watch(repairReviewAvailable, available => {
  if (!available) repairDiffVisible.value = false
})
const artifactReady = computed(() =>
  ['WAITING_BRANCH_MERGE', 'WAITING_MANUAL_DEPLOYMENT'].includes(selected.value?.context?.codexRepairStage)
  && !!selected.value?.context?.artifactName)
const branchMergePending = computed(() =>
  selected.value?.context?.codexRepairStage === 'WAITING_BRANCH_MERGE')
const manualDeploymentReady = computed(() =>
  selected.value?.context?.codexRepairStage === 'WAITING_MANUAL_DEPLOYMENT')
const artifactOperating = ref(false)
const activeRepairEngineLabel = computed(() =>
  repairEngineDisplayName(selected.value?.context?.repairEngine || selected.value?.context?.repairEnginePreference))
const activeRepairStepLabel = computed(() => {
  const engine = String(selected.value?.context?.repairEngine || selected.value?.context?.repairEnginePreference || '').trim().toLowerCase()
  if (!engine || engine === 'auto') return '本地修复'
  return `${activeRepairEngineLabel.value} 修复`
})

function textLines(value) {
  const lines = String(value || '').replace(/\r\n/g, '\n').split('\n')
  if (lines.at(-1) === '') lines.pop()
  return lines
}

function lineOperations(before, after) {
  let prefix = 0
  while (prefix < before.length && prefix < after.length && before[prefix] === after[prefix]) prefix++
  let suffix = 0
  while (suffix < before.length - prefix && suffix < after.length - prefix
    && before[before.length - 1 - suffix] === after[after.length - 1 - suffix]) suffix++

  const left = before.slice(prefix, before.length - suffix)
  const right = after.slice(prefix, after.length - suffix)
  const operations = before.slice(0, prefix).map((text, index) => ({
    type: 'context', text, leftNo: index + 1, rightNo: index + 1
  }))

  if (left.length * right.length <= 2_000_000) {
    const width = right.length + 1
    const matrix = new Uint32Array((left.length + 1) * width)
    for (let i = 1; i <= left.length; i++) {
      for (let j = 1; j <= right.length; j++) {
        matrix[i * width + j] = left[i - 1] === right[j - 1]
          ? matrix[(i - 1) * width + j - 1] + 1
          : Math.max(matrix[(i - 1) * width + j], matrix[i * width + j - 1])
      }
    }
    const middle = []
    let i = left.length
    let j = right.length
    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && left[i - 1] === right[j - 1]) {
        middle.push({ type: 'context', text: left[i - 1], leftNo: prefix + i, rightNo: prefix + j })
        i--
        j--
      } else if (j > 0 && (i === 0 || matrix[i * width + j - 1] >= matrix[(i - 1) * width + j])) {
        middle.push({ type: 'add', text: right[j - 1], rightNo: prefix + j })
        j--
      } else {
        middle.push({ type: 'remove', text: left[i - 1], leftNo: prefix + i })
        i--
      }
    }
    operations.push(...middle.reverse())
  } else {
    operations.push(...left.map((text, index) => ({ type: 'remove', text, leftNo: prefix + index + 1 })))
    operations.push(...right.map((text, index) => ({ type: 'add', text, rightNo: prefix + index + 1 })))
  }

  for (let index = suffix; index > 0; index--) {
    const leftNo = before.length - index + 1
    const rightNo = after.length - index + 1
    operations.push({ type: 'context', text: before[leftNo - 1], leftNo, rightNo })
  }
  return operations
}

function pairAndCollapseOperations(operations) {
  const rows = []
  for (let index = 0; index < operations.length;) {
    if (operations[index].type === 'context') {
      let end = index
      while (end < operations.length && operations[end].type === 'context') end++
      const unchanged = operations.slice(index, end)
      if (unchanged.length > 10) {
        rows.push(...unchanged.slice(0, 3).map(item => ({ ...item, left: item.text, right: item.text })))
        rows.push({ type: 'skip', count: unchanged.length - 6 })
        rows.push(...unchanged.slice(-3).map(item => ({ ...item, left: item.text, right: item.text })))
      } else {
        rows.push(...unchanged.map(item => ({ ...item, left: item.text, right: item.text })))
      }
      index = end
      continue
    }
    let end = index
    const removed = []
    const added = []
    while (end < operations.length && operations[end].type !== 'context') {
      if (operations[end].type === 'remove') removed.push(operations[end])
      else added.push(operations[end])
      end++
    }
    for (let offset = 0; offset < Math.max(removed.length, added.length); offset++) {
      rows.push({
        type: removed[offset] && added[offset] ? 'change' : (removed[offset] ? 'remove' : 'add'),
        left: removed[offset]?.text ?? '',
        right: added[offset]?.text ?? '',
        leftNo: removed[offset]?.leftNo,
        rightNo: added[offset]?.rightNo
      })
    }
    index = end
  }
  return rows
}

const repairDiffFiles = computed(() => {
  const value = String(repairDiff.value || '').replace(/\r\n/g, '\n')
  const header = /^===== (.+) =====$/gm
  const matches = [...value.matchAll(header)]
  return matches.map((match, index) => {
    const block = value.slice(match.index + match[0].length, matches[index + 1]?.index ?? value.length)
    const beforeMarker = '\n--- 修复前\n'
    const afterMarker = '\n+++ 修复后\n'
    const beforeStart = block.indexOf(beforeMarker)
    const afterStart = block.indexOf(afterMarker, beforeStart + beforeMarker.length)
    const beforeText = beforeStart >= 0 && afterStart >= 0
      ? block.slice(beforeStart + beforeMarker.length, afterStart)
      : ''
    const afterText = afterStart >= 0 ? block.slice(afterStart + afterMarker.length) : ''
    const beforeMissing = beforeText.trim() === '(文件不存在)'
    const afterMissing = afterText.trim() === '(文件不存在)'
    const before = beforeMissing ? [] : textLines(beforeText)
    const after = afterMissing ? [] : textLines(afterText)
    const operations = lineOperations(before, after)
    return {
      path: match[1],
      beforeMissing,
      afterMissing,
      additions: operations.filter(item => item.type === 'add').length,
      deletions: operations.filter(item => item.type === 'remove').length,
      rows: pairAndCollapseOperations(operations)
    }
  }).filter(file => !isInternalRepairDeliveryPath(file.path))
})
const terminalIncidentStatuses = new Set(['RESOLVED', 'REJECTED', 'FAILED', 'CANCELLED', 'TIMEOUT'])
const terminalOpsStatuses = new Set(['COMPLETED', 'FAILED'])
const detailIsActive = computed(() => {
  if (terminalOpsStatuses.has(selectedOps.value?.status)
      || terminalIncidentStatuses.has(selected.value?.status)) return false
  if (['CREATED', 'ANALYZING', 'WAITING_APPROVAL'].includes(selectedOps.value?.status)) return true
  return !!selected.value?.status && !terminalIncidentStatuses.has(selected.value.status)
})
const pullTimelineRunning = computed(() =>
  selectedOps.value?.status === 'CREATED' && !!selectedOps.value?.enabled)
const pullTimelineType = computed(() => {
  if (pullTimelineRunning.value) return 'primary'
  if (selectedOps.value?.status === 'FAILED'
      && String(selectedOps.value?.latestSummary || '').includes('日志拉取失败')) return 'danger'
  return selectedOps.value?.lastPullAt ? 'success' : 'info'
})
const repairStageLoadingLabels = {
  PREPARING_BRANCH: '正在准备安全修复分支',
  PATCH_PREVIEW: 'Agent 正在生成补丁方案',
  CODEX_LOCAL_REPAIR: '本地修复引擎正在修改代码',
  LOCAL_REPAIR_RUNNING: '本地修复引擎正在后台修改代码',
  BUILDING_ARTIFACT: '正在构建部署制品',
  WAITING_REVIEW: '等待人工审阅修复结果',
  WAITING_BRANCH_MERGE: '等待确认是否合并主分支',
  WAITING_MANUAL_DEPLOYMENT: '等待下载制品并完成部署验证',
  DEPLOYING: '正在部署修复制品',
  VERIFYING: '正在验证修复结果'
}

function agentProgressLabel(step) {
  const raw = String(step?.step || '')
  const match = raw.match(/^(LOG_EXTRACTION|ROOT_CAUSE_ANALYSIS|CODE_REPAIR|TEST_VERIFICATION)\s+(已接单|已回传|执行失败)$/i)
  if (!match) return ''
  const code = match[1].toUpperCase()
  const state = match[2]
  const names = {
    LOG_EXTRACTION: '日志提取 Agent',
    ROOT_CAUSE_ANALYSIS: '根因分析 Agent',
    CODE_REPAIR: '修复规划 Agent',
    TEST_VERIFICATION: '测试验证 Agent'
  }
  if (state === '已接单') return `${names[code]} 正在执行`
  if (state === '执行失败') return `${names[code]} 执行失败，正在收敛异常`
  return {
    LOG_EXTRACTION: '根因分析 Agent 正在研判证据',
    ROOT_CAUSE_ANALYSIS: '修复规划 Agent 正在制定方案',
    CODE_REPAIR: '协调 Agent 正在整理修复方案',
    TEST_VERIFICATION: '协调 Agent 正在汇总验证结果'
  }[code]
}

const nextTimelineLabel = computed(() => {
  if (pullTimelineRunning.value) return '正在等待日志拉取完成'
  if (branchMergePending.value) return '等待确认是否将运维分支合并到主分支'
  if (artifactReady.value) return '等待下载 JAR 并完成人工部署验证'
  if (repairPending.value) return '等待人工审阅修复结果'
  if (selectedOps.value?.status === 'WAITING_APPROVAL') return '等待高风险操作审批'
  const repairStage = String(selected.value?.context?.codexRepairStage || '').toUpperCase()
  if (repairStageLoadingLabels[repairStage]) return repairStageLoadingLabels[repairStage]
  const latestStep = (selected.value?.timeline || []).at(-1)
  const specialistProgress = agentProgressLabel(latestStep)
  if (specialistProgress) return specialistProgress
  const stage = latestStep ? timelineStageName(latestStep.step) : ''
  if (stage === '证据收集') return '根因分析 Agent 正在研判证据'
  if (stage === '故障分析') return '修复规划 Agent 正在制定方案'
  if (stage === '修复处理') return '测试验证 Agent 正在检查修复结果'
  if (stage === '验证构建') return '正在准备部署或生成最终结论'
  if (stage === '部署执行') return '正在执行部署后健康检查'
  if (selectedOps.value?.status === 'ANALYZING') return '协调 Agent 正在编排下一处理步骤'
  return '正在同步任务最新进度'
})
let detailPollTimer = null
let detailRequestToken = 0
let detailSecondaryRequestToken = 0
const DETAIL_POLL_INTERVAL_MS = 10000
const approvalStatusLabel = status => ({
  PENDING: '待审批', APPROVED: '已批准', REJECTED: '已拒绝'
}[status] || status || '待审批')
const approvalStatusType = status => ({
  PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger'
}[status] || 'info')
const stepLabels = {
  'agentscope-log-query': '日志查询',
  'agentscope log query': '日志查询',
  'agentscope-patch-preview': '补丁预览',
  'agentscope-codex-repair': 'CodeX 修复',
  'agentscope-verification': '验证执行',
  'agentscope-deployment': '部署执行',
  'agentscope-final': '最终结论',
  'agentscope-error': '执行异常',
  'operator-rejection': '人工驳回',
  '日志查询': '日志查询',
  '补丁预览': '补丁预览',
  'CodeX 修复': 'CodeX 修复',
  '验证执行': '验证执行',
  '部署执行': '部署执行',
  '最终结论': '最终结论',
  '执行异常': '执行异常',
  '人工驳回': '人工驳回'
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function priorityType(priority) {
  return priorityCss.value[priority] || 'info'
}

function incidentStatusLabel(incident) {
  if (incident?.context?.codexReviewStatus === 'PENDING') return '待确认修复'
  return incidentLabels[incident?.status] || incident?.status || '未知'
}

function incidentStatusTagType(incident) {
  if (incident?.context?.codexReviewStatus === 'PENDING') return 'warning'
  return incidentStatusType[incident?.status] || 'info'
}

/** 列表行：运维任务状态 + 故障状态融合为单一展示，避免「已完成 / 已解决」并排。 */
function fuseOpsRowStatus(row) {
  const opsStatus = row?.status || ''
  const incidentStatus = row?.incidentStatus || ''
  if (opsStatus === 'FAILED' || incidentStatus === 'FAILED') {
    return { text: '失败', type: 'danger' }
  }
  if (incidentStatus === 'REJECTED') {
    return { text: '已驳回', type: 'danger' }
  }
  if (opsStatus === 'WAITING_APPROVAL'
      || ['APPROVAL_PENDING', 'WAITING_INPUT', 'PATCH_READY', 'TESTING'].includes(incidentStatus)) {
    return { text: '待确认', type: 'warning' }
  }
  if (opsStatus === 'ANALYZING'
      || ['NEW', 'RUNNING', 'TRIAGED', 'DIAGNOSED', 'DEPLOYING'].includes(incidentStatus)) {
    return { text: '处理中', type: '' }
  }
  if (opsStatus === 'COMPLETED'
      && (!incidentStatus || incidentStatus === 'RESOLVED' || incidentStatus === 'CANCELLED')) {
    return { text: '已完成', type: 'success' }
  }
  if (opsStatus === 'COMPLETED' && incidentStatus) {
    return {
      text: incidentLabels[incidentStatus] || incidentStatus,
      type: incidentStatusType[incidentStatus] || 'success'
    }
  }
  if (opsStatus) {
    return {
      text: opsStatusLabels[opsStatus] || opsStatus,
      type: opsStatusType[opsStatus] || 'info'
    }
  }
  if (incidentStatus) {
    return {
      text: incidentLabels[incidentStatus] || incidentStatus,
      type: incidentStatusType[incidentStatus] || 'info'
    }
  }
  return { text: '—', type: 'info' }
}

function formatStepName(step) {
  if (!step) return '执行步骤'
  const raw = String(step)
  const specialist = raw.match(/^(LOG_EXTRACTION|ROOT_CAUSE_ANALYSIS|CODE_REPAIR|TEST_VERIFICATION)\s+(已接单|已回传|执行失败)$/i)
  if (specialist) {
    const labels = {
      LOG_EXTRACTION: '日志提取',
      ROOT_CAUSE_ANALYSIS: '根因分析',
      CODE_REPAIR: '修复规划',
      TEST_VERIFICATION: '测试验证'
    }
    return `${labels[specialist[1].toUpperCase()]} ${specialist[2]}`
  }
  if (raw === 'agentscope-codex-repair' || raw === 'CodeX 修复') {
    return activeRepairStepLabel.value
  }
  const mapped = stepLabels[raw] || stepLabels[raw.toLowerCase()]
  if (mapped && mapped !== 'CodeX 修复') return mapped
  const match = raw.match(/^(codex|cursor|claude)(?:\s+code)?(?:\s+cli)?(\s+修复.*)$/i)
  if (match) {
    return `${repairEngineDisplayName(match[1])}${match[2]}`
  }
  return raw.replace(/^agentscope[- ]?/i, '').replace(/-/g, ' ')
}

function isLocalRepairStep(step) {
  const name = formatStepName(step)
  return name === '本地修复'
    || name === 'CodeX 修复'
    || name === 'Cursor 修复'
    || name === 'Claude Code 修复'
    || name.startsWith('CodeX 修复')
    || name.startsWith('Cursor 修复')
    || name.startsWith('Claude Code 修复')
    || name.startsWith('本地修复')
    || String(step) === 'agentscope-codex-repair'
}

function timelineStageName(step) {
  const name = formatStepName(step)
  if (/日志|查询|检索|证据|Git/i.test(name)) return '证据收集'
  if (/分析|诊断|根因|研判|定位/i.test(name)) return '故障分析'
  if (/补丁|修复|代码/i.test(name) && !/验证|测试|构建|部署|合并/i.test(name)) return '修复处理'
  // 部署/合并收尾优先，避免「制品/构建」把部署步骤误归到验证构建并重复展示 JAR
  if (/部署|发布|上线|合并主分支|主分支合并/i.test(name)) return '部署执行'
  if (/验证|测试|构建|制品/i.test(name)) return '验证构建'
  return name
}

function timelineStepPriority(step) {
  const name = formatStepName(step.step)
  if (step.success === false) return 100
  if (name === '修复确认' || isLocalRepairStep(step.step)) return 80
  if (name === 'Java 自动部署' || name === '一键部署脚本生成' || /主分支合并|合并主分支/.test(name)) return 75
  if (name === 'JAR 制品构建') return 70
  if (/最终结论|执行异常|人工驳回/.test(name)) return 60
  return 0
}

function fileNameOf(path) {
  const value = String(path || '').replace(/\\/g, '/')
  const index = value.lastIndexOf('/')
  return index >= 0 ? value.slice(index + 1) : value
}

function incidentArtifactContext(context, timeline, opsTask) {
  const ctx = context || {}
  const ops = opsTask || {}
  let artifactName = String(ctx.artifactName || fileNameOf(ctx.artifactPath) || fileNameOf(ctx.deploymentArtifact) || '').trim()
  const deploymentJar = String(ctx.deploymentJar || ops.deploymentJar || '').trim()
  if (!artifactName) {
    artifactName = fileNameOf(deploymentJar)
  }
  if (!artifactName) {
    for (const step of [...(timeline || [])].reverse()) {
      const match = String(step.summary || '').match(/([A-Za-z0-9][A-Za-z0-9._-]*\.(?:jar|war))\b/i)
      if (match && !/-sources\.|-javadoc\./i.test(match[1])) {
        artifactName = match[1]
        break
      }
    }
  }
  if (!artifactName && ops.applicationCode) {
    artifactName = `${ops.applicationCode}.jar`
  }
  const deploymentTargets = ctx.deploymentTargets || ops.deploymentTargets
  if (!artifactName && !deploymentJar && !deploymentTargets) return null
  return {
    artifactName,
    artifactSize: ctx.artifactSize,
    artifactPath: ctx.artifactPath,
    autoDeploy: ctx.autoDeploy === true || ctx.autoDeploy === 'true',
    deploymentJar,
    deploymentPath: ctx.deploymentPath || ops.deploymentPath,
    deploymentArtifact: ctx.deploymentArtifact,
    deploymentTargets
  }
}

const simplifiedTimeline = computed(() => {
  const result = []
  for (const step of selected.value?.timeline || []) {
    const displayName = timelineStageName(step.step)
    const previous = result.at(-1)
    const next = { ...step, displayName }
    if (previous?.displayName === displayName && previous.success !== false && step.success !== false) {
      // 同阶段默认保留最新进度，但审阅、制品等后续仍需操作的节点不能被过程记录覆盖。
      if (timelineStepPriority(step) >= timelineStepPriority(previous)) {
        result[result.length - 1] = next
      }
    } else {
      result.push(next)
    }
  }
  const artifact = incidentArtifactContext(selected.value?.context, selected.value?.timeline, selectedOps.value)
  if (!artifact) return result
  return result.map(step => {
    // 制品卡只挂到部署阶段，避免验证构建重复展示 JAR
    if (step.displayName !== '部署执行') return step
    return { ...step, context: { ...(step.context || {}), ...artifact } }
  })
})

const repairReviewTimelineStep = computed(() => [...simplifiedTimeline.value]
  .reverse()
  .find(step => step.displayName === '修复处理' && step.success !== false))

function isRepairReviewTimelineStep(step) {
  return repairReviewTimelineStep.value === step
}

function shouldShowTimelineSummary(step) {
  const name = formatStepName(step.step)
  if (step.displayName === '验证构建') return false
  if (step.displayName === '部署执行' && /JAR|WAR|\.jar|\.war|路径：/i.test(String(step.summary || ''))) {
    // 部署阶段的制品信息由制品卡展示，避免摘要再重复一坨路径
    return step.success === false
  }
  return step.success === false
    || /最终结论|执行异常|人工驳回|修复确认|合并/.test(name)
}
const menuCodes = computed(() => new Set((props.user.menus || []).map(item => item.code)))
function canAccessMenu(code) {
  if (code === 'HOME') return true
  const permissionCode = VIRTUAL_MENU_ITEMS[code]?.permissionCode || code
  return props.user.role === 'ADMIN' || menuCodes.value.has(permissionCode)
}
const showDashboard = computed(() => menuCodes.value.has('OPS_DASHBOARD') || props.user.role === 'ADMIN')
const menuTree = computed(() => {
  const nodes = (props.user.menus || [])
    .filter(item => !HIDDEN_MENU_CODES.has(item.code))
    .map(item => ({ ...item, children: [] }))
  const byId = new Map(nodes.map(item => [item.id, item]))
  const roots = []
  nodes.forEach(item => {
    if (item.parentId && byId.has(item.parentId)) byId.get(item.parentId).children.push(item)
    else roots.push(item)
  })
  return roots
})
const currentMenuItem = computed(() => VIRTUAL_MENU_ITEMS[currentMenu.value]
  || (props.user.menus || []).find(item => item.code === currentMenu.value))
const openedMenuItems = computed(() => openedMenus.value
  .map(code => VIRTUAL_MENU_ITEMS[code] || (props.user.menus || []).find(item => item.code === code))
  .filter(item => item && !HIDDEN_MENU_CODES.has(item.code)))

function defaultMenuCode() {
  return 'HOME'
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(SIDEBAR_KEY, sidebarCollapsed.value ? '1' : '0')
}

function detectStatusChanges(tasks) {
  const terminalStatuses = new Set(['COMPLETED', 'FAILED'])
  const activeIds = new Set()
  tasks.forEach(task => {
    activeIds.add(task.id)
    const prevStatus = previousTaskStatuses.get(task.id)
    const currentStatus = task.status
    if (prevStatus && prevStatus !== currentStatus && terminalStatuses.has(currentStatus)) {
      createOpsTaskNotification(task, currentStatus)
    }
    previousTaskStatuses.set(task.id, currentStatus)
  })
  for (const [id] of previousTaskStatuses) {
    if (!activeIds.has(id)) previousTaskStatuses.delete(id)
  }
}

function openTaskById(taskId) {
  if (!taskId) return
  const task = opsTasks.value.find(t => t.id === taskId)
  if (task) {
    openDetail(task)
    return
  }
  getOpsTask(taskId).then(task => {
    if (task) {
      opsTasks.value = [task, ...opsTasks.value.filter(t => t.id !== taskId)]
      openDetail(task)
    }
  }).catch(() => {})
}

async function refresh() {
  loading.value = true
  try {
    const [taskList, hostList] = await Promise.all([
      listOpsTasks(),
      listResources('hosts').catch(() => [])
    ])
    detectStatusChanges(taskList)
    opsTasks.value = taskList
    hosts.value = hostList
    if (selected.value?.id) selected.value = await getIncident(selected.value.id)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function stopDetailPolling() {
  if (detailPollTimer) clearTimeout(detailPollTimer)
  detailPollTimer = null
}

function scheduleDetailPolling() {
  stopDetailPolling()
  if (!detailVisible.value || !detailIsActive.value) return
  if (document.visibilityState === 'hidden') return
  detailPollTimer = setTimeout(async () => {
    if (!detailVisible.value || !selectedOps.value?.id) return
    if (document.visibilityState === 'hidden' || currentMenu.value !== 'OPS_DASHBOARD') {
      scheduleDetailPolling()
      return
    }
    detailLiveRefreshing.value = true
    try {
      await loadDetailData(selectedOps.value.id, true)
    } catch {
      // 静默轮询失败不打断用户查看，下一次刷新仍会继续。
    } finally {
      detailLiveRefreshing.value = false
      scheduleDetailPolling()
    }
  }, DETAIL_POLL_INTERVAL_MS)
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible' && detailVisible.value && detailIsActive.value) {
    scheduleDetailPolling()
  } else if (document.visibilityState === 'hidden') {
    stopDetailPolling()
  }
}

async function loadDetailData(taskId, polling = false) {
  const token = ++detailRequestToken
  const task = await getOpsTask(taskId, polling)
  if (token !== detailRequestToken || selectedOps.value?.id !== taskId) return
  const prevStatus = previousTaskStatuses.get(task.id)
  const terminalStatuses = new Set(['COMPLETED', 'FAILED'])
  if (prevStatus && prevStatus !== task.status && terminalStatuses.has(task.status)) {
    createOpsTaskNotification(task, task.status)
  }
  previousTaskStatuses.set(task.id, task.status)
  selectedOps.value = task
  const taskIndex = opsTasks.value.findIndex(item => item.id === task.id)
  if (taskIndex >= 0) opsTasks.value.splice(taskIndex, 1, task)
  // 详情接口已经携带当前故障，避免再按 ID 重复查询一次大字段。
  selected.value = task.incident || null
  if (selected.value && !detailIncidents.value.some(item => item.id === selected.value.id)) {
    detailIncidents.value = [selected.value, ...detailIncidents.value]
  }
  // 轮询只刷新任务及当前故障，故障历史由独立请求加载。
  if (!polling) loadDetailSecondaryData(task, ++detailSecondaryRequestToken)
  if (!polling && !detailIsActive.value) stopDetailPolling()
}

async function loadDetailSecondaryData(task, token) {
  detailSecondaryLoading.value = true
  try {
    const approvalsPromise = task.incidentId
      ? listApprovals(task.incidentId, true).catch(() => [])
      : Promise.resolve([])
    // 使用独立令牌，时间线轮询不会再把耗时较长的故障历史结果判定为过期。
    const incidentsPromise = listIncidents(task.id, true).catch(() => [])
    const [approvals, incidents] = await Promise.all([approvalsPromise, incidentsPromise])
    if (token !== detailSecondaryRequestToken || selectedOps.value?.id !== task.id) return
    detailApprovals.value = approvals
    detailIncidents.value = incidents
      .filter(item => String(item.opsTaskId || item.context?.opsTaskId || '') === String(task.id))
      .sort((left, right) => new Date(right.createdAt || 0) - new Date(left.createdAt || 0))
    if (selected.value && !detailIncidents.value.some(item => item.id === selected.value.id)) {
      detailIncidents.value.unshift(selected.value)
    }
    const maxPage = Math.max(1, Math.ceil(detailIncidents.value.length / incidentPageSize.value))
    if (incidentPage.value > maxPage) incidentPage.value = maxPage
  } finally {
    if (token === detailSecondaryRequestToken) detailSecondaryLoading.value = false
  }
}

async function openDetail(row) {
  stopDetailPolling()
  selectedOps.value = row
  selected.value = null
  detailApprovals.value = []
  detailIncidents.value = []
  detailSecondaryRequestToken++
  detailSecondaryLoading.value = false
  incidentPage.value = 1
  detailVisible.value = true
  detailLoading.value = true
  try {
    await loadDetailData(row.id)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    detailLoading.value = false
    scheduleDetailPolling()
  }
}

async function openIncidentFromList(incident) {
  selected.value = await getIncident(incident.id)
  const approvals = await listApprovals(incident.id, true).catch(() => [])
  detailApprovals.value = approvals
}

async function refreshDetail(showMessage = true) {
  if (!selectedOps.value?.id) return
  detailRefreshing.value = true
  try {
    await loadDetailData(selectedOps.value.id)
    scheduleDetailPolling()
    if (showMessage) ElMessage.success('任务状态已刷新')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    detailRefreshing.value = false
  }
}

async function enableFromDetail() {
  if (!selectedOps.value?.id) return
  detailEnabling.value = true
  try {
    await setOpsTaskEnabled(selectedOps.value.id, true)
    await refreshDetail(false)
    ElMessage.success('运维任务已启用，定时拉取和异常分析将自动执行')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    detailEnabling.value = false
  }
}

async function decideToolApproval(item, approved) {
  decidingApprovalId.value = item.id
  try {
    if (approved) await approveRequest(item.id, '批准执行')
    else await rejectRequest(item.id, '拒绝执行')
    ElMessage.success(approved ? '已批准，Agent 将继续执行' : '已拒绝，Agent 将调整处理方案')
    const incidentId = selectedOps.value?.incidentId
    if (incidentId) {
      const [incident, approvals] = await Promise.all([
        getIncident(incidentId),
        listApprovals(incidentId, true).catch(() => [])
      ])
      selected.value = incident
      detailApprovals.value = approvals
    }
    await refresh()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    decidingApprovalId.value = ''
  }
}

async function reviewRepair(accept) {
  if (!selected.value?.id) return
  const action = accept ? '接受' : '撤销'
  try {
    await ElMessageBox.confirm(
      accept ? '接受后将保留当前运维分支中的修复代码。' : `撤销后将恢复到本次 ${activeRepairEngineLabel.value} 执行前的工作区内容。`,
      `${action} ${activeRepairStepLabel.value}`,
      { confirmButtonText: action, cancelButtonText: '取消', type: accept ? 'success' : 'warning' }
    )
    repairReviewing.value = true
    const result = accept
      ? await acceptIncidentRepair(selected.value.id)
      : await revertIncidentRepair(selected.value.id)
    selected.value = result.incident
    repairDiffVisible.value = false
    await refreshDetail(false)
    ElMessage.success(accept ? '已接受修复，后台正在继续代码验证' : result.message)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || String(error))
  } finally {
    repairReviewing.value = false
  }
}

async function downloadArtifact() {
  if (!selected.value?.id || !artifactReady.value) return
  artifactOperating.value = true
  try {
    await downloadIncidentArtifact(selected.value.id, selected.value.context.artifactName)
    ElMessage.success('JAR 下载已开始，请部署到目标环境进行人工验证')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    artifactOperating.value = false
  }
}

async function downloadDeploymentScripts() {
  if (!selected.value?.id || !selected.value?.context?.deploymentScripts) return
  artifactOperating.value = true
  try {
    await downloadIncidentDeploymentScripts(selected.value.id)
    ElMessage.success('一键部署、备份和启动脚本下载已开始')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    artifactOperating.value = false
  }
}

async function finishManualVerification(success) {
  if (!selected.value?.id || !manualDeploymentReady.value) return
  let note = ''
  try {
    if (success) {
      await ElMessageBox.confirm(
        '请确认已经下载并部署该 JAR，且目标环境验证通过。确认后本次运维任务将完结。',
        '确认部署验证通过',
        { confirmButtonText: '验证通过并完结', cancelButtonText: '取消', type: 'success' }
      )
    } else {
      const result = await ElMessageBox.prompt(
        '请填写人工部署验证未通过的原因。',
        '部署验证失败',
        { confirmButtonText: '记录失败', cancelButtonText: '取消', inputValidator: value => !!String(value || '').trim() || '请填写失败原因' }
      )
      note = result.value
    }
    artifactOperating.value = true
    const result = await completeIncidentArtifactVerification(selected.value.id, success, note)
    selected.value = result.incident
    await refreshDetail(false)
    ElMessage.success(result.message)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || String(error))
  } finally {
    artifactOperating.value = false
  }
}

async function decideBranchMerge(merge) {
  if (!selected.value?.id || !branchMergePending.value) return
  try {
    await ElMessageBox.confirm(
      merge
        ? '确认将已审阅修复合并到主分支？合并成功后会回收本地运维分支，但不会自动 push。'
        : '本次暂不合并。该运维任务后续会复用同一分支，不会重复创建。',
      merge ? '合并主分支' : '暂不合并',
      { confirmButtonText: merge ? '确认合并' : '暂不合并', cancelButtonText: '取消', type: merge ? 'warning' : 'info' }
    )
    artifactOperating.value = true
    const result = await decideIncidentBranchMerge(selected.value.id, merge)
    selected.value = result.incident
    await refreshDetail(false)
    ElMessage.success(result.message)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || String(error))
  } finally {
    artifactOperating.value = false
  }
}

function closeDetail() {
  stopDetailPolling()
  detailRequestToken++
  detailSecondaryRequestToken++
  detailSecondaryLoading.value = false
  detailVisible.value = false
}

function resetFilters() {
  Object.assign(filters, { keyword: '', status: '', priority: '' })
}

function resetCreateForm() {
  Object.assign(form, {
    title: '', description: '', priority: 'P2', hostId: '', workspacePath: '', frontendWorkspacePath: '',
    backendKind: 'MAVEN', clientKind: 'NONE', clientVerifyCommand: '', backendVerifyCommand: '',
    clientDeployPath: '', clientDistDir: 'dist', dockerComposeFile: 'docker-compose.yml', dockerImageName: '',
    repairEngine: 'auto',
    applicationCode: '', repositoryUrl: '', ownerName: '', autoDeploy: false, deploymentPath: '', deploymentPaths: [],
    deploymentJar: '', deploymentTargets: [],
    scheduleEnabled: true, pullIntervalMinutes: 30, logRangeMinutes: 30, logTemplate: 'JOURNAL_LOGS',
    logPath: '', logServiceName: '', autoAnalyze: true
  })
  deploymentCandidateInput.value = ''
  deploymentDetectionMessage.value = ''
  detectionCandidates.value = []
}

function onClientKindChange(kind) {
  if (kind === 'NONE') {
    form.frontendWorkspacePath = ''
    form.clientVerifyCommand = ''
  }
  formRef.value?.validateField('frontendWorkspacePath').catch(() => {})
}

async function openCreate() {
  editingOpsId.value = ''
  resetCreateForm()
  createVisible.value = true
  formRef.value?.clearValidate()
  try {
    hosts.value = await listResources('hosts')
  } catch (error) {
    ElMessage.error(`读取最新主机配置失败：${error.message}`)
  }
}

async function openEdit(row) {
  editingOpsId.value = row.id
  const targets = normalizeDeploymentTargets(row)
  Object.assign(form, {
    title: row.title || '',
    description: row.description || '',
    priority: row.priority || 'P2',
    hostId: row.hostId || '',
    workspacePath: row.workspacePath || '',
    frontendWorkspacePath: row.frontendWorkspacePath || '',
    backendKind: row.backendKind || 'MAVEN',
    clientKind: row.clientKind || (row.frontendWorkspacePath ? 'WEB_NPM' : 'NONE'),
    clientVerifyCommand: row.clientVerifyCommand || '',
    backendVerifyCommand: row.backendVerifyCommand || '',
    clientDeployPath: row.clientDeployPath || '',
    clientDistDir: row.clientDistDir || 'dist',
    dockerComposeFile: row.dockerComposeFile || 'docker-compose.yml',
    dockerImageName: row.dockerImageName || '',
    repairEngine: row.repairEngine || 'auto',
    applicationCode: row.applicationCode || '',
    repositoryUrl: row.repositoryUrl || '',
    ownerName: row.ownerName || '',
    autoDeploy: row.autoDeploy === true,
    deploymentPath: targets[0]?.path || row.deploymentPath || '',
    deploymentPaths: Array.isArray(row.deploymentPaths) && row.deploymentPaths.length
      ? [...row.deploymentPaths]
      : (row.deploymentPath ? [row.deploymentPath] : []),
    deploymentJar: targets[0]?.jar || row.deploymentJar || '',
    deploymentTargets: targets,
    scheduleEnabled: row.scheduleEnabled !== false,
    pullIntervalMinutes: Number(row.pullIntervalMinutes) || 30,
    logRangeMinutes: Number(row.logRangeMinutes) || 30,
    logTemplate: row.logTemplate || 'JOURNAL_LOGS',
    logPath: row.logPath || '',
    logServiceName: row.logServiceName || '',
    autoAnalyze: row.autoAnalyze !== false
  })
  deploymentCandidateInput.value = form.deploymentPath || ''
  detectionCandidates.value = []
  deploymentDetectionMessage.value = boundTargetsSummary()
  createVisible.value = true
  formRef.value?.clearValidate()
  try {
    hosts.value = await listResources('hosts')
  } catch (error) {
    ElMessage.error(`读取最新主机配置失败：${error.message}`)
  }
}

/** 将接口返回的部署目标规范为 {path, jar}[]。 */
function normalizeDeploymentTargets(row) {
  if (Array.isArray(row?.deploymentTargets) && row.deploymentTargets.length) {
    return row.deploymentTargets
      .filter(item => item && item.path && item.jar)
      .map(item => ({ path: item.path, jar: item.jar }))
  }
  if (row?.deploymentPath && row?.deploymentJar) {
    return [{ path: row.deploymentPath, jar: row.deploymentJar }]
  }
  return []
}

/** 同步主 path/jar 字段，兼容旧接口。 */
function syncPrimaryDeploymentTarget() {
  const first = form.deploymentTargets[0]
  form.deploymentPath = first?.path || ''
  form.deploymentJar = first?.jar || ''
  deploymentDetectionMessage.value = boundTargetsSummary()
}

function boundTargetsSummary() {
  if (!form.deploymentTargets.length) return ''
  if (form.deploymentTargets.length === 1) {
    const item = form.deploymentTargets[0]
    return `已绑定：${item.path} / ${item.jar}`
  }
  return `已绑定 ${form.deploymentTargets.length} 个部署目标`
}

function isDeploymentTargetBound(path, jar) {
  return form.deploymentTargets.some(item => item.path === path && item.jar === jar)
}

function isDeploymentPathBound(path) {
  return form.deploymentTargets.some(item => item.path === path)
}

/** 主机、应用编码或候选目录变化后清除旧识别结果，避免误部署。 */
function invalidateDeploymentDetection() {
  form.deploymentPath = ''
  form.deploymentJar = ''
  form.deploymentTargets = []
  detectionCandidates.value = []
  deploymentDetectionMessage.value = ''
  formRef.value?.clearValidate('deploymentPaths')
}

/** 返回阻塞路径识别的原因；空字符串表示可识别。 */
const deploymentDetectBlockedReason = computed(() => {
  if (!form.hostId) return '请先选择目标主机'
  if (!String(form.applicationCode || '').trim()) return '请先填写应用编码'
  if (!form.deploymentPaths.length) return '请先添加候选部署目录'
  return ''
})

/** 从候选 JAR 列表中挑选优先绑定项。 */
function preferredJar(jars, code) {
  const list = Array.isArray(jars) ? jars : []
  if (!list.length) return ''
  const app = String(code || '').trim()
  const standard = `current/${app}.jar`
  if (list.includes(standard)) return standard
  if (list.includes(`${app}.jar`)) return `${app}.jar`
  const lower = app.toLowerCase()
  return list.find(item => String(item).toLowerCase().includes(lower)) || list[0]
}

/** 切换绑定某候选目录下的 JAR；可多选，再次点击取消。 */
function toggleDeploymentTarget(path, jar) {
  const index = form.deploymentTargets.findIndex(item => item.path === path && item.jar === jar)
  if (index >= 0) {
    form.deploymentTargets.splice(index, 1)
    ElMessage.success(`已取消绑定：${path} → ${jar}`)
  } else {
    form.deploymentTargets.push({ path, jar })
    deploymentCandidateInput.value = path
    ElMessage.success(`已绑定部署目标：${path} → ${jar}`)
  }
  syncPrimaryDeploymentTarget()
  formRef.value?.validateField('deploymentPaths').catch(() => {})
}

function removeDeploymentTarget(path, jar) {
  form.deploymentTargets = form.deploymentTargets.filter(item => !(item.path === path && item.jar === jar))
  syncPrimaryDeploymentTarget()
  formRef.value?.validateField('deploymentPaths').catch(() => {})
}

/** 将服务器选择或手工输入的安全绝对路径加入候选列表。 */
function addDeploymentCandidate(path = deploymentCandidateInput.value) {
  const value = String(path || '').trim().replace(/\/+$/, '')
  if (!value || value === '/' || !/^\/[A-Za-z0-9/_.-]+$/.test(value) || value.includes('..')) {
    ElMessage.warning('请输入安全的 Linux 绝对路径，且不能选择根目录')
    return
  }
  if (!form.deploymentPaths.includes(value)) form.deploymentPaths.push(value)
  deploymentCandidateInput.value = value
  invalidateDeploymentDetection()
}

/** 移除候选目录并使原识别结果失效。 */
function removeDeploymentCandidate(path) {
  form.deploymentPaths = form.deploymentPaths.filter(item => item !== path)
  if (deploymentCandidateInput.value === path) deploymentCandidateInput.value = ''
  invalidateDeploymentDetection()
}

/** 连接目标主机，根据进程和目录证据识别部署路径；歧义时列出 JAR 供多选绑定。 */
async function runDeploymentPathDetection() {
  const blocked = deploymentDetectBlockedReason.value
  if (blocked) {
    ElMessage.warning(blocked)
    return
  }
  detectingDeploymentPath.value = true
  try {
    const result = await detectDeploymentPath({
      hostId: form.hostId,
      applicationCode: String(form.applicationCode || '').trim(),
      deploymentPaths: form.deploymentPaths
    })
    detectionCandidates.value = Array.isArray(result.candidates) ? result.candidates : []
    deploymentDetectionMessage.value = result.message || ''
    form.deploymentTargets = []
    if (result.detected && result.deploymentPath) {
      const winner = detectionCandidates.value.find(item => item.path === result.deploymentPath)
      const jar = preferredJar(winner?.jars, form.applicationCode)
      if (jar) {
        form.deploymentTargets = [{ path: result.deploymentPath, jar }]
        syncPrimaryDeploymentTarget()
        deploymentCandidateInput.value = result.deploymentPath
        ElMessage.success(`已识别并绑定：${result.deploymentPath} / ${jar}（可继续点选更多目标）`)
      } else {
        form.deploymentPath = result.deploymentPath
        form.deploymentJar = ''
        deploymentCandidateInput.value = result.deploymentPath
        ElMessage.success(`已识别实际部署路径：${result.deploymentPath}`)
      }
    } else {
      form.deploymentPath = ''
      form.deploymentJar = ''
      ElMessage.warning(result.message || '未能识别唯一部署路径，请在下方点选一个或多个路径与 JAR 绑定')
    }
    formRef.value?.validateField('deploymentPaths').catch(() => {})
  } catch (error) {
    form.deploymentPath = ''
    form.deploymentJar = ''
    form.deploymentTargets = []
    detectionCandidates.value = []
    deploymentDetectionMessage.value = ''
    ElMessage.error(error.message || '识别部署路径失败')
  } finally {
    detectingDeploymentPath.value = false
  }
}

async function submit() {
  if (submitting.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    const created = editingOpsId.value
      ? await updateOpsTask(editingOpsId.value, form)
      : await createOpsTask(form)
    createVisible.value = false
    const wasEditing = !!editingOpsId.value
    editingOpsId.value = ''
    resetCreateForm()
    await refresh()
    ElMessage.success(wasEditing
      ? '运维任务已更新，并已按新配置重新启动'
      : '运维任务已创建，可在列表中启用以启动分析')
    if (created?.id) {
      const row = opsTasks.value.find(item => item.id === created.id) || created
      await openDetail(row)
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}

async function toggleEnabled(row, enabled) {
  try {
    const updated = await setOpsTaskEnabled(row.id, enabled)
    Object.assign(row, updated)
    await refresh()
    ElMessage.success(enabled ? (updated.incidentId ? '运维任务已启用，分析已启动' : '运维任务已启用') : '运维任务已停用')
  } catch (error) {
    row.enabled = !enabled
    ElMessage.error(error.message)
  }
}

async function removeOps(row) {
  try {
    await ElMessageBox.confirm(`确认删除运维任务「${row.title}」？删除后列表不再显示，关联故障记录仍保留。`, '删除运维任务', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteOpsTask(row.id)
    if (selectedOps.value?.id === row.id) {
      detailVisible.value = false
      selectedOps.value = null
      selected.value = null
    }
    await refresh()
    ElMessage.success('运维任务已删除')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function preferredRootPath(rootList) {
  const roots = rootList || []
  // Windows 下优先落到非系统盘，避免一打开就扫 C:\
  const preferred = roots.find(item => /^[D-Z]:\\?$/i.test(String(item.path || '').trim()))
  return preferred?.path || roots[0]?.path || ''
}

async function openPicker(target = 'backend') {
  if (target === 'frontend' && (!form.clientKind || form.clientKind === 'NONE')) {
    ElMessage.warning('请先选择端侧工程类型')
    return
  }
  pickerTarget.value = target === 'frontend' ? 'frontend' : 'backend'
  pickerVisible.value = true
  browsing.value = true
  try {
    roots.value = await listFilesystemRoots()
    const start = (pickerTarget.value === 'frontend'
      ? (form.frontendWorkspacePath || form.workspacePath)
      : form.workspacePath) || preferredRootPath(roots.value)
    await browse(start)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    browsing.value = false
  }
}

async function browse(path) {
  browsing.value = true
  try {
    const result = await listFilesystem(path)
    currentPath.value = result.path
    parentPath.value = result.parentPath
    currentHasPom.value = !!result.hasPom
    currentHasPackageJson.value = !!result.hasPackageJson
    currentHasMiniProgram.value = !!result.hasMiniProgram
    currentHasBackend.value = !!result[backendMarkerKey.value]
    entries.value = result.children || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    browsing.value = false
  }
}

function chooseCurrent() {
  if (pickerTarget.value === 'frontend') {
    if (pickerNeedsMiniProgram.value) {
      if (!currentHasMiniProgram.value) {
        ElMessage.warning('请选择含 project.config.json / app.json 的小程序目录，或其子目录中含工程的父目录')
        return
      }
    } else if (!currentHasPackageJson.value) {
      ElMessage.warning('请选择含 package.json 的目录，或其子目录中含端侧工程的父目录')
      return
    }
    form.frontendWorkspacePath = currentPath.value
  } else {
    if (!currentHasBackend.value) {
      ElMessage.warning(`请选择${backendWorkspaceHint.value}的目录，或其子目录中含对应工程的父目录`)
      return
    }
    form.workspacePath = currentPath.value
  }
  pickerVisible.value = false
}

function chooseEntry(entry) {
  const selectable = entrySelectable(entry)
  if (selectable) {
    if (pickerTarget.value === 'frontend') form.frontendWorkspacePath = entry.path
    else form.workspacePath = entry.path
    pickerVisible.value = false
    return
  }
  browse(entry.path)
}

function entrySelectable(row) {
  if (pickerTarget.value !== 'frontend') return !!row[backendMarkerKey.value]
  return pickerNeedsMiniProgram.value ? !!row.hasMiniProgram : !!row.hasPackageJson
}

async function run() {
  if (!selectedOps.value?.id) return
  await ElMessageBox.confirm(
    '将结束上一轮未完成的分析，拉取最新日志并创建一轮新的故障分析。历史时间线会保留，是否继续？',
    '重新启动分析',
    { confirmButtonText: '重新启动', cancelButtonText: '取消', type: 'warning' }
  )
  await restartOpsTaskAnalysis(selectedOps.value.id)
  await refresh()
  await refreshDetail()
  ElMessage.success('上一轮分析已结束，正在基于最新日志重新分析')
}

async function approve() {
  const { value } = await ElMessageBox.prompt('请输入审批意见', '审批部署', { inputPlaceholder: '例如：验证通过，同意部署' })
  await approveIncident(selected.value.id, value)
  await refresh()
  ElMessage.success('已批准部署')
}

async function reject() {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回方案', { inputPlaceholder: '请输入原因' })
  await rejectIncident(selected.value.id, value)
  await refresh()
  ElMessage.success('方案已驳回')
}
function handleMenuSelect(code) {
  if (HIDDEN_MENU_CODES.has(code)) return
  if (contentMain.value) menuScrollPositions.set(currentMenu.value, contentMain.value.scrollTop)
  currentMenu.value = code
  if (code !== 'HOME' && !openedMenus.value.includes(code)) openedMenus.value.push(code)
  mobileNav.value = false
  nextTick(() => {
    if (contentMain.value) contentMain.value.scrollTop = menuScrollPositions.get(code) || 0
  })
}
function scrollPageTabs(direction) {
  pageTabs.value?.scrollBy({ left: direction * 260, behavior: 'smooth' })
}
function handlePageTabsWheel(event) {
  if (!pageTabs.value || Math.abs(event.deltaY) <= Math.abs(event.deltaX)) return
  if (pageTabs.value.scrollWidth <= pageTabs.value.clientWidth) return
  event.preventDefault()
  pageTabs.value.scrollLeft += event.deltaY
}
function handleGuideNavigate(code) {
  if (!canAccessMenu(code)) {
    ElMessage.warning('当前账号暂无该功能权限，请联系管理员授权')
    return
  }
  handleMenuSelect(code)
}
function closeMenu(code) {
  hideTabContextMenu()
  const index = openedMenus.value.indexOf(code)
  if (index < 0) return
  if (currentMenu.value === code) {
    currentMenu.value = openedMenus.value[index - 1] || openedMenus.value[index + 1] || defaultMenuCode()
  }
  openedMenus.value.splice(index, 1)
}
function openTabContextMenu(event, code) {
  tabContextMenu.code = code
  tabContextMenu.x = Math.max(8, Math.min(event.clientX, window.innerWidth - 178))
  tabContextMenu.y = Math.max(8, Math.min(event.clientY, window.innerHeight - 174))
  tabContextMenu.visible = true
}
function hideTabContextMenu() {
  tabContextMenu.visible = false
}
function closeOtherMenus() {
  const code = tabContextMenu.code
  openedMenus.value = openedMenus.value.includes(code) ? [code] : []
  currentMenu.value = code || defaultMenuCode()
  hideTabContextMenu()
}
function closeRightMenus() {
  const index = openedMenus.value.indexOf(tabContextMenu.code)
  if (index < 0 || index >= openedMenus.value.length - 1) return
  const removed = openedMenus.value.slice(index + 1)
  openedMenus.value.splice(index + 1)
  if (removed.includes(currentMenu.value)) currentMenu.value = tabContextMenu.code
  hideTabContextMenu()
}
function closeAllMenus() {
  openedMenus.value = []
  currentMenu.value = defaultMenuCode()
  if (currentMenu.value && currentMenu.value !== 'HOME') {
    openedMenus.value.push(currentMenu.value)
  }
  hideTabContextMenu()
}
function resetTabDrag() {
  draggingTabCode.value = ''
  dragOverTabCode.value = ''
}
function onTabDragStart(event, code) {
  if (event.target?.closest?.('.page-tab-close')) {
    event.preventDefault()
    return
  }
  draggingTabCode.value = code
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', code)
  hideTabContextMenu()
}
function onTabDragOver(event, code) {
  if (!draggingTabCode.value || draggingTabCode.value === code) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
  dragOverTabCode.value = code
}
function onTabDrop(event, targetCode) {
  event.preventDefault()
  const sourceCode = draggingTabCode.value || event.dataTransfer.getData('text/plain')
  if (!sourceCode || sourceCode === targetCode) {
    resetTabDrag()
    return
  }
  const from = openedMenus.value.indexOf(sourceCode)
  let to = openedMenus.value.indexOf(targetCode)
  if (from < 0 || to < 0) {
    resetTabDrag()
    return
  }
  const rect = event.currentTarget.getBoundingClientRect()
  if (event.clientX > rect.left + rect.width / 2) to += 1
  const next = [...openedMenus.value]
  const [moved] = next.splice(from, 1)
  if (from < to) to -= 1
  next.splice(to, 0, moved)
  openedMenus.value = next
  resetTabDrag()
}
function onTabDragEnd() {
  resetTabDrag()
}
function userCommand(command) {
  if (command === 'password') {
    Object.assign(passwordForm, { currentPassword: '', newPassword: '', confirmPassword: '' })
    passwordFormRef.value?.clearValidate()
    passwordVisible.value = true
  } else if (command === 'logout') {
    emit('logout')
  }
}
async function submitPassword() {
  await passwordFormRef.value.validate()
  passwordSubmitting.value = true
  try {
    const result = await changePassword({
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword
    })
    passwordVisible.value = false
    ElMessage.success(result.message || '密码修改成功，请重新登录')
    emit('logout')
  } catch (error) {
    ElMessage.error(error.message || String(error))
  } finally {
    passwordSubmitting.value = false
  }
}
function refreshSession() { emit('session-refresh') }
watch(() => form.hostId, () => {
  if (form.logPath && !selectedHostLogPaths.value.includes(form.logPath)) {
    form.logPath = ''
  }
})

watch(() => form.logTemplate, (template) => {
  if (template !== 'APP_LOG_FILE') form.logPath = ''
})

watch(menuCodes, () => {
  openedMenus.value = openedMenus.value.filter(code => canAccessMenu(code) && !HIDDEN_MENU_CODES.has(code))
  if (!canAccessMenu(currentMenu.value)
      || HIDDEN_MENU_CODES.has(currentMenu.value)) {
    currentMenu.value = defaultMenuCode()
  }
})
watch([currentMenu, openedMenus], () => {
  localStorage.setItem(CURRENT_MENU_KEY, currentMenu.value)
  localStorage.setItem(MENU_STATE_KEY, JSON.stringify(openedMenus.value))
  nextTick(() => {
    pageTabs.value?.querySelector('.active')?.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest',
      inline: 'nearest'
    })
  })
}, { deep: true })
watch(filters, (val) => {
  sessionStorage.setItem(FILTERS_STORAGE_KEY, JSON.stringify({ keyword: val.keyword, status: val.status, priority: val.priority }))
}, { deep: true })
watch([page, pageSize], () => {
  sessionStorage.setItem(PAGINATION_STORAGE_KEY, JSON.stringify({ page: page.value, pageSize: pageSize.value }))
})
onMounted(async () => {
  let restoredMenus = []
  try {
    restoredMenus = JSON.parse(localStorage.getItem(MENU_STATE_KEY) || '[]')
  } catch { /* 忽略损坏的本地页签状态 */ }
  openedMenus.value = Array.isArray(restoredMenus)
    ? restoredMenus.filter(code => canAccessMenu(code) && !HIDDEN_MENU_CODES.has(code))
    : []
  const restoredCurrent = localStorage.getItem(CURRENT_MENU_KEY)
  currentMenu.value = restoredCurrent
    && canAccessMenu(restoredCurrent)
    && !HIDDEN_MENU_CODES.has(restoredCurrent)
    ? restoredCurrent
    : defaultMenuCode()
  if (currentMenu.value !== 'HOME' && !openedMenus.value.includes(currentMenu.value)) {
    openedMenus.value.push(currentMenu.value)
  }
  try {
    const savedFilters = JSON.parse(sessionStorage.getItem(FILTERS_STORAGE_KEY) || '{}')
    if (savedFilters.keyword !== undefined) filters.keyword = savedFilters.keyword
    if (savedFilters.status !== undefined) filters.status = savedFilters.status
    if (savedFilters.priority !== undefined) filters.priority = savedFilters.priority
  } catch { /* 忽略损坏的筛选状态 */ }
  try {
    const savedPagination = JSON.parse(sessionStorage.getItem(PAGINATION_STORAGE_KEY) || '{}')
    if (savedPagination.page) page.value = savedPagination.page
    if (savedPagination.pageSize) pageSize.value = savedPagination.pageSize
  } catch { /* 忽略损坏的分页状态 */ }
  document.addEventListener('visibilitychange', handleVisibilityChange)
  try {
    priorityOptions.value = await listDictOptions('OPS_PRIORITY')
  } catch { /* 保留本地兜底 */ }
  if (showDashboard.value) await refresh()
  try { await getHealth(); health.value = '服务正常' } catch { health.value = '服务异常' }
})
onUnmounted(() => {
  stopDetailPolling()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<template>
  <el-container class="app-shell" @click="hideTabContextMenu" @contextmenu="hideTabContextMenu">
    <el-header class="app-header">
      <div class="header-brand">
        <el-tooltip content="打开导航" placement="bottom"><button class="mobile-menu-button" aria-label="打开导航" @click="mobileNav = true"><el-icon><MenuIcon /></el-icon></button></el-tooltip>
        <div><div class="eyebrow">SERVER OPERATIONS</div><h1>智维超级员工</h1></div>
      </div>
      <div class="header-actions">
        <el-tag :type="health === '服务正常' ? 'success' : 'warning'" effect="dark"><el-icon><Monitor /></el-icon>{{ health }}</el-tag>
        <NotificationBell @open-task="openTaskById" />
        <el-dropdown trigger="click" @command="userCommand">
          <button class="user-trigger" :aria-label="user.displayName"><el-avatar :size="30"><el-icon><UserFilled /></el-icon></el-avatar><span>{{ user.displayName }}</span></button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <nav class="page-tabs-shell" aria-label="已打开页面">
      <button class="tab-scroll-control" aria-label="向左滚动导航" title="向左滚动" @click="scrollPageTabs(-1)">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div ref="pageTabs" class="page-tabs" @wheel="handlePageTabsWheel">
        <button class="home-tab" :class="{ active: currentMenu === 'HOME' }" aria-label="首页" title="首页" @click="handleMenuSelect('HOME')"><el-icon><House /></el-icon></button>
        <div
          v-for="item in openedMenuItems"
          :key="item.code"
          class="page-tab"
          :class="{
            active: currentMenu === item.code,
            'is-dragging': draggingTabCode === item.code,
            'is-drag-over': dragOverTabCode === item.code && draggingTabCode !== item.code
          }"
          draggable="true"
          title="拖动可调整顺序"
          @dragstart="onTabDragStart($event, item.code)"
          @dragover="onTabDragOver($event, item.code)"
          @drop="onTabDrop($event, item.code)"
          @dragend="onTabDragEnd"
          @contextmenu.stop.prevent="openTabContextMenu($event, item.code)"
        >
          <button class="page-tab-label" :title="item.name" @click="handleMenuSelect(item.code)">{{ item.name }}</button>
          <button class="page-tab-close" draggable="false" :aria-label="`关闭${item.name}`" :title="`关闭${item.name}`" @click.stop="closeMenu(item.code)"><el-icon><Close /></el-icon></button>
        </div>
      </div>
      <button class="tab-scroll-control" aria-label="向右滚动导航" title="向右滚动" @click="scrollPageTabs(1)">
        <el-icon><ArrowRight /></el-icon>
      </button>
    </nav>

    <teleport to="body">
      <div
        v-if="tabContextMenu.visible"
        class="tab-context-menu"
        :style="{ left: `${tabContextMenu.x}px`, top: `${tabContextMenu.y}px` }"
        role="menu"
        @click.stop
        @contextmenu.prevent
      >
        <button type="button" role="menuitem" @click="closeMenu(tabContextMenu.code)">关闭当前</button>
        <button type="button" role="menuitem" :disabled="openedMenus.length <= 1" @click="closeOtherMenus">关闭其他</button>
        <button
          type="button"
          role="menuitem"
          :disabled="openedMenus.indexOf(tabContextMenu.code) >= openedMenus.length - 1"
          @click="closeRightMenus"
        >关闭右侧</button>
        <div class="tab-context-divider" />
        <button type="button" role="menuitem" @click="closeAllMenus">关闭全部</button>
      </div>
    </teleport>

    <el-container class="workspace-shell">
      <el-aside :width="sidebarCollapsed ? '64px' : '220px'" :class="['app-sidebar', { 'is-collapsed': sidebarCollapsed }]">
        <div class="sidebar-toolbar">
          <span v-show="!sidebarCollapsed" class="sidebar-caption">功能导航</span>
          <el-tooltip :content="sidebarCollapsed ? '展开菜单' : '收起菜单'" placement="right">
            <button type="button" class="sidebar-collapse-btn" :aria-label="sidebarCollapsed ? '展开菜单' : '收起菜单'" @click="toggleSidebar">
              <el-icon><DArrowRight v-if="sidebarCollapsed" /><DArrowLeft v-else /></el-icon>
            </button>
          </el-tooltip>
        </div>
        <el-menu
          :default-active="currentMenu"
          :collapse="sidebarCollapsed"
          :collapse-transition="false"
          class="sidebar-menu"
          @select="handleMenuSelect"
        >
          <SidebarMenuItem v-for="item in menuTree" :key="item.id" :item="item" />
        </el-menu>
      </el-aside>

      <el-main ref="contentMain" class="content-main">
      <OperationsGuideHome v-show="currentMenu === 'HOME'" :user="user" @navigate="handleGuideNavigate" />
      <div v-show="currentMenu === 'OPS_DASHBOARD'" class="management-page dashboard-page page-fixed-layout">
        <div class="page-fixed-overview">
        <div class="page-heading">
          <div>
            <span class="page-kicker">OPS WORKBENCH</span>
            <h2>运维工作台</h2>
            <p>查询与跟踪运维任务。创建后可在列表中启用；启用后派生故障任务并由 Agent 分析修复。代码工作区在任务中配置。</p>
          </div>
          <div class="heading-actions">
            <el-button :icon="Refresh" @click="refresh">刷新</el-button>
            <el-button type="primary" :icon="Plus" @click="openCreate">新增运维任务</el-button>
          </div>
        </div>

        <div class="agent-metrics">
          <div><span>运维任务</span><strong class="metric-primary">{{ stats.total }}</strong></div>
          <div><span>分析中</span><strong>{{ stats.analyzing }}</strong></div>
          <div><span>待审批</span><strong class="metric-warning">{{ stats.pending }}</strong></div>
          <div><span>已完成</span><strong class="metric-success">{{ stats.completed }}</strong></div>
        </div>
        </div>

        <div class="management-section page-fixed-body">
          <el-form inline class="query-form">
            <el-form-item label="关键词">
              <el-input v-model="filters.keyword" clearable :prefix-icon="Search" placeholder="任务标题、主机或说明" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="filters.status" clearable placeholder="全部状态">
                <el-option v-for="(label, value) in opsStatusLabels" :key="value" :label="label" :value="value" />
              </el-select>
            </el-form-item>
            <el-form-item label="优先级">
              <el-select v-model="filters.priority" clearable placeholder="全部优先级">
                <el-option v-for="item in priorityOptions" :key="optionValue(item)" :label="item.label" :value="optionValue(item)" />
              </el-select>
            </el-form-item>
            <el-form-item class="query-actions">
              <el-button type="primary" :icon="Search">查询</el-button>
              <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
            </el-form-item>
          </el-form>

          <div class="table-heading">
            <div><h3>运维任务列表</h3><span>共 {{ total }} 条记录</span></div>
          </div>
          <div class="page-table-scroll">
          <div v-if="showSkeleton" class="table-skeleton-wrapper">
            <el-skeleton :rows="SKELETON_ROWS" animated>
              <template #default>
                <div v-for="i in SKELETON_ROWS" :key="i" class="skeleton-row">
                  <div class="skeleton-cell skeleton-cell--title">
                    <div class="skeleton-line skeleton-line--lg"></div>
                    <div class="skeleton-line skeleton-line--sm"></div>
                  </div>
                  <div class="skeleton-cell"><div class="skeleton-line"></div></div>
                  <div class="skeleton-cell"><div class="skeleton-line"></div></div>
                  <div class="skeleton-cell"><div class="skeleton-line"></div></div>
                  <div class="skeleton-cell skeleton-cell--center"><div class="skeleton-chip"></div></div>
                  <div class="skeleton-cell skeleton-cell--center"><div class="skeleton-chip"></div></div>
                  <div class="skeleton-cell skeleton-cell--center"><div class="skeleton-line"></div></div>
                  <div class="skeleton-cell skeleton-cell--center"><div class="skeleton-chip"></div></div>
                  <div class="skeleton-cell skeleton-cell--center"><div class="skeleton-chip"></div></div>
                  <div class="skeleton-cell skeleton-cell--center"><div class="skeleton-line skeleton-line--xs"></div></div>
                  <div class="skeleton-cell skeleton-cell--right"><div class="skeleton-line skeleton-line--sm"></div></div>
                  <div class="skeleton-cell skeleton-cell--center"><div class="skeleton-line skeleton-line--sm"></div></div>
                </div>
              </template>
            </el-skeleton>
          </div>
          <el-table v-else :loading="showTableLoading" :data="paged" border stripe row-key="id" height="100%" empty-text="暂无运维任务" @row-click="openDetail">
            <el-table-column label="运维任务" min-width="220">
              <template #default="{ row }">
                <div class="agent-identity">
                  <strong>{{ row.title }} <el-tag v-if="row.applicationCode" size="small" effect="plain">{{ row.applicationCode }}</el-tag></strong>
                  <span>{{ row.description || '暂无说明' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="目标主机" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.hostName || row.hostId || '—' }}</template>
            </el-table-column>
            <el-table-column v-if="!isMobile" label="后端" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ backendKindLabel(row.backendKind) }}{{ row.workspacePath ? ` · ${row.workspacePath}` : '' }}
              </template>
            </el-table-column>
            <el-table-column v-if="!isMobile" label="端侧工程" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.clientKind && row.clientKind !== 'NONE'">
                  {{ clientKindLabel(row.clientKind) }}{{ row.frontendWorkspacePath ? ` · ${row.frontendWorkspacePath}` : '' }}
                </span>
                <span v-else>{{ row.frontendWorkspacePath || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!isMobile" label="修复引擎" width="130" align="center">
              <template #default="{ row }">{{ repairEngineLabel(row.repairEngine) }}</template>
            </el-table-column>
            <el-table-column v-if="!isMobile" label="补丁部署" width="120" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.autoDeploy" size="small" type="success">自动部署</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">生成脚本</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="!isMobile" label="定时拉日志" width="210" align="center">
              <template #default="{ row }">
                <span v-if="row.scheduleEnabled">{{ formatPullInterval(row.pullIntervalMinutes) }} / {{ formatPullRange(row.logRangeMinutes) }}</span>
                <span v-else class="muted-text">关闭</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!isMobile" label="优先级" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="priorityType(row.priority)" effect="plain">{{ row.priority }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="fuseOpsRowStatus(row).type">
                  {{ fuseOpsRowStatus(row).text }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="!isMobile" label="启用" width="88" align="center">
              <template #default="{ row }">
                <span @click.stop>
                  <el-switch :model-value="!!row.enabled" @change="toggleEnabled(row, $event)" />
                </span>
              </template>
            </el-table-column>
            <el-table-column v-if="!isMobile" label="创建时间" width="150" align="right">
              <template #default="{ row }"><span class="time-cell">{{ formatTime(row.createdAt) }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="openDetail(row)">详情</el-button>
                <el-button link type="primary" @click.stop="openEdit(row)">编辑</el-button>
                <el-button link type="danger" @click.stop="removeOps(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          </div>
          <div class="table-pagination">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="pageSize"
              background
              :layout="isMobile ? 'prev, pager, next, total' : 'total, sizes, prev, pager, next'"
              :page-sizes="[20, 50, 100]"
              :total="total"
              :small="isMobile"
              @size-change="onSizeChange"
            />
          </div>
        </div>
      </div>
      <div v-if="openedMenus.includes('SYS_USER')" v-show="currentMenu === 'SYS_USER'" class="cached-menu-page"><UserManagementDialog /></div>
      <div v-if="openedMenus.includes('SYS_ROLE')" v-show="currentMenu === 'SYS_ROLE'" class="cached-menu-page"><RoleManagementDialog @changed="refreshSession" /></div>
      <div v-if="openedMenus.includes('SYS_MENU')" v-show="currentMenu === 'SYS_MENU'" class="cached-menu-page"><MenuManagementDialog @changed="refreshSession" /></div>
      <div v-if="openedMenus.includes('AGENT_MANAGEMENT')" v-show="currentMenu === 'AGENT_MANAGEMENT'" class="cached-menu-page"><AgentManagementPage :user="user" /></div>
      <div v-if="openedMenus.includes('CODEX_INSTALL_GUIDE')" v-show="currentMenu === 'CODEX_INSTALL_GUIDE'" class="cached-menu-page"><CodexInstallGuidePage @navigate="handleGuideNavigate" /></div>
      <div v-if="openedMenus.includes('OPS_APPROVAL')" v-show="currentMenu === 'OPS_APPROVAL'" class="cached-menu-page"><ApprovalCenterPage /></div>
      <div v-if="openedMenus.includes('OPS_SSH_CLIENT')" v-show="currentMenu === 'OPS_SSH_CLIENT'" class="cached-menu-page"><SshClientPage /></div>
      <div v-if="openedMenus.includes('OPS_DB_CLIENT')" v-show="currentMenu === 'OPS_DB_CLIENT'" class="cached-menu-page"><DbClientPage /></div>
      <div v-if="openedMenus.includes('OPS_GIT_CLIENT')" v-show="currentMenu === 'OPS_GIT_CLIENT'" class="cached-menu-page"><GitClientPage /></div>
      <div v-if="openedMenus.includes('AGENT_EXECUTION')" v-show="currentMenu === 'AGENT_EXECUTION'" class="cached-menu-page"><AgentExecutionPage /></div>
      <div v-if="openedMenus.includes('AGENT_TOOL')" v-show="currentMenu === 'AGENT_TOOL'" class="cached-menu-page"><ToolManagementPage /></div>
      <div v-if="openedMenus.includes('AGENT_AUTHORIZATION')" v-show="currentMenu === 'AGENT_AUTHORIZATION'" class="cached-menu-page"><AgentAuthorizationPage /></div>
      <div v-if="openedMenus.includes('OPS_KNOWLEDGE')" v-show="currentMenu === 'OPS_KNOWLEDGE'" class="cached-menu-page"><KnowledgeBasePage /></div>
      <div v-if="openedMenus.includes('SYS_AUDIT')" v-show="currentMenu === 'SYS_AUDIT'" class="cached-menu-page"><AuditLogPage /></div>
      <div v-if="openedMenus.includes('SYS_DICT')" v-show="currentMenu === 'SYS_DICT'" class="cached-menu-page"><DictManagementPage /></div>
      <div v-if="openedMenus.includes('RESOURCE_HOST')" v-show="currentMenu === 'RESOURCE_HOST'" class="cached-menu-page"><ResourceManagementPage kind="hosts" /></div>
      <div v-if="openedMenus.includes('RESOURCE_APPLICATION')" v-show="currentMenu === 'RESOURCE_APPLICATION'" class="cached-menu-page"><ResourceManagementPage kind="applications" /></div>
      <div v-if="openedMenus.includes('RESOURCE_DATABASE')" v-show="currentMenu === 'RESOURCE_DATABASE'" class="cached-menu-page"><ResourceManagementPage kind="databases" /></div>
      <div v-if="openedMenus.includes('RESOURCE_CREDENTIAL')" v-show="currentMenu === 'RESOURCE_CREDENTIAL'" class="cached-menu-page"><ResourceManagementPage kind="credentials" /></div>
      <section v-if="currentMenuItem && !['HOME', 'OPS_DASHBOARD'].includes(currentMenu) && !openedMenus.includes(currentMenu)" class="management-page"><div class="page-heading"><div><span class="page-kicker">{{ currentMenuItem.code }}</span><h2>{{ currentMenuItem.name }}</h2><p>该菜单已独立展示，页面功能尚待接入。</p></div></div><div class="management-section"><el-empty description="页面功能建设中" /></div></section>
      <div v-if="!currentMenuItem && !['HOME', 'OPS_DASHBOARD'].includes(currentMenu)" class="page"><el-empty description="当前角色未分配可访问菜单" /></div>
      </el-main>
    </el-container>

    <el-dialog v-model="createVisible" :title="editingOpsId ? '编辑应用运维任务' : '新增应用运维任务'" width="min(760px, 94vw)" :fullscreen="isMobile" destroy-on-close>
      <p class="editor-hint">应用配置、日志巡检和补丁部署统一由运维任务管理。支持多后端栈混合部署；Web 可同步静态资源，小程序仅验证不发布。</p>
      <el-form ref="formRef" class="editor-form" :model="form" :rules="rules" label-position="top">
        <div class="editor-section-title">应用信息</div>
        <el-form-item label="应用/任务名称" prop="title">
          <el-input v-model="form.title" placeholder="例如：订单服务异常巡检" />
          <p class="form-item-desc">列表与巡检通知中显示的名称，建议包含业务含义。</p>
        </el-form-item>
        <div class="editor-two-columns">
          <el-form-item label="应用编码" prop="applicationCode">
            <el-input v-model="form.applicationCode" placeholder="例如 order-service" @input="invalidateDeploymentDetection" />
            <p class="form-item-desc">稳定唯一标识，用于匹配部署目录与制品命名。</p>
          </el-form-item>
          <el-form-item label="后端工程类型">
            <el-select v-model="form.backendKind" class="full-width" @change="invalidateDeploymentDetection">
              <el-option v-for="item in backendKindOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <p class="form-item-desc">决定验证命令、产物发现与部署方式。</p>
          </el-form-item>
          <el-form-item label="代码仓库地址">
            <el-input v-model="form.repositoryUrl" placeholder="例如 https://git.example.com/order-service.git" clearable />
            <p class="form-item-desc">可选，便于追溯源码与协作说明。</p>
          </el-form-item>
          <el-form-item label="负责人">
            <el-input v-model="form.ownerName" placeholder="应用负责人" clearable />
            <p class="form-item-desc">业务或运维对接人，便于审批与通知。</p>
          </el-form-item>
        </div>
        <div class="editor-section-title">运行资源</div>
        <el-form-item label="目标主机" prop="hostId">
          <el-select v-model="form.hostId" class="full-width" filterable placeholder="选择已启用的主机" @change="invalidateDeploymentDetection">
            <el-option v-for="host in enabledHosts" :key="host.id" :label="`${host.name} (${host.ipAddress})`" :value="host.id" />
          </el-select>
          <p class="form-item-desc">日志拉取与远程部署所在主机，须已启用并探测连通。</p>
          <p v-if="!enabledHosts.length" class="form-item-desc">暂无可用主机，请先到资源中心配置并启用主机。</p>
        </el-form-item>
        <el-form-item label="后端工作区" prop="workspacePath">
          <div class="path-row">
            <el-input v-model="form.workspacePath" placeholder="例如 D:\work\demo-service" clearable />
            <el-button type="primary" :icon="Folder" @click="openPicker('backend')">浏览文件夹</el-button>
          </div>
          <p class="form-item-desc">
            必填。{{ backendWorkspaceHint }} 的目录，或其子目录含对应工程的父目录。
            <template v-if="form.backendKind === 'JAVA_WAR'">部署目录请指向 Tomcat 的 webapps；端侧请选「无端侧」。</template>
          </p>
        </el-form-item>
        <el-form-item label="后端验证命令">
          <el-input v-model="form.backendVerifyCommand" :placeholder="backendVerifyPlaceholder" clearable />
          <p class="form-item-desc">修复后本地验证；留空使用该工程类型的默认命令。</p>
        </el-form-item>
        <template v-if="form.backendKind === 'DOCKER'">
          <el-form-item label="Compose 文件">
            <el-input v-model="form.dockerComposeFile" placeholder="docker-compose.yml" clearable />
            <p class="form-item-desc">相对工作区的 Compose 文件名，用于构建与重启服务。</p>
          </el-form-item>
          <el-form-item label="镜像名">
            <el-input v-model="form.dockerImageName" placeholder="默认取应用编码" clearable />
            <p class="form-item-desc">可选；不填时按应用编码作为镜像名。</p>
          </el-form-item>
        </template>
        <el-form-item label="端侧工程类型">
          <el-select v-model="form.clientKind" class="full-width" @change="onClientKindChange">
            <el-option v-for="item in clientKindOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <p class="form-item-desc">无前端选「无端侧」；Web 可同步静态资源，小程序仅验证不发布。</p>
        </el-form-item>
        <el-form-item v-if="form.clientKind !== 'NONE'" label="端侧工程目录" prop="frontendWorkspacePath">
          <div class="path-row">
            <el-input
              v-model="form.frontendWorkspacePath"
              :placeholder="form.clientKind === 'MP_WEIXIN_NATIVE' ? '例如 D:\\work\\demo-mp' : '例如 D:\\work\\demo-web'"
              clearable
            />
            <el-button :icon="Folder" @click="openPicker('frontend')">浏览文件夹</el-button>
          </div>
          <p class="form-item-desc">
            须与后端工作区处于安全公共父目录下。验证：
            <template v-if="form.clientKind === 'WEB_NPM'">npm run build</template>
            <template v-else-if="form.clientKind === 'UNI_APP'">npm run build:mp-weixin（可覆盖）</template>
            <template v-else>微信小程序工程结构弱校验（不自动发布）</template>。
          </p>
        </el-form-item>
        <el-form-item v-if="form.clientKind === 'WEB_NPM' || form.clientKind === 'UNI_APP'" label="端侧验证命令">
          <el-input v-model="form.clientVerifyCommand" :placeholder="clientVerifyPlaceholder" clearable />
          <p class="form-item-desc">留空使用默认命令；仅允许 npm / pnpm / yarn。</p>
        </el-form-item>
        <template v-if="form.clientKind === 'WEB_NPM'">
          <el-form-item label="静态部署目录">
            <el-input v-model="form.clientDeployPath" placeholder="可选，例如 /var/www/app（空则不部署端侧）" clearable />
            <p class="form-item-desc">目标主机上的 Web 静态目录；留空则只验证不部署端侧。</p>
          </el-form-item>
          <el-form-item label="本地产物目录">
            <el-input v-model="form.clientDistDir" placeholder="dist" clearable />
            <p class="form-item-desc">相对端侧工程的构建输出目录，默认 dist。</p>
          </el-form-item>
        </template>
        <el-form-item label="修复引擎">
          <el-select v-model="form.repairEngine" class="full-width" placeholder="选择本地修复引擎">
            <el-option v-for="item in repairEngineOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <p class="form-item-desc">「自动」优先全局默认引擎，失败时依次尝试其他已启用引擎；也可固定为 CodeX、Cursor 或 Claude Code。</p>
        </el-form-item>
        <div class="editor-section-title">补丁部署</div>
        <el-form-item label="部署方式">
          <div class="deploy-mode-card">
            <div class="deploy-mode-row">
              <el-switch v-model="form.autoDeploy" />
              <div class="deploy-mode-copy">
                <strong>{{ form.autoDeploy ? '验证通过后自动部署' : '仅生成一键部署脚本' }}</strong>
                <span>无论是否开启，验证通过后都会生成 backup.sh、start.sh 和 deploy.sh。</span>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="候选部署目录" prop="deploymentPaths">
          <div class="deployment-path-editor">
            <div class="deployment-path-input-row">
              <el-input
                v-model="deploymentCandidateInput"
                clearable
                placeholder="例如 /opt/apps/order-service"
                @keyup.enter="addDeploymentCandidate()"
              />
              <div class="deployment-path-actions">
                <el-button :icon="Plus" @click="addDeploymentCandidate()">添加</el-button>
                <RemoteDirectoryPicker :host-id="form.hostId" @select="addDeploymentCandidate" />
              </div>
            </div>
            <div v-if="form.deploymentPaths.length" class="deployment-path-tags">
              <el-tag
                v-for="path in form.deploymentPaths"
                :key="path"
                type="info"
                effect="plain"
                closable
                @close="removeDeploymentCandidate(path)"
              >{{ path }}</el-tag>
            </div>
            <div v-else class="deployment-path-empty">尚未添加候选目录，可手动输入或连接服务器浏览选择。</div>
            <div class="deployment-detection-row">
              <el-tooltip
                :disabled="!deploymentDetectBlockedReason"
                :content="deploymentDetectBlockedReason"
                placement="top"
              >
                <span class="deployment-detect-trigger">
                  <el-button
                    type="primary"
                    :loading="detectingDeploymentPath"
                    :disabled="!!deploymentDetectBlockedReason"
                    @click="runDeploymentPathDetection"
                  >
                    识别应用部署路径
                  </el-button>
                </span>
              </el-tooltip>
              <div v-if="form.deploymentTargets.length" class="deployment-bound-tags">
                <el-tag
                  v-for="item in form.deploymentTargets"
                  :key="`${item.path}:${item.jar}`"
                  type="success"
                  effect="plain"
                  closable
                  @close="removeDeploymentTarget(item.path, item.jar)"
                >{{ item.path }} / {{ item.jar }}</el-tag>
              </div>
              <el-input
                v-else-if="form.deploymentPath"
                class="deployment-result-input"
                :model-value="form.deploymentJar ? `${form.deploymentPath} / ${form.deploymentJar}` : form.deploymentPath"
                readonly
              >
                <template #prepend>已绑定</template>
              </el-input>
              <span v-else-if="deploymentDetectionMessage" class="deployment-detection-message">{{ deploymentDetectionMessage }}</span>
              <span v-else-if="deploymentDetectBlockedReason" class="deployment-detection-message">{{ deploymentDetectBlockedReason }}</span>
            </div>
            <div v-if="detectionCandidates.length" class="deployment-bind-list">
              <div class="deployment-bind-title">识别到的路径与制品（可点选多个；Java 为 JAR，其它栈可绑定目录）</div>
              <div
                v-for="item in detectionCandidates"
                :key="item.path"
                class="deployment-bind-card"
                :class="{ active: isDeploymentPathBound(item.path) }"
              >
                <div class="deployment-bind-path">
                  <strong>{{ item.path }}</strong>
                  <el-tag size="small" effect="plain">可信分 {{ item.score || 0 }}</el-tag>
                </div>
                <div v-if="item.evidence?.length" class="deployment-bind-evidence">{{ item.evidence.join('；') }}</div>
                <div v-if="item.jars?.length" class="deployment-bind-jars">
                  <el-button
                    v-for="jar in item.jars"
                    :key="`${item.path}:${jar}`"
                    size="small"
                    :type="isDeploymentTargetBound(item.path, jar) ? 'primary' : 'default'"
                    @click="toggleDeploymentTarget(item.path, jar)"
                  >{{ jar }}</el-button>
                </div>
                <div v-else class="deployment-bind-empty">该目录下未发现可部署 JAR</div>
              </div>
            </div>
          </div>
          <p class="form-item-desc deployment-path-hint">
            可连接目标服务器选择多个候选目录。识别后可点选路径与制品；自动部署将按绑定目标依次匹配本地产物（多模块按 JAR 名匹配）。
          </p>
        </el-form-item>
        <div class="editor-section-title">巡检策略</div>
        <el-form-item label="运维目标与现象" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="描述现象、影响范围、已知日志路径或期望修复目标" />
          <p class="form-item-desc">供 Agent 理解背景；写清现象、影响与期望结果更利于分析。</p>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="form.priority" class="full-width">
            <el-option v-for="item in priorityOptions" :key="optionValue(item)" :label="item.label" :value="optionValue(item)" />
          </el-select>
          <p class="form-item-desc">影响列表排序与审批紧急程度。</p>
        </el-form-item>
        <el-form-item label="定时拉日志">
          <el-switch v-model="form.scheduleEnabled" active-text="开启" inactive-text="关闭" />
          <p class="form-item-desc">开启后按周期从目标主机拉取日志并可选自动分析。</p>
        </el-form-item>
        <template v-if="form.scheduleEnabled">
          <el-form-item label="拉取周期" prop="pullIntervalMinutes">
            <el-select v-model="form.pullIntervalMinutes" class="full-width" placeholder="选择执行周期">
              <el-option v-for="item in logPullIntervalOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <p class="form-item-desc">两次自动拉日志之间的间隔。</p>
          </el-form-item>
          <el-form-item label="日志时间范围" prop="logRangeMinutes">
            <el-select v-model="form.logRangeMinutes" class="full-width" placeholder="选择日志时间范围">
              <el-option v-for="item in logPullRangeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <p class="form-item-desc">每次只拉取该时间窗口内的最新日志，避免整文件过大。</p>
          </el-form-item>
          <el-form-item label="日志来源">
            <el-select v-model="form.logTemplate" class="full-width">
              <el-option v-for="item in logTemplateOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <p class="form-item-desc">应用日志文件或 systemd/journal 等来源模板。</p>
          </el-form-item>
          <el-form-item v-if="form.logTemplate === 'APP_LOG_FILE'" label="远端日志路径" prop="logPath">
            <el-select
              v-model="form.logPath"
              class="full-width"
              filterable
              clearable
              :disabled="!form.hostId"
              :placeholder="form.hostId ? (selectedHostLogPaths.length ? '选择白名单路径（可为日志目录）' : '该主机尚未配置允许的日志路径') : '请先选择目标主机'"
            >
              <el-option v-for="path in selectedHostLogPaths" :key="path" :label="path" :value="path" />
            </el-select>
            <p class="form-item-desc">须在主机白名单内；可为文件或目录，分日日志配目录即可。</p>
            <p v-if="form.hostId && !selectedHostLogPaths.length" class="form-item-desc">
              请先到资源中心为该主机配置「允许的日志路径」白名单。
            </p>
          </el-form-item>
          <el-form-item v-if="form.logTemplate === 'JAVA_APPLICATION_LOGS'" label="systemd 服务名">
            <el-input v-model="form.logServiceName" placeholder="例如 demo-service" clearable />
            <p class="form-item-desc">对应 journalctl/systemd 的服务单元名。</p>
          </el-form-item>
          <el-form-item label="异常时自动分析">
            <el-switch v-model="form.autoAnalyze" active-text="开启" inactive-text="仅落盘" />
            <p class="form-item-desc">开启后发现异常关键字会自动进入根因分析流程。</p>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">{{ editingOpsId ? '保存并重新启动' : '创建' }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="passwordVisible" title="修改密码" width="min(460px, 92vw)" destroy-on-close>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top" class="editor-form">
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input v-model="passwordForm.currentPassword" type="password" show-password autocomplete="current-password" />
          <p class="form-item-desc">用于校验本人身份。</p>
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password autocomplete="new-password" />
          <p class="form-item-desc">长度 8–64 位；修改成功后需重新登录。</p>
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password autocomplete="new-password" />
          <p class="form-item-desc">再次输入新密码，避免输错。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="submitPassword">确认修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pickerVisible" :title="pickerDialogTitle" width="min(720px, 94vw)" destroy-on-close append-to-body>
      <div class="fs-toolbar">
        <el-button :icon="ArrowLeft" :disabled="!parentPath" @click="browse(parentPath)">上级目录</el-button>
        <el-select class="root-select" :model-value="roots.find(item => currentPath.startsWith(item.path))?.path || roots[0]?.path" placeholder="根目录" @change="browse">
          <el-option v-for="root in roots" :key="root.path" :label="root.name" :value="root.path" />
        </el-select>
      </div>
      <p class="editor-hint">当前：<code>{{ currentPath }}</code>
        <el-tag v-if="pickerSelectable" type="success" size="small" style="margin-left: 8px;">可选用</el-tag>
      </p>
      <el-table v-loading="browsing" :data="entries" height="360" row-key="path" empty-text="没有子目录" @row-dblclick="chooseEntry">
        <el-table-column label="目录名" min-width="200">
          <template #default="{ row }">
            <button class="dir-link" type="button" @click="browse(row.path)">{{ row.name }}</button>
          </template>
        </el-table-column>
        <el-table-column label="标记" width="160">
          <template #default="{ row }">
            <template v-if="pickerTarget === 'frontend' && pickerNeedsMiniProgram">
              <el-tag v-if="row.directMiniProgram" type="success" size="small">本目录小程序</el-tag>
              <el-tag v-else-if="row.hasMiniProgram" type="warning" size="small">子目录有小程序</el-tag>
              <span v-else class="muted-text">—</span>
            </template>
            <template v-else-if="pickerTarget === 'frontend'">
              <el-tag v-if="row.directPackageJson" type="success" size="small">本目录 package.json</el-tag>
              <el-tag v-else-if="row.hasPackageJson" type="warning" size="small">子目录有前端</el-tag>
              <span v-else class="muted-text">—</span>
            </template>
            <template v-else>
              <el-tag v-if="row[backendMarkerKey]" type="success" size="small">可选用</el-tag>
              <span v-else class="muted-text">—</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!entrySelectable(row)" @click="chooseEntry(row)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" :icon="Check" :disabled="!pickerSelectable" @click="chooseCurrent">选择当前目录</el-button>
      </template>
    </el-dialog>

    <IncidentDetailDrawer
      v-model:visible="detailVisible"
      :selected-ops="selectedOps"
      :selected="selected"
      :detail-loading="detailLoading"
      :detail-refreshing="detailRefreshing"
      :detail-enabling="detailEnabling"
      :detail-live-refreshing="detailLiveRefreshing"
      :detail-secondary-loading="detailSecondaryLoading"
      :detail-incidents="detailIncidents"
      :paged-detail-incidents="pagedDetailIncidents"
      :incident-page="incidentPage"
      :incident-page-size="incidentPageSize"
      :detail-approvals="detailApprovals"
      :detail-is-active="detailIsActive"
      :simplified-timeline="simplifiedTimeline"
      :repair-review-timeline-step="repairReviewTimelineStep"
      :repair-pending="repairPending"
      :repair-reviewing="repairReviewing"
      :review-status="selected?.context?.codexReviewStatus"
      :repair-files="repairFiles"
      :active-repair-engine-label="activeRepairEngineLabel"
      :active-repair-step-label="activeRepairStepLabel"
      :pull-timeline-type="pullTimelineType"
      :pull-timeline-running="pullTimelineRunning"
      :next-timeline-label="nextTimelineLabel"
      :manual-deployment-ready="manualDeploymentReady"
      :branch-merge-pending="branchMergePending"
      :artifact-operating="artifactOperating"
      :deciding-approval-id="decidingApprovalId"
      :is-mobile="isMobile"
      @update:visible="detailVisible = $event"
      @close="closeDetail"
      @open-edit="openEdit"
      @open-incident="openIncidentFromList"
      @enable="enableFromDetail"
      @refresh="refreshDetail()"
      @run="run"
      @approve="approve"
      @reject="reject"
      @decide-approval="decideToolApproval"
      @review="reviewRepair"
      @show-repair-diff="repairDiffVisible = true"
      @download-artifact="downloadArtifact"
      @download-deployment-scripts="downloadDeploymentScripts"
      @decide-branch-merge="decideBranchMerge"
      @finish-manual-verification="finishManualVerification"
      @update:incidentPage="incidentPage = $event"
      @update:incidentPageSize="incidentPageSize = $event"
    />

    <RepairDiffDialog
      v-model:visible="repairDiffVisible"
      :title="`${activeRepairStepLabel || activeRepairEngineLabel} 代码变更对比`"
      :repair-files="repairFiles"
      :repair-diff-files="repairDiffFiles"
      :repair-pending="repairPending"
      :repair-reviewing="repairReviewing"
      :review-status="selected?.context?.codexReviewStatus"
      @update:visible="repairDiffVisible = $event"
      @review="reviewRepair"
    />

    <el-drawer v-model="mobileNav" direction="ltr" size="min(300px, 88vw)" :with-header="false" class="mobile-nav-drawer" :modal="true" :close-on-click-modal="true" :close-on-press-escape="true" :show-close="false">
      <div class="mobile-nav-header">
        <span class="mobile-nav-title">功能导航</span>
        <button class="mobile-nav-close" @click="mobileNav = false" aria-label="关闭导航">×</button>
      </div>
      <el-menu :default-active="currentMenu" class="sidebar-menu" @select="handleMenuSelect">
        <SidebarMenuItem v-for="item in menuTree" :key="item.id" :item="item" />
      </el-menu>
    </el-drawer>

    <teleport to="body">
      <div class="notification-toasts-container">
        <NotificationToast
          v-for="(toast, index) in toasts"
          :key="toast.id"
          :notification="toast"
          :style="{ bottom: `${isMobile ? 12 + index * 72 : 24 + index * 80}px` }"
          @click="openTaskById(toast.opsTaskId)"
        />
      </div>
    </teleport>
  </el-container>
</template>

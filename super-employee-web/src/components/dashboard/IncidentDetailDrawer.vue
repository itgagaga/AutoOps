<script setup>
import { computed } from 'vue'
import { CircleCheck, CircleClose, Download, Loading, Refresh, VideoPlay } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  selectedOps: { type: Object, default: null },
  selected: { type: Object, default: null },
  detailLoading: { type: Boolean, default: false },
  detailRefreshing: { type: Boolean, default: false },
  detailEnabling: { type: Boolean, default: false },
  detailLiveRefreshing: { type: Boolean, default: false },
  detailSecondaryLoading: { type: Boolean, default: false },
  detailIncidents: { type: Array, default: () => [] },
  pagedDetailIncidents: { type: Array, default: () => [] },
  incidentPage: { type: Number, default: 1 },
  incidentPageSize: { type: Number, default: 5 },
  detailApprovals: { type: Array, default: () => [] },
  detailIsActive: { type: Boolean, default: false },
  simplifiedTimeline: { type: Array, default: () => [] },
  repairReviewTimelineStep: { type: Object, default: null },
  repairPending: { type: Boolean, default: false },
  repairReviewing: { type: Boolean, default: false },
  reviewStatus: { type: String, default: '' },
  repairFiles: { type: Array, default: () => [] },
  activeRepairEngineLabel: { type: String, default: '' },
  activeRepairStepLabel: { type: String, default: '' },
  pullTimelineType: { type: String, default: '' },
  pullTimelineRunning: { type: Boolean, default: false },
  nextTimelineLabel: { type: String, default: '' },
  manualDeploymentReady: { type: Boolean, default: false },
  branchMergePending: { type: Boolean, default: false },
  artifactOperating: { type: Boolean, default: false },
  decidingApprovalId: { type: String, default: '' },
  isMobile: { type: Boolean, default: false }
})

const emit = defineEmits([
  'update:visible', 'close', 'open-edit', 'open-incident',
  'enable', 'refresh', 'run', 'approve', 'reject',
  'decide-approval', 'review', 'show-repair-diff',
  'download-artifact', 'download-deployment-scripts',
  'decide-branch-merge', 'finish-manual-verification',
  'update:incidentPage', 'update:incidentPageSize'
])

const INCIDENT_STATUS_LABELS = {
  NEW: '新建', RUNNING: '分析中', APPROVAL_PENDING: '待审批', DEPLOYING: '部署中',
  RESOLVED: '已解决', REJECTED: '已驳回', FAILED: '失败', TRIAGED: '已分诊',
  DIAGNOSED: '已诊断', TESTING: '测试中', PATCH_READY: '待验证', WAITING_INPUT: '待人工验证'
}
const INCIDENT_STATUS_TYPES = {
  NEW: 'info', RUNNING: '', APPROVAL_PENDING: 'warning', DEPLOYING: 'warning',
  RESOLVED: 'success', REJECTED: 'danger', FAILED: 'danger', TRIAGED: 'info',
  DIAGNOSED: '', TESTING: 'warning', PATCH_READY: 'warning', WAITING_INPUT: 'warning'
}
const OPS_STATUS_LABELS = {
  CREATED: '已创建', ANALYZING: '分析中', WAITING_APPROVAL: '待审批', COMPLETED: '已完成', FAILED: '失败'
}
const OPS_STATUS_TYPES = {
  CREATED: 'info', ANALYZING: '', WAITING_APPROVAL: 'warning', COMPLETED: 'success', FAILED: 'danger'
}
const APPROVAL_STATUS_LABELS = { PENDING: '待审批', APPROVED: '已批准', REJECTED: '已拒绝' }
const APPROVAL_STATUS_TYPES = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }

function incidentStatusLabel(incident) {
  const stage = String(incident?.context?.codexRepairStage || '')
  if (stage === 'WAITING_BRANCH_MERGE') return '待确认合并'
  if (stage === 'WAITING_MANUAL_DEPLOYMENT') return '待部署验证'
  if (incident?.context?.codexReviewStatus === 'PENDING') return '待确认修复'
  return INCIDENT_STATUS_LABELS[incident?.status] || incident?.status || '未知'
}
function incidentStatusTagType(incident) {
  const stage = String(incident?.context?.codexRepairStage || '')
  if (stage === 'WAITING_BRANCH_MERGE' || stage === 'WAITING_MANUAL_DEPLOYMENT') return 'warning'
  if (incident?.context?.codexReviewStatus === 'PENDING') return 'warning'
  return INCIDENT_STATUS_TYPES[incident?.status] || 'info'
}
function approvalStatusLabel(status) {
  return APPROVAL_STATUS_LABELS[status] || status || ''
}
function approvalStatusType(status) {
  return APPROVAL_STATUS_TYPES[status] || 'info'
}
function textLines(value) {
  const lines = String(value || '').replace(/\r\n/g, '\n').split('\n')
  if (lines.at(-1) === '') lines.pop()
  return lines
}
function isRepairReviewTimelineStep(step) {
  return step?.success !== false && props.repairReviewTimelineStep === step
}
function shouldShowTimelineSummary(step) {
  const name = formatStepName(step?.step)
  if (step?.displayName === '验证构建') {
    // 验证构建只保留阶段名，制品路径/下载统一放到「部署执行」
    return step?.success === false
  }
  if (step?.success === false
    || /最终结论|执行异常|人工驳回|修复确认|合并/.test(name)) {
    return true
  }
  return false
}

function fileNameOf(path) {
  const value = String(path || '').replace(/\\/g, '/')
  const index = value.lastIndexOf('/')
  return index >= 0 ? value.slice(index + 1) : value
}

function extractJarName(text) {
  const matches = String(text || '').match(/[A-Za-z0-9][A-Za-z0-9._-]*\.(?:jar|war)/gi) || []
  return matches.filter(name => !/-sources\.|-javadoc\./i.test(name)).at(-1) || ''
}

function formatArtifactSize(size) {
  const value = Number(size)
  if (!Number.isFinite(value) || value <= 0) return ''
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(2)} MB`
}

function artifactKindLabel(name) {
  const lower = String(name || '').toLowerCase()
  if (lower.endsWith('.war')) return 'WAR'
  if (lower.endsWith('.jar')) return 'JAR'
  return '制品'
}

function collectArtifactSources(step) {
  const incident = props.selected || {}
  const ops = props.selectedOps || {}
  return {
    ...(ops.deploymentJar ? { deploymentJar: ops.deploymentJar } : {}),
    ...(ops.deploymentPath ? { deploymentPath: ops.deploymentPath } : {}),
    ...(ops.deploymentTargets ? { deploymentTargets: ops.deploymentTargets } : {}),
    ...(ops.applicationCode ? { applicationCode: ops.applicationCode } : {}),
    ...(incident.context || {}),
    ...(step?.context || {})
  }
}

function timelineArtifact(step) {
  // 仅「部署执行」展示 JAR/下载；验证构建、合并确认等阶段都不挂制品卡。
  if (step?.displayName !== '部署执行') return null
  const ctx = collectArtifactSources(step)
  const deploymentJar = String(ctx.deploymentJar || '').trim()
  let artifactName = String(ctx.artifactName || fileNameOf(ctx.artifactPath)
    || fileNameOf(ctx.deploymentArtifact) || fileNameOf(deploymentJar) || '').trim()
  if (!artifactName) {
    artifactName = extractJarName(step?.summary) || ''
  }
  if (!artifactName && ctx.applicationCode) {
    artifactName = `${ctx.applicationCode}.jar`
  }
  if (!artifactName && !deploymentJar && !ctx.artifactPath) return null
  const targets = Array.isArray(ctx.deploymentTargets) ? ctx.deploymentTargets : []
  const targetLines = targets.length
    ? targets.map(item => {
        const path = String(item?.path || '').trim()
        const jar = String(item?.artifact || item?.jar || '').trim()
        return [path, jar].filter(Boolean).join(' / ')
      }).filter(Boolean)
    : [[ctx.deploymentPath, deploymentJar].filter(Boolean).join(' / ')].filter(Boolean)
  const sizeText = formatArtifactSize(ctx.artifactSize)
  const artifactPath = String(ctx.artifactPath || ctx.deploymentArtifact || '').trim()
  const kind = artifactKindLabel(artifactName || deploymentJar)
  const headline = artifactName
    ? `${kind}：${artifactName}${sizeText ? ` · ${sizeText}` : ''}`
    : (deploymentJar ? `部署 ${kind}：${deploymentJar}` : `${kind}：未记录到文件名`)
  return {
    stage: '部署执行',
    artifactName,
    kind,
    sizeText,
    headline,
    artifactPath,
    autoDeploy: ctx.autoDeploy === true || ctx.autoDeploy === 'true',
    targetLines,
    showActions: !!(String(ctx.artifactName || ctx.artifactPath || artifactName || '').trim())
  }
}
const stepLabels = {
  'agentscope-log-query': '日志查询',
  'agentscope-patch-preview': '补丁预览',
  'agentscope-codex-repair': 'CodeX 修复',
  'agentscope-verification': '验证执行',
  'agentscope-deployment': '部署执行',
  'agentscope-final': '最终结论',
  'agentscope-error': '执行异常',
  'operator-rejection': '人工驳回'
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
  const mapped = stepLabels[raw] || stepLabels[raw.toLowerCase()]
  if (mapped) return mapped === 'CodeX 修复' ? props.activeRepairStepLabel || mapped : mapped
  return raw.replace(/^agentscope[- ]?/i, '').replace(/-/g, ' ')
}
function formatTime(date) {
  if (!date) return ''
  const d = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(d.getTime())) return String(date)
  const pad = n => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const taskTitle = computed(() => props.selectedOps?.title || props.selected?.title || '运维任务详情')
const taskStatusType = computed(() => OPS_STATUS_TYPES[props.selectedOps?.status] || 'info')
const taskStatusText = computed(() => OPS_STATUS_LABELS[props.selectedOps?.status] || props.selectedOps?.status || '')

/** 运维任务与故障任务合并为单一状态，避免「已完成 / 已解决」并排歧义。 */
const fusedStatus = computed(() => {
  const ops = props.selectedOps
  const incident = props.selected
  if (!ops && !incident) return null
  if (props.repairPending || props.branchMergePending || props.manualDeploymentReady) {
    return { text: '待确认', type: 'warning' }
  }
  const opsStatus = ops?.status || ''
  const incidentStatus = incident?.status || ''
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
  if ((opsStatus === 'COMPLETED' || !ops)
      && (incidentStatus === 'RESOLVED' || !incident || incidentStatus === 'CANCELLED')) {
    return { text: '已完成', type: 'success' }
  }
  if (opsStatus === 'COMPLETED') {
    return { text: incidentStatusLabel(incident) || '已完成', type: incidentStatusTagType(incident) || 'success' }
  }
  if (ops) {
    return { text: taskStatusText.value || '进行中', type: taskStatusType.value }
  }
  return {
    text: incidentStatusLabel(incident) || '进行中',
    type: incidentStatusTagType(incident) || 'info'
  }
})

const awaitingOperatorDecision = computed(() =>
  props.branchMergePending || props.manualDeploymentReady || props.repairPending)
const createdAt = computed(() => props.selectedOps?.createdAt || props.selected?.createdAt)
const approvedFiles = computed(() => {
  if (props.repairFiles.length === 0) return ''
  return props.repairFiles.join('、')
})
const activeReviewStatusLabel = computed(() => {
  if (props.reviewStatus === 'ACCEPTED') return '已接受'
  if (props.reviewStatus === 'REVERTED') return '已撤销'
  return '待确认'
})
const activeReviewStatusType = computed(() => {
  if (props.reviewStatus === 'ACCEPTED') return 'success'
  if (props.reviewStatus === 'REVERTED') return 'info'
  return 'warning'
})
</script>

<template>
  <el-drawer
    :model-value="visible"
    direction="rtl"
    size="min(560px, 96vw)"
    class="incident-detail-drawer"
    destroy-on-close
    @update:model-value="(v) => emit('update:visible', v)"
    @close="emit('close')"
  >
    <template #header>
      <div class="drawer-heading">
        <div class="detail-title">
          <h3>{{ taskTitle }}</h3>
          <el-tag v-if="fusedStatus" size="small" :type="fusedStatus.type">
            {{ fusedStatus.text }}
          </el-tag>
        </div>
        <span class="muted-text">创建于 {{ formatTime(createdAt) }}</span>
      </div>
    </template>

    <div v-if="detailLoading" class="drawer-loading">
      <el-skeleton :rows="10" animated />
    </div>
    <template v-else-if="selectedOps || selected">
      <div class="drawer-block detail-panel">
        <div class="drawer-label">运维说明</div>
        <p class="description">{{ selectedOps?.description || selected?.description || '暂无描述' }}</p>
      </div>
      <div v-if="manualDeploymentReady" class="drawer-block pending-decision-card">
        <div class="detail-title">
          <strong>确认人工部署验证结果</strong>
          <el-tag size="small" type="warning">待确认</el-tag>
        </div>
        <p class="description">请先下载制品并完成部署，再回填验证结果。</p>
        <div class="actions artifact-actions">
          <el-button type="success" :icon="CircleCheck" :disabled="artifactOperating" @click="emit('finish-manual-verification', true)">
            部署验证通过并完结
          </el-button>
          <el-button type="danger" :icon="CircleClose" :disabled="artifactOperating" @click="emit('finish-manual-verification', false)">
            验证失败
          </el-button>
        </div>
      </div>
      <div v-if="selectedOps" class="drawer-block detail-panel">
        <div class="drawer-label">关联信息</div>
        <dl class="detail-kv">
          <div><dt>目标主机</dt><dd>{{ selectedOps.hostName || selectedOps.hostId || '—' }}</dd></div>
          <div><dt>后端类型</dt><dd>{{ selectedOps.backendKind || '—' }}</dd></div>
          <div><dt>后端工作区</dt><dd class="path-value">{{ selectedOps.workspacePath || '—' }}</dd></div>
          <div><dt>端侧类型</dt><dd>{{ selectedOps.clientKind || '—' }}</dd></div>
          <div><dt>端侧工程</dt><dd class="path-value">{{ selectedOps.frontendWorkspacePath || '—' }}</dd></div>
          <div v-if="selectedOps.clientDeployPath"><dt>静态部署目录</dt><dd class="path-value">{{ selectedOps.clientDeployPath }}</dd></div>
          <div v-if="selectedOps.clientVerifyCommand"><dt>端侧验证命令</dt><dd>{{ selectedOps.clientVerifyCommand }}</dd></div>
          <div><dt>修复引擎</dt><dd>{{ selectedOps.repairEngine || '—' }}</dd></div>
          <div><dt>启用状态</dt><dd><el-tag size="small" :type="selectedOps.enabled ? 'success' : 'info'">{{ selectedOps.enabled ? '已启用' : '未启用' }}</el-tag></dd></div>
          <div><dt>定时拉日志</dt><dd>{{ selectedOps.scheduleEnabled ? '已开启' : '关闭' }}</dd></div>
          <div><dt>最近拉取</dt><dd>{{ selectedOps.lastPullSummary || '尚未拉取' }}</dd></div>
          <div><dt>本地日志</dt><dd class="path-value">{{ selectedOps.lastPullFile || '—' }}</dd></div>
        </dl>
      </div>
      <div v-if="selectedOps" class="drawer-block">
        <div class="drawer-label section-heading">
          <span>故障任务</span>
          <el-tag size="small" type="info" class="fault-task-count-tag">
            <template v-if="detailSecondaryLoading">
              <el-icon class="is-loading" :size="12"><Loading /></el-icon>
              <span>加载中</span>
            </template>
            <template v-else>{{ detailIncidents.length }}</template>
          </el-tag>
        </div>
        <div v-if="detailIncidents.length" class="incident-list">
          <button
            v-for="incident in pagedDetailIncidents"
            :key="incident.id"
            type="button"
            :class="['incident-list-item', { active: selected?.id === incident.id }]"
            @click="emit('open-incident', incident)"
          >
            <span class="incident-list-main">
              <strong>{{ incident.title }}</strong>
              <small>{{ incident.latestSummary || incident.description || '等待处理' }}</small>
            </span>
            <span class="incident-list-meta">
              <el-tag size="small" :type="incidentStatusTagType(incident)">{{ incidentStatusLabel(incident) }}</el-tag>
              <time>{{ formatTime(incident.createdAt) }}</time>
            </span>
          </button>
          <el-pagination
            v-if="detailIncidents.length > incidentPageSize"
            :model-value="incidentPage"
            :page-size="incidentPageSize"
            class="incident-pagination"
            small
            background
            :page-sizes="[5, 10, 20]"
            layout="total, sizes, prev, pager, next"
            :total="detailIncidents.length"
            @update:current-page="(v) => emit('update:incidentPage', v)"
            @update:page-size="(v) => emit('update:incidentPageSize', v)"
          />
        </div>
        <div v-else-if="detailSecondaryLoading" class="no-incidents">
          正在加载该运维任务的故障历史…
        </div>
        <div v-else class="no-incidents">
          当前尚未检测到异常。定时日志发现错误特征后，会自动创建故障任务并启动 Agent 分析。
        </div>
      </div>
      <div class="actions">
        <el-button v-if="selectedOps" @click="emit('open-edit', selectedOps)">编辑任务</el-button>
        <el-button
          v-if="selectedOps && !selectedOps.enabled"
          type="primary"
          :icon="VideoPlay"
          :loading="detailEnabling"
          @click="emit('enable')"
        >启用任务</el-button>
        <el-button
          v-if="selectedOps"
          :icon="Refresh"
          :loading="detailRefreshing"
          @click="emit('refresh')"
        >刷新状态</el-button>
        <el-button v-if="selectedOps?.enabled && selected" type="primary" :icon="VideoPlay" @click="emit('run')">重新启动分析</el-button>
        <el-button v-if="selected?.status === 'APPROVAL_PENDING'" type="success" :icon="CircleCheck" @click="emit('approve')">批准部署</el-button>
        <el-button v-if="selected?.status === 'APPROVAL_PENDING'" type="danger" :icon="CircleClose" @click="emit('reject')">驳回方案</el-button>
      </div>
      <template v-if="detailApprovals.length">
        <div class="drawer-label">审批记录</div>
        <div v-for="item in detailApprovals" :key="item.id" class="drawer-block approval-inline-card">
          <div class="detail-title">
            <strong>{{ item.toolName || item.toolCode }}</strong>
            <span class="approval-card-tags">
              <el-tag size="small" :type="approvalStatusType(item.status)">{{ approvalStatusLabel(item.status) }}</el-tag>
              <el-tag size="small" type="info">{{ item.riskLevel || 'MEDIUM' }}</el-tag>
            </span>
          </div>
          <p class="description">{{ item.description || `Agent 请求调用 ${item.toolCode}` }}</p>
          <pre class="timeline-summary">{{ item.argumentsJson }}</pre>
          <p v-if="item.status !== 'PENDING'" class="approval-decision">
            {{ item.decidedBy || '操作人员' }} · {{ formatTime(item.decidedAt) }}
          </p>
          <div v-if="item.status === 'PENDING'" class="actions">
            <el-button
              type="success"
              :icon="CircleCheck"
              :loading="decidingApprovalId === item.id"
              @click="emit('decide-approval', item, true)"
            >批准</el-button>
            <el-button
              type="danger"
              :icon="CircleClose"
              :disabled="!!decidingApprovalId"
              @click="emit('decide-approval', item, false)"
            >拒绝</el-button>
          </div>
        </div>
      </template>
      <div class="drawer-label section-heading">
        <span>执行时间线</span>
        <span v-if="detailIsActive" class="timeline-live-status">
          <el-icon class="is-loading"><Loading /></el-icon>
          {{ detailLiveRefreshing ? '正在同步最新进度' : (awaitingOperatorDecision ? '等待你确认' : '持续跟踪中') }}
        </span>
      </div>
      <el-timeline v-if="selectedOps || simplifiedTimeline.length || detailIsActive">
        <el-timeline-item
          v-if="selectedOps"
          :timestamp="formatTime(selectedOps.lastPullAt || selectedOps.createdAt)"
          :type="pullTimelineType"
        >
          <div class="timeline-step">
            <div class="timeline-step-title">
              <strong>日志拉取</strong>
              <span v-if="pullTimelineRunning" class="timeline-live-status">
                <el-icon class="is-loading"><Loading /></el-icon>
                拉取中
              </span>
            </div>
            <div v-if="selectedOps.lastPullFile" class="timeline-file">
              {{ selectedOps.lastPullFile }}
            </div>
          </div>
        </el-timeline-item>
        <el-timeline-item
          v-for="step in simplifiedTimeline"
          :key="(step.executedAt || '') + (step.step || '')"
          :timestamp="formatTime(step.executedAt)"
          :type="step.success ? 'success' : 'danger'"
        >
          <div class="timeline-step">
            <strong>{{ step.displayName || formatStepName(step.step) }}</strong>
            <div v-if="timelineArtifact(step)" class="timeline-file">
              {{ timelineArtifact(step).headline }}
            </div>
            <div
              v-if="timelineArtifact(step)?.artifactPath"
              class="timeline-file"
            >{{ timelineArtifact(step).artifactPath }}</div>
            <div
              v-for="(line, index) in (timelineArtifact(step)?.targetLines || [])"
              :key="'jar-target-' + index"
              class="timeline-file"
            >部署目标：{{ line }}</div>
            <pre v-if="shouldShowTimelineSummary(step)" class="timeline-summary">{{ step.summary }}</pre>
            <div
              v-if="isRepairReviewTimelineStep(step) && repairFiles.length > 0"
              class="repair-review-card timeline-repair-review"
            >
              <div class="detail-title">
                <strong>{{ activeRepairStepLabel || activeRepairEngineLabel }} 代码修复确认</strong>
                <el-tag size="small" :type="activeReviewStatusType">{{ activeReviewStatusLabel }}</el-tag>
              </div>
              <p v-if="approvedFiles" class="description">
                修改 {{ repairFiles.length }} 个文件：{{ approvedFiles }}
              </p>
              <el-button plain type="primary" @click="emit('show-repair-diff')">审阅</el-button>
              <div v-if="repairPending" class="actions repair-actions">
                <el-button type="success" :icon="CircleCheck" :loading="repairReviewing" @click="emit('review', true)">
                  接受并继续验证
                </el-button>
                <el-button type="warning" :icon="CircleClose" :disabled="repairReviewing" @click="emit('review', false)">
                  撤销本次修复
                </el-button>
              </div>
            </div>
            <div
              v-if="timelineArtifact(step)?.showActions"
              class="artifact-review-card"
            >
              <div class="detail-title">
                <strong>
                  {{ timelineArtifact(step).stage === '部署执行'
                    ? (timelineArtifact(step).autoDeploy ? '自动部署制品' : '部署制品')
                    : '构建制品' }}
                </strong>
                <el-tag size="small" type="success">{{ timelineArtifact(step).kind }}</el-tag>
              </div>
              <p v-if="timelineArtifact(step).artifactName" class="description">
                {{ timelineArtifact(step).artifactName }}
                <span v-if="timelineArtifact(step).sizeText">
                  · {{ timelineArtifact(step).sizeText }}
                </span>
              </p>
              <div
                v-if="timelineArtifact(step).artifactPath"
                class="timeline-file"
              >{{ timelineArtifact(step).artifactPath }}</div>
              <div
                v-for="(line, index) in timelineArtifact(step).targetLines"
                :key="index"
                class="timeline-file"
              >部署目标：{{ line }}</div>
              <div v-if="timelineArtifact(step).showActions" class="actions artifact-actions">
                <el-button type="primary" :icon="Download" :loading="artifactOperating" @click="emit('download-artifact')">
                  下载 {{ timelineArtifact(step).kind }}
                </el-button>
                <el-button :icon="Download" :disabled="artifactOperating" @click="emit('download-deployment-scripts')">
                  下载一键部署脚本
                </el-button>
              </div>
            </div>
          </div>
        </el-timeline-item>
        <el-timeline-item v-if="branchMergePending || manualDeploymentReady" type="warning">
          <div class="pending-decision-card">
            <div class="detail-title">
              <strong>{{ branchMergePending ? '确认是否将运维分支合并到主分支' : '确认人工部署验证结果' }}</strong>
              <el-tag size="small" type="warning">待确认</el-tag>
            </div>
            <p class="description">
              {{ branchMergePending
                ? '制品已生成。合并成功后会回收本地运维分支，但不会自动 push。'
                : '请先下载制品并完成部署，再回填验证结果。' }}
            </p>
            <div class="actions artifact-actions">
              <el-button v-if="branchMergePending" type="warning" :icon="CircleCheck" :loading="artifactOperating" @click="emit('decide-branch-merge', true)">
                合并主分支
              </el-button>
              <el-button v-if="branchMergePending" :disabled="artifactOperating" @click="emit('decide-branch-merge', false)">
                暂不合并
              </el-button>
              <el-button v-if="manualDeploymentReady" type="success" :icon="CircleCheck" :disabled="artifactOperating" @click="emit('finish-manual-verification', true)">
                部署验证通过并完结
              </el-button>
              <el-button v-if="manualDeploymentReady" type="danger" :icon="CircleClose" :disabled="artifactOperating" @click="emit('finish-manual-verification', false)">
                验证失败
              </el-button>
            </div>
          </div>
        </el-timeline-item>
        <el-timeline-item v-else-if="detailIsActive" class="timeline-next-loading">
          <template #dot>
            <span class="timeline-pulse-dot" />
          </template>
          <div class="timeline-next-step">
            <strong>{{ nextTimelineLabel }}</strong>
            <span class="three-dot-loading" aria-label="加载中">
              <i /><i /><i />
            </span>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else class="compact-empty" :image-size="72" description="暂无执行记录" />
    </template>
  </el-drawer>
</template>

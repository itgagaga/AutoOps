<!-- @author yangdb -->
<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, Close, Download, Edit, Key, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  cancelClaudeEnvironmentOperation, cancelCodexEnvironmentOperation, cancelCursorEnvironmentOperation,
  createAgent, getClaudeEnvironmentOperation, getClaudeEnvironmentStatus,
  getCodexEnvironmentOperation, getCodexEnvironmentStatus,
  getCursorEnvironmentOperation, getCursorEnvironmentStatus,
  installCodexCli, listAgents, listDictOptions, loginClaudeCli, loginCodexCli, loginCursorCli,
  setAgentEnabled, updateAgent
} from '../api'
import { toLabelMap } from '../dict'
import { useTablePagination } from '../useTablePagination'

const props = defineProps({ user: { type: Object, required: true } })
const agents = ref([])
const loading = ref(false)
const editorVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const filters = reactive({ keyword: '', type: '', status: '' })
const form = reactive({ code: '', name: '', type: 'REMOTE', description: '', endpointUrl: '', modelName: '' })
const typeLabels = ref({ BUILT_IN: '内置智能体', REACT: '协调智能体', REMOTE: '远程服务', TOOL: '工具智能体', WORKFLOW: '流程智能体' })
const typeOptions = ref([])
const statusLabels = { OFFLINE: '离线', IDLE: '空闲', RUNNING: '运行中', ERROR: '异常' }
const typeTagTypes = { BUILT_IN: 'warning', REACT: '', REMOTE: 'success', TOOL: 'info', WORKFLOW: 'danger' }
const envLoading = ref(false)
const engineStatuses = reactive({
  codex: null,
  cursor: null,
  claude: null
})
const ENGINE_DEFS = [
  {
    key: 'codex',
    label: 'CodeX CLI',
    authLabel: 'ChatGPT',
    canInstall: true,
    getStatus: getCodexEnvironmentStatus,
    getOperation: getCodexEnvironmentOperation,
    login: loginCodexCli,
    cancel: cancelCodexEnvironmentOperation,
    install: installCodexCli
  },
  {
    key: 'cursor',
    label: 'Cursor CLI',
    authLabel: '账号',
    canInstall: false,
    getStatus: getCursorEnvironmentStatus,
    getOperation: getCursorEnvironmentOperation,
    login: loginCursorCli,
    cancel: cancelCursorEnvironmentOperation
  },
  {
    key: 'claude',
    label: 'Claude Code',
    authLabel: '账号',
    canInstall: false,
    getStatus: getClaudeEnvironmentStatus,
    getOperation: getClaudeEnvironmentOperation,
    login: loginClaudeCli,
    cancel: cancelClaudeEnvironmentOperation
  }
]
const rules = {
  code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: '使用大写字母、数字和下划线', trigger: 'blur' }],
  name: [{ required: true, message: '请输入 Agent 名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择 Agent 类型', trigger: 'change' }]
}

let refreshTimer = null
let envOperationTimer = null

const stats = computed(() => ({
  total: agents.value.length,
  online: agents.value.filter(item => ['IDLE', 'RUNNING'].includes(item.runtimeStatus)).length,
  running: agents.value.filter(item => item.runtimeStatus === 'RUNNING').length,
  error: agents.value.filter(item => item.runtimeStatus === 'ERROR').length
}))
const filteredAgents = computed(() => agents.value.filter(agent => {
  const keyword = filters.keyword.trim().toLowerCase()
  const matchesKeyword = !keyword || agent.name.toLowerCase().includes(keyword) || agent.code.toLowerCase().includes(keyword)
  return matchesKeyword && (!filters.type || agent.type === filters.type) && (!filters.status || agent.runtimeStatus === filters.status)
}))
const { page, pageSize, total, paged, onSizeChange } = useTablePagination(filteredAgents)
const engineCards = computed(() => ENGINE_DEFS.map(def => {
  const status = engineStatuses[def.key]
  const busy = status?.operation?.state === 'RUNNING'
  return {
    ...def,
    status,
    busy,
    operationLabel: status?.operation?.type === 'INSTALL' ? '安装中' : '等待登录',
    message: busy
      ? `${status?.operation?.type === 'INSTALL' ? '安装中' : '等待登录'}：${status?.operation?.message || ''}`
      : [
          status?.version || (status?.installed ? '已安装' : '未安装，可前往环境配置向导完成'),
          status?.authenticated ? status.authSummary : null
        ].filter(Boolean).join(' · ')
  }
}))

async function loadDicts() {
  try {
    const options = await listDictOptions('AGENT_TYPE')
    if (options.length) {
      typeOptions.value = options
      typeLabels.value = toLabelMap(options)
    }
  } catch {
    typeOptions.value = Object.entries(typeLabels.value).map(([code, label]) => ({ code, label, value: code }))
  }
}

async function load(silent = false) {
  if (!silent) loading.value = true
  try {
    if (!silent) await loadDicts()
    agents.value = await listAgents()
  }
  catch (error) { if (!silent) ElMessage.error(error.message) }
  finally { if (!silent) loading.value = false }
}
function resetFilters() { Object.assign(filters, { keyword: '', type: '', status: '' }) }
function openCreate() {
  editingId.value = null
  Object.assign(form, { code: '', name: '', type: 'REMOTE', description: '', endpointUrl: '', modelName: '' })
  formRef.value?.clearValidate()
  editorVisible.value = true
}
function openEdit(agent) {
  editingId.value = agent.id
  Object.assign(form, { code: agent.code, name: agent.name, type: agent.type, description: agent.description || '', endpointUrl: agent.endpointUrl || '', modelName: agent.modelName || '' })
  formRef.value?.clearValidate()
  editorVisible.value = true
}
async function save() {
  await formRef.value.validate()
  try {
    const payload = {
      ...form,
      enabled: editingId.value ? (agents.value.find(item => item.id === editingId.value)?.enabled ?? true) : true
    }
    if (editingId.value) await updateAgent(editingId.value, payload); else await createAgent(payload)
    editorVisible.value = false
    await load()
    ElMessage.success('Agent 配置已保存')
  } catch (error) { ElMessage.error(error.message) }
}
async function toggleEnabled(agent, enabled) {
  try {
    const updated = await setAgentEnabled(agent.id, enabled)
    Object.assign(agent, updated)
    ElMessage.success(enabled ? 'Agent 已启用' : 'Agent 已停用')
  } catch (error) { agent.enabled = !enabled; ElMessage.error(error.message) }
}
function formatTime(value) {
  if (!value) return '尚未上报'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function engineDef(key) {
  return ENGINE_DEFS.find(item => item.key === key)
}

async function loadEnvironmentStatus(silent = false) {
  if (props.user.role !== 'ADMIN') return
  if (!silent) envLoading.value = true
  try {
    const [codex, cursor, claude] = await Promise.all([
      getCodexEnvironmentStatus(),
      getCursorEnvironmentStatus(),
      getClaudeEnvironmentStatus()
    ])
    engineStatuses.codex = codex
    engineStatuses.cursor = cursor
    engineStatuses.claude = claude
    if ([codex, cursor, claude].some(item => item?.operation?.state === 'RUNNING')) {
      startEnvOperationPolling()
    }
  } catch (error) {
    if (!silent) ElMessage.error(error.message)
  } finally {
    if (!silent) envLoading.value = false
  }
}

function stopEnvOperationPolling() {
  if (envOperationTimer) clearInterval(envOperationTimer)
  envOperationTimer = null
}

function startEnvOperationPolling() {
  if (envOperationTimer) return
  envOperationTimer = setInterval(async () => {
    try {
      let anyRunning = false
      for (const def of ENGINE_DEFS) {
        const current = engineStatuses[def.key]
        if (current?.operation?.state !== 'RUNNING') continue
        const operation = await def.getOperation()
        engineStatuses[def.key] = { ...(current || {}), operation }
        if (operation.state === 'RUNNING') {
          anyRunning = true
          continue
        }
        await loadEnvironmentStatus(true)
        if (operation.state === 'SUCCEEDED') {
          ElMessage.success(operation.type === 'INSTALL'
            ? `${def.label} 安装完成`
            : `${def.label} 登录完成`)
        } else if (operation.state === 'FAILED') {
          ElMessage.error(operation.message || `${def.label} 操作失败`)
        }
      }
      if (!anyRunning) stopEnvOperationPolling()
    } catch (error) {
      stopEnvOperationPolling()
      ElMessage.error(error.message)
    }
  }, 2000)
}

async function installEngine(key) {
  const def = engineDef(key)
  if (!def?.install) return
  try {
    await ElMessageBox.confirm(
      '将从官方地址下载并安装 CodeX CLI 到当前 Windows 用户目录，是否继续？',
      '安装 CodeX CLI',
      { type: 'warning', confirmButtonText: '开始安装', cancelButtonText: '取消' })
    const operation = await def.install()
    engineStatuses[key] = { ...(engineStatuses[key] || {}), operation }
    startEnvOperationPolling()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || String(error))
  }
}

async function loginEngine(key) {
  const def = engineDef(key)
  if (!def) return
  try {
    const operation = await def.login()
    engineStatuses[key] = { ...(engineStatuses[key] || {}), operation }
    ElMessage.info(`已启动 ${def.label} 登录，请在浏览器或终端中完成授权`)
    startEnvOperationPolling()
  } catch (error) {
    ElMessage.error(error.message || String(error))
  }
}

async function cancelEngineOperation(key) {
  const def = engineDef(key)
  if (!def) return
  try {
    const operation = await def.cancel()
    engineStatuses[key] = { ...(engineStatuses[key] || {}), operation }
    stopEnvOperationPolling()
    ElMessage.success('操作已取消')
  } catch (error) {
    ElMessage.error(error.message)
  }
}

onMounted(() => {
  load()
  loadEnvironmentStatus()
  refreshTimer = setInterval(() => load(true), 15000)
})
onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  stopEnvOperationPolling()
})
</script>

<template>
  <section class="management-page agent-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">AGENT OPERATIONS</span>
          <h2>Agent 管理</h2>
          <p>维护运维流程中的协调与专业智能体。运行状态由心跳自动检测：本地 Agent 随平台探测，远程 Agent 超时未上报则离线。</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Refresh" @click="load()">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增 Agent</el-button>
        </div>
      </div>

      <div class="agent-metrics" aria-label="Agent 运行概览">
        <div><span>Agent 总数</span><strong>{{ stats.total }}</strong></div>
        <div><span>在线</span><strong class="metric-success">{{ stats.online }}</strong></div>
        <div><span>运行中</span><strong class="metric-primary">{{ stats.running }}</strong></div>
        <div><span>异常</span><strong class="metric-danger">{{ stats.error }}</strong></div>
      </div>

      <div v-if="user.role === 'ADMIN'" v-loading="envLoading" class="repair-env-grid">
        <div v-for="card in engineCards" :key="card.key" class="repair-env-card">
          <div class="repair-env-main">
            <div class="repair-env-title">
              <strong>{{ card.label }}</strong>
              <el-tag v-if="card.status && card.status.supported === false" size="small" type="danger">不支持</el-tag>
              <el-tag v-else-if="!card.status?.installed" size="small" type="info">未安装</el-tag>
              <el-tag v-else size="small" type="success">已安装</el-tag>
              <el-tag
                v-if="card.status?.installed"
                size="small"
                :type="card.status.authenticated ? 'success' : 'warning'"
              >
                {{ card.status.authenticated ? `${card.authLabel} 已登录` : `${card.authLabel} 未登录` }}
              </el-tag>
            </div>
            <p class="repair-env-message">{{ card.message }}</p>
          </div>
          <div class="repair-env-actions">
            <el-button size="small" :icon="Refresh" :disabled="card.busy" @click="loadEnvironmentStatus()">检测</el-button>
            <el-button
              v-if="card.canInstall && card.status?.supported && !card.status?.installed"
              size="small"
              type="primary"
              :icon="Download"
              :loading="card.status?.operation?.type === 'INSTALL' && card.busy"
              @click="installEngine(card.key)"
            >安装</el-button>
            <el-button
              v-else-if="card.status?.installed && !card.status?.authenticated"
              size="small"
              type="primary"
              :icon="Key"
              :loading="card.status?.operation?.type === 'LOGIN' && card.busy"
              @click="loginEngine(card.key)"
            >登录</el-button>
            <el-button
              v-else-if="card.status?.authenticated"
              size="small"
              type="success"
              plain
              :icon="CircleCheck"
              disabled
            >可用</el-button>
            <el-button
              v-if="card.busy"
              size="small"
              type="danger"
              plain
              :icon="Close"
              @click="cancelEngineOperation(card.key)"
            >取消</el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="management-section page-fixed-body">
      <el-form :model="filters" inline class="query-form agent-query-form">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable placeholder="Agent 名称或编码" :prefix-icon="Search" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="filters.type" clearable placeholder="全部类型"><el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部状态"><el-option v-for="(label, value) in statusLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item class="query-actions"><el-button type="primary" :icon="Search">查询</el-button><el-button :icon="Refresh" @click="resetFilters">重置</el-button></el-form-item>
      </el-form>
      <div class="table-heading"><div><h3>Agent 列表</h3><span>共 {{ total }} 条记录</span></div></div>
      <div class="page-table-scroll">
        <el-table v-loading="loading" :data="paged" border stripe row-key="id" class="agent-table" height="100%" empty-text="暂无 Agent">
          <el-table-column label="Agent" min-width="220">
            <template #default="{ row }">
              <el-tooltip :content="row.description || '暂无职责说明'" placement="top" :show-after="400">
                <div class="agent-cell">
                  <div class="agent-cell-title">
                    <strong>{{ row.name }}</strong>
                    <code>{{ row.code }}</code>
                  </div>
                </div>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="118" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="typeTagTypes[row.type] || 'info'" effect="plain">
                {{ typeLabels[row.type] || row.type }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="模型" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="cell-main">{{ row.modelName || '未配置' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="服务地址" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="cell-sub">{{ row.endpointUrl || '本地执行' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="运行状态" width="110" align="center">
            <template #default="{ row }">
              <span class="status-pill" :data-status="row.runtimeStatus || 'OFFLINE'">
                <i class="status-dot" />
                {{ statusLabels[row.runtimeStatus] || row.runtimeStatus || '未知' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="最近心跳" width="168" align="right">
            <template #default="{ row }"><span class="time-cell">{{ formatTime(row.lastHeartbeat) }}</span></template>
          </el-table-column>
          <el-table-column label="任务" width="128" align="center">
            <template #default="{ row }">
              <div class="task-stats">
                <strong>{{ row.totalTasks ?? 0 }}</strong>
                <span>
                  <em class="ok">{{ row.successTasks ?? 0 }}</em>
                  /
                  <em class="bad">{{ row.failedTasks ?? 0 }}</em>
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="78" align="center">
            <template #default="{ row }"><el-switch v-model="row.enabled" @change="toggleEnabled(row, $event)" /></template>
          </el-table-column>
          <el-table-column label="操作" width="88" fixed="right" align="center">
            <template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button></template>
          </el-table-column>
        </el-table>
      </div>
      <div class="table-pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          background
          layout="total, sizes, prev, pager, next"
          :page-sizes="[20, 50, 100]"
          :total="total"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <el-dialog v-model="editorVisible" :title="editingId ? '编辑 Agent' : '新增 Agent'" width="min(720px, 94vw)" destroy-on-close>
      <p class="editor-hint">新建默认启用，可在列表中切换启用状态。协调智能体（REACT）负责调度日志诊断、根因分析、CodeX 修复与验证。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <el-row :gutter="14">
          <el-col :xs="24" :sm="12">
            <el-form-item label="Agent 编码" prop="code">
              <el-input v-model="form.code" :disabled="Boolean(editingId)" placeholder="例如：ALERT_ANALYSIS" />
              <p class="form-item-desc">大写字母开头，仅含大写字母、数字与下划线；创建后不可改。</p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="Agent 名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入名称" />
              <p class="form-item-desc">界面展示名称，建议体现职责。</p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="Agent 类型" prop="type">
              <el-select v-model="form.type" class="full-width"><el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" /></el-select>
              <p class="form-item-desc">REACT 为协调调度；BUILT_IN 为内置专业能力；REMOTE 为外部服务。</p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="模型名称">
              <el-input v-model="form.modelName" placeholder="例如：qwen-plus" />
              <p class="form-item-desc">调用大模型时的模型 ID；本地工具型可留空。</p>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="服务地址">
              <el-input v-model="form.endpointUrl" placeholder="本地执行为空；远程服务填写 URL" />
              <p class="form-item-desc">本地执行留空；远程 Agent 填写可访问的 HTTP 地址。</p>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="职责说明">
              <el-input v-model="form.description" type="textarea" :rows="3" placeholder="用中文描述 Agent 职责与适用场景" />
              <p class="form-item-desc">说明适用场景，列表悬停可查看。</p>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="editorVisible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.repair-env-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-top: 8px;
}

.repair-env-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid #cfe3ee;
  border-radius: 6px;
  background: linear-gradient(135deg, #f5fbff 0%, #f8fafc 100%);
}

.repair-env-title,
.repair-env-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.repair-env-title strong {
  margin-right: 2px;
  color: #243b53;
  font-size: 13px;
}

.repair-env-message {
  margin: 4px 0 0;
  overflow: hidden;
  color: #627d98;
  font-size: 11px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.repair-env-actions {
  margin-top: auto;
}

@media (max-width: 1100px) {
  .repair-env-grid { grid-template-columns: 1fr; }
}

.agent-table :deep(.el-table__cell) {
  padding: 10px 0;
}

.agent-cell {
  min-width: 0;
  cursor: default;
}

.agent-cell-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.agent-cell-title strong {
  color: #243b53;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}

.agent-cell-title code {
  padding: 1px 6px;
  border-radius: 3px;
  background: #f0f4f8;
  color: #147d92;
  font-size: 11px;
  line-height: 1.5;
}

.cell-main {
  color: #334e68;
  font-size: 13px;
}

.cell-sub,
.time-cell {
  color: #627d98;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f0f4f8;
  color: #486581;
  font-size: 12px;
  line-height: 1.6;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #829ab1;
}

.status-pill[data-status='IDLE'] {
  background: #e3f9e5;
  color: #1b7a36;
}
.status-pill[data-status='IDLE'] .status-dot { background: #2da44e; }

.status-pill[data-status='RUNNING'] {
  background: #e6f6fb;
  color: #0b6e84;
}
.status-pill[data-status='RUNNING'] .status-dot { background: #147d92; }

.status-pill[data-status='ERROR'] {
  background: #fde8e8;
  color: #b42318;
}
.status-pill[data-status='ERROR'] .status-dot { background: #d92d20; }

.status-pill[data-status='OFFLINE'] {
  background: #eef2f6;
  color: #627d98;
}
.status-pill[data-status='OFFLINE'] .status-dot { background: #9aa5b1; }

.task-stats {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  line-height: 1.2;
}

.task-stats strong {
  color: #243b53;
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.task-stats span {
  color: #829ab1;
  font-size: 11px;
}

.task-stats em {
  font-style: normal;
  font-variant-numeric: tabular-nums;
}

.task-stats .ok { color: #238636; }
.task-stats .bad { color: #c0392b; }
</style>

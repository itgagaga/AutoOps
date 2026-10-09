<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  getAgentPermissions, listAgents, listDictOptionsBatch, listResources, listTools, saveAgentPermissions
} from '../api'
import { toCssMap, toLabelMap } from '../dict'

const agents = ref([])
const tools = ref([])
const hosts = ref([])
const applications = ref([])
const databases = ref([])
const selectedAgentId = ref('')
const loading = ref(false)
const saving = ref(false)
const detailLoading = ref(false)
const keyword = ref('')

const riskLabels = ref({ LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重' })
const riskTypes = ref({ LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' })

const toolRows = ref([])
const resourceRows = ref([])
const policy = reactive({
  autoApproveMaxRisk: 'LOW',
  productionRequiresApproval: true,
  credentialAccessEnabled: false
})

const accessLevels = [
  { value: 'READ', label: '只读' },
  { value: 'EXECUTE', label: '执行' },
  { value: 'ADMIN', label: '管理' }
]
const resourceTypes = [
  { value: 'HOST', label: '主机' },
  { value: 'APPLICATION', label: '应用' },
  { value: 'DATABASE', label: '数据库' }
]
const autoApproveOptions = [
  { value: 'NONE', label: '全部需审批' },
  { value: 'LOW', label: '低风险可自动通过' },
  { value: 'MEDIUM', label: '中及以下可自动通过' }
]

const filteredAgents = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  return agents.value.filter(agent => {
    if (!q) return true
    return `${agent.name} ${agent.code}`.toLowerCase().includes(q)
  })
})

const selectedAgent = computed(() => agents.value.find(item => item.id === selectedAgentId.value) || null)

const stats = computed(() => ({
  agents: agents.value.length,
  tools: toolRows.value.filter(item => item.granted).length,
  resources: resourceRows.value.length,
  credential: policy.credentialAccessEnabled ? 1 : 0
}))

const resourceCatalog = computed(() => {
  const map = {
    HOST: hosts.value.map(item => ({ id: item.id, name: item.name || item.hostName || item.code, environment: item.environment })),
    APPLICATION: applications.value.map(item => ({ id: item.id, name: item.name || item.code, environment: item.environment || '-' })),
    DATABASE: databases.value.map(item => ({ id: item.id, name: item.name || item.instanceName || item.code, environment: item.environment }))
  }
  return map
})

function resourceOptions(type) {
  const used = new Set(resourceRows.value.filter(item => item.resourceType === type).map(item => item.resourceId))
  return (resourceCatalog.value[type] || []).filter(item => !used.has(item.id))
}

async function loadDicts() {
  try {
    const batch = await listDictOptionsBatch(['RISK_LEVEL'])
    if (batch.RISK_LEVEL?.length) {
      riskLabels.value = toLabelMap(batch.RISK_LEVEL)
      riskTypes.value = { ...riskTypes.value, ...toCssMap(batch.RISK_LEVEL) }
    }
  } catch { /* 本地兜底 */ }
}

async function loadBase() {
  loading.value = true
  try {
    await loadDicts()
    const [agentList, toolList, hostList, appList, dbList] = await Promise.all([
      listAgents(), listTools(), listResources('hosts'), listResources('applications'), listResources('databases')
    ])
    agents.value = agentList
    tools.value = toolList
    hosts.value = hostList
    applications.value = appList
    databases.value = dbList
    if (!selectedAgentId.value && agentList.length) {
      selectedAgentId.value = agentList[0].id
    } else if (selectedAgentId.value && !agentList.some(item => item.id === selectedAgentId.value)) {
      selectedAgentId.value = agentList[0]?.id || ''
    } else if (selectedAgentId.value) {
      await loadDetail(selectedAgentId.value)
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function buildToolRows(grants = []) {
  const byToolId = new Map(grants.map(item => [item.toolId, item]))
  toolRows.value = tools.value.map(tool => {
    const grant = byToolId.get(tool.id)
    return {
      toolId: tool.id,
      code: tool.code,
      name: tool.name,
      riskLevel: tool.riskLevel,
      description: tool.description || '',
      granted: !!grant,
      enabled: grant ? !!grant.enabled : true,
      timeoutSeconds: grant?.timeoutSeconds ?? tool.timeoutSeconds ?? 300,
      dailyLimit: grant?.dailyLimit ?? 100
    }
  })
}

function buildResourceRows(grants = []) {
  resourceRows.value = grants.map(item => ({
    resourceType: item.resourceType,
    resourceId: item.resourceId,
    resourceName: item.resourceName || item.resourceId,
    environment: item.environment || '-',
    accessLevel: item.accessLevel || 'READ',
    enabled: item.enabled !== false
  }))
}

async function loadDetail(agentId) {
  if (!agentId) {
    toolRows.value = []
    resourceRows.value = []
    Object.assign(policy, { autoApproveMaxRisk: 'LOW', productionRequiresApproval: true, credentialAccessEnabled: false })
    return
  }
  detailLoading.value = true
  try {
    const data = await getAgentPermissions(agentId)
    buildToolRows(data.tools || [])
    buildResourceRows(data.resources || [])
    Object.assign(policy, {
      autoApproveMaxRisk: data.policy?.autoApproveMaxRisk || 'LOW',
      productionRequiresApproval: data.policy?.productionRequiresApproval !== false,
      credentialAccessEnabled: !!data.policy?.credentialAccessEnabled
    })
  } catch (error) {
    ElMessage.error(error.message)
    buildToolRows([])
    buildResourceRows([])
  } finally {
    detailLoading.value = false
  }
}

function selectAgent(agent) {
  selectedAgentId.value = agent.id
}

function addResource() {
  const type = 'HOST'
  const options = resourceOptions(type)
  if (!options.length) {
    ElMessage.warning('没有可新增的主机资源，或已全部授权')
    return
  }
  const first = options[0]
  resourceRows.value.push({
    resourceType: type,
    resourceId: first.id,
    resourceName: first.name,
    environment: first.environment || '-',
    accessLevel: 'READ',
    enabled: true
  })
}

function onResourceTypeChange(row) {
  const options = resourceOptions(row.resourceType)
  const pick = options[0]
  if (!pick) {
    row.resourceId = ''
    row.resourceName = ''
    row.environment = '-'
    ElMessage.warning('该类型下暂无可授权资源')
    return
  }
  row.resourceId = pick.id
  row.resourceName = pick.name
  row.environment = pick.environment || '-'
}

function onResourceIdChange(row) {
  const found = (resourceCatalog.value[row.resourceType] || []).find(item => item.id === row.resourceId)
  row.resourceName = found?.name || row.resourceId
  row.environment = found?.environment || '-'
}

function removeResource(index) {
  resourceRows.value.splice(index, 1)
}

async function save() {
  if (!selectedAgentId.value) {
    ElMessage.warning('请先选择 Agent')
    return
  }
  for (const row of resourceRows.value) {
    if (!row.resourceId) {
      ElMessage.warning('请完善资源授权中的资源选择')
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      tools: toolRows.value.filter(item => item.granted).map(item => ({
        toolId: item.toolId,
        timeoutSeconds: item.timeoutSeconds,
        dailyLimit: item.dailyLimit,
        enabled: item.enabled !== false
      })),
      resources: resourceRows.value.map(item => ({
        resourceType: item.resourceType,
        resourceId: item.resourceId,
        accessLevel: item.accessLevel,
        enabled: item.enabled !== false
      })),
      policy: {
        autoApproveMaxRisk: policy.autoApproveMaxRisk,
        productionRequiresApproval: policy.productionRequiresApproval,
        credentialAccessEnabled: policy.credentialAccessEnabled
      }
    }
    const data = await saveAgentPermissions(selectedAgentId.value, payload)
    buildToolRows(data.tools || [])
    buildResourceRows(data.resources || [])
    Object.assign(policy, {
      autoApproveMaxRisk: data.policy?.autoApproveMaxRisk || 'LOW',
      productionRequiresApproval: data.policy?.productionRequiresApproval !== false,
      credentialAccessEnabled: !!data.policy?.credentialAccessEnabled
    })
    ElMessage.success('授权策略已保存')
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    saving.value = false
  }
}

watch(selectedAgentId, id => loadDetail(id))

onMounted(loadBase)
</script>

<template>
  <section class="management-page authz-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">AGENT AUTHORIZATION</span>
          <h2>授权策略</h2>
          <p>为每个 Agent 配置可调用工具、可访问资源，以及高风险动作的审批边界。未授权的工具与主机将无法被 Agent 调用。</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadBase">刷新</el-button>
          <el-button type="primary" :loading="saving" :disabled="!selectedAgentId" @click="save">保存策略</el-button>
        </div>
      </div>

      <div class="agent-metrics">
        <div><span>Agent 数</span><strong>{{ stats.agents }}</strong></div>
        <div><span>已授工具</span><strong class="metric-primary">{{ stats.tools }}</strong></div>
        <div><span>已授资源</span><strong class="metric-success">{{ stats.resources }}</strong></div>
        <div><span>凭据访问</span><strong :class="stats.credential ? 'metric-warning' : ''">{{ stats.credential ? '开' : '关' }}</strong></div>
      </div>
    </div>

    <div class="authz-layout page-fixed-scroll" v-loading="loading">
      <aside class="management-section authz-agents">
        <div class="table-heading">
          <div><h3>选择 Agent</h3><span>共 {{ filteredAgents.length }} 个</span></div>
        </div>
        <el-input v-model="keyword" clearable :prefix-icon="Search" placeholder="名称或编码" class="agent-search" />
        <div class="agent-list">
          <button
            v-for="agent in filteredAgents"
            :key="agent.id"
            type="button"
            class="agent-list-item"
            :class="{ active: agent.id === selectedAgentId }"
            @click="selectAgent(agent)"
          >
            <strong>{{ agent.name }}</strong>
            <code>{{ agent.code }}</code>
            <span>{{ agent.enabled ? '已启用' : '已停用' }}</span>
          </button>
          <el-empty v-if="!filteredAgents.length" description="暂无 Agent" :image-size="64" />
        </div>
      </aside>

      <div class="authz-detail" v-loading="detailLoading">
        <template v-if="selectedAgent">
          <div class="management-section">
            <div class="table-heading">
              <div>
                <h3>审批策略 · {{ selectedAgent.name }}</h3>
                <span>高风险工具始终强制审批</span>
              </div>
            </div>
            <el-form label-position="top" class="policy-form editor-form">
              <el-row :gutter="16">
                <el-col :xs="24" :sm="12" :md="8">
                  <el-form-item label="自动通过上限">
                    <el-select v-model="policy.autoApproveMaxRisk" class="full-width">
                      <el-option v-for="opt in autoApproveOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
                    </el-select>
                    <p class="form-item-desc">该风险及以下可自动执行；更高风险进审批。高风险始终强制审批。</p>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8">
                  <el-form-item label="生产环境需审批">
                    <el-switch v-model="policy.productionRequiresApproval" active-text="开启" inactive-text="关闭" />
                    <p class="form-item-desc">开启后，目标为生产环境时即使低风险也需审批。</p>
                  </el-form-item>
                </el-col>
                <el-col :xs="24" :sm="12" :md="8">
                  <el-form-item label="允许使用凭据">
                    <el-switch v-model="policy.credentialAccessEnabled" active-text="开启" inactive-text="关闭" />
                    <p class="form-item-desc">开启后 Agent 才能用已授权主机凭据做 SSH 诊断。</p>
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>

          <div class="management-section">
            <div class="table-heading">
              <div><h3>工具授权</h3><span>勾选后 Agent 方可调用；可单独设置超时与日限额</span></div>
            </div>
            <el-table :data="toolRows" border stripe row-key="toolId" empty-text="暂无工具，请先在工具管理中登记" max-height="360">
              <el-table-column label="授权" width="72" align="center">
                <template #default="{ row }"><el-checkbox v-model="row.granted" /></template>
              </el-table-column>
              <el-table-column label="工具" min-width="200">
                <template #default="{ row }">
                  <div class="tool-cell">
                    <strong>{{ row.name }}</strong>
                    <code>{{ row.code }}</code>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="风险" width="90" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="riskTypes[row.riskLevel] || 'info'">{{ riskLabels[row.riskLevel] || row.riskLevel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="超时(秒)" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.timeoutSeconds" :min="1" :max="3600" :disabled="!row.granted" controls-position="right" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="日限额" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.dailyLimit" :min="1" :max="100000" :disabled="!row.granted" controls-position="right" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="启用" width="80" align="center">
                <template #default="{ row }">
                  <el-switch v-model="row.enabled" :disabled="!row.granted" />
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="management-section">
            <div class="table-heading">
              <div><h3>资源授权</h3><span>主机授权是 SSH 诊断的前提；访问级别建议从只读开始</span></div>
              <el-button type="primary" :icon="Plus" @click="addResource">添加资源</el-button>
            </div>
            <el-table :data="resourceRows" border stripe empty-text="尚未授权任何资源" max-height="320">
              <el-table-column label="类型" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.resourceType" @change="onResourceTypeChange(row)">
                    <el-option v-for="opt in resourceTypes" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="资源" min-width="200">
                <template #default="{ row }">
                  <el-select v-model="row.resourceId" filterable class="full-width" @change="onResourceIdChange(row)">
                    <el-option
                      v-for="opt in [
                        ...(resourceCatalog[row.resourceType] || []).filter(item => item.id === row.resourceId),
                        ...resourceOptions(row.resourceType)
                      ]"
                      :key="opt.id"
                      :label="`${opt.name}${opt.environment && opt.environment !== '-' ? ' · ' + opt.environment : ''}`"
                      :value="opt.id"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="访问级别" width="130">
                <template #default="{ row }">
                  <el-select v-model="row.accessLevel">
                    <el-option v-for="opt in accessLevels" :key="opt.value" :label="opt.label" :value="opt.value" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="启用" width="80" align="center">
                <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
              </el-table-column>
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ $index }">
                  <el-button link type="danger" :icon="Delete" @click="removeResource($index)">移除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
        <div v-else class="management-section">
          <el-empty description="请选择左侧 Agent 配置授权策略" />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.authz-layout {
  display: grid;
  grid-template-columns: minmax(240px, 280px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  min-height: 0;
}

.authz-agents {
  position: sticky;
  top: 0;
}

.agent-search { margin-bottom: 12px; }

.agent-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: min(52vh, 480px);
  overflow: auto;
}

.agent-list-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #d9e2ec;
  background: #fff;
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition: border-color .15s, background .15s;
}

.agent-list-item:hover { border-color: #9fb3c8; }
.agent-list-item.active {
  border-color: #147d92;
  background: #e6f6fb;
}

.agent-list-item strong {
  color: #243b53;
  font-size: 14px;
}

.agent-list-item code {
  width: fit-content;
  padding: 1px 6px;
  border-radius: 3px;
  background: #f0f4f8;
  color: #147d92;
  font-size: 11px;
}

.agent-list-item span {
  color: #829ab1;
  font-size: 12px;
}

.authz-detail { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.policy-form { margin-top: 4px; }
.policy-hint { margin: 0; color: #627d98; font-size: 13px; line-height: 1.6; }

.tool-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.tool-cell strong { color: #243b53; font-size: 13px; }
.tool-cell code { width: fit-content; color: #147d92; font-size: 11px; }

@media (max-width: 960px) {
  .authz-layout { grid-template-columns: 1fr; }
  .authz-agents { position: static; }
  .agent-list { max-height: 240px; }
}
</style>

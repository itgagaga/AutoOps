<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search, View } from '@element-plus/icons-vue'
import { getAgentExecution, listAgentExecutions } from '../api'
import { useTablePagination } from '../useTablePagination'

const items = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const selected = ref(null)
const filters = reactive({ keyword: '', agent: '', result: '' })

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

const filteredGroups = computed(() => items.value.filter(item => {
  const keyword = filters.keyword.trim().toLowerCase()
  const text = `${item.opsTaskTitle || ''} ${item.incidentTitle || ''} ${item.hostName || ''} ${item.summary || ''} ${(item.agentCodes || []).join(' ')}`.toLowerCase()
  return (!keyword || text.includes(keyword))
    && (!filters.agent || item.agentCodes?.includes(filters.agent))
    && (!filters.result
      || (filters.result === 'true' ? item.successCount > 0 : item.failCount > 0))
}))

const agents = computed(() => [...new Set(items.value.flatMap(item => item.agentCodes || []).filter(Boolean))])
const stats = computed(() => ({
  tasks: filteredGroups.value.length,
  steps: filteredGroups.value.reduce((sum, item) => sum + item.stepCount, 0),
  success: filteredGroups.value.reduce((sum, item) => sum + item.successCount, 0),
  failed: filteredGroups.value.reduce((sum, item) => sum + item.failCount, 0)
}))

const { page, pageSize, total, paged, onSizeChange } = useTablePagination(filteredGroups)

function formatStepName(step) {
  if (!step) return '执行步骤'
  const raw = String(step)
  const match = raw.match(/^(codex|cursor|claude)(?:\s+code)?(?:\s+cli)?(\s+修复.*)$/i)
  if (match) {
    const engine = match[1].toLowerCase()
    const label = engine === 'cursor' ? 'Cursor' : engine === 'claude' ? 'Claude Code' : 'CodeX'
    return `${label}${match[2]}`
  }
  if (raw === 'agentscope-codex-repair') return '本地修复'
  return stepLabels[step] || stepLabels[String(step).toLowerCase()] || String(step).replace(/^agentscope[- ]?/i, '').replace(/-/g, ' ')
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function priorityType(priority) {
  return ({ P0: 'danger', P1: 'warning', P2: '', P3: 'info' })[priority] || 'info'
}

async function load() {
  loading.value = true
  try {
    items.value = await listAgentExecutions()
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(filters, { keyword: '', agent: '', result: '' })
}

async function openDetail(row) {
  selected.value = { ...row, steps: [] }
  detailVisible.value = true
  detailLoading.value = true
  try {
    const detail = await getAgentExecution(row.incidentId)
    if (selected.value?.incidentId === row.incidentId) {
      selected.value.steps = detail.steps || []
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    detailLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="management-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">AGENT OBSERVABILITY</span>
          <h2>执行记录</h2>
          <p>按运维任务汇总 Agent 执行步骤。点击详情可查看该任务下的完整执行时间线。</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <div class="agent-metrics">
        <div><span>运维任务</span><strong class="metric-primary">{{ stats.tasks }}</strong></div>
        <div><span>执行步骤</span><strong>{{ stats.steps }}</strong></div>
        <div><span>成功</span><strong class="metric-success">{{ stats.success }}</strong></div>
        <div><span>失败</span><strong class="metric-danger">{{ stats.failed }}</strong></div>
      </div>
    </div>

    <div class="management-section page-fixed-body">
      <el-form inline class="query-form">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable :prefix-icon="Search" placeholder="运维任务、主机或摘要" />
        </el-form-item>
        <el-form-item label="步骤">
          <el-select v-model="filters.agent" clearable filterable placeholder="全部步骤">
            <el-option v-for="agent in agents" :key="agent" :label="formatStepName(agent)" :value="agent" />
          </el-select>
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="filters.result" clearable placeholder="全部结果">
            <el-option label="成功" value="true" />
            <el-option label="失败" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item class="query-actions">
          <el-button type="primary" :icon="Search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-heading">
        <div><h3>按运维任务汇总</h3><span>共 {{ total }} 条记录</span></div>
      </div>
      <div class="page-table-scroll">
        <el-table v-loading="loading" :data="paged" border stripe row-key="id" height="100%" empty-text="暂无执行记录" @row-click="openDetail">
          <el-table-column label="运维任务" min-width="220">
            <template #default="{ row }">
              <div class="agent-identity">
                <strong>{{ row.opsTaskTitle }}</strong>
                <span>{{ row.hostName || row.incidentTitle || '—' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="优先级" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="priorityType(row.priority)" effect="plain">{{ row.priority || '—' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="步骤统计" width="150">
            <template #default="{ row }">
              <div class="agent-task-count">
                <strong>{{ row.stepCount }}</strong>
                <span>成功 {{ row.successCount }} / 失败 {{ row.failCount }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最近步骤" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ formatStepName(row.lastAgentCode) }}</template>
          </el-table-column>
          <el-table-column label="最近执行" width="150" align="right">
            <template #default="{ row }"><span class="time-cell">{{ formatTime(row.lastExecutedAt) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click.stop="openDetail(row)">详情</el-button>
            </template>
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

    <el-drawer
      v-model="detailVisible"
      direction="rtl"
      size="min(560px, 96vw)"
      class="incident-detail-drawer"
      destroy-on-close
    >
      <template #header>
        <div class="drawer-heading">
          <div class="detail-title">
            <h3>{{ selected?.opsTaskTitle || '执行详情' }}</h3>
            <el-tag v-if="selected?.priority" size="small" :type="priorityType(selected.priority)" effect="plain">
              {{ selected.priority }}
            </el-tag>
          </div>
          <span class="muted-text">最近执行 {{ formatTime(selected?.lastExecutedAt) }}</span>
        </div>
      </template>

      <template v-if="selected">
        <div class="drawer-block">
          <div class="drawer-label">关联信息</div>
          <p class="description">
            目标主机：{{ selected.hostName || '—' }}<br>
            代码工作区：{{ selected.workspacePath || '—' }}<br>
            关联故障：{{ selected.incidentTitle || selected.incidentId || '—' }}<br>
            步骤统计：共 {{ selected.stepCount }} 步，成功 {{ selected.successCount }}，失败 {{ selected.failCount }}
          </p>
        </div>
        <div class="drawer-label">执行时间线</div>
        <div v-loading="detailLoading" class="execution-detail-timeline">
          <el-timeline v-if="selected.steps.length">
            <el-timeline-item
              v-for="step in selected.steps"
              :key="step.id"
              :timestamp="formatTime(step.executedAt)"
              :type="step.success ? 'success' : 'danger'"
            >
              <div class="timeline-step">
                <strong>{{ formatStepName(step.agentCode) }}</strong>
                <pre class="timeline-summary">{{ step.summary || '暂无摘要' }}</pre>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else-if="!detailLoading" :image-size="64" description="暂无执行步骤" />
        </div>
      </template>
    </el-drawer>
  </section>
</template>

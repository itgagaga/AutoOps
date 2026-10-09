<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Refresh, Search, View } from '@element-plus/icons-vue'
import { approveRequest, listApprovals, listDictOptions, rejectRequest } from '../api'
import { toCssMap } from '../dict'
import { useTablePagination } from '../useTablePagination'

const items = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const selected = ref(null)
const filters = reactive({ keyword: '', riskLevel: '' })

const riskLabels = ref({ LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '严重风险' })
const riskTypes = ref({ LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' })
const fallbackTools = {
  LOG_QUERY: '日志查询',
  GIT_REPOSITORY: 'Git 仓库工具',
  PATCH_PREVIEW: '补丁预览',
  TEST_VERIFICATION: '测试验证',
  DEPLOY_PATCH: '补丁部署',
  CODEX_LOCAL_REPAIR: 'CodeX 本地修复'
}

const stats = computed(() => ({
  total: items.value.length,
  medium: items.value.filter(item => item.riskLevel === 'MEDIUM').length,
  high: items.value.filter(item => item.riskLevel === 'HIGH').length,
  critical: items.value.filter(item => item.riskLevel === 'CRITICAL').length
}))

const filtered = computed(() => items.value.filter(item => {
  const keyword = filters.keyword.trim().toLowerCase()
  const text = [
    item.toolCode, item.toolName, item.incidentId, item.incidentTitle,
    item.opsTaskId, item.opsTaskTitle, item.hostName, item.description
  ].filter(Boolean).join(' ').toLowerCase()
  return (!keyword || text.includes(keyword)) && (!filters.riskLevel || item.riskLevel === filters.riskLevel)
}))
const { page, pageSize, total, paged, onSizeChange } = useTablePagination(filtered)

function toolName(item) {
  return item.toolName || fallbackTools[item.toolCode] || item.toolCode
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatArgs(value) {
  if (!value) return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return String(value)
  }
}

function argsPreview(value) {
  if (!value) return '—'
  const text = String(value).replace(/\s+/g, ' ').trim()
  return text.length > 80 ? `${text.slice(0, 80)}…` : text
}

async function loadDicts() {
  try {
    const options = await listDictOptions('RISK_LEVEL')
    if (options.length) {
      riskLabels.value = Object.fromEntries(options.map(item => [item.value || item.code, item.label.includes('风险') ? item.label : `${item.label}风险`]))
      riskTypes.value = { ...riskTypes.value, ...toCssMap(options) }
    }
  } catch { /* 本地兜底 */ }
}

async function load() {
  loading.value = true
  try {
    await loadDicts()
    items.value = await listApprovals()
    if (selected.value?.id) {
      selected.value = items.value.find(item => item.id === selected.value.id) || null
      if (!selected.value) detailVisible.value = false
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function openDetail(row) {
  selected.value = row
  detailVisible.value = true
}

function reset() {
  Object.assign(filters, { keyword: '', riskLevel: '' })
}

async function approve(item) {
  try {
    await ElMessageBox.confirm(
      `确认允许 Agent 执行「${toolName(item)}」？批准后 Agent 将继续自主决策。`,
      '批准工具调用',
      { confirmButtonText: '批准', cancelButtonText: '取消', type: 'warning' }
    )
    await approveRequest(item.id, '批准执行')
    detailVisible.value = false
    await load()
    ElMessage.success('已批准，Agent 将继续执行')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message)
  }
}

async function reject(item) {
  try {
    await ElMessageBox.confirm(
      `确认拒绝 Agent 执行「${toolName(item)}」？`,
      '拒绝工具调用',
      {
        confirmButtonText: '拒绝',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await rejectRequest(item.id, '拒绝执行')
    detailVisible.value = false
    await load()
    ElMessage.success('已拒绝，Agent 将重新评估处置方案')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message)
  }
}

onMounted(load)
</script>

<template>
  <section class="management-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">HUMAN OVERSIGHT</span>
          <h2>审批中心</h2>
          <p>审核运维任务中超出 Agent 授权边界的高风险工具动作（如 CodeX 修复、验证、部署）。审批结果会回传给 Agent 继续执行。</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <div class="agent-metrics">
        <div><span>待审批</span><strong class="metric-primary">{{ stats.total }}</strong></div>
        <div><span>中风险</span><strong class="metric-warning">{{ stats.medium }}</strong></div>
        <div><span>高风险</span><strong class="metric-danger">{{ stats.high }}</strong></div>
        <div><span>严重风险</span><strong class="metric-danger">{{ stats.critical }}</strong></div>
      </div>
    </div>

    <div class="management-section page-fixed-body">
      <el-form inline class="query-form compact-query">
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable :prefix-icon="Search" placeholder="工具、运维任务、主机或说明" />
        </el-form-item>
        <el-form-item label="风险">
          <el-select v-model="filters.riskLevel" clearable placeholder="全部风险">
            <el-option v-for="(label, value) in riskLabels" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item class="query-actions">
          <el-button type="primary" :icon="Search">查询</el-button>
          <el-button :icon="Refresh" @click="reset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-heading">
        <div><h3>待审批工具动作</h3><span>共 {{ total }} 条记录</span></div>
      </div>
      <div class="page-table-scroll">
        <el-table v-loading="loading" :data="paged" border stripe row-key="id" height="100%" empty-text="暂无待审批动作" @row-click="openDetail">
          <el-table-column label="工具动作" min-width="180">
            <template #default="{ row }">
              <div class="agent-identity">
                <strong>{{ toolName(row) }}</strong>
                <code>{{ row.toolCode }}</code>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="风险" width="105" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="riskTypes[row.riskLevel] || 'info'">{{ riskLabels[row.riskLevel] || row.riskLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="关联运维" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="agent-identity">
                <strong>{{ row.opsTaskTitle || row.incidentTitle || '—' }}</strong>
                <span>{{ row.hostName || row.opsTaskId || row.incidentId || '暂无关联信息' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="审批说明" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.description || '—' }}</template>
          </el-table-column>
          <el-table-column label="调用参数" min-width="160" show-overflow-tooltip>
            <template #default="{ row }"><span class="muted-text">{{ argsPreview(row.argumentsJson) }}</span></template>
          </el-table-column>
          <el-table-column label="申请时间" width="150" align="right">
            <template #default="{ row }"><span class="time-cell">{{ formatTime(row.requestedAt) }}</span></template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click.stop="openDetail(row)">详情</el-button>
              <el-button link type="success" :icon="CircleCheck" @click.stop="approve(row)">批准</el-button>
              <el-button link type="danger" :icon="CircleClose" @click.stop="reject(row)">拒绝</el-button>
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
            <h3>{{ selected ? toolName(selected) : '审批详情' }}</h3>
            <el-tag v-if="selected" size="small" :type="riskTypes[selected.riskLevel] || 'info'">
              {{ riskLabels[selected.riskLevel] || selected.riskLevel }}
            </el-tag>
          </div>
          <span class="muted-text">申请于 {{ formatTime(selected?.requestedAt) }}</span>
        </div>
      </template>

      <template v-if="selected">
        <div class="drawer-block">
          <div class="drawer-label">关联信息</div>
          <p class="description">
            运维任务：{{ selected.opsTaskTitle || '—' }}<br>
            目标主机：{{ selected.hostName || '—' }}<br>
            代码工作区：{{ selected.workspacePath || '—' }}<br>
            关联故障：{{ selected.incidentTitle || selected.incidentId || '—' }}
          </p>
        </div>
        <div class="drawer-block">
          <div class="drawer-label">审批说明</div>
          <p class="description">{{ selected.description || '暂无说明' }}</p>
        </div>
        <div class="drawer-block">
          <div class="drawer-label">调用参数</div>
          <pre class="timeline-summary">{{ formatArgs(selected.argumentsJson) }}</pre>
        </div>
        <div class="actions">
          <el-button type="success" :icon="CircleCheck" @click="approve(selected)">批准执行</el-button>
          <el-button type="danger" :icon="CircleClose" @click="reject(selected)">拒绝</el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createTool, listDictOptionsBatch, listTools, setToolEnabled, updateTool } from '../api'
import { toCssMap, toLabelMap } from '../dict'
import { useTablePagination } from '../useTablePagination'

const items = ref([])
const loading = ref(false)
const visible = ref(false)
const editingId = ref(null)
const formRef = ref()
const filters = reactive({ keyword: '', type: '', risk: '' })
const form = reactive({ code: '', name: '', type: 'SSH', description: '', commandTemplate: '', riskLevel: 'MEDIUM', timeoutSeconds: 300 })
const typeLabels = ref({ SSH: 'SSH 命令', GIT: 'Git 仓库', LOG: '日志查询', CICD: 'CI/CD 流水线', HTTP: 'HTTP 接口', SCRIPT: '脚本' })
const riskLabels = ref({ LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重' })
const riskTypes = ref({ LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' })
const rules = { code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: '使用大写字母、数字和下划线', trigger: 'blur' }], name: [{ required: true, message: '请输入工具名称', trigger: 'blur' }], type: [{ required: true, message: '请选择工具类型', trigger: 'change' }], riskLevel: [{ required: true, message: '请选择风险等级', trigger: 'change' }] }
const filtered = computed(() => items.value.filter(item => { const keyword = filters.keyword.trim().toLowerCase(); return (!keyword || item.name.toLowerCase().includes(keyword) || item.code.toLowerCase().includes(keyword)) && (!filters.type || item.type === filters.type) && (!filters.risk || item.riskLevel === filters.risk) }))
const { page, pageSize, total, paged, onSizeChange } = useTablePagination(filtered)
const stats = computed(() => ({ total: items.value.length, enabled: items.value.filter(item => item.enabled).length, high: items.value.filter(item => ['HIGH', 'CRITICAL'].includes(item.riskLevel)).length, disabled: items.value.filter(item => !item.enabled).length }))

async function loadDicts() {
  try {
    const batch = await listDictOptionsBatch(['TOOL_TYPE', 'RISK_LEVEL'])
    if (batch.TOOL_TYPE?.length) typeLabels.value = toLabelMap(batch.TOOL_TYPE)
    if (batch.RISK_LEVEL?.length) {
      riskLabels.value = toLabelMap(batch.RISK_LEVEL)
      riskTypes.value = { ...riskTypes.value, ...toCssMap(batch.RISK_LEVEL) }
    }
  } catch { /* 本地兜底 */ }
}

async function load() {
  loading.value = true
  try {
    await loadDicts()
    items.value = await listTools()
  } catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}
function resetFilters() { Object.assign(filters, { keyword: '', type: '', risk: '' }) }
function openCreate() { editingId.value = null; Object.assign(form, { code: '', name: '', type: 'SSH', description: '', commandTemplate: '', riskLevel: 'MEDIUM', timeoutSeconds: 300 }); visible.value = true }
function openEdit(item) { editingId.value = item.id; Object.assign(form, { code: item.code, name: item.name, type: item.type, description: item.description || '', commandTemplate: item.commandTemplate || '', riskLevel: item.riskLevel, timeoutSeconds: item.timeoutSeconds }); visible.value = true }
async function save() {
  await formRef.value.validate()
  try {
    const payload = { ...form, enabled: editingId.value ? (items.value.find(item => item.id === editingId.value)?.enabled ?? true) : true }
    if (editingId.value) await updateTool(editingId.value, payload); else await createTool(payload)
    visible.value = false; await load(); ElMessage.success('工具配置已保存')
  } catch (error) { ElMessage.error(error.message) }
}
async function toggle(item, enabled) { try { Object.assign(item, await setToolEnabled(item.id, enabled)); ElMessage.success(enabled ? '工具已启用' : '工具已停用') } catch (error) { item.enabled = !enabled; ElMessage.error(error.message) } }
onMounted(load)
</script>

<template>
  <section class="management-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">AGENT CAPABILITIES</span>
          <h2>工具管理</h2>
          <p>登记 Agent 可调用的外部能力，并通过风险等级和启用状态控制使用边界。</p>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增工具</el-button>
      </div>
      <div class="agent-metrics">
        <div><span>工具总数</span><strong>{{ stats.total }}</strong></div>
        <div><span>已启用</span><strong class="metric-success">{{ stats.enabled }}</strong></div>
        <div><span>高风险</span><strong class="metric-danger">{{ stats.high }}</strong></div>
        <div><span>已停用</span><strong>{{ stats.disabled }}</strong></div>
      </div>
    </div>
    <div class="management-section page-fixed-body">
      <el-form inline class="query-form">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable :prefix-icon="Search" placeholder="工具名称或编码" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="filters.type" clearable placeholder="全部类型"><el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="风险"><el-select v-model="filters.risk" clearable placeholder="全部风险"><el-option v-for="(label, value) in riskLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item class="query-actions">
          <el-button type="primary" :icon="Search">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </el-form-item>
      </el-form>
      <div class="table-heading"><div><h3>工具清单</h3><span>共 {{ total }} 条记录</span></div></div>
      <div class="page-table-scroll">
        <el-table v-loading="loading" :data="paged" border stripe row-key="id" height="100%">
          <el-table-column label="工具" min-width="230"><template #default="{ row }"><div class="agent-identity"><strong>{{ row.name }}</strong><code>{{ row.code }}</code><span>{{ row.description || '暂无说明' }}</span></div></template></el-table-column>
          <el-table-column label="类型" width="130"><template #default="{ row }">{{ typeLabels[row.type] || row.type }}</template></el-table-column>
          <el-table-column label="风险" width="100"><template #default="{ row }"><el-tag :type="riskTypes[row.riskLevel]">{{ riskLabels[row.riskLevel] }}</el-tag></template></el-table-column>
          <el-table-column prop="timeoutSeconds" label="超时(秒)" width="110" />
          <el-table-column label="启用" width="90"><template #default="{ row }"><el-switch v-model="row.enabled" @change="value => toggle(row, value)" /></template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button></template></el-table-column>
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
    <el-dialog v-model="visible" :title="editingId ? '编辑工具' : '新增工具'" width="min(680px, 92vw)" destroy-on-close>
      <p class="editor-hint">新建默认启用，可在列表中切换启用状态。高风险工具建议完成审批与参数校验后再开放。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <el-row :gutter="18">
          <el-col :xs="24" :sm="12">
            <el-form-item label="工具编码" prop="code">
              <el-input v-model="form.code" :disabled="!!editingId" placeholder="例如 LOG_QUERY" />
              <p class="form-item-desc">Agent 调用时的稳定标识，创建后不可改。</p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="工具名称" prop="name">
              <el-input v-model="form.name" />
              <p class="form-item-desc">界面与审批单中展示的名称。</p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="工具类型" prop="type">
              <el-select v-model="form.type" class="full-width"><el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" /></el-select>
              <p class="form-item-desc">区分查询、执行、集成等能力类别。</p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="风险等级" prop="riskLevel">
              <el-select v-model="form.riskLevel" class="full-width"><el-option v-for="(label, value) in riskLabels" :key="value" :label="label" :value="value" /></el-select>
              <p class="form-item-desc">高风险工具建议开启审批后再对 Agent 开放。</p>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="职责说明">
              <el-input v-model="form.description" type="textarea" :rows="2" />
              <p class="form-item-desc">说明用途与边界，避免误用。</p>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="调用模板">
              <el-input v-model="form.commandTemplate" type="textarea" :rows="3" placeholder="命令或接口调用模板，可暂不填写" />
              <p class="form-item-desc">命令或接口模板；可含占位符，暂无可留空。</p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item label="超时时间(秒)">
              <el-input-number v-model="form.timeoutSeconds" :min="1" :max="3600" />
              <p class="form-item-desc">单次调用最长等待时间，超时将中止。</p>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

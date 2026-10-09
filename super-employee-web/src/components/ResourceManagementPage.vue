<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Delete, Edit, Folder, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { checkDatabaseConnectivity, checkHostConnectivity, createResource, fetchHostFingerprint, listDictOptionsBatch, listResources, listSftp, setResourceEnabled, updateResource } from '../api'
import { optionValue, toCssMap, toLabelMap } from '../dict'
import { useTablePagination } from '../useTablePagination'

const props = defineProps({ kind: { type: String, required: true } })
const items = ref([])
const hosts = ref([])
const credentials = ref([])
const loading = ref(false)
const saving = ref(false)
const probing = ref(false)
const fetchingFingerprint = ref(false)
const pathPickerVisible = ref(false)
const pathPickerLoading = ref(false)
const pathPickerPath = ref('/')
const pathPickerParent = ref(null)
const pathPickerEntries = ref([])
const visible = ref(false)
const editingId = ref(null)
const formRef = ref()
const filters = reactive({ keyword: '', type: '', environment: '' })
const form = reactive({})
const fallback = {
  environments: { DEV: '开发', TEST: '测试', UAT: '预生产', PROD: '生产', OTHER: '其他' },
  statuses: { ONLINE: '在线', OFFLINE: '离线', UNKNOWN: '未知', MAINTENANCE: '维护中' },
  statusTypes: { ONLINE: 'success', OFFLINE: 'info', UNKNOWN: 'warning', MAINTENANCE: 'primary' },
  typeMaps: {
    hosts: { LINUX: 'Linux', WINDOWS: 'Windows', UNIX: 'Unix', OTHER: '其他' },
    applications: { JAVA: 'Java', NODE: 'Node.js', PYTHON: 'Python', GO: 'Go', DOTNET: '.NET', OTHER: '其他' },
    databases: { DM: '达梦', MYSQL: 'MySQL', ORACLE: 'Oracle', KINGBASE: '人大金仓' },
    credentials: { SSH: 'SSH', DATABASE: '数据库', GIT: 'Git', CICD: 'CI/CD', API_KEY: 'API Key' }
  }
}
const environments = ref({ ...fallback.environments })
const statuses = ref({ ...fallback.statuses })
const statusTypes = ref({ ...fallback.statusTypes })
const typeMaps = ref({ ...fallback.typeMaps })
const envOptions = ref([])
const statusOptions = ref([])
const typeOptions = ref([])
const configs = {
  hosts: { kicker: 'INFRASTRUCTURE ASSETS', title: '主机管理', description: '维护服务器地址、日志路径白名单与 SSH 凭据；指纹可自动获取，启用/探测时会校验连通性。', singular: '主机', codeLabel: '主机编码' },
  applications: { kicker: 'APPLICATION ASSETS', title: '应用管理', description: '登记应用仓库、部署目录、负责人和主部署主机。', singular: '应用', codeLabel: '应用编码' },
  databases: { kicker: 'DATABASE ASSETS', title: '数据库实例', description: '统一维护达梦、MySQL、Oracle 和人大金仓数据库实例。', singular: '数据库实例', codeLabel: '实例编码' },
  credentials: { kicker: 'CREDENTIAL VAULT', title: '凭据管理', description: '集中管理 Agent 使用的访问凭据，密钥加密保存且不会回显。', singular: '凭据', codeLabel: '凭据编码' }
}
const config = computed(() => configs[props.kind])
const types = computed(() => typeMaps.value[props.kind] || {})
const typeDictCode = computed(() => ({
  hosts: 'HOST_OS_TYPE',
  applications: 'APP_TYPE',
  databases: 'DB_TYPE',
  credentials: 'CREDENTIAL_TYPE'
})[props.kind])
const rules = computed(() => ({
  code: [{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: '使用大写字母、数字和下划线', trigger: 'blur' }],
  name: [{ required: true, message: `请输入${config.value.singular}名称`, trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  ipAddress: props.kind === 'hosts' ? [{ required: true, message: '请输入 IP 地址或主机名', trigger: 'blur' }] : [],
  hostAddress: props.kind === 'databases' ? [{ required: true, message: '请输入数据库地址', trigger: 'blur' }] : [],
  databaseName: props.kind === 'databases' ? [{ required: true, message: '请输入数据库名', trigger: 'blur' }] : [],
  username: props.kind === 'databases' ? [{ required: true, message: '请输入登录账号', trigger: 'blur' }] : [],
  environment: ['hosts', 'databases'].includes(props.kind) ? [{ required: true, message: '请选择环境', trigger: 'change' }] : [],
  secret: props.kind === 'credentials' && !editingId.value
    ? [{ required: true, message: '请输入密钥', trigger: 'blur' }]
    : (props.kind === 'databases' && !editingId.value ? [{ required: true, message: '请输入登录密码', trigger: 'blur' }] : [])
}))
const filtered = computed(() => items.value.filter(item => {
  const keyword = filters.keyword.trim().toLowerCase()
  const itemType = item.type || item.osType
  return (!keyword || item.name.toLowerCase().includes(keyword) || item.code.toLowerCase().includes(keyword)) &&
    (!filters.type || itemType === filters.type) && (!filters.environment || item.environment === filters.environment)
}))
const { page, pageSize, total, paged, onSizeChange } = useTablePagination(filtered)
const stats = computed(() => ({
  total: items.value.length,
  enabled: items.value.filter(item => item.enabled).length,
  production: items.value.filter(item => item.environment === 'PROD').length,
  linked: props.kind === 'credentials'
    ? items.value.filter(item => item.secretConfigured).length
    : items.value.filter(item => item.credentialId || item.hostId || item.username || item.secretConfigured).length
}))

function defaults() {
  if (props.kind === 'hosts') {
    return {
      code: '', name: '', ipAddress: '', sshPort: 22, osType: 'LINUX', environment: 'TEST',
      ownerName: '', status: 'UNKNOWN', credentialId: '', description: '', enabled: true,
      allowedLogPaths: [''], hostKeyFingerprints: ['']
    }
  }
  if (props.kind === 'applications') return { code: '', name: '', type: 'JAVA', repositoryUrl: '', deployPath: '', hostId: '', ownerName: '', description: '', enabled: true }
  if (props.kind === 'databases') return { code: '', name: '', type: 'DM', hostAddress: '', port: 5236, databaseName: '', schemaName: '', environment: 'TEST', ownerName: '', username: '', secret: '', status: 'UNKNOWN', description: '', enabled: true }
  return { code: '', name: '', type: 'SSH', username: '', secret: '', description: '', enabled: true }
}

function asList(value) {
  if (Array.isArray(value)) return value.length ? [...value] : ['']
  if (typeof value === 'string' && value.trim()) {
    return value.split('|').map(item => item.trim()).filter(Boolean)
  }
  return ['']
}

function cleanList(values) {
  return (values || []).map(item => String(item || '').trim()).filter(Boolean)
}

async function loadDicts() {
  try {
    const batch = await listDictOptionsBatch(['RESOURCE_ENV', 'RESOURCE_STATUS', typeDictCode.value].filter(Boolean))
    if (batch.RESOURCE_ENV?.length) {
      envOptions.value = batch.RESOURCE_ENV
      environments.value = toLabelMap(batch.RESOURCE_ENV)
    }
    if (batch.RESOURCE_STATUS?.length) {
      statusOptions.value = batch.RESOURCE_STATUS
      statuses.value = toLabelMap(batch.RESOURCE_STATUS)
      statusTypes.value = { ...fallback.statusTypes, ...toCssMap(batch.RESOURCE_STATUS) }
    }
    const typeKey = typeDictCode.value
    if (typeKey && batch[typeKey]?.length) {
      typeOptions.value = batch[typeKey]
      typeMaps.value = { ...typeMaps.value, [props.kind]: toLabelMap(batch[typeKey]) }
    } else {
      typeOptions.value = Object.entries(types.value).map(([code, label]) => ({ code, label, value: code }))
    }
  } catch {
    envOptions.value = Object.entries(environments.value).map(([code, label]) => ({ code, label, value: code }))
    statusOptions.value = Object.entries(statuses.value).map(([code, label]) => ({ code, label, value: code }))
    typeOptions.value = Object.entries(types.value).map(([code, label]) => ({ code, label, value: code }))
  }
}

async function load() {
  loading.value = true
  try {
    await loadDicts()
    items.value = await listResources(props.kind)
    await refreshRelatedOptions()
  } catch (error) { ElMessage.error(error.message) }
  finally { loading.value = false }
}

/** 刷新表单关联下拉（主机页凭据、应用页主机），避免菜单缓存导致选项过期。 */
async function refreshRelatedOptions() {
  if (props.kind === 'applications') {
    hosts.value = await listResources('hosts').catch(() => [])
  }
  if (['hosts', 'databases'].includes(props.kind)) {
    credentials.value = await listResources('credentials').catch(() => [])
  }
}

function resetFilters() { Object.assign(filters, { keyword: '', type: '', environment: '' }) }
async function openCreate() {
  editingId.value = null
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, defaults())
  visible.value = true
  await refreshRelatedOptions()
}
async function openEdit(item) {
  editingId.value = item.id
  Object.keys(form).forEach(key => delete form[key])
  Object.assign(form, defaults(), item, {
    secret: '',
    allowedLogPaths: asList(item.allowedLogPaths),
    hostKeyFingerprints: asList(item.hostKeyFingerprints)
  })
  if (props.kind === 'databases') {
    form.username = item.username || ''
    form.secret = ''
  }
  visible.value = true
  await refreshRelatedOptions()
}

function addListItem(field) {
  if (!Array.isArray(form[field])) form[field] = ['']
  form[field].push('')
}
function removeListItem(field, index) {
  if (!Array.isArray(form[field])) return
  form[field].splice(index, 1)
  if (!form[field].length) form[field].push('')
}

async function loadPathPicker(path = pathPickerPath.value) {
  pathPickerLoading.value = true
  try {
    const result = await listSftp(editingId.value, path || '/')
    pathPickerPath.value = result.path || '/'
    pathPickerParent.value = result.parentPath || null
    pathPickerEntries.value = result.entries || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    pathPickerLoading.value = false
  }
}

async function openPathPicker() {
  if (!editingId.value) {
    ElMessage.warning('请先保存主机并探测连通性，再从服务器选择日志路径')
    return
  }
  const current = items.value.find(item => item.id === editingId.value)
  if (!current?.enabled) {
    ElMessage.warning('请先启用主机，再从服务器选择日志路径')
    return
  }
  pathPickerPath.value = '/'
  pathPickerParent.value = null
  pathPickerEntries.value = []
  pathPickerVisible.value = true
  await loadPathPicker('/')
}

function openPathPickerEntry(entry) {
  if (entry.directory) loadPathPicker(entry.path)
}

function addSelectedLogPath(path) {
  const normalized = String(path || '').trim()
  if (!normalized) return
  const paths = cleanList(form.allowedLogPaths)
  if (!paths.includes(normalized)) paths.push(normalized)
  form.allowedLogPaths = paths.length ? paths : ['']
  pathPickerVisible.value = false
  ElMessage.success(`已添加日志路径：${normalized}`)
}

function formatFileSize(value) {
  const size = Number(value || 0)
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function buildPayload() {
  const base = props.kind === 'hosts'
    ? {
        ...form,
        allowedLogPaths: cleanList(form.allowedLogPaths),
        hostKeyFingerprints: cleanList(form.hostKeyFingerprints)
      }
    : { ...form }
  if (props.kind === 'hosts' || props.kind === 'databases') {
    // 运行状态仅由心跳/连通性探测写入，保存时不提交手工状态。
    delete base.status
    delete base.lastConnectivityAt
  }
  if (props.kind === 'databases') {
    delete base.secretConfigured
    // 编辑留空密码表示不修改。
    if (editingId.value && !String(base.secret || '').trim()) delete base.secret
  }
  if (editingId.value) {
    const current = items.value.find(item => item.id === editingId.value)
    base.enabled = current?.enabled ?? true
  } else {
    base.enabled = true
  }
  return base
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value) await updateResource(props.kind, editingId.value, payload)
    else await createResource(props.kind, payload)
    visible.value = false
    await load()
    ElMessage.success(`${config.value.singular}已保存`)
  } catch (error) { ElMessage.error(error.message) }
  finally { saving.value = false }
}

async function fetchFingerprints() {
  if (!form.ipAddress) {
    ElMessage.warning('请先填写 IP 地址 / 主机名')
    return
  }
  fetchingFingerprint.value = true
  try {
    const result = await fetchHostFingerprint({
      ipAddress: form.ipAddress,
      sshPort: form.sshPort || 22
    })
    const list = Array.isArray(result.fingerprints) ? result.fingerprints.filter(Boolean) : []
    form.hostKeyFingerprints = list.length ? list : ['']
    ElMessage.success(result.message || `已获取 ${list.length} 条指纹`)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    fetchingFingerprint.value = false
  }
}

async function probeConnectivity() {
  if (!editingId.value) {
    ElMessage.warning(props.kind === 'databases' ? '请先保存数据库后再探测连通性' : '请先保存主机后再探测连通性')
    return
  }
  if (props.kind === 'hosts' && !form.credentialId) {
    ElMessage.warning('请先选择 SSH 凭据')
    return
  }
  if (props.kind === 'databases' && !String(form.username || '').trim() && !form.credentialId) {
    ElMessage.warning('请先填写数据库登录账号')
    return
  }
  probing.value = true
  try {
    const result = props.kind === 'databases'
      ? await checkDatabaseConnectivity(editingId.value)
      : await checkHostConnectivity(editingId.value)
    ElMessage.success(result.message || '连通性探测成功')
    await load()
    const latest = items.value.find(item => item.id === editingId.value)
    if (latest) openEdit(latest)
  } catch (error) { ElMessage.error(error.message) }
  finally { probing.value = false }
}

async function toggle(item, enabled) {
  try { Object.assign(item, await setResourceEnabled(props.kind, item.id, enabled)); ElMessage.success(enabled ? '已启用' : '已停用') }
  catch (error) { item.enabled = !enabled; ElMessage.error(error.message) }
}
function databaseTypeChanged(type) { form.port = { DM: 5236, MYSQL: 3306, ORACLE: 1521, KINGBASE: 54321 }[type] }
function credentialName(id) { return credentials.value.find(item => item.id === id)?.name || '未关联' }
function pathSummary(row) {
  const paths = asList(row.allowedLogPaths).filter(Boolean)
  return paths.length ? `${paths.length} 条路径` : '未配置'
}
function fingerprintSummary(row) {
  const items = asList(row.hostKeyFingerprints).filter(Boolean)
  return items.length ? `${items.length} 条指纹` : '未配置'
}
function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}
watch(() => props.kind, () => { resetFilters(); load() })
onMounted(load)
</script>

<template>
  <section class="management-page resource-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">{{ config.kicker }}</span>
          <h2>{{ config.title }}</h2>
          <p>{{ config.description }}</p>
        </div>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增{{ config.singular }}</el-button>
      </div>
      <div class="agent-metrics">
        <div><span>资源总数</span><strong>{{ stats.total }}</strong></div>
        <div><span>已启用</span><strong class="metric-success">{{ stats.enabled }}</strong></div>
        <div><span>{{ kind === 'credentials' ? '已配置密钥' : '生产环境' }}</span><strong class="metric-primary">{{ kind === 'credentials' ? stats.linked : stats.production }}</strong></div>
        <div><span>{{ ['applications'].includes(kind) ? '已关联主机' : ['hosts', 'databases'].includes(kind) ? '已关联凭据' : '已停用' }}</span><strong>{{ kind === 'credentials' ? stats.total - stats.enabled : stats.linked }}</strong></div>
      </div>
    </div>
    <div class="management-section page-fixed-body">
      <el-form inline :class="['query-form', kind === 'applications' || kind === 'credentials' ? 'compact-query' : 'resource-query']">
        <el-form-item label="关键词"><el-input v-model="filters.keyword" clearable :prefix-icon="Search" :placeholder="`${config.singular}名称或编码`" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="filters.type" clearable placeholder="全部类型"><el-option v-for="(label, value) in types" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item v-if="['hosts', 'databases'].includes(kind)" label="环境"><el-select v-model="filters.environment" clearable placeholder="全部环境"><el-option v-for="(label, value) in environments" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item class="query-actions"><el-button type="primary" :icon="Search">查询</el-button><el-button :icon="Refresh" @click="resetFilters">重置</el-button><el-button :icon="Refresh" @click="load">刷新</el-button></el-form-item>
      </el-form>
      <div class="table-heading"><div><h3>{{ config.singular }}清单</h3><span>共 {{ total }} 条记录</span></div></div>
      <div class="page-table-scroll">
        <el-table v-loading="loading" :data="paged" border stripe row-key="id" height="100%" empty-text="暂无资源">
          <el-table-column :label="config.singular" min-width="220">
            <template #default="{ row }"><div class="agent-identity"><strong>{{ row.name }}</strong><code>{{ row.code }}</code><span>{{ row.description || '暂无说明' }}</span></div></template>
          </el-table-column>
          <template v-if="kind === 'hosts'">
            <el-table-column label="地址" min-width="160"><template #default="{ row }"><code>{{ row.ipAddress }}:{{ row.sshPort }}</code></template></el-table-column>
            <el-table-column label="系统" width="100"><template #default="{ row }">{{ types[row.osType] }}</template></el-table-column>
            <el-table-column label="环境" width="90"><template #default="{ row }">{{ environments[row.environment] }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusTypes[row.status]">{{ statuses[row.status] }}</el-tag></template></el-table-column>
            <el-table-column label="最近探测" width="150"><template #default="{ row }">{{ formatTime(row.lastConnectivityAt) }}</template></el-table-column>
            <el-table-column label="日志路径" width="100"><template #default="{ row }">{{ pathSummary(row) }}</template></el-table-column>
            <el-table-column label="指纹" width="90"><template #default="{ row }">{{ fingerprintSummary(row) }}</template></el-table-column>
            <el-table-column label="凭据" min-width="130"><template #default="{ row }">{{ credentialName(row.credentialId) }}</template></el-table-column>
          </template>
          <template v-else-if="kind === 'applications'">
            <el-table-column label="类型" width="100"><template #default="{ row }">{{ types[row.type] }}</template></el-table-column>
            <el-table-column prop="hostName" label="主部署主机" min-width="150"><template #default="{ row }">{{ row.hostName || '未关联' }}</template></el-table-column>
            <el-table-column prop="deployPath" label="部署目录" min-width="170" show-overflow-tooltip />
            <el-table-column prop="ownerName" label="负责人" width="120" />
          </template>
          <template v-else-if="kind === 'databases'">
            <el-table-column label="数据库" width="110"><template #default="{ row }">{{ types[row.type] }}</template></el-table-column>
            <el-table-column label="连接地址" min-width="180"><template #default="{ row }"><code>{{ row.hostAddress }}:{{ row.port }}</code></template></el-table-column>
            <el-table-column label="库 / Schema" min-width="150"><template #default="{ row }">{{ row.databaseName }}<small class="table-secondary">{{ row.schemaName || '-' }}</small></template></el-table-column>
            <el-table-column label="环境" width="90"><template #default="{ row }">{{ environments[row.environment] }}</template></el-table-column>
            <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusTypes[row.status]">{{ statuses[row.status] }}</el-tag></template></el-table-column>
            <el-table-column label="最近探测" width="150"><template #default="{ row }">{{ formatTime(row.lastConnectivityAt) }}</template></el-table-column>
            <el-table-column label="登录账号" min-width="120"><template #default="{ row }">{{ row.username || credentialName(row.credentialId) }}</template></el-table-column>
          </template>
          <template v-else>
            <el-table-column label="类型" width="110"><template #default="{ row }">{{ types[row.type] }}</template></el-table-column>
            <el-table-column prop="username" label="登录账号" min-width="150" />
            <el-table-column label="密钥" width="110"><template #default="{ row }"><el-tag :type="row.secretConfigured ? 'success' : 'danger'">{{ row.secretConfigured ? '已配置' : '未配置' }}</el-tag></template></el-table-column>
            <el-table-column label="更新时间" min-width="170" align="right">
              <template #default="{ row }"><span class="time-cell">{{ formatTime(row.updatedAt) }}</span></template>
            </el-table-column>
          </template>
          <el-table-column label="启用" width="88"><template #default="{ row }"><el-switch v-model="row.enabled" @change="value => toggle(row, value)" /></template></el-table-column>
          <el-table-column label="操作" width="95" fixed="right"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button></template></el-table-column>
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

    <el-dialog v-model="visible" :title="`${editingId ? '编辑' : '新增'}${config.singular}`" width="min(820px, 94vw)" destroy-on-close>
      <p v-if="kind === 'credentials'" class="editor-hint">密钥将加密保存且不会回显；编辑时留空表示保留原密钥。</p>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="editor-form">
        <el-row :gutter="18">
          <el-col :xs="24" :sm="12">
            <el-form-item :label="config.codeLabel" prop="code">
              <el-input v-model="form.code" :disabled="!!editingId" placeholder="使用大写字母和下划线" />
              <p class="form-item-desc">资源唯一编码，建议大写字母与下划线，创建后不可改。</p>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12">
            <el-form-item :label="`${config.singular}名称`" prop="name">
              <el-input v-model="form.name" />
              <p class="form-item-desc">列表与运维任务中展示的名称。</p>
            </el-form-item>
          </el-col>

          <template v-if="kind === 'hosts'">
            <el-col :xs="24" :sm="12">
              <el-form-item label="IP 地址 / 主机名" prop="ipAddress">
                <el-input v-model="form.ipAddress" />
                <p class="form-item-desc">SSH 连接目标，可为 IP 或可解析主机名。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="SSH 端口">
                <el-input-number v-model="form.sshPort" :min="1" :max="65535" />
                <p class="form-item-desc">默认 22，按实际 sshd 监听端口填写。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="操作系统" prop="osType">
                <el-select v-model="form.osType" class="full-width"><el-option v-for="(label, value) in types" :key="value" :label="label" :value="value" /></el-select>
                <p class="form-item-desc">影响命令与路径习惯，请按真实系统选择。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="环境" prop="environment">
                <el-select v-model="form.environment" class="full-width"><el-option v-for="(label, value) in environments" :key="value" :label="label" :value="value" /></el-select>
                <p class="form-item-desc">生产环境操作通常需更严格审批。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="SSH 凭据">
                <el-select v-model="form.credentialId" clearable class="full-width"><el-option v-for="item in credentials" :key="item.id" :label="item.name" :value="item.id" /></el-select>
                <p class="form-item-desc">在「凭据」中预先配置的账号/密钥。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="运行状态（心跳）">
                <div class="host-status-readonly">
                  <el-tag :type="statusTypes[form.status] || 'info'">{{ statuses[form.status] || form.status || '未知' }}</el-tag>
                  <span class="form-item-desc" style="margin: 0;">最近探测：{{ formatTime(form.lastConnectivityAt) }}</span>
                </div>
                <p class="form-item-desc">由探测连通性或启用时的 SSH 心跳写入，不可手工改。</p>
              </el-form-item>
            </el-col>

            <el-col :span="24">
              <el-form-item label="SSH 主机密钥指纹">
                <div class="list-editor">
                  <p class="form-item-desc">选择凭据后可自动获取；探测/启用时也会补齐。一般无需手工填写。</p>
                  <div class="path-row" style="margin-bottom: 8px;">
                    <el-button type="primary" plain :loading="fetchingFingerprint" @click="fetchFingerprints">自动获取指纹</el-button>
                    <el-button :icon="Plus" @click="addListItem('hostKeyFingerprints')">手工添加</el-button>
                  </div>
                  <div v-for="(_, index) in form.hostKeyFingerprints" :key="`fp-${index}`" class="list-editor-row">
                    <el-input v-model="form.hostKeyFingerprints[index]" placeholder="自动获取后显示 SHA256:...." />
                    <el-button :icon="Delete" @click="removeListItem('hostKeyFingerprints', index)" />
                  </div>
                </div>
              </el-form-item>
            </el-col>

            <el-col :span="24">
              <el-form-item label="允许读取的日志路径（白名单列表）">
                <div class="list-editor">
                  <p class="form-item-desc">可填日志<strong>文件</strong>或<strong>目录</strong>绝对路径。分日滚动（如 <code>app_log.2026-07-28.log</code>）配目录即可；拉取时优先当日，否则最新 <code>*.log</code>。任务选 APP_LOG_FILE 时路径须命中本列表。</p>
                  <div v-for="(_, index) in form.allowedLogPaths" :key="`path-${index}`" class="list-editor-row">
                    <el-input v-model="form.allowedLogPaths[index]" placeholder="/opt/app/logs 或 /opt/app/logs/app.log" />
                    <el-button :icon="Delete" @click="removeListItem('allowedLogPaths', index)" />
                  </div>
                  <div class="path-actions">
                    <el-button type="primary" plain :icon="Folder" @click="openPathPicker">从服务器选择</el-button>
                    <el-button :icon="Plus" @click="addListItem('allowedLogPaths')">手动添加路径</el-button>
                  </div>
                  <p v-if="!editingId" class="form-item-desc path-picker-tip">新建主机需先保存并探测连通性，之后编辑即可浏览服务器目录。</p>
                </div>
              </el-form-item>
            </el-col>
          </template>

          <template v-else-if="kind === 'applications'">
            <el-col :xs="24" :sm="12">
              <el-form-item label="应用类型" prop="type">
                <el-select v-model="form.type" class="full-width"><el-option v-for="(label, value) in types" :key="value" :label="label" :value="value" /></el-select>
                <p class="form-item-desc">应用技术栈或形态分类。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="主部署主机">
                <el-select v-model="form.hostId" clearable class="full-width"><el-option v-for="item in hosts" :key="item.id" :label="`${item.name} (${item.ipAddress})`" :value="item.id" /></el-select>
                <p class="form-item-desc">主运行主机，便于关联运维任务。</p>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="代码仓库">
                <el-input v-model="form.repositoryUrl" />
                <p class="form-item-desc">可选，Git 仓库地址便于追溯。</p>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="部署目录">
                <el-input v-model="form.deployPath" />
                <p class="form-item-desc">服务器上应用安装/发布的根目录。</p>
              </el-form-item>
            </el-col>
          </template>
          <template v-else-if="kind === 'databases'">
            <el-col :xs="24" :sm="12">
              <el-form-item label="数据库类型" prop="type">
                <el-select v-model="form.type" class="full-width" @change="databaseTypeChanged"><el-option v-for="(label, value) in types" :key="value" :label="label" :value="value" /></el-select>
                <p class="form-item-desc">决定 JDBC 驱动与默认端口。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="环境" prop="environment">
                <el-select v-model="form.environment" class="full-width"><el-option v-for="(label, value) in environments" :key="value" :label="label" :value="value" /></el-select>
                <p class="form-item-desc">区分开发/测试/生产，影响审批策略。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="16">
              <el-form-item label="主机地址" prop="hostAddress">
                <el-input v-model="form.hostAddress" />
                <p class="form-item-desc">数据库服务 IP 或主机名。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="8">
              <el-form-item label="端口">
                <el-input-number v-model="form.port" :min="1" :max="65535" />
                <p class="form-item-desc">切换类型时会带出常见默认端口。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="数据库名" prop="databaseName">
                <el-input v-model="form.databaseName" />
                <p class="form-item-desc">连接串中的库名 / SID 等。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="Schema">
                <el-input v-model="form.schemaName" />
                <p class="form-item-desc">可选；部分库用 schema 区分对象空间。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="登录账号" prop="username">
                <el-input v-model="form.username" placeholder="数据库登录用户" autocomplete="username" />
                <p class="form-item-desc">只读账号更安全；按最小权限原则配置。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item :label="editingId ? '登录密码（留空不修改）' : '登录密码'" prop="secret">
                <el-input v-model="form.secret" type="password" show-password :placeholder="editingId && form.secretConfigured ? '已配置，留空则保留原密码' : '数据库登录密码'" autocomplete="new-password" />
                <p class="form-item-desc">加密保存不回显；编辑时留空表示不改密码。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="运行状态（心跳）">
                <div class="host-status-readonly">
                  <el-tag :type="statusTypes[form.status] || 'info'">{{ statuses[form.status] || form.status || '未知' }}</el-tag>
                  <span class="form-item-desc" style="margin: 0;">最近探测：{{ formatTime(form.lastConnectivityAt) }}</span>
                </div>
                <p class="form-item-desc">由探测连通性或启用时的 JDBC 心跳写入，不可手工改。</p>
              </el-form-item>
            </el-col>
          </template>
          <template v-else>
            <el-col :xs="24" :sm="12">
              <el-form-item label="凭据类型" prop="type">
                <el-select v-model="form.type" class="full-width"><el-option v-for="(label, value) in types" :key="value" :label="label" :value="value" /></el-select>
                <p class="form-item-desc">密码或私钥等类型，决定校验方式。</p>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="登录账号">
                <el-input v-model="form.username" />
                <p class="form-item-desc">SSH/系统登录用户名。</p>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item :label="editingId ? '新密钥（留空则不修改）' : '密钥'" prop="secret">
                <el-input v-model="form.secret" type="password" show-password autocomplete="new-password" />
                <p class="form-item-desc">密码或私钥内容；加密保存，编辑留空则保留原值。</p>
              </el-form-item>
            </el-col>
          </template>

          <el-col v-if="kind !== 'credentials'" :xs="24" :sm="12">
            <el-form-item label="负责人">
              <el-input v-model="form.ownerName" />
              <p class="form-item-desc">资产对接人，便于通知与追责。</p>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="说明">
              <el-input v-model="form.description" type="textarea" :rows="2" />
              <p class="form-item-desc">可选备注，如用途、机房、注意事项。</p>
            </el-form-item>
          </el-col>
          <el-col v-if="kind === 'hosts'" :span="24"><p class="editor-hint">新建默认启用、状态为「未知」。填写 IP/端口并选择 SSH 凭据即可；指纹可点「自动获取」，或在启用/探测连通性时自动补齐。</p></el-col>
          <el-col v-if="kind === 'databases'" :span="24"><p class="editor-hint">新建默认启用、状态为「未知」。填写地址、库名、账号密码后即可保存并「探测连通性」；启用实例时会强制 JDBC 心跳探测。密码加密保存，不会回显。</p></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button v-if="kind === 'hosts' || kind === 'databases'" :loading="probing" @click="probeConnectivity">探测连通性</el-button>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="pathPickerVisible"
      title="从服务器选择日志路径"
      width="min(760px, 94vw)"
      append-to-body
      destroy-on-close
    >
      <div class="path-picker-toolbar">
        <el-button :icon="ArrowLeft" :disabled="!pathPickerParent" @click="loadPathPicker(pathPickerParent)">上级</el-button>
        <el-button :icon="Refresh" :loading="pathPickerLoading" @click="loadPathPicker()">刷新</el-button>
        <span>当前路径：<code>{{ pathPickerPath }}</code></span>
      </div>
      <el-table
        v-loading="pathPickerLoading"
        :data="pathPickerEntries"
        height="380"
        border
        empty-text="当前目录为空或无权访问"
        @row-dblclick="openPathPickerEntry"
      >
        <el-table-column label="名称" min-width="280">
          <template #default="{ row }">
            <el-button v-if="row.directory" link type="primary" :icon="Folder" @click="loadPathPicker(row.path)">{{ row.name }}</el-button>
            <span v-else>{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.directory ? '目录' : '文件' }}</template>
        </el-table-column>
        <el-table-column label="大小" width="110">
          <template #default="{ row }">{{ row.directory ? '—' : formatFileSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="addSelectedLogPath(row.path)">选择</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="pathPickerVisible = false">取消</el-button>
        <el-button type="primary" @click="addSelectedLogPath(pathPickerPath)">选择当前目录</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.host-status-readonly {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  min-height: 32px;
}
.host-fingerprint-guide { margin-bottom: 8px; }
.host-fingerprint-guide pre {
  margin: 8px 0;
  padding: 10px 12px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 12px;
}
.list-editor { display: flex; flex-direction: column; gap: 10px; width: 100%; }
.list-editor-row { display: flex; gap: 8px; align-items: center; }
.list-editor-row .el-input { flex: 1; }
.path-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.path-picker-tip { margin: -2px 0 0; }
.path-picker-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.path-picker-toolbar span {
  min-width: 0;
  overflow-wrap: anywhere;
}
</style>

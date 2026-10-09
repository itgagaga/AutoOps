<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, CopyDocument, Delete, Document, FullScreen, Grid, Plus, Refresh, Search, VideoPlay } from '@element-plus/icons-vue'
import {
  getDbTableDdl,
  listDbSchemas,
  listDbTables,
  listResources,
  pingDatabase,
  previewDbTable,
  queryDatabase
} from '../api'

const TYPE_LABELS = {
  DM: '达梦',
  MYSQL: 'MySQL',
  ORACLE: 'Oracle',
  KINGBASE: '金仓'
}

const instances = ref([])
const loading = ref(false)
const pinging = ref(false)
const executing = ref(false)
const treeLoading = ref(false)
const selectedId = ref('')
const sqlText = ref('SELECT 1')
const statusText = ref('未探测')
const statusTone = ref('idle')
const filters = reactive({ keyword: '' })
const lastResult = ref(null)
const workspaceTab = ref('sql')
const treeData = ref([])
const treeProps = { label: 'label', children: 'children', isLeaf: 'leaf' }
const currentSchema = ref('')
const currentTable = ref('')
const ddlText = ref('')
const ddlLoading = ref(false)
const dataLoading = ref(false)
const previewResult = ref(null)
const workspaceFullscreen = ref(false)
const selectedRows = ref([])
const queryConditions = ref([])
const orderBy = ref('')
const orderDirection = ref('ASC')
const previewLimit = ref(100)

const enabledInstances = computed(() => instances.value.filter(item => item.enabled))
const filteredInstances = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return enabledInstances.value.filter(item => {
    if (!keyword) return true
    return [item.name, item.code, item.hostAddress, item.type, item.databaseName, item.environment]
      .filter(Boolean).join(' ').toLowerCase().includes(keyword)
  })
})
const selected = computed(() => enabledInstances.value.find(item => item.id === selectedId.value) || null)

function hasAuth(item) {
  return !!(item?.username || item?.secretConfigured || item?.credentialId)
}

const stats = computed(() => ({
  total: instances.value.length,
  enabled: enabledInstances.value.length,
  withAuth: instances.value.filter(item => hasAuth(item)).length
}))

const activeResult = computed(() => (workspaceTab.value === 'data' ? previewResult.value : lastResult.value))
const resultColumns = computed(() => activeResult.value?.columns || [])
const resultTableData = computed(() => {
  const columns = resultColumns.value
  const rows = activeResult.value?.rows || []
  return rows.map((row, index) => {
    const record = { __rowKey: index, __raw: row }
    columns.forEach((col, colIndex) => {
      const value = Array.isArray(row) ? row[colIndex] : row?.[col]
      record[col] = formatCell(value)
    })
    return record
  })
})

const objectPath = computed(() => {
  if (!currentSchema.value) return ''
  return currentTable.value ? `${currentSchema.value}.${currentTable.value}` : currentSchema.value
})

function typeLabel(type) {
  return TYPE_LABELS[type] || type || '—'
}

function formatCell(value) {
  if (value === null || value === undefined) return 'NULL'
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    } catch {
      return String(value)
    }
  }
  return String(value)
}

const CONDITION_OPERATORS = ['=', '!=', '>', '>=', '<', '<=', 'LIKE', 'IS NULL', 'IS NOT NULL']

function addCondition(column = '') {
  queryConditions.value.push({ column: column || resultColumns.value[0] || '', operator: '=', value: '' })
}

function removeCondition(index) {
  queryConditions.value.splice(index, 1)
}

function sqlLiteral(value) {
  if (value === null || value === undefined) return 'NULL'
  if (typeof value === 'number' || typeof value === 'bigint') return String(value)
  if (typeof value === 'boolean') return value ? '1' : '0'
  return `'${String(value).replaceAll("'", "''")}'`
}

function buildDataQuery() {
  const type = selected.value?.type || 'DM'
  const table = `${quoteIdent(type, currentSchema.value)}.${quoteIdent(type, currentTable.value)}`
  const clauses = queryConditions.value
    .filter(item => item.column && item.operator)
    .map(item => {
      const column = quoteIdent(type, item.column)
      if (item.operator === 'IS NULL' || item.operator === 'IS NOT NULL') return `${column} ${item.operator}`
      return `${column} ${item.operator} ${sqlLiteral(item.value)}`
    })
  let sql = `SELECT * FROM ${table}`
  if (clauses.length) sql += ` WHERE ${clauses.join(' AND ')}`
  if (orderBy.value) sql += ` ORDER BY ${quoteIdent(type, orderBy.value)} ${orderDirection.value}`
  const limit = Math.max(1, Math.min(500, Number(previewLimit.value) || 100))
  if (type === 'MYSQL' || type === 'KINGBASE') return `${sql} LIMIT ${limit}`
  return `SELECT * FROM (${sql}) WHERE ROWNUM <= ${limit}`
}

async function queryTableData() {
  if (!currentTable.value) return
  dataLoading.value = true
  try {
    const sql = buildDataQuery()
    const primaryKeys = previewResult.value?.primaryKeys || []
    const result = await queryDatabase(selectedId.value, sql)
    result.sql = sql
    result.primaryKeys = primaryKeys
    previewResult.value = result
    selectedRows.value = []
    setStatus(previewResult.value.message || '条件查询完成', previewResult.value.truncated ? 'warn' : 'ok')
  } catch (error) {
    setStatus(error.message || '条件查询失败', 'error')
    ElMessage.error(statusText.value)
  } finally {
    dataLoading.value = false
  }
}

async function resetAndLoadPreview() {
  queryConditions.value = []
  orderBy.value = ''
  orderDirection.value = 'ASC'
  previewLimit.value = 100
  selectedRows.value = []
  await loadPreview()
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

function copyText(text, message) {
  if (!text) return
  navigator.clipboard.writeText(text)
    .then(() => ElMessage.success(message))
    .catch(() => ElMessage.error('复制失败，请检查浏览器剪贴板权限'))
}

function selectedRawRows() {
  const source = selectedRows.value.length ? selectedRows.value : resultTableData.value
  return source.map(record => record.__raw)
}

function generateInsertSql() {
  const rows = selectedRawRows()
  if (!rows.length) return ElMessage.warning('没有可复制的数据')
  const type = selected.value?.type || 'DM'
  const table = `${quoteIdent(type, currentSchema.value)}.${quoteIdent(type, currentTable.value)}`
  const columns = resultColumns.value.map(col => quoteIdent(type, col)).join(', ')
  const statements = rows.map(row => {
    const values = resultColumns.value.map((_, index) => sqlLiteral(Array.isArray(row) ? row[index] : row?.[resultColumns.value[index]]))
    return `INSERT INTO ${table} (${columns}) VALUES (${values.join(', ')});`
  })
  copyText(statements.join('\n'), `已复制 ${rows.length} 条 INSERT`)
}

function generateUpdateSql() {
  const rows = selectedRawRows()
  if (!rows.length) return ElMessage.warning('没有可复制的数据')
  const type = selected.value?.type || 'DM'
  const table = `${quoteIdent(type, currentSchema.value)}.${quoteIdent(type, currentTable.value)}`
  const keys = previewResult.value?.primaryKeys?.length ? previewResult.value.primaryKeys : resultColumns.value
  const statements = rows.map(row => {
    const valueAt = col => {
      const index = resultColumns.value.indexOf(col)
      return Array.isArray(row) ? row[index] : row?.[col]
    }
    const setters = resultColumns.value.filter(col => !keys.includes(col))
      .map(col => `${quoteIdent(type, col)} = ${sqlLiteral(valueAt(col))}`)
    const effectiveSetters = setters.length ? setters : resultColumns.value.map(col => `${quoteIdent(type, col)} = ${sqlLiteral(valueAt(col))}`)
    const where = keys.map(col => {
      const value = valueAt(col)
      return value == null ? `${quoteIdent(type, col)} IS NULL` : `${quoteIdent(type, col)} = ${sqlLiteral(value)}`
    })
    return `UPDATE ${table} SET ${effectiveSetters.join(', ')} WHERE ${where.join(' AND ')};`
  })
  copyText(statements.join('\n'), `已复制 ${rows.length} 条 UPDATE`)
}

function copyTabularData() {
  const rows = selectedRawRows()
  if (!rows.length) return ElMessage.warning('没有可复制的数据')
  const lines = [resultColumns.value.join('\t'), ...rows.map(row =>
    resultColumns.value.map((col, index) => formatCell(Array.isArray(row) ? row[index] : row?.[col])).join('\t')
  )]
  copyText(lines.join('\n'), `已复制 ${rows.length} 行数据`)
}

function isLikelyWrite(sql) {
  const text = String(sql || '').trim()
  const cleaned = text.replace(/^(\s|--[^\n]*\n|\/\*[\s\S]*?\*\/)*/g, '')
  const match = cleaned.match(/^([A-Za-z_]+)/)
  const kw = (match?.[1] || '').toUpperCase()
  return !['SELECT', 'SHOW', 'DESC', 'DESCRIBE', 'EXPLAIN', 'WITH', 'VALUES', 'TABLE', 'CALL'].includes(kw)
}

function setStatus(text, tone = 'idle') {
  statusText.value = text
  statusTone.value = tone
}

function quoteIdent(type, ident) {
  if (type === 'MYSQL') return `\`${ident.replaceAll('`', '``')}\``
  if (type === 'KINGBASE') return `"${ident.replaceAll('"', '""')}"`
  return ident
}

async function loadInstances() {
  loading.value = true
  try {
    instances.value = await listResources('databases')
    if (selectedId.value && !enabledInstances.value.some(item => item.id === selectedId.value)) {
      selectedId.value = ''
      resetBrowser()
      setStatus('未探测', 'idle')
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

function resetBrowser() {
  treeData.value = []
  currentSchema.value = ''
  currentTable.value = ''
  ddlText.value = ''
  previewResult.value = null
  selectedRows.value = []
  queryConditions.value = []
  orderBy.value = ''
  lastResult.value = null
  workspaceTab.value = 'sql'
}

async function selectInstance(item) {
  selectedId.value = item.id
  resetBrowser()
  setStatus('未探测', 'idle')
  if (!sqlText.value.trim()) sqlText.value = 'SELECT 1'
  if (hasAuth(item)) {
    await loadSchemaTree()
  }
}

async function loadSchemaTree() {
  if (!selectedId.value || !hasAuth(selected.value)) return
  treeLoading.value = true
  try {
    const data = await listDbSchemas(selectedId.value)
    const preferred = (data.preferredSchema || '').toUpperCase()
    treeData.value = (data.schemas || []).map(name => ({
      id: `schema:${name}`,
      label: name,
      kind: 'schema',
      schema: name,
      leaf: false,
      preferred: preferred && name.toUpperCase() === preferred
    }))
    setStatus(`已加载 ${treeData.value.length} 个 Schema`, 'ok')
  } catch (error) {
    treeData.value = []
    setStatus(error.message || '加载 Schema 失败', 'error')
    ElMessage.error(statusText.value)
  } finally {
    treeLoading.value = false
  }
}

async function loadNode(node, resolve) {
  if (node.level === 0) {
    resolve(treeData.value)
    return
  }
  const data = node.data
  if (data.kind !== 'schema') {
    resolve([])
    return
  }
  try {
    const result = await listDbTables(selectedId.value, data.schema)
    resolve((result.tables || []).map(item => ({
      id: `table:${data.schema}.${item.name}`,
      label: item.name,
      kind: 'table',
      schema: data.schema,
      table: item.name,
      objectType: item.type || 'TABLE',
      leaf: true
    })))
  } catch (error) {
    ElMessage.error(error.message || '加载表失败')
    resolve([])
  }
}

async function onTreeClick(data) {
  if (!data) return
  if (data.kind === 'schema') {
    currentSchema.value = data.schema
    currentTable.value = ''
    ddlText.value = ''
    previewResult.value = null
    return
  }
  if (data.kind === 'table') {
    currentSchema.value = data.schema
    currentTable.value = data.table
    queryConditions.value = []
    orderBy.value = ''
    selectedRows.value = []
    const type = selected.value?.type || 'DM'
    sqlText.value = `SELECT * FROM ${quoteIdent(type, data.schema)}.${quoteIdent(type, data.table)} WHERE ROWNUM <= 100`
    if (type === 'MYSQL' || type === 'KINGBASE') {
      sqlText.value = `SELECT * FROM ${quoteIdent(type, data.schema)}.${quoteIdent(type, data.table)} LIMIT 100`
    }
    workspaceTab.value = 'data'
    await Promise.all([loadDdl(), loadPreview()])
  }
}

async function loadDdl() {
  if (!selectedId.value || !currentSchema.value || !currentTable.value) return
  ddlLoading.value = true
  try {
    const data = await getDbTableDdl(selectedId.value, currentSchema.value, currentTable.value)
    ddlText.value = data.ddl || ''
  } catch (error) {
    ddlText.value = ''
    ElMessage.error(error.message || '读取 DDL 失败')
  } finally {
    ddlLoading.value = false
  }
}

async function loadPreview() {
  if (!selectedId.value || !currentSchema.value || !currentTable.value) return
  dataLoading.value = true
  try {
    previewResult.value = await previewDbTable(selectedId.value, currentSchema.value, currentTable.value, 100)
    setStatus(previewResult.value.message || '数据预览完成', previewResult.value.truncated ? 'warn' : 'ok')
  } catch (error) {
    previewResult.value = null
    setStatus(error.message || '预览失败', 'error')
    ElMessage.error(statusText.value)
  } finally {
    dataLoading.value = false
  }
}

async function pingSelected() {
  if (!selectedId.value) {
    ElMessage.warning('请先选择数据库实例')
    return
  }
  if (!hasAuth(selected.value)) {
    ElMessage.warning('该实例未配置登录账号密码')
    return
  }
  pinging.value = true
  setStatus('探测中…', 'idle')
  try {
    const data = await pingDatabase(selectedId.value)
    setStatus(data.message || '连通正常', 'ok')
    ElMessage.success(`${statusText.value}（${data.elapsedMs ?? 0} ms）`)
    await loadSchemaTree()
  } catch (error) {
    setStatus(error.message || '连通失败', 'error')
    ElMessage.error(statusText.value)
  } finally {
    pinging.value = false
  }
}

async function runSql() {
  if (!selectedId.value) {
    ElMessage.warning('请先选择数据库实例')
    return
  }
  if (!hasAuth(selected.value)) {
    ElMessage.warning('该实例未配置登录账号密码')
    return
  }
  const sql = sqlText.value.trim()
  if (!sql) {
    ElMessage.warning('请输入 SQL')
    return
  }
  if (isLikelyWrite(sql)) {
    try {
      await ElMessageBox.confirm(
        '当前语句可能修改数据或结构，确认在目标库执行？',
        '写操作确认',
        { type: 'warning', confirmButtonText: '确认执行', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  executing.value = true
  workspaceTab.value = 'sql'
  try {
    const data = await queryDatabase(selectedId.value, sql)
    lastResult.value = data
    const tone = data.truncated ? 'warn' : 'ok'
    setStatus(data.message || '执行完成', tone)
    if (data.truncated) ElMessage.warning(statusText.value)
    else ElMessage.success(`${statusText.value}（${data.elapsedMs ?? 0} ms）`)
  } catch (error) {
    lastResult.value = null
    setStatus(error.message || '执行失败', 'error')
    ElMessage.error(statusText.value)
  } finally {
    executing.value = false
  }
}

function clearSql() {
  sqlText.value = ''
}

function clearResult() {
  if (workspaceTab.value === 'data') previewResult.value = null
  else lastResult.value = null
}

watch(workspaceTab, (tab) => {
  selectedRows.value = []
  if (tab === 'ddl' && currentTable.value && !ddlText.value) loadDdl()
  if (tab === 'data' && currentTable.value && !previewResult.value) loadPreview()
})

onMounted(loadInstances)
</script>

<template>
  <section class="management-page db-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">DB / SQL</span>
          <h2>数据库终端</h2>
          <p>像 IDE 一样浏览 Schema / 表，查看 DDL 与数据，并切换执行 SQL。</p>
        </div>
        <div class="heading-actions db-heading-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadInstances">刷新实例</el-button>
          <el-button :icon="Connection" :loading="pinging" :disabled="!selectedId" @click="pingSelected">连通性测试</el-button>
          <el-button type="primary" :icon="VideoPlay" :loading="executing" :disabled="!selectedId" @click="runSql">执行 SQL</el-button>
        </div>
      </div>
      <div class="agent-metrics">
        <div><span>实例总数</span><strong>{{ stats.total }}</strong></div>
        <div><span>已启用</span><strong class="metric-success">{{ stats.enabled }}</strong></div>
        <div><span>已配置认证</span><strong class="metric-primary">{{ stats.withAuth }}</strong></div>
        <div><span>当前对象</span><strong :class="objectPath ? 'metric-warning' : ''" :title="objectPath || selected?.name || ''">{{ objectPath || selected?.name || '未选' }}</strong></div>
      </div>
    </div>

    <div class="db-layout page-fixed-scroll">
      <aside class="management-section db-side" v-loading="loading || treeLoading">
        <div class="table-heading">
          <div><h3>实例 / 对象</h3><span>{{ filteredInstances.length }} 实例</span></div>
        </div>
        <el-input v-model="filters.keyword" clearable placeholder="筛选实例" class="instance-search" />
        <div class="instance-list compact">
          <button
            v-for="item in filteredInstances"
            :key="item.id"
            type="button"
            class="instance-list-item"
            :class="{ active: item.id === selectedId }"
            @click="selectInstance(item)"
          >
            <div class="instance-title-row">
              <strong>{{ item.name }}</strong>
              <em class="type-chip">{{ typeLabel(item.type) }}</em>
            </div>
            <code>{{ item.hostAddress }}:{{ item.port }}</code>
          </button>
          <el-empty v-if="!filteredInstances.length" description="暂无已启用实例" :image-size="48" />
        </div>

        <div class="object-browser">
          <div class="object-browser-head">
            <strong>Schema / 表</strong>
            <el-button link type="primary" :disabled="!selectedId || !hasAuth(selected)" @click="loadSchemaTree">刷新</el-button>
          </div>
          <el-tree
            v-if="selectedId && treeData.length"
            class="object-tree"
            lazy
            :load="loadNode"
            :data="treeData"
            :props="treeProps"
            node-key="id"
            highlight-current
            @node-click="onTreeClick"
          >
            <template #default="{ data }">
              <span class="tree-node">
                <em class="tree-kind">{{ data.kind === 'schema' ? 'S' : (data.objectType === 'VIEW' ? 'V' : 'T') }}</em>
                <span>{{ data.label }}</span>
                <small v-if="data.preferred">默认</small>
              </span>
            </template>
          </el-tree>
          <el-empty
            v-else
            :description="selectedId ? (hasAuth(selected) ? '点击连通性测试或刷新加载 Schema' : '请先配置登录账号密码') : '请先选择实例'"
            :image-size="48"
          />
        </div>
      </aside>

      <div class="management-section db-main-panel" :class="{ 'is-workspace-fullscreen': workspaceFullscreen }">
        <div class="table-heading workspace-toolbar">
          <div class="workspace-title">
            <h3>工作区</h3>
            <span v-if="selected" class="instance-name">{{ selected.name }}{{ objectPath ? ` · ${objectPath}` : '' }}</span>
          </div>
          <div class="sql-actions">
            <el-button :icon="FullScreen" @click="workspaceFullscreen = !workspaceFullscreen">
              {{ workspaceFullscreen ? '退出全屏' : '全屏' }}
            </el-button>
            <el-button :icon="Delete" @click="clearSql">清空 SQL</el-button>
            <el-button :disabled="!(workspaceTab === 'data' ? previewResult : lastResult)" @click="clearResult">清空结果</el-button>
          </div>
        </div>

        <div class="status-bar" :class="`tone-${statusTone}`">
          <span class="status-dot" />
          <span class="status-text" :title="statusText">{{ statusText }}</span>
        </div>

        <el-tabs v-model="workspaceTab" class="workspace-tabs">
          <el-tab-pane label="SQL" name="sql">
            <div class="workspace-body">
              <div class="sql-pane">
                <el-input
                  v-model="sqlText"
                  type="textarea"
                  :rows="7"
                  resize="none"
                  class="sql-editor"
                  placeholder="输入单条 SQL，例如 SELECT 1；点击左侧表可自动生成查询"
                  @keydown.ctrl.enter.prevent="runSql"
                />
                <p class="editor-hint">Ctrl + Enter 执行 · 写操作二次确认 · 结果上限约 500 行</p>
              </div>
              <div class="result-pane" v-loading="executing">
                <div class="result-meta" v-if="lastResult">
                  <span>{{ lastResult.message }}</span>
                  <span v-if="lastResult.elapsedMs != null">{{ lastResult.elapsedMs }} ms</span>
                  <span v-if="lastResult.kind">{{ lastResult.kind }}</span>
                  <span v-if="lastResult.updateCount != null">影响 {{ lastResult.updateCount }} 行</span>
                  <span v-if="lastResult.truncated" class="truncated">已截断</span>
                </div>
                <div class="result-wrap">
                  <el-table
                    v-if="lastResult?.hasResultSet"
                    :data="resultTableData"
                    height="100%"
                    border
                    stripe
                    empty-text="无数据"
                    row-key="__rowKey"
                    size="small"
                    @selection-change="onSelectionChange"
                  >
                    <el-table-column type="selection" width="44" fixed="left" />
                    <el-table-column
                      v-for="col in resultColumns"
                      :key="col"
                      :prop="col"
                      :label="col"
                      min-width="120"
                      show-overflow-tooltip
                    />
                  </el-table>
                  <div v-else class="result-empty">
                    <p>{{ lastResult ? (lastResult.message || '无结果集') : '执行 SQL 后在此查看结果' }}</p>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane name="ddl">
            <template #label>
              <span class="tab-label"><el-icon><Document /></el-icon> DDL</span>
            </template>
            <div class="ddl-pane" v-loading="ddlLoading">
              <div class="pane-toolbar" v-if="currentTable">
                <span>{{ currentSchema }}.{{ currentTable }}</span>
                <el-button link type="primary" @click="loadDdl">重新加载</el-button>
              </div>
              <pre v-if="ddlText" class="ddl-view">{{ ddlText }}</pre>
              <el-empty v-else description="在左侧选择表后查看 DDL" :image-size="56" />
            </div>
          </el-tab-pane>

          <el-tab-pane name="data">
            <template #label>
              <span class="tab-label"><el-icon><Grid /></el-icon> 数据</span>
            </template>
            <div class="result-pane data-pane" v-loading="dataLoading">
              <div class="pane-toolbar" v-if="currentTable">
                <span>{{ currentSchema }}.{{ currentTable }}</span>
                <div class="data-toolbar-actions">
                  <el-dropdown split-button type="primary" :icon="CopyDocument" :disabled="!previewResult?.hasResultSet" @click="copyTabularData">
                    复制数据
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item @click="generateInsertSql">复制为 INSERT</el-dropdown-item>
                        <el-dropdown-item @click="generateUpdateSql">复制为 UPDATE</el-dropdown-item>
                        <el-dropdown-item @click="copyTabularData">复制为表格数据</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button link type="primary" @click="resetAndLoadPreview">重置并刷新</el-button>
                </div>
              </div>
              <div class="data-query-builder" v-if="currentTable">
                <div v-for="(condition, index) in queryConditions" :key="index" class="condition-row">
                  <el-select v-model="condition.column" filterable placeholder="字段">
                    <el-option v-for="col in resultColumns" :key="col" :label="col" :value="col" />
                  </el-select>
                  <el-select v-model="condition.operator" class="operator-select">
                    <el-option v-for="operator in CONDITION_OPERATORS" :key="operator" :label="operator" :value="operator" />
                  </el-select>
                  <el-input
                    v-model="condition.value"
                    placeholder="条件值"
                    :disabled="condition.operator === 'IS NULL' || condition.operator === 'IS NOT NULL'"
                    @keydown.enter.prevent="queryTableData"
                  />
                  <el-button text type="danger" @click="removeCondition(index)">删除</el-button>
                </div>
                <div class="query-builder-footer">
                  <el-button :icon="Plus" @click="addCondition()">添加条件</el-button>
                  <span class="query-label">排序</span>
                  <el-select v-model="orderBy" clearable filterable placeholder="排序字段" class="order-column">
                    <el-option v-for="col in resultColumns" :key="col" :label="col" :value="col" />
                  </el-select>
                  <el-select v-model="orderDirection" class="direction-select">
                    <el-option label="升序 ASC" value="ASC" />
                    <el-option label="降序 DESC" value="DESC" />
                  </el-select>
                  <span class="query-label">行数</span>
                  <el-input-number v-model="previewLimit" :min="1" :max="500" :step="50" controls-position="right" />
                  <el-button type="primary" :icon="Search" :loading="dataLoading" @click="queryTableData">查询</el-button>
                </div>
              </div>
              <div class="result-meta" v-if="previewResult">
                <span>{{ previewResult.message }}</span>
                <span v-if="previewResult.rowCount != null">{{ previewResult.rowCount }} 行</span>
                <span v-if="previewResult.truncated" class="truncated">已截断</span>
              </div>
              <div class="result-wrap">
                <el-table
                  v-if="previewResult?.hasResultSet"
                  :data="resultTableData"
                  height="100%"
                  border
                  stripe
                  empty-text="无数据"
                  row-key="__rowKey"
                  size="small"
                  @selection-change="onSelectionChange"
                >
                  <el-table-column type="selection" width="44" fixed="left" />
                  <el-table-column
                    v-for="col in resultColumns"
                    :key="col"
                    :prop="col"
                    :label="col"
                    min-width="120"
                    show-overflow-tooltip
                  />
                </el-table>
                <div v-else class="result-empty">
                  <p>{{ currentTable ? (previewResult?.message || '暂无数据') : '在左侧选择表后预览数据' }}</p>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </section>
</template>

<style scoped>
.db-heading-actions {
  flex-wrap: nowrap;
  flex-shrink: 0;
  align-items: center;
}

.db-page .agent-metrics strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 20px;
  line-height: 1.25;
}

.db-layout {
  display: grid;
  grid-template-columns: minmax(280px, 320px) minmax(0, 1fr);
  gap: 14px;
  align-items: stretch;
  min-height: 0;
}

.db-layout .management-section {
  margin-bottom: 0;
  padding: 16px;
}

.db-side {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.instance-search { margin-bottom: 10px; flex: 0 0 auto; }

.instance-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 0 0 auto;
  max-height: 180px;
  overflow: auto;
  margin-bottom: 12px;
}

.instance-list-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  font: inherit;
  transition: border-color .15s, background .15s;
}

.instance-list-item:hover { border-color: #9fb3c8; }
.instance-list-item.active {
  border-color: #147d92;
  background: #e6f6fb;
}

.instance-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.instance-list-item strong {
  color: #243b53;
  font-size: 13px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-chip {
  flex: 0 0 auto;
  margin: 0;
  padding: 1px 6px;
  border-radius: 3px;
  background: #f0f4f8;
  color: #486581;
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
}

.instance-list-item.active .type-chip {
  background: #d9eef5;
  color: #0b6e84;
}

.instance-list-item code {
  width: fit-content;
  padding: 1px 6px;
  border-radius: 3px;
  background: #f0f4f8;
  color: #147d92;
  font-size: 11px;
}

.object-browser {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border-top: 1px solid #e4e7eb;
  padding-top: 10px;
}

.object-browser-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #243b53;
  font-size: 13px;
}

.object-tree {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  background: transparent;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.tree-kind {
  display: inline-grid;
  place-items: center;
  width: 16px;
  height: 16px;
  border-radius: 3px;
  background: #e6f6fb;
  color: #0b6e84;
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
}

.tree-node small {
  color: #829ab1;
  font-size: 11px;
}

.db-main-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.db-main-panel.is-workspace-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 3000;
  width: 100vw;
  height: 100vh;
  padding: 18px 22px;
  border: 0;
  border-radius: 0;
  background: #fff;
}

.workspace-toolbar { margin-bottom: 8px; gap: 10px; }

.workspace-title {
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.instance-name {
  color: #486581;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sql-actions {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  flex-shrink: 0;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding: 7px 10px;
  border: 1px solid #e4e7eb;
  border-radius: 4px;
  background: #f8fafc;
  min-height: 34px;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #9fb3c8;
  flex: 0 0 auto;
}

.status-text {
  color: #486581;
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-bar.tone-ok { border-color: #b7ebc6; background: #f0fff4; }
.status-bar.tone-ok .status-dot { background: #238636; }
.status-bar.tone-ok .status-text { color: #1b5e2a; }
.status-bar.tone-warn { border-color: #f5d0a9; background: #fff8f1; }
.status-bar.tone-warn .status-dot { background: #c05621; }
.status-bar.tone-warn .status-text { color: #9a3412; }
.status-bar.tone-error { border-color: #f5c2c7; background: #fff5f5; }
.status-bar.tone-error .status-dot { background: #c0392b; }
.status-bar.tone-error .status-text { color: #9b1c1c; }

.workspace-tabs {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.workspace-tabs :deep(.el-tabs__header) { margin-bottom: 8px; }
.workspace-tabs :deep(.el-tabs__content) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}
.workspace-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.workspace-body {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 10px;
  height: 100%;
  min-height: 0;
}

.sql-pane { flex: 0 0 auto; }

.sql-editor :deep(.el-textarea__inner) {
  font-family: Consolas, "Courier New", monospace;
  font-size: 13px;
  line-height: 1.5;
  color: #e2e8f0;
  background: #0f172a;
  border: 1px solid #1e293b;
  box-shadow: none;
  border-radius: 4px;
  padding: 12px 14px;
}

.sql-editor :deep(.el-textarea__inner):focus {
  border-color: #147d92;
  box-shadow: 0 0 0 1px #147d9233;
}

.editor-hint {
  margin: 6px 0 0;
  color: #829ab1;
  font-size: 12px;
}

.result-pane, .ddl-pane, .data-pane {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.pane-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  color: #486581;
  font-size: 12px;
}

.data-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.data-query-builder {
  display: grid;
  gap: 8px;
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #d9e2ec;
  border-radius: 4px;
  background: #f8fafc;
}

.condition-row {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) 120px minmax(180px, 1.5fr) auto;
  gap: 8px;
}

.query-builder-footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.query-label {
  margin-left: 6px;
  color: #627d98;
  font-size: 12px;
}

.order-column { width: 170px; }
.direction-select { width: 118px; }
.operator-select { width: 120px; }

.ddl-view {
  flex: 1 1 auto;
  min-height: 0;
  margin: 0;
  padding: 12px 14px;
  overflow: auto;
  border-radius: 4px;
  background: #0f172a;
  color: #e2e8f0;
  font-family: Consolas, "Courier New", monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.result-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 14px;
  margin-bottom: 8px;
  color: #486581;
  font-size: 12px;
}

.result-meta .truncated { color: #b44d12; font-weight: 600; }

.result-wrap {
  flex: 1 1 auto;
  min-height: 160px;
  border: 1px solid #e4e7eb;
  border-radius: 4px;
  overflow: hidden;
  background: #fff;
}

.result-empty {
  height: 100%;
  min-height: 160px;
  display: grid;
  place-items: center;
  color: #829ab1;
  font-size: 13px;
  background: #fafbfc;
}

.result-empty p { margin: 0; }

@media (max-width: 960px) {
  .db-layout { grid-template-columns: 1fr; }
  .instance-list { max-height: 140px; }
  .object-browser { min-height: 220px; }
  .db-heading-actions { flex-wrap: wrap; width: 100%; }
  .workspace-body { grid-template-rows: auto minmax(180px, 1fr); }
  .condition-row { grid-template-columns: 1fr 110px; }
}
</style>

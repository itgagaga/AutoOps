<!-- @author yangdb -->
<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Download, FolderOpened, Refresh, Search, VideoPlay
} from '@element-plus/icons-vue'
import {
  getGitDefaultWorkspace, listFilesystem, listFilesystemRoots, probeGitWorkspace, queryGit
} from '../api'

const loading = ref(false)
const querying = ref(false)
const probing = ref(false)
const workspacePath = ref('')
const repoRoot = ref('')
const gitReady = ref(false)
const activeTab = ref('STATUS')
const statusText = ref('请选择含 .git 的本地目录')
const statusTone = ref('idle')
const output = ref('')
const elapsedMs = ref(null)
const pathOrRef = ref('')
const maxCommits = ref(30)
const fullDiff = ref(true)

const pickerVisible = ref(false)
const roots = ref([])
const currentPath = ref('')
const parentPath = ref(null)
const children = ref([])
const pickerLoading = ref(false)

const tabs = [
  { name: 'STATUS', label: '状态' },
  { name: 'BRANCH', label: '分支' },
  { name: 'LOG', label: '提交' },
  { name: 'DIFF', label: '差异' },
  { name: 'SHOW', label: '查看提交' }
]

const tabLabel = computed(() => tabs.find(item => item.name === activeTab.value)?.label || activeTab.value)

const repoName = computed(() => {
  const path = (repoRoot.value || workspacePath.value || '').replace(/[\\/]+$/, '')
  if (!path) return '未选择'
  const parts = path.split(/[\\/]/).filter(Boolean)
  return parts[parts.length - 1] || path
})

const stats = computed(() => ({
  ready: gitReady.value ? '已识别' : '未识别',
  name: repoName.value,
  view: tabLabel.value,
  elapsed: elapsedMs.value == null ? '—' : `${elapsedMs.value} ms`
}))

function setStatus(text, tone = 'idle') {
  statusText.value = text
  statusTone.value = tone
}

async function loadDefault() {
  loading.value = true
  try {
    const data = await getGitDefaultWorkspace()
    if (data.workspacePath) {
      workspacePath.value = data.workspacePath
      repoRoot.value = data.gitRepositoryRoot || ''
      gitReady.value = !!data.gitReady
      setStatus(data.gitReady ? `仓库：${data.gitRepositoryRoot}` : '默认工作区未找到 Git 仓库', data.gitReady ? 'ok' : 'warn')
      if (data.gitReady) await runQuery('STATUS')
    } else {
      setStatus('尚未配置默认代码工作区，请手动选择目录', 'idle')
    }
  } catch (error) {
    setStatus(error.message || '加载默认工作区失败', 'error')
  } finally {
    loading.value = false
  }
}

async function probe() {
  if (!workspacePath.value.trim()) {
    ElMessage.warning('请先填写仓库目录')
    return
  }
  probing.value = true
  try {
    const data = await probeGitWorkspace(workspacePath.value.trim())
    workspacePath.value = data.workspacePath
    repoRoot.value = data.gitRepositoryRoot || ''
    gitReady.value = !!data.gitReady
    setStatus(data.message + (data.gitRepositoryRoot ? `：${data.gitRepositoryRoot}` : ''), data.gitReady ? 'ok' : 'warn')
    if (data.gitReady) {
      ElMessage.success('已识别 Git 仓库')
      await runQuery(activeTab.value)
    } else {
      ElMessage.warning(data.message || '未找到 Git 仓库')
    }
  } catch (error) {
    gitReady.value = false
    repoRoot.value = ''
    setStatus(error.message || '探测失败', 'error')
    ElMessage.error(statusText.value)
  } finally {
    probing.value = false
  }
}

async function runQuery(action = activeTab.value) {
  if (!workspacePath.value.trim()) {
    ElMessage.warning('请先选择仓库目录')
    return
  }
  if (!gitReady.value && action !== 'STATUS') {
    await probe()
    if (!gitReady.value) return
  }
  if (action === 'SHOW' && !pathOrRef.value.trim()) {
    ElMessage.warning('查看提交请填写 commit / 引用，例如 HEAD')
    return
  }
  if (action === 'PULL') {
    try {
      await ElMessageBox.confirm(
        '将执行 git pull --ff-only（仅快进），确认继续？',
        '拉取确认',
        { type: 'warning', confirmButtonText: '确认拉取', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  querying.value = true
  if (!['PULL'].includes(action)) activeTab.value = action
  try {
    const data = await queryGit({
      workspacePath: repoRoot.value || workspacePath.value.trim(),
      action,
      pathOrRef: pathOrRef.value.trim() || undefined,
      maxCommits: maxCommits.value,
      fullDiff: fullDiff.value
    })
    output.value = data.output || ''
    repoRoot.value = data.repositoryRoot || repoRoot.value
    if (data.repositoryRoot) workspacePath.value = data.repositoryRoot
    gitReady.value = true
    elapsedMs.value = data.elapsedMs
    setStatus(`${data.message} · ${data.action} · ${data.elapsedMs ?? 0} ms`, 'ok')
    if (action === 'PULL') ElMessage.success('拉取完成')
  } catch (error) {
    output.value = error.message || ''
    elapsedMs.value = null
    setStatus(error.message || '执行失败', 'error')
    ElMessage.error(error.message || '执行失败')
  } finally {
    querying.value = false
  }
}

function onTabChange(name) {
  if (workspacePath.value.trim()) runQuery(name)
}

async function openPicker() {
  pickerVisible.value = true
  pickerLoading.value = true
  try {
    roots.value = await listFilesystemRoots()
    const start = workspacePath.value || roots.value[0]?.path || ''
    await browse(start)
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    pickerLoading.value = false
  }
}

async function browse(path) {
  pickerLoading.value = true
  try {
    const result = await listFilesystem(path || undefined)
    currentPath.value = result.path
    parentPath.value = result.parentPath
    children.value = result.children || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    pickerLoading.value = false
  }
}

function useCurrentPath() {
  workspacePath.value = currentPath.value
  pickerVisible.value = false
  probe()
}

onMounted(loadDefault)
</script>

<template>
  <section class="management-page git-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">GIT / LOCAL</span>
          <h2>Git 客户端</h2>
          <p>浏览本机代码仓库：状态、分支、提交与差异；支持受控快进拉取（pull --ff-only）。</p>
        </div>
        <div class="heading-actions git-heading-actions">
          <el-button :icon="FolderOpened" @click="openPicker">选择目录</el-button>
          <el-button :icon="Search" :loading="probing" @click="probe">识别仓库</el-button>
          <el-button :icon="Refresh" :loading="querying" :disabled="!workspacePath" @click="runQuery(activeTab)">刷新</el-button>
          <el-button type="primary" :icon="Download" :loading="querying" :disabled="!workspacePath" @click="runQuery('PULL')">拉取</el-button>
        </div>
      </div>
      <div class="agent-metrics">
        <div><span>仓库状态</span><strong :class="gitReady ? 'metric-success' : 'metric-warning'">{{ stats.ready }}</strong></div>
        <div><span>仓库名称</span><strong class="metric-warning" :title="repoRoot || workspacePath">{{ stats.name }}</strong></div>
        <div><span>当前视图</span><strong class="metric-primary">{{ stats.view }}</strong></div>
        <div><span>最近耗时</span><strong>{{ stats.elapsed }}</strong></div>
      </div>
    </div>

    <div class="git-layout page-fixed-scroll">
      <div class="management-section git-main-panel" v-loading="loading">
        <div class="path-row">
          <el-input v-model="workspacePath" clearable placeholder="本地仓库路径，例如 D:\work\demo-service" @keyup.enter="probe" />
          <el-button :icon="FolderOpened" @click="openPicker">浏览</el-button>
          <el-button type="primary" :icon="VideoPlay" :loading="probing" @click="probe">识别</el-button>
        </div>

        <div class="status-bar" :class="`tone-${statusTone}`">
          <span class="status-dot" />
          <span class="status-text" :title="statusText">{{ statusText }}</span>
          <span v-if="elapsedMs != null" class="elapsed">{{ elapsedMs }} ms</span>
        </div>

        <div class="query-extras">
          <el-input
            v-model="pathOrRef"
            clearable
            placeholder="可选：DIFF 相对路径 / SHOW 的 commit（如 HEAD）"
            class="ref-input"
          />
          <el-input-number v-model="maxCommits" :min="1" :max="100" controls-position="right" />
          <span class="extra-label">提交数</span>
          <el-checkbox v-model="fullDiff">完整差异</el-checkbox>
        </div>

        <el-tabs v-model="activeTab" class="git-tabs" @tab-change="onTabChange">
          <el-tab-pane v-for="tab in tabs" :key="tab.name" :label="tab.label" :name="tab.name" />
        </el-tabs>

        <div class="output-wrap" v-loading="querying">
          <pre v-if="output" class="git-output" :class="{ 'git-output-graph': activeTab === 'LOG' || activeTab === 'BRANCH' }">{{ output }}</pre>
          <div v-else class="result-empty">
            <p>识别仓库后，在此查看 Git 输出</p>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="pickerVisible" title="选择 Git 仓库目录" width="min(720px, 94vw)" destroy-on-close>
      <div class="picker-toolbar">
        <el-button :disabled="!parentPath" @click="browse(parentPath)">上级</el-button>
        <el-select
          :model-value="roots.find(item => currentPath?.startsWith(item.path))?.path || roots[0]?.path"
          placeholder="根目录"
          style="width: 160px"
          @change="browse"
        >
          <el-option v-for="root in roots" :key="root.path" :label="root.name" :value="root.path" />
        </el-select>
        <code class="picker-path">{{ currentPath }}</code>
      </div>
      <div class="picker-list" v-loading="pickerLoading">
        <button
          v-for="child in children"
          :key="child.path"
          type="button"
          class="picker-item"
          :class="{ 'is-git': child.hasGit }"
          @dblclick="browse(child.path)"
          @click="currentPath = child.path"
        >
          <div class="picker-item-title">
            <strong>{{ child.name }}</strong>
            <em v-if="child.hasGit">Git</em>
          </div>
          <span>{{ child.path }}</span>
        </button>
        <el-empty v-if="!children.length" description="无子目录" :image-size="56" />
      </div>
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" @click="useCurrentPath">使用当前目录</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.git-heading-actions {
  flex-wrap: nowrap;
  flex-shrink: 0;
}

.git-page .agent-metrics strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 20px;
  line-height: 1.25;
}

.git-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  min-height: 0;
}

.git-layout .management-section {
  margin-bottom: 0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.git-main-panel { min-width: 0; }

.path-row {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  flex: 0 0 auto;
}
.path-row .el-input { flex: 1; }

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
  flex: 1;
  min-width: 0;
  color: #486581;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.elapsed { color: #829ab1; font-size: 12px; flex: 0 0 auto; }
.status-bar.tone-ok { border-color: #b7ebc6; background: #f0fff4; }
.status-bar.tone-ok .status-dot { background: #238636; }
.status-bar.tone-ok .status-text { color: #1b5e2a; }
.status-bar.tone-warn { border-color: #f5d0a9; background: #fff8f1; }
.status-bar.tone-warn .status-dot { background: #c05621; }
.status-bar.tone-warn .status-text { color: #9a3412; }
.status-bar.tone-error { border-color: #f5c2c7; background: #fff5f5; }
.status-bar.tone-error .status-dot { background: #c0392b; }
.status-bar.tone-error .status-text { color: #9b1c1c; }

.query-extras {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.ref-input { flex: 1 1 280px; min-width: 200px; }
.extra-label { color: #829ab1; font-size: 12px; }

.git-tabs { flex: 0 0 auto; }
.git-tabs :deep(.el-tabs__header) { margin-bottom: 10px; }

.output-wrap {
  flex: 1 1 auto;
  min-height: 280px;
  border: 1px solid #1e293b;
  border-radius: 4px;
  overflow: auto;
  background: #0f172a;
}

.git-output {
  margin: 0;
  padding: 14px 16px;
  color: #e2e8f0;
  font-family: Consolas, "Cascadia Mono", "Courier New", monospace;
  font-size: 12.5px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 提交树 / 分支：保持 ASCII 图对齐，横向滚动 */
.git-output-graph {
  white-space: pre;
  word-break: normal;
  overflow-wrap: normal;
  line-height: 1.45;
  min-width: max-content;
}

.result-empty {
  height: 100%;
  min-height: 280px;
  display: grid;
  place-items: center;
  color: #94a3b8;
  font-size: 13px;
}
.result-empty p { margin: 0; }

.picker-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.picker-path {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #147d92;
  font-size: 12px;
}
.picker-list {
  max-height: 360px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.picker-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d9e2ec;
  background: #fff;
  text-align: left;
  cursor: pointer;
  font: inherit;
}
.picker-item:hover { border-color: #147d92; background: #e6f6fb; }
.picker-item.is-git { border-color: #9fd3c0; }
.picker-item-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.picker-item-title em {
  margin: 0;
  padding: 0 6px;
  border-radius: 3px;
  background: #e6f6fb;
  color: #0b6e84;
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
}
.picker-item strong { color: #243b53; font-size: 13px; }
.picker-item span { color: #829ab1; font-size: 12px; }

@media (max-width: 960px) {
  .git-heading-actions { flex-wrap: wrap; width: 100%; }
}
</style>

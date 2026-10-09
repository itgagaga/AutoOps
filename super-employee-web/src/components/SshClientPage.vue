<!-- @author yangdb -->
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Connection, Delete, Download, Folder, FolderAdd, FullScreen, Refresh, SwitchButton, Upload
} from '@element-plus/icons-vue'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'
import {
  deleteSftp, downloadSftp, getToken, listResources, listSftp, mkdirSftp, uploadSftpResumable
} from '../api'

const hosts = ref([])
const loading = ref(false)
const connecting = ref(false)
const connected = ref(false)
const statusText = ref('未连接')
const selectedHostId = ref('')
const activeTab = ref('terminal')
const termHost = ref(null)
const terminalFullscreen = ref(false)
const filters = reactive({ keyword: '' })

const sftpLoading = ref(false)
const sftpUploading = ref(false)
const sftpPath = ref('/')
const sftpParent = ref(null)
const sftpEntries = ref([])
const uploadInput = ref(null)
const folderInput = ref(null)
const dragOver = ref(false)
const uploadProgress = reactive({
  total: 0, done: 0, current: '', failed: 0, percent: 0, resumed: false, uploadedSize: 0, fileSize: 0
})

let terminal = null
let fitAddon = null
let socket = null
let resizeObserver = null
let connectionEpoch = 0

const enabledHosts = computed(() => hosts.value.filter(item => item.enabled))
const filteredHosts = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase()
  return enabledHosts.value.filter(item => {
    if (!keyword) return true
    return [item.name, item.code, item.ipAddress, item.environment]
      .filter(Boolean).join(' ').toLowerCase().includes(keyword)
  })
})
const selectedHost = computed(() => enabledHosts.value.find(item => item.id === selectedHostId.value) || null)
const stats = computed(() => ({
  total: hosts.value.length,
  enabled: enabledHosts.value.length,
  online: hosts.value.filter(item => item.status === 'ONLINE').length,
  connected: connected.value ? 1 : 0
}))

function wsUrl(hostId, cols, rows) {
  const base = import.meta.env.VITE_API_BASE_URL || ''
  const root = base.startsWith('http')
    ? base.replace(/^http/, 'ws')
    : `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}${base}`
  const token = encodeURIComponent(getToken() || '')
  return `${root}/api/ssh/ws?hostId=${encodeURIComponent(hostId)}&token=${token}&cols=${cols}&rows=${rows}`
}

function formatSize(size) {
  const n = Number(size) || 0
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

function formatTime(ms) {
  if (!ms) return '—'
  const date = new Date(ms)
  if (Number.isNaN(date.getTime())) return '—'
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function ensureTerminal() {
  if (terminal || !termHost.value) return
  terminal = new Terminal({
    cursorBlink: true,
    fontSize: 13,
    fontFamily: 'Consolas, "Courier New", monospace',
    theme: {
      background: '#0f172a',
      foreground: '#e2e8f0',
      cursor: '#38bdf8',
      selectionBackground: '#334155'
    },
    convertEol: true
  })
  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.open(termHost.value)
  fitAddon.fit()
  terminal.onData(data => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type: 'input', data }))
    }
  })
  resizeObserver = new ResizeObserver(() => {
    syncTerminalSize()
  })
  resizeObserver.observe(termHost.value)
}

async function loadHosts() {
  loading.value = true
  try {
    hosts.value = await listResources('hosts')
    if (selectedHostId.value && !enabledHosts.value.some(item => item.id === selectedHostId.value)) {
      selectedHostId.value = ''
    }
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    loading.value = false
  }
}

async function loadSftp(path = sftpPath.value) {
  if (!selectedHostId.value) return
  sftpLoading.value = true
  try {
    const data = await listSftp(selectedHostId.value, path)
    sftpPath.value = data.path || '/'
    sftpParent.value = data.parentPath
    sftpEntries.value = data.entries || []
  } catch (error) {
    ElMessage.error(error.message)
  } finally {
    sftpLoading.value = false
  }
}

async function connect() {
  if (!selectedHostId.value) {
    ElMessage.warning('请先选择主机')
    return
  }
  if (!selectedHost.value?.credentialId) {
    ElMessage.warning('该主机未关联 SSH 凭据')
    return
  }
  disconnect(false)
  const epoch = ++connectionEpoch
  activeTab.value = 'terminal'
  await nextTick()
  ensureTerminal()
  terminal.reset()
  terminal.focus()
  connecting.value = true
  statusText.value = '正在连接…'
  const url = wsUrl(selectedHostId.value, terminal.cols, terminal.rows)
  socket = new WebSocket(url)
  socket.onopen = () => {
    if (epoch !== connectionEpoch) return
    connecting.value = false
    connected.value = true
    statusText.value = `已连接 ${selectedHost.value?.name || ''}`
  }
  socket.onmessage = event => {
    if (epoch !== connectionEpoch) return
    try {
      const msg = JSON.parse(event.data)
      if (msg.type === 'output' && msg.data) terminal.write(msg.data)
      else if (msg.type === 'status') {
        statusText.value = msg.message || '已连接'
        terminal.writeln(`\r\n\x1b[36m${msg.message || ''}\x1b[0m\r\n`)
      } else if (msg.type === 'error') {
        statusText.value = msg.message || '连接异常'
        terminal.writeln(`\r\n\x1b[31m${msg.message || '连接异常'}\x1b[0m\r\n`)
        ElMessage.error(msg.message || 'SSH 连接失败')
      }
    } catch {
      terminal.write(String(event.data || ''))
    }
  }
  socket.onerror = () => {
    if (epoch !== connectionEpoch) return
    connecting.value = false
    connected.value = false
    statusText.value = '连接失败'
    ElMessage.error('WebSocket 连接失败，请确认已登录且后端已启动')
  }
  socket.onclose = () => {
    if (epoch !== connectionEpoch) return
    connecting.value = false
    connected.value = false
    if (statusText.value.startsWith('已连接') || statusText.value === '正在连接…') {
      statusText.value = '已断开'
    }
  }
}

function disconnect(notify = true) {
  connectionEpoch += 1
  if (socket) {
    try { socket.close() } catch { /* ignore */ }
    socket = null
  }
  connecting.value = false
  connected.value = false
  if (notify) statusText.value = '已断开'
}

function syncTerminalSize() {
  if (!fitAddon || !terminal) return
  fitAddon.fit()
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: 'resize', cols: terminal.cols, rows: terminal.rows }))
  }
}

async function setTerminalFullscreen(enabled) {
  terminalFullscreen.value = !!enabled
  document.body.classList.toggle('ssh-terminal-fs-lock', terminalFullscreen.value)
  await nextTick()
  // 等布局/动画稳定后再 fit，避免行列数偏差
  requestAnimationFrame(() => {
    syncTerminalSize()
    if (terminalFullscreen.value) terminal?.focus()
  })
}

function toggleTerminalFullscreen() {
  setTerminalFullscreen(!terminalFullscreen.value)
}

function onFullscreenKeydown(event) {
  if (event.key === 'Escape' && terminalFullscreen.value) {
    event.preventDefault()
    setTerminalFullscreen(false)
  }
}

async function selectHost(host) {
  if (host.id === selectedHostId.value && (connected.value || connecting.value)) {
    activeTab.value = 'terminal'
    await nextTick()
    terminal?.focus()
    return
  }
  selectedHostId.value = host.id
  sftpPath.value = '/'
  sftpEntries.value = []
  await connect()
}

async function openSftpEntry(row) {
  if (row.directory) await loadSftp(row.path)
}

async function createRemoteDir() {
  if (!selectedHostId.value) return
  try {
    const { value } = await ElMessageBox.prompt('请输入新目录名', '新建目录', {
      inputPlaceholder: '例如 deploy 或 release/20260727',
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputValidator: v => !!v?.trim() || '目录名不能为空'
    })
    const name = value.trim().replace(/^\/+|\/+$/g, '')
    const target = sftpPath.value === '/' ? `/${name}` : `${sftpPath.value}/${name}`
    await mkdirSftp(selectedHostId.value, target)
    ElMessage.success('目录已创建')
    await loadSftp()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || String(error))
  }
}

async function removeEntry(row) {
  try {
    await ElMessageBox.confirm(`确认删除「${row.name}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await deleteSftp(selectedHostId.value, row.path)
    ElMessage.success('已删除')
    await loadSftp()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function downloadEntry(row) {
  if (row.directory) {
    ElMessage.warning('暂不支持下载整个目录')
    return
  }
  try {
    await downloadSftp(selectedHostId.value, row.path, row.name)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function triggerUpload() {
  if (!selectedHostId.value) {
    ElMessage.warning('请先选择主机')
    return
  }
  uploadInput.value?.click()
}

function triggerFolderUpload() {
  if (!selectedHostId.value) {
    ElMessage.warning('请先选择主机')
    return
  }
  folderInput.value?.click()
}

function relativeOf(file) {
  const rel = file.webkitRelativePath || file.relativePath || file.name
  return String(rel || file.name).replace(/\\/g, '/')
}

async function uploadFiles(fileList) {
  const files = [...fileList].filter(Boolean)
  if (!files.length || !selectedHostId.value) return
  sftpUploading.value = true
  Object.assign(uploadProgress, {
    total: files.length, done: 0, current: '', failed: 0, percent: 0, resumed: false, uploadedSize: 0, fileSize: 0
  })
  try {
    for (const file of files) {
      const rel = relativeOf(file)
      uploadProgress.current = rel
      uploadProgress.fileSize = file.size
      uploadProgress.uploadedSize = 0
      uploadProgress.percent = 0
      uploadProgress.resumed = false
      try {
        await uploadSftpResumable(
          selectedHostId.value,
          sftpPath.value,
          file,
          rel.includes('/') ? rel : undefined,
          {
            onProgress: p => {
              uploadProgress.uploadedSize = p.uploadedSize
              uploadProgress.percent = p.percent
              uploadProgress.resumed = !!p.resumed
            }
          }
        )
        uploadProgress.done += 1
      } catch (error) {
        uploadProgress.failed += 1
        ElMessage.error(`${rel}：${error.message}`)
      }
    }
    const ok = uploadProgress.done
    const fail = uploadProgress.failed
    if (ok && !fail) ElMessage.success(`已上传 ${ok} 个文件${uploadProgress.resumed ? '（含续传）' : ''}`)
    else if (ok) ElMessage.warning(`完成 ${ok} 个，失败 ${fail} 个（失败文件可再次上传以续传）`)
    await loadSftp()
  } finally {
    sftpUploading.value = false
    uploadProgress.current = ''
  }
}

async function onUploadChange(event) {
  const files = event.target.files
  event.target.value = ''
  await uploadFiles(files)
}

function onDragEnter(event) {
  event.preventDefault()
  if (selectedHostId.value) dragOver.value = true
}

function onDragOver(event) {
  event.preventDefault()
  if (selectedHostId.value) dragOver.value = true
}

function onDragLeave(event) {
  if (event.currentTarget === event.target || !event.currentTarget.contains(event.relatedTarget)) {
    dragOver.value = false
  }
}

async function onDrop(event) {
  event.preventDefault()
  dragOver.value = false
  if (!selectedHostId.value) {
    ElMessage.warning('请先选择主机')
    return
  }
  const items = event.dataTransfer?.items
  if (items?.length) {
    const files = await readDataTransferItems(items)
    if (files.length) {
      await uploadFiles(files)
      return
    }
  }
  await uploadFiles(event.dataTransfer?.files || [])
}

async function readDataTransferItems(items) {
  const entries = []
  for (const item of items) {
    const entry = item.webkitGetAsEntry?.()
    if (entry) entries.push(entry)
  }
  if (!entries.length) return []
  const files = []
  for (const entry of entries) {
    await walkEntry(entry, '', files)
  }
  return files
}

function walkEntry(entry, prefix, out) {
  return new Promise((resolve, reject) => {
    if (entry.isFile) {
      entry.file(file => {
        const relativePath = prefix ? `${prefix}/${file.name}` : file.name
        Object.defineProperty(file, 'relativePath', { value: relativePath })
        out.push(file)
        resolve()
      }, reject)
      return
    }
    if (entry.isDirectory) {
      const reader = entry.createReader()
      const readBatch = () => {
        reader.readEntries(async batch => {
          if (!batch.length) {
            resolve()
            return
          }
          const nextPrefix = prefix ? `${prefix}/${entry.name}` : entry.name
          for (const child of batch) {
            await walkEntry(child, nextPrefix, out)
          }
          readBatch()
        }, reject)
      }
      readBatch()
      return
    }
    resolve()
  })
}

watch(activeTab, async tab => {
  if (tab !== 'terminal' && terminalFullscreen.value) {
    await setTerminalFullscreen(false)
  }
  if (tab === 'terminal') {
    await nextTick()
    ensureTerminal()
    syncTerminalSize()
  } else if (tab === 'files' && selectedHostId.value) {
    await loadSftp(sftpPath.value || '/')
  }
})

onMounted(async () => {
  window.addEventListener('keydown', onFullscreenKeydown)
  await loadHosts()
  await nextTick()
  ensureTerminal()
  terminal?.writeln('\x1b[90m点击左侧主机即可自动连接终端；切换主机会自动断开旧会话并连接新主机。也可切换到「文件传输」进行上传部署，Esc 退出全屏。\x1b[0m')
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onFullscreenKeydown)
  document.body.classList.remove('ssh-terminal-fs-lock')
  disconnect(false)
  if (resizeObserver && termHost.value) resizeObserver.unobserve(termHost.value)
  resizeObserver = null
  if (terminal) {
    terminal.dispose()
    terminal = null
  }
  fitAddon = null
})
</script>

<template>
  <section class="management-page ssh-page page-fixed-layout">
    <div class="page-fixed-overview">
      <div class="page-heading">
        <div>
          <span class="page-kicker">SSH / SFTP</span>
          <h2>SSH 客户端</h2>
          <p>交互式终端 + 文件传输（类 Xftp）。基于资源中心主机、凭据与指纹，支持手动上传部署。</p>
        </div>
        <div class="heading-actions">
          <el-button :icon="Refresh" :loading="loading" @click="loadHosts">刷新主机</el-button>
          <el-button type="primary" :icon="Connection" :loading="connecting" :disabled="!selectedHostId || connected" @click="connect">连接终端</el-button>
          <el-button type="danger" plain :icon="SwitchButton" :disabled="!connected && !connecting" @click="disconnect()">断开终端</el-button>
        </div>
      </div>
      <div class="agent-metrics">
        <div><span>主机总数</span><strong>{{ stats.total }}</strong></div>
        <div><span>已启用</span><strong class="metric-success">{{ stats.enabled }}</strong></div>
        <div><span>在线</span><strong class="metric-primary">{{ stats.online }}</strong></div>
        <div>
          <span>终端会话</span>
          <strong
            :class="connected ? 'metric-warning' : ''"
            :title="connected ? (selectedHost?.name || '') : ''"
          >{{ connected ? (selectedHost?.name || '已连接') : '空闲' }}</strong>
        </div>
      </div>
    </div>

    <div class="ssh-layout page-fixed-scroll">
      <aside class="management-section ssh-hosts" v-loading="loading">
        <div class="table-heading">
          <div><h3>选择主机</h3><span>共 {{ filteredHosts.length }} 台</span></div>
        </div>
        <el-input v-model="filters.keyword" clearable placeholder="名称 / 编码 / IP" class="host-search" />
        <div class="host-list">
          <button
            v-for="host in filteredHosts"
            :key="host.id"
            type="button"
            class="host-list-item"
            :class="{ active: host.id === selectedHostId }"
            @click="selectHost(host)"
          >
            <strong>{{ host.name }}</strong>
            <code>{{ host.ipAddress }}:{{ host.sshPort }}</code>
            <span>{{ host.status || 'UNKNOWN' }} · {{ host.credentialId ? '已关联凭据' : '未关联凭据' }}</span>
          </button>
          <el-empty v-if="!filteredHosts.length" description="暂无已启用主机" :image-size="64" />
        </div>
      </aside>

      <div class="management-section ssh-main-panel">
        <el-tabs v-model="activeTab" class="ssh-tabs">
          <el-tab-pane label="终端" name="terminal">
            <div
              class="terminal-panel"
              :class="{ 'is-fullscreen': terminalFullscreen }"
            >
              <div class="table-heading terminal-toolbar">
                <div>
                  <h3>交互终端</h3>
                  <span>{{ selectedHost ? `${selectedHost.name} · ${statusText}` : statusText }}</span>
                </div>
                <div class="terminal-actions">
                  <el-button :icon="FullScreen" @click="toggleTerminalFullscreen">
                    {{ terminalFullscreen ? '退出全屏' : '全屏' }}
                  </el-button>
                </div>
              </div>
              <div ref="termHost" class="ssh-terminal" />
            </div>
          </el-tab-pane>

          <el-tab-pane label="文件传输" name="files">
            <div class="table-heading sftp-toolbar">
              <div>
                <h3>远程文件</h3>
                <span>{{ selectedHost ? selectedHost.name : '请选择主机' }}</span>
              </div>
              <div class="sftp-actions">
                <el-button :icon="ArrowLeft" :disabled="!sftpParent" @click="loadSftp(sftpParent)">上级</el-button>
                <el-button :icon="Refresh" :loading="sftpLoading" :disabled="!selectedHostId" @click="loadSftp()">刷新</el-button>
                <el-button :icon="FolderAdd" :disabled="!selectedHostId" @click="createRemoteDir">新建目录</el-button>
                <el-button type="primary" :icon="Upload" :loading="sftpUploading" :disabled="!selectedHostId" @click="triggerUpload">上传文件</el-button>
                <el-button :icon="Folder" :loading="sftpUploading" :disabled="!selectedHostId" @click="triggerFolderUpload">上传文件夹</el-button>
                <input ref="uploadInput" type="file" multiple class="hidden-upload" @change="onUploadChange" />
                <input ref="folderInput" type="file" class="hidden-upload" webkitdirectory directory multiple @change="onUploadChange" />
              </div>
            </div>
            <p class="editor-hint sftp-path">当前路径：<code>{{ sftpPath }}</code>
              <span v-if="sftpUploading" class="upload-progress">
                · 上传 {{ uploadProgress.done }}/{{ uploadProgress.total }}
                <template v-if="uploadProgress.current">
                  · {{ uploadProgress.current }} {{ uploadProgress.percent }}%
                  <template v-if="uploadProgress.fileSize">
                    （{{ formatSize(uploadProgress.uploadedSize) }}/{{ formatSize(uploadProgress.fileSize) }}）
                  </template>
                  <template v-if="uploadProgress.resumed"> · 续传</template>
                </template>
              </span>
            </p>
            <el-progress
              v-if="sftpUploading"
              :percentage="uploadProgress.percent"
              :stroke-width="10"
              style="margin-bottom: 10px;"
            />
            <div
              class="sftp-dropzone"
              :class="{ active: dragOver, disabled: !selectedHostId }"
              @dragenter="onDragEnter"
              @dragover="onDragOver"
              @dragleave="onDragLeave"
              @drop="onDrop"
            >
              <div class="sftp-table-wrap" v-loading="sftpLoading || sftpUploading">
                <el-table :data="sftpEntries" height="100%" empty-text="请选择主机后刷新，或将文件拖拽到此处上传" @row-dblclick="openSftpEntry">
                  <el-table-column label="名称" min-width="220">
                    <template #default="{ row }">
                      <button v-if="row.directory" type="button" class="dir-link" @click="openSftpEntry(row)">
                        <el-icon><Folder /></el-icon>{{ row.name }}
                      </button>
                      <span v-else class="file-name">{{ row.name }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="大小" width="110" align="right">
                    <template #default="{ row }">{{ row.directory ? '—' : formatSize(row.size) }}</template>
                  </el-table-column>
                  <el-table-column label="修改时间" width="160" align="right">
                    <template #default="{ row }"><span class="time-cell">{{ formatTime(row.mtime) }}</span></template>
                  </el-table-column>
                  <el-table-column label="操作" width="160" fixed="right" align="center">
                    <template #default="{ row }">
                      <el-button v-if="!row.directory" link type="primary" :icon="Download" @click="downloadEntry(row)">下载</el-button>
                      <el-button link type="danger" :icon="Delete" @click="removeEntry(row)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <div v-if="dragOver" class="drop-hint">松开鼠标即可上传到当前目录</div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ssh-layout {
  display: grid;
  grid-template-columns: minmax(240px, 280px) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  min-height: 0;
}

.ssh-page .agent-metrics strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 20px;
  line-height: 1.25;
}

.ssh-layout .management-section { margin-bottom: 0; }

.ssh-hosts {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.host-search { margin-bottom: 12px; flex: 0 0 auto; }

.host-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.host-list-item {
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

.host-list-item:hover { border-color: #9fb3c8; }
.host-list-item.active {
  border-color: #147d92;
  background: #e6f6fb;
}

.host-list-item strong { color: #243b53; font-size: 14px; }
.host-list-item code {
  width: fit-content;
  padding: 1px 6px;
  border-radius: 3px;
  background: #f0f4f8;
  color: #147d92;
  font-size: 11px;
}
.host-list-item span { color: #829ab1; font-size: 12px; }

.ssh-main-panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.ssh-tabs {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}

.ssh-tabs :deep(.el-tabs__header) { flex: 0 0 auto; margin-bottom: 10px; }
.ssh-tabs :deep(.el-tabs__content) {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}
.ssh-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.terminal-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 0;
}

.terminal-toolbar { gap: 12px; }
.terminal-actions { display: flex; flex-wrap: wrap; gap: 8px; }

.terminal-panel.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 4000;
  padding: 12px 14px 14px;
  background: #0b1220;
  box-shadow: none;
}

.terminal-panel.is-fullscreen .table-heading h3,
.terminal-panel.is-fullscreen .table-heading span {
  color: #e2e8f0;
}

.terminal-panel.is-fullscreen .ssh-terminal {
  min-height: 0;
  border-radius: 6px;
}

.ssh-terminal {
  flex: 1 1 auto;
  min-height: 360px;
  padding: 10px;
  border-radius: 4px;
  background: #0f172a;
  overflow: hidden;
}

.ssh-terminal :deep(.xterm) { height: 100%; }
.ssh-terminal :deep(.xterm-viewport) { overflow-y: auto !important; }

.sftp-toolbar { gap: 12px; }
.sftp-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.sftp-path { margin: 0 0 10px; }
.upload-progress { color: #147d92; }
.sftp-dropzone {
  position: relative;
  flex: 1 1 auto;
  min-height: 280px;
  display: flex;
  flex-direction: column;
  border: 1px dashed transparent;
  border-radius: 4px;
  transition: border-color .15s, background .15s;
}
.sftp-dropzone.active {
  border-color: #147d92;
  background: #e6f6fb88;
}
.sftp-dropzone.disabled { opacity: .85; }
.sftp-table-wrap {
  flex: 1 1 auto;
  min-height: 280px;
  overflow: hidden;
}
.drop-hint {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  pointer-events: none;
  color: #0b6e84;
  font-size: 15px;
  font-weight: 600;
  background: #e6f6fbd9;
}

.dir-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  background: transparent;
  color: #147d92;
  cursor: pointer;
  font: inherit;
  padding: 0;
}
.file-name { color: #334e68; }
.hidden-upload { display: none; }

@media (max-width: 960px) {
  .ssh-layout { grid-template-columns: 1fr; }
  .host-list { max-height: 220px; }
  .ssh-terminal { min-height: 280px; }
}
</style>

<style>
body.ssh-terminal-fs-lock {
  overflow: hidden;
}
</style>

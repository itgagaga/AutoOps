/** @author yangdb */
import { ElMessage } from 'element-plus'

const TOKEN_KEY = 'super_employee_token'
const DEFAULT_TIMEOUT = 30_000

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (token) => localStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => localStorage.removeItem(TOKEN_KEY)

let messageDedupeTimer = null
const shownMessages = new Set()

function showApiError(message) {
  const key = String(message || '').trim()
  if (!key) return
  const now = Date.now()
  if (shownMessages.has(key) && now - (shownMessages.get(key) || 0) < 5000) return
  shownMessages.set(key, now)
  ElMessage.error(message, { duration: 4000, showClose: true })
}

const json = async (url, options = {}, authenticated = true) => {
  const headers = { ...(options.headers || {}) }
  if (authenticated && getToken()) headers.Authorization = `Bearer ${getToken()}`

  const timeout = Number(options.timeout) || DEFAULT_TIMEOUT
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeout)
  const signal = options.signal
  if (signal) {
    if (signal.aborted) controller.abort()
    else signal.addEventListener('abort', () => controller.abort(), { once: true })
  }

  try {
    const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}${url}`, {
      ...options,
      headers,
      signal: controller.signal
    })

    if (response.status === 401 && authenticated) {
      clearToken()
      window.dispatchEvent(new Event('auth-expired'))
      throw new Error('登录已过期，请重新登录')
    }
    if (response.status === 403) {
      throw new Error('权限不足，无法执行该操作')
    }
    if (response.status === 404) {
      throw new Error('请求的资源不存在')
    }
    if (response.status === 422) {
      const error = await response.json().catch(() => ({}))
      throw new Error(error.message || '请求参数校验失败')
    }
    if (response.status === 429) {
      throw new Error('请求过于频繁，请稍后再试')
    }
    if (response.status >= 500) {
      throw new Error(`服务器异常 (${response.status})，请稍后重试`)
    }
    if (!response.ok) {
      const error = await response.json().catch(() => ({}))
      throw new Error(error.message || `请求失败：${response.status}`)
    }
    return response.status === 204 ? null : response.json()
  } catch (err) {
    if (err.name === 'AbortError') {
      if (!options.silent) showApiError('请求超时，请检查网络或稍后重试')
      throw new Error('请求超时')
    }
    if (!options.silent && err.message && !err.message.includes('登录已过期')) {
      showApiError(err.message)
    }
    throw err
  } finally {
    clearTimeout(timeoutId)
  }
}

export const login = (payload) => json('/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
}, false)
export const getCaptcha = () => json('/api/auth/captcha', {}, false)
export const getCurrentUser = () => json('/api/auth/me')
export const changePassword = (payload) => json('/api/auth/password', {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const listUsers = () => json('/api/users')
export const createUser = (payload) => json('/api/users', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const assignUserRole = (id, role) => json(`/api/users/${id}/role`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ role })
})
export const setUserEnabled = (id, enabled) => json(`/api/users/${id}/enabled`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
})
export const listRoles = () => json('/api/roles')
export const createRole = (payload) => json('/api/roles', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const updateRole = (id, payload) => json(`/api/roles/${id}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const setRoleMenus = (id, menuIds) => json(`/api/roles/${id}/menus`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ menuIds })
})
export const listMenus = () => json('/api/menus')
export const createMenu = (payload) => json('/api/menus', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const updateMenu = (id, payload) => json(`/api/menus/${id}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const listAgents = () => json('/api/agents')
export const createAgent = (payload) => json('/api/agents', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const updateAgent = (id, payload) => json(`/api/agents/${id}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const setAgentEnabled = (id, enabled) => json(`/api/agents/${id}/enabled`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
})
export const getCodexEnvironmentStatus = () => json('/api/system/codex/status')
export const getCodexEnvironmentOperation = () => json('/api/system/codex/operation', {
  headers: { 'X-Polling-Request': 'true' }
})
export const installCodexCli = () => json('/api/system/codex/install', { method: 'POST' })
export const loginCodexCli = () => json('/api/system/codex/login', { method: 'POST' })
export const cancelCodexEnvironmentOperation = () => json('/api/system/codex/cancel', { method: 'POST' })
export const getRepairEngine = () => json('/api/system/repair/engine')
export const saveRepairEngine = (engine) => json('/api/system/repair/engine', {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ engine })
})
export const getCursorEnvironmentStatus = () => json('/api/system/repair/cursor/status')
export const getCursorEnvironmentOperation = () => json('/api/system/repair/cursor/operation', {
  headers: { 'X-Polling-Request': 'true' }
})
export const loginCursorCli = () => json('/api/system/repair/cursor/login', { method: 'POST' })
export const cancelCursorEnvironmentOperation = () => json('/api/system/repair/cursor/cancel', { method: 'POST' })
export const getClaudeEnvironmentStatus = () => json('/api/system/repair/claude/status')
export const getClaudeEnvironmentOperation = () => json('/api/system/repair/claude/operation', {
  headers: { 'X-Polling-Request': 'true' }
})
export const loginClaudeCli = () => json('/api/system/repair/claude/login', { method: 'POST' })
export const cancelClaudeEnvironmentOperation = () => json('/api/system/repair/claude/cancel', { method: 'POST' })
export const reportAgentHeartbeat = (id, payload) => json(`/api/agents/${id}/heartbeat`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const listAgentExecutions = () => json('/api/agent-executions')
export const getAgentExecution = (incidentId) =>
  json(`/api/agent-executions/${encodeURIComponent(incidentId)}`)
  export const listApprovals = (incidentId = '', includeDecided = false) => {
    const query = new URLSearchParams()
    if (incidentId) query.set('incidentId', incidentId)
    if (includeDecided) query.set('includeDecided', 'true')
    const suffix = query.toString() ? `?${query}` : ''
    return json(`/api/approvals${suffix}`)
  }
export const approveRequest = (id, comment) => json(`/api/approvals/${id}/approve`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ comment })
})
export const rejectRequest = (id, reason) => json(`/api/approvals/${id}/reject`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason })
})
export const getAgentPermissions = (agentId) => json(`/api/agent-permissions/${agentId}`)
export const saveAgentPermissions = (agentId, payload) => json(`/api/agent-permissions/${agentId}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const listTools = () => json('/api/tools')
export const createTool = (payload) => json('/api/tools', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const updateTool = (id, payload) => json(`/api/tools/${id}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const setToolEnabled = (id, enabled) => json(`/api/tools/${id}/enabled`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
})
export const listAuditLogs = () => json('/api/audit-logs')
export const listResources = (kind) => json(`/api/resources/${kind}`)
export const createResource = (kind, payload) => json(`/api/resources/${kind}`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const updateResource = (kind, id, payload) => json(`/api/resources/${kind}/${id}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const setResourceEnabled = (kind, id, enabled) => json(`/api/resources/${kind}/${id}/enabled`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
})
export const checkHostConnectivity = (id) => json(`/api/resources/hosts/${id}/connectivity-check`, {
  method: 'POST'
})
export const checkDatabaseConnectivity = (id) => json(`/api/resources/databases/${id}/connectivity-check`, {
  method: 'POST'
})
export const fetchHostFingerprint = (payload) => json('/api/resources/hosts/fetch-fingerprint', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const pingDatabase = (id) => json(`/api/db/${encodeURIComponent(id)}/ping`, { method: 'POST' })
export const queryDatabase = (id, sql) => json(`/api/db/${encodeURIComponent(id)}/query`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sql })
})
export const listDbSchemas = (id) => json(`/api/db/${encodeURIComponent(id)}/schemas`)
export const listDbTables = (id, schema) =>
  json(`/api/db/${encodeURIComponent(id)}/tables?schema=${encodeURIComponent(schema)}`)
export const getDbTableDdl = (id, schema, table) =>
  json(`/api/db/${encodeURIComponent(id)}/ddl?schema=${encodeURIComponent(schema)}&table=${encodeURIComponent(table)}`)
export const previewDbTable = (id, schema, table, limit = 100) =>
  json(`/api/db/${encodeURIComponent(id)}/preview?schema=${encodeURIComponent(schema)}&table=${encodeURIComponent(table)}&limit=${limit}`)
export const getGitDefaultWorkspace = () => json('/api/git/default-workspace')
export const probeGitWorkspace = (workspacePath) => json('/api/git/probe', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ workspacePath })
})
export const queryGit = (payload) => json('/api/git/query', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const listSftp = (hostId, path = '/') =>
  json(`/api/ssh/sftp/${encodeURIComponent(hostId)}/list?path=${encodeURIComponent(path || '/')}`)
export const mkdirSftp = (hostId, path) => json(`/api/ssh/sftp/${encodeURIComponent(hostId)}/mkdir`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ path })
})
export const deleteSftp = (hostId, path) =>
  json(`/api/ssh/sftp/${encodeURIComponent(hostId)}/entry?path=${encodeURIComponent(path)}`, { method: 'DELETE' })
export const uploadSftp = async (hostId, path, file, relativePath) => {
  const form = new FormData()
  form.append('file', file)
  const qs = new URLSearchParams({ path: path || '/' })
  if (relativePath) qs.set('relativePath', relativePath)
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL || ''}/api/ssh/sftp/${encodeURIComponent(hostId)}/upload?${qs}`,
    { method: 'POST', headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {}, body: form }
  )
  if (response.status === 401) {
    clearToken()
    window.dispatchEvent(new Event('auth-expired'))
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `上传失败：${response.status}`)
  }
  return response.json()
}

export const sftpUploadStatus = (hostId, path, fileName, totalSize, relativePath) => {
  const qs = new URLSearchParams({
    path: path || '/',
    fileName,
    totalSize: String(totalSize)
  })
  if (relativePath) qs.set('relativePath', relativePath)
  return json(`/api/ssh/sftp/${encodeURIComponent(hostId)}/upload-status?${qs}`)
}

export const uploadSftpChunk = async (hostId, {
  path, relativePath, fileName, offset, totalSize, chunk
}) => {
  const form = new FormData()
  form.append('chunk', chunk, fileName)
  const qs = new URLSearchParams({
    path: path || '/',
    fileName,
    offset: String(offset),
    totalSize: String(totalSize)
  })
  if (relativePath) qs.set('relativePath', relativePath)
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL || ''}/api/ssh/sftp/${encodeURIComponent(hostId)}/upload-chunk?${qs}`,
    { method: 'POST', headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {}, body: form }
  )
  if (response.status === 401) {
    clearToken()
    window.dispatchEvent(new Event('auth-expired'))
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `分片上传失败：${response.status}`)
  }
  return response.json()
}

/** 断点续传上传：按远端已有字节续传，默认 2MB 分片。 */
export const uploadSftpResumable = async (hostId, path, file, relativePath, {
  chunkSize = 2 * 1024 * 1024,
  onProgress
} = {}) => {
  const fileName = relativePath
    ? String(relativePath).split('/').pop()
    : (file.name || 'upload.bin')
  const rel = relativePath && String(relativePath).includes('/') ? relativePath : undefined
  const totalSize = file.size
  if (totalSize === 0) {
    throw new Error('不能上传空文件')
  }
  let status
  try {
    status = await sftpUploadStatus(hostId, path, fileName, totalSize, rel)
  } catch {
    status = { uploadedSize: 0, complete: false }
  }
  let offset = Number(status.uploadedSize) || 0
  if (status.complete || offset === totalSize) {
    onProgress?.({ uploadedSize: totalSize, totalSize, percent: 100, resumed: offset > 0 })
    return { path: status.path, uploadedSize: totalSize, complete: true, resumed: true }
  }
  if (offset > totalSize) offset = 0
  // 非分片对齐时截断到分片边界，避免半片损坏
  if (offset > 0 && offset % chunkSize !== 0) {
    offset = Math.floor(offset / chunkSize) * chunkSize
  }
  const resumed = offset > 0
  while (offset < totalSize) {
    const end = Math.min(offset + chunkSize, totalSize)
    const blob = file.slice(offset, end)
    const result = await uploadSftpChunk(hostId, {
      path,
      relativePath: rel,
      fileName,
      offset,
      totalSize,
      chunk: blob
    })
    offset = Number(result.uploadedSize) || end
    onProgress?.({
      uploadedSize: offset,
      totalSize,
      percent: Math.min(100, Math.round((offset / totalSize) * 100)),
      resumed
    })
  }
  return { path: status.path, uploadedSize: totalSize, complete: true, resumed }
}
export const downloadSftpUrl = (hostId, path) =>
  `${import.meta.env.VITE_API_BASE_URL || ''}/api/ssh/sftp/${encodeURIComponent(hostId)}/download?path=${encodeURIComponent(path)}`
export const downloadSftp = async (hostId, path, filename) => {
  const response = await fetch(downloadSftpUrl(hostId, path), {
    headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {}
  })
  if (response.status === 401) {
    clearToken()
    window.dispatchEvent(new Event('auth-expired'))
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `下载失败：${response.status}`)
  }
  const blob = await response.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename || 'download.bin'
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
export const listFilesystemRoots = () => json('/api/system/filesystem/roots')
export const listFilesystem = (path) => json(`/api/system/filesystem${path ? `?path=${encodeURIComponent(path)}` : ''}`)
export const createOpsTask = (payload) => json('/api/ops-tasks', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const updateOpsTask = (id, payload) => json(`/api/ops-tasks/${id}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const listDeploymentDirectories = (hostId, path = '/') =>
  json(`/api/ops-tasks/deployment-directories?hostId=${encodeURIComponent(hostId)}&path=${encodeURIComponent(path || '/')}`)
export const detectDeploymentPath = (payload) => json('/api/ops-tasks/deployment-paths/detect', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const setOpsTaskEnabled = (id, enabled) => json(`/api/ops-tasks/${id}/enabled`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
})
export const restartOpsTaskAnalysis = (id) => json(`/api/ops-tasks/${id}/restart-analysis`, { method: 'POST' })
export const deleteOpsTask = (id) => json(`/api/ops-tasks/${id}`, { method: 'DELETE' })
export const listOpsTasks = () => json('/api/ops-tasks')
export const getOpsTask = (id, polling = false) => json(`/api/ops-tasks/${id}`, polling
  ? { headers: { 'X-Polling-Request': 'true' } }
  : {})
export const listDictTypes = () => json('/api/dicts')
export const createDictType = (payload) => json('/api/dicts', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const updateDictType = (id, payload) => json(`/api/dicts/${id}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const deleteDictType = (id) => json(`/api/dicts/${id}`, { method: 'DELETE' })
export const listDictItems = (typeCode) => json(`/api/dicts/${encodeURIComponent(typeCode)}/items`)
export const createDictItem = (typeCode, payload) => json(`/api/dicts/${encodeURIComponent(typeCode)}/items`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const updateDictItem = (id, payload) => json(`/api/dicts/items/${id}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const setDictItemEnabled = (id, enabled) => json(`/api/dicts/items/${id}/enabled`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled })
})
export const deleteDictItem = (id) => json(`/api/dicts/items/${id}`, { method: 'DELETE' })
export const listDictOptions = (typeCode) => json(`/api/dicts/options/${encodeURIComponent(typeCode)}`)
export const listDictOptionsBatch = (types) => json(`/api/dicts/options?types=${encodeURIComponent(types.join(','))}`)
export const listIncidents = (opsTaskId = '', summary = false) => {
  const query = new URLSearchParams()
  if (opsTaskId) query.set('opsTaskId', opsTaskId)
  if (summary) query.set('summary', 'true')
  const suffix = query.toString()
  return json(`/api/incidents${suffix ? `?${suffix}` : ''}`)
}
export const getIncident = (id) => json(`/api/incidents/${id}`)
export const createIncident = (payload) => json('/api/incidents', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const runIncident = (id) => json(`/api/incidents/${id}/run`, { method: 'POST' })
export const acceptIncidentRepair = (id) => json(`/api/incidents/${id}/repair/accept`, { method: 'POST' })
export const revertIncidentRepair = (id) => json(`/api/incidents/${id}/repair/revert`, { method: 'POST' })
export const completeIncidentArtifactVerification = (id, success, note = '') =>
  json(`/api/incidents/${id}/artifact/complete`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ success, note })
  })
export const decideIncidentBranchMerge = (id, merge) =>
  json(`/api/incidents/${id}/branch-merge/decide`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ merge })
  })
export const downloadIncidentArtifact = async (id, filename = 'repair.jar') => {
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL || ''}/api/incidents/${encodeURIComponent(id)}/artifact/download`,
    { headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {} }
  )
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `JAR 下载失败：${response.status}`)
  }
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
export const downloadIncidentDeploymentScripts = async (id) => {
  const response = await fetch(
    `${import.meta.env.VITE_API_BASE_URL || ''}/api/incidents/${encodeURIComponent(id)}/deployment-scripts/download`,
    { headers: getToken() ? { Authorization: `Bearer ${getToken()}` } : {} }
  )
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `部署脚本下载失败：${response.status}`)
  }
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = url
  link.download = 'java-deployment-scripts.zip'
  link.click()
  URL.revokeObjectURL(url)
}
export const approveIncident = (id, comment) => json(`/api/incidents/${id}/approve`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ comment })
})
export const rejectIncident = (id, reason) => json(`/api/incidents/${id}/reject`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ reason })
})
export const getHealth = () => json('/actuator/health', {}, false)
export const listKnowledge = () => json('/api/knowledge')
export const createKnowledge = payload => json('/api/knowledge', {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const updateKnowledge = (id, payload) => json(`/api/knowledge/${id}`, {
  method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
})
export const deleteKnowledge = id => json(`/api/knowledge/${id}`, { method: 'DELETE' })
/** 根据文档与/或描述预生成知识草稿（不落库） */
export const generateKnowledgeDraft = ({ file, description, category, scope } = {}) => {
  const body = new FormData()
  if (file) body.append('file', file)
  if (description) body.append('description', description)
  if (category) body.append('category', category)
  if (scope) body.append('scope', scope)
  return json('/api/knowledge/generate', { method: 'POST', body })
}

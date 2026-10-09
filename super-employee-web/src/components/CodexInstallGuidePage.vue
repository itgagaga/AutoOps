<!-- @author yangdb -->
<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowRight, CircleCheck, Close, CopyDocument, Download, Key, Loading, Monitor, Refresh, Setting
} from '@element-plus/icons-vue'
import {
  cancelClaudeEnvironmentOperation, cancelCodexEnvironmentOperation, cancelCursorEnvironmentOperation,
  getClaudeEnvironmentOperation, getClaudeEnvironmentStatus,
  getCodexEnvironmentOperation, getCodexEnvironmentStatus,
  getCursorEnvironmentOperation, getCursorEnvironmentStatus,
  getRepairEngine, installCodexCli, loginClaudeCli, loginCodexCli, loginCursorCli, saveRepairEngine
} from '../api'

const emit = defineEmits(['navigate'])
const status = ref(null)
const cursorStatus = ref(null)
const claudeStatus = ref(null)
const engine = ref(null)
const selectedEngine = ref('codex')
const loading = ref(false)
let pollingTimer = null

const ENGINE_LABELS = {
  codex: 'CodeX CLI',
  cursor: 'Cursor CLI',
  claude: 'Claude Code CLI'
}

const activeEngine = computed(() => engine.value?.engine || selectedEngine.value || 'codex')
const usingCursor = computed(() => activeEngine.value === 'cursor')
const usingClaude = computed(() => activeEngine.value === 'claude')
const activeStatus = computed(() => {
  if (usingCursor.value) return cursorStatus.value
  if (usingClaude.value) return claudeStatus.value
  return status.value
})
const busy = computed(() => activeStatus.value?.operation?.state === 'RUNNING')
const installed = computed(() => !!activeStatus.value?.installed)
const authenticated = computed(() => !!activeStatus.value?.authenticated)
const currentStage = computed(() => {
  if (authenticated.value) return 5
  if (busy.value && activeStatus.value?.operation?.type === 'LOGIN') return 4
  if (installed.value) return 4
  if (busy.value && status.value?.operation?.type === 'INSTALL') return 3
  if (activeStatus.value) return 2
  return 1
})
const steps = computed(() => {
  if (usingCursor.value) {
    return [
      {
        title: '确认运行环境', icon: Monitor,
        description: 'Cursor CLI 通过官方安装脚本安装 agent 命令，支持 CURSOR_API_KEY 或登录态鉴权。',
        detail: '请在本机 PowerShell 中执行官方安装脚本：',
        commands: [
          '# 前置条件：Node.js >= 18',
          'node --version',
          '# Windows 一键安装 Cursor CLI',
          cursorStatus.value?.installHint || "irm 'https://cursor.com/install?win32=true' | iex"
        ],
        state: currentStage.value > 1 ? 'done' : 'current'
      },
      {
        title: '检测 Cursor CLI', icon: Refresh,
        description: '检测 agent / cursor-agent 可执行文件与版本信息。',
        detail: installed.value
          ? (cursorStatus.value?.version || '已检测到 Cursor CLI')
          : '尚未检测到可用的 agent 命令。请在本机终端执行：',
        commands: installed.value ? [
          '# 当前已安装，可验证版本',
          cursorStatus.value?.version ? `# ${cursorStatus.value.version}` : ''
        ].filter(Boolean) : [
          'agent --version',
          'cursor-agent --version'
        ],
        state: installed.value ? 'done' : currentStage.value === 2 ? 'current' : 'waiting'
      },
      {
        title: '安装 Cursor CLI', icon: Download,
        description: '请在本机执行官方安装命令（平台不代执行任意安装脚本）。',
        detail: installed.value ? 'Cursor CLI 已安装。' : '安装完成后重启平台后端，再点击「重新检测」。',
        commands: installed.value ? [
          '# 安装已完成，如需重装请先卸载',
          'agent uninstall'
        ] : [
          '# 执行官方安装脚本',
          cursorStatus.value?.installHint || "irm 'https://cursor.com/install?win32=true' | iex",
          '# 安装完成后重启后端服务'
        ],
        state: installed.value ? 'done' : currentStage.value === 3 ? 'current' : 'waiting'
      },
      {
        title: '登录 Cursor', icon: Key,
        description: '启动 agent login，或在服务器环境配置 CURSOR_API_KEY。',
        detail: busy.value && cursorStatus.value?.operation?.type === 'LOGIN'
          ? cursorStatus.value.operation.message
          : authenticated.value
            ? (cursorStatus.value?.authSummary || '已登录')
            : '可在终端登录，或配置环境变量 CURSOR_API_KEY：',
        commands: authenticated.value || (busy.value && cursorStatus.value?.operation?.type === 'LOGIN')
          ? [
              '# 已登录成功，可通过以下命令管理会话',
              'agent session list'
            ]
          : [
              '# 方式一：交互式登录',
              'agent login',
              '# 方式二：配置 API Key（适合服务器环境）',
              '# 设置环境变量 CURSOR_API_KEY=<your-key>'
            ],
        state: authenticated.value ? 'done' : currentStage.value === 4 ? 'current' : 'waiting'
      },
      {
        title: '验证环境可用', icon: CircleCheck,
        description: '验证 CLI 可用后，将默认修复引擎切到 Cursor 即可用于自动代码修复。',
        detail: authenticated.value || installed.value ? '可将修复引擎切换为 Cursor。' : '请完成前面的安装与登录步骤。',
        commands: authenticated.value ? [
          '# 环境就绪，切换修复引擎',
          '# 前往「Agent 管理」页面将默认引擎设为 Cursor'
        ] : [
          '# 完成以上步骤后',
          'agent --version && agent'
        ],
        state: authenticated.value || installed.value ? 'done' : 'waiting'
      }
    ]
  }
  if (usingClaude.value) {
    return [
      {
        title: '确认运行环境', icon: Monitor,
        description: 'Claude Code CLI 通过官方安装脚本安装 claude 命令，支持 ANTHROPIC_API_KEY 或登录态鉴权。',
        detail: '请在本机 PowerShell 中执行官方安装脚本：',
        commands: [
          '# 前置条件：Node.js >= 18',
          'node --version',
          '# Windows 一键安装 Claude Code CLI',
          claudeStatus.value?.installHint?.replace(/^PowerShell:\s*/i, '') || 'irm https://claude.ai/install.ps1 | iex'
        ],
        state: currentStage.value > 1 ? 'done' : 'current'
      },
      {
        title: '检测 Claude Code', icon: Refresh,
        description: '检测 claude 可执行文件与版本信息。',
        detail: installed.value
          ? (claudeStatus.value?.version || '已检测到 Claude Code CLI')
          : (claudeStatus.value?.detectHint
            || '尚未检测到可用的 claude 命令。请在本机终端执行：'),
        commands: installed.value ? [
          '# 当前已安装，可验证版本',
          claudeStatus.value?.version ? `# ${claudeStatus.value.version}` : ''
        ].filter(Boolean) : [
          claudeStatus.value?.detectHint || 'claude --version'
        ],
        state: installed.value ? 'done' : currentStage.value === 2 ? 'current' : 'waiting'
      },
      {
        title: '安装 Claude Code', icon: Download,
        description: '请在本机执行官方安装命令（平台不代执行任意安装脚本）。',
        detail: installed.value ? 'Claude Code CLI 已安装。' : '安装完成后重启平台后端，再点击「重新检测」。',
        commands: installed.value ? [
          '# 安装已完成，如需重装请先卸载',
          'claude --uninstall'
        ] : [
          '# 执行官方安装脚本',
          claudeStatus.value?.installHint?.replace(/^PowerShell:\s*/i, '') || 'irm https://claude.ai/install.ps1 | iex',
          '# 安装完成后重启后端服务'
        ],
        state: installed.value ? 'done' : currentStage.value === 3 ? 'current' : 'waiting'
      },
      {
        title: '登录 Claude', icon: Key,
        description: '启动 claude auth login，或在配置中设置 ANTHROPIC_API_KEY。',
        detail: busy.value && claudeStatus.value?.operation?.type === 'LOGIN'
          ? claudeStatus.value.operation.message
          : authenticated.value
            ? (claudeStatus.value?.authSummary || '已登录')
            : '可在终端登录，或配置环境变量 ANTHROPIC_API_KEY：',
        commands: authenticated.value || (busy.value && claudeStatus.value?.operation?.type === 'LOGIN')
          ? [
              '# 已登录成功，可通过以下命令管理会话',
              'claude config list'
            ]
          : [
              '# 方式一：交互式登录',
              'claude auth login',
              '# 方式二：配置 API Key（适合服务器环境）',
              '# 设置环境变量 ANTHROPIC_API_KEY=<your-key>'
            ],
        state: authenticated.value ? 'done' : currentStage.value === 4 ? 'current' : 'waiting'
      },
      {
        title: '验证环境可用', icon: CircleCheck,
        description: '验证 CLI 可用后，将默认修复引擎切到 Claude Code 即可用于自动代码修复。',
        detail: authenticated.value || installed.value ? '可将修复引擎切换为 Claude Code。' : '请完成前面的安装与登录步骤。',
        commands: authenticated.value ? [
          '# 环境就绪，切换修复引擎',
          '# 前往「Agent 管理」页面将默认引擎设为 Claude Code'
        ] : [
          '# 完成以上步骤后',
          'claude --version && claude'
        ],
        state: authenticated.value || installed.value ? 'done' : 'waiting'
      }
    ]
  }
  return [
    {
      title: '确认运行环境', icon: Monitor,
      description: 'Codex CLI 需要 Node.js >= 22。Windows 环境建议使用 WSL2，也可在 PowerShell 中直接安装（实验性支持）。',
      detail: status.value?.supported === false ? '当前系统不支持自动安装，请改用 WSL2 或手动方式。' : '环境检测通过，准备开始安装 Codex CLI。',
      commands: status.value?.supported === false
        ? [
            '# 当前系统不支持，建议使用 WSL2',
            'wsl --install',
            '# 在 WSL 中安装 Node.js 22',
            'nvm install 22',
            '# 确认 Node.js 版本',
            'node --version'
          ]
        : [
            '# 前置条件：Node.js >= 22',
            'node --version',
            '# 全局安装 Codex CLI',
            'npm install -g @openai/codex'
          ],
      state: status.value?.supported === false ? 'error' : currentStage.value > 1 ? 'done' : 'current'
    },
    {
      title: '检测 Codex CLI', icon: Refresh,
      description: '检测 codex 可执行文件、版本信息和当前登录状态。',
      detail: installed.value
        ? (status.value?.version || '已检测到 Codex CLI')
        : '尚未检测到可用的 Codex CLI。请在本机终端执行：',
      commands: installed.value ? [
        '# 当前已安装，可验证版本',
        status.value?.version ? `# ${status.value.version}` : ''
      ].filter(Boolean) : [
        status.value?.detectHint || 'codex --version'
      ],
      state: installed.value ? 'done' : currentStage.value === 2 ? 'current' : 'waiting'
    },
    {
      title: '安装 Codex CLI', icon: Download,
      description: '调用官方 npm 安装脚本，将 Codex CLI 安装到全局可识别的路径。',
      detail: busy.value && status.value?.operation?.type === 'INSTALL'
        ? status.value.operation.message : installed.value ? 'Codex CLI 已安装完成。' : '安装过程通常需要数十秒，也可点击上方「安装 Codex CLI」。',
      commands: installed.value ? [
        '# 安装已完成，如需重装请先卸载',
        'npm uninstall -g @openai/codex'
      ] : [
        '# 执行 npm 全局安装',
        status.value?.installHint || 'npm install -g @openai/codex',
        '# 国内用户可使用镜像源加速',
        '# npm install -g @openai/codex --registry=https://registry.npmmirror.com'
      ],
      state: installed.value ? 'done' : currentStage.value === 3 ? 'current' : 'waiting'
    },
    {
      title: '登录 ChatGPT', icon: Key,
      description: '启动 Codex OAuth 登录，在自动打开的浏览器页面中完成账号授权。',
      detail: busy.value && status.value?.operation?.type === 'LOGIN'
        ? status.value.operation.message : authenticated.value ? (status.value?.authSummary || 'ChatGPT 已登录') : '安装完成后即可点击上方「登录 ChatGPT」。',
      commands: authenticated.value || (busy.value && status.value?.operation?.type === 'LOGIN')
        ? [
            '# 已登录成功',
            status.value?.authSummary ? `# ${status.value.authSummary}` : 'codex whoami'
          ]
        : [
            '# 方式一：浏览器 OAuth 登录（推荐）',
            status.value?.loginHint || 'codex login',
            '# 方式二：设备码登录（SSH 远程环境）',
            '# codex login --device-code',
            '# 方式三：API Key 登录',
            '# codex login --with-api-key'
          ],
      state: authenticated.value ? 'done' : currentStage.value === 4 ? 'current' : 'waiting'
    },
    {
      title: '验证环境可用', icon: CircleCheck,
      description: '同时验证 CLI 版本和登录状态，完成后即可用于自动代码修复。',
      detail: authenticated.value ? 'Codex 修复环境已经就绪。' : '请完成前面的安装与登录步骤。',
      commands: authenticated.value ? [
        '# 环境就绪，切换修复引擎',
        '# 前往「Agent 管理」页面将默认引擎设为 Codex'
      ] : [
        '# 完成以上步骤后，验证环境',
        'codex --version && codex'
      ],
      state: authenticated.value ? 'done' : 'waiting'
    }
  ]
})

async function copyCommand(command) {
  try {
    await navigator.clipboard.writeText(command)
    ElMessage.success('命令已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择命令')
  }
}

function isNoteCommand(cmd) {
  return String(cmd || '').trim().startsWith('#')
}

function stripNotePrefix(cmd) {
  return String(cmd || '').replace(/^#\s*/, '').trim()
}

function normalizeCommand(raw) {
  return String(raw || '')
    .replace(/^PowerShell:\s*/i, '')
    .replace(/^Bash:\s*/i, '')
    .trim()
}

function stopPolling() {
  if (pollingTimer) clearTimeout(pollingTimer)
  pollingTimer = null
}

async function loadStatus(silent = false) {
  if (!silent) loading.value = true
  try {
    const [engineStatus, codex, cursor, claude] = await Promise.all([
      getRepairEngine(),
      getCodexEnvironmentStatus(),
      getCursorEnvironmentStatus(),
      getClaudeEnvironmentStatus()
    ])
    engine.value = engineStatus
    selectedEngine.value = engineStatus?.engine || 'codex'
    status.value = codex
    cursorStatus.value = cursor
    claudeStatus.value = claude
    if (busy.value) startPolling()
  } catch (error) {
    if (!silent) ElMessage.error(error.message)
  } finally {
    if (!silent) loading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollingTimer = setTimeout(async () => {
    try {
      if (usingCursor.value) {
        cursorStatus.value = {
          ...(cursorStatus.value || {}),
          operation: await getCursorEnvironmentOperation()
        }
        if (cursorStatus.value?.operation?.state !== 'RUNNING') {
          await loadStatus(true)
          return
        }
      } else if (usingClaude.value) {
        claudeStatus.value = {
          ...(claudeStatus.value || {}),
          operation: await getClaudeEnvironmentOperation()
        }
        if (claudeStatus.value?.operation?.state !== 'RUNNING') {
          await loadStatus(true)
          return
        }
      } else {
        status.value = {
          ...(status.value || {}),
          operation: await getCodexEnvironmentOperation()
        }
        if (status.value?.operation?.state !== 'RUNNING') {
          await loadStatus(true)
          return
        }
      }
      startPolling()
    } catch {
      stopPolling()
    }
  }, 2000)
}

async function switchEngine(value) {
  try {
    engine.value = await saveRepairEngine(value)
    selectedEngine.value = engine.value?.engine || value
    ElMessage.success(`已切换本地修复引擎为 ${ENGINE_LABELS[value] || value}`)
  } catch (error) {
    selectedEngine.value = engine.value?.engine || 'codex'
    ElMessage.error(error.message)
  }
}

async function install() {
  try {
    await ElMessageBox.confirm('将调用官方脚本安装 CodeX CLI，是否继续？', '安装确认', { type: 'warning' })
    status.value = { ...(status.value || {}), operation: await installCodexCli() }
    startPolling()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error.message)
  }
}

async function login() {
  try {
    if (usingCursor.value) {
      cursorStatus.value = { ...(cursorStatus.value || {}), operation: await loginCursorCli() }
    } else if (usingClaude.value) {
      claudeStatus.value = { ...(claudeStatus.value || {}), operation: await loginClaudeCli() }
    } else {
      status.value = { ...(status.value || {}), operation: await loginCodexCli() }
    }
    startPolling()
  } catch (error) {
    ElMessage.error(error.message)
  }
}

async function cancel() {
  try {
    if (usingCursor.value) await cancelCursorEnvironmentOperation()
    else if (usingClaude.value) await cancelClaudeEnvironmentOperation()
    else await cancelCodexEnvironmentOperation()
    await loadStatus(true)
  } catch (error) {
    ElMessage.error(error.message)
  }
}

function loginButtonLabel() {
  if (usingCursor.value) return '登录 Cursor'
  if (usingClaude.value) return '登录 Claude'
  return '登录 ChatGPT'
}

onMounted(loadStatus)
onUnmounted(stopPolling)
</script>

<template>
  <section class="codex-guide-page">
    <header class="codex-guide-hero">
      <div>
        <span class="codex-guide-kicker">LOCAL REPAIR ENGINE</span>
        <h2>本地修复引擎向导</h2>
        <p>可在 CodeX、Cursor 与 Claude Code 之间切换；自动修复将调用当前选中的引擎。</p>
      </div>
      <div class="codex-guide-status">
        <el-icon :class="{ spinning: busy }"><Loading v-if="busy" /><CircleCheck v-else-if="authenticated" /><Setting v-else /></el-icon>
        <div>
          <strong>{{ busy ? '环境配置中' : authenticated ? '环境已就绪' : installed ? '等待登录' : '等待安装' }}</strong>
          <span>{{ activeStatus?.version || activeStatus?.operation?.message || '请先检测当前环境' }}</span>
        </div>
      </div>
    </header>

    <div class="codex-guide-toolbar">
      <el-radio-group v-model="selectedEngine" :disabled="busy" @change="switchEngine">
        <el-radio-button label="codex" :disabled="engine && engine.codexAvailable === false">CodeX CLI</el-radio-button>
        <el-radio-button label="cursor" :disabled="engine && engine.cursorAvailable === false">Cursor CLI</el-radio-button>
        <el-radio-button label="claude" :disabled="engine && engine.claudeAvailable === false">Claude Code</el-radio-button>
      </el-radio-group>
      <el-tag type="info" effect="plain">当前：{{ ENGINE_LABELS[activeEngine] || activeEngine }}</el-tag>
      <el-button :icon="Refresh" :loading="loading" :disabled="busy" @click="loadStatus()">重新检测</el-button>
      <el-button v-if="!usingCursor && !usingClaude && !installed" type="primary" :icon="Download" :loading="busy && status?.operation?.type === 'INSTALL'" :disabled="busy || status?.supported === false" @click="install">安装 CodeX CLI</el-button>
      <el-button v-else-if="!authenticated" type="primary" :icon="Key" :loading="busy && activeStatus?.operation?.type === 'LOGIN'" :disabled="busy" @click="login">{{ loginButtonLabel() }}</el-button>
      <el-button v-else type="success" :icon="CircleCheck" disabled>环境可用</el-button>
      <el-button v-if="busy" type="danger" plain :icon="Close" @click="cancel">取消操作</el-button>
      <el-button class="codex-agent-link" text type="primary" @click="emit('navigate', 'AGENT_MANAGEMENT')">前往 Agent 管理<el-icon><ArrowRight /></el-icon></el-button>
    </div>

    <div v-loading="loading" class="codex-install-timeline">
      <article v-for="(step, index) in steps" :key="`${activeEngine}-${step.title}`" :class="['codex-install-step', `is-${step.state}`]">
        <div class="codex-step-rail">
          <span><el-icon><component :is="step.icon" /></el-icon></span>
          <i v-if="index < steps.length - 1" />
        </div>
        <div class="codex-step-card">
          <div class="codex-step-number">STEP {{ String(index + 1).padStart(2, '0') }}</div>
          <div class="codex-step-heading">
            <h3>{{ step.title }}</h3>
            <el-tag v-if="step.state === 'done'" type="success" size="small">已完成</el-tag>
            <el-tag v-else-if="step.state === 'current'" type="primary" size="small">当前步骤</el-tag>
            <el-tag v-else-if="step.state === 'error'" type="danger" size="small">不支持</el-tag>
            <el-tag v-else type="info" size="small">待完成</el-tag>
          </div>
          <p>{{ step.description }}</p>
          <div v-if="step.detail" class="codex-step-detail">{{ step.detail }}</div>
          <div v-if="step.commands?.length" class="codex-step-commands">
            <template v-for="(cmd, i) in step.commands" :key="`${cmd}-${i}`">
              <div v-if="isNoteCommand(cmd)" class="codex-cmd codex-cmd--note">
                <span class="codex-cmd-prompt">#</span>
                <code>{{ stripNotePrefix(cmd) }}</code>
              </div>
              <button
                v-else
                type="button"
                class="codex-cmd"
                :title="'点击复制：' + cmd"
                @click="copyCommand(normalizeCommand(cmd))"
              >
                <span class="codex-cmd-prompt">$</span>
                <code>{{ normalizeCommand(cmd) }}</code>
                <el-icon class="codex-cmd-copy"><CopyDocument /></el-icon>
              </button>
            </template>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.codex-guide-page { display: flex; flex: 1 1 auto; flex-direction: column; min-height: 0; padding: 20px; overflow: hidden; background: #f5f8fb; }
.codex-guide-hero { display: flex; flex: 0 0 auto; align-items: center; justify-content: space-between; gap: 28px; padding: 24px 30px; border: 1px solid #cfe3ea; border-radius: 14px; background: linear-gradient(120deg, #fff, #edf9fb); box-shadow: 0 9px 26px rgba(32, 76, 97, .08); }
.codex-guide-kicker { color: #147d92; font-size: 10px; font-weight: 700; letter-spacing: .15em; }
.codex-guide-hero h2 { margin: 7px 0 6px; color: #102a43; font-size: 27px; }
.codex-guide-hero p { margin: 0; color: #627d98; font-size: 13px; }
.codex-guide-status { display: flex; align-items: center; gap: 12px; min-width: 260px; padding: 13px 16px; border: 1px solid #d5e7ed; border-radius: 10px; background: rgba(255,255,255,.78); }
.codex-guide-status > .el-icon { color: #147d92; font-size: 27px; }
.codex-guide-status strong, .codex-guide-status span { display: block; }
.codex-guide-status strong { color: #294a5b; font-size: 13px; }
.codex-guide-status span { max-width: 260px; margin-top: 3px; overflow: hidden; color: #78909f; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.spinning { animation: codex-spin 1s linear infinite; }
@keyframes codex-spin { to { transform: rotate(360deg); } }
.codex-guide-toolbar { display: flex; flex: 0 0 auto; align-items: center; gap: 10px; padding: 14px 2px; flex-wrap: wrap; }
.codex-agent-link { margin-left: auto; }
.codex-install-timeline { flex: 1 1 auto; min-height: 0; padding: 5px 8px 30px; overflow-y: auto; scrollbar-width: thin; }
.codex-install-step { display: grid; grid-template-columns: 62px 1fr; }
.codex-step-rail { display: flex; flex-direction: column; align-items: center; }
.codex-step-rail > span { position: relative; z-index: 1; display: grid; place-items: center; width: 40px; height: 40px; border: 4px solid #f5f8fb; border-radius: 50%; color: #829ab1; font-size: 17px; background: #e8eef3; }
.codex-step-rail i { width: 2px; flex: 1; min-height: 88px; background: #dce6ed; }
.codex-install-step.is-done .codex-step-rail > span { color: #268653; background: #dff7e9; }
.codex-install-step.is-current .codex-step-rail > span { color: #fff; background: #168ca2; box-shadow: 0 0 0 5px rgba(22,140,162,.12); }
.codex-install-step.is-error .codex-step-rail > span { color: #c45656; background: #fdeaea; }
.codex-step-card { margin: 0 0 13px; padding: 16px 20px; border: 1px solid #dde7ee; border-radius: 11px; background: #fff; box-shadow: 0 4px 15px rgba(32,76,97,.05); }
.codex-install-step.is-current .codex-step-card { border-color: #9dcfda; background: linear-gradient(100deg, #f4fcfd, #fff); }
.codex-step-number { color: #8ba1af; font-size: 9px; font-weight: 700; letter-spacing: .12em; }
.codex-step-heading { display: flex; align-items: center; gap: 9px; margin-top: 4px; }
.codex-step-heading h3 { margin: 0; color: #243b53; font-size: 16px; }
.codex-step-card p { margin: 7px 0; color: #627d98; font-size: 13px; line-height: 1.6; }
.codex-step-detail { margin-top: 4px; color: #526d82; font-size: 12px; line-height: 1.55; }
.codex-step-commands { display: flex; flex-direction: column; gap: 8px; margin-top: 10px; }
.codex-cmd {
  display: flex; align-items: center; gap: 10px; width: 100%; padding: 11px 14px;
  border: 1px solid #0f3d4a; border-radius: 8px; background: #16343f; color: #e7f6f8;
  font: 600 12.5px/1.45 Consolas, "Cascadia Mono", "Courier New", monospace;
  text-align: left; cursor: pointer; transition: border-color .15s, background .15s;
}
.codex-cmd:hover { border-color: #2aa0b5; background: #1a3f4c; }
.codex-cmd-prompt { flex: 0 0 auto; color: #5fd4c8; font-weight: 700; }
.codex-cmd code { flex: 1 1 auto; min-width: 0; overflow-wrap: anywhere; color: inherit; font: inherit; background: transparent; }
.codex-cmd-copy { flex: 0 0 auto; color: #8eb8c2; font-size: 15px; }
.codex-cmd:hover .codex-cmd-copy { color: #d6f3f7; }
.codex-cmd--note {
  cursor: default;
  background: transparent;
  border-style: dashed;
  border-color: #b0c4cc;
  color: #829ab1;
}
.codex-cmd--note:hover {
  border-color: #b0c4cc;
  background: transparent;
}
.codex-cmd--note .codex-cmd-prompt {
  color: #b0c4cc;
}
.codex-cmd--note code {
  color: #829ab1;
  font-style: italic;
}
@media (max-width: 760px) {
  .codex-guide-page { overflow: auto; padding: 12px; }
  .codex-guide-hero { align-items: stretch; flex-direction: column; padding: 20px; }
  .codex-guide-status { min-width: 0; }
  .codex-guide-toolbar { flex-wrap: wrap; }
  .codex-agent-link { width: 100%; margin-left: 0; }
  .codex-install-timeline { flex: none; overflow: visible; padding-inline: 0; }
}
</style>

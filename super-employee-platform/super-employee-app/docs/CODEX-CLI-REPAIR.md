# CodeX CLI 受控本地修复接入指南

本文说明「智维超级员工」如何将 OpenAI Codex CLI 接入故障处置链路：远程日志仍只走 SSH 白名单采集，CodeX 仅在**已审批**的本地 Maven 工作区内执行最小修复，随后再走 Maven 测试与独立部署审批。

## 1. 安全模型

| 边界 | 约束 |
|------|------|
| 远程主机 | 仅 SSH 只读模板（如 `HOST_HEALTH`、`JOURNAL_LOGS`、`JAVA_APPLICATION_LOGS`），禁止任意 Shell |
| CodeX 进程 | 默认关闭；未人工批准时不会启动 |
| 工作区 | 必须是含 `pom.xml` 的本地 Maven 目录；禁止路径越界 |
| 沙箱参数 | 固定 `codex exec --sandbox workspace-write` |
| Prompt 禁令 | 禁止 `git commit` / `git push` / `deploy` / `curl` / `wget` |
| 文件范围 | 仅允许审批时声明的工作区相对路径 |
| Diff 审计 | 执行后读取 `git diff` / `git diff --name-only`；越界则失败并失效测试候选 |
| 部署 | `DEPLOY_PATCH` 仍单独人工审批；测试通过只产生部署候选 |

## 2. 端到端流程

```text
新建事件
  → LOG_QUERY（SSH 证据，写入 evidenceReference）
  → PATCH_PREVIEW（补丁计划 + patchFingerprint，状态 PATCH_READY）
  → CODEX_LOCAL_REPAIR（AgentScope ASK → 审批中心批准 → 启动 CLI）
  → TEST_VERIFICATION（必须使用 CodeX 返回的实际 diff；Maven test/verify）
  → DEPLOY_PATCH（再次人工审批 → 受控部署命令）
```

状态与门禁由 `IncidentLifecycleService` 强制：

1. **beforeCodexRepair**：要求 `PATCH_READY`、已有证据、补丁指纹未变、允许文件非空。
2. **codexRepairCompleted**：写入实际 diff / 变更文件；清除旧 `verificationPassed`；失败或越界抛错。
3. **verificationCompleted**：若存在 `codexActualDiff`，入参 diff 必须与其一致；通过后才可请求部署审批。
4. **beforeDeploy**：补丁指纹必须与验证指纹、已批准部署键一致。

## 3. 本机安装 Codex CLI（Windows）

### 3.1 前置条件

- Node.js **≥ 22**
- 可访问 OpenAI / ChatGPT 账号或 API Key
- 工作区为 Git 仓库（需用 `git diff` 审计）

```powershell
node --version
npm --version
```

### 3.2 Windows 安装（推荐完整步骤）

包名必须是 `@openai/codex`（不要安装裸包 `codex`）。若曾出现 `Missing optional dependency @openai/codex-win32-x64`，按下列顺序执行（建议管理员 PowerShell）：

```powershell
# 1）彻底卸载残留
npm uninstall -g @openai/codex
npm uninstall -g @openai/codex-win32-x64
npm cache clean --force

# 2）切换到当前 Node 版本（使用 nvm 时必做；版本按本机调整）
nvm use 22.20.0
node -v
where.exe node

# 3）切换国内镜像，避免跨平台包下载失败（可选，网络不稳时建议）
npm config set registry https://registry.npmmirror.com

# 4）强制指定平台架构安装（关键）
npm install -g @openai/codex@latest --cpu=x64 --os=win32

# 5）单独补丁 Windows 64 位二进制包（兜底）
npm install -g @openai/codex-win32-x64

# 6）验证
codex.cmd --version
```

若出现 `codex: command not found`：

```powershell
npm bin -g
# 将输出目录加入用户 PATH，例如：
# C:\Users\<你>\AppData\Roaming\npm
# 或 nvm 路径：D:\Tools\nvm\v22.20.0
```

或在平台中配置绝对路径：

```powershell
$env:CODEX_CLI_EXECUTABLE="C:\Users\<你>\AppData\Roaming\npm\codex.cmd"
# nvm 场景也可能是：
# $env:CODEX_CLI_EXECUTABLE="D:\Tools\nvm\v22.20.0\codex.cmd"
```

注意：nvm 下的 Node（如 `D:\Tools\nvm\v22.20.0`）与 `D:\Program Files\nodejs` 的全局包互不影响，平台 `CODEX_CLI_EXECUTABLE` 必须指向实际验证通过的那份。

### 3.3 鉴权与冒烟

```powershell
codex login
# 或
$env:OPENAI_API_KEY="sk-..."

cd D:\path\to\your-maven-project
codex exec --sandbox workspace-write "只确认 pom.xml 是否存在，不要修改任何文件"
```

升级：

```powershell
npm update -g @openai/codex
```

> Windows 原生沙箱仍偏实验性；若生产要求更强隔离，可在 WSL2 中安装同一套 `@openai/codex`，并将平台的 `CODEX_WORKSPACE` / `CODEX_CLI_EXECUTABLE` 指向 WSL 可访问路径与可执行文件。

官方仓库：https://github.com/openai/codex

## 4. 平台配置

### 4.1 环境变量

| 变量 | 含义 | 默认 |
|------|------|------|
| `CODEX_REPAIR_ENABLED` | 是否启用 CodeX 网关 | `false` |
| `CODEX_WORKSPACE` | 本地 Maven 工作区绝对路径 | 空 |
| `CODEX_CLI_EXECUTABLE` | CLI 可执行文件 | `codex` |
| `CODEX_TIMEOUT_SECONDS` | 进程超时秒数 | `300` |
| `REACT_ENABLED` / `AI_ENABLED` | 启用 ReAct 协调智能体 | `false` |

对应 `application.yml`：

```yaml
super-employee:
  codex:
    enabled: ${CODEX_REPAIR_ENABLED:false}
    executable: ${CODEX_CLI_EXECUTABLE:codex}
    workspace: ${CODEX_WORKSPACE:}
    timeout-seconds: ${CODEX_TIMEOUT_SECONDS:300}
```

`CodexCliRepairGateway` 仅在 `super-employee.codex.enabled=true` 时注册；未启用时工具不会出现在协调智能体 Toolkit 中。

### 4.2 Maven 验证（建议与 CodeX 同工作区）

```yaml
super-employee:
  tools:
    enabled: true
    working-directory: D:\path\to\your-maven-project
    verification-command: "mvn test"
    allowed-commands: "mvn,mvn.cmd"
```

PowerShell 示例：

```powershell
$env:CODEX_REPAIR_ENABLED="true"
$env:CODEX_WORKSPACE="D:\path\to\your-maven-project"
$env:CODEX_CLI_EXECUTABLE="codex"
$env:REACT_ENABLED="true"
```

开发 / 测试 profile：`application-dev.yml` 默认开启 CodeX（可用 `CODEX_REPAIR_ENABLED=false` 关闭）；`application-test.yml` 保持关闭。

工作区路径可在管理台 **系统管理 → 工作区配置** 中设置，支持浏览服务器本机文件夹；界面保存优先于 YAML。

## 5. 数据库与工具授权

### 5.1 注册工具

达梦等环境执行：

- `super-employee-app/docs/sql/codex-repair-migration.sql`

该脚本仅插入 `managed_tool.tool_code = CODEX_LOCAL_REPAIR`（风险等级 `HIGH`），**不会**自动授权给 `OPS_REACT`。

全量 schema 已在 `docs/sql/schema.sql` 中同步种子数据。

### 5.2 授权给 OPS_REACT

在管理台 **工具管理 / Agent 授权** 中：

1. 确认工具 `CODEX_LOCAL_REPAIR` 已启用。
2. 为智能体 `OPS_REACT` 勾选该工具。

能力层强制：即使风险被标为 `LOW`，`CODEX_LOCAL_REPAIR` 也始终进入「需审批」集合；`ReactAgentFactory` 权限上下文对该工具固定 `PermissionBehavior.ASK`。

## 6. 运行时调用约定

工具名：`CODEX_LOCAL_REPAIR`

关键参数：

| 参数 | 说明 |
|------|------|
| `title` / `diffPreview` / `summary` | 必须与已登记的补丁预览指纹一致 |
| `prompt` | 交给 Codex 的受限修复说明（不得含禁令词） |
| `allowedFiles` | 允许修改的相对路径，英文逗号分隔，如 `src/main/java/App.java` |

执行后返回 JSON：`success`、`output`、`diff`、`changedFiles`、`withinScope`。

后续 `TEST_VERIFICATION` 的 `diffPreview` **必须**使用返回的实际 `diff`（上下文键 `codexActualDiff`），否则生命周期会拒绝。

`DEPLOY_PATCH` 与 CodeX 解耦：测试通过只产生部署候选，部署仍需单独审批。

## 7. 关键实现位置

| 组件 | 职责 |
|------|------|
| `CodexCliRepairGateway` | 工作区校验、禁令检查、CLI 启动、diff 采集与范围判定 |
| `ReactAgentFactory` | 注册 `CODEX_LOCAL_REPAIR`、ASK 规则、审计脱敏 |
| `IncidentLifecycleService` | 修复前后门禁、指纹更新、验证/部署阻断 |
| `AgentCapabilityService` | 工具授权汇总；CodeX 始终需审批 |
| `AgentSessionRepository` | 将 CodeX 标为不可自动重试工具 |
| `CommandIncidentToolGateway` | 同工作区受控 `mvn test` / `mvn verify` |

单元测试：

- `CodexCliRepairGatewayTest`
- `IncidentLifecycleServiceTest`（CodeX 相关用例）
- `ReactAgentFactoryTest`（ASK / 未启用时省略工具）

## 8. 验收清单

- [ ] `CODEX_REPAIR_ENABLED=false` 时，Toolkit 不含 `CODEX_LOCAL_REPAIR`
- [ ] 审批中心未批准前，本机无 `codex` 进程启动
- [ ] Prompt 含 `git commit` / `deploy` / `curl` 等被拒绝
- [ ] `allowedFiles` 含 `../` 或绝对路径被拒绝
- [ ] Diff 出现未批准文件时失败，并清除验证候选
- [ ] 验证必须绑定 CodeX 实际 diff；指纹变化后旧部署审批失效
- [ ] 工具调用、CLI 输出、diff、Maven 结果、审批记录可审计关联

## 9. 常见问题

| 现象 | 排查 |
|------|------|
| 工具未出现 | 检查 `codex.enabled`、工作区是否含 `pom.xml`、是否授权给 `OPS_REACT` |
| `Missing optional dependency @openai/codex-win32-x64` | Windows 原生包未装上。按 §3.2 完整重装步骤执行 |
| 启动失败 `CodeX CLI 无法启动` | 检查 `CODEX_CLI_EXECUTABLE`、PATH、Node 版本 |
| 鉴权失败 | 执行 `codex login` 或配置 `OPENAI_API_KEY` |
| 验证报「必须使用实际差异」 | 将 CodeX 返回的 `diff` 原样传入 `TEST_VERIFICATION` |
| 部署被拒 | 补丁指纹与验证指纹不一致，或缺少当前版本的部署审批 |

### 9.1 Windows：缺少 `@openai/codex-win32-x64`

直接按 [§3.2 Windows 安装](#32-windows-安装推荐完整步骤) 的 1～6 步重装即可，核心是：

```powershell
npm install -g @openai/codex@latest --cpu=x64 --os=win32
npm install -g @openai/codex-win32-x64
codex.cmd --version
```

## 10. 推荐演练顺序

1. 仅安装并本地 `codex exec` 冒烟。
2. 执行 SQL 迁移并授权工具，但保持 `CODEX_REPAIR_ENABLED=false`，确认工具不可见。
3. 打开 CodeX + 同工作区 Maven 验证，用预发事件走通：证据 → 预览 → **批准 CodeX** → 测试 → **批准部署**。
4. 故意提交越界文件列表或越界 diff，确认阻断与候选失效。

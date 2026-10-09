# 智维超级员工平台

面向服务器运维故障自愈的 AgentScope-Java 多智能体平台。

## 模块结构

- `super-employee-core`：领域模型、工具网关抽象与事件状态机。
- `super-employee-app`：Spring Boot 启动器、REST 接口、JWT 鉴权、资源管理、AgentScope 运行时和 JDBC 持久化。

## 已实现能力

- AgentScope 协调智能体及证据收集、根因分析、修复规划、验证、部署规划子智能体。
- 会话 Checkpoint、租约、超时回收、瞬时故障重试、工具调用审计与人工审批恢复。
- 仅允许调用授权工具和资源；补丁预览、验证、审批、部署由后端状态机强制约束。
- 支持达梦、MySQL、Oracle、KingbaseES 的 JDBC 持久化。

## 开发环境默认开启整套流程

`dev` profile 默认打开：

| 能力 | 配置项 | 默认 |
|------|--------|------|
| 模型 / ReAct | `super-employee.model.enabled` / `super-employee.react.enabled` | `true` |
| SSH 只读诊断 | `super-employee.ssh.enabled` | `true` |
| Maven 验证工具 | `super-employee.tools.enabled` | `true` |
| CodeX 本地修复 | `super-employee.codex.enabled` | `true` |

Maven / CodeX 工作区默认使用启动时的 `user.dir`（须含 `pom.xml`）。数据库和密钥由被 Git 忽略的私有 YAML 提供，不需要设置电脑环境变量。首次启动前：

```powershell
cd super-employee-platform/super-employee-app
New-Item -ItemType Directory -Force local-config
if (-not (Test-Path local-config/application-private.yml)) {
    Copy-Item application-private.yml.example local-config/application-private.yml
}
# 填好私有配置后启动；IDE 也使用当前目录作为工作目录
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

若本机未装 Codex CLI，在私有 YAML 中设置 `super-employee.codex.enabled: false`。关闭 AI 流程时，设置 `super-employee.model.enabled`、`super-employee.react.enabled` 为 `false`。

前端开发服务器默认连 `http://localhost:8088`；首次管理员由私有文件中的 `super-employee.security.initial-admin` 配置创建。具体字段见 [DATABASE.md](DATABASE.md)。

## SSH 只读诊断

SSH 诊断默认关闭。启用前必须为目标主机授予 `HOST` 资源读取权限、启用凭据访问策略，并为每台主机配置 SHA-256 主机密钥指纹：

在私有 YAML 中按需设置 `super-employee.ssh.enabled` 和 `super-employee.ssh.host-key-fingerprints`，真实指纹不写入公开配置。

当前支持 `HOST_HEALTH`、`SERVICE_STATUS`、`JOURNAL_LOGS`、`DISK_USAGE`、`JAVA_APPLICATION_LOGS`、`APP_LOG_FILE` 等固定只读模板；模型无法提交任意 Shell 命令。生产环境应先在预发主机完成主机密钥、权限和超时演练验证。

按主机白名单路径读取应用日志文件、以及配置主机时的连通性探测，见：

- [super-employee-app/docs/HOST-APP-LOG-PATH.md](super-employee-app/docs/HOST-APP-LOG-PATH.md)

## CodeX CLI 受控本地修复

`dev` profile 默认开启 CodeX（`CODEX_REPAIR_ENABLED=true`），工作区与 Maven 验证共用 `CODEX_WORKSPACE` / `MAVEN_WORKSPACE` / `user.dir`。远程证据仍只走 SSH；本地修复必须在补丁预览之后、Maven 验证之前，且每次调用都需人工审批。完整安装、配置、授权与验收流程见：

- [super-employee-app/docs/CODEX-CLI-REPAIR.md](super-employee-app/docs/CODEX-CLI-REPAIR.md)

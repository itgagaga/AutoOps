# 智维超级员工

面向服务器运维故障自愈的多智能体平台。系统定时拉取应用日志，发现异常后由协调 Agent 编排日志提取、根因分析、代码修复与测试验证，经人工审批后完成受控部署，形成可审计的自治运维闭环。

当前交付版本：**V1.2**（多技术栈混合部署 + 知识库导入预生成 + 运维体验收敛）。

## 能力概览

- **自治处置链路**：日志巡检 → 异常检测 → 多 Agent 分析 → 补丁预览 → 本地修复 → Maven/多栈验证 → 人工审批部署。
- **多智能体编排**：基于 AgentScope-Java，内置协调 Agent 与四类专业 Agent（日志提取、根因分析、代码修复、测试验证），默认随主应用同进程启动。
- **受控执行**：仅允许调用授权工具与资源；SSH 仅只读模板，禁止任意 Shell；补丁预览、验证、审批、部署由后端状态机强制约束。
- **本地修复引擎**：支持 CodeX、Cursor、Claude Code，可在环境向导中切换；修复必须在补丁预览之后、验证之前，且每次调用需人工审批。
- **多技术栈**：后端支持 MAVEN / JAVA_WAR / GRADLE / NODE / GO / PYTHON / DOCKER；端侧支持 NONE / WEB_NPM / UNI_APP / MP_WEIXIN_NATIVE。
- **知识库**：文档导入（txt / md / docx 等）+ Agent 预生成草稿，人工确认后入库。
- **运维工作台**：主机/凭据/数据库资源中心、SSH / DB / Git 客户端、审批中心、执行回溯、审计与 RBAC。

## 仓库结构

```text
intelligent-ops-tools/
├── super-employee-platform/     # 后端平台（Spring Boot）
│   ├── super-employee-core/     # 领域模型、工具网关、事件状态机
│   └── super-employee-app/      # REST、JWT、AgentScope 运行时、JDBC 持久化
├── super-employee-agents/       # 专业 Agent（默认同 JVM；亦可独立进程）
│   ├── log-extraction-agent     # 8101  日志提取
│   ├── root-cause-analysis-agent# 8102  根因分析
│   ├── code-repair-agent        # 8103  代码修复
│   └── test-verification-agent  # 8104  测试验证
├── super-employee-web/          # 前端（Vue 3 + Element Plus + Vite）
├── delivery/                    # 交付包：SQL、验收与部署清单
├── scripts/                     # 交付文档生成脚本
├── 测试工程/                     # 运维演练用缺陷样例工程
└── agentscope-java-main/        # AgentScope-Java 依赖源码（框架）
```

四个内置 Agent 已集成到 `super-employee-app`，启动主项目即同步注册，**默认不需要**单独启动 8101–8104。独立进程模式仅用于隔离测试或远程部署，详见 [super-employee-agents/README.md](super-employee-agents/README.md)。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.5.8、MyBatis-Plus、Spring Security、JWT |
| Agent | AgentScope-Java 2.0.0（ReAct 协调 + 子智能体） |
| 前端 | Vue 3、Element Plus、Vite 6 |
| 数据库 | 达梦 8（默认）、MySQL 8、Oracle 12c+、KingbaseES |
| 诊断 / 修复 | SSH 只读模板、CodeX / Cursor / Claude CLI、Maven 验证 |

## 环境要求

- JDK **17+**（构建产物时会自动寻找 JDK 17+，避免 `JAVA_HOME` 指向 JDK 8）
- Maven 3.8+
- Node.js 18+（前端开发）；若使用 Codex CLI，建议 Node.js **≥ 22**
- 达梦 / MySQL / Oracle / Kingbase 之一
- 可选：Codex CLI、Cursor CLI（`agent`）、Claude Code CLI（`claude`）
- Windows 克隆/提交前请开启长路径（AgentScope 源码路径会超过 260 字符）：`git config core.longpaths true`

## 快速开始

### 1. 初始化数据库

新环境（推荐达梦）：

1. 创建用户 / 模式
2. 执行 [`delivery/sql/dameng/01-install-full.sql`](delivery/sql/dameng/01-install-full.sql)
3. 按所选库配置数据源（见 [super-employee-platform/DATABASE.md](super-employee-platform/DATABASE.md)）

存量库升级请按 [`delivery/sql/dameng/02-upgrade-catalog.txt`](delivery/sql/dameng/02-upgrade-catalog.txt) 顺序执行增量脚本。MySQL / Oracle / Kingbase 全量脚本见 `delivery/sql/other-db/`。

### 2. 启动后端

在 `super-employee-platform/super-employee-app` 目录启动。数据库连接与密钥通过本地私有 YAML 加载，不需要设置电脑环境变量：

```powershell
cd super-employee-platform/super-employee-app
New-Item -ItemType Directory -Force local-config
if (-not (Test-Path local-config/application-private.yml)) {
    Copy-Item application-private.yml.example local-config/application-private.yml
}
# 编辑私有文件、填好空字段后再启动
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

私有文件已被 Git 忽略，只留在本机或部署服务器；不要放入 `src/main/resources` 或打包进 JAR。公开仓库只保留空模板。IDE 的工作目录也应设置为 `super-employee-app`。详细字段见 [数据库与私有配置](super-employee-platform/DATABASE.md)。

后端默认端口 **8088**。`dev` profile 会同时激活 `dameng`，并默认打开整套自治流程：

| 能力 | 私有 YAML 中可覆盖的配置项 | 开发默认 |
|------|----------|----------|
| 模型 / ReAct | `super-employee.model.enabled` / `super-employee.react.enabled` | `true` |
| SSH 只读诊断 | `super-employee.ssh.enabled` | `true` |
| Maven 验证工具 | `super-employee.tools.enabled` | `true` |
| CodeX 本地修复 | `super-employee.codex.enabled` | `true` |

本机未装 Codex CLI 或需关闭 AI 流程时，在私有 YAML 中设置：

```yaml
super-employee:
  codex:
    enabled: false
  model:
    enabled: false
  react:
    enabled: false
```

切换其他数据库时，先在私有文件中填写对应的完整连接串，再选择 Profile，例如：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev,mysql"
```

### 3. 启动前端

```powershell
cd super-employee-web
npm install
npm run dev
```

浏览器打开 [http://localhost:5173](http://localhost:5173)。Vite 将 `/api`、`/actuator` 代理到 `http://localhost:8088`。后端地址不同时，可设置 `VITE_API_PROXY`。

首次管理员账号密码在私有文件的 `super-employee.security.initial-admin` 中填写，登录后应立即修改。

## 生产部署（运维端）

运维控制台由三部分组成，建议放在**同一台能 SSH 到业务主机**的机器上（修复 CLI、Maven 验证都在后端本机执行）：

| 组件 | 产物 | 端口 |
|------|------|------|
| 数据库 | 达梦等，先执行 SQL | 按库 |
| 后端 `super-employee-app` | 可执行 JAR | **8088** |
| 前端 `super-employee-web` | `dist/` 静态资源 | 80 / 443（Nginx） |

推荐用 Nginx 同源反代：浏览器只访问前端域名，`/api`、`/actuator` 转到 8088，此时 **不必** 设置 `VITE_API_BASE_URL`。

### 1. 初始化数据库

新环境执行 `delivery/sql/dameng/01-install-full.sql`。存量库按 `delivery/sql/dameng/02-upgrade-catalog.txt` 升级。`prod` 默认同时激活 `dameng`，连接串、账号和密码在服务器的私有 YAML 中填写。

### 2. 构建后端

需先能解析 AgentScope 依赖（本仓库含 `agentscope-java-main` 时，可先 `mvn -f agentscope-java-main/pom.xml install -DskipTests`）。

```powershell
cd super-employee-platform
mvn -f .\super-employee-app\pom.xml -DskipTests package
# 产物：super-employee-app/target/super-employee-app-0.1.0-SNAPSHOT.jar
```

### 3. 构建前端

```powershell
cd super-employee-web
npm ci
npm run build
# 产物：dist/
```

前后端不同源时，构建前设置 `VITE_API_BASE_URL=https://ops-api.example.com`（不要末尾斜杠）。

### 4. 启动后端（prod）

将空模板复制到服务器启动目录的 `local-config/application-private.yml`，填写数据库和密钥，再从该目录启动 JAR。私有文件缺失时启动会停止：

```powershell
java -jar super-employee-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

健康检查：`GET http://127.0.0.1:8088/actuator/health`。

### 5. Nginx 示例

```nginx
server {
    listen 80;
    server_name ops.example.com;
    root /opt/super-employee-web/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
    location /api/ {
        proxy_pass http://127.0.0.1:8088;
        proxy_set_header Host $host;
        proxy_set_header Authorization $http_authorization;
        client_max_body_size 210m;
    }
    location /actuator/ {
        proxy_pass http://127.0.0.1:8088;
    }
}
```

### 6. 上线后配置

1. 用管理员登录，立即改密。
2. 资源中心登记主机（SSH 指纹、日志白名单）和凭据。
3. Agent 管理页检测修复引擎 CLI，确认「环境可用」。
4. 创建运维任务：工作区、主机、拉日志周期、是否自动分析。
5. 小流量试跑一条任务，再开全量巡检。

生产使用 `prod` Profile，并在私有文件中按需开启 SSH 和配置已核验的主机指纹。Windows 服务可用 NSSM / 计划任务保活；Linux 可用 systemd。更细的验收项见 `delivery/上线验收清单.docx`（由 `scripts/build_delivery_package.py` 生成）。

## 处置流程

```text
运维任务周期拉日志
  → 异常检测并创建故障事件
  → LOG_QUERY（SSH 只读证据）
  → 根因分析 / 修复规划（PATCH_PREVIEW）
  → 本地修复引擎（CodeX / Cursor / Claude，需审批）
  → TEST_VERIFICATION（Maven / 多栈验证）
  → DEPLOY_PATCH（再次人工审批后受控部署）
  → 经验回填知识库
```

会话支持 Checkpoint、租约、超时回收、瞬时故障重试、工具调用审计与审批恢复。

## 主要配置

完整通用配置见 `super-employee-platform/super-employee-app/src/main/resources/application.yml`。以下配置可在本地私有 YAML 中填写或覆盖：

| 配置项 | 说明 |
|------|------|
| `super-employee.web.allowed-origin-patterns` | 前端允许来源 |
| `super-employee.repair.engine` | 默认修复引擎：`codex` / `cursor` / `claude` |
| `super-employee.codex.workspace` / `super-employee.tools.working-directory` | 本地修复与 Maven 工作区 |
| `super-employee.ssh.host-key-fingerprints` | 主机 ID 与 SHA-256 指纹映射 |
| `super-employee.agents.shared-token` | 远程扩展 Agent 心跳令牌 |
| `super-employee.agents.local-enabled` | 是否在主进程内启用四个专业 Agent，默认 `true` |
| `agentscope.dashscope.model-name` | 通义模型名，开发默认 `qwen-plus` |

生产环境必须：

- 更换 JWT Secret、凭据加密密钥与初始管理员密码
- 显式开启 SSH，并为每台主机配置已核验的 SHA-256 指纹
- 为目标主机授予 `HOST` 读取权限，启用凭据访问策略
- 将部署命令从开发用的 `mvn verify` 改为真实流水线，并收紧命令白名单

SSH 当前支持的只读模板：`HOST_HEALTH`、`SERVICE_STATUS`、`JOURNAL_LOGS`、`DISK_USAGE`、`JAVA_APPLICATION_LOGS`、`APP_LOG_FILE`。按主机白名单读取应用日志见 [HOST-APP-LOG-PATH.md](super-employee-platform/super-employee-app/docs/HOST-APP-LOG-PATH.md)。

## 文档索引

| 文档 | 内容 |
|------|------|
| [super-employee-platform/README.md](super-employee-platform/README.md) | 平台模块、开发默认能力、SSH / CodeX 说明 |
| [super-employee-platform/DATABASE.md](super-employee-platform/DATABASE.md) | 多库 Profile 与增量脚本 |
| [CODEX-CLI-REPAIR.md](super-employee-platform/super-employee-app/docs/CODEX-CLI-REPAIR.md) | CodeX 安装、授权与验收 |
| [HOST-APP-LOG-PATH.md](super-employee-platform/super-employee-app/docs/HOST-APP-LOG-PATH.md) | 主机应用日志路径与连通性探测 |
| [super-employee-agents/README.md](super-employee-agents/README.md) | 专业 Agent 独立进程与心跳 |
| [super-employee-web/README.md](super-employee-web/README.md) | 前端开发与代理 |
| [delivery/README.md](delivery/README.md) | 交付包、SQL 与验收清单 |
| `智维超级员工-AI提效说明.docx` | 传统对照、分环节提效、试点 KPI（由脚本生成） |
| `智维超级员工-操作流程技术说明.docx` | 端到端操作流程、状态机与截图占位（由脚本生成） |

## 交付包

`delivery/` 用于离线移交，主要包含：

- `sql/dameng/01-install-full.sql`：达梦空库一键初始化
- `sql/dameng/upgrade/`：存量升级增量脚本
- 配置与部署清单、上线验收清单等 Word 文档

重新生成路演 / 手册：

```powershell
python .\scripts\build_delivery_package.py
python .\scripts\generate_ops_manual.py
python .\scripts\generate_pitch_deck.py
python .\scripts\generate_pitch_ops_doc.py
python .\scripts\generate_results_doc.py
python .\scripts\generate_ai_efficiency_doc.py
python .\scripts\generate_ops_flow_doc.py
```


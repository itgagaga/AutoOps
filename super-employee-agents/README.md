# 个体 Agent 兼容工程

四个内置 Agent 现已集成到 `super-employee-app`，启动主项目时会同步注册并在同一 JVM
内执行，默认不需要启动本目录下的 8101–8104 服务。

本目录仅保留给进程隔离测试或未来远程部署场景；如无此需求，请直接启动主项目。

每个个体 Agent 可单独启动，向平台上报心跳，并提供 `/v1/tasks` 供协调器调用。

| 工程 | 端口 | managed_agent.id |
|---|---|---|
| log-extraction-agent | 8101 | `00000000-0000-0000-0000-000000000401` |
| root-cause-analysis-agent | 8102 | `...402` |
| code-repair-agent | 8103 | `...403` |
| test-verification-agent | 8104 | `...404` |

```bash
# 安装契约与 starter
mvn -f super-employee-agents/pom.xml clean install -DskipTests

# 可选的独立进程兼容模式（平台需已在 8088 运行）
cd super-employee-agents/log-extraction-agent
mkdir local-config
cp ../application-private.yml.example local-config/application-private.yml
# 填写平台地址与 Agent 令牌后再启动
mvn spring-boot:run
```

私有文件的路径相对进程工作目录；每个独立 Agent 使用自己工作目录下的 `local-config/application-private.yml`。真实文件已忽略，不能提交或打包进 JAR。四个 Agent 的模板相同，`zhiwei.agent.shared-token` 必须与平台私有文件中的令牌一致。

心跳：`POST {platform}/api/agents/{id}/heartbeat`，Header `X-Agent-Token` 与平台 `super-employee.agents.shared-token` 一致。  
超时未上报 → 管理台 `OFFLINE`，协调器不可 `agent_spawn`。

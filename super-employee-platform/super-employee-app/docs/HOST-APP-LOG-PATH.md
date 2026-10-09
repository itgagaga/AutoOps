# 主机指定路径日志读取与连通性校验

本文说明如何在资源中心为每台主机配置**允许读取的日志绝对路径白名单**与 **SSH 指纹列表**，并通过连通性探测保证启用后可取到日志。

## 1. 流程

```text
资源中心「主机管理」以列表配置指纹 + 日志路径（文件或目录）
  → 建议先 enabled=false 保存
  → 探测连通性（登录 + 文件 test -r / 目录 test -d && test -r）
  → 启用（启用时也会强制探测）
  → Agent / 运维任务 APP_LOG_FILE + logPath（命中白名单文件，或白名单目录及其下文件）
  → 远端执行：
       · 文件：tail -n <N> -- <path>
       · 目录：优先取名含当日日期的日志，否则取最新 *.log / *.log.*，再 tail
```

## 2. 页面配置（运维）

### 2.1 日志路径：列表录入

在主机编辑弹窗中「允许读取的日志路径（白名单列表）」逐条添加，例如：

- `/opt/app/logs/app.log`（固定文件）
- `/data/rcpx/insiis-rcpx-hlw/logs`（分日滚动时只配目录即可）
- `/var/log/app/error.log`

不得填写相对路径、`..` 或含空格/Shell 元字符的路径。

路径最后一段**不含扩展名**时按**目录**处理；含点号（如 `app.log`、`app_log.2026-07-28.log`）按**文件**处理。

### 2.2 SSH 指纹：列表录入 + 获取方法

弹窗内会显式提示获取命令。在能访问目标机的运维终端执行：

```powershell
ssh-keyscan -p <端口> <主机IP> 2>$null | ssh-keygen -lf - -E sha256
```

Linux / macOS：

```bash
ssh-keyscan -p <端口> <主机IP> 2>/dev/null | ssh-keygen -lf - -E sha256
```

从输出中复制 `SHA256:...` 整段，添加到「SSH 主机密钥指纹（列表）」；一台主机可添加多条（RSA/ECDSA/ED25519）。

### 2.3 连通性探测

编辑已保存主机时可点「探测连通性」。启用主机时后端也会强制探测，失败则拒绝启用。

## 3. API 字段（列表）

创建/更新主机请求体使用**数组**：

```json
{
  "code": "APP_HOST_01",
  "name": "应用机",
  "ipAddress": "10.0.0.12",
  "sshPort": 22,
  "osType": "LINUX",
  "environment": "DEV",
  "credentialId": "<SSH凭据ID>",
  "hostKeyFingerprints": [
    "SHA256:IWf48AvxwyoL41kiGt38kwGAcuZ+MmwlYswbD/Zo6p4",
    "SHA256:HjYX8/jIjX3sqpB8Ygs2fcyzukbtmNKBZCAtNW9gaJY"
  ],
  "allowedLogPaths": [
    "/opt/app/logs/app.log",
    "/data/rcpx/insiis-rcpx-hlw/logs"
  ],
  "enabled": false
}
```

查询主机时同样返回上述数组字段。库内仍以 `|` 拼接存储。

显式探测：

```http
POST /api/resources/hosts/{id}/connectivity-check
```

达梦迁移：`docs/sql/host-log-path-migration-dameng.sql`。  
环境变量 `SSH_HOST_KEY_FINGERPRINTS` 仍可作为指纹回退。

## 4. Agent / 运维任务取日志

```text
template: APP_LOG_FILE
hostId:   <主机ID>
logPath:  /data/rcpx/insiis-rcpx-hlw/logs
maxLines: 200
```

`logPath` 规则：

1. 等于白名单中的文件 → 直接 `tail`
2. 等于白名单中的目录 → 自动选当日/最新日志再 `tail`
3. 位于白名单目录下的具体文件（如 `.../logs/insii-rcpx-hlw_log.2026-07-28.log`）→ 直接 `tail`

## 5. 权限前提

- `SSH_DIAGNOSTIC_ENABLED=true`
- `OPS_REACT` 拥有 `LOG_QUERY` 与目标主机 `HOST` 读权限
- 策略允许凭据访问

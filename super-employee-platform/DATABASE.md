# 数据库与私有配置

数据库连接串、账号、密码及安全密钥放在本地 `local-config/application-private.yml` 中。应用通过 `spring.config.import` 加载该文件，不需要设置电脑环境变量。

公开的数据库 Profile 只保留驱动、连接池等通用参数：

| Profile | 数据库 | 初始化脚本 |
| --- | --- | --- |
| `dameng` | 达梦 8 | `schema-dameng.sql` |
| `mysql` | MySQL 8 | `schema-mysql.sql` |
| `oracle` | Oracle | `schema-oracle.sql` |
| `kingbase` | KingbaseES | `schema-kingbase.sql` |

`dev`、`prod` 默认激活达梦。使用其他数据库时，显式选择对应 Profile，例如 `dev,mysql` 或 `prod,oracle`，并在私有文件中填写该数据库的完整连接串。

## 首次配置

在 `super-employee-platform/super-employee-app` 目录执行：

```powershell
New-Item -ItemType Directory -Force local-config
if (-not (Test-Path local-config/application-private.yml)) {
    Copy-Item application-private.yml.example local-config/application-private.yml
}
```

编辑私有文件，填写 `spring.datasource.url`、`username`、`password`，以及模型 API Key、JWT 密钥、凭据加密密钥、初始管理员账号和密码、Agent 令牌。模板中所有敏感字段均为空，不提供通用口令。

启动时工作目录必须是这个目录；IDE 的 Working directory 也设置到这里：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

私有文件缺失时启动会停止。空的 JWT 密钥、凭据加密密钥或首次管理员账号密码也不能用于正常启动。

## 部署

把私有配置单独放在服务器启动目录的 `local-config/application-private.yml`，然后从该目录启动 JAR。真实文件不放入 `src/main/resources`，不打包进 JAR，不提交到 Git。公开仓库只提交空的 `.example` 模板。

已有数据库的 schema、账号、密码和连接选项由部署方填写，本文不展示具体值。四种 JDBC 驱动以运行时依赖引入；若组织使用厂商授权驱动，替换 `super-employee-app/pom.xml` 中对应版本。

已有环境按版本执行增量 SQL；执行前备份并确认未重复执行。密钥更换不会自动重加密已有凭据，已有凭据密文需迁移或重新录入。

配置加载机制参见 [Spring Boot 外部配置文档](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)。

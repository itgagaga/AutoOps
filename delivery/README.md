# 智维超级员工 · 交付包（delivery）

版本：V1.2（多技术栈混合部署 + 知识库导入预生成 + 运维体验收敛）

## 本期成果要点

- 后端工程画像：MAVEN / JAVA_WAR / GRADLE / NODE / GO / PYTHON / DOCKER
- 端侧画像：NONE / WEB_NPM / UNI_APP / MP_WEIXIN_NATIVE（Web 可静态同步，小程序仅验证）
- 存量升级关键脚本：`15b` 前端目录 → `15c` 工程画像 → `15d` 多栈部署字段
- 知识库：文档导入（txt/md/docx 等）+ Agent 预生成草稿，人工确认后入库
- 体验：列表默认 20 条、表单字段说明、长弹窗内部滚动、Agent 三引擎环境检测

## 目录说明

| 文件/目录 | 用途 |
|-----------|------|
| sql/dameng/01-install-full.sql | 达梦**新空库**一键初始化（整合版，仅执行一次） |
| sql/dameng/02-upgrade-catalog.txt | 达梦**存量库**升级顺序与执行记录 |
| sql/dameng/upgrade/ | 按序号复制的增量 SQL，便于离线移交 |
| sql/other-db/ | 其他库全量 schema 副本（对齐需评估） |
| 交付物清单.docx | 最终成果与全部可交付索引 |
| 配置与部署清单.docx | 部署组件与关键配置项 |
| 上线验收清单.docx | 上线/移交验收表 |

## 数据库怎么用

### 新环境（推荐）
1. 创建达梦用户/模式
2. 执行 `sql/dameng/01-install-full.sql`
3. 在本地 `local-config/application-private.yml` 配置数据库与密钥后启动

### 已有环境升级
1. 备份数据库
2. 打开 `02-upgrade-catalog.txt`，跳过已执行脚本
3. 按序执行 `upgrade/` 下未执行文件（多栈务必含 15b→15c→15d）
4. 填写执行记录

### 其他数据库
MySQL / Oracle / Kingbase 全量脚本见 `sql/other-db/` 与源码 `docs/sql/schema-*.sql`。
近期增量以达梦为主，跨库交付请单独评估。

## 重新生成

```powershell
python .\scripts\build_delivery_package.py
python .\scripts\generate_ops_manual.py
python .\scripts\generate_pitch_deck.py
python .\scripts\generate_pitch_ops_doc.py
python .\scripts\generate_results_doc.py
python .\scripts\generate_ai_efficiency_doc.py
python .\scripts\generate_ops_flow_doc.py
```

项目根目录同步生成/更新：
- `智维超级员工-产品路演.pptx` / `-v2.pptx`
- `智维超级员工-运维与管理人员操作手册.docx`
- `智维超级员工-路演操作手册.docx`
- `智维超级员工-成果汇报.docx`（含功能截图占位，供人工粘贴）
- `智维超级员工-AI提效说明.docx`（试点对照与管理层沟通）
- `智维超级员工-操作流程技术说明.docx`（端到端流程 + 截图占位）

# -*- coding: utf-8 -*-
"""Generate 智维超级员工路演操作 Word 文档."""
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

TEAL = RGBColor(0x0B, 0x72, 0x85)
TEAL_DARK = RGBColor(0x0B, 0x4F, 0x5C)
SLATE = RGBColor(0x1F, 0x2A, 0x37)
GRAY = RGBColor(0x52, 0x60, 0x6D)


def set_run_font(run, size=11, bold=False, color=SLATE, name='微软雅黑'):
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = color
    run.font.name = name
    run._element.rPr.rFonts.set(qn('w:eastAsia'), name)


def add_heading_cn(doc, text, level=1):
    p = doc.add_heading(text, level=level)
    for run in p.runs:
        set_run_font(run, size={1: 18, 2: 14, 3: 12}.get(level, 11), bold=True, color=TEAL_DARK if level == 1 else TEAL)
    return p


def add_para(doc, text, size=11, bold=False, color=SLATE, space_after=6):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    run = p.add_run(text)
    set_run_font(run, size=size, bold=bold, color=color)
    return p


def add_bullets(doc, items, size=11):
    for item in items:
        p = doc.add_paragraph(style='List Bullet')
        p.clear()
        run = p.add_run(item)
        set_run_font(run, size=size, color=SLATE)
        p.paragraph_format.space_after = Pt(3)


def add_numbered(doc, items, size=11):
    for item in items:
        p = doc.add_paragraph(style='List Number')
        p.clear()
        run = p.add_run(item)
        set_run_font(run, size=size, color=SLATE)
        p.paragraph_format.space_after = Pt(4)


def add_code(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(8)
    run = p.add_run(text)
    set_run_font(run, size=9, color=SLATE, name='Consolas')
    run.font.name = 'Consolas'
    run._element.rPr.rFonts.set(qn('w:eastAsia'), '微软雅黑')
    # light background via shading
    from docx.oxml import OxmlElement
    shd = OxmlElement('w:shd')
    shd.set(qn('w:fill'), 'F5F8FA')
    shd.set(qn('w:val'), 'clear')
    p._p.get_or_add_pPr().append(shd)
    return p


def set_cell_text(cell, text, bold=False, color=SLATE, size=10):
    cell.text = ''
    p = cell.paragraphs[0]
    run = p.add_run(text)
    set_run_font(run, size=size, bold=bold, color=color)


def shade_header_row(row):
    from docx.oxml import OxmlElement
    for cell in row.cells:
        tc_pr = cell._tc.get_or_add_tcPr()
        shd = OxmlElement('w:shd')
        shd.set(qn('w:fill'), '0B7285')
        shd.set(qn('w:val'), 'clear')
        tc_pr.append(shd)
        for p in cell.paragraphs:
            for run in p.runs:
                run.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
                run.font.bold = True


def main():
    doc = Document()
    section = doc.sections[0]
    section.top_margin = Cm(2.2)
    section.bottom_margin = Cm(2.0)
    section.left_margin = Cm(2.4)
    section.right_margin = Cm(2.4)

    # Cover title
    t = doc.add_paragraph()
    t.alignment = WD_ALIGN_PARAGRAPH.CENTER
    t.paragraph_format.space_before = Pt(40)
    r = t.add_run('智维超级员工')
    set_run_font(r, size=28, bold=True, color=TEAL_DARK)

    st = doc.add_paragraph()
    st.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = st.add_run('产品路演操作手册')
    set_run_font(r, size=20, bold=True, color=TEAL)

    sub = doc.add_paragraph()
    sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = sub.add_run('面向服务器运维的多 Agent 全流程自治平台')
    set_run_font(r, size=12, color=GRAY)

    meta = doc.add_paragraph()
    meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    meta.paragraph_format.space_before = Pt(18)
    r = meta.add_run('版本：V1.2 ｜ 用途：路演演示与现场操作指引 ｜ 机密')
    set_run_font(r, size=10, color=GRAY)

    add_para(doc, '说明：本文档面向路演讲解人与现场操作员，覆盖环境准备、资源配置、演示剧本、审批门禁与常见问题。可与《智维超级员工-产品路演》PPT 配套使用。', size=10, color=GRAY)

    # 1
    add_heading_cn(doc, '一、文档目的与演示目标', 1)
    add_para(doc, '路演目标是用 8–15 分钟完成一条完整闭环：')
    add_bullets(doc, [
        '演示应用产生 ERROR 日志',
        '平台巡检发现异常并派生故障事件',
        '多 Agent 完成证据收集、根因分析、补丁预览',
        '人工审批后调用 Coding CLI 本地修码',
        '按工程画像完成验证与受控部署，审阅后完结',
    ])
    add_para(doc, '一句话金句（可配合 PPT）：不是 ChatOps 聊天机器人，而是带审批门禁与真实修码引擎的服务器运维超级员工。', bold=True)

    # 2
    add_heading_cn(doc, '二、角色与时间建议', 1)
    table = doc.add_table(rows=5, cols=3)
    table.style = 'Table Grid'
    headers = ['角色', '职责', '建议时长']
    for i, h in enumerate(headers):
        set_cell_text(table.rows[0].cells[i], h, bold=True, color=RGBColor(0xFF, 0xFF, 0xFF), size=10)
    shade_header_row(table.rows[0])
    data = [
        ('讲解人', '讲痛点、定位、架构与差异化，控制节奏', '全程'),
        ('操作员', '按剧本操作页面、触发缺陷、完成审批', '全程'),
        ('讲解节奏 A', 'PPT 讲解 + 关键页演示', '约 12 分钟'),
        ('讲解节奏 B', '短讲 + 端到端演示为主', '约 8 分钟'),
    ]
    for r_i, row in enumerate(data, start=1):
        for c_i, val in enumerate(row):
            set_cell_text(table.rows[r_i].cells[c_i], val, size=10)

    # 3
    add_heading_cn(doc, '三、环境准备清单', 1)
    add_heading_cn(doc, '3.1 软件依赖', 2)
    add_bullets(doc, [
        'JDK 17+（平台与演示工程构建；勿误用 JDK 8 作为构建 JAVA_HOME）',
        'Maven 3.x',
        'Node.js（前端开发服务）',
        'Git（工作区需有可解析的 HEAD；演示工程建议已有初始提交）',
        '至少安装一种本地修复引擎 CLI：Cursor / CodeX / Claude Code',
        '可 SSH 访问的目标主机（本机演示可用本机/WSL；日志路径须符合白名单规则）',
    ])

    add_heading_cn(doc, '3.2 工程与默认账号', 2)
    add_bullets(doc, [
        '平台后端：super-employee-platform / super-employee-app（dev profile）',
        '平台前端：super-employee-web',
        '演示工程：测试工程（demo-ops-app），日志默认 logs/demo-ops-app.log',
        '管理员密码由本地 application-private.yml 指定；首次登录后请改密或使用专用账号',
    ])

    add_heading_cn(doc, '3.3 启动顺序（操作员）', 2)
    add_numbered(doc, [
        '启动演示工程 demo-ops-app（确认日志文件持续产生 ERROR）',
        '启动平台后端（dev profile，开启 AI / ReAct / SSH 诊断 / 修复引擎相关开关）',
        '启动平台前端，浏览器登录管理端',
        '确认本地修复引擎 CLI 可用（按任务所选引擎：Cursor / CodeX / Claude）',
    ])

    add_heading_cn(doc, '3.4 常用启动命令参考', 2)
    add_para(doc, '演示工程：', bold=True)
    add_code(doc, 'cd <测试工程目录>\nmvn spring-boot:run')
    add_para(doc, '平台后端：', bold=True)
    add_code(doc, 'cd super-employee-platform\nmvn -f .\\super-employee-app\\pom.xml spring-boot:run "-Dspring-boot.run.profiles=dev"')
    add_para(doc, '平台前端：', bold=True)
    add_code(doc, 'cd super-employee-web\nnpm install\nnpm run dev')
    add_para(doc, '手动补打缺陷日志（可选）：', bold=True)
    add_code(doc, 'cd <测试工程目录>\n.\\scripts\\trigger-faults.ps1')

    add_heading_cn(doc, '3.5 本机日志白名单注意点', 2)
    add_bullets(doc, [
        '平台通过 SSH 只读模板拉日志；白名单路径需为绝对路径，且匹配安全正则（不宜含中文路径段）。',
        'Windows 本机演示时，推荐在 WSL 中建立软链接，例如将演示工程 logs 映射到 /home/<user>/demo-ops-app/logs，白名单填写该 Linux 路径。',
        '配置主机后务必先「探测连通性」，再启用主机。',
    ])

    # 4
    add_heading_cn(doc, '四、路演前配置（一次性）', 1)
    add_heading_cn(doc, '4.1 凭据管理', 2)
    add_numbered(doc, [
        '进入「凭据管理」，新增 SSH 凭据（用户名/密钥或密码，按环境选择）。',
        '确认密钥不会在页面回显；保存后供主机引用。',
    ])

    add_heading_cn(doc, '4.2 主机管理', 2)
    add_numbered(doc, [
        '进入「主机管理」→ 新增/编辑主机。',
        '填写 IP、SSH 端口、操作系统、环境、绑定凭据。',
        '添加 SSH 主机密钥指纹（SHA256:...）。获取示例：',
    ])
    add_code(doc, 'ssh-keyscan -p <端口> <主机IP> 2>$null | ssh-keygen -lf - -E sha256')
    add_numbered(doc, [
        '添加「允许读取的日志路径」白名单（文件或目录）。演示工程建议指向已映射的 demo-ops-app 日志路径。',
        '先保存（可不启用）→ 点「探测连通性」→ 通过后再启用。',
    ])

    add_heading_cn(doc, '4.3 运维任务', 2)
    add_numbered(doc, [
        '进入「运维工作台」→「新增运维任务」。',
        '填写任务名称、目标主机、代码工作区路径（指向测试工程根目录，须含 pom.xml）。',
        '选择修复引擎：Cursor CLI / CodeX CLI / Claude CLI，或 auto（失败切换）。',
        '日志模板选择 APP_LOG_FILE（或与环境匹配的模板），配置巡检周期与时间窗。',
        '保存后暂不启用，等讲解到演示环节再打开「启用」开关。',
    ])

    add_heading_cn(doc, '4.4 授权与工具（如环境未预置）', 2)
    add_bullets(doc, [
        '确认当前账号具备运维任务、主机、凭据、审批等菜单权限。',
        '在 Agent 中心核对工具授权策略：有副作用的修码/部署工具需走审批。',
        '确认知识库可选：不可用也不应阻断主流程。',
    ])

    # 5
    add_heading_cn(doc, '五、路演演示剧本（推荐）', 1)
    add_para(doc, '总时长建议 8–12 分钟。操作员与讲解人可按下表配合。')

    script = doc.add_table(rows=9, cols=4)
    script.style = 'Table Grid'
    for i, h in enumerate(['步骤', '讲解要点', '操作动作', '预期结果']):
        set_cell_text(script.rows[0].cells[i], h, bold=True, color=RGBColor(0xFF, 0xFF, 0xFF), size=9)
    shade_header_row(script.rows[0])
    rows = [
        ('1 开场', '痛点：告警有、修码难、ChatOps 不敢放权', '打开运维工作台首页', '看到任务统计与任务列表'),
        ('2 造故障', '演示工程会定时打 ERROR；也可手动触发', '确认 demo 已启动；可选执行 trigger-faults', '日志文件出现 ERROR 堆栈'),
        ('3 启巡检', '任务绑定主机日志 + 工程画像/工作区 + 修复引擎', '打开目标运维任务「启用」', '进入定时拉日志状态'),
        ('4 发现异常', '阈值判异常 → 自动建故障', '刷新任务/故障列表或打开执行记录', '出现分析中/待处理故障'),
        ('5 多 Agent', '证据→根因→修复规划，不是单模型包打', '进入故障详情看时间线', '各阶段输出与工具调用可见'),
        ('6 审批修码', '人在回路：修码前必须批', '审批通过本地修复；观察 CLI 执行', '产出真实 Diff，非口头建议'),
        ('7 验证部署', '验证绑定补丁指纹；按画像构建/部署', '等待验证；可选自动部署确认', '验证通过，制品/部署就绪'),
        ('8 收束', '再审批部署/确认完结；可提知识沉淀', '完成部署确认或结案', '任务完结，闭环可复盘'),
    ]
    for r_i, row in enumerate(rows, start=1):
        for c_i, val in enumerate(row):
            set_cell_text(script.rows[r_i].cells[c_i], val, size=9)

    add_heading_cn(doc, '5.1 演示中建议重点展示的页面', 2)
    add_bullets(doc, [
        '运维工作台：任务、主机、工程画像、工作区、修复引擎',
        'Agent 管理：CodeX / Cursor / Claude 环境检测',
        '故障详情 / 执行记录：多 Agent 时间线与工具审计',
        '补丁 Diff 审阅页：证明「改得动且可审」',
        '审批记录与受控部署：证明「控得住」',
    ])

    add_heading_cn(doc, '5.2 可演示的缺陷类型（测试工程）', 2)
    add_bullets(doc, [
        '空指针 NPE',
        '数字格式异常 NumberFormat',
        '日期解析失败',
        '分页越界 IndexOutOfBounds',
        '文件不存在 NoSuchFile',
        '字符串越界 StringIndex',
        '并发修改 ConcurrentModification（脚本可触发）',
    ])

    # 6
    add_heading_cn(doc, '六、关键操作说明', 1)
    add_heading_cn(doc, '6.1 审批门禁', 2)
    add_bullets(doc, [
        '本地 CLI 修复前：必须人工批准，否则不会真正改代码。',
        '部署执行前：再次审批/确认。',
        '路演时讲解人可强调：这是与「裸奔 Agent」的核心区别。',
    ])

    add_heading_cn(doc, '6.2 修复引擎选择', 2)
    add_bullets(doc, [
        '固定引擎：便于演示某一 CLI 的稳定性。',
        'auto：某引擎失败时可切换，适合展示容错，但耗时可能更长。',
        '路演前务必在同一台机器上预跑一次所选引擎，避免现场冷启动失败。',
    ])

    add_heading_cn(doc, '6.3 代码工作区与 Git', 2)
    add_bullets(doc, [
        '工作区须与所选工程画像匹配（如 Maven/WAR 含 pom.xml；Node 含 package.json）。',
        '建议演示工程已完成至少一次 git commit，避免快照命令异常。',
        '平台以受控方式改码并做 diff 审计；不要在演示过程中手动大范围改同一工作区。',
    ])

    add_heading_cn(doc, '6.4 制品构建与部署', 2)
    add_bullets(doc, [
        'Java 构建依赖 JDK 17+；若本机默认 JAVA_HOME 为 8，平台会尝试自动选择更高版本 JDK。',
        '也可在平台配置中显式指定 Java Home（按部署环境配置项为准）。',
        '多栈场景按任务 backendKind/clientKind 准备对应工具链；Web 可演示静态同步。',
        '验证失败时，优先看构建日志与工程画像是否选错，再重试。',
    ])

    # 7
    add_heading_cn(doc, '七、验收检查表（路演当天）', 1)
    checks = [
        ('T-60 分钟', '演示工程可启动，日志持续滚动'),
        ('T-60 分钟', '后端/前端可登录，菜单权限正常'),
        ('T-45 分钟', '主机探测连通性通过，日志白名单可读'),
        ('T-45 分钟', '运维任务配置完整（主机/工作区/引擎）'),
        ('T-30 分钟', '所选 CLI 引擎能在工作区完成一次试修复或 --version 可用'),
        ('T-15 分钟', '浏览器全屏页签只保留演示相关页面'),
        ('开场前', '关闭无关弹窗、通知；确认管理员未锁屏'),
        ('演示后', '禁用运维任务，避免后台持续消耗模型/CLI'),
    ]
    ct = doc.add_table(rows=len(checks) + 1, cols=2)
    ct.style = 'Table Grid'
    set_cell_text(ct.rows[0].cells[0], '时间点', bold=True, color=RGBColor(0xFF, 0xFF, 0xFF), size=10)
    set_cell_text(ct.rows[0].cells[1], '检查项', bold=True, color=RGBColor(0xFF, 0xFF, 0xFF), size=10)
    shade_header_row(ct.rows[0])
    for i, (when, what) in enumerate(checks, start=1):
        set_cell_text(ct.rows[i].cells[0], when, size=10)
        set_cell_text(ct.rows[i].cells[1], what, size=10)

    # 8
    add_heading_cn(doc, '八、常见问题与应急话术', 1)
    faqs = [
        ('巡检一直无故障', '先看演示日志是否有 ERROR；再查主机白名单/连通性；可手动 trigger-faults；适当缩短巡检间隔（仅演示环境）。'),
        ('凭据下拉看不到新凭据', '打开主机新增/编辑时会刷新关联选项；若仍无，退出重进资源中心。'),
        ('CLI 修复失败', '确认引擎已登录/授权；工作区路径正确；切换另一引擎或 auto；向观众说明「门禁会拦截不安全操作」。'),
        ('验证 / 制品构建失败', '核对工程画像与 JDK/工具链；清理后重试；展示日志根因即可，不必现场死磕编译。'),
        ('审批提示不能执行', '可能阶段已推进，刷新详情；重复提交会被状态机幂等处理，改讲「状态机防乱序」。'),
        ('时间不够', '跳过 PPT 细节页，直接从「启巡检」演示到 Diff 审阅，用金句收束。'),
    ]
    for q, a in faqs:
        add_para(doc, f'Q：{q}', bold=True, size=11)
        add_para(doc, f'A：{a}', size=11, color=GRAY)

    # 9
    add_heading_cn(doc, '九、安全与演示纪律', 1)
    add_bullets(doc, [
        '演示环境与生产环境隔离；勿使用生产主机、生产凭据。',
        '路演账号密码勿投屏过久；讲解结束后锁定屏幕。',
        '远程侧仅允许只读诊断模板；不要为「图快」放开任意 Shell。',
        '文档与 PPT 标注「机密 · 仅供路演演示」，对外发放前做脱敏。',
    ])

    # 10
    add_heading_cn(doc, '十、附录：页面路径速查', 1)
    pages = [
        ('运维工作台', '左侧菜单 → 运维工作台', '任务启停、故障入口'),
        ('执行记录', '顶部标签 / Agent 中心', '时间线与工具审计'),
        ('主机管理', '资源中心', '指纹、日志白名单、探测'),
        ('凭据管理', '资源中心', 'SSH/DB/Git 凭据'),
        ('授权策略', 'Agent 中心', '工具与权限收敛'),
        ('知识库', '左侧/顶部菜单', '导入文档/AI 预生成与沉淀'),
        ('本地修复引擎向导', '顶部标签（如已打开）', 'CLI 安装与配置引导'),
    ]
    pt = doc.add_table(rows=len(pages) + 1, cols=3)
    pt.style = 'Table Grid'
    for i, h in enumerate(['功能', '进入方式', '路演用途']):
        set_cell_text(pt.rows[0].cells[i], h, bold=True, color=RGBColor(0xFF, 0xFF, 0xFF), size=10)
    shade_header_row(pt.rows[0])
    for i, row in enumerate(pages, start=1):
        for j, val in enumerate(row):
            set_cell_text(pt.rows[i].cells[j], val, size=10)

    add_para(doc, '')
    end = doc.add_paragraph()
    end.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = end.add_run('— 全文完 · 配合《智维超级员工-产品路演》PPT 使用 —')
    set_run_font(r, size=10, color=GRAY)

    root = Path(__file__).resolve().parents[1]
    out = root / '智维超级员工-路演操作手册.docx'
    try:
        doc.save(str(out))
    except PermissionError:
        out = root / '智维超级员工-路演操作手册-v2.docx'
        doc.save(str(out))
    print(out)


if __name__ == '__main__':
    main()

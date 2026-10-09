<!-- @author yangdb -->
<script setup>
import { computed, ref } from 'vue'
import {
  ArrowRight, CircleCheck, Collection, DocumentChecked, Files,
  Key, List, MagicStick, Monitor, Operation, Platform, Tools
} from '@element-plus/icons-vue'

const props = defineProps({ user: { type: Object, required: true } })
const emit = defineEmits(['navigate'])
const guideDialog = ref('')

const menuCodes = computed(() => new Set((props.user.menus || []).map(item => item.code)))
const phases = [
  { number: '01', title: '环境准备', caption: '资源与访问基础' },
  { number: '02', title: '智能配置', caption: '修复引擎与 Agent' },
  { number: '03', title: '任务运行', caption: '巡检、分析与修复' },
  { number: '04', title: '闭环追踪', caption: '验证与执行回溯' }
]
const steps = [
  {
    phase: '准备', title: '配置本地修复引擎', code: 'AGENT_MANAGEMENT', target: 'CODEX_INSTALL_GUIDE', icon: MagicStick, featured: true,
    description: '在本地修复引擎向导中选择 CodeX、Cursor 或 Claude Code；完成安装与登录后，自动修复将调用当前引擎。',
    action: '进入引擎向导', tips: ['选择修复引擎', '检测并安装 CLI', '完成登录或配置 API Key', '确认“环境可用”']
  },
  {
    phase: '准备', title: '登记目标主机', code: 'RESOURCE_HOST', icon: Platform,
    description: '录入服务器地址、SSH 连接信息与允许拉取的日志路径，完成连通性验证。',
    action: '配置主机', tips: ['配置主机指纹', '验证 SSH 连通性', '设置日志白名单']
  },
  {
    phase: '准备', title: '配置应用运维任务', code: 'OPS_DASHBOARD', icon: Files,
    description: '在运维任务中统一配置应用、代码工作区、修复引擎（可自动切换）、目标主机和 Java 补丁部署策略。',
    action: '配置运维业务', tips: ['填写应用编码', '确认 Maven 工作区', '选择修复引擎', '选择部署策略']
  },
  {
    phase: '准备', title: '托管运维凭据', code: 'RESOURCE_CREDENTIAL', icon: Key,
    description: '集中维护主机和应用访问所需的凭据，避免在运维任务中重复填写敏感信息。',
    action: '配置凭据', tips: ['创建访问凭据', '关联使用范围', '确认凭据状态']
  },
  {
    phase: '智能', title: '配置 Agent 与权限', code: 'AGENT_AUTHORIZATION', icon: Key,
    description: '确认协调 Agent、专业 Agent 和工具权限，使自动分析流程具备所需能力。',
    action: '配置授权', tips: ['启用所需 Agent', '授权诊断工具', '校验资源访问范围']
  },
  {
    phase: '智能', title: '准备知识与工具', code: 'OPS_KNOWLEDGE', icon: Tools,
    description: '沉淀运维规范、故障经验和处置约束，为根因分析与修复方案提供上下文。',
    action: '维护知识库', tips: ['录入运维知识', '检查工具状态', '补充修复约束']
  },
  {
    phase: '运行', title: '创建并启用运维任务', code: 'OPS_DASHBOARD', icon: Operation,
    description: '选择主机和代码工作区，设置日志拉取周期、日志范围与异常自动分析策略。',
    action: '创建运维任务', tips: ['选择日志来源', '设置拉取频率', '开启自动分析']
  },
  {
    phase: '运行', title: '自动巡检与根因分析', code: 'OPS_DASHBOARD', icon: Monitor, dialog: 'timeline',
    description: '平台定时拉取日志，发现异常后自动创建故障任务，并组织多个 Agent 完成分析。',
    action: '查看任务时间线', tips: ['观察日志拉取', '查看分析步骤', '确认故障状态']
  },
  {
    phase: '运行', title: '审阅修复并验证', code: 'OPS_DASHBOARD', icon: DocumentChecked, dialog: 'review',
    description: '本地修复引擎生成变更后进行差异审阅；接受修复后自动构建并进入部署验证。',
    action: '进入修复审阅', tips: ['审阅代码差异', '接受或撤销修复', '完成部署验证']
  },
  {
    phase: '追踪', title: '查看执行记录', code: 'AGENT_EXECUTION', icon: List,
    description: '回看 Agent 会话、工具调用和各阶段耗时，快速定位执行失败或性能瓶颈。',
    action: '查看执行记录', tips: ['查看调用轨迹', '检查失败步骤', '分析执行耗时']
  },
  {
    phase: '追踪', title: '持续沉淀运维经验', code: 'OPS_KNOWLEDGE', icon: Collection,
    description: '将验证结果和处置经验回填知识库，形成可复用、可审计的自动化运维闭环。',
    action: '沉淀知识', tips: ['归档根因', '记录验证结果', '更新处置规范']
  }
]

function available(step) {
  return props.user.role === 'ADMIN' || menuCodes.value.has(step.code)
}

function go(step) {
  if (!available(step)) return
  if (step.dialog) {
    guideDialog.value = step.dialog
    return
  }
  emit('navigate', step.target || step.code)
}

function openWorkbench() {
  guideDialog.value = ''
  emit('navigate', 'OPS_DASHBOARD')
}
</script>

<template>
  <section class="guide-home">
    <header class="guide-hero">
      <div class="guide-hero-copy">
        <span class="guide-kicker">AUTONOMOUS OPERATIONS JOURNEY</span>
        <h2>{{ user.displayName }}，欢迎使用智维超级员工</h2>
        <p>按照这条运维路线完成基础配置，即可建立从日志巡检、故障分析、代码修复到人工验证的自治闭环。</p>
        <el-button type="primary" size="large" :icon="ArrowRight" @click="emit('navigate', 'OPS_DASHBOARD')">
          进入运维工作台
        </el-button>
      </div>
      <div class="guide-flow-visual" aria-hidden="true">
        <div class="guide-flow-status"><i /> AUTONOMOUS FLOW</div>
        <div class="guide-flow-chain">
          <div><span><el-icon><Monitor /></el-icon></span><small>日志</small></div>
          <el-icon class="guide-flow-arrow"><ArrowRight /></el-icon>
          <div><span><el-icon><MagicStick /></el-icon></span><small>Agent</small></div>
          <el-icon class="guide-flow-arrow"><ArrowRight /></el-icon>
          <div><span><el-icon><CircleCheck /></el-icon></span><small>闭环</small></div>
        </div>
        <div class="guide-flow-caption"><strong>多 Agent 自治编排</strong><span>诊断 · 修复 · 验证</span></div>
      </div>
    </header>

    <div class="guide-phase-grid">
      <article v-for="phase in phases" :key="phase.number">
        <span>{{ phase.number }}</span>
        <div><strong>{{ phase.title }}</strong><small>{{ phase.caption }}</small></div>
      </article>
    </div>

    <div class="guide-section-heading">
      <div><span>从配置到闭环</span><h3>全流程运维教程</h3></div>
      <p>点击任意步骤可直接前往对应功能；灰色按钮表示当前账号暂未获得该页面权限。</p>
    </div>

    <div class="guide-timeline">
      <article
        v-for="(step, index) in steps"
        :key="`${step.title}-${index}`"
        :class="['guide-step', { featured: step.featured }]"
      >
        <div class="guide-step-rail">
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <i v-if="index < steps.length - 1" />
        </div>
        <div class="guide-step-card">
          <div class="guide-step-icon"><el-icon><component :is="step.icon" /></el-icon></div>
          <div class="guide-step-content">
            <div class="guide-step-title">
              <el-tag size="small" effect="plain">{{ step.phase }}</el-tag>
              <h4>{{ step.title }}</h4>
              <el-tag v-if="step.featured" size="small" type="success">推荐先完成</el-tag>
            </div>
            <p>{{ step.description }}</p>
            <div class="guide-checklist">
              <span v-for="tip in step.tips" :key="tip"><el-icon><CircleCheck /></el-icon>{{ tip }}</span>
            </div>
          </div>
          <el-button
            type="primary"
            plain
            :disabled="!available(step)"
            @click="go(step)"
          >{{ available(step) ? step.action : '暂无权限' }}<el-icon class="el-icon--right"><ArrowRight /></el-icon></el-button>
        </div>
      </article>
    </div>

    <el-dialog
      :model-value="guideDialog === 'timeline'"
      title="任务时间线查看指南"
      width="min(760px, 92vw)"
      destroy-on-close
      @close="guideDialog = ''"
    >
      <div class="guide-dialog-intro">进入运维任务详情后，时间线会持续展示一次故障从发现到完成的全部处理步骤。</div>
      <div class="timeline-preview">
        <div v-for="(item, index) in [
          ['日志拉取', '按任务配置的时间范围获取最新日志', 'success'],
          ['异常识别', '发现异常并创建对应的故障任务', 'success'],
          ['多 Agent 分析', '分析现象、定位根因并生成修复建议', 'primary'],
          ['本地修复', '根据批准范围由当前修复引擎修改代码并生成审阅差异', 'warning'],
          ['验证与部署', '构建验证通过后执行部署并记录结果', 'info']
        ]" :key="item[0]" class="timeline-preview-item">
          <span :class="`is-${item[2]}`">{{ index + 1 }}</span>
          <div><strong>{{ item[0] }}</strong><p>{{ item[1] }}</p></div>
        </div>
      </div>
      <template #footer>
        <el-button @click="guideDialog = ''">关闭</el-button>
        <el-button type="primary" @click="openWorkbench">进入工作台查看任务</el-button>
      </template>
    </el-dialog>

    <el-dialog
      :model-value="guideDialog === 'review'"
      title="修复审阅指南"
      width="min(900px, 94vw)"
      destroy-on-close
      @close="guideDialog = ''"
    >
      <div class="guide-dialog-intro">本地修复引擎成功生成变更后，在故障任务中点击“审阅”即可逐行检查差异，再决定接受或撤销。</div>
      <div class="review-preview">
        <div class="review-preview-head">
          <strong>src/main/java/service/LogService.java</strong>
          <span class="review-remove">− 1</span><span class="review-add">+ 2</span>
        </div>
        <div class="review-preview-grid review-preview-label"><span>修复前</span><span>修复后</span></div>
        <div class="review-preview-grid">
          <code class="review-remove">- if (logs.isEmpty()) return null;</code>
          <code class="review-add">+ if (logs.isEmpty()) return List.of();</code>
        </div>
        <div class="review-preview-grid">
          <code class="review-context">  return analyze(logs);</code>
          <code class="review-add">+ validateScope(logs);<br>  return analyze(logs);</code>
        </div>
      </div>
      <div class="review-guide-actions">
        <span><b>撤销修复</b>：恢复修改前的代码</span>
        <span><b>接受并继续验证</b>：保留变更并启动构建验证</span>
      </div>
      <template #footer>
        <el-button @click="guideDialog = ''">关闭</el-button>
        <el-button type="primary" @click="openWorkbench">进入工作台审阅修复</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.guide-home {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  align-self: stretch;
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 14px 20px 30px;
  box-sizing: border-box;
  background:
    radial-gradient(circle at 10% 8%, rgba(74, 180, 204, .12), transparent 24%),
    radial-gradient(circle at 90% 24%, rgba(59, 130, 246, .09), transparent 22%),
    #f5f8fb;
}
.guide-hero {
  position: relative;
  min-height: 178px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 40px;
  overflow: hidden;
  padding: 22px 32px;
  border: 1px solid rgba(148, 196, 212, .45);
  border-radius: 18px;
  background: linear-gradient(125deg, #ffffff 0%, #eef9fb 58%, #e8f3ff 100%);
  box-shadow: 0 14px 38px rgba(31, 75, 94, .10);
  flex: 0 0 auto;
}
.guide-hero-copy { position: relative; z-index: 2; max-width: 720px; }
.guide-kicker { color: #11839a; font-size: 11px; font-weight: 700; letter-spacing: .16em; }
.guide-hero h2 { margin: 7px 0 8px; color: #102a43; font-size: clamp(24px, 2.6vw, 34px); line-height: 1.2; }
.guide-hero p { max-width: 650px; margin: 0 0 15px; color: #526d82; font-size: 14px; line-height: 1.65; }
.guide-flow-visual {
  position: relative;
  flex: 0 0 286px;
  width: 286px;
  padding: 16px 18px 14px;
  overflow: hidden;
  border: 1px solid rgba(132, 191, 207, .48);
  border-radius: 14px;
  background: linear-gradient(145deg, rgba(255,255,255,.92), rgba(229,245,250,.82));
  box-shadow: 0 12px 30px rgba(37, 91, 112, .11);
}
.guide-flow-visual::after { content: ""; position: absolute; right: -38px; bottom: -50px; width: 130px; height: 130px; border-radius: 50%; background: rgba(54, 162, 185, .08); }
.guide-flow-status { position: relative; z-index: 1; display: flex; align-items: center; gap: 6px; color: #6b8798; font-size: 9px; font-weight: 700; letter-spacing: .12em; }
.guide-flow-status i { width: 7px; height: 7px; border-radius: 50%; background: #31a66a; box-shadow: 0 0 0 4px rgba(49,166,106,.12); }
.guide-flow-chain { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; margin: 14px 0 11px; }
.guide-flow-chain > div { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.guide-flow-chain > div > span { display: grid; place-items: center; width: 38px; height: 38px; border: 1px solid #cde2e9; border-radius: 10px; color: #147d92; font-size: 19px; background: #fff; box-shadow: 0 5px 13px rgba(32, 76, 97, .10); }
.guide-flow-chain > div:nth-of-type(2) > span { color: #fff; border-color: transparent; background: linear-gradient(145deg, #168ca2, #2865ac); }
.guide-flow-chain > div:nth-of-type(3) > span { color: #2c9257; background: #effaf3; }
.guide-flow-chain small { color: #6d8596; font-size: 9px; }
.guide-flow-arrow { color: #98b4c2; font-size: 13px; }
.guide-flow-caption { position: relative; z-index: 1; display: flex; align-items: center; justify-content: space-between; padding-top: 9px; border-top: 1px solid rgba(159, 193, 204, .36); }
.guide-flow-caption strong { color: #294a5b; font-size: 11px; }
.guide-flow-caption span { color: #8299a8; font-size: 9px; }
.guide-phase-grid { display: grid; flex: 0 0 auto; grid-template-columns: repeat(4, 1fr); gap: 9px; margin: 11px 0 14px; }
.guide-phase-grid article { display: flex; align-items: center; gap: 10px; padding: 10px 14px; border: 1px solid #dde7ef; border-radius: 9px; background: rgba(255,255,255,.88); }
.guide-phase-grid article > span { color: #9bb0bf; font-size: 18px; font-weight: 700; }
.guide-phase-grid strong, .guide-phase-grid small { display: block; }
.guide-phase-grid strong { color: #243b53; font-size: 14px; }
.guide-phase-grid small { margin-top: 2px; color: #829ab1; }
.guide-section-heading { display: flex; flex: 0 0 auto; align-items: end; justify-content: space-between; gap: 24px; width: 100%; margin: 0 0 14px; }
.guide-section-heading span { color: #147d92; font-size: 12px; font-weight: 600; }
.guide-section-heading h3 { margin: 2px 0 0; color: #102a43; font-size: 22px; }
.guide-section-heading p { margin: 0; color: #829ab1; font-size: 12px; }
.guide-timeline {
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  margin: 0;
  padding: 4px 14px 32px 0;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: #b4c7d4 transparent;
}
.guide-timeline::-webkit-scrollbar { width: 6px; }
.guide-timeline::-webkit-scrollbar-track { background: transparent; }
.guide-timeline::-webkit-scrollbar-thumb { border-radius: 4px; background: #b4c7d4; }
.guide-step { display: grid; grid-template-columns: 58px 1fr; }
.guide-step-rail { position: relative; display: flex; flex-direction: column; align-items: center; }
.guide-step-rail span { position: relative; z-index: 2; display: grid; place-items: center; width: 36px; height: 36px; border: 4px solid #f5f8fb; border-radius: 50%; color: #147d92; font-size: 11px; font-weight: 700; background: #dff3f7; }
.guide-step-rail i { width: 2px; flex: 1; min-height: 105px; background: linear-gradient(#b8dfe8, #dce8ee); }
.guide-step-card { display: flex; align-items: center; gap: 18px; margin-bottom: 14px; padding: 20px 22px; border: 1px solid #dfe8ef; border-radius: 12px; background: #fff; box-shadow: 0 5px 18px rgba(32, 76, 97, .055); transition: transform .18s ease, box-shadow .18s ease; }
.guide-step-card:hover { transform: translateY(-2px); box-shadow: 0 10px 28px rgba(32, 76, 97, .10); }
.guide-step.featured .guide-step-card { border-color: #9ed6c4; background: linear-gradient(100deg, #f5fffb, #fff); }
.guide-step.featured .guide-step-rail span { color: #238653; background: #dff7e9; }
.guide-step-icon { flex: 0 0 48px; display: grid; place-items: center; width: 48px; height: 48px; border-radius: 12px; color: #147d92; font-size: 24px; background: #e9f6f9; }
.guide-step.featured .guide-step-icon { color: #238653; background: #dff7e9; }
.guide-step-content { flex: 1 1 auto; min-width: 0; }
.guide-step-title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.guide-step-title h4 { margin: 0; color: #243b53; font-size: 16px; }
.guide-step-content > p { margin: 8px 0 10px; color: #627d98; font-size: 13px; line-height: 1.65; }
.guide-checklist { display: flex; flex-wrap: wrap; gap: 8px 16px; }
.guide-checklist span { display: inline-flex; align-items: center; gap: 4px; color: #71879a; font-size: 11px; }
.guide-checklist .el-icon { color: #31a66a; }
.guide-dialog-intro { margin: -4px 0 20px; color: #627d98; line-height: 1.7; }
.timeline-preview { padding: 4px 8px; }
.timeline-preview-item { position: relative; display: flex; gap: 14px; min-height: 66px; }
.timeline-preview-item:not(:last-child)::before { content: ""; position: absolute; top: 30px; bottom: 0; left: 15px; width: 2px; background: #dce8ee; }
.timeline-preview-item > span { position: relative; z-index: 1; display: grid; place-items: center; width: 32px; height: 32px; flex: 0 0 32px; border-radius: 50%; color: #147d92; font-size: 12px; font-weight: 700; background: #e5f5f8; }
.timeline-preview-item > span.is-success { color: #238653; background: #dff7e9; }
.timeline-preview-item > span.is-warning { color: #b7791f; background: #fff2cf; }
.timeline-preview-item > span.is-info { color: #526d82; background: #e8eef3; }
.timeline-preview-item strong { color: #243b53; }
.timeline-preview-item p { margin: 5px 0 0; color: #71879a; font-size: 13px; }
.review-preview { overflow: hidden; border: 1px solid #dce6ed; border-radius: 9px; background: #f8fafc; }
.review-preview-head { display: flex; align-items: center; gap: 10px; padding: 12px 15px; border-bottom: 1px solid #dce6ed; background: #fff; }
.review-preview-head strong { flex: 1; color: #334e68; font-family: Consolas, monospace; font-size: 13px; }
.review-preview-head span { padding: 2px 7px; border-radius: 4px; font-size: 12px; }
.review-preview-grid { display: grid; grid-template-columns: 1fr 1fr; }
.review-preview-grid > * { min-width: 0; padding: 10px 13px; border-bottom: 1px solid #e5ecf1; white-space: pre-wrap; }
.review-preview-grid > *:first-child { border-right: 1px solid #dce6ed; }
.review-preview-label { color: #627d98; font-size: 12px; background: #eef3f7; }
.review-preview code { color: #52606d; font-family: Consolas, monospace; font-size: 12px; line-height: 1.7; }
.review-preview .review-remove { color: #a23b3b; background: #fff0f0; }
.review-preview .review-add { color: #237a47; background: #ecf9f0; }
.review-guide-actions { display: flex; flex-wrap: wrap; gap: 12px 28px; margin-top: 16px; color: #627d98; font-size: 13px; }
@media (max-width: 900px) {
  .guide-home { height: auto; min-height: 100%; overflow: visible; padding: 16px 12px 30px; }
  .guide-hero { padding: 28px 24px; }
  .guide-flow-visual { display: none; }
  .guide-phase-grid { grid-template-columns: repeat(2, 1fr); }
  .guide-section-heading { align-items: start; flex-direction: column; gap: 8px; }
  .guide-timeline { flex: none; overflow: visible; padding-right: 0; }
  .guide-step-card { align-items: flex-start; flex-wrap: wrap; }
  .guide-step-card > .el-button { margin-left: 66px; }
}
@media (max-width: 560px) {
  .guide-phase-grid { grid-template-columns: 1fr; }
  .guide-step { grid-template-columns: 42px 1fr; }
  .guide-step-card { gap: 12px; padding: 16px; }
  .guide-step-icon { width: 40px; height: 40px; flex-basis: 40px; }
  .guide-step-card > .el-button { width: 100%; margin-left: 52px; }
}
</style>

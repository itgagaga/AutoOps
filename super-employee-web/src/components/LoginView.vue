<!-- @author yangdb -->
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { CircleCheck, Connection, Lock, MagicStick, Monitor, Refresh, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getCaptcha, login } from '../api'

const emit = defineEmits(['logged-in'])
const formRef = ref()
const loading = ref(false)
const captchaLoading = ref(false)
const captcha = ref(null)
const sliderValue = ref(0)
const form = reactive({ username: '', password: '', captchaId: '', captchaAnswer: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaAnswer: [{ required: true, message: '请完成安全验证', trigger: 'change' }]
}

async function refreshCaptcha() {
  captchaLoading.value = true
  form.captchaAnswer = ''
  sliderValue.value = 0
  try {
    captcha.value = await getCaptcha()
    form.captchaId = captcha.value.id
  } catch (error) {
    ElMessage.error(`验证码加载失败：${error.message}`)
  } finally {
    captchaLoading.value = false
  }
}

function updateSlider(value) {
  sliderValue.value = value
  form.captchaAnswer = String(value)
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try { emit('logged-in', await login(form)) }
  catch (error) {
    ElMessage.error(error.message)
    await refreshCaptcha()
  }
  finally { loading.value = false }
}

onMounted(refreshCaptcha)
</script>

<template>
  <main class="login-page">
    <section class="login-hero" aria-label="产品介绍">
      <div class="login-hero-mesh" aria-hidden="true" />
      <div class="login-hero-signal" aria-hidden="true" />
      <div class="login-hero-inner">
        <div class="login-brand-block">
          <span class="login-brand-mark" aria-hidden="true">
            <el-icon><Monitor /></el-icon>
          </span>
          <div class="login-brand-name">
            <h1>智维超级员工</h1>
            <p>SUPER EMPLOYEE</p>
          </div>
        </div>

        <p class="login-kicker">AUTONOMOUS SERVER OPERATIONS</p>
        <h2 class="login-headline">让服务器运维<br />更智能、更从容</h2>
        <p class="login-lead">
          多 Agent 协同完成异常发现、根因分析、代码修复与部署验证，把重复运维从人工值班里解放出来。
        </p>

        <ul class="login-traits">
          <li><el-icon><Connection /></el-icon><span>日志智能巡检</span></li>
          <li><el-icon><MagicStick /></el-icon><span>多 Agent 协同</span></li>
          <li><el-icon><CircleCheck /></el-icon><span>修复验证闭环</span></li>
        </ul>

        <ol class="login-pipeline">
          <li>日志扫描</li>
          <li>故障分析</li>
          <li>本地修复</li>
          <li>验证部署</li>
        </ol>
      </div>
    </section>

    <section class="login-access" aria-label="登录">
      <div class="login-access-wash" aria-hidden="true" />
      <div class="login-sheet">
        <header class="login-sheet-head">
          <span class="login-sheet-icon" aria-hidden="true">
            <el-icon><Lock /></el-icon>
          </span>
          <div>
            <p class="login-sheet-kicker">SECURE ACCESS</p>
            <h2>欢迎回来</h2>
            <p class="login-sheet-sub">登录智维超级员工运维工作台</p>
          </div>
        </header>

        <el-form ref="formRef" class="login-form" :model="form" :rules="rules" size="large" @keyup.enter="submit">
          <label class="login-label" for="login-username">账号</label>
          <el-form-item prop="username">
            <el-input
              id="login-username"
              v-model="form.username"
              :prefix-icon="User"
              placeholder="用户名"
              autocomplete="username"
            />
          </el-form-item>

          <label class="login-label" for="login-password">密码</label>
          <el-form-item prop="password">
            <el-input
              id="login-password"
              v-model="form.password"
              :prefix-icon="Lock"
              type="password"
              show-password
              placeholder="密码"
              autocomplete="current-password"
            />
          </el-form-item>

          <div class="login-captcha-head">
            <label class="login-label">安全验证</label>
            <el-button class="login-captcha-refresh" link :icon="Refresh" @click="refreshCaptcha">换一张</el-button>
          </div>
          <el-form-item prop="captchaAnswer">
            <div v-loading="captchaLoading" class="login-captcha">
              <template v-if="captcha?.type === 'SLIDER'">
                <div class="login-captcha-scene">
                  <img v-if="captcha.image" :src="captcha.image" alt="滑块验证码" />
                  <i
                    class="login-captcha-piece"
                    :style="{ left: `clamp(2px, calc(${sliderValue}% - 15px), calc(100% - 32px))` }"
                    aria-hidden="true"
                  />
                  <span>拖动滑块，将拼图块移动到缺口位置</span>
                </div>
                <el-slider
                  :model-value="sliderValue"
                  :show-tooltip="false"
                  @input="updateSlider"
                  @change="updateSlider"
                />
              </template>
              <template v-else>
                <el-input v-model="form.captchaAnswer" placeholder="输入验证码" maxlength="12">
                  <template #append>
                    <button type="button" class="login-captcha-image-btn" title="点击刷新" @click="refreshCaptcha">
                      <img v-if="captcha?.image" :src="captcha.image" alt="验证码" />
                    </button>
                  </template>
                </el-input>
              </template>
            </div>
          </el-form-item>

          <el-button type="primary" class="login-submit" :loading="loading" @click="submit">
            {{ loading ? '正在登录' : '安全登录' }}
          </el-button>
        </el-form>

        <p class="login-audit">
          <el-icon><CircleCheck /></el-icon>
          账号访问受权限控制，所有关键操作均记录审计日志
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  --ink: #071c2a;
  --ink-mid: #0d3648;
  --sea: #1a7a86;
  --sea-bright: #2aa8a0;
  --mist: #eef4f6;
  --paper: #fbfcfd;
  --line: #d5e2e8;
  --muted: #6b8494;
  --text: #163248;
  --font-display: "Sora", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  --font-body: "Noto Sans SC", "Sora", "PingFang SC", "Microsoft YaHei", sans-serif;

  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 48%) 1fr;
  font-family: var(--font-body);
  background: var(--mist);
  color: var(--text);
}

.login-hero {
  position: relative;
  display: grid;
  align-content: center;
  padding: clamp(40px, 7vw, 88px);
  overflow: hidden;
  color: #f4fbfb;
  background:
    linear-gradient(155deg, #061722 0%, #0b3040 46%, #0f4d55 100%);
}

.login-hero-mesh {
  position: absolute;
  inset: 0;
  opacity: 0.22;
  background-image:
    linear-gradient(rgba(120, 196, 196, 0.35) 1px, transparent 1px),
    linear-gradient(90deg, rgba(120, 196, 196, 0.35) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: radial-gradient(ellipse 80% 70% at 20% 30%, #000 20%, transparent 75%);
  animation: login-mesh-drift 28s linear infinite;
}

.login-hero-signal {
  position: absolute;
  right: -18%;
  bottom: -30%;
  width: 72%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(42, 168, 160, 0.22) 0%, transparent 68%);
  animation: login-signal-pulse 8s ease-in-out infinite;
}

.login-hero-inner {
  position: relative;
  z-index: 1;
  width: min(540px, 100%);
  animation: login-rise 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.login-brand-block {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: clamp(36px, 6vh, 64px);
}

.login-brand-mark {
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  flex: 0 0 56px;
  border: 1px solid rgba(120, 210, 200, 0.45);
  border-radius: 14px;
  color: #7fe0d4;
  font-size: 26px;
  background: rgba(255, 255, 255, 0.05);
}

.login-brand-name h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(28px, 3.2vw, 40px);
  font-weight: 700;
  letter-spacing: 0.02em;
  line-height: 1.15;
}

.login-brand-name p {
  margin: 6px 0 0;
  color: #7fa3b0;
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.28em;
}

.login-kicker {
  margin: 0 0 14px;
  color: #6fd0c4;
  font-family: var(--font-display);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.2em;
}

.login-headline {
  margin: 0;
  max-width: 14em;
  font-family: var(--font-display);
  font-size: clamp(26px, 2.8vw, 36px);
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: -0.01em;
  color: rgba(244, 251, 251, 0.92);
}

.login-lead {
  max-width: 34em;
  margin: 18px 0 0;
  color: #a8c2cd;
  font-size: 15px;
  line-height: 1.8;
}

.login-traits {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 28px 0 0;
  padding: 0;
  list-style: none;
}

.login-traits li {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid rgba(146, 204, 211, 0.22);
  border-radius: 8px;
  color: #c5dbe4;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.04);
}

.login-traits .el-icon {
  color: #6fd0c4;
}

.login-pipeline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0;
  width: fit-content;
  margin: clamp(36px, 6vh, 56px) 0 0;
  padding: 0;
  list-style: none;
  color: #9bb5c2;
  font-size: 12px;
  letter-spacing: 0.04em;
}

.login-pipeline li {
  display: inline-flex;
  align-items: center;
}

.login-pipeline li + li::before {
  content: "";
  display: inline-block;
  width: 28px;
  height: 1px;
  margin: 0 10px;
  background: linear-gradient(90deg, rgba(46, 132, 147, 0.3), #2aa8a0);
}

.login-access {
  position: relative;
  display: grid;
  place-items: center;
  padding: clamp(24px, 4vw, 48px);
  overflow: hidden;
  background:
    radial-gradient(ellipse 50% 40% at 88% 8%, rgba(42, 168, 160, 0.1), transparent 60%),
    radial-gradient(ellipse 45% 35% at 10% 95%, rgba(22, 80, 110, 0.06), transparent 55%),
    linear-gradient(180deg, #f3f7f9 0%, #eef3f5 100%);
}

.login-access-wash {
  position: absolute;
  inset: 8% 6%;
  border: 1px solid rgba(22, 80, 110, 0.06);
  border-radius: 48% 52% 45% 55% / 48% 42% 58% 52%;
  pointer-events: none;
  animation: login-wash-shift 18s ease-in-out infinite alternate;
}

.login-sheet {
  position: relative;
  z-index: 1;
  width: min(420px, 100%);
  padding: 36px 36px 28px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: color-mix(in srgb, var(--paper) 92%, white);
  box-shadow: 0 24px 48px rgba(16, 52, 72, 0.08);
  animation: login-sheet-in 0.75s cubic-bezier(0.22, 1, 0.36, 1) 0.08s both;
}

.login-sheet-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 28px;
}

.login-sheet-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: 0 0 48px;
  border-radius: 12px;
  color: var(--sea);
  font-size: 22px;
  background: #e4f3f4;
}

.login-sheet-kicker {
  margin: 0 0 4px;
  color: var(--sea);
  font-family: var(--font-display);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.login-sheet-head h2 {
  margin: 0;
  color: var(--ink);
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.login-sheet-sub {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.login-label {
  display: block;
  margin: 0 0 8px;
  color: #486581;
  font-size: 13px;
  font-weight: 600;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 10px;
  background: #f5fafb;
  box-shadow: 0 0 0 1px #d7e4ea inset;
  transition: box-shadow 0.2s ease, background 0.2s ease;
}

.login-form :deep(.el-input__wrapper:hover) {
  background: #fff;
  box-shadow: 0 0 0 1px #9ec4cf inset;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: 0 0 0 1px var(--sea) inset, 0 0 0 3px rgba(26, 122, 134, 0.12);
}

.login-captcha-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 0 8px;
}

.login-captcha-head .login-label {
  margin: 0;
}

.login-captcha {
  width: 100%;
}

.login-captcha-scene {
  position: relative;
  height: 56px;
  margin-bottom: 8px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: linear-gradient(135deg, #e8f3f5, #f7fafc);
  color: var(--muted);
  font-size: 12px;
  line-height: 56px;
  text-align: center;
  overflow: hidden;
}

.login-captcha-scene img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.login-captcha-piece {
  position: absolute;
  z-index: 2;
  top: 12px;
  width: 28px;
  height: 28px;
  border: 2px solid var(--sea);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 2px 8px rgba(16, 42, 67, 0.18);
  transition: left 0.04s linear;
  pointer-events: none;
}

.login-captcha-piece::after {
  content: "";
  position: absolute;
  top: 8px;
  right: -7px;
  width: 10px;
  height: 10px;
  border: 2px solid var(--sea);
  border-left: 0;
  border-radius: 0 6px 6px 0;
  background: rgba(255, 255, 255, 0.95);
}

.login-captcha-scene > span {
  position: relative;
  z-index: 3;
  padding: 4px 10px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.82);
}

.login-captcha-image-btn {
  width: 168px;
  height: 46px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  overflow: hidden;
}

.login-captcha-image-btn img {
  display: block;
  width: 168px;
  height: 46px;
  object-fit: cover;
}

.login-submit {
  width: 100%;
  height: 48px;
  margin-top: 4px;
  border: 0;
  border-radius: 10px;
  font-family: var(--font-display);
  font-weight: 600;
  letter-spacing: 0.14em;
  background: linear-gradient(105deg, #178a97 0%, #1f6fa8 100%);
  transition: transform 0.2s ease, filter 0.2s ease;
}

.login-submit:hover {
  filter: brightness(1.05);
  transform: translateY(-1px);
}

.login-audit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 18px 0 0;
  color: #91a4b2;
  font-size: 11px;
  line-height: 1.5;
}

.login-audit .el-icon {
  color: #3f9d6e;
}

@keyframes login-rise {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes login-sheet-in {
  from { opacity: 0; transform: translateY(22px) scale(0.985); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

@keyframes login-mesh-drift {
  from { background-position: 0 0; }
  to { background-position: 42px 42px; }
}

@keyframes login-signal-pulse {
  0%, 100% { opacity: 0.65; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.04); }
}

@keyframes login-wash-shift {
  from { transform: translate(0, 0) rotate(0deg); }
  to { transform: translate(-12px, 8px) rotate(2deg); }
}

@media (max-width: 960px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .login-hero {
    min-height: auto;
    padding: 28px 24px 32px;
    align-content: start;
  }

  .login-brand-block {
    margin-bottom: 22px;
  }

  .login-brand-name h1 {
    font-size: 26px;
  }

  .login-headline {
    font-size: 22px;
  }

  .login-lead,
  .login-traits,
  .login-pipeline {
    display: none;
  }

  .login-access {
    padding: 20px 16px 36px;
  }

  .login-sheet {
    padding: 28px 22px 22px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-hero-mesh,
  .login-hero-signal,
  .login-access-wash,
  .login-hero-inner,
  .login-sheet {
    animation: none;
  }
}
</style>

/** @author yangdb */
import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import './styles.css'
import App from './App.vue'

const app = createApp(App)

app.config.errorHandler = (err, instance, info) => {
  console.error('[Vue Error]', info, err)
  ElMessage.error('页面组件出现异常，请刷新重试')
}

window.addEventListener('unhandledrejection', (event) => {
  console.error('[Unhandled Rejection]', event.reason)
  const msg = event.reason?.message || event.reason || '请求处理失败'
  ElMessage.error(typeof msg === 'string' ? msg : '操作处理失败，请重试')
})

window.addEventListener('error', (event) => {
  if (event.error) {
    console.error('[Script Error]', event.error)
    ElMessage.error('页面脚本执行异常')
  }
})

app.use(ElementPlus).mount('#app')

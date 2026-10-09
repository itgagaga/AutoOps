import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxy = process.env.VITE_API_PROXY || env.VITE_API_PROXY || 'http://localhost:8088'
  return {
    plugins: [vue()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: apiProxy,
          changeOrigin: true,
          ws: true
        },
        '/actuator': {
          target: apiProxy,
          changeOrigin: true
        }
      }
    },
    build: {
      rollupOptions: {
        output: {
          manualChunks: {
            'vue-vendor': ['vue'],
            'element-plus': ['element-plus'],
            'element-icons': ['@element-plus/icons-vue']
          }
        }
      },
      chunkSizeWarningLimit: 600
    }
  }
})

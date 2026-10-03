import { defineConfig } from 'vitest/config'
import { loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { port: 5173, host: '0.0.0.0', proxy: { '/api': { target: loadEnv(mode, process.cwd(), 'VITE_').VITE_BACKEND_URL || 'http://localhost:8080', changeOrigin: true } } },
  build: { rollupOptions: { output: { manualChunks(id) {
    if (!id.includes('node_modules')) return
    if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
    if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
    if (id.includes('highlight.js') || id.includes('markdown-it')) return 'markdown'
    return 'vendor'
  } } } },
  test: { environment: 'jsdom', globals: true }
}))

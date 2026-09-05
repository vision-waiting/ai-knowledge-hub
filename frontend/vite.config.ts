import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, host: '0.0.0.0' },
  test: { environment: 'jsdom', globals: true }
})

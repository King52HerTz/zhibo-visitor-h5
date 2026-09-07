import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 8020,
    strictPort: true,
    proxy: {
      '/api/visitor': {
        target: 'http://127.0.0.1:8021',
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 8020,
    strictPort: true,
  },
})

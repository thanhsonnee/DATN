import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: {
    port: 5173,
    // Chuyển tiếp mọi lời gọi /api sang backend, nhờ vậy trình duyệt coi web và
    // API cùng một nguồn — không phải cấu hình CORS ở backend.
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})

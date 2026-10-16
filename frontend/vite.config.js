import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 개발할 때 /api 요청을 Spring Boot 서버(8080)로 넘겨요.
    // 외부 AI 분석은 최대 1분 넘게 걸릴 수 있어서 프록시도 75초까지 기다려요.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        timeout: 75_000,
        proxyTimeout: 75_000,
      },
    },
  },
})

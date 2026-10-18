/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // BACKEND_PROXY_TARGET is dev-server-only (no VITE_ prefix => never sent to the browser).
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.BACKEND_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [react()],
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      restoreMocks: true,
    },
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': backend,
        '/actuator': backend,
      },
    },
  }
})

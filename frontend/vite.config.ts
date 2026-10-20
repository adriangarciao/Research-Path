/// <reference types="vitest/config" />
import { readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'
import { cspMetaPlugin } from './csp.ts'

type HeaderRule = { source: string; headers: { key: string; value: string }[] }

/** The hosting headers from vercel.json, so `vite preview` serves the build exactly as Vercel would. */
function vercelHeaders(allowHttpApi: string | undefined): Record<string, string> {
  const config = JSON.parse(readFileSync(new URL('./vercel.json', import.meta.url), 'utf8')) as { headers: HeaderRule[] }
  const all = config.headers.find((r) => r.source === '/(.*)')?.headers ?? []
  const headers: Record<string, string> = {}
  for (const h of all) {
    // Local-only: the deployed API is https; a local preview may need to reach an http API on localhost.
    headers[h.key] =
      h.key === 'Content-Security-Policy' && allowHttpApi ? h.value.replace('connect-src', `connect-src ${allowHttpApi}`) : h.value
  }
  return headers
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // BACKEND_PROXY_TARGET is dev-server-only (no VITE_ prefix => never sent to the browser).
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.BACKEND_PROXY_TARGET || 'http://localhost:8080'

  return {
    // VITE_API_BASE_URL is public build configuration (an origin, never a secret).
    plugins: [react(), cspMetaPlugin(env.VITE_API_BASE_URL)],
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      restoreMocks: true,
      include: ['src/**/*.test.{ts,tsx}', 'csp.test.ts'],
    },
    server: {
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': backend,
        '/actuator': backend,
      },
    },
    preview: {
      headers: vercelHeaders(env.PREVIEW_ALLOW_HTTP_API),
    },
  }
})
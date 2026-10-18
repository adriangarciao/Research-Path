import { defineConfig, devices } from '@playwright/test'

// Whole-flow browser test against the REAL backend (which calls OpenAlex). Not part of verify.ps1:
// run `.\scripts\e2e.ps1` with the backend up. The Vite dev server is started (or reused) here.
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 60_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  outputDir: './e2e-results',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
})

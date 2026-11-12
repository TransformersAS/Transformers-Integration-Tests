import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './tests/e2e', testMatch: 'cu09-profile.spec.ts', workers: 1,
  timeout: 90_000, expect: { timeout: 15_000 },
  outputDir: 'target/cu09/browser-results',
  reporter: [['list'], ['json', { outputFile: 'target/cu09/e2e-results.json' }],
    ['html', { outputFolder: 'target/cu09/browser-report', open: 'never' }]],
  use: { baseURL: process.env.FRONTEND_BASE_URL || 'http://127.0.0.1:14309',
    trace: 'retain-on-failure', screenshot: 'only-on-failure', headless: true },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
});

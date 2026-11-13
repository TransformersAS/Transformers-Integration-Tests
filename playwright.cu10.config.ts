import { defineConfig, devices } from '@playwright/test';
const evidence = process.env.CU10_RUN_DIR || 'target/cu10/discovery';
export default defineConfig({
  testDir: './tests/cu10-e2e', workers: 1, fullyParallel: false,
  timeout: 120_000, expect: { timeout: 15_000 },
  outputDir: `${evidence}/browser-results`,
  reporter: [['list'], ['json', { outputFile: `${evidence}/e2e-results.json` }],
    ['html', { outputFolder: `${evidence}/browser-report`, open: 'never' }]],
  use: { baseURL: process.env.FRONTEND_BASE_URL || 'http://127.0.0.1:14310',
    trace: 'retain-on-failure', screenshot: 'only-on-failure', headless: true },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
});

import { defineConfig, devices } from '@playwright/test';

const demoMode = process.env.DEMO_MODE === 'true';

export default defineConfig({
  testDir: './tests/e2e',

  timeout: 30_000,

  expect: {
    timeout: 10_000
  },

  outputDir: 'test-results',

  reporter: [
    ['list'],
    ['html', { open: 'never' }]
  ],

  use: {
    baseURL:
      process.env.FRONTEND_BASE_URL?.trim()
      || 'http://localhost:4300',

    navigationTimeout: 15_000,
    actionTimeout: 10_000,

    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',

    // CI = sin ventana
    // DEMO_MODE=true = navegador visible
    headless: !demoMode,

    launchOptions: {
      // En demo espera 1.3 segundos entre acciones
      slowMo: demoMode ? 1300 : 0,

      args: demoMode
        ? ['--start-maximized']
        : []
    }
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome']
      }
    }
  ]
});
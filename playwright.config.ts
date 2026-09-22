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

  // En GitHub Actions se ejecutan uno por uno.
  // Esto evita que varias pruebas modifiquen al mismo tiempo
  // el carrito, pedidos o inventario del usuario E2E.
  workers: process.env.CI ? 1 : undefined,

  use: {
    baseURL:
      process.env.FRONTEND_BASE_URL?.trim()
      || 'http://localhost:4300',

    navigationTimeout: 15_000,
    actionTimeout: 10_000,

    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',

    // GitHub Actions: headless
    // DEMO_MODE=true: navegador visible
    headless: !demoMode,

    launchOptions: {
      // La ejecución normal sigue siendo rápida.
      // La demo se ralentiza para poder verla en la sustentación.
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
import { defineConfig, devices } from '@playwright/test';

const demoMode = process.env.DEMO_MODE === 'true';

export default defineConfig({
  testDir: './tests/e2e',
  // Un solo trabajador: el backend comparte un único carrito y una única cuenta demo entre compradores, así que dos
  // recorridos de compra a la vez se pisarían. No es una preferencia de estilo, es un límite del sistema bajo prueba.
  workers: 1,
  fullyParallel: false,
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
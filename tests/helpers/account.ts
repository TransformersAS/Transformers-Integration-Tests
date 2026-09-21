import { expect, type Page } from '@playwright/test';

// Datos exclusivos del perfil local de pruebas; se pueden sobrescribir por entorno.
const email = process.env.E2E_EMAIL ?? 'demo@marketplace.local';
const password = process.env.E2E_PASSWORD ?? 'MarketplaceDemo123!';

export async function loginAsBuyer(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Ver cuenta', exact: true }).click();
  const login = page.getByRole('dialog', { name: 'Acceso a tu cuenta', exact: true });
  await login.getByRole('textbox', { name: 'Correo', exact: true }).fill(email);
  await login.getByRole('textbox', { name: 'Contraseña', exact: true }).fill(password);
  const [response] = await Promise.all([
    page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/login'
      && r.request().method() === 'POST'),
    login.getByRole('button', { name: 'Iniciar sesión', exact: true }).click(),
  ]);
  expect(response.ok(), `Login local rechazado (HTTP ${response.status()}). Verificar perfil local y E2E_EMAIL/E2E_PASSWORD.`)
    .toBeTruthy();
  const account = page.getByRole('dialog', { name: 'Seguridad de tu cuenta', exact: true });
  await expect(account).toBeVisible();
  await expect(account.getByRole('button', { name: 'Cerrar sesión', exact: true })).toBeVisible();
  await expect(account.getByText(`Hola, ${email}`, { exact: true })).toBeVisible();
  await selectBuyerRole(page);
  await account.getByRole('button', { name: 'Cerrar panel', exact: true }).click();
  // Recargar el catálogo que pudo consultarse antes de iniciar sesión.
  await page.reload();
  await expect(page.getByRole('button', { name: 'Mis pedidos', exact: true })).toBeVisible();
}

export async function selectBuyerRole(page: Page) {
  const account = page.getByRole('dialog', { name: 'Seguridad de tu cuenta', exact: true });
  const activeRole = account.getByText(/^Rol activo:/);
  await expect(activeRole).toBeVisible();
  if (!(await activeRole.innerText()).includes('COMPRADOR')) {
    // Ionic superpone su etiqueta al botón; activar por teclado evita ese solapamiento.
    await account.getByRole('button', { name: /^Cambiar rol activo,/ }).press('Space');
    await page.getByRole('radio', { name: 'COMPRADOR', exact: true }).click();
    await page.getByRole('button', { name: 'OK', exact: true }).click();
    await expect(page.getByRole('button', { name: 'OK', exact: true })).toBeHidden();
  }
  await expect(activeRole).toHaveText(/Rol activo:\s*COMPRADOR/);
}

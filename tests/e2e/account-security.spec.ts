import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { loginAsBuyer, selectBuyerRole } from '../helpers/account';

const accountDialog = (page: Page) => page.getByRole('dialog', {
  name: 'Seguridad de tu cuenta', exact: true,
});

test('cuenta cambia roles, revoca otra sesión y cierra su sesión principal', async ({ page, browser, baseURL }) => {
  test.setTimeout(90_000);
  // El contexto principal pertenece a la fixture page: Playwright lo cierra incluso al fallar.
  let secondContext: BrowserContext | undefined;
  try {
    await test.step('Iniciar sesión y alternar los roles disponibles', async () => {
      await loginAsBuyer(page); // Comprueba también el correo y el estado autenticado.
      await page.getByRole('button', { name: 'Ver cuenta', exact: true }).click();
      const account = accountDialog(page);
      await expect(account.getByText(/^Roles disponibles:/)).toContainText('COMPRADOR');
      await expect(account.getByText(/^Roles disponibles:/)).toContainText('VENDEDOR');
      await account.getByRole('button', { name: /^Cambiar rol activo,/ }).press('Space');
      await page.getByRole('radio', { name: 'VENDEDOR', exact: true }).click();
      await page.getByRole('button', { name: 'OK', exact: true }).click();
      await expect(page.getByRole('button', { name: 'OK', exact: true })).toBeHidden();
      await expect(account.getByText(/^Rol activo:/)).toHaveText(/Rol activo:\s*VENDEDOR/);
      await selectBuyerRole(page);
    });

    secondContext = await browser.newContext({ baseURL });
    const secondPage = await secondContext.newPage();
    let secondSessionId: string;
    await test.step('Crear una segunda sesión e identificarla desde su propia UI', async () => {
      await loginAsBuyer(secondPage);
      await secondPage.getByRole('button', { name: 'Ver cuenta', exact: true }).click();
      const secondAccount = accountDialog(secondPage);
      await secondAccount.getByRole('button', { name: 'Sesiones activas', exact: true }).click();
      const current = secondAccount.getByRole('listitem').filter({
        has: secondPage.getByText('Esta es tu sesión actual', { exact: true }),
      });
      await expect(current).toHaveCount(1);
      // El identificador es leído de la sesión creada, nunca fijado ni inferido por orden/fecha.
      secondSessionId = (await current.getByRole('definition').first().innerText()).trim();
      expect(secondSessionId).not.toBe('');
    });

    await test.step('Revocar únicamente la segunda sesión y conservar la principal', async () => {
      const account = accountDialog(page);
      await account.getByRole('button', { name: 'Sesiones activas', exact: true }).click();
      const sessions = account.getByRole('listitem');
      await expect.poll(() => sessions.count()).toBeGreaterThanOrEqual(2);
      const current = sessions.filter({ has: page.getByText('Esta es tu sesión actual', { exact: true }) });
      await expect(current).toHaveCount(1);
      const primarySessionId = (await current.getByRole('definition').first().innerText()).trim();
      expect(primarySessionId).not.toBe(secondSessionId);
      const other = sessions.filter({ has: page.getByText(secondSessionId, { exact: true }) });
      await expect(other.getByText('Otra sesión', { exact: true })).toBeVisible();
      await other.getByRole('button', { name: 'Revocar sesión', exact: true }).click();
      const confirmation = account.getByRole('group', { name: 'Confirmar revocación' });
      await expect(confirmation.getByText(secondSessionId, { exact: true })).toBeVisible();
      await confirmation.getByRole('button', { name: 'Sí, revocar', exact: true }).click();
      await expect(account.getByRole('status')).toHaveText('Sesión revocada.');
      await expect(other).toHaveCount(0);
      await expect(current.getByText(primarySessionId, { exact: true })).toBeVisible();

      // Recargar ambas páginas exige que la aplicación vuelva a consultar la sesión real.
      const [revokedSession] = await Promise.all([
        secondPage.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/me'),
        secondPage.reload(),
      ]);
      expect(revokedSession.status(), 'La sesión revocada debe ser rechazada por el backend').toBe(401);
      await secondPage.getByRole('button', { name: 'Ver cuenta', exact: true }).click();
      await expect(secondPage.getByRole('dialog', { name: 'Acceso a tu cuenta', exact: true })
        .getByRole('button', { name: 'Iniciar sesión', exact: true })).toBeVisible();
      await expect(accountDialog(secondPage)).toHaveCount(0);
      await expect(secondPage.getByRole('button', { name: 'Mis pedidos', exact: true })).toHaveCount(0);

      const [primarySession] = await Promise.all([
        page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/me'),
        page.reload(),
      ]);
      expect(primarySession.status(), 'La sesión principal debe seguir autenticada').toBe(200);
      await page.getByRole('button', { name: 'Ver cuenta', exact: true }).click();
      await expect(accountDialog(page).getByRole('button', { name: 'Cerrar sesión', exact: true })).toBeVisible();
      await expect(accountDialog(page).getByText(/^Rol activo:/)).toHaveText(/Rol activo:\s*COMPRADOR/);
    });

    await test.step('Cerrar sesión y comprobar el estado anónimo', async () => {
      await accountDialog(page).getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
      await expect(accountDialog(page)).toHaveCount(0);
      const [loggedOutSession] = await Promise.all([
        page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/me'),
        page.reload(),
      ]);
      expect(loggedOutSession.status()).toBe(401);
      await page.getByRole('button', { name: 'Ver cuenta', exact: true }).click();
      await expect(page.getByRole('dialog', { name: 'Acceso a tu cuenta', exact: true })
        .getByRole('button', { name: 'Iniciar sesión', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Mis pedidos', exact: true })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Cerrar sesión', exact: true })).toHaveCount(0);
    });
  } finally {
    await secondContext?.close();
  }
});

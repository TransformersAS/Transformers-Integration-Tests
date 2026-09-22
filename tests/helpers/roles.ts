import { expect, type Page } from '@playwright/test';

/**
 * Cambio del rol activo desde el panel de cuenta. La misma cuenta del perfil local tiene COMPRADOR y VENDEDOR, así que
 * un recorrido que compra y luego despacha alterna su rol por la UI, igual que lo haría una persona.
 */
export async function selectActiveRole(page: Page, role: 'COMPRADOR' | 'VENDEDOR') {
  const account = page.getByRole('dialog', { name: 'Seguridad de tu cuenta', exact: true });
  if (!(await account.isVisible())) {
    await page.getByRole('button', { name: 'Ver cuenta', exact: true }).click();
  }
  const activeRole = account.getByText(/^Rol activo:/);
  await expect(activeRole).toBeVisible();
  if (!(await activeRole.innerText()).includes(role)) {
    // Ionic superpone su etiqueta al botón; activar por teclado evita ese solapamiento.
    await account.getByRole('button', { name: /^Cambiar rol activo,/ }).press('Space');
    await page.getByRole('radio', { name: role, exact: true }).click();
    await page.getByRole('button', { name: 'OK', exact: true }).click();
    await expect(page.getByRole('button', { name: 'OK', exact: true })).toBeHidden();
  }
  await expect(activeRole).toHaveText(new RegExp(`Rol activo:\\s*${role}`));
  await account.getByRole('button', { name: 'Cerrar panel', exact: true }).click();
  await expect(account).toBeHidden();
}

/** Abre «Pedidos recibidos», el panel del vendedor. Requiere el rol activo VENDEDOR. */
export async function openSellerOrders(page: Page) {
  await selectActiveRole(page, 'VENDEDOR');
  await page.getByRole('button', { name: 'Pedidos recibidos', exact: true }).click();
  // Ionic pinta el título del panel en <ion-title>, que no expone el rol heading.
  await expect(page.locator('ion-title').filter({ hasText: 'Pedidos recibidos' })).toBeVisible();
}

/** Abre «Mis pedidos», el panel del comprador. Requiere el rol activo COMPRADOR. */
export async function openBuyerOrders(page: Page) {
  await selectActiveRole(page, 'COMPRADOR');
  await page.getByRole('button', { name: 'Mis pedidos', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Mis pedidos' })).toBeVisible();
}

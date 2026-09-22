import { expect, type Page } from '@playwright/test';

/**
 * Reclamaciones de compra (CU-13). Los tres roles comparten el mismo panel «app-reclamaciones»; su título y el texto
 * del botón que lo abre cambian según el rol activo, pero la estructura es la misma.
 */

/** Abre el panel de reclamaciones del rol activo (COMPRADOR: «Mis reclamaciones»; VENDEDOR: «Reclamaciones»). */
export async function openClaims(page: Page, buttonName: 'Mis reclamaciones' | 'Reclamaciones') {
  await page.getByRole('button', { name: buttonName, exact: true }).click();
}

/** Cierra el panel de reclamaciones, esté en la lista o en el detalle de una reclamación. */
export async function closeClaims(page: Page) {
  if (await page.getByRole('button', { name: 'Volver', exact: true }).isVisible()) {
    await page.getByRole('button', { name: 'Volver', exact: true }).click();
  }
  await page.getByRole('button', { name: 'Cerrar', exact: true }).click();
}

/**
 * Abre una reclamación como comprador, sobre el pedido y el producto indicados. Los selects de Ionic superponen su
 * etiqueta al botón: se abren con la tecla espacio, igual que el selector de rol (ver helpers/roles.ts).
 */
export async function openClaim(page: Page, orderId: string, productName: string, description: string): Promise<string> {
  await openClaims(page, 'Mis reclamaciones');
  await page.getByRole('button', { name: 'Nueva reclamación', exact: true }).click();

  await page.getByRole('button', { name: 'Compra', exact: true }).press('Space');
  await page.getByRole('radio', { name: new RegExp(`^Pedido #${orderId} ·`) }).click();
  await page.getByRole('button', { name: 'OK', exact: true }).click();

  await page.getByRole('button', { name: 'Producto afectado', exact: true }).press('Space');
  await page.getByRole('radio', { name: productName, exact: true }).click();
  await page.getByRole('button', { name: 'OK', exact: true }).click();

  await page.getByRole('textbox', { name: 'Cuéntanos qué pasó' }).fill(description);
  const [response] = await Promise.all([
    page.waitForResponse(r => new URL(r.url()).pathname === '/api/claims' && r.request().method() === 'POST'),
    page.getByRole('button', { name: 'Enviar reclamación', exact: true }).click(),
  ]);
  expect(response.ok(), `Abrir la reclamación debe responder correctamente (HTTP ${response.status()})`).toBeTruthy();
  await expect(page.getByText('Reclamación enviada. El vendedor la revisará.', { exact: true })).toBeVisible();

  const heading = page.getByRole('heading', { level: 2 }).filter({ hasText: productName }).first();
  const text = await heading.innerText();
  const claimId = text.match(/#(\d+)/)?.[1];
  if (!claimId) {
    throw new Error(`No se pudo leer el número de la reclamación recién creada: "${text}"`);
  }
  return claimId;
}

/** Selecciona una reclamación de la lista por su número, sin importar en qué panel (comprador, vendedor o soporte). */
export async function selectClaim(page: Page, claimId: string) {
  await page.getByRole('button', { name: new RegExp(`^#${claimId} ·`) }).click();
  await expect(page.getByRole('heading', { level: 2 }).filter({ hasText: `#${claimId} ·` })).toBeVisible();
}

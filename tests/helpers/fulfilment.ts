import { expect, type Page } from '@playwright/test';
import { openSellerOrders } from './roles';

/**
 * Preparación y despacho del pedido desde «Pedidos recibidos» (CU-23), con los botones del panel del vendedor. Al
 * marcarlo listo, el marketplace le pide al servicio logístico el envío que después se sigue (CU-24).
 */
export async function prepareAndDispatch(page: Page, orderId: string) {
  await openSellerOrders(page);
  await page.getByRole('button', { name: new RegExp(`Pedido #${orderId} ·`) }).click();
  await expect(page.getByRole('heading', { name: `Pedido #${orderId}`, exact: true })).toBeVisible();

  // El estado vigente se lee en el resumen; el historial repite los estados por los que pasó.
  const summary = page.getByRole('region', { name: 'Resumen del pedido' });
  await expect(summary.getByText('Confirmado', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Iniciar preparación', exact: true }).click();
  await expect(summary.getByText('En preparación', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Listo para despacho', exact: true }).click();
  await expect(summary.getByText('Listo para despacho', { exact: true })).toBeVisible();
}

/** Cierra el panel del vendedor. */
export async function closeSellerOrders(page: Page) {
  await page.getByRole('button', { name: 'Cerrar', exact: true }).click();
  await expect(page.locator('ion-title').filter({ hasText: 'Pedidos recibidos' })).toBeHidden();
}

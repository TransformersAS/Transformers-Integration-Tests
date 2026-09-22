import { expect, type Page } from '@playwright/test';

export const DEMO_PRODUCT = 'Camiseta demo local';

/**
 * Compra una unidad del producto del perfil local desde la interfaz, con los mismos controles que usaría una persona:
 * catálogo, carrito, dirección, envío y pago con tarjeta. Devuelve el número de pedido que muestra la confirmación.
 *
 * Requiere sesión iniciada con el rol activo COMPRADOR y el carrito vacío (el backend todavía comparte un único
 * carrito entre compradores).
 */
export async function buyDemoShirt(page: Page): Promise<string> {
  const cart = page.getByRole('complementary', { name: 'Carrito de compras' });
  // Solo el catálogo: las recomendaciones (CU-02) muestran el mismo producto en otra tarjeta.
  const catalog = page.getByRole('region', { name: 'Piezas que merecen vitrina' });
  const product = catalog.getByRole('article').filter({
    has: page.getByRole('heading', { name: DEMO_PRODUCT, exact: true }),
  });
  await expect(product, 'El perfil local debe provisionar ' + DEMO_PRODUCT).toBeVisible();
  const [added] = await Promise.all([
    page.waitForResponse(r => new URL(r.url()).pathname === '/api/cart/items'
      && r.request().method() === 'POST'),
    product.getByRole('button', { name: 'Agregar', exact: true }).click(),
  ]);
  expect(added.ok(), 'La UI debe poder agregar el producto al carrito').toBeTruthy();

  await page.getByRole('button', { name: 'Abrir carrito', exact: true }).click();
  await expect(cart.getByRole('heading', { name: DEMO_PRODUCT, exact: true })).toBeVisible();
  await cart.getByRole('button', { name: 'Continuar compra', exact: true }).click();

  const checkout = page.getByRole('complementary', { name: 'Finalizar compra' });
  await checkout.getByPlaceholder('Escribe tu dirección de entrega').fill('Calle 123 # 45-67, pruebas E2E');
  await checkout.getByRole('radio', { name: /Estándar/ }).check();
  await checkout.getByRole('button', { name: 'Calcular total', exact: true }).click();
  await expect(checkout.getByRole('heading', { name: 'Resumen de compra' })).toBeVisible();
  await checkout.getByRole('combobox').selectOption({ label: 'Tarjeta' });
  await checkout.getByRole('button', { name: 'Confirmar compra', exact: true }).click();
  await expect(checkout.getByRole('heading', { name: /¡Compra confirmada!/ })).toBeVisible();

  const orderNumber = checkout.getByText(/^\s*#\d+\s*$/);
  await expect(orderNumber).toBeVisible();
  const orderId = (await orderNumber.innerText()).trim().slice(1);
  await checkout.getByRole('button', { name: 'Cerrar checkout' }).click();
  return orderId;
}

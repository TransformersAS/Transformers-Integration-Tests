import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';

test('CU-03 comprador agrega un producto y completa el checkout', async ({ page }) => {
  test.setTimeout(90_000);
  const productName = 'Camiseta demo local';

  await loginAsBuyer(page);

  const cart = page.getByRole('complementary', { name: 'Carrito de compras' });

  await page.getByRole('button', { name: 'Abrir carrito', exact: true }).click();
  await expect(cart.getByRole('heading', { name: /vacío/i }),
    'La cuenta E2E debe comenzar con el carrito vacío').toBeVisible();
  await cart.getByRole('button', { name: 'Cerrar carrito' }).click();

  const catalog = page.locator('#catalogo');
  const product = catalog.locator('article.product-card').filter({ hasText: productName });
  await expect(product, 'Debe existir el producto demo para el checkout').toBeVisible();

  const [added] = await Promise.all([
    page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/cart/items' && response.request().method() === 'POST';
    }),
    product.getByRole('button', { name: 'Agregar', exact: true }).click(),
  ]);
  expect(added.status(), 'Agregar al carrito debe responder Created').toBe(201);

  await page.getByRole('button', { name: 'Abrir carrito', exact: true }).click();
  await expect(cart.getByRole('heading', { name: productName, exact: true })).toBeVisible();
  await cart.getByRole('button', { name: 'Continuar compra', exact: true }).click();

  const checkout = page.getByRole('complementary', { name: 'Finalizar compra' });
  await checkout.getByPlaceholder('Escribe tu dirección de entrega')
    .fill('Calle 123 # 45-67, prueba CU-03');
  await checkout.getByRole('radio', { name: /Estándar/ }).check();

  await checkout.getByRole('button', { name: 'Calcular total', exact: true }).click();
  await expect(checkout.getByRole('heading', { name: 'Resumen de compra', exact: true }))
    .toBeVisible();

  await checkout.getByRole('combobox').selectOption({ label: 'Tarjeta' });
  const [payment] = await Promise.all([
    page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/payments/process' && response.request().method() === 'POST';
    }),
    checkout.getByRole('button', { name: 'Confirmar compra', exact: true }).click(),
  ]);

  expect(payment.ok(), 'El pago y la creación del pedido deben completarse').toBeTruthy();
  await expect(checkout.getByRole('heading', { name: /¡Compra confirmada!/ })).toBeVisible();
  await expect(checkout.getByText('Tu pedido fue creado correctamente.', { exact: true })).toBeVisible();

  await checkout.getByRole('button', { name: 'Cerrar checkout', exact: true }).click();
  await page.getByRole('button', { name: 'Abrir carrito', exact: true }).click();
  await expect(cart.getByRole('heading', { name: /vacío/i }),
    'Después de una compra aprobada el carrito debe quedar vacío').toBeVisible();
});

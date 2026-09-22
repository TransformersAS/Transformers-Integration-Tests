import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';

test('CU-03 comprador agrega un producto y completa el checkout', async ({ page }) => {
  test.setTimeout(90_000);

  const demoMode = process.env.DEMO_MODE === 'true';

  const demoPause = async (milliseconds: number) => {
    if (demoMode) {
      await page.waitForTimeout(milliseconds);
    }
  };

  const productName = 'Camiseta demo local';

  // =========================================================
  // 1. LOGIN
  // =========================================================

  await loginAsBuyer(page);

  const cart = page.getByRole('complementary', {
    name: 'Carrito de compras'
  });

  // =========================================================
  // 2. COMPROBAR CARRITO VACÍO
  // =========================================================

  await page
    .getByRole('button', {
      name: 'Abrir carrito',
      exact: true
    })
    .click();

  await expect(
    cart.getByRole('heading', {
      name: /vacío/i
    }),
    'La cuenta E2E debe comenzar con el carrito vacío'
  ).toBeVisible();

  await demoPause(2000);

  await cart
    .getByRole('button', {
      name: 'Cerrar carrito'
    })
    .click();

  // =========================================================
  // 3. AGREGAR PRODUCTO
  // =========================================================

  const catalog = page.locator('#catalogo');

  const product = catalog
    .locator('article.product-card')
    .filter({
      hasText: productName
    });

  await expect(
    product,
    'Debe existir el producto demo para el checkout'
  ).toBeVisible();

  await demoPause(1500);

  const [added] = await Promise.all([
    page.waitForResponse(response => {
      const url = new URL(response.url());

      return url.pathname === '/api/cart/items'
        && response.request().method() === 'POST';
    }),

    product
      .getByRole('button', {
        name: 'Agregar',
        exact: true
      })
      .click()
  ]);

  expect(
    added.status(),
    'Agregar al carrito debe responder Created'
  ).toBe(201);

  // =========================================================
  // 4. CARRITO
  // =========================================================

  await page
    .getByRole('button', {
      name: 'Abrir carrito',
      exact: true
    })
    .click();

  await expect(
    cart.getByRole('heading', {
      name: productName,
      exact: true
    })
  ).toBeVisible();

  // Para que el profesor alcance a ver el producto en carrito
  await demoPause(3000);

  await cart
    .getByRole('button', {
      name: 'Continuar compra',
      exact: true
    })
    .click();

  // =========================================================
  // 5. CHECKOUT
  // =========================================================

  const checkout = page.getByRole('complementary', {
    name: 'Finalizar compra'
  });

  await checkout
    .getByPlaceholder('Escribe tu dirección de entrega')
    .fill('Calle 123 # 45-67, prueba CU-03');

  await checkout
    .getByRole('radio', {
      name: /Estándar/
    })
    .check();

  await demoPause(2000);

  // =========================================================
  // 6. CALCULAR TOTAL
  // =========================================================

  await checkout
    .getByRole('button', {
      name: 'Calcular total',
      exact: true
    })
    .click();

  const summary = checkout.getByRole('heading', {
    name: 'Resumen de compra',
    exact: true
  });

  await expect(summary).toBeVisible();

  await summary.scrollIntoViewIfNeeded();

  // IMPORTANTE PARA LA DEMO:
  // deja visible el resumen y el total
  await demoPause(4000);

  // =========================================================
  // 7. MÉTODO DE PAGO
  // =========================================================

  await checkout
    .getByRole('combobox')
    .selectOption({
      label: 'Tarjeta'
    });

  // Deja que se vea claramente "Tarjeta"
  await demoPause(3000);

  const confirmButton = checkout.getByRole('button', {
    name: 'Confirmar compra',
    exact: true
  });

  await confirmButton.scrollIntoViewIfNeeded();

  // Se queda aquí antes de realizar el pago
  await demoPause(3000);

  // =========================================================
  // 8. PROCESAR PAGO
  // =========================================================

  const [payment] = await Promise.all([
    page.waitForResponse(response => {
      const url = new URL(response.url());

      return url.pathname === '/api/payments/process'
        && response.request().method() === 'POST';
    }),

    confirmButton.click()
  ]);

  // Validamos técnicamente que el pago fue aprobado
  expect(
    payment.ok(),
    'El pago y la creación del pedido deben completarse'
  ).toBeTruthy();

  // =========================================================
  // 9. CONFIRMACIÓN DE COMPRA
  // =========================================================

  const confirmed = checkout.getByRole('heading', {
    name: /¡Compra confirmada!/
  });

  await expect(confirmed).toBeVisible();

  await expect(
    checkout.getByText(
      'Tu pedido fue creado correctamente.',
      { exact: true }
    )
  ).toBeVisible();

  await confirmed.scrollIntoViewIfNeeded();

  // ⭐ MOMENTO PRINCIPAL DE LA PRESENTACIÓN
  // La pantalla queda 6 segundos mostrando que el pago
  // y la creación del pedido fueron exitosos.
  await demoPause(6000);

  // =========================================================
  // 10. COMPROBAR CARRITO VACÍO
  // =========================================================

  await checkout
    .getByRole('button', {
      name: 'Cerrar checkout',
      exact: true
    })
    .click();

  await page
    .getByRole('button', {
      name: 'Abrir carrito',
      exact: true
    })
    .click();

  await expect(
    cart.getByRole('heading', {
      name: /vacío/i
    }),
    'Después de una compra aprobada el carrito debe quedar vacío'
  ).toBeVisible();

  // Evidencia visual final
  await demoPause(3000);
});
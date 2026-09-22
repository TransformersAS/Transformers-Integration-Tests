import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';

test(
  'DEMO CU-02 + CU-03: recomendación, rechazo y compra aprobada',
  async ({ page }) => {

    test.setTimeout(180_000);

    const demoMode =
      process.env.DEMO_MODE === 'true';

    const pause = async (ms: number) => {
      if (demoMode) {
        await page.waitForTimeout(ms);
      }
    };


    // =========================================================
    // 1. LOGIN
    // =========================================================

    await loginAsBuyer(page);

    await pause(2000);


    // =========================================================
    // 2. RECOMENDACIONES
    // =========================================================

    const recommendations =
      page.locator('.recommendations-section');

    const recommendationTitle =
      recommendations.getByRole('heading', {
        name: 'Recomendado para ti',
        exact: true
      });

    await expect(recommendationTitle).toBeVisible();

    await recommendations.scrollIntoViewIfNeeded();

    // El profesor alcanza a ver la sección
    await pause(5000);


    // =========================================================
    // 3. SELECCIONAR PRODUCTO RECOMENDADO
    // =========================================================

    const recommendedProduct =
      recommendations
        .locator('article.recommendation-card')
        .first();

    await expect(recommendedProduct).toBeVisible();

    const recommendedName =
      (
        await recommendedProduct
          .getByRole('heading')
          .innerText()
      ).trim();

    await recommendedProduct.hover();

    // Mostrar claramente cuál producto se eligió
    await pause(4000);


    // =========================================================
    // 4. REGISTRAR VIEW
    // =========================================================

    const [viewResponse] =
      await Promise.all([

        page.waitForResponse(response => {
          const url = new URL(response.url());

          return url.pathname === '/api/interactions'
            && response.request().method() === 'POST'
            && (response.request().postData() ?? '')
              .includes('"interactionType":"VIEW"');
        }),

        recommendedProduct.click()
      ]);

    expect(viewResponse.status()).toBe(201);

    await pause(1500);


    // =========================================================
    // 5. AGREGAR ESE MISMO PRODUCTO RECOMENDADO
    // =========================================================

    const [addedToCart] =
      await Promise.all([

        page.waitForResponse(response => {
          const url = new URL(response.url());

          return url.pathname === '/api/cart/items'
            && response.request().method() === 'POST';
        }),

        recommendedProduct
          .getByRole('button', {
            name: 'Agregar',
            exact: true
          })
          .click()
      ]);

    expect(addedToCart.status()).toBe(201);

    await pause(2000);


    // =========================================================
    // 6. MOSTRAR CARRITO
    // =========================================================

    await page
      .getByRole('button', {
        name: 'Abrir carrito',
        exact: true
      })
      .click();

    const cart =
      page.getByRole('complementary', {
        name: 'Carrito de compras'
      });

    await expect(
      cart.getByRole('heading', {
        name: recommendedName,
        exact: true
      })
    ).toBeVisible();

    // ⭐ Se ve que el producto recomendado llegó al carrito
    await pause(5000);


    // =========================================================
    // 7. CHECKOUT
    // =========================================================

    await cart
      .getByRole('button', {
        name: 'Continuar compra',
        exact: true
      })
      .click();

    const checkout =
      page.getByRole('complementary', {
        name: 'Finalizar compra'
      });

    await checkout
      .getByPlaceholder(
        'Escribe tu dirección de entrega'
      )
      .fill(
        'Calle 123 # 45-67, demostración Marketplace'
      );

    await checkout
      .getByRole('radio', {
        name: /Estándar/
      })
      .check();


    // =========================================================
    // 8. CALCULAR TOTAL
    // =========================================================

    await checkout
      .getByRole('button', {
        name: 'Calcular total',
        exact: true
      })
      .click();

    const summary =
      checkout.getByRole('heading', {
        name: 'Resumen de compra',
        exact: true
      });

    await expect(summary).toBeVisible();

    await summary.scrollIntoViewIfNeeded();

    // ⭐ Mostrar precio, envío y total
    await pause(5000);


    // =========================================================
    // 9. PRIMER INTENTO: PAGO RECHAZADO
    // =========================================================

    const paymentMethod =
      checkout.getByRole('combobox');

    await paymentMethod.selectOption({
      label: 'Simular pago rechazado'
    });

    // Mostrar al profesor el método seleccionado
    await pause(4000);

    const confirmButton =
      checkout.getByRole('button', {
        name: 'Confirmar compra',
        exact: true
      });

    await confirmButton.scrollIntoViewIfNeeded();

    await pause(2500);


    const [rejectedPayment] =
      await Promise.all([

        page.waitForResponse(response => {
          const url = new URL(response.url());

          return url.pathname ===
            '/api/payments/process'
            && response.request().method() === 'POST';
        }),

        confirmButton.click()
      ]);


    expect(rejectedPayment.ok()).toBeTruthy();

    const rejectedBody =
      await rejectedPayment.json();

    expect(rejectedBody.status).toBe('REJECTED');


    // =========================================================
    // 10. MOSTRAR RECHAZO
    // =========================================================

    const rejectedTitle =
      checkout.getByRole('heading', {
        name: 'Pago rechazado',
        exact: true
      });

    await expect(rejectedTitle).toBeVisible();

    await expect(
      checkout.getByText(
        /No se realizó la compra/
      )
    ).toBeVisible();

    await rejectedTitle.scrollIntoViewIfNeeded();

    // ⭐ MUY IMPORTANTE PARA LA PRESENTACIÓN
    await pause(7000);


    // =========================================================
    // 11. SEGUNDO INTENTO: TARJETA
    // =========================================================

    await paymentMethod.selectOption({
      label: 'Tarjeta'
    });

    await pause(3500);


    const [approvedPayment] =
      await Promise.all([

        page.waitForResponse(response => {
          const url = new URL(response.url());

          return url.pathname ===
            '/api/payments/process'
            && response.request().method() === 'POST';
        }),

        confirmButton.click()
      ]);


    expect(approvedPayment.ok()).toBeTruthy();

    const approvedBody =
      await approvedPayment.json();

    expect(approvedBody.status).toBe('APPROVED');


    // =========================================================
    // 12. COMPRA APROBADA
    // =========================================================

    const approvedTitle =
      checkout.getByRole('heading', {
        name: /¡Compra confirmada!/
      });

    await expect(approvedTitle).toBeVisible();

    await expect(
      checkout.getByText(
        'Tu pedido fue creado correctamente.',
        { exact: true }
      )
    ).toBeVisible();

    await approvedTitle.scrollIntoViewIfNeeded();

    // ⭐ Aquí se ve claramente que finalmente pagó
    await pause(7000);


    // =========================================================
    // 13. CARRITO VACÍO
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
      })
    ).toBeVisible();

    await pause(4000);
  }
);
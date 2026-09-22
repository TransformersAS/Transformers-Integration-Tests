import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';

test(
  'CU-02 muestra recomendaciones y registra una visualización',
  async ({ page }) => {

    test.setTimeout(90_000);

    const demoMode =
      process.env.DEMO_MODE === 'true';

    const demoPause = async (milliseconds: number) => {
      if (demoMode) {
        await page.waitForTimeout(milliseconds);
      }
    };


    // =========================================================
    // 1. LOGIN
    // =========================================================

    await loginAsBuyer(page);

    await demoPause(2000);


    // =========================================================
    // 2. OBTENER RECOMENDACIONES
    // =========================================================

    const [recommendationsResponse] =
      await Promise.all([

        page.waitForResponse(response => {
          const url = new URL(response.url());

          return url.pathname === '/api/recommendations'
            && response.request().method() === 'GET';
        }),

        page.reload()
      ]);


    expect(
      recommendationsResponse.ok(),
      'El backend debe devolver las recomendaciones correctamente'
    ).toBeTruthy();


    // =========================================================
    // 3. MOSTRAR SECCIÓN DE RECOMENDACIONES
    // =========================================================

    const recommendations =
      page.locator('.recommendations-section');

    const title =
      recommendations.getByRole('heading', {
        name: 'Recomendado para ti',
        exact: true
      });


    await expect(title).toBeVisible();

    await recommendations.scrollIntoViewIfNeeded();


    // ⭐ MOMENTO IMPORTANTE:
    // deja visible toda la sección de recomendaciones
    await demoPause(6000);


    // =========================================================
    // 4. MOSTRAR PRODUCTO RECOMENDADO
    // =========================================================

    const firstCard =
      recommendations
        .locator('article.recommendation-card')
        .first();


    await expect(
      firstCard,
      'Debe mostrarse al menos un producto recomendado'
    ).toBeVisible();


    await firstCard.scrollIntoViewIfNeeded();

    // Playwright coloca el mouse sobre el producto
    // para que el profesor vea claramente cuál seleccionará.
    await firstCard.hover();

    // ⭐ Se queda mostrando el producto recomendado
    await demoPause(5000);


    // =========================================================
    // 5. REGISTRAR VISUALIZACIÓN
    // =========================================================

    const [viewInteraction] =
      await Promise.all([

        page.waitForResponse(response => {
          const url = new URL(response.url());

          return url.pathname === '/api/interactions'
            && response.request().method() === 'POST'
            && (response.request().postData() ?? '')
              .includes('"interactionType":"VIEW"');
        }),

        firstCard.click()
      ]);


    expect(
      viewInteraction.status(),
      'La visualización debe registrarse'
    ).toBe(201);


    // =========================================================
    // 6. VERIFICAR INTERACCIÓN
    // =========================================================

    const body =
      viewInteraction.request().postDataJSON();


    expect(body).toMatchObject({
      interactionType: 'VIEW'
    });


    expect(body.productId).not.toBeNull();


    // Pequeña pausa final para la sustentación
    await demoPause(3000);
  }
);
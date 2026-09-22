import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';

test(
  'CU-02 muestra recomendaciones y registra una visualización',
  async ({ page }) => {

    test.setTimeout(90_000);

    // 1. Login real como comprador
    await loginAsBuyer(page);

    // 2. Recargar el frontend y comprobar que consulta
    //    realmente las recomendaciones al backend.
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


    // 3. Comprobar que el usuario las ve en la interfaz.
    const recommendations =
      page.locator('.recommendations-section');

    await expect(
      recommendations.getByRole('heading', {
        name: 'Recomendado para ti',
        exact: true
      })
    ).toBeVisible();


    const firstCard =
      recommendations
        .locator('article.recommendation-card')
        .first();

    await expect(firstCard).toBeVisible();


    // 4. Al seleccionar una recomendación,
    //    el frontend debe registrar una interacción VIEW.
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


    // 5. Verificamos que efectivamente se envió VIEW.
    const body =
      viewInteraction.request().postDataJSON();

    expect(body).toMatchObject({
      interactionType: 'VIEW'
    });

    expect(body.productId).not.toBeNull();
  }
);
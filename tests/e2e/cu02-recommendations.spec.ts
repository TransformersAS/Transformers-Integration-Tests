import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';

test('CU-02 registra búsqueda, refresca recomendaciones y registra una visualización', async ({ page }) => {
  test.setTimeout(90_000);

  await loginAsBuyer(page);

  const search = page.getByRole('searchbox', { name: 'Buscar productos', exact: true });
  await expect(search).toBeVisible();

  const [searchInteraction, refreshedRecommendations] = await Promise.all([
    page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/interactions'
        && response.request().method() === 'POST'
        && (response.request().postData() ?? '').includes('SEARCH');
    }),
    page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/recommendations'
        && response.request().method() === 'GET';
    }),
    search.fill('camiseta').then(() => search.press('Enter')),
  ]);

  expect(searchInteraction.status(), 'La búsqueda debe registrarse como interacción').toBe(201);
  expect(refreshedRecommendations.ok(), 'Las recomendaciones deben refrescarse').toBeTruthy();

  const recommendations = page.locator('.recommendations-section');
  await expect(recommendations.getByRole('heading', { name: 'Recomendado para ti', exact: true }))
    .toBeVisible();

  const firstCard = recommendations.locator('article.recommendation-card').first();
  await expect(firstCard).toBeVisible();

  const [viewInteraction] = await Promise.all([
    page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.pathname === '/api/interactions'
        && response.request().method() === 'POST'
        && (response.request().postData() ?? '').includes('VIEW');
    }),
    firstCard.click(),
  ]);

  expect(viewInteraction.status(), 'La visualización recomendada debe registrarse').toBe(201);
});

import { expect, test } from '@playwright/test';

test('Mercado Integral carga y tolera la consulta de sesión del visitante', async ({ page }) => {
  const pageErrors: string[] = [];
  page.on('pageerror', error => pageErrors.push(error.message));

  // Observar la petición real de la aplicación antes de navegar, sin mocks.
  const [documentResponse, sessionResponse] = await Promise.all([
    page.goto('/'),
    page.waitForResponse(response => {
      const url = new URL(response.url());
      return url.origin === new URL(page.url()).origin
        && url.pathname === '/api/auth/me'
        && response.request().method() === 'GET';
    }),
  ]);

  expect(documentResponse, 'El frontend debe responder a la navegación').not.toBeNull();
  expect(documentResponse!.ok(), 'La página debe responder con HTTP 2xx').toBeTruthy();
  expect(await sessionResponse.finished(), 'La respuesta /api debe completarse').toBeNull();
  // Un visitante sin sesión puede recibir 401; no implica un fallo fatal.
  expect([200, 401], 'La consulta de sesión debe responder sin fallo del servidor')
    .toContain(sessionResponse.status());

  await expect(page).toHaveTitle('Mercado Integral');
  await expect(page.getByRole('link', { name: 'Marketplace Integral, inicio', exact: true }))
    .toBeVisible();
  await expect(page.getByRole('heading', { name: 'Objetos con buena historia.', exact: true }))
    .toBeVisible();
  await expect(page.getByRole('searchbox', { name: 'Buscar productos', exact: true }))
    .toBeVisible();

  const fatalMessage = /error fatal|fatal error|internal server error|error interno del servidor|algo salió mal|something went wrong|application error/i;
  await expect(page.getByText(fatalMessage)).toHaveCount(0);
  await expect(page.getByRole('alert').filter({ hasText: /error|fall[oó]|no se pudo/i }))
    .toHaveCount(0);
  expect(pageErrors, 'No debe haber excepciones JavaScript sin manejar').toEqual([]);
});

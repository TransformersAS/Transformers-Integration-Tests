import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';
import { selectActiveRole } from '../helpers/roles';
import { buyDemoShirt, DEMO_PRODUCT } from '../helpers/purchase';
import { closeClaims, openClaim, openClaims, selectClaim } from '../helpers/claims';

/**
 * CU-13 de punta a punta: el comprador abre una reclamación sobre una compra real, el vendedor de la tienda la
 * atiende desde su propio panel y el comprador decide. La misma cuenta local actúa en las dos sesiones (COMPRADOR y
 * VENDEDOR), igual que en el resto de este repositorio: nadie fija un estado a mano, cada paso pasa por los
 * controles reales de la interfaz.
 *
 * No hay ninguna cuenta local con el rol SOPORTE, así que la decisión de una reclamación escalada por un agente de
 * soporte no se puede recorrer desde la interfaz con este perfil; queda fuera de este archivo (documentado en
 * docs/pruebas-cu13-cu15.md, igual que en las pruebas de integración por HTTP).
 */
test('el comprador abre una reclamación, el vendedor propone una solución y el comprador la acepta', async ({ page }) => {
  test.setTimeout(120_000);

  let orderId: string;
  await test.step('Comprar el producto del perfil local como COMPRADOR', async () => {
    await loginAsBuyer(page);
    orderId = await buyDemoShirt(page);
  });

  let claimId: string;
  await test.step('Abrir una reclamación sobre esa compra', async () => {
    claimId = await openClaim(page, orderId, DEMO_PRODUCT, 'Llegó con un defecto de fábrica');
    await selectClaim(page, claimId);
    await expect(page.getByText('Abierta', { exact: true })).toBeVisible();
    // Sin exact: el texto comparte párrafo con la etiqueta "Problema:" y trae un espacio inicial.
    await expect(page.getByText('Llegó con un defecto de fábrica')).toBeVisible();
    await expect(page.getByRole('listitem').filter({ hasText: 'Todavía no hay mensajes.' })).toBeVisible();
    await closeClaims(page);
  });

  await test.step('El vendedor pide información', async () => {
    await selectActiveRole(page, 'VENDEDOR');
    await openClaims(page, 'Reclamaciones');
    await selectClaim(page, claimId);
    await page.getByRole('textbox', { name: 'Mensaje para el comprador' }).fill('¿Puedes enviar una foto del defecto?');
    const [response] = await Promise.all([
      page.waitForResponse(r => new URL(r.url()).pathname === `/api/seller/claims/${claimId}/request-info`),
      page.getByRole('button', { name: 'Pedir información', exact: true }).click(),
    ]);
    expect(response.ok()).toBeTruthy();
    await expect(page.getByText('Información solicitada', { exact: true }).first()).toBeVisible();
    await closeClaims(page);
  });

  await test.step('El comprador responde y el vendedor propone una solución con reembolso', async () => {
    await selectActiveRole(page, 'COMPRADOR');
    await openClaims(page, 'Mis reclamaciones');
    await selectClaim(page, claimId);
    await page.getByRole('textbox', { name: 'Escribe un mensaje o el motivo para escalar' })
      .fill('Aquí la foto: http://evidencia.example/1.jpg');
    await Promise.all([
      page.waitForResponse(r => new URL(r.url()).pathname === `/api/claims/${claimId}/messages`),
      page.getByRole('button', { name: 'Enviar mensaje', exact: true }).click(),
    ]);
    await expect(page.getByText('Abierta', { exact: true })).toBeVisible();
    await closeClaims(page);

    await selectActiveRole(page, 'VENDEDOR');
    await openClaims(page, 'Reclamaciones');
    await selectClaim(page, claimId);
    await page.getByRole('textbox', { name: 'Mensaje para el comprador' }).fill('Te devolvemos lo pagado');
    await page.getByRole('spinbutton', { name: /Reembolso que ofreces/ }).fill('25000');
    const [proposeResponse] = await Promise.all([
      page.waitForResponse(r => new URL(r.url()).pathname === `/api/seller/claims/${claimId}/propose`),
      page.getByRole('button', { name: 'Proponer solución', exact: true }).click(),
    ]);
    expect(proposeResponse.ok()).toBeTruthy();
    await expect(page.getByText('Solución propuesta', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Reembolso ofrecido: $25,000', { exact: false })).toBeVisible();
    await closeClaims(page);
  });

  await test.step('El comprador acepta la solución y la reclamación queda resuelta', async () => {
    await selectActiveRole(page, 'COMPRADOR');
    await openClaims(page, 'Mis reclamaciones');
    await selectClaim(page, claimId);
    await expect(page.getByRole('button', { name: 'Aceptar la solución', exact: true })).toBeVisible();
    const [response] = await Promise.all([
      page.waitForResponse(r => new URL(r.url()).pathname === `/api/claims/${claimId}/accept`),
      page.getByRole('button', { name: 'Aceptar la solución', exact: true }).click(),
    ]);
    expect(response.ok()).toBeTruthy();
    await expect(page.getByText('Resuelta', { exact: true }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Aceptar la solución', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Escalar a soporte', exact: true })).toHaveCount(0);

    // Vuelve a consultar por la UI: la resolución quedó persistida, no solo en la respuesta del clic.
    await closeClaims(page);
    await openClaims(page, 'Mis reclamaciones');
    await selectClaim(page, claimId);
    await expect(page.getByText('Resuelta', { exact: true }).first()).toBeVisible();
  });
});

test('sin acuerdo con el vendedor, el comprador escala la reclamación a soporte', async ({ page }) => {
  test.setTimeout(90_000);

  let orderId: string;
  await test.step('Comprar el producto del perfil local como COMPRADOR', async () => {
    await loginAsBuyer(page);
    orderId = await buyDemoShirt(page);
  });

  await test.step('Abrir una reclamación y escalarla directamente', async () => {
    const claimId = await openClaim(page, orderId, DEMO_PRODUCT, 'El vendedor no responde a mis mensajes');
    await selectClaim(page, claimId);
    await page.getByRole('textbox', { name: 'Escribe un mensaje o el motivo para escalar' })
      .fill('No llegamos a un acuerdo con el vendedor');
    const [response] = await Promise.all([
      page.waitForResponse(r => new URL(r.url()).pathname === `/api/claims/${claimId}/escalate`),
      page.getByRole('button', { name: 'Escalar a soporte', exact: true }).click(),
    ]);
    expect(response.ok()).toBeTruthy();
    await expect(page.getByText('Escalada a soporte', { exact: true }).first()).toBeVisible();
    // Una vez escalada, ya no se puede volver a escalar ni actuar como si siguiera abierta.
    await expect(page.getByRole('button', { name: 'Escalar a soporte', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Aceptar la solución', exact: true })).toHaveCount(0);
  });
});

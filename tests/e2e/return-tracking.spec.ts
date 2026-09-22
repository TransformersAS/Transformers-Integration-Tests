import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';
import { closeSellerOrders, prepareAndDispatch } from '../helpers/fulfilment';
import { reportReturn, reportShipment, requireWebhookSecret } from '../helpers/logistics';
import { buyDemoShirt } from '../helpers/purchase';
import { openBuyerOrders, openSellerOrders } from '../helpers/roles';
import { returnInPickup, returnRequestStatus } from '../helpers/returns';

/**
 * CU-25 de punta a punta: el retorno de una devolución aprobada se sigue desde la interfaz mientras el servicio
 * logístico informa sus novedades. Comprador y vendedor consultan el mismo retorno por su número, y la entrega al
 * vendedor es la que hace que la devolución de CU-19 pase a inspección.
 */
test('el retorno de una devolución se sigue hasta que llega al vendedor', async ({ page }) => {
  test.setTimeout(180_000);
  requireWebhookSecret();

  let returnId: string;
  await test.step('Llegar a una devolución aprobada con su retorno en curso', async () => {
    await loginAsBuyer(page);
    const orderId = await buyDemoShirt(page);
    await prepareAndDispatch(page, orderId);
    await closeSellerOrders(page);

    // Solo se devuelve lo entregado: el proveedor informa el envío hasta la entrega.
    await reportShipment(page, orderId, 'PICKED_UP');
    await reportShipment(page, orderId, 'IN_TRANSIT');
    await reportShipment(page, orderId, 'DELIVERED');

    returnId = await returnInPickup(page, orderId);
    expect(await returnRequestStatus(page, returnId), 'La devolución queda aprobada a la espera del retorno')
      .toBe('APPROVED');
  });

  const tracking = page.getByRole('region', { name: 'Seguimiento de la devolución' });
  await test.step('El comprador consulta su devolución por número y ve la recogida pendiente', async () => {
    await openBuyerOrders(page);
    const lookup = page.getByRole('region', { name: 'Seguimiento de devolución' });
    await lookup.getByRole('spinbutton', { name: 'N.º de devolución' }).fill(returnId);
    await lookup.getByRole('button', { name: 'Consultar', exact: true }).click();

    await expect(tracking.getByRole('heading', { name: `Seguimiento de la devolución #${returnId}` })).toBeVisible();
    await expect(tracking.getByText(`Recogida pendiente · Guía TRK-R${returnId}`)).toBeVisible();
    await expect(tracking.getByRole('button', { name: 'Actualizar seguimiento' })).toBeVisible();
  });

  await test.step('La recogida y el retorno en curso quedan en la línea de tiempo', async () => {
    expect(await reportReturn(page, returnId, 'PICKED_UP')).toBe('APPLIED');
    await tracking.getByRole('button', { name: 'Actualizar seguimiento' }).click();
    await expect(tracking.getByRole('listitem').filter({ hasText: 'Recogido' })).toHaveCount(1);

    expect(await reportReturn(page, returnId, 'IN_TRANSIT')).toBe('APPLIED');
    await tracking.getByRole('button', { name: 'Actualizar seguimiento' }).click();
    await expect(tracking.getByText(`En retorno · Guía TRK-R${returnId}`)).toBeVisible();
  });

  await test.step('La entrega al vendedor cierra el retorno y pone la devolución en inspección', async () => {
    expect(await reportReturn(page, returnId, 'DELIVERED_TO_SELLER')).toBe('APPLIED');
    await tracking.getByRole('button', { name: 'Actualizar seguimiento' }).click();

    await expect(tracking.getByText(`Entregado al vendedor · Guía TRK-R${returnId}`)).toBeVisible();
    await expect(tracking.getByText(/^Entregado el /)).toBeVisible();
    await expect(tracking.getByText('El seguimiento finalizó: ya no se consulta al servicio logístico.')).toBeVisible();
    await expect(tracking.getByRole('button', { name: 'Actualizar seguimiento' })).toHaveCount(0);

    // La entrega es la señal con la que CU-19 abre sus 24 h de inspección.
    expect(await returnRequestStatus(page, returnId)).toBe('IN_INSPECTION');
  });

  await test.step('El vendedor consulta el mismo retorno desde su panel', async () => {
    await page.getByRole('button', { name: 'Cerrar pedidos', exact: true }).click();
    await openSellerOrders(page);

    const lookup = page.getByRole('region', { name: 'Seguimiento de devolución' });
    await lookup.getByRole('spinbutton', { name: 'N.º de devolución' }).fill(returnId);
    await lookup.getByRole('button', { name: 'Consultar', exact: true }).click();

    await expect(tracking.getByText(`Entregado al vendedor · Guía TRK-R${returnId}`)).toBeVisible();
    await expect(tracking.getByRole('listitem').filter({ hasText: 'Entregado al vendedor' })).toHaveCount(1);
  });
});

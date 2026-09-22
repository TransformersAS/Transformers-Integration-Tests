import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';
import { openBuyerOrders, selectActiveRole } from '../helpers/roles';
import { closeSellerOrders, prepareAndDispatch } from '../helpers/fulfilment';
import { buyDemoShirt, DEMO_PRODUCT } from '../helpers/purchase';
import { newEventId, reportShipment, requireWebhookSecret } from '../helpers/logistics';

/**
 * CU-23 y CU-24 de punta a punta: la misma persona compra, despacha su pedido como vendedora y sigue el envío desde los
 * dos paneles mientras el servicio logístico informa las novedades por su webhook firmado. Nadie fija un estado de
 * transporte a mano: la interfaz solo consulta.
 */
test('el pedido se despacha y el seguimiento avanza con lo que informa el servicio logístico', async ({ page }) => {
  test.setTimeout(150_000);
  requireWebhookSecret();

  let orderId: string;
  await test.step('Comprar el producto del perfil local como COMPRADOR', async () => {
    await loginAsBuyer(page);
    orderId = await buyDemoShirt(page);
  });

  const seller = page.locator('ion-title').filter({ hasText: 'Pedidos recibidos' });
  await test.step('Preparar y marcar listo para despacho como VENDEDOR', async () => {
    await prepareAndDispatch(page, orderId);
    await expect(seller).toBeVisible();
    // El envío lo crea el servicio logístico al despachar; su guía es la que seguirá el resto del recorrido.
    const shipment = page.getByRole('region', { name: 'Envío' });
    await expect(shipment.getByText(`Guía: TRK-${orderId} · CREATED`)).toBeVisible();
  });

  const tracking = page.getByRole('region', { name: 'Seguimiento del envío' });
  await test.step('El vendedor ve la recogida y el tránsito que informa el proveedor', async () => {
    expect(await reportShipment(page, orderId, 'PICKED_UP')).toBe('APPLIED');
    expect(await reportShipment(page, orderId, 'IN_TRANSIT')).toBe('APPLIED');

    // «Actualizar seguimiento» pide al backend que vuelva a consultar y devuelve el seguimiento vigente.
    await tracking.getByRole('button', { name: 'Actualizar seguimiento' }).click();
    await expect(tracking.getByText('En camino', { exact: true }).first()).toBeVisible();
    await expect(tracking.getByRole('listitem').filter({ hasText: 'Recogido' })).toHaveCount(1);
    await expect(tracking.getByText(`En camino · Guía TRK-${orderId}`)).toBeVisible();
  });

  await test.step('El comprador ve la misma línea de tiempo en su propio panel', async () => {
    await closeSellerOrders(page);
    await openBuyerOrders(page);
    const orders = page.getByRole('dialog', { name: 'Mis pedidos' });
    await orders.getByRole('button', { name: `Ver detalle del pedido ${orderId}`, exact: true }).click();
    await expect(orders.getByText(DEMO_PRODUCT, { exact: true })).toBeVisible();

    await expect(tracking.getByRole('listitem').filter({ hasText: 'Recogido' })).toHaveCount(1);
    await expect(tracking.getByRole('listitem').filter({ hasText: 'En camino' })).toHaveCount(1);
    await expect(tracking.getByRole('button', { name: 'Actualizar seguimiento' })).toBeVisible();
  });

  await test.step('Una novedad atrasada se marca y no hace retroceder el estado', async () => {
    expect(await reportShipment(page, orderId, 'DELIVERY_EXCEPTION')).toBe('APPLIED');
    // El proveedor reenvía una recogida que ya había informado: no puede volver atrás el seguimiento.
    expect(await reportShipment(page, orderId, 'PICKED_UP')).toBe('OUT_OF_ORDER');

    await tracking.getByRole('button', { name: 'Actualizar seguimiento' }).click();
    await expect(tracking.getByText('Novedad de entrega', { exact: true }).first()).toBeVisible();
    await expect(tracking.getByText('Recibida fuera de orden: no cambió el estado')).toBeVisible();
    await expect(tracking.getByRole('listitem').filter({ hasText: 'Recogido' })).toHaveCount(2);
  });

  await test.step('La entrega cierra el seguimiento y muestra su constancia', async () => {
    const delivery = newEventId();
    expect(await reportShipment(page, orderId, 'DELIVERED', delivery)).toBe('APPLIED');
    // El proveedor reintenta la misma entrega: el marketplace no la aplica dos veces.
    expect(await reportShipment(page, orderId, 'DELIVERED', delivery)).toBe('DUPLICATE');

    await tracking.getByRole('button', { name: 'Actualizar seguimiento' }).click();
    await expect(tracking.getByText(/^Entregado el /)).toBeVisible();
    await expect(tracking.getByText(/Confirmación: POD-/)).toBeVisible();
    await expect(tracking.getByRole('listitem').filter({ hasText: 'Entregado' })).toHaveCount(1);
    // Ya no se consulta al servicio logístico, así que tampoco se ofrece actualizar.
    await expect(tracking.getByText('El seguimiento finalizó: ya no se consulta al servicio logístico.')).toBeVisible();
    await expect(tracking.getByRole('button', { name: 'Actualizar seguimiento' })).toHaveCount(0);
  });

  await test.step('El pedido entregado aparece en el filtro «Entregados» del vendedor', async () => {
    await page.getByRole('button', { name: 'Cerrar pedidos', exact: true }).click();
    await selectActiveRole(page, 'VENDEDOR');
    await page.getByRole('button', { name: 'Pedidos recibidos', exact: true }).click();
    await expect(seller).toBeVisible();

    // Ionic superpone la etiqueta al control; activarlo por teclado evita ese solapamiento.
    await page.getByRole('button', { name: /^Mostrar,/ }).press('Space');
    await page.getByRole('radio', { name: 'Entregados', exact: true }).click();
    await page.getByRole('button', { name: 'OK', exact: true }).click();
    await expect(page.getByRole('button', { name: new RegExp(`Pedido #${orderId} ·`) })).toBeVisible();
  });
});

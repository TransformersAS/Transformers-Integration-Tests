import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';
import { cu11Order } from '../helpers/cu11-orders';

test.skip(process.env.CU11_ISOLATED !== 'true', 'Requiere scripts/cu11-run.sh y su base desechable');

test('CU-11: comprador consulta y cancela un pedido fixture, sin validar checkout', async ({ page }) => {
  test.setTimeout(90_000);
  const fixture = cu11Order();
  const productName = fixture.productName;
  const orderId = String(fixture.orderId);
  const transaction = `Transacción: ${fixture.transactionId}`;
  const paidTotal = String(fixture.total);
  test.info().annotations.push({type:'fixture',description:'Pedido sembrado en MySQL desechable. No valida compra ni pago desde checkout.'});
  await test.step('Autenticarse con la cuenta exclusiva CU11 y seleccionar COMPRADOR', async () => {
    await loginAsBuyer(page);
  });

  const orders = page.getByRole('dialog', { name: 'Mis pedidos' });
  await test.step('Abrir el pedido fixture y comprobar sus datos históricos', async () => {
    await page.getByRole('button', { name: 'Mis pedidos', exact: true }).click();
    await orders.getByRole('button', { name: `Ver detalle del pedido ${orderId}`, exact: true }).click();
    await expect(orders.getByRole('heading', { name: `Pedido #${orderId}`, exact: true })).toBeVisible();
    await expect(orders.getByText('Confirmado', { exact: true })).toBeVisible();
    const item = orders.getByRole('listitem').filter({ hasText: productName });
    await expect(item).toHaveCount(1);
    await expect(item.getByText(productName, { exact: true })).toBeVisible();
    await expect(item.getByText(/^Cantidad: 2 · Precio unitario:/)).toBeVisible();
    await expect(item.getByText(/^Subtotal:\s*\d/)).toBeVisible();
    await expect(orders.getByText(transaction, { exact: true })).toBeVisible();
    await expect(orders.getByRole('heading', {name:'Dirección de entrega registrada',exact:true})).toBeVisible();
    await expect(orders.getByText('Dirección: Calle CU11 123',{exact:true})).toBeVisible();
    const total = orders.getByText(/^Total:.*Envío: STANDARD$/);
    await expect(total).toBeVisible();
    expect((await total.innerText()).split('·')[0].replace(/\D/g, ''))
      .toBe(`${paidTotal}00`);
  });

  await test.step('Cancelar directamente con motivo Otro y comprobar el reembolso', async () => {
    await orders.getByRole('button', { name: 'Cancelar pedido', exact: true }).click();
    const confirm = orders.getByRole('button', { name: 'Confirmar cancelación', exact: true });
    await expect(confirm).toBeDisabled();
    await orders.getByRole('combobox', { name: 'Motivo de cancelación' }).selectOption({ label: 'Otro' });
    const explanation = orders.getByRole('textbox', { name: 'Explica el motivo (obligatorio)' });
    await expect(explanation).toBeVisible();
    await expect(confirm).toBeDisabled();
    await explanation.fill('   ');
    await expect(confirm).toBeDisabled();
    await explanation.fill('Compré la talla equivocada; cancelación de prueba CU-11.');
    await expect(confirm).toBeEnabled();
    await expect(orders.getByText(`¿Confirmas la cancelación definitiva del pedido #${orderId}?`, { exact: true }))
      .toBeVisible();
    // Solo observar la respuesta de la petición que hace la UI; no sustituirla ni cancelar por API.
    const [cancelled] = await Promise.all([
      page.waitForResponse(r => new URL(r.url()).pathname === `/api/orders/${orderId}/cancellation`
        && r.request().method() === 'POST'),
      confirm.click(),
    ]);
    expect(cancelled.status()).toBe(200);
    expect(await cancelled.json()).toMatchObject({
      orderId: Number(orderId), status: 'CANCELLED', paymentStatus: 'REFUNDED', refund: { status: 'COMPLETED' },
    });
    await expect(orders.getByText('Cancelado', { exact: true })).toBeVisible();
    await expect(orders.getByRole('status').filter({hasText:'Pedido cancelado. Reembolso completado'})).toBeVisible();
    await expect(orders.getByText('Resultado simulado',{exact:true})).toBeVisible();
    await expect(orders.getByText('Este resultado pertenece al simulador y no acredita una devolución de dinero real.',{exact:true})).toBeVisible();
    await expect(orders.getByRole('button', { name: 'Cancelar pedido', exact: true })).toHaveCount(0);
    await expect(confirm).toHaveCount(0);
  });

  await test.step('Recargar y verificar la cancelación persistida', async () => {
    await page.reload();
    await page.getByRole('button', { name: 'Mis pedidos', exact: true }).click();
    const [detail] = await Promise.all([
      page.waitForResponse(r => new URL(r.url()).pathname === `/api/orders/${orderId}`
        && r.request().method() === 'GET'),
      orders.getByRole('button', { name: `Ver detalle del pedido ${orderId}`, exact: true }).click(),
    ]);
    expect(detail.status()).toBe(200);
    expect(await detail.json()).toMatchObject({ id: Number(orderId), status: 'CANCELLED', paymentStatus:'REFUNDED', refunds:[{status:'SIMULATED'}] });
    await expect(orders.getByText('Cancelado', { exact: true })).toBeVisible();
    await expect(orders.getByRole('button', { name: 'Cancelar pedido', exact: true })).toHaveCount(0);
  });
});

test('CU-11: cancelación de fixture MP permanece cancelada con devolución no completada',async({page})=>{
  const order=cu11Order({mp:true});await loginAsBuyer(page);
  await page.getByRole('button',{name:'Mis pedidos',exact:true}).click();
  const panel=page.getByRole('dialog',{name:'Mis pedidos',exact:true});
  await panel.getByRole('button',{name:`Ver detalle del pedido ${order.orderId}`,exact:true}).click();
  await panel.getByRole('button',{name:'Cancelar pedido',exact:true}).click();
  await expect(panel.getByRole('note',{name:'Consecuencias de cancelar'})).toContainText('La devolución del dinero es una operación separada');
  await panel.getByRole('combobox',{name:'Motivo de cancelación'}).selectOption('CHANGED_MIND');
  const response=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/orders/${order.orderId}/cancellation` && r.request().method()==='POST');
  await panel.getByRole('button',{name:'Confirmar cancelación',exact:true}).click();
  const cancelled=await response;expect(cancelled.status()).toBe(200);
  expect(await cancelled.json()).toMatchObject({status:'CANCELLED',paymentStatus:'REFUND_PENDING',refund:{status:'FAILED'}});
  await expect(panel.getByText('Cancelado',{exact:true})).toBeVisible();
  await expect(panel.getByText('No se pudo completar',{exact:true})).toBeVisible();
  await expect(panel.getByText('Resultado simulado',{exact:true})).toHaveCount(0);
  await expect(panel.getByRole('button',{name:'Cancelar pedido',exact:true})).toHaveCount(0);
  await page.reload();await page.getByRole('button',{name:'Mis pedidos',exact:true}).click();
  await panel.getByRole('button',{name:`Ver detalle del pedido ${order.orderId}`,exact:true}).click();
  await expect(panel.getByText('No se pudo completar',{exact:true})).toBeVisible();
});

test('CU-11: pedido entregado no ofrece cancelación ni cambia por recarga',async({page})=>{
  const order=cu11Order({status:'DELIVERED'});await loginAsBuyer(page);
  await page.getByRole('button',{name:'Mis pedidos',exact:true}).click();const panel=page.getByRole('dialog',{name:'Mis pedidos',exact:true});
  await panel.getByRole('button',{name:`Ver detalle del pedido ${order.orderId}`,exact:true}).click();
  await expect(panel.getByText('Entregado',{exact:true})).toBeVisible();
  await expect(panel.getByRole('button',{name:'Cancelar pedido',exact:true})).toHaveCount(0);
  await expect(panel.getByRole('status').filter({hasText:'El pedido fue entregado y ya no puede cancelarse'})).toBeVisible();
  await page.reload();await page.getByRole('button',{name:'Mis pedidos',exact:true}).click();
  await panel.getByRole('button',{name:`Ver detalle del pedido ${order.orderId}`,exact:true}).click();
  await expect(panel.getByText('Entregado',{exact:true})).toBeVisible();
  await expect(panel.getByRole('button',{name:'Cancelar pedido',exact:true})).toHaveCount(0);
});

test('CU-11: filtra pedidos propios por fecha y estado y consulta comprobante PDF real',async({page})=>{
  const order=cu11Order({createdAt:'2025-03-10T12:00:00'});
  cu11Order({status:'DELIVERED',createdAt:'2025-03-11T12:00:00'});
  await loginAsBuyer(page);await page.getByRole('button',{name:'Mis pedidos',exact:true}).click();
  const panel=page.getByRole('dialog',{name:'Mis pedidos',exact:true});
  await panel.getByLabel('Fecha inicial',{exact:true}).fill('2025-03-10');await panel.getByLabel('Fecha final',{exact:true}).fill('2025-03-10');
  await panel.getByRole('combobox',{name:'Estado del pedido'}).selectOption('CONFIRMED');
  const filtered=page.waitForResponse(r=>{const u=new URL(r.url());return u.pathname==='/api/orders' && u.searchParams.get('from')==='2025-03-10' && u.searchParams.get('status')==='CONFIRMED';});
  await panel.getByRole('button',{name:'Aplicar filtros',exact:true}).click();
  expect((await filtered).status()).toBe(200);
  await expect(panel.getByRole('button',{name:/^Ver detalle del pedido/})).toHaveCount(1);
  await panel.getByRole('button',{name:`Ver detalle del pedido ${order.orderId}`,exact:true}).click();
  const pdfResponse=page.waitForResponse(r=>new URL(r.url()).pathname===`/api/orders/${order.orderId}/receipt`);
  await panel.getByRole('button',{name:'Ver comprobante',exact:true}).click();const pdf=await pdfResponse;
  expect(pdf.status()).toBe(200);expect(pdf.headers()['content-type']).toContain('application/pdf');
  expect((await pdf.body()).subarray(0,5).toString()).toBe('%PDF-');
  await expect(panel.getByTitle('PDF del comprobante de compra')).toBeVisible();
  await panel.getByRole('button',{name:'Cerrar comprobante',exact:true}).click();
  const download=page.waitForEvent('download');await panel.getByRole('button',{name:'Descargar comprobante',exact:true}).click();
  expect((await download).suggestedFilename()).toContain(`comprobante-pedido-${order.orderId}`);
});

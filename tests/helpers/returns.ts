import { expect, type Page } from '@playwright/test';
import { selectActiveRole } from './roles';

/**
 * Devolución preparada con la API de CU-19, que es la dueña de solicitarla, revisarla, aprobarla y ofrecer los métodos
 * de retorno. Al elegir el método, CU-19 crea el retorno en el servicio logístico y registra su seguimiento: eso es lo
 * que CU-25 puede seguir, y lo que la prueba comprueba después en la interfaz.
 *
 * Se prepara por la API, y no por la interfaz de devoluciones, porque esa interfaz pertenece a CU-19 y sus pruebas; aquí
 * solo hace falta llegar a un retorno en curso. Las llamadas van con la sesión y el rol activo de la propia página, así
 * que el rol se alterna con los mismos controles que usaría una persona.
 */
export async function returnInPickup(page: Page, orderId: string): Promise<string> {
  // Devolver es cosa del comprador: el rol activo debe ser el suyo aunque se venga de despachar como vendedor.
  await selectActiveRole(page, 'COMPRADOR');
  const orderItemId = await eligibleLine(page, orderId);
  const requested = await write(page, 'POST', '/api/return-requests', {
    orderId: Number(orderId), orderItemId, reason: 'DEFECTIVE', description: 'Llegó con una costura abierta',
  });
  const returnId = String(requested.id);

  await selectActiveRole(page, 'VENDEDOR');
  await write(page, 'POST', `/api/seller/return-requests/${returnId}/review`, {});
  const approved = await write(page, 'POST', `/api/seller/return-requests/${returnId}/approve`,
    { note: 'Aprobada para la prueba E2E' });
  expect(approved.status, 'El vendedor debe dejar la devolución aprobada').toBe('APPROVED');

  await selectActiveRole(page, 'COMPRADOR');
  const methods = await read(page, `/api/return-requests/${returnId}/return-methods`);
  expect(methods.map((m: { code: string }) => m.code), 'Logística debe ofrecer la recogida').toContain('PICKUP');
  const chosen = await write(page, 'POST', `/api/return-requests/${returnId}/return-method`, { method: 'PICKUP' });
  expect(chosen.returnMethodCode).toBe('PICKUP');
  return returnId;
}

/** El estado de la devolución según CU-19, para comprobar lo que el seguimiento provoca en ella. */
export async function returnRequestStatus(page: Page, returnId: string): Promise<string> {
  return (await read(page, `/api/return-requests/${returnId}`)).status;
}

async function eligibleLine(page: Page, orderId: string): Promise<number> {
  const orders = await read(page, '/api/return-requests/eligible-orders');
  const order = orders.find((o: { orderId: number }) => String(o.orderId) === orderId);
  const line = order?.lines?.find((l: { eligible: boolean }) => l.eligible);
  expect(line, `El pedido ${orderId} debe tener una línea devolvible`).toBeTruthy();
  return line.orderItemId;
}

async function read(page: Page, path: string) {
  const response = await page.request.get(path);
  expect(response.ok(), `GET ${path} respondió ${response.status()}: ${await response.text()}`).toBeTruthy();
  return response.json();
}

async function write(page: Page, method: 'POST' | 'PUT', path: string, body: unknown) {
  // El token CSRF cambia con la sesión y con el rol activo: se pide uno para cada escritura.
  const csrf = await read(page, '/api/auth/csrf');
  const response = await page.request.fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
    data: JSON.stringify(body),
  });
  expect(response.ok(), `${method} ${path} respondió ${response.status()}: ${await response.text()}`).toBeTruthy();
  return response.json();
}

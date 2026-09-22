import { createHmac } from 'node:crypto';
import { expect, type Page } from '@playwright/test';

/**
 * Hace de servicio logístico externo en los recorridos E2E: envía al webhook del backend las novedades firmadas con
 * HMAC-SHA256 sobre el cuerpo exacto, como el proveedor real. El webhook no usa sesión ni CSRF, solo la firma.
 *
 * Las novedades no se pueden provocar desde la interfaz: el caso de uso consiste precisamente en que las informa el
 * proveedor y el marketplace las procesa. Se envían por la misma ruta `/api` que usa la aplicación.
 */
const secret = process.env.LOGISTICS_WEBHOOK_SECRET ?? '';

export type ShipmentEvent =
  | 'PICKED_UP' | 'IN_TRANSIT' | 'DELIVERY_EXCEPTION' | 'DELIVERY_ATTEMPT_FAILED'
  | 'NEXT_ATTEMPT_SCHEDULED' | 'DELIVERED' | 'RETURNED_TO_SELLER';

export type ReturnEvent =
  | 'PICKUP_SCHEDULED' | 'PICKED_UP' | 'IN_TRANSIT' | 'INCIDENT' | 'PICKUP_FAILED' | 'DELIVERED_TO_SELLER';

/** Momento del último evento: la línea de tiempo se ordena por él, así que cada uno va después del anterior. */
let lastEvent = 0;

export function requireWebhookSecret() {
  expect(secret, 'Define LOGISTICS_WEBHOOK_SECRET con el mismo valor con el que arrancó el backend')
    .not.toBe('');
}

/** Novedad de un envío. El envío del pedido N es SIM-order-N y su guía TRK-N en el proveedor simulado. */
export async function reportShipment(page: Page, orderId: string, type: ShipmentEvent, eventId = newEventId()) {
  return send(page, '/api/logistics/webhooks/shipments', {
    eventId, shipmentId: `SIM-order-${orderId}`, trackingCode: `TRK-${orderId}`, type,
  });
}

/** Novedad de un retorno. El retorno N es SIM-return-N y su guía TRK-RN. */
export async function reportReturn(page: Page, returnId: string, type: ReturnEvent, eventId = newEventId()) {
  return send(page, '/api/logistics/webhooks/returns', {
    eventId, returnId: `SIM-return-${returnId}`, trackingCode: `TRK-R${returnId}`, type,
  });
}

export function newEventId() {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

async function send(page: Page, path: string, event: Record<string, string>) {
  const withEvidence = event.type.startsWith('DELIVERED')
    ? { ...event, evidence: { type: 'SIGNATURE', reference: `POD-${event.eventId}` } }
    : event;
  const body = JSON.stringify({ ...withEvidence, occurredAt: nextOccurredAt() });
  const response = await page.request.post(path, {
    headers: {
      'Content-Type': 'application/json',
      'X-Logistics-Signature': `sha256=${createHmac('sha256', secret).update(body).digest('hex')}`,
    },
    data: body,
  });
  expect(response.ok(), `El webhook rechazó ${event.type}: HTTP ${response.status()} ${await response.text()}`)
    .toBeTruthy();
  return (await response.json()).result as string;
}

function nextOccurredAt() {
  lastEvent = Math.max(Date.now(), lastEvent + 10);
  return new Date(lastEvent).toISOString();
}

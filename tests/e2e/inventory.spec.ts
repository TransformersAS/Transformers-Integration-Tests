import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';
import { selectActiveRole } from '../helpers/roles';
import { DEMO_PRODUCT } from '../helpers/purchase';
import { buildXlsx, STOCK_HEADERS, XLSX_MIME } from '../helpers/xlsx';

/**
 * CU-15 de punta a punta, desde el panel «Inventario» del vendedor: existencias, entradas, ajustes, el nivel mínimo
 * con su alerta de stock bajo, y las dos cargas masivas con Excel. Usa el único producto que aprovisiona el perfil
 * local («Camiseta demo local») con mutaciones aditivas o revertidas al final de cada prueba, porque otras pruebas
 * de este repositorio lo compran repetidamente.
 */

async function openInventory(page: import('@playwright/test').Page) {
  await selectActiveRole(page, 'VENDEDOR');
  await page.getByRole('button', { name: 'Inventario', exact: true }).click();
  await expect(page.locator('ion-title').filter({ hasText: 'Inventario' })).toBeVisible();
}

function demoRow(page: import('@playwright/test').Page) {
  // Con "Stock bajo" (dejado por otra prueba de este mismo archivo), el nombre accesible del heading deja de ser
  // exactamente DEMO_PRODUCT: se filtra por contenido, no por nombre exacto.
  return page.getByRole('listitem')
    .filter({ has: page.getByRole('heading', { level: 2 }).filter({ hasText: DEMO_PRODUCT }) });
}

async function currentStock(page: import('@playwright/test').Page): Promise<number> {
  const text = await demoRow(page).getByText(/^Físico \d+/).innerText();
  return Number(text.match(/Físico (\d+)/)?.[1]);
}

test('el vendedor registra una entrada y un ajuste, y quedan en el historial', async ({ page }) => {
  test.setTimeout(90_000);
  await loginAsBuyer(page);
  await openInventory(page);
  const row = demoRow(page);
  const before = await currentStock(page);

  await test.step('Entrada: suma unidades', async () => {
    await row.getByRole('button', { name: 'Registrar entrada', exact: true }).click();
    await page.getByRole('spinbutton', { name: 'Unidades que llegaron' }).fill('3');
    await page.getByRole('textbox', { name: 'Motivo (opcional)' }).fill('Reposición E2E');
    const [response] = await Promise.all([
      page.waitForResponse(r => /\/entries$/.test(new URL(r.url()).pathname)),
      page.getByRole('button', { name: 'Guardar', exact: true }).click(),
    ]);
    expect(response.ok()).toBeTruthy();
    await expect(page.getByText('Entrada registrada.', { exact: true })).toBeVisible();
    await expect(row.getByText(`Físico ${before + 3}`)).toBeVisible();
  });

  const afterEntry = before + 3;
  const afterAdjustment = afterEntry + 2; // el ajuste solo sube, para no dejar el producto con menos stock del que había

  await test.step('Ajuste: fija el conteo real y exige un motivo', async () => {
    await row.getByRole('button', { name: 'Ajustar conteo', exact: true }).click();
    await page.getByRole('spinbutton', { name: 'Conteo real (unidades)' }).fill(String(afterAdjustment));
    // El backend exige el motivo en un ajuste; la pantalla no valida esto por su cuenta, así que sin motivo se
    // envía igual y el rechazo viene del backend.
    const [rejected] = await Promise.all([
      page.waitForResponse(r => /\/adjustments$/.test(new URL(r.url()).pathname)),
      page.getByRole('button', { name: 'Guardar', exact: true }).click(),
    ]);
    expect(rejected.ok()).toBeFalsy();
    await expect(page.getByRole('alert').first()).toBeVisible();
    await expect(row.getByText(`Físico ${afterEntry}`)).toBeVisible(); // no cambió: el ajuste sin motivo no se aplicó

    await page.getByRole('textbox', { name: 'Motivo (obligatorio)' }).fill('Conteo físico E2E');
    const [response] = await Promise.all([
      page.waitForResponse(r => /\/adjustments$/.test(new URL(r.url()).pathname)),
      page.getByRole('button', { name: 'Guardar', exact: true }).click(),
    ]);
    expect(response.ok()).toBeTruthy();
    await expect(page.getByText('Ajuste registrado.', { exact: true })).toBeVisible();
    await expect(row.getByText(`Físico ${afterAdjustment}`)).toBeVisible();
  });

  await test.step('El historial muestra los dos movimientos, el más nuevo primero', async () => {
    await row.getByRole('button', { name: 'Historial', exact: true }).click();
    const entries = page.getByRole('listitem').filter({ hasText: /Ajuste:|Entrada:/ });
    await expect(entries.first()).toContainText(`Ajuste: +2 → ${afterAdjustment} unidades`);
    await expect(entries.first()).toContainText('Conteo físico E2E');
    await expect(entries.nth(1)).toContainText(`Entrada: +3 → ${afterEntry} unidades`);
    await expect(entries.nth(1)).toContainText('Reposición E2E');
    await page.getByRole('button', { name: 'Cerrar historial', exact: true }).click();
  });
});

test('el mínimo enciende la alerta de stock bajo y se puede apagar de nuevo', async ({ page }) => {
  test.setTimeout(60_000);
  await loginAsBuyer(page);
  await openInventory(page);
  const row = demoRow(page);
  const stock = await currentStock(page);

  await test.step('Un mínimo por encima del stock enciende la alerta', async () => {
    await row.getByRole('button', { name: 'Mínimo', exact: true }).click();
    await page.getByRole('spinbutton', { name: 'Nivel mínimo (unidades)' }).fill(String(stock + 1000));
    const [response] = await Promise.all([
      page.waitForResponse(r => /\/minimum$/.test(new URL(r.url()).pathname)),
      page.getByRole('button', { name: 'Guardar', exact: true }).click(),
    ]);
    expect(response.ok()).toBeTruthy();
    await expect(row).toContainText('Stock bajo');
    await expect(page.getByText(/producto\(s\) están en su nivel mínimo o por debajo/)).toBeVisible();
  });

  // Se restaura a "sin aviso": el mínimo queda compartido con las demás pruebas de este mismo producto.
  await test.step('Volver el mínimo a 0 apaga la alerta', async () => {
    await row.getByRole('button', { name: 'Mínimo', exact: true }).click();
    await page.getByRole('spinbutton', { name: 'Nivel mínimo (unidades)' }).fill('0');
    await Promise.all([
      page.waitForResponse(r => /\/minimum$/.test(new URL(r.url()).pathname)),
      page.getByRole('button', { name: 'Guardar', exact: true }).click(),
    ]);
    await expect(row).not.toContainText('Stock bajo');
  });
});

test('las plantillas de Excel se descargan y una carga con un error rechaza todo el archivo', async ({ page }) => {
  test.setTimeout(60_000);
  await loginAsBuyer(page);
  await openInventory(page);

  await test.step('Las dos plantillas se descargan con su nombre de archivo', async () => {
    const [products] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Plantilla de productos nuevos', exact: true }).click(),
    ]);
    expect(products.suggestedFilename()).toBe('plantilla-productos.xlsx');

    const [stock] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Plantilla de inventario', exact: true }).click(),
    ]);
    expect(stock.suggestedFilename()).toBe('plantilla-inventario.xlsx');
  });

  await test.step('Una fila con un producto inexistente rechaza el archivo completo', async () => {
    const file = buildXlsx(STOCK_HEADERS, [['999999', 'No existe', '0', '0', 'ENTRADA', '4', '']]);
    // El botón dispara un <input type="file"> oculto; se completa directamente, como si se hubiera elegido el archivo.
    const inputs = page.locator('input[type="file"]');
    await inputs.nth(1).setInputFiles({ name: 'inventario.xlsx', mimeType: XLSX_MIME, buffer: file });
    // El mensaje principal y la lista de errores por fila son dos elementos con role="alert" distintos.
    await expect(page.getByRole('alert').first()).toContainText('no se guardó nada');
    await expect(page.getByRole('listitem').filter({ hasText: 'Producto 999999' })).toBeVisible();
  });
});

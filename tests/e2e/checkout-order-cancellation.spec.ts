import { expect, test } from '@playwright/test';
import { loginAsBuyer } from '../helpers/account';

test('comprador compra una camiseta y solicita la cancelación de su pedido', async ({ page }) => {
  test.setTimeout(90_000);

  const productName = 'Camiseta demo local';

  await test.step(
    'Autenticarse con la cuenta local y seleccionar COMPRADOR',
    async () => {
      await loginAsBuyer(page);
    }
  );

  const cart = page.getByRole('complementary', {
    name: 'Carrito de compras'
  });

  await test.step(
    'Agregar una unidad del producto provisionado al carrito',
    async () => {

      const [initialCart] = await Promise.all([
        page.waitForResponse(
          r =>
            new URL(r.url()).pathname === '/api/cart'
            && r.request().method() === 'GET'
        ),

        page
          .getByRole('button', {
            name: 'Abrir carrito',
            exact: true
          })
          .click(),
      ]);

      expect(
        initialCart.ok(),
        'La consulta del carrito debe responder correctamente'
      ).toBeTruthy();

      await initialCart.finished();

      // No consumir ni borrar un carrito previo de la cuenta compartida.
      await expect(
        cart.getByRole('button', {
          name: 'Eliminar',
          exact: true
        }),
        'La cuenta E2E necesita un carrito vacío; no se eliminan datos previos'
      ).toHaveCount(0);

      await expect(
        cart.getByRole('heading', {
          name: /vacío/i
        })
      ).toBeVisible();

      await cart
        .getByRole('button', {
          name: 'Cerrar carrito'
        })
        .click();


      // =========================================================
      // PRODUCTO DEL CATÁLOGO
      // =========================================================
      //
      // Antes se usaba getByRole('article'), pero ahora existen
      // dos artículos con "Camiseta demo local":
      //
      // - recommendation-card
      // - product-card
      //
      // Por eso buscamos específicamente dentro del catálogo.

      const product = page
        .locator('#catalogo')
        .locator('article.product-card')
        .filter({
          hasText: productName
        });

      await expect(
        product,
        'El perfil local debe provisionar Camiseta demo local'
      ).toBeVisible();


      const [added] = await Promise.all([
        page.waitForResponse(
          r =>
            new URL(r.url()).pathname === '/api/cart/items'
            && r.request().method() === 'POST'
        ),

        product
          .getByRole('button', {
            name: 'Agregar',
            exact: true
          })
          .click(),
      ]);

      expect(
        added.ok(),
        'La UI debe poder agregar el producto'
      ).toBeTruthy();


      await page
        .getByRole('button', {
          name: 'Abrir carrito',
          exact: true
        })
        .click();


      await expect(
        cart.getByRole('heading', {
          name: productName,
          exact: true
        })
      ).toBeVisible();


      await expect(
        cart.getByText('1', {
          exact: true
        })
      ).toBeVisible();


      await expect(
        cart.getByRole('button', {
          name: 'Eliminar',
          exact: true
        })
      ).toHaveCount(1);


      await cart
        .getByRole('button', {
          name: 'Continuar compra',
          exact: true
        })
        .click();
    }
  );


  let orderId: string;
  let transaction: string;
  let paidTotal: string;


  const checkout = page.getByRole('complementary', {
    name: 'Finalizar compra'
  });


  await test.step(
    'Completar dirección, envío y pago desde los controles visibles',
    async () => {

      await checkout
        .getByPlaceholder(
          'Escribe tu dirección de entrega'
        )
        .fill(
          'Calle 123 # 45-67, pruebas E2E'
        );


      await checkout
        .getByRole('radio', {
          name: /Estándar/
        })
        .check();


      await checkout
        .getByRole('button', {
          name: 'Calcular total',
          exact: true
        })
        .click();


      await expect(
        checkout.getByRole('heading', {
          name: 'Resumen de compra'
        })
      ).toBeVisible();


      await checkout
        .getByRole('combobox')
        .selectOption({
          label: 'Tarjeta'
        });


      await checkout
        .getByRole('button', {
          name: 'Confirmar compra',
          exact: true
        })
        .click();


      await expect(
        checkout.getByRole('heading', {
          name: /¡Compra confirmada!/
        })
      ).toBeVisible();


      await expect(
        checkout.getByText(
          'Tu pedido fue creado correctamente.',
          {
            exact: true
          }
        )
      ).toBeVisible();


      const orderNumber =
        checkout.getByText(/^\s*#\d+\s*$/);


      await expect(
        orderNumber
      ).toBeVisible();


      orderId =
        (
          await orderNumber.innerText()
        )
          .trim()
          .slice(1);


      transaction =
        (
          await checkout
            .getByText(/^\s*Transacción:/)
            .innerText()
        ).trim();


      // Los importes usan moneda sin decimales en checkout
      // y dos decimales en pedidos.
      paidTotal =
        (
          await checkout
            .getByText(
              'Total pagado',
              {
                exact: true
              }
            )
            .locator('..')
            .locator('strong')
            .innerText()
        )
          .replace(/\D/g, '');


      expect(
        Number(paidTotal)
      ).toBeGreaterThan(0);


      await checkout
        .getByRole('button', {
          name: 'Cerrar checkout'
        })
        .click();
    }
  );


  const orders = page.getByRole(
    'dialog',
    {
      name: 'Mis pedidos'
    }
  );


  await test.step(
    'Abrir el pedido recién creado y comprobar sus datos',
    async () => {

      await page
        .getByRole('button', {
          name: 'Mis pedidos',
          exact: true
        })
        .click();


      await orders
        .getByRole('button', {
          name:
            `Ver detalle del pedido ${orderId}`,
          exact: true
        })
        .click();


      await expect(
        orders.getByRole('heading', {
          name:
            `Pedido #${orderId}`,
          exact: true
        })
      ).toBeVisible();


      await expect(
        orders.getByText(
          'Confirmado',
          {
            exact: true
          }
        )
      ).toBeVisible();


      const item =
        orders
          .getByRole('listitem')
          .filter({
            hasText: productName
          });


      await expect(
        item
      ).toHaveCount(1);


      await expect(
        item.getByText(
          productName,
          {
            exact: true
          }
        )
      ).toBeVisible();


      await expect(
        item.getByText(
          /^Cantidad: 1 · Precio unitario:/
        )
      ).toBeVisible();


      await expect(
        item.getByText(
          /^Subtotal:\s*\d/
        )
      ).toBeVisible();


      await expect(
        orders.getByText(
          transaction,
          {
            exact: true
          }
        )
      ).toBeVisible();


      await expect(
        orders.getByText(
          /^Dirección de envío registrada #\d+/
        )
      ).toBeVisible();


      const total =
        orders.getByText(
          /^Total:.*Envío: STANDARD$/
        );


      await expect(
        total
      ).toBeVisible();


      expect(
        (
          await total.innerText()
        )
          .split('·')[0]
          .replace(/\D/g, '')
      ).toBe(
        `${paidTotal}00`
      );
    }
  );


  await test.step(
    'Solicitar y confirmar cancelación sin permitir una segunda solicitud',
    async () => {

      await orders
        .getByRole('button', {
          name: 'Solicitar cancelación',
          exact: true
        })
        .click();


      await expect(
        orders.getByText(
          `¿Solicitar la cancelación del pedido #${orderId}?`,
          {
            exact: false
          }
        )
      ).toBeVisible();


      await orders
        .getByRole('button', {
          name: 'Confirmar solicitud',
          exact: true
        })
        .click();


      await expect(
        orders.getByText(
          'Cancelación solicitada',
          {
            exact: true
          }
        )
      ).toBeVisible();


      await expect(
        orders.getByText(
          'La cancelación de este pedido ya fue solicitada.',
          {
            exact: true
          }
        )
      ).toBeVisible();


      await expect(
        orders.getByRole('button', {
          name: 'Solicitar cancelación',
          exact: true
        })
      ).toHaveCount(0);


      await expect(
        orders.getByRole('button', {
          name: 'Confirmar solicitud',
          exact: true
        })
      ).toHaveCount(0);


      // Volver a consultar mediante la UI
      // para verificar persistencia del estado.
      await orders
        .getByRole('button', {
          name: 'Volver a mis pedidos',
          exact: true
        })
        .click();


      await orders
        .getByRole('button', {
          name:
            `Ver detalle del pedido ${orderId}`,
          exact: true
        })
        .click();


      await expect(
        orders.getByText(
          'Cancelación solicitada',
          {
            exact: true
          }
        )
      ).toBeVisible();


      await expect(
        orders.getByRole('button', {
          name: 'Solicitar cancelación',
          exact: true
        })
      ).toHaveCount(0);
    }
  );
});

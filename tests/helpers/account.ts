import { expect, type Page } from '@playwright/test';

// Datos exclusivos del perfil local de pruebas;
// se pueden sobrescribir por variables de entorno.
const email =
  process.env.E2E_EMAIL
  ?? 'demo@marketplace.local';

const password =
  process.env.E2E_PASSWORD
  ?? 'MarketplaceDemo123!';


export async function loginAsBuyer(page: Page) {

  await page.goto('/');

  await page
    .getByRole('button', {
      name: 'Ver cuenta',
      exact: true
    })
    .click();


  const login =
    page.getByRole('dialog', {
      name: 'Acceso a tu cuenta',
      exact: true
    });


  await login
    .getByRole('textbox', {
      name: /Contrase/i
    })
    .fill(password);


  await login
    .getByRole('textbox', {
      name: 'Correo',
      exact: true
    })
    .fill(email);


  // =========================================================
  // INTENTO DE LOGIN
  // =========================================================

  const intentarLogin = async () => {

    const [response] =
      await Promise.all([

        page.waitForResponse(r =>
          new URL(r.url()).pathname === '/api/auth/login'
          && r.request().method() === 'POST'
        ),

        login
          .getByRole('button', {
            name: /Iniciar sesi/i
          })
          .click()
      ]);

    return response;
  };


  let response =
    await intentarLogin();


  // =========================================================
  // REINTENTO CSRF
  // =========================================================
  //
  // El interceptor invalida el CSRF cuando recibe 403.
  // Por lo tanto, si el primer login falla por CSRF,
  // hacemos UN solo reintento.
  //
  // En el segundo intento Angular solicitará un nuevo
  // token CSRF automáticamente.
  //
  // No reintentamos 401 porque eso sí indicaría
  // credenciales incorrectas.

  if (response.status() === 403) {

    console.log(
      'Login recibió 403. Reintentando una vez con CSRF renovado...'
    );

    response =
      await intentarLogin();
  }


  expect(
    response.ok(),
    `Login local rechazado (HTTP ${response.status()}). `
    + 'Verificar perfil local y E2E_EMAIL/E2E_PASSWORD.'
  ).toBeTruthy();


  // =========================================================
  // CUENTA AUTENTICADA
  // =========================================================

  const account =
    page.getByRole('dialog', {
      name: 'Seguridad de tu cuenta',
      exact: true
    });


  await expect(
    account
  ).toBeVisible();


  await expect(
    account.getByRole('button', {
      name: /Cerrar sesi/i
    })
  ).toBeVisible();


  await expect(
    account.getByText(
      `Hola, ${email}`,
      {
        exact: true
      }
    )
  ).toBeVisible();


  // =========================================================
  // SELECCIONAR ROL COMPRADOR
  // =========================================================

  await selectBuyerRole(page);


  await account
    .getByRole('button', {
      name: 'Cerrar panel',
      exact: true
    })
    .click();


  // Recargar el catálogo que pudo consultarse
  // antes de iniciar sesión.
  await page.reload();


  await expect(
    page.getByRole('button', {
      name: 'Mis pedidos',
      exact: true
    })
  ).toBeVisible();
}


export async function selectBuyerRole(page: Page) {

  const account =
    page.getByRole('dialog', {
      name: 'Seguridad de tu cuenta',
      exact: true
    });


  const activeRole =
    account.getByText(/^Rol activo:/);


  await expect(
    activeRole
  ).toBeVisible();


  if (
    !(await activeRole.innerText())
      .includes('COMPRADOR')
  ) {

    // Ionic puede superponer la etiqueta al botón.
    // Activarlo por teclado evita ese solapamiento.
    await account
      .getByRole('button', {
        name: /^Cambiar rol activo,/
      })
      .press('Space');


    await page
      .getByRole('radio', {
        name: 'COMPRADOR',
        exact: true
      })
      .click();


    await page
      .getByRole('button', {
        name: 'OK',
        exact: true
      })
      .click();


    await expect(
      page.getByRole('button', {
        name: 'OK',
        exact: true
      })
    ).toBeHidden();
  }


  await expect(
    activeRole
  ).toHaveText(
    /Rol activo:\s*COMPRADOR/
  );
}
import { execFileSync } from 'node:child_process';
import { test, expect } from '@playwright/test';
import { openBuyerRegistration, fillBuyer, uniqueBuyerEmail, verificationCode } from '../helpers/buyer-registration';
test.skip(process.env.CU07_ISOLATED !== 'true', 'Requires the disposable external CU07 environment');
test.beforeAll(() => {
  expect(['127.0.0.1','localhost']).toContain(new URL(process.env.FRONTEND_BASE_URL!).hostname);
  expect(process.env.CU07_MAIL_URL).toBeTruthy();
});

test('CU07 real: browser registers, receives SMTP code, verifies and logs in as buyer', async ({page,request})=>{
  const email=uniqueBuyerEmail();
  await openBuyerRegistration(page);
  await fillBuyer(page, email);
  await page.locator('ion-checkbox[name="terms"]').click();await page.locator('ion-checkbox[name="privacy"]').click();
  await page.getByRole('button',{name:'Crear cuenta de comprador',exact:true}).click();
  await expect(page.locator('app-registro-comprador [role="status"]')).toContainText('Se envió una verificación', {timeout:15000});
  expect((await page.request.get('/api/auth/me')).status()).toBe(401);
  const token = await verificationCode(request, email);
  await page.locator('ion-input[name="token"] input').fill(token);
  await page.getByRole('button',{name:'Confirmar correo',exact:true}).click();
  await expect(page.getByRole('dialog',{name:'Acceso a tu cuenta',exact:true})).toBeVisible();
  await page.locator('ion-input[name="correo"] input').fill(email);await page.locator('ion-input[name="clave"] input').fill('Cu07RealPassword123!');
  await page.getByRole('button',{name:'Iniciar sesión',exact:true}).click();
  await expect(page.getByRole('dialog',{name:'Seguridad de tu cuenta',exact:true})).toBeVisible();
  const me=await page.request.get('/api/auth/me');expect(me.status()).toBe(200);
  expect(await me.json()).toMatchObject({email,roles:['COMPRADOR'],activeRole:'COMPRADOR'});
  expect((await page.request.get('/api/orders')).status()).toBe(200);
});

test('CU07 real: an authenticated seller adds buyer without changing identity or active role',async({page})=>{
  const sellerEmail = uniqueBuyerEmail();
  execFileSync('bash', ['scripts/cu07-compose.sh', 'exec', '-T', 'mysql', 'sh', '-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'], {input:
    `INSERT INTO user_accounts(email,password_hash,status,email_verified_at) VALUES ('${sellerEmail}','$2y$12$1b8lQufQ.HVnswuPxAClJ.mZciYOAGr8F2XGKjzndmAJQ7t94AK7m','ACTIVA',CURRENT_TIMESTAMP(6));
     INSERT INTO user_account_roles(account_id,role) SELECT id,'VENDEDOR' FROM user_accounts WHERE email='${sellerEmail}';`});
  await page.goto('/');await page.getByRole('button',{name:'Ver cuenta',exact:true}).click();
  await page.locator('ion-input[name="correo"] input').fill(sellerEmail);
  await page.locator('ion-input[name="clave"] input').fill('Cu08RealTestPassword!');
  await page.getByRole('button',{name:'Iniciar sesión',exact:true}).click();
  await expect(page.getByRole('dialog',{name:'Seguridad de tu cuenta',exact:true})).toBeVisible();
  const before=await (await page.request.get('/api/auth/me')).json();
  await page.getByRole('button',{name:'Cerrar panel',exact:true}).click();
  await page.getByRole('button',{name:'Crear cuenta de comprador / Habilitar COMPRADOR',exact:true}).click();
  await page.locator('app-registro-comprador ion-checkbox').nth(0).click();await page.locator('app-registro-comprador ion-checkbox').nth(1).click();
  await page.getByRole('button',{name:'Habilitar COMPRADOR',exact:true}).click();
  await expect(page.locator('app-registro-comprador')).toContainText('ya tiene COMPRADOR');
  const after=await (await page.request.get('/api/auth/me')).json();
  expect(after).toMatchObject({accountId:before.accountId,email:before.email,activeRole:'VENDEDOR'});
  expect(after.roles.sort()).toEqual(['COMPRADOR','VENDEDOR']);
  await page.getByRole('button',{name:'Volver a mi cuenta',exact:true}).click();
  await page.locator('ion-select').click();
  await page.getByRole('radio',{name:'COMPRADOR',exact:true}).click();await page.getByRole('button',{name:'OK',exact:true}).click();
  await expect.poll(async()=> (await (await page.request.get('/api/auth/me')).json()).activeRole).toBe('COMPRADOR');
  expect((await page.request.get('/api/orders')).status()).toBe(200);
});

test('CU07 validations preserve valid data and explain missing consent', async ({page}) => {
  await openBuyerRegistration(page);
  const submit = page.getByRole('button', {name: 'Crear cuenta de comprador', exact: true});
  await submit.click();
  await expect(page.getByText('Escribe tu nombre completo.', {exact: true})).toBeVisible();
  const email = uniqueBuyerEmail();
  await fillBuyer(page, email);
  const password = page.locator('ion-input[name="password"] input');
  const confirmation = page.locator('ion-input[name="passwordConfirmation"] input');
  await confirmation.fill('DifferentPassword123!');
  await submit.click();
  await expect(page.getByText('Las contraseñas no coinciden.', {exact: true})).toBeVisible();
  await password.fill('short'); await confirmation.fill('short'); await submit.click();
  await expect(page.getByText('La contraseña requiere al menos 12 caracteres.', {exact: true})).toBeVisible();
  await password.fill('Cu07RealPassword123!'); await confirmation.fill('Cu07RealPassword123!'); await submit.click();
  await expect(page.getByText('Acepta los términos y condiciones.', {exact: true})).toBeVisible();
  await expect(page.getByText('Acepta la política de privacidad.', {exact: true})).toBeVisible();
  await expect(page.locator('ion-input[name="email"] input')).toHaveValue(email);
  await expect(page.locator('ion-input[name="fullName"] input')).toHaveValue('Ana Pérez');
});

test('CU07 pending account cannot log in and anonymous enable is denied', async ({page}) => {
  await openBuyerRegistration(page);
  const email = uniqueBuyerEmail(); await fillBuyer(page, email);
  await page.locator('ion-checkbox[name="terms"]').click();
  await page.locator('ion-checkbox[name="privacy"]').click();
  await page.getByRole('button',{name:'Crear cuenta de comprador',exact:true}).click();
  await expect(page.locator('app-registro-comprador [role="status"]')).toContainText('Se envió una verificación');
  await expect(page.locator('app-registro-comprador ion-input[name="password"]')).toHaveCount(0);
  await page.getByRole('button', {name:'Ir al inicio de sesión',exact:true}).click();
  await page.locator('ion-input[name="correo"] input').fill(email);
  await page.locator('ion-input[name="clave"] input').fill('Cu07RealPassword123!');
  const pending = page.waitForResponse(r => new URL(r.url()).pathname === '/api/auth/login' && r.request().method() === 'POST');
  await page.getByRole('button',{name:'Iniciar sesión',exact:true}).click();
  const rejection = await pending;
  expect(rejection.status()).toBe(403);
  expect(await rejection.json()).toMatchObject({code:'EMAIL_NOT_VERIFIED'});
  expect((await page.request.get('/api/auth/me')).status()).toBe(401);
  const csrf = await (await page.request.get('/api/auth/csrf')).json();
  const enable = await page.request.post('/api/buyers/enable', {headers:{[csrf.headerName]:csrf.token},data:{acceptTerms:true,acceptPrivacy:true}});
  expect(enable.status()).toBe(401);
  const data = await enable.text();
  expect(data).not.toContain('passwordHash');
  expect(data).not.toContain('roles');
});

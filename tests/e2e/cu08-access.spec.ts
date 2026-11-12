import { test, expect } from '@playwright/test';
import { account, password, newPassword, openLogin, attempt, login, action, code, accountPanel } from '../helpers/cu08-auth';
test.skip(process.env.CU08_ISOLATED!=='true','Requires the isolated CU08 proxy, database and SMTP harness');
test.beforeAll(()=>{
  expect(['127.0.0.1','localhost']).toContain(new URL(process.env.FRONTEND_BASE_URL!).hostname);
});

test('CU08 rejects incorrect credentials and authenticates the same account correctly',async({page})=>{
  const email=account();await openLogin(page);
  expect((await attempt(page,email,'WrongPassword123!')).status()).toBe(401);
  await expect(page.getByRole('alert')).toHaveText('La sesión no está disponible o las credenciales son incorrectas. Inicia sesión para continuar.');
  expect((await page.request.get('/api/auth/me')).status()).toBe(401);
  expect((await attempt(page,email)).status()).toBe(204);
  await expect(accountPanel(page)).toBeVisible();
  const me=await (await page.request.get('/api/auth/me')).json();
  expect(me).toMatchObject({email,roles:['COMPRADOR'],activeRole:'COMPRADOR'});
});

test('CU08 closes all other browser sessions while preserving the current one',async({page,browser})=>{
  const email=account();await login(page,email);
  const context=await browser.newContext({baseURL:process.env.FRONTEND_BASE_URL});
  try {
    const other=await context.newPage();await login(other,email);
    await page.getByRole('button',{name:'Sesiones activas',exact:true}).click();
    await expect(accountPanel(page).getByRole('listitem')).toHaveCount(2);
    expect((await action(page,'Cerrar las demás sesiones','/api/auth/sessions/revoke-others')).status()).toBe(204);
    await expect(accountPanel(page).getByRole('listitem')).toHaveCount(1);
    expect((await other.request.get('/api/auth/me')).status()).toBe(401);
    expect((await page.request.get('/api/auth/me')).status()).toBe(200);
  } finally {await context.close();}
});

test('CU08 changes password, revokes the other browser and rejects the previous password',async({page,browser})=>{
  const email=account();await login(page,email);
  const context=await browser.newContext({baseURL:process.env.FRONTEND_BASE_URL});
  try {
    const other=await context.newPage();await login(other,email);
    await page.getByRole('button',{name:'Cambiar contraseña',exact:true}).click();
    await page.locator('ion-input[name="actual"] input').fill(password);
    await page.locator('ion-input[name="nueva"] input').fill(newPassword);
    await page.locator('ion-input[name="confirmacion"] input').fill(newPassword);
    expect((await action(page,'Guardar nueva contraseña','/api/auth/password')).status()).toBe(204);
    await expect(page.locator('app-acceso').getByRole('status')).toContainText('Contraseña actualizada');
    expect((await other.request.get('/api/auth/me')).status()).toBe(401);
    expect((await page.request.get('/api/auth/me')).status()).toBe(200);
    expect((await action(page,'Cerrar sesión','/api/auth/logout')).status()).toBe(204);
    expect((await attempt(page,email,password)).status()).toBe(401);
    expect((await attempt(page,email,newPassword)).status()).toBe(204);
  } finally {await context.close();}
});

test('CU08 recovers through real SMTP and invalidates the previous authenticated session',async({page,browser,request})=>{
  const email=account();await login(page,email);
  const context=await browser.newContext({baseURL:process.env.FRONTEND_BASE_URL});
  try {
    const recovery=await context.newPage();await openLogin(recovery);
    await recovery.getByRole('button',{name:'Olvidé mi contraseña',exact:true}).click();
    await recovery.locator('ion-input[name="correoRecuperacion"] input').fill(email);
    expect((await action(recovery,'Solicitar instrucciones','/api/auth/password-recovery/request')).status()).toBe(202);
    await expect(recovery.locator('app-acceso').getByRole('status')).toContainText('Si la cuenta está activa');
    const token=await code(request,email,'Recupera');
    await recovery.getByRole('button',{name:'Ingresar token recibido',exact:true}).click();
    await recovery.locator('ion-input[name="token"] input').fill(token);
    await recovery.locator('ion-input[name="nueva"] input').fill(newPassword);
    await recovery.locator('ion-input[name="confirmacion"] input').fill(newPassword);
    expect((await action(recovery,'Recuperar contraseña','/api/auth/password-recovery/confirm')).status()).toBe(204);
    expect((await page.request.get('/api/auth/me')).status()).toBe(401);
    await expect(recovery.locator('app-acceso').getByRole('status')).toContainText('Contraseña recuperada');
    expect((await attempt(recovery,email,newPassword)).status()).toBe(204);
  } finally {await context.close();}
});

test('CU08 pending email cannot log in, resends and verifies through the real UI',async({page,request})=>{
  const email=account(false);await openLogin(page);
  expect((await attempt(page,email)).status()).toBe(403);
  expect((await page.request.get('/api/auth/me')).status()).toBe(401);
  expect((await action(page,'Reenviar verificación','/api/auth/email-verification/resend')).status()).toBe(202);
  const token=await code(request,email,'Verifica');
  await page.getByRole('button',{name:'Verificar correo',exact:true}).click();
  await page.locator('ion-input[name="verificacion"] input').fill('bad');
  expect((await action(page,'Confirmar correo','/api/auth/email-verification/confirm')).status()).toBe(400);
  await expect(page.getByRole('alert')).toBeVisible();
  await page.locator('ion-input[name="verificacion"] input').fill(token);
  expect((await action(page,'Confirmar correo','/api/auth/email-verification/confirm')).status()).toBe(204);
  await expect(page.locator('app-acceso').getByRole('status')).toContainText('Correo verificado');
  expect((await attempt(page,email)).status()).toBe(204);
});

test('CU08 displays temporary login blocking and Retry-After without authenticating',async({page})=>{
  const email=account();await openLogin(page);
  for(let i=0;i<5;i++) expect((await attempt(page,email,'WrongPassword123!')).status()).toBe(401);
  const blocked=await attempt(page,email,password);expect(blocked.status()).toBe(429);
  expect(Number(blocked.headers()['retry-after'])).toBeGreaterThan(0);
  await expect(page.getByRole('alert')).toContainText('El acceso está limitado temporalmente');
  expect((await page.request.get('/api/auth/me')).status()).toBe(401);
});

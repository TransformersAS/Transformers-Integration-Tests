import { test, expect, Page, APIRequestContext } from '@playwright/test';
import { execFileSync } from 'node:child_process';

const password = 'Cu08RealTestPassword!';
const fixture = (...args: string[]) => execFileSync('python3', ['scripts/cu09-fixture.py', ...args], { encoding: 'utf8' }).trim();
const account = () => fixture();
async function login(page: Page, email: string) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Ver cuenta', exact: true }).click();
  await page.locator('ion-input[name="correo"] input').fill(email);
  await page.locator('ion-input[name="clave"] input').fill(password);
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
  await expect(page.locator('.welcome-strip')).toContainText(email.split('@')[0]);
  await page.getByRole('button', { name: 'Cerrar panel', exact: true }).click();
  await page.getByRole('button', { name: 'Mi perfil y preferencias', exact: true }).click();
  await expect(page.locator('app-perfil-personal')).toContainText('Ana Perfil');
}
async function token(request: APIRequestContext, email: string) {
  let value = '';
  await expect.poll(async () => {
    const response = await request.get(`${process.env.CU09_MAIL_URL}/api/v1/messages`);
    expect(response.status()).toBe(200);
    const list = await response.json();
    for (const message of list.messages || []) {
      if (!JSON.stringify(message.To).includes(email)) continue;
      const detail = await request.get(`${process.env.CU09_MAIL_URL}/api/v1/message/${message.ID}`);
      expect(detail.status()).toBe(200);
      value = (await detail.json()).Text.match(/(?:^|\s)([A-Za-z0-9_-]{43})(?=\s|$)/)?.[1] || '';
      if (value) return true;
    }
    return false;
  }).toBe(true);
  return value;
}

test('RF031–035: edición, validación, preferencias, correo real y cierre', async ({ page, request }) => {
  const email = account(), next = email.replace('@', '-nuevo@');
  await login(page, email);
  const profile = page.locator('app-perfil-personal');
  await expect(profile).toContainText('Las comunicaciones esenciales');
  await profile.getByRole('button', { name: 'Editar perfil y preferencias', exact: true }).click();
  await profile.locator('ion-input[name="fullName"] input').fill('Sofía CU09 Navegador');
  await profile.locator('ion-input[name="birthDate"] input').fill('2999-01-01');
  await profile.getByRole('button', { name: 'Revisar cambios', exact: true }).click();
  await expect(profile).toContainText('La fecha no puede ser futura');
  await expect(profile.locator('ion-input[name="fullName"] input')).toHaveValue('Sofía CU09 Navegador');
  await profile.locator('ion-input[name="birthDate"] input').fill('1999-01-01');
  await profile.locator('ion-input[name="email"] input').fill(next);
  await profile.locator('ion-checkbox[name="promotions"]').click();
  await profile.getByRole('button', { name: 'Revisar cambios', exact: true }).click();
  await profile.locator('ion-input[name="currentPassword"] input').fill('incorrecta');
  await profile.getByRole('button', { name: 'Confirmar y guardar', exact: true }).click();
  await expect(profile).toContainText('La contraseña actual es incorrecta');
  await profile.locator('ion-input[name="currentPassword"] input').fill(password);
  await profile.getByRole('button', { name: 'Confirmar y guardar', exact: true }).click();
  await expect(profile).toContainText('Verificar nuevo correo');
  const persisted = await page.request.get('/api/account/profile');
  expect(persisted.status()).toBe(200);
  expect(await persisted.json()).toMatchObject({ email, pendingEmail: next, promotions: true, marketplaceNews: false, essentialCommunications: true });
  const code = await token(request, next);
  await profile.locator('ion-input[name="codigoCorreo"] input').fill(code);
  await profile.getByRole('button', { name: 'Confirmar nuevo correo', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'Acceso a tu cuenta', exact: true })).toBeVisible();
  expect((await page.request.get('/api/account/profile')).status()).toBe(401);
  await page.locator('ion-input[name="correo"] input').fill(next);
  await page.locator('ion-input[name="clave"] input').fill(password);
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
  await expect(page.locator('.welcome-strip')).toContainText(next.split('@')[0]);
  await page.getByRole('button', { name: 'Cerrar panel', exact: true }).click();
  await page.getByRole('button', { name: 'Mi perfil y preferencias', exact: true }).click();
  await expect(profile).toContainText('Sofía CU09 Navegador');
  await profile.getByRole('button', { name: 'Solicitar cierre de cuenta', exact: true }).click();
  await expect(profile).toContainText('Se conservarán pedidos');
  await expect(profile.getByRole('button', { name: 'Desactivar mi cuenta', exact: true })).toBeDisabled();
  await profile.locator('ion-input[name="claveCierre"] input').fill(password);
  await profile.locator('ion-checkbox[name="confirmClosure"]').click();
  await profile.getByRole('button', { name: 'Desactivar mi cuenta', exact: true }).click();
  await expect(page.locator('.welcome-strip')).toContainText('visitante');
  expect((await page.request.get('/api/account/profile')).status()).toBe(401);
  expect(fixture('sql', `SELECT status FROM user_accounts WHERE email='${next}';`)).toBe('INACTIVA');
});

test('RF035: impedimentos visibles impiden desactivar la cuenta', async ({ page }) => {
  const email = account();
  fixture('bloquear', email);
  await login(page, email);
  const profile = page.locator('app-perfil-personal');
  await profile.getByRole('button', { name: 'Solicitar cierre de cuenta', exact: true }).click();
  await expect(profile).toContainText('Pedidos sin finalizar');
  await expect(profile.getByRole('button', { name: 'Desactivar mi cuenta', exact: true })).toHaveCount(0);
  const eligibility = await page.request.get('/api/account/profile/closure');
  expect(eligibility.status()).toBe(200);
  expect((await eligibility.json()).allowed).toBe(false);
  expect(fixture('sql', `SELECT status FROM user_accounts WHERE email='${email}';`)).toBe('ACTIVA');
});

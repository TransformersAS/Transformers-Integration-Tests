import { expect, type APIRequestContext, type Page } from '@playwright/test';
export const buyerPassword = 'Cu07RealPassword123!';
export const uniqueBuyerEmail = () => `browser-${crypto.randomUUID()}@example.test`;
export async function openBuyerRegistration(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Crear cuenta de comprador / Habilitar COMPRADOR', exact: true }).click();
}
export async function fillBuyer(page: Page, email: string) {
  for (const [key, value] of Object.entries({ fullName: 'Ana Pérez', birthDate: '2000-05-20', email,
    phone: '+57 300 123 4567', password: buyerPassword, passwordConfirmation: buyerPassword })) {
    await page.locator(`app-registro-comprador ion-input[name="${key}"] input`).fill(value);
  }
}
export async function verificationCode(request: APIRequestContext, email: string) {
  const mailUrl = process.env.CU07_MAIL_URL!;
  let id = '';
  await expect.poll(async () => {
    const response = await request.get(`${mailUrl}/api/v1/messages`);
    expect(response.status()).toBe(200);
    const data = await response.json();
    id = data.messages.find((m: {To: {Address: string}[]}) => m.To.some(t => t.Address === email))?.ID ?? '';
    return Boolean(id);
  }).toBe(true);
  const mail = await (await request.get(`${mailUrl}/api/v1/message/${id}`)).json();
  const code = mail.Text.match(/(?:^|\s)([A-Za-z0-9_-]{43})(?=\s|$)/m)?.[1];
  if (!code) throw new Error('No verification code delivered to test mailbox');
  return code as string;
}

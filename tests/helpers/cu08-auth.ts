import { execFileSync } from 'node:child_process';
import { expect, type Page, type APIRequestContext } from '@playwright/test';
export const password = 'Cu08RealTestPassword!';
export const newPassword = 'Cu08NewExternalPassword!';
export function account(verified = true) {
  if (process.env.CU08_ISOLATED !== 'true') throw new Error('Requires the disposable CU08 fixture');
  const email = `cu08-ui-${crypto.randomUUID()}@example.test`;
  const query = `INSERT INTO user_accounts(email,password_hash,status,email_verified_at)
    VALUES ('${email}','$2y$12$1b8lQufQ.HVnswuPxAClJ.mZciYOAGr8F2XGKjzndmAJQ7t94AK7m','ACTIVA',${verified ? 'CURRENT_TIMESTAMP(6)' : 'NULL'});
    INSERT INTO user_account_roles(account_id,role) SELECT id,'COMPRADOR' FROM user_accounts WHERE email='${email}';`;
  const args = ['scripts/cu08-compose.sh','exec','-T','mysql','sh','-c'];
  const command = 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -N -B -u "$MYSQL_USER" "$MYSQL_DATABASE"';
  const schema = execFileSync('bash',[...args,command],{input:'SELECT DATABASE();',encoding:'utf8'}).trim();
  expect(schema).toBe('cu08_e2e');
  execFileSync('bash',[...args,command],{input:query});
  return email;
}
export const accountPanel = (page: Page) => page.getByRole('dialog',{name:'Seguridad de tu cuenta',exact:true});
export async function openLogin(page: Page) {
  await page.goto('/');
  await page.getByRole('button',{name:'Ver cuenta',exact:true}).click();
  await expect(page.getByRole('dialog',{name:'Acceso a tu cuenta',exact:true})).toBeVisible();
}
export async function attempt(page: Page,email: string,secret = password) {
  await page.locator('ion-input[name="correo"] input').fill(email);
  await page.locator('ion-input[name="clave"] input').fill(secret);
  return action(page,'Iniciar sesión','/api/auth/login');
}
export async function login(page: Page,email: string,secret = password) {
  await openLogin(page);expect((await attempt(page,email,secret)).status()).toBe(204);
  await expect(accountPanel(page)).toBeVisible();
}
export async function action(page: Page,name: string,path: string) {
  const response=page.waitForResponse(r=>new URL(r.url()).pathname===path && r.request().method()!=='GET');
  await page.getByRole('button',{name,exact:true}).click();
  return response;
}
export async function code(request: APIRequestContext,email: string,subject: string) {
  const url=process.env.CU08_MAIL_URL!;let id='';
  await expect.poll(async()=>{
    const response=await request.get(`${url}/api/v1/messages`);expect(response.status()).toBe(200);
    const data=await response.json();
    id=data.messages.find((m:{To:{Address:string}[];Subject:string})=>m.To.some(t=>t.Address===email)&&m.Subject.includes(subject))?.ID??'';
    return Boolean(id);
  }).toBe(true);
  const response=await request.get(`${url}/api/v1/message/${id}`);expect(response.status()).toBe(200);
  const mail=await response.json();const token=mail.Text.match(/(?:^|\s)([A-Za-z0-9_-]{43})(?=\s|$)/m)?.[1];
  if(!token) throw new Error('Test mail code absent (contents redacted)');
  return token as string;
}

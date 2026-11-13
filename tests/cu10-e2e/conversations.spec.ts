import { test, expect, BrowserContext, Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';

type Fixture = { buyer: {email: string}, seller: {email: string}, password: string,
 product: number, otherProduct: number, order: number, productName: string };
function fixture(): Fixture { return JSON.parse(execFileSync('python3', ['scripts/cu10-fixture.py'], {encoding:'utf8'})); }
async function login(page: Page, email: string, password: string) {
  await page.goto('/');
  await page.getByRole('button', {name: 'Iniciar sesión', exact: true}).click();
  await page.getByLabel('Correo', {exact: true}).fill(email);
  await page.getByLabel('Contraseña', {exact: true}).fill(password);
  await page.locator('app-acceso').getByRole('button', {name: 'Iniciar sesión', exact: true}).click();
  await expect(page.getByRole('button', {name:'Iniciar sesión',exact:true})).toHaveCount(0);
  await page.getByRole('button',{name:'Cerrar panel',exact:true}).click();
}
async function openInbox(page: Page, seller: boolean) {
  await page.getByRole('button', {name: seller ? 'Opciones de mi negocio' : 'Opciones de mis compras'}).click();
  await page.getByRole('button', {name: 'Conversaciones', exact: true}).click();
}
async function reply(page: Page, text: string) {
  const chat=page.locator('app-conversaciones');
  await chat.getByLabel('Mensaje', {exact:true}).fill(text);
  await chat.getByRole('button', {name:'Enviar mensaje',exact:true}).click();
  await expect(chat.locator('article').filter({hasText:text})).toBeVisible();
}
async function csrfPost(context: BrowserContext, path: string, data: unknown) {
  const csrf=await (await context.request.get('/api/auth/csrf')).json();
  return context.request.post(path,{data,headers:{[csrf.headerName]:csrf.token}});
}
test('producto: conversación entre dos sesiones, imagen, estados y persistencia', async ({browser}) => {
  const f=fixture(); const buyer=await browser.newContext({baseURL:process.env.FRONTEND_BASE_URL});
  const seller=await browser.newContext({baseURL:process.env.FRONTEND_BASE_URL});
  try {
    const page=await buyer.newPage(), sp=await seller.newPage();
    await login(page,f.buyer.email,f.password); await login(sp,f.seller.email,f.password);
    await page.getByRole('button',{name:f.productName,exact:true}).click();
    await page.locator('app-contactar-vendedor').getByRole('button',{name:'Contactar al vendedor'}).click();
    const chat=page.locator('app-conversaciones');const text=`Pregunta navegador ${Date.now()}`;
    execFileSync('python3',['scripts/cu10-fixture.py','sql',"CREATE TRIGGER cu10_browser_fail BEFORE INSERT ON notifications FOR EACH ROW SET NEW.type=NULL;"]);
    try {
      await chat.getByLabel('Mensaje',{exact:true}).fill('Borrador tras fallo SQL CU10');
      await chat.getByRole('button',{name:'Enviar mensaje',exact:true}).click();
      await expect(chat.getByRole('alert')).toBeVisible();
      await expect(chat.getByLabel('Mensaje',{exact:true})).toHaveValue('Borrador tras fallo SQL CU10');
      await expect(chat.locator('article')).toHaveCount(0);
    } finally {execFileSync('python3',['scripts/cu10-fixture.py','sql','DROP TRIGGER cu10_browser_fail;']);}

    await chat.locator('input[type=file]').setInputFiles('tests/cu10-e2e/assets/real.png');
    await reply(page,text);await expect(chat.locator('article').filter({hasText:text}).locator('img')).toBeVisible();
    const threads=await (await buyer.request.get('/api/conversations')).json();const id=threads.find((t:{productId:number,orderId:number|null})=>t.productId===f.product&&!t.orderId).id;
    await expect.poll(async()=>(await (await seller.request.get(`/api/conversations/${id}`)).json()).unread).toBeGreaterThan(0);
    await openInbox(sp,true);const sc=sp.locator('app-conversaciones');await sc.locator('nav button').filter({hasText:f.productName}).click();
    await expect(sc.locator('article').filter({hasText:text})).toBeVisible();
    await expect.poll(async()=>(await (await seller.request.get(`/api/conversations/${id}`)).json()).unread).toBe(0);
    await reply(sp,`Respuesta ${text}`);await expect(chat.locator('article').filter({hasText:`Respuesta ${text}`})).toBeVisible();
    const notices=await (await buyer.request.get('/api/notifications')).json();expect(notices.items.some((n:{conversationId:number})=>n.conversationId===id)).toBe(true);
    await chat.getByRole('button',{name:'Cerrar conversación',exact:true}).click();await expect(chat.getByRole('button',{name:'Enviar mensaje',exact:true})).toBeDisabled();
    await sc.getByRole('button',{name:'Reabrir conversación',exact:true}).click();await expect(chat.getByRole('button',{name:'Cerrar conversación',exact:true})).toBeVisible();
    await chat.getByRole('button',{name:'Archivar',exact:true}).click();await chat.getByLabel('Archivadas').check();
    await chat.locator('nav button').filter({hasText:f.productName}).click();await chat.getByRole('button',{name:'Restaurar',exact:true}).click();
    await page.reload();await openInbox(page,false);await chat.locator('nav button').filter({hasText:f.productName}).click();await expect(chat.locator('article').filter({hasText:text}).first()).toBeVisible();
  } finally {await buyer.close();await seller.close();}
});
test('pedido: producto seleccionado, respuesta, aislamiento, borrador y reporte', async ({browser}) => {
  const f=fixture();const buyer=await browser.newContext({baseURL:process.env.FRONTEND_BASE_URL});const seller=await browser.newContext({baseURL:process.env.FRONTEND_BASE_URL});
  try {
    const page=await buyer.newPage(),sp=await seller.newPage();await login(page,f.buyer.email,f.password);await login(sp,f.seller.email,f.password);
    await page.getByRole('button',{name:'Mis pedidos',exact:true}).click();const orders=page.locator('app-mis-pedidos');
    await orders.getByRole('button',{name:new RegExp(`Ver detalle.*${f.order}`)}).click();await expect(orders.locator('app-contactar-vendedor')).toHaveCount(2);
    await orders.locator('app-contactar-vendedor').last().getByRole('button').click();const chat=page.locator('app-conversaciones');await expect(chat.locator('main')).toContainText(`Pedido #${f.order}`);
    const text=`Pedido navegador ${Date.now()}`;await reply(page,text);
    const threads=await (await buyer.request.get('/api/conversations')).json();const thread=threads.find((t:{orderId:number,productId:number})=>t.orderId===f.order&&t.productId===f.otherProduct);expect(thread).toBeTruthy();
    await openInbox(sp,true);const sc=sp.locator('app-conversaciones');await sc.locator('nav button').filter({hasText:'CU10 segundo'}).click();await reply(sp,`Respuesta ${text}`);
    await expect(chat.locator('article').filter({hasText:`Respuesta ${text}`})).toBeVisible();
    const other=await csrfPost(buyer,'/api/conversations',{productId:f.product,orderId:f.order});expect(other.status()).toBe(200);const otherId=(await other.json()).id;
    expect(JSON.stringify(await (await buyer.request.get(`/api/conversations/${otherId}/messages`)).json())).not.toContain(text);
    await chat.getByLabel('Mensaje',{exact:true}).fill('Borrador CU10');await chat.locator('input[type=file]').setInputFiles({name:'falso.png',mimeType:'image/png',buffer:Buffer.from('<script>falso</script>')});
    await chat.getByRole('button',{name:'Enviar mensaje',exact:true}).click();await expect(chat.getByRole('alert')).toBeVisible();await expect(chat.getByLabel('Mensaje',{exact:true})).toHaveValue('Borrador CU10');
    const report=chat.locator('article').filter({hasText:`Respuesta ${text}`}).locator('app-reportar-contenido');await report.getByRole('button',{name:'Reportar mensaje',exact:true}).click();
    await report.locator('select').selectOption('CONTENIDO_INAPROPIADO');await report.locator('textarea').fill('Reporte externo CU10');await report.getByRole('button',{name:'Enviar reporte',exact:true}).click();await expect(report).toContainText('Reporte radicado');
  } finally {await buyer.close();await seller.close();}
});

// Run through RadioMembershipIntegrationTests#browserEndToEnd with -Dradio.browser=true.
// Testcontainers supplies isolated databases and test users; no production data or payments.
const { chromium } = require('playwright');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const backendPort = Number(process.argv[2]);
const root = path.resolve('dist/comercio-flex-frontend/browser');
const server = http.createServer((req, res) => {
 if (req.url.startsWith('/api/')) {
  const proxy = http.request({ hostname: '127.0.0.1', port: backendPort, path: req.url, method: req.method, headers: { ...req.headers, host: req.headers.host } }, response => { res.writeHead(response.statusCode, response.headers); response.pipe(res); });
  proxy.on('error', () => { res.writeHead(502); res.end(); }); req.pipe(proxy); return;
 }
 const file = path.resolve(root, '.' + new URL(req.url, 'http://localhost').pathname);
 const safe = file.startsWith(root + path.sep) && fs.existsSync(file) && fs.statSync(file).isFile() ? file : path.join(root, 'index.html');
 const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml' };
 res.setHeader('Content-Type', types[path.extname(safe)] || 'application/octet-stream'); fs.createReadStream(safe).pipe(res);
});
(async () => {
 await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
 const origin = 'http://127.0.0.1:' + server.address().port;
 const executable = process.env.CHROME_BIN || (process.platform === 'win32' ? 'C:/Program Files/Google/Chrome/Application/chrome.exe' : undefined);
 const browser = await chromium.launch({ headless: true, executablePath: executable });
 const page = await browser.newPage({ viewport: { width: 1366, height: 900 } });
 page.setDefaultTimeout(15000);
 const errors=[]; page.on('pageerror', e=>errors.push(e.message));
 const password='radio-membership-password';
 try {
  await page.goto(origin + '/admin/login');
  await page.getByLabel(/^(Email|Correo electrónico)$/).fill('owner@example.com');
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: /Ingresar|Iniciar sesión/ }).click();
  await page.waitForURL('**/tiendas/radio-a/admin');
  await page.getByRole('link', { name: 'Planes', exact: true }).click();
  await page.getByRole('button', { name: 'Crear plan', exact: true }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('PLUS');
  await page.getByLabel('Descripción', { exact: true }).fill('Plan piloto de socios');
  await page.getByLabel('Precio mensual', { exact: true }).fill('6000');
  await page.getByLabel('Beneficios (uno por línea)', { exact: true }).fill('Comunidad\nEventos');
  await page.getByRole('button', { name: 'Guardar', exact: true }).click();
  await page.getByRole('cell', { name: 'PLUS', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await page.goto(origin + '/radio-a/registro');
  for (const width of [390,1366]) {
   await page.setViewportSize({width,height:900});
   await page.goto(origin + '/radio-a');
   if(width===390) await page.getByRole('button',{name:'Menú',exact:true}).click();
   await page.getByRole('link',{name:'Programas',exact:true}).click();
   await page.waitForURL('**/radio-a/programas');
   assert.equal(new URL(page.url()).pathname,'/radio-a/programas');
   await page.reload();
   await page.getByRole('heading',{name:'Programas',exact:true}).waitFor();
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true);
  }
  await page.goto(origin + '/radio-a/registro');
  await page.getByLabel('Nombre', { exact: true }).fill('Ignacio');
  await page.getByLabel('Apellido', { exact: true }).fill('Echave');
  await page.getByLabel(/^(Email|Correo electrónico)$/).fill('browser-member@example.com');
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByLabel('Confirmar contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Crear cuenta', exact: true }).click();
  await page.getByText('Solicitud recibida.', { exact: false }).waitFor();
  await page.goto(origin + '/radio-a/socios');
  await page.getByRole('button', { name: 'Elegir plan', exact: true }).click();
  await page.waitForURL('**/login?next=socios');
  await page.getByLabel(/^(Email|Correo electrónico)$/).fill('browser-member@example.com');
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
  await page.waitForURL('**/socios');
  await page.getByRole('button', { name: 'Elegir plan', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar', exact: true }).click();
  await page.waitForURL('**/mi-cuenta');
  await page.reload();
  await page.getByRole('heading', { name: 'Hola, Ignacio' }).waitFor();
  assert.match(await page.locator('main').innerText(), /PLUS/);
  assert.match(await page.locator('main').innerText(), /PENDIENTE/);
  assert.match(await page.locator('main').innerText(), /septiembre/);
  assert.match(await page.locator('main').innerText(), /6[.,]000/);
  await page.getByText('Mercado Pago no está disponible', { exact: false }).waitFor();
  for(const width of [390,768,1366,1920]) {
   await page.setViewportSize({width,height:900});
   assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth),true,'Account width '+width);
  }
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await page.waitForURL('**/radio-a');
  await page.goto(origin + '/radio-a/login');
  await page.getByLabel(/^(Email|Correo electrónico)$/).fill('browser-member@example.com');
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Iniciar sesión', exact: true }).click();
  await page.waitForURL('**/mi-cuenta');
  await page.getByRole('heading', { name: 'PLUS', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Cerrar sesión', exact: true }).click();
  await page.goto(origin + '/admin/login');
  await page.getByLabel(/^(Email|Correo electrónico)$/).fill('owner@example.com');
  await page.getByLabel('Contraseña', { exact: true }).fill(password);
  await page.getByRole('button', { name: /Ingresar|Iniciar sesión/ }).click();
  await page.waitForURL('**/tiendas/radio-a/admin');
  await page.getByRole('link', { name: 'Socios', exact: true }).click();
  await page.getByRole('cell', { name: 'browser-member@example.com', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Ver cuotas', exact: true }).click();
  assert.match(await page.locator('main').innerText(), /PENDIENTE/);
  assert.deepEqual(errors,[]);
  console.log('RADIO E2E OK: admin creates plan, registration, login, confirmation, persistence, admin member and period history; four viewport widths.');
 } finally { await browser.close(); await new Promise(resolve=>server.close(resolve)); }
})().catch(error=>{ console.error(error); server.close(); process.exitCode=1; });

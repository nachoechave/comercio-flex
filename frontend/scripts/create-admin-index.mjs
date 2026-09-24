import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const outputDir = resolve(process.cwd(), 'dist/comercio-flex-frontend/browser');
const sourcePath = resolve(outputDir, 'index.html');
const adminPath = resolve(outputDir, 'admin-index.html');

const source = await readFile(sourcePath, 'utf8');
if (!source.includes('</head>')) {
  throw new Error('No se encontró </head> en el index generado por Angular.');
}

const adminHead = [
  '  <link rel="manifest" href="/admin.webmanifest" data-admin-pwa-manifest="true">',
  '  <meta name="theme-color" content="#0b4ddb" data-admin-pwa-theme="true">',
].join('\n');

const adminHtml = source.replace('</head>', `${adminHead}\n</head>`);
await writeFile(adminPath, adminHtml, 'utf8');

console.log(`Admin PWA shell generado: ${adminPath}`);

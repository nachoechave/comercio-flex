import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const outputDir = resolve(process.cwd(), 'dist/comercio-flex-frontend/browser');
const sourcePath = resolve(outputDir, 'index.html');
const adminPath = resolve(outputDir, 'admin-index.html');
const platformPath = resolve(outputDir, 'platform-index.html');

const source = await readFile(sourcePath, 'utf8');
if (!source.includes('</head>') || !source.match(/<title>.*?<\/title>/s)) {
  throw new Error('No se encontró un <head> y <title> válidos en el index generado por Angular.');
}

const replaceTitle = (html, title) => html.replace(/<title>.*?<\/title>/s, `<title>${title}</title>`);

const adminHead = [
  '  <meta name="robots" content="noindex, nofollow" data-admin-seo="true">',
  '  <link rel="manifest" href="/admin.webmanifest" data-admin-pwa-manifest="true">',
  '  <meta name="theme-color" content="#0b4ddb" data-admin-pwa-theme="true">',
].join('\n');

const adminHtml = replaceTitle(source, 'Comercio Flex | Panel de administración').replace(
  '</head>',
  `${adminHead}\n</head>`,
);
await writeFile(adminPath, adminHtml, 'utf8');

const landingTitle = 'Comercio Flex | Tienda online y gestión para comercios';
const landingDescription =
  'Creá tu tienda online y gestioná productos, stock, pedidos, pagos y envíos desde un solo lugar. Comercio Flex simplifica la venta online de tu negocio.';
const landingUrl = 'https://comercioflex.com.ar/';
const landingImage = 'https://comercioflex.com.ar/assets/comercio-flex/icon-512x512.png';
const structuredData = JSON.stringify({
  '@context': 'https://schema.org',
  '@graph': [
    {
      '@type': 'Organization',
      '@id': `${landingUrl}#organization`,
      name: 'Comercio Flex',
      url: landingUrl,
      logo: landingImage,
    },
    {
      '@type': 'SoftwareApplication',
      '@id': `${landingUrl}#software`,
      name: 'Comercio Flex',
      url: landingUrl,
      description: landingDescription,
      applicationCategory: 'BusinessApplication',
      operatingSystem: 'Web',
      inLanguage: 'es-AR',
      publisher: { '@id': `${landingUrl}#organization` },
    },
  ],
});

const platformHead = [
  `  <meta name="description" content="${landingDescription}" data-platform-seo="true">`,
  '  <meta name="robots" content="index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1" data-platform-seo="true">',
  `  <link rel="canonical" href="${landingUrl}" data-platform-seo="true">`,
  '  <meta property="og:site_name" content="Comercio Flex" data-platform-seo="true">',
  '  <meta property="og:locale" content="es_AR" data-platform-seo="true">',
  `  <meta property="og:title" content="${landingTitle}" data-platform-seo="true">`,
  `  <meta property="og:description" content="${landingDescription}" data-platform-seo="true">`,
  '  <meta property="og:type" content="website" data-platform-seo="true">',
  `  <meta property="og:url" content="${landingUrl}" data-platform-seo="true">`,
  `  <meta property="og:image" content="${landingImage}" data-platform-seo="true">`,
  '  <meta property="og:image:type" content="image/png" data-platform-seo="true">',
  '  <meta property="og:image:width" content="512" data-platform-seo="true">',
  '  <meta property="og:image:height" content="512" data-platform-seo="true">',
  '  <meta property="og:image:alt" content="Logo de Comercio Flex" data-platform-seo="true">',
  '  <meta name="twitter:card" content="summary" data-platform-seo="true">',
  `  <meta name="twitter:title" content="${landingTitle}" data-platform-seo="true">`,
  `  <meta name="twitter:description" content="${landingDescription}" data-platform-seo="true">`,
  `  <meta name="twitter:image" content="${landingImage}" data-platform-seo="true">`,
  '  <meta name="twitter:image:alt" content="Logo de Comercio Flex" data-platform-seo="true">',
  `  <script type="application/ld+json" data-platform-seo="true">${structuredData}</script>`,
].join('\n');

const platformHtml = replaceTitle(source, landingTitle).replace(
  '</head>',
  `${platformHead}\n</head>`,
);
await writeFile(platformPath, platformHtml, 'utf8');

console.log(`Admin PWA shell generado: ${adminPath}`);
console.log(`Landing SEO shell generado: ${platformPath}`);

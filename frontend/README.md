# Frontend de Comercio Flex

SPA Angular que contiene la tienda pública, la administración de cada comercio,
la autenticación y la administración global de la plataforma.

En producción el build se incorpora al JAR de Spring Boot y se sirve desde el
mismo origen que la API. El frontend no se despliega como una aplicación
productiva independiente.

## Estructura

`src/app/` se organiza por responsabilidad:

- `core`: autenticación, routing y servicios transversales;
- `features`: áreas funcionales cargadas por rutas;
- `layouts`: shells de storefront, admin y superadmin;
- `shared`: pipes, modelos, utilidades y componentes reutilizables.

## Principales áreas

### Storefront

Tienda pública, catálogo, detalle de producto, carrito persistente, checkout,
Mercado Pago, transferencia bancaria, confirmación e historial local de pedidos.
El estado local se separa por `storeSlug` y los datos comerciales se revalidan
contra backend.

### Admin

Dashboard, categorías, productos, variantes, imágenes, inventario, pedidos,
transferencias, pagos y configuración del comercio. Las acciones visibles se
adaptan al rol, aunque la autorización definitiva siempre corresponde al backend.

### Auth y SuperAdmin

Login/sesión global, selección de comercio y panel de plataforma para gestionar
tenants, provisioning, usuarios, actividad, infraestructura sanitizada y branding.

## Desarrollo local

Requisitos: Node/npm compatibles con `package.json` y el backend disponible según
`proxy.conf.json`.

```powershell
npm ci
npm start
```

`npm start` ejecuta el script real `ng serve --proxy-config proxy.conf.json`. El
servidor de desarrollo queda disponible normalmente en `http://localhost:4200`.

Para una instalación exploratoria puede usarse `npm install`, pero CI y las
validaciones reproducibles deben preferir `npm ci`.

## Tests

```powershell
# Modo interactivo según Angular CLI
npm test

# Ejecución única como CI
npm test -- --watch=false

# Spec focalizado
npm test -- --watch=false --include=src/app/ruta/al-archivo.spec.ts
```

La suite usa el runner configurado por Angular (`Vitest` en la configuración
actual). No hay un comando E2E configurado en `package.json`.

## Build

```powershell
npm run build
```

El comando ejecuta `ng build`. Para validación explícita de producción puede
usarse:

```powershell
npm run build -- --configuration production
```

La salida se genera bajo `dist/comercio-flex-frontend/`; el `Dockerfile` copia la
carpeta `browser` a los recursos estáticos del backend.

## Convenciones relevantes

- componentes standalone y rutas lazy;
- signals para estado local y RxJS para operaciones HTTP;
- contratos API tipados por feature;
- estilos SCSS acotados por componente;
- fechas comerciales en `es-AR` y zona `America/Argentina/Buenos_Aires` sólo en
  presentación;
- ninguna credencial, lookup token o dato financiero debe aparecer en UI/logs.

La guía integral de ejecución está en
[`../docs/GUIA_DE_DESARROLLO.md`](../docs/GUIA_DE_DESARROLLO.md).

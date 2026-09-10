# RADIO — Fase 5: experiencia final configurable

La Fase 5 convierte RADIO en una experiencia pública y administrativa completa por tenant. La implementación parte del estado local de Fase 4 (`0ed2b2f`), porque `origin/main` todavía no contiene ese commit; no modifica la lógica central de Checkout Pro ni incorpora Orders, noticias o streaming propio.

## Rutas

Sitio público: `/tiendas/{slug}`, `/programas`, `/nosotros`, `/socios`, `/registro`, `/ingresar` y `/mi-cuenta/**`. La navegación RADIO se mantiene en `frontend/src/app/features/radio/radio.routes.ts` y no comparte componentes de ecommerce.

Administración RADIO: `/tiendas/{slug}/admin`, `/socios`, `/planes`, `/cuotas` y `/contenido`. Productos, inventario, pedidos y checkout ecommerce siguen en sus rutas existentes y no aparecen en la navegación RADIO.

## Configuración y entidades

La migración tenant `V027__create_radio_site_content.sql` crea:

- `radio_site_settings`: hero, descripción y enlaces YouTube, Instagram, X y WhatsApp.
- `radio_programs`: nombre, descripción, días, horario, imagen, conductores, orden y activo.
- `radio_team_members`: nombre, rol, bio, foto, red social, orden y activo.
- `radio_sponsors`: nombre, logo, URL, texto, nivel principal/secundario, orden y activo.

Logo, favicon, hero, colores y tipografía reutilizan `store_settings`, `TenantBrandingService` y el almacenamiento de assets existente. La interfaz RADIO aplica el favicon configurado por tenant mediante el layout común y hereda variables CSS de branding.

## Backend y seguridad

`RadioSiteController` expone `GET /radio-site` para contenido público y CRUD tenant-scoped bajo `/admin/radio-site/**`. El filtro de resolución incluye el recurso público y exige `TenantType.RADIO`; las escrituras requieren `MANAGE_BASIC_SETTINGS`. Las respuestas no contienen IDs internos.

Los planes se siguen leyendo desde `membership-plans`, con precio proveniente del backend. Si existe membresía, la pantalla de socios conduce a `Mi membresía`; el panel conserva estados PENDING, ACTIVE, EXPIRED y el botón de pago manual de Fase 4. No se modificó el flujo de pagos.

## Frontend

`RadioLayout` aporta navbar responsive con menú móvil, identidad, YouTube, sesión y footer social. `RadioHomePage` muestra hero configurable, programas destacados, planes y sponsors. `RadioProgramsPage` y `RadioAboutPage` renderizan programas y equipo. `RadioContentAdminPage` permite guardar configuración, crear programas, integrantes y sponsors, y retirar publicaciones.

El panel de socio existente conserva Inicio, Mi plan, Pagos, Perfil y cierre de sesión; su CSS se mantiene responsive. Los formularios tienen labels, botones reales, focus visible, headings semánticos, alt text y enlaces externos con `noopener`.

## Alcance excluido

No se implementan noticias, streaming interno, reproductor propio, ecommerce dentro de RADIO, calendario complejo, detección automática de YouTube, pagos recurrentes ni suscripciones.

## Validación

- Frontend completo: 331 tests en 64 archivos, 0 fallos.
- Backend completo: 443 tests, 0 fallos, 0 errores y 1 omitido; `package` aprobado.
- Test RADIO/MySQL de aislamiento y autorización: aprobado; la suite también cubre migraciones V027, snapshots y concurrencia existentes de Fases 1–4.
- Backend compile/package: aprobado (535 clases).
- Frontend production build: aprobado (bundle inicial aproximado 330.83 kB).
- E2E real con navegador: aprobado en 390, 768, 1366 y 1920 px.
- Docker build `comercio-flex:radio-final-experience`: aprobado.
- `git diff --check`: código 0; sólo advertencias informativas de normalización LF/CRLF.
- El árbol contiene únicamente cambios de Fase 5 y no conserva logs temporales generados por las pruebas.

## Configurar una radio nueva

1. Crear/provisionar un tenant con `tenant_type=RADIO`.
2. Configurar branding y assets desde `Configuración > Apariencia`.
3. Abrir `Administración > Programas y sponsors` y guardar hero, redes, programas, equipo y sponsors.
4. Crear planes desde `Planes` y habilitar pagos manuales desde la configuración de pagos cuando existan credenciales sandbox/producción.
5. Verificar las cuatro resoluciones objetivo (390, 768, 1366 y 1920 px), favicon, enlaces externos y permisos de un socio normal.

## Riesgos y producción

Las URLs de imágenes de programas, equipo y sponsors son referencias configurables; el siguiente paso de producción puede conectarlas a un selector sobre el storage ya existente. Hay que validar CSP, HTTPS, compresión de imágenes y dominios finales antes de Donweb. La acreditación de Mercado Pago mantiene los riesgos y controles documentados en `docs/RADIO_FASE_4.md`.

# Estado Codex

## Completado

- Fase 1 — SuperAdmin: rol global separado, `/api/v1/superadmin`, dashboard,
  búsqueda/filtro/detalle de empresas, activación/suspensión y auditoría.
- Angular: área `/superadmin` con guard, layout, estados de carga/error/empty y
  confirmación de cambios sensibles.
- Bootstrap local opcional mediante `LOCAL_SUPER_ADMIN_*`.
- Fase 2 — Alta automática: formulario SuperAdmin, base MySQL aislada, Flyway,
  configuración inicial, `OWNER`, pool Hikari dinámico, auditoría y reintento.
- Fase 3 — Branding: Apariencia exclusiva SuperAdmin, paleta, tipografía, hero,
  logo/favicon, template, storefront dinámico, assets versionados y auditoría.
- Fase 4 — Variantes genéricas: opciones nombre/valor normalizadas, V015 con
  backfill de Talle/Color, combinaciones canónicas, editor Angular, catálogo,
  carrito y snapshots inmutables de pedido.

## Decisiones

- `SUPER_ADMIN` vive en `platform_users.platform_role`; roles tenant permanecen
  en `memberships` y no se concede acceso implícito a bases tenant.
- `SUSPENDED` impide resolver tienda pública y panel tenant.
- Dominio se persiste como metadato; su resolución HTTP y la señal de última
  actividad siguen pendientes.
- `database_key` y datos de conexión nunca se exponen.
- Provisioning MySQL vive detrás de un puerto reemplazable; usa una plantilla JDBC
  y secretos globales, con grants exactos por base.
- Fallos quedan `PROVISIONING_FAILED`; no se borran bases automáticamente.
- Branding se persiste en cada `store_settings`; SuperAdmin resuelve la empresa
  desde control DB y abre el tenant internamente. Angular nunca envía `database_key`.
- `CLASSIC`/`MODERN`/`MINIMAL` ya se persisten; la composición completa es Fase 6.
- `size`/`color` permanecen derivados en API y base para compatibilidad; los
  clientes nuevos usan exclusivamente `options`.

## Pendiente

- Fase 5 — Inventario operativo avanzado sobre variantes genéricas.
- Definir resolución de dominios y una señal de actividad tenant.

## Archivos clave

- `backend/src/main/resources/db/migration/control/V009__add_super_admin_foundation.sql`
- `backend/src/main/resources/db/migration/control/V010__add_tenant_provisioning.sql`
- `backend/src/main/resources/db/migration/tenant/V014__add_tenant_branding.sql`
- `backend/src/main/resources/db/migration/tenant/V015__add_generic_product_options.sql`
- `backend/src/main/java/com/comercioflex/catalog/domain/VariantOptionValue.java`
- `backend/src/main/java/com/comercioflex/platformadmin/`
- `backend/src/main/java/com/comercioflex/tenant/application/BrandingAssetService.java`
- `backend/src/main/java/com/comercioflex/tenant/infrastructure/routing/`
- `frontend/src/app/features/superadmin/`
- `frontend/src/app/features/superadmin/companies/company-branding.ts`
- `frontend/src/app/layouts/super-admin-layout/`

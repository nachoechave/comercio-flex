# Entrega: fundación RADIO (fase 1)

Rama: codex/radio-tenant-foundation. Base: origin/main, cbd012bbd97e108fd3c7c6c5f6360219f31ee435.

## Implementación

Tipo ECOMMERCE/RADIO en control, resolución y provisioning. El alta de Super Admin
permite elegir el tipo y el detalle lo muestra. El frontend selecciona árboles de
rutas separados por slug o dominio; RADIO muestra únicamente un placeholder.
No se agregaron roles, socios, registro, planes ni pagos. Las APIs ecommerce y sus
permisos siguen vigentes; esta fase no introduce restricciones de API por vertical.

## Migración

V016__add_tenant_type.sql: VARCHAR(30), NOT NULL, DEFAULT ECOMMERCE y CHECK.
Se verificó V015 como última versión antes de crearla. El test aplica primero las
migraciones hasta V015, inserta un tenant previo y luego aplica V016. No se cambiaron
migraciones anteriores ni el esquema tenant. Industry y templates conservan su significado.

## Validación

- Backend, lote de fundación: 44 pruebas, 0 fallos, 0 errores, 0 omitidas.
  TenantTypeMigrationTests, CreateCompanyTenantTypeTests, TenantRoutingIntegrationTests,
  SuperAdminIntegrationTests, CompanyProvisioningServiceTests, IdentitySecurityIntegrationTests,
  MembershipRoleTests, StorefrontTenantResolutionControllerTests y TenantDomainResolverTests.
- Backend, contratos actualizados y regresión: 55 pruebas, 0 fallos, 0 errores, 0 omitidas.
  CreateCompanyTenantTypeTests, StorefrontTenantResolutionControllerTests,
  CheckoutProServiceTests, CheckoutProProviderIdempotencyTests, BankTransferPaymentServiceTests,
  QrOrderServiceTests y MembershipRoleTests. Ambos lotes tienen pruebas en común.
- Frontend completo: npm test -- --watch=false; 60 archivos, 290 pruebas aprobadas.
- Backend: mvn con el segundo lote y objetivo package; BUILD SUCCESS.
- Frontend producción: npm run build -- --configuration production; correcto.
- Imagen integrada: docker build -t comercio-flex:radio-foundation .; correcto.
  Imagen local construida sin publicación ni despliegue.
- git diff --check sin errores. Revisión de alcance sin cambios en catálogo, inventario,
  pedidos, pagos, carrito, checkout o templates; los cambios storefront se limitan a resolución.

## Operación y límites

No se ejecutó ninguna migración sobre bases de aplicación existentes ni se desplegó.
Flyway aplicará V016 en control al iniciar la nueva versión. Para ver el placeholder,
crear un tenant Radio / Medio desde Super Admin y abrir /tiendas/{slug}; requiere el
provisioner que ya usa la plataforma. No hay conversión de tipo desde la edición.
Los tenants RADIO mantienen el esquema y la administración existentes.
Las verificaciones visuales en navegador y un despliegue real quedan fuera de esta validación.

## Fase 2 sugerida

Implementar el acceso de usuarios finales a RADIO: registro, login y recuperación
reutilizando platform_users, sesiones y CSRF, sin crear relaciones administrativas
memberships. Agregar autorización por tipo y titularidad para los endpoints nuevos,
con tests entre tenants. No incluir planes ni pagos hasta una fase posterior.
El ajuste futuro del filtro está documentado en ARQUITECTURA.md.

## Archivos creados

- [backend/src/main/java/com/comercioflex/tenant/domain/TenantType.java](../backend/src/main/java/com/comercioflex/tenant/domain/TenantType.java)
- [backend/src/main/resources/db/migration/control/V016__add_tenant_type.sql](../backend/src/main/resources/db/migration/control/V016__add_tenant_type.sql)
- [backend/src/test/java/com/comercioflex/platformadmin/api/CreateCompanyTenantTypeTests.java](../backend/src/test/java/com/comercioflex/platformadmin/api/CreateCompanyTenantTypeTests.java)
- [backend/src/test/java/com/comercioflex/tenant/TenantTypeMigrationTests.java](../backend/src/test/java/com/comercioflex/tenant/TenantTypeMigrationTests.java)
- [frontend/src/app/core/tenant/tenant-experience.guards.spec.ts](../frontend/src/app/core/tenant/tenant-experience.guards.spec.ts)
- [frontend/src/app/core/tenant/tenant-experience.guards.ts](../frontend/src/app/core/tenant/tenant-experience.guards.ts)
- [frontend/src/app/core/tenant/tenant-type.ts](../frontend/src/app/core/tenant/tenant-type.ts)
- [frontend/src/app/features/radio/radio-placeholder.ts](../frontend/src/app/features/radio/radio-placeholder.ts)
- [frontend/src/app/features/radio/radio.routes.ts](../frontend/src/app/features/radio/radio.routes.ts)
- [docs/RADIO_FASE_1.md](../docs/RADIO_FASE_1.md)

## Archivos modificados

- [backend/src/main/java/com/comercioflex/platformadmin/api/CompanyDetailResponse.java](../backend/src/main/java/com/comercioflex/platformadmin/api/CompanyDetailResponse.java)
- [backend/src/main/java/com/comercioflex/platformadmin/api/CreateCompanyRequest.java](../backend/src/main/java/com/comercioflex/platformadmin/api/CreateCompanyRequest.java)
- [backend/src/main/java/com/comercioflex/platformadmin/application/CompanyProvisioningService.java](../backend/src/main/java/com/comercioflex/platformadmin/application/CompanyProvisioningService.java)
- [backend/src/main/java/com/comercioflex/platformadmin/application/CreateCompanyCommand.java](../backend/src/main/java/com/comercioflex/platformadmin/application/CreateCompanyCommand.java)
- [backend/src/main/java/com/comercioflex/platformadmin/domain/CompanyDetail.java](../backend/src/main/java/com/comercioflex/platformadmin/domain/CompanyDetail.java)
- [backend/src/main/java/com/comercioflex/platformadmin/infrastructure/control/JdbcCompanyCreationRepository.java](../backend/src/main/java/com/comercioflex/platformadmin/infrastructure/control/JdbcCompanyCreationRepository.java)
- [backend/src/main/java/com/comercioflex/platformadmin/infrastructure/control/JdbcCompanyRepository.java](../backend/src/main/java/com/comercioflex/platformadmin/infrastructure/control/JdbcCompanyRepository.java)
- [backend/src/main/java/com/comercioflex/tenant/api/AdminStoreSettingsResponse.java](../backend/src/main/java/com/comercioflex/tenant/api/AdminStoreSettingsResponse.java)
- [backend/src/main/java/com/comercioflex/tenant/api/StoreSettingsController.java](../backend/src/main/java/com/comercioflex/tenant/api/StoreSettingsController.java)
- [backend/src/main/java/com/comercioflex/tenant/api/StoreSettingsResponse.java](../backend/src/main/java/com/comercioflex/tenant/api/StoreSettingsResponse.java)
- [backend/src/main/java/com/comercioflex/tenant/api/StorefrontTenantResolutionResponse.java](../backend/src/main/java/com/comercioflex/tenant/api/StorefrontTenantResolutionResponse.java)
- [backend/src/main/java/com/comercioflex/tenant/api/TenantResolutionFilter.java](../backend/src/main/java/com/comercioflex/tenant/api/TenantResolutionFilter.java)
- [backend/src/main/java/com/comercioflex/tenant/application/ResolvedTenant.java](../backend/src/main/java/com/comercioflex/tenant/application/ResolvedTenant.java)
- [backend/src/main/java/com/comercioflex/tenant/application/TenantResolver.java](../backend/src/main/java/com/comercioflex/tenant/application/TenantResolver.java)
- [backend/src/main/java/com/comercioflex/tenant/infrastructure/control/ActiveTenant.java](../backend/src/main/java/com/comercioflex/tenant/infrastructure/control/ActiveTenant.java)
- [backend/src/main/java/com/comercioflex/tenant/infrastructure/control/TenantEntity.java](../backend/src/main/java/com/comercioflex/tenant/infrastructure/control/TenantEntity.java)
- [backend/src/main/java/com/comercioflex/tenant/infrastructure/control/TenantRepository.java](../backend/src/main/java/com/comercioflex/tenant/infrastructure/control/TenantRepository.java)
- [backend/src/test/java/com/comercioflex/SuperAdminIntegrationTests.java](../backend/src/test/java/com/comercioflex/SuperAdminIntegrationTests.java)
- [backend/src/test/java/com/comercioflex/TenantRoutingIntegrationTests.java](../backend/src/test/java/com/comercioflex/TenantRoutingIntegrationTests.java)
- [backend/src/test/java/com/comercioflex/tenant/api/StorefrontTenantResolutionControllerTests.java](../backend/src/test/java/com/comercioflex/tenant/api/StorefrontTenantResolutionControllerTests.java)
- [docs/ARQUITECTURA.md](../docs/ARQUITECTURA.md)
- [frontend/src/app/app.routes.spec.ts](../frontend/src/app/app.routes.spec.ts)
- [frontend/src/app/app.routes.ts](../frontend/src/app/app.routes.ts)
- [frontend/src/app/features/storefront/storefront-domain.guard.ts](../frontend/src/app/features/storefront/storefront-domain.guard.ts)
- [frontend/src/app/features/storefront/storefront-routing.service.ts](../frontend/src/app/features/storefront/storefront-routing.service.ts)
- [frontend/src/app/features/storefront/storefront.models.ts](../frontend/src/app/features/storefront/storefront.models.ts)
- [frontend/src/app/features/superadmin/companies/company-create.html](../frontend/src/app/features/superadmin/companies/company-create.html)
- [frontend/src/app/features/superadmin/companies/company-create.spec.ts](../frontend/src/app/features/superadmin/companies/company-create.spec.ts)
- [frontend/src/app/features/superadmin/companies/company-create.ts](../frontend/src/app/features/superadmin/companies/company-create.ts)
- [frontend/src/app/features/superadmin/companies/company-detail.html](../frontend/src/app/features/superadmin/companies/company-detail.html)
- [frontend/src/app/features/superadmin/super-admin.models.ts](../frontend/src/app/features/superadmin/super-admin.models.ts)

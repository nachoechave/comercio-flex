# RADIO — Fase 2: identidad pública

Implementación del 9 de septiembre de 2026. Commit y PR de Fase 2 autorizados posteriormente por el usuario; sin despliegue.

## Rama y base

Rama: codex/radio-identity. Base local autorizada: 4c11858 (feat: establish radio tenant foundation), que preserva la Fase 1. Parte de cbd012bbd97e108fd3c7c6c5f6360219f31ee435, último origin/main observado. La Fase 1 todavía no estaba mergeada en main: el usuario autorizó continuar con esta dependencia local. La PR de Fase 2 usa codex/radio-tenant-foundation como base para mostrar sólo esta fase. Antes de integrar a main, incorporar la Fase 1 y actualizar/revisar la base de esta PR.

## Modelo y autorización

Una identidad global por email normalizado en platform_users. Se reutilizan BCrypt coste 12, Spring Session JDBC, CFSESSION, login, logout y CSRF existentes. El registro crea exclusivamente platform_role=USER, status=ACTIVE; nunca crea filas administrativas en memberships ni modifica una identidad existente al repetir el email.

No se agrega relación persistente usuario-radio: todavía no existe un derecho comercial que represente. El tenant activo RADIO se resuelve desde el slug y TenantResolver; TenantResolutionFilter abre y cierra TenantContext con la databaseKey del servidor. /me toma solamente el id del principal autenticado, y consulta el propio perfil global en control DB. Un usuario puede consultar su mismo perfil desde Radio A o B, sin obtener perfiles ajenos ni datos privados de ninguna radio. No debe interpretarse como una suscripción o habilitación de beneficios. La Fase 3 deberá agregar autorización comercial por tenant para sus recursos propios.

Los DTO de registro y actualización rechazan campos desconocidos, incluidos userId, role, permissions y databaseKey. La actualización permite sólo nombre, apellido y teléfono opcional. Email es visible y no editable porque identifica el login global; cambiarlo requerirá un flujo de verificación separado. Para identidades antiguas, el nombre inicial usa display_name y el apellido queda vacío hasta editarse.

Los guards de administración y Super Admin siguen exigiendo permisos existentes. La cuenta privada RADIO sólo requiere identidad autenticada; el backend exige usuario ACTIVE para leer/escribir el perfil. Un usuario deshabilitado conserva la semántica anterior de /auth/session pero no puede operar su perfil ni restablecer contraseña.

## Migración

Control V017__add_public_identity_profile_and_recovery.sql, posterior a V016 de Fase 1. Agrega first_name, last_name y phone opcionales a platform_users y crea identity_password_resets con token_hash BINARY(32), user_id, tenant_id, created_at y expires_at. FK a usuario y tenant, índices de usuario y vencimiento. No modifica bases tenant ni crea tablas comerciales.

## API

Base: /api/v1/stores/{storeSlug}; todos estos endpoints exigen tenant activo RADIO.

| Método | Sufijo | Acceso / respuesta |
| --- | --- | --- |
| POST | /member-registration | Público + CSRF; 202 genérico también ante email repetido |
| GET | /me/profile | Sesión; perfil propio sin ids, roles ni secretos |
| PUT | /me/profile | Sesión + CSRF; nombre, apellido, teléfono |
| POST | /account/password/forgot | Público + CSRF; 202 sin enumeración |
| POST | /account/password/reset | Público + CSRF; 204 al consumir el token |

Se reutilizan /api/v1/auth/login, /logout, /session y /csrf. ECOMMERCE no habilita las nuevas operaciones (404 para una solicitud autorizada); no se modificaron sus rutas de negocio.

Frontend RADIO: registro, ingresar, olvide-contrasena, nueva-contrasena, mi-cuenta y mi-cuenta/perfil bajo /tiendas/{slug}, o desde la raíz en un dominio propio verificado. Visitante en mi-cuenta va a ingresar; login vuelve a mi-cuenta. El layout y formularios son mínimos, con campos apilados hasta 600px, navegación con ajuste de línea y controles accesibles. La portada conserva el placeholder de Fase 1.

## Recuperación

- 32 bytes SecureRandom, Base64 URL sin padding; sólo SHA-256 del token se persiste.
- Enlace válido 30 minutos y ligado al tenant que lo emitió. Una nueva solicitud reemplaza los enlaces anteriores del usuario global.
- Transacción en control DB con bloqueo del usuario y DELETE condicional: un solo consumo incluso con solicitudes concurrentes. Después cambia el BCrypt, elimina todos los tokens del usuario y revoca sus sesiones JDBC.
- CredentialSessionFilter compara la credencial de la sesión con la actual para rechazar sesiones antiguas que una petición simultánea pudiera volver a guardar. Agrega una consulta corta por solicitud autenticada. No cambia el tratamiento existente de usuarios deshabilitados.
- Limitador separado del login: 10 solicitudes por 15 minutos por acción/IP y acción/email; memoria acotada a 10.000 claves. Reset limita por IP. Se usa la dirección remota del servidor, nunca un header arbitrario para saltar límites.
- Cola de identidad acotada (core 1, máximo 2 hilos, 100 pendientes). Tanto emails existentes como inexistentes se encolan; búsqueda y SMTP quedan fuera de la respuesta pública. Fallos y saturación registran códigos genéricos sin email, token ni cuerpo.
- Reutiliza TransactionalEmailSender y su SMTP; no usa el outbox de pedidos ni fabrica order_id. Branding básico con nombre de radio escapado en HTML. El correo aclara que la contraseña es global.
- Errores de validación y JSON inválido se manejan sin registrar valores rechazados de contraseña/token.
- Enlace desde IDENTITY_PUBLIC_BASE_URI (origen HTTPS confiable; localhost HTTP sólo para desarrollo) o dominio primario verificado del tenant. No se construye desde Host ni parámetros del cliente. Token en fragmento #token=, retirado de la URL por Angular con replaceUrl; no se envía en query a servidores ni referers.

## Validación

Frontend: 305 tests en 61 archivos, con 15 casos RADIO. Navegador real: 15 casos a 390x844 y 15 a 1440x900. Build de producción: OK, bundle inicial 312,81 kB. Docker local final: OK, comercio-flex:radio-identity, imagen sha256:b4ff6b8cab6807d3804e1062b200302e76dc80675afe015d3f886ccf23b1e184. Backend completo: 407 pruebas, 0 fallos, 0 errores y 0 omitidas (mvn test, 15m33s, BUILD SUCCESS). Maven avisó de cierre lento del fork a los 30 segundos: los contextos de pruebas conservan tareas programadas de negocio mientras Testcontainers ya cerró sus bases. No hubo fallos de aserciones; queda como deuda del ciclo de vida del harness existente. Pase final sobre el código definitivo: mvn -Dtest=RadioIdentityIntegrationTests,IdentityRecoveryDeliveryTests,IdentitySecurityIntegrationTests package: 29 pruebas (13 + 6 + 10), sin fallos/errores/omitidas y BUILD SUCCESS, 1m50s. Incluye concurrencia, cuenta deshabilitada y ausencia de secretos en logs. JAR backend generado correctamente. En total se verificaron 416 casos distintos del backend entre ambos pases; la suite completa de 407 precedió únicamente al manejo localizado de errores de validación y a los casos ampliados.

Las pruebas de integración usan MySQL real, Spring Security, sesiones JDBC y CSRF real. Se simula únicamente el transporte SMTP; no se envía correo a terceros. Incluyen duplicados globales, hashing, RADIO/ECOMMERCE/inactivos, DTO restringido, acceso propio desde dos radios, admin y Super Admin denegados, reset/expiración/reuso/concurrencia, credenciales anteriores, sesiones revocadas y rate limiting. Las suites existentes complementan con bases físicas separadas, catálogo, pedidos, stock, pagos, branding y TenantType.

Comandos reproducibles desde backend: mvn test y mvn -DskipTests package. Desde frontend: npm test -- --watch=false y npm run build -- --configuration production.

Responsive en navegador: npm run test:radio:browser -- --browser-viewport=390x844 y repetir con 1440x900. Requiere Chromium de Playwright (npx playwright install chromium) o CHROME_BIN apuntando al Chrome instalado. La dependencia @vitest/browser-playwright sólo es de desarrollo. La suite normal jsdom no calcula geometría; esa verificación se ejecuta en los dos pases reales de navegador.

Docker: docker build -t comercio-flex:radio-identity . (sin push/run/deploy).

## Límites y pasos manuales

1. Para enviar enlaces, habilitar EMAIL_ENABLED y configurar el SMTP existente (remitente y credenciales), además de IDENTITY_PUBLIC_BASE_URI al origen público correcto. Con email deshabilitado se devuelve la misma confirmación pero no se emite correo.
2. Aplicar migraciones con el usuario migrador habitual al iniciar la versión aprobada, después de V016. No se aplicaron sobre producción ni sobre el MySQL de desarrollo persistente para esta entrega.
3. Probar recepción real, antispam y enlace en una casilla propia y un tenant RADIO de prueba. SMTP se simuló en los tests.
4. Las cookies conservan su alcance por host: misma identidad global no implica sesión compartida automáticamente entre dominios propios distintos. Login en cada host mantiene el diseño actual.
5. La cola no es durable ni tiene reintentos persistentes: reinicio, saturación o error SMTP pueden perder una solicitud; el usuario solicita un enlace nuevo. Un fallo de entrega posterior a crear token puede invalidar el enlace anterior. El limitador es local al proceso; para múltiples réplicas se necesita control distribuido/perimetral. Proxies deben conservar una dirección cliente confiable o el límite se agrupará por proxy.
6. No se verifica propiedad del email durante el registro (fuera del alcance pedido); la recuperación demuestra control de la casilla. No habilitar beneficios por el solo hecho de existir la identidad. Los tokens vencidos no se consumen; se reemplazan al pedir otro y conviene depurarlos periódicamente por expires_at según política operativa.
7. La edición es global y afecta el nombre visible en todos los sitios. Sesiones ya abiertas pueden mantener displayName anterior hasta nuevo login; /me devuelve siempre datos actualizados.

Dependencias de desarrollo: se actualizó Vitest y su adaptador a 4.1.11 para corregir el aviso detectado al agregar tests de navegador. npm audit aún informa 4 avisos en dependencias de herramientas existentes (fast-uri, nanoid, hono y qs); no se ejecutó una actualización general ajena al vertical.

## Fase 3 propuesta

Definir planes por tenant y una relación comercial explícita entre tenant RADIO y platform_user, separada de memberships administrativas. Agregar servicios de autorización por tenant y estado comercial para cada recurso privado comercial. Diseñar períodos, precios históricos, idempotencia y transiciones antes de integrar cobro; cubrir dos radios para el mismo usuario y mantener la identidad global. Ninguna tabla, pago, beneficio o pantalla comercial se implementa en esta fase.

git diff --check: OK. Revisión adicional de los 34 archivos nuevos/modificados: sin espacios al final ni marcadores de conflicto. Se eliminaron las capturas temporales de la prueba fallida; no se agregaron secretos, bases, builds ni archivos temporales al diff. Los cambios de Fase 2 se agrupan en un commit propio y una PR dependiente de Fase 1.

## Inventario de cambios

Creados:

- backend/src/main/java/com/comercioflex/identity/api/CredentialSessionFilter.java
- backend/src/main/java/com/comercioflex/identity/api/PublicIdentityController.java
- backend/src/main/java/com/comercioflex/identity/application/PublicIdentityException.java
- backend/src/main/java/com/comercioflex/identity/application/PublicIdentityRateLimiter.java
- backend/src/main/java/com/comercioflex/identity/application/PublicIdentityRepository.java
- backend/src/main/java/com/comercioflex/identity/application/PublicIdentityService.java
- backend/src/main/java/com/comercioflex/identity/application/PublicProfile.java
- backend/src/main/java/com/comercioflex/identity/infrastructure/IdentityRecoveryConfiguration.java
- backend/src/main/java/com/comercioflex/identity/infrastructure/IdentityRecoveryDelivery.java
- backend/src/main/java/com/comercioflex/identity/infrastructure/control/JdbcPublicIdentityRepository.java
- backend/src/main/resources/db/migration/control/V017__add_public_identity_profile_and_recovery.sql
- backend/src/test/java/com/comercioflex/RadioIdentityIntegrationTests.java
- backend/src/test/java/com/comercioflex/identity/infrastructure/IdentityRecoveryDeliveryTests.java
- docs/RADIO_FASE_2.md
- frontend/src/app/features/radio/radio-account-api.service.ts
- frontend/src/app/features/radio/radio-account.guard.ts
- frontend/src/app/features/radio/radio-account.scss
- frontend/src/app/features/radio/radio-auth-page.html
- frontend/src/app/features/radio/radio-auth-page.ts
- frontend/src/app/features/radio/radio-context.ts
- frontend/src/app/features/radio/radio-identity.spec.ts
- frontend/src/app/features/radio/radio-layout.ts
- frontend/src/app/features/radio/radio-private-page.ts

Modificados:

- backend/src/main/java/com/comercioflex/config/SecurityConfig.java
- backend/src/main/java/com/comercioflex/config/SpaForwardController.java
- backend/src/main/java/com/comercioflex/tenant/api/TenantResolutionFilter.java
- backend/src/main/resources/application.yml
- frontend/.gitignore
- frontend/package-lock.json
- frontend/package.json
- frontend/src/app/app.routes.spec.ts
- frontend/src/app/features/radio/radio-placeholder.ts
- frontend/src/app/features/radio/radio.routes.ts
- infra/production.env.example

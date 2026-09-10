# URLs públicas RADIO

Branch: `codex/radio-clean-urls`. Base: `origin/main` en `9627732`, con las Fases 1–5 integradas (incluye PR #41 y #42). Sin migraciones ni cambios comerciales.

## Rutas y compatibilidad

La URL pública de plataforma es `/{slug}`. Los children RADIO son Inicio, `programas`, `nosotros`, `socios`, `login`, `registro`, recuperación de contraseña y `mi-cuenta` con `plan`, `cuotas`, `perfil`, `pago-retorno`. `ingresar` permanece como alias de login. El admin sigue en `/tiendas/{slug}/admin`.

Las URLs públicas antiguas `/tiendas/{slug}` se redirigen con HTTP 302 sólo para RADIO. Angular también redirige navegaciones internas antiguas, conservando query y fragment. ECOMMERCE conserva `/tiendas/{slug}`. El 302 permite compatibilidad sin cachear permanentemente una decisión de tenant. No se añade un canonical global que pudiera apuntar todas las subpáginas a Inicio.

## Resolución y seguridad

`SpaForwardController` sólo entrega la SPA para rutas RADIO reconocidas y tenants activos de tipo RADIO, resueltos mediante `TenantResolver`. No usa el slug como nombre de base. Las rutas de API, recursos estáticos y administración conservan handlers específicos y prioridad. Un slug inexistente, inactivo o ECOMMERCE no abre una home RADIO.

`TenantResolutionFilter` no requiere cambios: Angular sigue llamando `/api/v1/stores/{slug}/...`; el filtro resuelve el tenant mediante el catálogo, abre `TenantContext` y lo limpia al finalizar. Los permisos administrativos siguen separados de la identidad del socio.

`TenantPublicPaths` centraliza las reglas backend: lowercase, letras/números separados por guiones, máximo 100 caracteres, sin traversal ni escapes. Valida creación en API y servicio. La unicidad continúa en la base de control. No existe edición de slug en el admin actual y no se renombran tenants existentes.

Prefijos reservados: `api`, `admin`, `superadmin`, `tiendas`, `stores`, `login`, `logout`, `auth`, `oauth`, `actuator`, `error`, `assets`, `static`, `index.html`, `favicon.ico`, `robots.txt`, `sitemap.xml`, `registro`, `ingresar`, `olvide-contrasena`, `nueva-contrasena`, `mi-cuenta`, `socios`, `programas`, `nosotros`, `carrito`, `checkout`, `mis-pedidos`, `pedidos`, `productos`, `payment-return`, `no-encontrado`. Angular descarta estos namespaces antes de consultar settings; backend sigue siendo la autoridad.

## Angular y branding

La ruta `:storeSlug` se registra después de las rutas específicas; el nombre del parámetro reutiliza `RadioContext` y `StorefrontRoutingService.storeSlug`. `radioCleanGuard` verifica tipo mediante settings y muestra una página no encontrada para fallos de resolución. No se duplica auth, servicios de membresía o pagos.

`RadioRoutingService` construye todos los links públicos; convierte el alias `ingresar` a `login`. Navbar, CTA y panel usan `RadioContext.link`. Logout vuelve al inicio del tenant. El layout aplica favicon, fuente y colores de branding al abrir directamente la URL.

## Pagos, recuperación y dominios

Checkout Pro genera `{frontendBaseUri}/{slug}/mi-cuenta/pago-retorno`. Un dominio primario verificado conserva prioridad y usa `https://{dominio}/mi-cuenta/pago-retorno`. Las preferencias ya creadas conservan su return URL guardada, compatible mediante redirect legacy. No cambia la acreditación, firma, webhook, reconciliación, idempotencia ni pagos ecommerce.

Los enlaces de recuperación usan el mismo esquema limpio o la raíz del dominio verificado. La resolución por hostname existente continúa antes del matching de slug en Angular; las APIs conservan el slug explícito.

## Nginx / Donweb

El proxy debe enviar las rutas navegables a Spring, manteniendo URI y query, por ejemplo `location / { proxy_pass http://backend; }` dentro de la configuración existente. No agregar un rewrite que quite el primer segmento. Si se sirven assets desde Nginx, resolver sólo archivos reales y enviar el resto a Spring para que verifique tenant y tipo. Un `try_files ... /index.html` incondicional pierde la validación HTTP 404 del backend. Conservar Host y la configuración existente de proxies confiables; no aceptar cabeceras forwarded arbitrarias.

No se modificó infraestructura productiva. Antes de publicar: verificar dominio/base pública, proxy, HTTPS, y abrir/refrescar Inicio, Programas y Mi cuenta en una radio y `/tiendas/{slug}` ecommerce. Revisar si algún tenant histórico usa un slug reservado antes de habilitarlo como RADIO público.

## Validación

La suite backend final pasó con 478 tests, 0 fallos, 0 errores y 1 omitido; `package` fue exitoso. Frontend pasó con 347 tests en 65 archivos y el production build fue exitoso. Docker build pasó con `comercio-flex:radio-clean-urls`. El E2E de Chrome pasó con navegación pública, registro/login, selección de plan, refresh, logout y comprobación responsive en 390, 768, 1366 y 1920 px; utiliza MySQL aislado y no realiza pagos reales. `git diff --check` devuelve código 0 y sólo advierte sobre normalización LF/CRLF.

## Inventario

Nuevos: `TenantPublicPaths.java`, `TenantPublicPathsTests.java`, `radio-clean.guard.ts`, `not-found-page.ts`, `radio-routing.service.ts`, `radio-clean-urls.spec.ts` y este documento.

Modificados backend: `SecurityConfig`, `SpaForwardController`, `IdentityRecoveryDelivery`, `MembershipCheckoutService`, `CreateCompanyRequest`, `CompanyProvisioningService`; pruebas `RadioMembershipIntegrationTests`, `MembershipPaymentSecurityTests`, `CreateCompanyTenantTypeTests`.

Modificados frontend: rutas principales y su spec, guards de experiencia y su spec, `RadioContext`, guard de cuenta, layout, rutas RADIO, spec de identidad y `e2e/radio-membership.cjs`.

No se modifican `TenantResolver`, `TenantResolutionFilter`, el catálogo de conexiones, las APIs internas, el webhook, los dominios ni los modelos comerciales.

# RADIO — Fase 4: pago manual mensual con Checkout Pro

La Fase 4 agrega pagos manuales por cuota para RADIO. Cada operación toma el snapshot persistido de `MembershipPeriod`; no usa el precio vigente de `MembershipPlan`, no crea `Order` y no implementa suscripciones, preapproval ni renovación automática.

## Base y migraciones

La rama parte de `origin/main` actualizado, commit `ee6264d`, con las Fases 1, 2 y 3 integradas. La rama de trabajo es `codex/radio-membership-payments`.

Las siguientes versiones libres fueron verificadas antes de crear migraciones:

- Control V018: `V018__route_radio_membership_payments.sql` agrega `membership_checkout_enabled` a `merchant_payment_capabilities` y `destination_type` a `payment_webhook_routes`.
- Tenant V026: `V026__create_membership_payments.sql` agrega el bloqueo de cuota y las tablas de intentos y pagos.

No se modifican migraciones aplicadas. La capability es explícita y queda deshabilitada por defecto para todos los tenants existentes.

## Modelo

`membership_payment_attempts` vive en cada tenant y contiene el período, clave idempotente, fingerprint, referencia externa opaca, ambiente, vendedor, preferencia, URL de retorno, estado técnico y diagnóstico. Sus estados son `CREATING`, `CREATED`, `UNKNOWN` y `FAILED`.

`membership_payments` registra cada pago observado de Mercado Pago, aunque un segundo pago aprobado para el mismo período no pueda aplicarse. Tiene unicidad por proveedor/ambiente/vendedor/ID externo y una restricción de aplicación única por período mediante columna generada. `provider_status` permanece separado de `MembershipPeriod.accreditation_status`.

`payment_locked_at` impide reemplazar el snapshot de una cuota desde que existe un intento. El cambio de plan de Fase 3 sigue permitido antes de iniciar un pago y deja de reemplazar el snapshot después.

## Checkout

El endpoint autenticado es:

`POST /api/v1/stores/{storeSlug}/me/membership/current-period/checkout-pro`

Acepta únicamente un objeto vacío. Obtiene el usuario desde la sesión, resuelve RADIO, verifica la membresía y el período mensual PENDING, comprueba que no esté cancelada, toma monto/moneda del snapshot, valida la capability y las credenciales del tenant, crea o reutiliza un intento y devuelve `checkoutUrl`.

La clave y el fingerprint atan tenant, período, importe, moneda, ambiente y seller. Un segundo request reutiliza el intento. Un timeout del proveedor marca `UNKNOWN`; no se genera una segunda preferencia automáticamente. La recuperación busca una única preferencia existente por referencia externa y valida sus ítems, importe, moneda, collector y período antes de asociarla.

Checkout Pro reutiliza `CheckoutProGateway`, `CheckoutPreferenceCommand`, `PaymentCredentialResolver`, OAuth, credenciales cifradas, SDK y configuración TEST/PRODUCTION existentes. No se usa `CheckoutProService`, `Order`, `PaymentApplicationService` ni tablas de payment intent de ecommerce.

Las URLs de retorno usan el dominio verificado del tenant cuando existe y, como fallback, la URL frontend configurada. El frontend permite navegación sólo a hosts HTTPS de Mercado Pago. Volver al navegador nunca acredita.

## Webhook y routing

La migración de control marca cada ruta como `ECOMMERCE_ORDER` o `RADIO_MEMBERSHIP`. El inbox y el worker existentes siguen procesando ecommerce; las rutas RADIO llaman `MembershipPaymentWebhookHandler` dentro del `TenantContext` correcto.

Antes de acreditar se valida firma HMAC, ventana de timestamp configurable (`app.payments.membership.signature-tolerance`, cinco minutos por defecto), `paymentId`, seller, ambiente, referencia externa, preferencia, importe, moneda, tenant y RADIO. El worker consulta el pago server-side mediante el gateway.

Sólo `APPROVED` con todas las validaciones correctas cambia el período de `PENDING` a `ACCREDITED` y registra `applied_at`. `PENDING`, `REJECTED` y `CANCELLED` quedan sin acreditar. `REFUNDED` y `CHARGED_BACK` se registran y quedan para revisión; no se revierte automáticamente la membresía en esta fase.

Webhook duplicado y notificaciones repetidas son idempotentes. Dos pagos externos distintos se conservan; sólo el primero puede aplicar el período y el siguiente queda con `SECOND_APPROVED_PAYMENT`. Una membresía cancelada no permite nuevos checkouts; un pago legítimo iniciado antes conserva auditoría y no reactiva la membresía.

## Frontend y administración

Mi cuenta muestra el botón `Pagar con Mercado Pago` sólo cuando la cuota está PENDING y la capability/credencial están disponibles. El botón bloquea doble clic, pide CSRF, no envía importe ni moneda y redirige a la URL validada. Muestra estados pagado, rechazado, pendiente, operación desconocida y proveedor no configurado.

`/mi-cuenta/pago-retorno` consulta el estado real del backend. Cuotas/Pagos muestra período, snapshot, estado de cuota, estado del proveedor y fecha; no expone IDs técnicos al socio. El administrador puede consultar pagos, intentos, preferencias y diagnósticos de un socio autorizado. La capability se administra sólo desde el endpoint OWNER con `MANAGE_PAYMENTS`.

## Seguridad y aislamiento

El usuario sólo puede iniciar el período actual de su `PaidMembership`. No puede enviar identidad, período, monto, moneda, seller, ambiente, tenant ni estado. El controller rechaza cuerpos no vacíos. CSRF continúa activo.

Cada webhook abre el tenant resuelto desde la ruta firmada en control. Se valida que el tenant sea RADIO, que la base y el seller coincidan, y que la referencia del intento corresponda al pago. Un UUID o payment de Radio A no puede acreditar Radio B. Los pagos comerciales no crean filas en la tabla administrativa `memberships`.

## Validación realizada

- Backend completo: `mvn -Dradio.browser=false package`, 441 tests, 0 fallos, 0 errores y 1 skip (el E2E opt-in).
- Integración RADIO/MySQL: 22 tests, 0 fallos, 0 errores y 1 skip; V024, V025 y V026 aplican y validan el aislamiento en MySQL.
- Tests backend de seguridad de pagos: 3 aprobados (fingerprint/tenant, seller/ambiente y URL de retorno).
- Frontend completo: 328 tests en 63 archivos, 0 fallos; tests específicos de pago: 4 aprobados.
- Build frontend production: correcto, bundle inicial aproximado de 333.35 kB.
- Build Docker: `comercio-flex:radio-membership-payments` generado correctamente.
- E2E real de navegador: 1 test aprobado en cuatro anchos de viewport; cubre alta de plan, registro, login, confirmación, persistencia, historial administrativo y estado de proveedor no configurado.
- `git diff --check`: sin errores.

La validación de pago aprobado contra Mercado Pago queda pendiente de credenciales sandbox y capability RADIO habilitada explícitamente. No se usan credenciales reales en tests.

## Riesgos y pasos manuales

1. Configurar OAuth/Checkout Pro y HTTPS público antes de habilitar `membership_checkout_enabled`.
2. Conectar Mercado Pago por tenant RADIO en el ambiente correspondiente.
3. Habilitar la capability sólo para radios piloto y verificar seller/ambiente.
4. Probar pagos rechazados, pendientes, reintentos, duplicados y conciliación antes de producción.
5. Definir posteriormente la política operativa para refunds/chargebacks y pagos aprobados posteriores a una cancelación.

No hay pagos recurrentes, débito automático, preapproval ni integración con Orders.

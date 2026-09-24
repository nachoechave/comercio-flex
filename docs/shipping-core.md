# Envíos propios — Fase 1

## Auditoría previa a la implementación

Base: `origin/main` actualizado, rama `feature/shipping-core`. El repositorio es
un monolito modular Spring Boot 3.5 / Java 21, con adaptadores JDBC y frontend
Angular 22 standalone. No se encontraron archivos AGENTS.md en el proyecto.

El checkout compartido por FASHION, FRESH y CATALOG usa CartService y
GuestOrderService. El carrito local no es autoridad: la creación bloquea variantes
y reservas, valida disponibilidad, calcula unitPrice × quantity con BigDecimal
y redondea cada línea a dos decimales. Reserva por 30 minutos en la transacción
tenant. La confirmación consume inventario mediante OrderTransitionExecutor.

Ya existe descuento por transferencia (V023): list_subtotal es el subtotal de
productos antes del descuento, discount_amount guarda el descuento y subtotal
es el importe neto de productos. Actualmente pagos usan ese último campo como
importe final. Shipping debe conservar esta semántica y agregar shipping_amount
y total. El umbral gratuito se evaluará sobre list_subtotal, antes del descuento
por transferencia; el descuento seguirá aplicándose sólo a productos.

Checkout Pro envía un único ítem por el importe del intento; recuperación y
webhooks comparan importe, moneda, vendedor y referencia. Transferencia usa el
pedido y conserva comprobantes privados, revisión y confirmación idempotente.
Es necesario actualizar todas las lecturas del importe, incluido QR ecommerce.
RADIO tiene módulos y pagos de membresía propios que no deben modificarse.

Las bases tenant contienen catálogo, inventario, pedidos, pagos y outbox. La
base control resuelve slug y membresías. TenantResolutionFilter abre el contexto;
tenantJdbcTemplate y tenantTransactionTemplate eligen la base sin fallback.
Flyway separa control/tenant. La rama anterior llegaba a V030; main actualizado
incluye V031 (hero_eyebrow), por lo que envíos utiliza V032.

El panel usa permisos de membresía; configuración puede reutilizar
MANAGE_BASIC_SETTINGS y operación MANAGE_ORDERS. CSRF sigue protegiendo escrituras
administrativas. La cotización pública es una consulta sin efectos comerciales.

Notification tiene outbox durable, claves únicas de evento, worker, lease,
retry/backoff, renderer HTML/text y branding tenant. Se reutilizará para despacho.
La entrega SMTP existente es al menos una vez; la idempotencia de encolado no
promete exactamente una entrega ante un fallo después de enviar.

Dashboard agrega pedidos e importes; no necesita una reescritura. Las pruebas
existentes son JUnit/MockMvc/Testcontainers con MySQL 8.4 y Angular/Vitest; CI
ejecuta suites y builds. La validación de esta fase debe cubrir migración,
aislamiento, concurrencia, inventario, pagos y RADIO además del nuevo recorrido.

## Implementación final — 23/09/2026

El checkpoint d7cc7e5 fue publicado antes de continuar el cierre de la fase.
Esta documentación describe el resultado final. No se realizó deploy ni merge.

### Implementado

- Módulo shipping con API, aplicación, dominio y repositorio JDBC tenant.
- PICKUP, FIXED_RATE, LOCATION_RATE y POSTAL_CODE_RATE; umbral gratuito opcional.
  Localidades y CP se comparan normalizando espacios, mayúsculas y acentos.
  No hay rangos de CP ni consultas geográficas externas.
- Quote con precios y stock resueltos en backend. Comparte priceCart con creación.
  Fórmula: listSubtotal - discountAmount + shippingAmount = total.
  subtotal conserva su significado anterior: productos después del descuento.
  El umbral gratuito usa listSubtotal antes del descuento por transferencia.
- Selección con expectedTotal: sirve para detectar cambios, nunca para fijar un
  importe. El backend recalcula y devuelve SHIPPING_CONFLICT si el total cambió.
- Dirección de entrega; los datos de nombre completo, teléfono, email y notas
  reutilizan los campos del pedido. PICKUP no requiere dirección del comprador.
- Snapshot JSON de método, tipo, costo, dirección e instrucciones.
- Lecturas de pago ecommerce (Checkout Pro, QR, transferencia y pago genérico)
  actualizadas para usar el total del pedido. No se alteró la seguridad del
  webhook ni los módulos de membresías.
- Shipment por pedido SHIPPING, con tracking y transportista manuales.
  Transiciones: PENDING → PREPARING → SHIPPED → DELIVERED.
  PENDING/PREPARING permiten CANCELLED; no hay retorno desde estados terminales.
  Se permiten actualizaciones del mismo estado con control de versión.
- Se exige pedido confirmado para preparar/despachar/entregar. La operación de
  envío conserva su estado separado del estado comercial del pedido.
- Email de despacho en la outbox existente, clave ORDER_SHIPPED por pedido,
  branding y templates HTML/text. Confirmación con productos, descuento, envío
  y total. No hay otro worker ni proveedor de emails.
- Checkout compartido por los templates, configuración administrativa,
  detalle del envío y actualización manual del seguimiento.
- MANAGE_BASIC_SETTINGS para tarifas; MANAGE_ORDERS para operación.
  Escrituras administrativas conservan CSRF y membresía tenant.
  Quote público sin efectos comerciales; nuevas APIs limitadas a ECOMMERCE.
- ShippingProvider como puerto de cotización. Sólo existe implementación interna.
  Proveedores reales y operaciones de etiquetas/tracking remoto quedan fuera.

### Persistencia y endpoints

V032__create_shipping_core.sql crea shipping_settings, shipping_methods,
shipping_rules y shipments. Agrega a orders shipping_amount (default 0),
shipping_snapshot (nullable) y total generado como subtotal + shipping_amount.
Amplía fulfillment_type conservando PICKUP histórico. V031 y anteriores no se
modifican. Configuración usa una versión y bloqueo tenant para evitar escrituras
perdidas y estabilizar la cotización durante la creación del pedido.

Rutas nuevas bajo /api/v1/stores/{storeSlug}:

- POST /shipping/quote
- GET y PUT /admin/shipping
- GET y PUT /admin/orders/{id}/shipment

POST /orders acepta shipping con methodId, address opcional y expectedTotal.
Las respuestas de pedido y administración agregan shippingAmount, total y el
snapshot shipping. El listado administrativo agrega shippingAmount y total.
Los clientes anteriores pueden omitir shipping sólo si sigue habilitado un
retiro gratuito; no se impone una tarifa desconocida a esos clientes.

Los modelos Angular están en features/shipping/shipping.models.ts. El checkout
invalida la selección al cambiar destino, carrito o medio de pago; ignora
respuestas obsoletas y exige una cotización nueva después de un conflicto.

### Ciclo comercial y logístico

Un pedido SHIPPING confirmado pasa a COMPLETED sólo cuando el envío está
DELIVERED. No utiliza READY_FOR_PICKUP. Una vez SHIPPED o DELIVERED se bloquea
la cancelación comercial para impedir reponer stock de mercadería despachada.
Cancelar/rechazar el pedido cancela su envío pendiente o en preparación; al
consultar un envío de un pedido vencido también se sincroniza CANCELLED.
Cancelar el shipment por sí solo no anula ni reembolsa el pedido: el comerciante
debe gestionar la cancelación comercial con el flujo existente.

Las escrituras de estados bloquean primero el pedido y después el shipment.
La versión protege la edición concurrente de tracking. Una URL de seguimiento
requiere HTTP/HTTPS, host y ausencia de credenciales; no se consulta remotamente.

### Validación final

- Suite backend completa: 501 casos, 0 fallos, 0 errores, 1 omitido. La omisión
  corresponde a RadioMembershipIntegrationTests condicionado a radio.browser.
  Incluye regresiones de pagos, reservas, tenants, outbox y RADIO.
- Tras los últimos ajustes: ShippingCoreTests, ShippingMigrationTests y
  GuestOrderIntegrationTests aprobados (39 casos). Maven package: BUILD SUCCESS.
- Suite frontend completa: 370 aprobadas y 1 omitida (responsive exige navegador),
  en 67 archivos. Chromium: 21 aprobadas en 3 archivos, incluida la prueba mobile
  a 390 × 844.
- Build frontend de producción aprobado. Advertencia preexistente:
  catalog-page-v2.scss ocupa 13,59 kB frente al presupuesto de 12 kB.
- La prueba de migración aplica V032 sobre V031 con pedido y transferencia
  existentes: conserva sus importes y agrega envío cero sin borrar datos.
- Integración del recorrido: configuración → quote → creación/reserva → pago
  confirmado → preparación → tracking → SHIPPED/outbox → DELIVERED → COMPLETED.
  Transferencia incluye comprobante PDF y aprobación repetida sin duplicar stock.
  Mercado Pago se verifica con sus lectores de importe y la infraestructura de
  pruebas del proyecto; no se realizaron cobros externos reales.
- Email de despacho probado con y sin tracking y sin duplicar el evento al repetir
  SHIPPED. Se verifica outbox y contenido, no entrega SMTP a una casilla real.
- Formato Java con google-java-format; frontend con Prettier; git diff --check.
- Docker/Testcontainers usa MySQL real. La suite completa finalizó BUILD SUCCESS
  con advertencias de cierre de contextos/pools después de detener contenedores.
  Un intento posterior sin Docker falló en inicialización y se repitió al iniciarlo.

### Archivos y operación

Backend nuevo: shipping/{api,application,domain,infrastructure},
order/application/OrderFulfillmentPolicy y migración tenant V032.
Adaptaciones: GuestOrderService, modelos/repositorios/respuestas de order,
lectores JDBC de payment, TenantResolutionFilter, SecurityConfig,
CustomerNotificationPublisher, OutboxCustomerNotificationService y templates.
Frontend: features/shipping/{shipping.models,shipping-selector,
shipping-settings-page,shipment-panel}, checkout, confirmación/historial de
pedidos, listado/detalle administrativo, navegación y rutas.
Las pruebas se encuentran en ShippingCoreTests, ShippingMigrationTests,
GuestOrderIntegrationTests y los specs de shipping y checkout.

El administrador configura tarifas en /admin/configuracion/envios. El cliente
completa contacto y dirección, elige medio de pago y consulta opciones (el medio
puede cambiar el descuento). Al seleccionar una opción ve productos, descuento,
envío y total. Confirmar vuelve a resolver precios, stock y tarifas en backend;
si cambian, el checkout exige cotizar de nuevo. El snapshot queda inmutable.

### Límites, riesgos y Fase 2

La dirección de retiro se copia de la configuración existente durante V032.
En comercios nuevos o sin dirección previa, el administrador debe completarla
en Envíos; el retiro inicial gratuito se mantiene por compatibilidad. Cambiar el
campo histórico de configuración general no modifica los métodos ni snapshots.
Consolidar ambos campos de configuración queda como deuda de UX.

No hay rangos de CP, geocodificación, historial específico de cambios de tracking
ni nuevos indicadores de dashboard. No se añadieron transportistas externos,
etiquetas, tracking automático ni devoluciones. Los estados de logística no
realizan reembolsos. La outbox evita encolados duplicados; SMTP conserva entrega
al menos una vez ante una caída posterior al envío.

ShippingProvider separa la cotización del core; InternalShippingProvider es la
única implementación. Fase 2 puede agregar adaptadores y capacidades de crear/
cancelar envíos, etiquetas y tracking remoto conservando snapshots, transacciones
por tenant y cálculo de importes en backend.

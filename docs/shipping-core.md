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

## Estado del commit de avance — 23/09/2026

El usuario pidió hacer commit y push hasta el avance actual. **Esta entrega es un
checkpoint, no la finalización de la fase 1.** No se realizó deploy ni merge.

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

### Validación realizada y pendiente

- Build frontend producción pasó en una versión intermedia del avance. Advertencia
  preexistente de presupuesto de catalog-page-v2.scss. Repetir para el commit final.
- Última suite frontend completa ejecutada: 369 pruebas aprobadas en 67 archivos.
  Después se agregó una prueba y el reinicio de cotización tras conflicto; falta
  volver a ejecutar la suite sobre ese último ajuste.
- Se agregó cobertura de quote, dirección, retiro, reglas, umbral, respuestas
  obsoletas, formularios administrativos, tracking, estados y CSRF del frontend.
- Backend compiló en la ejecución limpia con Testcontainers. La suite completa
  fue interrumpida para atender el pedido de checkpoint: no se declara aprobada.
- GuestOrderIntegrationTests completó 29 pruebas sin fallos ni errores, incluidos
  los nuevos recorridos de envío, pagos, stock, permisos y despacho.
- Se agregaron ShippingCoreTests, ShippingMigrationTests y recorridos a
  GuestOrderIntegrationTests para aislamiento, importes, snapshot, inventario,
  permisos, cambios de tarifas y despacho. Completar y revisar sus resultados.
- Primeras ejecuciones fallaron por Docker apagado y luego por numeración V031
  duplicada; se inició Docker y se cambió la migración nueva a V032.
- El intento de tests Chromium no ejecutó casos: falta el ejecutable de
  Playwright chromium_headless_shell-1243. No se declara validación visual/mobile.
- Prettier aplicado a componentes nuevos y checkout; repetir check tras los
  últimos ajustes. No hay formateador Java configurado en el proyecto.
- No se realizaron cobros reales ni envío real de emails. La validación de pagos
  utiliza las infraestructuras de prueba existentes.

### Trabajo restante y límites

Completar suite backend, pruebas de integración, suite frontend final, build
backend/frontend final, formato y revisión del diff. Revisar el recorrido completo
con navegador disponible y actualizar este documento con evidencia final.
Revisar también los mensajes de estado para pedidos SHIPPING y el ciclo de
cancelación/vencimiento frente al shipment operativo antes de considerar la fase
productiva. Hoy los estados comerciales y logísticos permanecen separados.
La configuración de retiro de envíos pasa a ser la fuente del snapshot; conviene
revisar la UX del campo histórico pickupAddress en configuración general.
No se añadieron indicadores de dashboard ni historial específico de cambios de
tracking. El dashboard conserva sus agregados comerciales existentes.

La outbox evita encolados duplicados; SMTP conserva su semántica existente de
entrega al menos una vez. No se promete exactamente una entrega en caso de caída
después de enviar. Fase 2 deberá ampliar el puerto con las capacidades de crear/
cancelar envíos, etiquetas y seguimiento remoto sin sustituir los snapshots ni
confiar en importes del navegador.

# Modelo de datos preliminar

> Estado: conceptual; estrategia de una base por comercio aprobada.

## Base de control

> Aprobada como registro central el 2026-07-23.

| Entidad | Responsabilidad |
|---|---|
| `tenants` | Comercio, slug, estado y referencia de conexión |
| `tenant_domains` | Paths/dominios habilitados y resolución |
| `tenant_database_registry` | Identificador lógico de la base y estado de migración, sin contraseñas |
| `platform_users` | Identidad global, credenciales y estado de la cuenta |
| `memberships` | Relación usuario-comercio, rol y estado de acceso |

Las credenciales de conexión no se guardarán en texto plano en esta base. Serán
secretos externos o valores cifrados con una clave externa.

En CORE-01, `tenants.database_key` es la referencia lógica implementada. No es una
URL ni un nombre de base proporcionado al navegador. La URL, usuario y contraseña
se resuelven contra configuración externa del backend. `tenant_database_registry`
permanece como evolución para metadatos operativos, no como almacén de secretos.

## Base de cada comercio

| Entidad | Responsabilidad |
|---|---|
| `store_settings` | Branding, contacto, moneda y configuración pública |
| `categories` | Organización del catálogo |
| `products` | Información común y publicación |
| `product_variants` | SKU, precio, unidad y opciones vendibles |
| `inventory`, `inventory_movements` | Existencia y trazabilidad de ajustes |
| `customers` | Compradores invitados asociados al comercio |
| `delivery_methods` | Retiro o envío |
| `orders`, `order_items`, `order_status_history` | Compra y fotografía histórica |
| `payments`, `payment_webhook_events` | Intentos, resultado e idempotencia |
| `merchant_payment_connections` | Conexión cifrada del comercio |
| `audit_events` | Acciones administrativas sensibles |

## Reglas

- Dinero usa `DECIMAL`, nunca punto flotante.
- Cantidad usa `DECIMAL(12,3)` candidato para soportar peso.
- Fechas se guardan en UTC.
- `order_items` conserva nombre, SKU, unidad y precio del momento de compra.
- Pedidos y pagos no se eliminan físicamente desde la aplicación.
- Imágenes se guardan fuera de MySQL; la base conserva URL y metadatos.
- Estados de pedido y pago son máquinas de estado separadas.
- La base seleccionada determina el comercio. Las tablas de negocio no necesitan
  `tenant_id` para aislamiento primario, aunque algunos registros de auditoría
  podrán conservar un identificador lógico del comercio.
- Ninguna operación usa un nombre de base recibido directamente desde el cliente.
- Todas las bases de comercio deben mantener la misma versión de migración.
- La sesión identifica un `platform_user`, pero una `membership` activa autoriza
  el acceso al comercio y determina el rol.
- Cada variante posee una única existencia en el MVP; no se modelan ubicaciones.
- Cada cambio de existencia genera un movimiento de inventario auditable.

## Relaciones principales

```text
Control DB: PlatformUser ── Membership ── Tenant ── DatabaseRegistry
                                      └────────────── Path

Tenant DB:
StoreSettings
├── Category ── Product ── ProductVariant ── Inventory
├── Customer
├── DeliveryMethod
├── Order ── OrderItem
│          └── Payment ── PaymentWebhookEvent
└── MerchantPaymentConnection
```

## Identificadores

Propuesta: `BIGINT` interno para índices y un `public_id` opaco para URLs públicas.
La alternativa es UUIDv7 como clave primaria. La decisión se aprobará antes de la
primera migración.

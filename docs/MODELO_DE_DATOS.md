# Modelo de datos

> Actualizado en CAT-01 el 2026-07-28. La estrategia de una base por comercio y
> el patrón de identificadores internos/públicos están aprobados.

## Base de control

> Aprobada como registro central el 2026-07-23.

| Entidad | Responsabilidad |
|---|---|
| `tenants` | Comercio, slug, estado y referencia de conexión |
| `tenant_domains` | Paths/dominios habilitados y resolución |
| `tenant_database_registry` | Identificador lógico de la base y estado de migración, sin contraseñas |
| `platform_users` | Identidad global, credenciales y estado de la cuenta |
| `memberships` | Relación usuario-comercio, rol y estado de acceso |
| `SPRING_SESSION` | Metadatos y vencimiento de sesiones web persistentes |
| `SPRING_SESSION_ATTRIBUTES` | Atributos mínimos asociados a cada sesión |

Las credenciales de conexión no se guardarán en texto plano en esta base. Serán
secretos externos o valores cifrados con una clave externa.

En CORE-01, `tenants.database_key` es la referencia lógica implementada. No es una
URL ni un nombre de base proporcionado al navegador. La URL, usuario y contraseña
se resuelven contra configuración externa del backend. `tenant_database_registry`
permanece como evolución para metadatos operativos, no como almacén de secretos.

### Identidad global

Modelo aprobado para CORE-02:

| Tabla | Campos conceptuales y restricciones |
|---|---|
| `platform_users` | `id`, `public_id` único, correo normalizado único, nombre visible, hash de contraseña, estado, fecha de cambio de contraseña y timestamps |
| `memberships` | `id`, FK a usuario, FK a tenant, rol, estado y timestamps; combinación usuario-tenant única |
| `SPRING_SESSION` | identificador opaco, creación, último acceso, vencimiento y referencia de principal |
| `SPRING_SESSION_ATTRIBUTES` | atributos serializados de la sesión con FK y borrado en cascada |

Estados iniciales:

- usuario: `ACTIVE`, `LOCKED`, `DISABLED`;
- membresía: `ACTIVE`, `INACTIVE`;
- rol: `OWNER`, `ADMIN`, `STAFF`.

El correo normalizado se usa para evitar dos cuentas equivalentes por diferencias
de mayúsculas o espacios. El hash de contraseña utiliza un algoritmo adaptativo
con identificador de formato; no existe una columna para contraseña plana. Los
intentos de login del limitador básico son transitorios y no forman parte del
modelo persistente del MVP.

La sesión guarda una identidad global mínima. La membresía y el rol se leen desde
la base de control al autorizar cada comercio, de modo que una revocación sea
inmediata. Las tablas de Spring Session se crean con una migración MySQL
versionada; la inicialización automática de esquema no reemplaza a Flyway.

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

### Categorías

`categories` se crea mediante la migración tenant
`V002__create_categories.sql` en cada base de comercio:

| Campo | Responsabilidad |
|---|---|
| `id` | `BIGINT` interno para relaciones e índices; nunca sale por la API |
| `public_id` | UUID almacenado como `BINARY(16)`, único y expuesto como `id` HTTP |
| `name` | Nombre visible, obligatorio y único dentro del comercio |
| `slug` | Dirección técnica única, generada al crear e inmutable al renombrar |
| `status` | `ACTIVE` o `INACTIVE`; representa archivado reversible |
| `created_at`, `updated_at` | Timestamps técnicos en UTC |

La tabla no contiene `tenant_id`: la base seleccionada después de autorizar la
membresía es el límite de aislamiento. El mismo nombre puede existir en bases de
comercios diferentes. Los índices únicos son la defensa final ante dos altas
concurrentes.

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
- Un usuario sólo puede tener una membresía por comercio.
- Una sesión expirada o invalidada no puede recuperar acceso mediante una cookie
  antigua.
- El primer `OWNER` se crea con un proceso operativo idempotente y secretos
  externos, nunca mediante datos sensibles versionados.
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

ADR-025 fija `BIGINT` interno para índices y relaciones más un `public_id` UUID
opaco para contratos y URLs. El patrón comienza en `categories` y se reutilizará
en las entidades comerciales posteriores, salvo que un ADR futuro justifique una
excepción.

# Modelo de datos

> Actualizado en INV-01 el 2026-07-29. Catálogo e inventario conservan el
> aislamiento mediante una base por comercio.

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
| `payment_intents`, `payment_transactions` | Intentos y resultados internos implementados en PAY-01A |
| `merchant_payment_connections`, `payment_webhook_events` | Conexión e inbox previstos para PAY-01B/C |
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

### Productos y variantes

La migración tenant `V003__create_products_and_variants.sql` crea:

| Tabla | Campos y reglas principales |
|---|---|
| `products` | `BIGINT` interno, UUID público, FK de categoría, nombre, slug único, descripción, estado, versión y timestamps |
| `product_variants` | `BIGINT` interno, UUID público, FK de producto, SKU único, `DECIMAL(15,2)`, talle, color, estado, versión y timestamps |

Estados de producto: `DRAFT`, `PUBLISHED`, `ARCHIVED`. Estados de variante:
`ACTIVE`, `INACTIVE`. Una restricción única evita repetir la combinación
normalizada de talle y color dentro del mismo producto. Las cadenas vacías de
talle y color representan la variante base.

Producto y variante tienen versiones independientes. La aplicación actualiza una
fila sólo cuando la versión enviada coincide y luego la incrementa. Las
operaciones que protegen la regla “publicado implica al menos una variante
activa” bloquean primero el producto para mantener un orden consistente entre
transacciones concurrentes.

### Inventario y movimientos

La migración tenant `V004__create_inventory.sql` incorpora:

| Tabla | Responsabilidad |
|---|---|
| `inventory_balances` | Balance materializado por variante, cantidad, versión y fecha de actualización |
| `inventory_movements` | Ledger append-only con operación idempotente, antes, delta, después, motivo y actor |

`inventory_balances.variant_id` es simultáneamente PK y FK a la variante. La
ausencia de fila se interpreta como cero y se materializa bajo bloqueo al aplicar
el primer ajuste.

Cada movimiento posee UUID público, una clave de idempotencia única dentro de la
base tenant y la versión de balance resultante. La combinación
`variant_id + balance_version` también es única. El movimiento guarda un snapshot
del UUID y nombre visible del actor porque no existen claves foráneas entre la
base tenant y la base de control.

Balance y movimiento cambian en una sola transacción. El balance optimiza
listados y futuras validaciones de pedidos; el ledger explica cómo se obtuvo.
Los movimientos no tienen `updated_at` porque no se editan ni eliminan.

## Lectura pública de catálogo

STORE-01 no agrega tablas. Construye un modelo de lectura mediante joins dentro
de la base tenant:

```text
Category ACTIVE
→ Product PUBLISHED
→ ProductVariant ACTIVE
→ LEFT JOIN InventoryBalance
→ priceFrom, priceTo y available
```

La ausencia de balance equivale a cero. La consulta conserva los productos
agotados, pero reduce la cantidad a un booleano público. UUID y slug atraviesan
la API; las claves `BIGINT`, SKU, versiones y cantidades permanecen internas.

## Reglas

- Dinero usa `DECIMAL`, nunca punto flotante.
- Cantidad usa `DECIMAL(15,3)` para soportar peso sin migrar el ledger.
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

### Modelo de pagos

PAY-01A implementa en cada base tenant `V008__create_payment_foundation.sql` y
`V009__enforce_case_sensitive_payment_currency.sql`. No modifica la base de
control.

#### `payment_intents`

Representa una intención interna de cobrar un pedido. Conserva UUID público,
pedido, clave idempotente, fingerprint SHA-256, clave estable para la transición
del pedido, proveedor, intento secuencial, importe, moneda, referencia externa,
estado y versión optimista.

Estados implementados:

```text
CREATED → PENDING
CREATED → REJECTED
CREATED → APPROVED
CREATED → REQUIRES_REVIEW
```

Un pedido puede tener varios intentos históricos, pero sólo después de que el
anterior haya sido rechazado. La aplicación bloquea primero `orders` y consulta
si existe `CREATED`, `PENDING`, `APPROVED` o `REQUIRES_REVIEW`. Además, la
columna generada `blocking_order_id` y su restricción `UNIQUE` garantizan esta
regla en MySQL aun ante concurrencia. La unicidad de `(order_id, attempt_number)`
protege la secuencia de intentos.

Son únicos `public_id`, `idempotency_key`, `transition_idempotency_key`,
`external_reference` y `(order_id, attempt_number)`.

#### `payment_transactions`

Conserva cada resultado normalizado del proveedor: UUID, intento, identificador
externo único dentro de su proveedor, estado, importe, moneda, instante de
aplicación y marca de revisión.
No guarda payloads externos, datos de tarjeta ni credenciales.

Estados implementados: `PENDING`, `APPROVED` y `REJECTED`. Una transacción
aprobada puede quedar aplicada o marcada para revisión, nunca ambas. La consulta
por `(provider, provider_payment_id)` usa `FOR UPDATE`. Si dos pedidos compiten
por el mismo identificador, la restricción compuesta de MySQL elige un ganador;
el perdedor se traduce a conflicto de dominio y su intento queda para revisión.
Las monedas se validan distinguiendo mayúsculas y minúsculas.

```text
orders
└── payment_intents
    └── payment_transactions
```

PAY-01B agregará `merchant_payment_connections` y estado OAuth en la base de
control. PAY-01C agregará preferencias reales, routing de notificaciones e inbox
`payment_webhook_events`. Esas tablas continúan como diseño futuro y no deben
considerarse implementadas en PAY-01A.

PAY-01A sólo acepta el primer resultado `CREATED → resultado`. La evolución
externa `PENDING → APPROVED` se implementará en PAY-01C mediante eventos de
webhook idempotentes; no se simula como un replay inmutable en esta entrega.

El contrato `CredentialCipher` y su adaptador AES-256-GCM ya existen, pero todavía
no persisten tokens. El ciphertext futuro deberá guardar además nonce, `key_id` y
contexto autenticado. El AAD usa el identificador público e inmutable del tenant,
proveedor, ambiente, sujeto, campo y `key_id`; las claves provendrán
exclusivamente del entorno.
- Un balance nunca es negativo y no puede exceder la capacidad del decimal.
- La disponibilidad comercial futura no debe confundirse con existencia física:
  INV-01 registra cantidad en mano y todavía no modela reservas.

## Carrito local

CART-01 no agrega tablas ni migraciones. Persiste en el navegador una versión
del formato, identificadores públicos de producto y variante, nombres visibles,
talle, color, precio como string decimal y cantidad entera. El `storeSlug` forma
parte de la clave de almacenamiento, no de cada línea.

Ese snapshot es descartable y no constituye un pedido. No contiene cliente,
dirección, stock exacto, SKU, secretos ni identificadores internos. Al abrir el
carrito Angular revalida contra el catálogo; ORD-01 no confía en este snapshot.

## Pedidos y reservas

`orders` conserva el UUID público, identificador interno, clave idempotente,
fingerprint SHA-256, hash del token privado, estado, retiro, datos de contacto
como snapshot, moneda, subtotal y vencimiento.

`order_items` conserva producto, variante, nombre, SKU operativo, opciones,
precio, cantidad, unidad y total de línea. La confirmación pública no expone el
SKU. `inventory_reservations` relaciona pedido y variante con cantidad, estado y
vencimiento.

```text
disponible
= inventory_balances.quantity
− SUM(reservas ACTIVE cuyo expires_at todavía no venció)
```

Crear un pedido no modifica el balance físico. Pedido, items y reservas se
insertan en una única transacción. Las conexiones tenant fijan UTC para que Java
y MySQL comparen el mismo instante sin depender de la zona del host.

ORD-02 agrega `version` a `orders` y crea `order_status_history`. Cada entrada
guarda estado anterior, nuevo estado, actor, nota y fecha. La clave idempotente de
la transición impide repetir un efecto sobre stock.

```text
Confirmar
→ reserva ACTIVE pasa a CONSUMED
→ inventory_balances disminuye
→ inventory_movements registra ORDER_CONFIRMED

Cancelar después de confirmar
→ inventory_balances aumenta
→ inventory_movements registra ORDER_CANCELLED
→ reserva CONSUMED pasa a RELEASED
```

`inventory_movements.order_id` relaciona movimientos automáticos con el pedido.
Rechazar o vencer libera la reserva sin modificar el balance físico.
La migración V007 agrega una unicidad por `(order_id, new_status)`. Como el MVP
no permite ciclos, un mismo pedido no puede registrar dos veces el mismo estado,
incluso ante carreras entre vencimiento, consulta y operación administrativa.

## Relaciones principales

```text
Control DB: PlatformUser ── Membership ── Tenant ── DatabaseRegistry
                                      └────────────── Path

Tenant DB:
StoreSettings
├── Category ── Product ── ProductVariant ── Inventory
│                              └── InventoryReservation
├── Customer
├── DeliveryMethod
├── Order ── OrderItem
│     └── InventoryReservation
│          └── Payment ── PaymentWebhookEvent
└── MerchantPaymentConnection
```

## Identificadores

ADR-025 fija `BIGINT` interno para índices y relaciones más un `public_id` UUID
opaco para contratos y URLs. El patrón comienza en `categories` y se reutilizará
en las entidades comerciales posteriores, salvo que un ADR futuro justifique una
excepción.

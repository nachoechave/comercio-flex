# Estructura del proyecto

> Actualizado durante DASH-01 el 2026-08-03. El monolito modular incluye
> identidad, routing tenant, catálogo, inventario, pedidos, pagos y dashboard.

```text
comercio-flex/
├── backend/                # API Spring Boot y migraciones
├── frontend/               # SPA Angular
├── infra/                  # MySQL local con Docker Compose
├── docs/                   # Producto, arquitectura y aprendizaje
├── .gitignore              # Archivos que Git no debe versionar
├── .gitattributes          # Finales de línea consistentes
└── README.md               # Entrada al repositorio
```

## Frontend

```text
frontend/
├── public/                         # Archivos estáticos públicos
├── proxy.conf.json                 # Proxy local Angular → Spring Boot
└── src/app/
    ├── core/
    │   ├── auth/                   # Sesión global, CSRF, guards e interceptor
    │   ├── health/                 # Cliente del health check backend
    │   └── routing/                # Lectura reactiva de parámetros de rutas tenant
    ├── layouts/
    │   ├── storefront-layout/      # Marco de la tienda pública
    │   └── admin-layout/           # Marco del panel administrativo
    ├── features/
    │   ├── auth/                   # Pantalla de login global
    │   ├── storefront/
    │   │   ├── home/               # Página pública inicial
    │   │   ├── catalog/            # Catálogo, filtros y paginación
    │   │   ├── product-detail/     # Variantes y alta al carrito
    │   │   ├── cart/               # Estado local, revalidación y página del carrito
    │   │   ├── checkout/           # Creación del pedido invitado
    │   │   └── order-confirmation/ # Consulta privada del pedido creado
    │   └── admin/
    │       ├── categories/         # Listado, formulario y API de categorías
    │       ├── inventory/          # Balance, ajustes e historial por variante
    │       ├── products/           # Lista, alta, detalle, edición y API de productos
    │       ├── dashboard/          # Métricas operativas y umbral de stock bajo
    │       ├── payment-connection/ # Conexión OAuth exclusiva de OWNER
    │       └── store-selector/     # Selector para usuarios con varias membresías
    ├── shared/ui/status-pill/      # UI reutilizable sin negocio
    ├── app.config.ts               # Proveedores globales
    └── app.routes.ts               # Rutas lazy
```

Reglas de dependencia:

- `features` puede usar `core` y `shared`.
- `shared` no conoce features ni reglas de negocio.
- Una feature no importa detalles internos de otra.
- Los guards futuros ayudan a la navegación; el backend aplica la seguridad real.

## Backend

```text
backend/src/main/
├── java/com/comercioflex/
│   ├── config/                     # Seguridad, datasources y migraciones
│   ├── identity/
│   │   ├── api/                    # Contrato HTTP de sesión, login y logout
│   │   ├── application/            # Autenticación y autorización de casos de uso
│   │   ├── domain/                 # Usuario, membresía, roles y estados
│   │   └── infrastructure/
│   │       └── control/            # Persistencia de identidad en la base central
│   ├── catalog/
│   │   ├── api/                    # Contrato HTTP y Problem Details de categorías
│   │   ├── application/            # Casos de uso, normalización y puertos
│   │   ├── domain/                 # Categoría y estado del dominio
│   │   └── infrastructure/jdbc/    # Persistencia en la base tenant seleccionada
│   ├── inventory/
│   │   ├── api/                    # Contratos HTTP y Problem Details de stock
│   │   ├── application/            # Transacción, idempotencia y reglas de ajuste
│   │   ├── domain/                 # Balance, movimiento, dirección y motivo
│   │   └── infrastructure/jdbc/    # Locks, balances, ledger y listados tenant
│   ├── order/
│   │   ├── api/                    # Contratos públicos y administrativos de pedidos
│   │   ├── application/            # Checkout, operación y transición compartida
│   │   ├── domain/                 # Pedido, estado, item y forma de entrega
│   │   └── infrastructure/jdbc/    # Pedidos, reservas, historial y stock tenant
│   ├── payment/
│   │   ├── api/                    # Conexión OAuth y callback fijo
│   │   ├── application/            # Casos de uso, puertos y cifrado
│   │   ├── domain/                 # Intento, proveedor y resultados de pago
│   │   └── infrastructure/
│   │       ├── control/            # OAuth y conexiones en control DB
│   │       ├── crypto/             # AES-GCM sin claves embebidas
│   │       ├── fake/               # Proveedor determinista sólo para pruebas
│   │       ├── jdbc/               # Intentos y transacciones en la base tenant
│   │       └── mercadopago/        # OAuth remoto tipado; Checkout en evolución
│   ├── dashboard/
│   │   ├── api/                    # GET del resumen y PUT del umbral
│   │   ├── application/            # Ventanas horarias y modelos agregados
│   │   └── infrastructure/jdbc/    # Consultas tenant de ventas, pedidos y stock
│   ├── tenant/
│   │   ├── api/                    # Endpoint, filtro y errores HTTP
│   │   ├── application/            # Resolución, contexto y caso de consulta
│   │   └── infrastructure/
│   │       ├── control/            # Entity/repository de la base central
│   │       └── routing/            # Pools y selección de conexión tenant
│   └── shared/                     # Tipos transversales mínimos
└── resources/
    ├── application.yml             # Configuración común sin secretos
    ├── application-local.yml       # Comportamiento del perfil local
    └── db/migration/
        ├── control/                 # Esquema de la base central
        └── tenant/                  # Esquema idéntico para cada comercio
```

`catalog` contiene categorías, productos y variantes; `inventory` registra
existencias y movimientos sin modificar publicación ni precio. `order` coordina
reservas y operación; `payment` conserva intentos financieros sin exponerlos aún
por HTTP. Los módulos `customer`, `delivery` y `reporting` se crearán al comenzar
sus historias.
No se agregan carpetas vacías sólo para simular avance.

Dentro de un módulo de negocio se utilizarán, cuando hagan falta:

```text
api → application → domain
          ↑
infrastructure
```

`api` no accede directamente a JPA y `domain` no depende de HTTP.

## Infraestructura

```text
infra/
├── compose.yaml
├── .env.example
├── README.md
└── mysql/init/
    └── 01-create-development-databases.sh
```

Compose crea una instancia MySQL con tres bases lógicas:

- `comercio_flex_control`;
- `comercio_flex_tenant_a`;
- `comercio_flex_tenant_b`.

El volumen local no es un backup. Los secretos reales nunca se versionan.

## Flujo actual del health check

```text
Navegador
→ StorefrontHome de Angular
→ HealthService
→ proxy local /actuator
→ Spring Security permite health
→ Spring Boot Actuator
→ respuesta {"status":"UP"}
→ estado accesible en la pantalla
```

## Flujo implementado de resolución multiempresa

```text
Usuario
→ GET /api/v1/stores/{slug}/settings
→ TenantResolutionFilter
→ TenantResolver
→ TenantRepository
→ base de control
→ database_key lógica
→ TenantContext
→ TenantRoutingDataSource
→ base MySQL del comercio
→ StoreSettingsResponse
→ Usuario
```

Al finalizar, `TenantContext.Scope.close()` elimina la clave del hilo aunque haya
una excepción. `api` depende de `application`; `application` usa el puerto
`TenantConnectionCatalog`; `infrastructure` implementa ese puerto. La capa de
aplicación no conoce URLs JDBC ni contraseñas.

## Flujo futuro completo

```text
Componente Angular
→ servicio Angular
→ API REST
→ Controller
→ caso de uso
→ dominio
→ Repository
→ base MySQL del comercio resuelto
→ DTO
→ Angular
```

## Flujo implementado de categorías

```text
CategoryList o CategoryForm
→ CategoryApiService
→ proxy local /api
→ sesión y CSRF
→ TenantResolutionFilter valida comercio y membership
→ TenantPermissionAuthorizationManager valida VIEW_CATALOG o MANAGE_CATALOG
→ AdminCategoryController
→ CategoryService abre la transacción tenant
→ JdbcCategoryRepository
→ TenantRoutingDataSource
→ tabla categories de la base del comercio
→ CategoryResponse con UUID público
→ Angular actualiza el estado visible
```

`catalog` usa las capacidades de autorización de `identity` y la conexión ya
seleccionada por `tenant`. No conoce la base de control, credenciales JDBC ni
componentes Angular. El frontend nunca envía `tenant_id` ni `database_key`.

## Flujo implementado de productos

```text
ProductList, ProductForm o ProductDetail
→ ProductApiService
→ API paginada /admin/products
→ sesión, CSRF, membership y permiso
→ AdminProductController
→ ProductService valida y coordina la transacción
→ ProductValidator aplica reglas del agregado
→ JdbcProductRepository
→ products y product_variants en la base tenant
→ DTO con UUID, versiones y precio string
→ Angular actualiza la vista
```

El alta confirma producto y variantes como una unidad. La versión evita
sobrescrituras silenciosas y los bloqueos de fila preservan las reglas de
publicación ante operaciones concurrentes.

## Flujo implementado de inventario

```text
InventoryList, InventoryDetail o StockAdjustmentForm
→ InventoryApiService
→ sesión, CSRF y permisos de inventario
→ AdminInventoryController
→ InventoryService abre transacción tenant
→ bloquea variante y balance
→ verifica Idempotency-Key
→ calcula resultado y valida no negativo/capacidad
→ JdbcInventoryRepository actualiza balance y agrega movimiento
→ respuesta con cantidades string
→ Angular muestra balance e historial
```

`inventory` consulta metadatos de catálogo mediante joins de lectura en su propio
adaptador JDBC. No importa `JdbcProductRepository` ni expone claves internas.

## Flujo implementado de tienda pública

```text
StorefrontLayout
→ StorefrontContextService obtiene configuración de la tienda
→ CatalogPage conserva q, categoría y página en la URL
→ StorefrontApiService
→ GET /api/v1/stores/{slug}/catalog/**
→ TenantResolutionFilter
→ PublicCatalogController
→ PublicCatalogService
→ JdbcPublicCatalogRepository
→ products, categories, product_variants e inventory_balances
→ DTO público
→ ProductCard o PublicProductDetail
```

Dentro de `frontend/src/app/features/storefront/`, `catalog/` coordina el
listado, `product-card/` representa cada resultado y `product-detail/` presenta
las variantes. Estos componentes pueden depender de los modelos, servicios y
contexto públicos del mismo feature, pero no deben importar features
administrativas.

`cart/` contiene el modelo, `CartService` y la página pública del carrito. Puede
depender del contrato público de storefront, pero no conoce controllers,
entidades ni repositorios backend. `CartService` es la única pieza que accede a
`localStorage`; los componentes piden operaciones de negocio en lugar de leer o
escribir claves directamente.

### Flujo del carrito local

```text
Detalle público selecciona variante
→ CartService valida cantidad y disponibilidad
→ actualiza signal por storeSlug
→ persiste snapshot mínimo en localStorage
→ cabecera y página reaccionan al mismo estado
→ CartPage relee cada producto con StorefrontApiService
→ CartService actualiza precio/opciones o marca la línea
```

El flujo termina en el navegador: CART-01 no crea pedidos ni movimientos de
inventario. ORD-01 agregará la frontera Angular → Spring Boot → MySQL y repetirá
la validación de forma autoritativa.

## Flujo implementado de checkout invitado

`frontend/src/app/features/storefront/checkout/` contiene el formulario y
orquesta el alta. `order-confirmation/` consulta y presenta el resultado. Ambas
dependen de los contratos y servicios públicos de `storefront`, no de features
administrativas.

`backend/src/main/java/com/comercioflex/order/` contiene:

- `api`: DTO públicos, controller y errores HTTP;
- `application`: caso de uso, validación, idempotencia y puerto de persistencia;
- `domain`: pedido, items, estados y tipo de entrega;
- `infrastructure/jdbc`: consultas e inserciones sobre la base tenant.

```text
CheckoutPage
→ CsrfService
→ StorefrontApiService + Idempotency-Key
→ POST /api/v1/stores/{slug}/orders
→ TenantResolutionFilter abre la base del comercio
→ GuestOrderController
→ GuestOrderService abre una transacción tenant
→ JdbcGuestOrderRepository bloquea variantes
→ MySQL calcula saldo físico − reservas vigentes
→ orders + order_items + inventory_reservations
→ respuesta con UUID público y token privado
→ Angular vacía el carrito
→ OrderConfirmationPage consulta UUID + token
```

`order` puede leer tablas de catálogo e inventario mediante su adaptador JDBC
para aplicar la regla transaccional, pero no depende de controllers ni de los
repositorios concretos de esos módulos. `catalog` sólo considera reservas al
calcular disponibilidad pública.

## Flujo administrativo de pedidos

`frontend/src/app/features/admin/orders/` contiene contratos, servicio HTTP,
listado y detalle. Sólo depende de utilidades compartidas y de la API; no conoce
SQL ni reglas de transición.

```text
OrderDetail
→ OrderApiService + Idempotency-Key
→ AdminOrderController valida MANAGE_ORDERS
→ AdminOrderService bloquea el pedido
→ JdbcAdminOrderRepository
→ orders + order_status_history
→ inventory_reservations
→ inventory_balances + inventory_movements
→ respuesta actualizada al panel
```

El controller no decide transiciones y el repositorio no acepta actores enviados
por Angular. El caso de uso coordina toda la operación dentro de una transacción
tenant.

## Flujo interno de pagos de PAY-01A

PAY-01A no agrega controllers ni componentes Angular. El flujo se activa sólo
desde pruebas mediante una instancia explícita del proveedor falso.

```text
Prueba de integración abre TenantContext
→ PaymentApplicationService bloquea el pedido
→ JdbcPaymentRepository crea o recupera payment_intent
→ commit antes de invocar PaymentGateway
→ FakePaymentGateway devuelve APPROVED, PENDING o REJECTED
→ nueva transacción bloquea order → intent → transaction
→ PaidOrderConfirmer reutiliza OrderTransitionExecutor
→ orders + reservations + balances + movements + history
→ payment_intents + payment_transactions
```

`payment.application` no depende del adaptador falso ni de JDBC. El puerto
`PaymentGateway` permite reemplazar el doble de prueba por Mercado Pago en una
entrega posterior. `OrderTransitionExecutor` contiene las reglas compartidas de
stock, pero no abre transacciones; tanto administración como pagos lo llaman
dentro de la transacción tenant correspondiente.

`OrderPaymentPolicy` es el puerto que permite a pedidos preguntar si una
confirmación manual o una cancelación está permitida. Su adaptador
`JdbcOrderPaymentPolicy` vive en pagos: así el repositorio de pedidos no conoce
tablas ajenas a su módulo.

Las restricciones de MySQL son la última barrera ante dos solicitudes
simultáneas. La aplicación traduce colisiones de clave y deadlocks esperables a
conflictos del negocio; nunca repite la llamada externa en un replay y marca para
revisión un resultado externo que no pudo aplicarse de forma segura.

Los datos de pago permanecen en la base ya seleccionada por `TenantContext`.
No existe fallback, búsqueda global de intentos, endpoint simulador ni secreto
versionado.

## Flujo de conexión OAuth de PAY-01B

```text
PaymentConnectionPage (sólo OWNER)
→ PaymentConnectionApiService
→ PaymentConnectionController valida MANAGE_PAYMENTS
→ MerchantPaymentConnectionService crea state + PKCE
→ JdbcMerchantOAuthRepository guarda el intento en control DB
→ Mercado Pago autoriza y vuelve al callback fijo
→ MercadoPagoOAuthClientAdapter intercambia el código
→ GET /users/me verifica user_id y obtiene nickname público
→ AesGcmCredentialCipher cifra access token y refresh token
→ merchant_payment_connections en control DB
→ Angular consulta el estado real
→ “Conectada a: nickname”
```

`payment.application` define casos de uso y puertos; no depende de HTTP, JDBC ni
DTO externos. `payment.infrastructure.mercadopago` conoce el contrato remoto y
`payment.infrastructure.control` conoce las tablas de control. El feature
Angular sólo consume la API pública y no puede leer material cifrado.

## Estructura implementada para PAY-01C

PAY-01C reutiliza el módulo `payment`; no crea un microservicio. Las
responsabilidades nuevas se separan así:

- `payment/api`: inicio público, consulta del resultado y receptor webhook;
- `payment/application`: preferencia, token de retorno, validación autoritativa,
  reclamo/reintento del inbox y coordinación idempotente;
- `payment/domain`: estados de entrega del webhook y habilitación comercial;
- `payment/infrastructure/control`: inbox y resolución global de conexión;
- `payment/infrastructure/jdbc`: correlación y efectos dentro de la base tenant;
- `payment/infrastructure/mercadopago`: SDK oficial detrás de `PaymentGateway` y
  verificación de firma sin exponer DTO externos.

Angular incluye la pantalla `payment/payment-return-page`, los servicios
`PaymentApiService`, `PaymentRecoveryService` y `CheckoutProNavigationService`, y
extiende confirmación/checkout para iniciar la preferencia. Esa pantalla no
depende del feature administrativo `payment-connection`.

PAY-01D amplía las mismas fronteras sin crear carpetas transversales nuevas:

- `PaymentWebhookMetrics` instrumenta resultados con etiquetas cerradas;
- `PaymentWebhookOperationsService` lista y reprograma eventos `DEAD` mediante el
  puerto `CheckoutControlRepository`;
- `PaymentWebhookOperationsController` publica la operación exclusiva de `OWNER`;
- `JdbcCheckoutControlRepository` mantiene aislamiento tenant/ambiente, fencing
  por intento y auditoría atómica;
- `payment-connection-page` incorpora la vista operativa y cancela solicitudes al
  cambiar de tenant.
- la vista consulta el `timezone` público de `store_settings` para presentar los
  instantes UTC en la zona del comercio, sin duplicar ese dato en el inbox.

```text
OrderConfirmationPage
→ API de inicio con lookupToken + Idempotency-Key
→ backend verifica conexión + habilitación + pedido
→ SDK crea preferencia con datos del servidor
→ Angular navega al init_point en la misma pestaña
→ PaymentReturnPage recibe token opaco
→ polling acotado consulta estado normalizado

WebhookController
→ validador de firma y timestamp
→ repositorio de inbox en control DB
→ worker con lease/retry/DEAD
→ PaymentGateway consulta el pago real
→ repositorio tenant aplica transacción idempotente

OWNER → PaymentConnectionPage
→ API administrativa tenant-safe
→ control DB lista un evento DEAD sanitizado
→ confirmación manual con CSRF
→ auditoría + RETRY en una transacción
→ worker retoma el flujo idempotente
```

Dependencias prohibidas:

- el receptor webhook no abre directamente tablas tenant antes de persistir;
- Angular no conoce firma, access token, seller ID interno ni payload remoto;
- el worker no mantiene simultáneamente una transacción control y otra tenant;
- `domain` no importa SDK, Spring MVC, JDBC ni DTO de Mercado Pago;
- la pantalla pública de resultado no depende de componentes administrativos.

Dentro de `backend/.../catalog`, las clases `Public*` forman una frontera de
lectura pública. Pueden depender del dominio y de la abstracción de repositorio,
pero nunca de Angular ni de los controllers administrativos. El adaptador JDBC
no debe aceptar un nombre de base proveniente del cliente.

## Flujo de autenticación y autorización de CORE-02

```text
LoginComponent
→ AuthService obtiene XSRF-TOKEN
→ POST /api/v1/auth/login con X-XSRF-TOKEN
→ Spring Security verifica credenciales
→ PlatformUserRepository consulta control DB
→ Spring Session guarda la sesión en control DB
→ Angular consulta /api/v1/auth/session
→ AuthService conserva usuario y memberships en memoria
→ selector navega al slug elegido
→ guard verifica el estado de navegación
→ backend valida nuevamente membership y rol
→ recién entonces TenantRoutingDataSource abre la base del comercio
```

Dependencias permitidas:

- `identity` puede consultar `tenant` en la base de control para describir y
  autorizar membresías, pero no accede a datos de negocio tenant.
- `tenant` no conoce componentes Angular ni acepta roles provenientes del cliente.
- `core/auth` puede ser usado por features y layouts; no depende de una pantalla
  administrativa concreta.
- `features/auth` usa `core/auth` y `shared`, pero ninguna feature de negocio debe
  importar detalles internos de la pantalla de login.

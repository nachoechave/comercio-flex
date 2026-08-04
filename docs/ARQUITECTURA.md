# Arquitectura propuesta

> Estado: decisiones principales aprobadas el 2026-07-23

## Vista general

Se propone un monolito modular: una aplicación Angular, una API Spring Boot y una
base MySQL. Es un despliegue sencillo, pero el código se divide por dominios para
evitar un monolito desordenado.

```text
Navegador
  → Angular (tienda pública / administración)
  → API REST Spring Boot
  → módulos de aplicación y dominio
  → adaptadores JPA / Mercado Pago
  → MySQL / API de Mercado Pago
```

## Módulos backend

- `identity`: credenciales, sesión, roles y permisos.
- `tenant`: comercio, configuración y capacidades activadas.
- `catalog`: categorías, productos, variantes, precios y publicación.
- `inventory`: existencias y movimientos.
- `customer`: datos mínimos del comprador.
- `order`: pedidos, ítems, totales, estados e historial.
- `delivery`: retiro y envío.
- `payment`: intentos, proveedor, webhooks e idempotencia.
- `reporting`: consultas simples, de solo lectura.
- `shared`: tipos transversales mínimos; no debe ser un cajón de sastre.

Dentro de cada módulo:

```text
api → application → domain
          ↑
infrastructure (implementa puertos)
```

Un módulo no accede directamente a repositorios o entidades internas de otro.

## Frontend

Una SPA Angular con componentes standalone y rutas lazy para dos shells:
tienda pública y administración. Organización por funcionalidad:

```text
page → componente UI → data-access/store → HttpClient → API
```

Signals administrará estado local y RxJS las operaciones HTTP. No se propone NgRx
para el MVP. Los guards mejoran la experiencia, pero la autorización real siempre
la aplica el backend.

## Multiempresa

Se aprobó una estrategia de **base de datos separada por comercio**. En MySQL,
`SCHEMA` y `DATABASE` son equivalentes. También se aprobó una base de control
compartida para registrar y resolver los comercios:

- una base de control compartida para identificar comercios, estado, slug,
  enrutamiento de conexión y metadatos operativos;
- una base de negocio por comercio para catálogo, inventario, clientes, pedidos y
  pagos;
- un router de conexiones backend que elige la base después de resolver el comercio;
- Flyway aplicado de forma controlada y verificable a todas las bases de comercio.

Aunque la separación física reduce el impacto de una consulta sin filtro, el
backend seguirá validando el tenant y nunca aceptará un identificador enviado por
el navegador como autoridad. Las pruebas con comercios A y B continúan siendo
obligatorias. La caché y el carrito frontend también se separan por comercio.

Consecuencias aceptadas: mayor aislamiento y restauración individual, a cambio de
más conexiones, provisión, migraciones, monitoreo, backups y costo operativo.

### Routing implementado en CORE-01

- La base de control usa JPA y permanece separada del acceso a datos de negocio.
- Un slug `ACTIVE` se traduce a una `database_key` lógica.
- Esa clave sólo puede resolverse contra la allowlist cargada desde configuración
  externa del backend.
- Cada conexión tenant tiene un pool Hikari independiente y acotado.
- `AbstractRoutingDataSource` no tiene datasource por defecto y usa
  `lenientFallback=false`.
- El contexto vive en un `ThreadLocal` encapsulado y se elimina con `remove()` en
  un bloque garantizado.
- El acceso tenant comienza después de establecer el contexto y usa su propio
  límite transaccional.

```text
slug público
  → SELECT en control.tenants con estado ACTIVE
  → database_key lógica
  → allowlist externa de conexiones
  → contexto de la solicitud
  → datasource tenant
  → transacción de negocio
  → limpieza del contexto
```

No hay atomicidad distribuida entre la base de control y una base tenant. En el
MVP se evita diseñar operaciones que necesiten confirmar ambas bases dentro de una
misma transacción. Las tareas `@Async`, schedulers y paralelismo con contexto
tenant quedan prohibidos hasta definir propagación y limpieza explícitas.

En desarrollo todavía se comparte un usuario runtime con permisos sobre control,
A y B. Antes de un cliente real se crearán usuarios de mínimo privilegio separados
por base para agregar una segunda barrera ante un error de routing.

## Identidad y autorización

La identidad es global y reside en la base de control. Una `membership` vincula a
un usuario con un comercio y su rol. La sesión identifica al usuario, pero no
autoriza por sí sola el acceso a una base de negocio.

```text
Cookie de sesión
  → recuperar sesión JDBC desde la base de control
  → identificar usuario global activo
  → resolver comercio por path en la base de control
  → comprobar membresía activa y permiso
  → obtener referencia de conexión confiable
  → seleccionar base del comercio
  → ejecutar operación
  → limpiar contexto del tenant
```

El backend no aceptará un nombre de base, identificador interno de tenant ni rol
enviado por el navegador como fuente de autoridad.

### Sesión web

Spring Session JDBC persiste las sesiones en la base de control. Esto permite que
una sesión sobreviva al reinicio de una instancia y sea reconocida por más de una
réplica. La cookie de sesión es opaca y `HttpOnly`; la contraseña, el rol activo y
las credenciales tenant no se guardan en el navegador.

La sesión conserva la identidad global mínima. Las membresías y sus roles se
consultan en la base de control al autorizar una operación tenant. De este modo,
deshabilitar un usuario o una membresía tiene efecto sin esperar a que expire una
lista de permisos almacenada en la sesión.

La sesión expira después de 30 minutos de inactividad según ADR-023. El valor se
configura con `SESSION_TIMEOUT`; no existe una cookie persistente de “Recordarme”
en el MVP.

### Login global y selección

```text
Angular inicia la aplicación
  → obtiene token CSRF
  → consulta GET /api/v1/auth/session
  → si es anónimo, presenta el login global
  → si está autenticado, recibe membresías activas
  → una membresía: navega directamente
  → varias membresías: el usuario elige el comercio
  → cada llamada administrativa incluye el slug en la URL
  → el backend vuelve a autorizar usuario + membership + rol
```

La selección frontend es navegación, no una fuente de autoridad. El datasource
tenant sólo se abre después de comprobar la membresía.

### Matriz fija de roles del MVP

| Capacidad | `OWNER` | `ADMIN` | `STAFF` |
|---|---:|---:|---:|
| Ver dashboard operativo | Sí | Sí | No |
| Gestionar categorías, productos y precios | Sí | Sí | No |

CAT-01 implementa la primera parte de `catalog`: `STAFF` puede consultar
categorías mediante `VIEW_CATALOG`, mientras `OWNER` y `ADMIN` necesitan
`MANAGE_CATALOG` para crear, renombrar, archivar o restaurar. La autorización se
ejecuta antes del controller y se repite como defensa en el caso de uso HTTP.

CAT-02 extiende el mismo módulo con el agregado producto-variante. El producto es
la raíz que coordina publicación y variantes; el alta completa usa una única
transacción tenant. Producto y variante tienen versiones optimistas
independientes. Las operaciones concurrentes que podrían dejar un publicado sin
variantes activas bloquean primero la fila del producto y validan la invariante
dentro de la transacción.

INV-01 agrega un módulo `inventory` separado de `catalog`. Catálogo define qué
variante existe; inventario mantiene su balance físico y ledger. El estado
comercial no altera automáticamente la existencia. El repositorio de inventario
puede hacer joins de lectura con tablas de catálogo, pero no depende de sus
adaptadores internos.

STORE-01 agrega una lectura pública dentro del módulo `catalog`, con controller,
DTO y repositorio dedicados. No reutiliza los DTO administrativos: la frontera
pública omite SKU, cantidades, versiones y estados internos. El repositorio
público puede unir `products`, `categories`, `product_variants` e
`inventory_balances` porque todas pertenecen a la misma base tenant, pero expone
sólo disponibilidad booleana.

```text
URL /tiendas/{slug}
→ Angular obtiene settings y catálogo
→ TenantResolutionFilter resuelve un tenant ACTIVE
→ PublicCatalogController
→ PublicCatalogService
→ JdbcPublicCatalogRepository
→ base MySQL exclusiva del comercio
→ DTO público sin datos administrativos
```

Los endpoints públicos son anónimos únicamente para `GET`; las rutas
`/admin/**` conservan sesión, membresía y permisos. El catálogo usa `no-store`
y el futuro checkout vuelve a validar toda información comercial.

CART-01 mantiene estado exclusivamente en Angular. `CartService` usa signals
para compartir carrito y contador, y una clave versionada de `localStorage` por
`storeSlug` para sobrevivir recargas. El contenido local nunca es autoridad:

```text
Snapshot local no confiable
→ validación estructural y de límites
→ relectura del detalle público por producto
→ actualización de nombre, opciones y precio
→ marca AVAILABLE, UNAVAILABLE o UNKNOWN
→ subtotal sólo de líneas AVAILABLE
```

No se almacenan correo, dirección, credenciales ni tokens. Tampoco se descuenta
stock. Aunque CART-01 muestre un precio actualizado, ORD-01 deberá volver a leer
variante, precio y saldo dentro de la transacción que crea el pedido.

Los ajustes bloquean la variante y el balance dentro de una transacción tenant.
La misma transacción valida idempotencia, no negatividad y capacidad decimal,
actualiza el balance e inserta el movimiento. La integración futura con pedidos
usará un puerto interno; no habrá llamadas HTTP entre módulos del monolito.
| Consultar catálogo e inventario | Sí | Sí | Sí |
| Ajustar stock | Sí | Sí | Sí |
| Ver pedidos y cambiar estados permitidos | Sí | Sí | Sí |
| Cambiar configuración básica del comercio | Sí | Sí | No |
| Gestionar conexión de pagos | Sí | No | No |
| Gestionar membresías y propiedad | Sí | No | No |

La interfaz oculta acciones no disponibles para mejorar la experiencia, pero el
backend aplica esta matriz en todos los casos. Los permisos configurables quedan
fuera del MVP.

### CSRF, CORS y cookies

- Las operaciones que cambian estado mediante una sesión autenticada requieren el
  par `XSRF-TOKEN` / `X-XSRF-TOKEN`. Los contratos públicos del checkout invitado
  no dependen de sesión y aplican idempotencia, tokens opacos, validación y
  aislamiento tenant según ADR-109.
- La cookie de sesión usa `HttpOnly`, `SameSite=Lax` y `Secure` en ambientes con
  HTTPS; el perfil local puede desactivar `Secure` para `http://localhost`.
- CORS admite credenciales sólo desde orígenes explícitos, nunca con wildcard.
- Login y logout también están protegidos por CSRF.
- El identificador de sesión se renueva después de autenticar y se invalida en el
  logout.

El login aplica un límite básico, acotado en memoria, por IP confiable y correo
normalizado. Es apropiado para la instancia única del MVP, pero no comparte
contadores entre réplicas. Un limitador distribuido y Redis requieren una decisión
futura sustentada por el despliegue y la carga.

### Provisión y recuperación

El primer `OWNER` se crea mediante un proceso operativo idempotente, deshabilitado
por defecto y alimentado por variables de entorno. No se versionan contraseñas ni
hashes reutilizables. La recuperación autoservicio por correo pertenece a V2; el
piloto tendrá una rotación operativa controlada.

## Inventario del MVP

Cada variante vendible tiene una única existencia dentro de la base del comercio.
Los movimientos registran aumentos y disminuciones con motivo, fecha y actor. El
modelo no incluye sucursales, depósitos ni transferencias en el MVP; estas
capacidades quedan preparadas como evolución explícita y no como complejidad
anticipada.

## Pagos

El dominio depende de un puerto `PaymentGateway`; Mercado Pago es un adaptador.
Checkout Pro reduce la superficie de datos de tarjeta. El backend recalcula el
importe, crea la preferencia y valida el pago servidor a servidor. El retorno del
navegador nunca confirma el pedido. Webhooks firmados e idempotentes actualizan el
estado después de verificar cuenta, referencia, moneda e importe.

Diseño incremental aprobado para PAY-01. PAY-01A y PAY-01B construyen la base
interna y la conexión; PAY-01C se encuentra en desarrollo:

```text
Pedido PENDING_CONFIRMATION
→ PaymentIntent local
→ PaymentGateway falso u oficial
→ Checkout Pro alojado
→ inbox MySQL de webhooks
→ consulta servidor a servidor
→ validación de vendedor, referencia, preferencia, moneda e importe
→ APPROVED confirma el pedido y consume stock una sola vez
```

Una aplicación OAuth de Comercio Flex conectará cada cuenta vendedora mediante
Authorization Code, `state` de un uso y PKCE. Los tokens se cifrarán en backend y
se aplicarán por solicitud; no existirá un token global mutable. El SDK oficial
quedará encapsulado detrás de `PaymentGateway`, de modo que dominio, pruebas y
proveedor falso no dependan de Mercado Pago.

PAY-01B concreta la conexión OAuth en la base de control. El callback fijo exige
la misma sesión OWNER que inició el intento, resuelve tenant y ambiente desde un
`state` server-side y verifica que el `user_id` del token coincida con el `id` de
`GET /users/me`. Sólo persiste ese identificador y el `nickname` público; los dos
tokens se cifran con AES-256-GCM y AAD ligada a tenant, ambiente, conexión y
campo. Una cuenta vendedora activa no puede pertenecer a dos tenants del mismo
ambiente.

La aplicación OAuth es central y pertenece a Comercio Flex. Sus `Client ID` y
`Client Secret` se configuran una sola vez como secretos del despliegue y nunca
se solicitan desde Angular. Cada vendedor sólo presta consentimiento; los tokens
resultantes siguen asociados y cifrados de manera independiente por tenant.

El refresh se realiza bajo demanda, con bloqueo de la conexión y reemplazo
atómico de access y refresh token. Un rechazo definitivo borra secretos y marca
`REAUTHORIZATION_REQUIRED`; una indisponibilidad transitoria no destruye una
conexión válida. No hay scheduler ni cobros reales en esta entrega.

La inbox persistente acepta y deduplica la notificación antes de procesarla con
reintentos. No requiere Kafka ni otro servicio. Una aprobación posterior al
vencimiento queda `REQUIRES_REVIEW`; una cancelación cobrada se bloquea mientras
el producto no implemente reembolsos.

### Fronteras de PAY-01C

La conexión técnica y la habilitación comercial son condiciones independientes:

```text
Conexión OAuth CONNECTED
+ habilitación comercial ACTIVE
+ pedido y reserva elegibles
→ crear preferencia con credencial del vendedor
→ Angular abre init_point HTTPS en la misma pestaña
```

Una credencial vendedora TEST central es una excepción operativa limitada a un
único tenant demo. No reemplaza el OAuth por comercio y la configuración debe
impedir su uso en `PRODUCTION`.

El retorno del navegador lleva a una ruta específica con un token opaco y
vencible. Angular consulta por tiempo limitado; no interpreta `status`,
`payment_id` ni otros parámetros de Mercado Pago como prueba de pago. Al agotarse
el polling conserva un estado neutral y ofrece actualización manual.

El webhook cruza primero una frontera global porque todavía no existe un contexto
tenant confiable:

```text
POST HTTPS de Mercado Pago
→ límites de tamaño y formato
→ firma + timestamp obligatorios en TEST/PRODUCTION
→ inbox en control DB y respuesta rápida 200/201
→ worker reclama evento con lease
→ resuelve conexión y tenant
→ consulta autoritativa a Mercado Pago con token del vendedor
→ valida seller + ambiente + referencia + preferencia + importe + moneda
→ transacción en tenant DB aplica pago/pedido/stock idempotentemente
→ control DB marca PROCESSED
```

El inbox usa `RECEIVED`, `PROCESSING`, `RETRY`, `PROCESSED` y `DEAD`,
con intentos, `next_attempt_at`, lease y error sanitizado. No existe transacción
distribuida entre control y tenant: si la aplicación cae después del commit tenant
y antes del cierre global, el worker repite y las restricciones por pago externo
y transición impiden repetir el efecto comercial.

La firma autentica la notificación, pero el cuerpo sigue sin ser autoridad de
negocio. Sólo se persisten metadatos mínimos y un fingerprint de entrega; no se
guardan payloads completos, tokens ni PII para depurar. Los secretos de firma TEST
y producción provienen del entorno y se rotan mediante un procedimiento
controlado.

Observabilidad prevista: conteos de recibidos, firma inválida, duplicados,
reintentos y `DEAD`; edad del evento más antiguo; latencias del worker y del
proveedor. Los logs utilizan IDs públicos y códigos sanitizados, nunca headers de
autorización, firmas, tokens, query completa o cuerpo del proveedor.

## Versiones aprobadas y candidatas verificadas el 2026-07-23

- Angular 22 + Node 24 LTS: compatibles según la tabla oficial de Angular.
- Spring Boot 3.5 + Java 21 LTS: línea aprobada por estabilidad.
- MySQL 8.4 LTS.
- Flyway y Maven; versiones exactas se fijarán al inicializar.

Fuentes: [Angular](https://angular.dev/reference/versions),
[Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html),
[MySQL 8.4](https://dev.mysql.com/doc/refman/8.4/en/).

## Flujo del dashboard MVP

```text
OWNER/ADMIN
→ AdminDashboard Angular
→ DashboardApiService
→ GET /admin/dashboard
→ filtro tenant + permiso VIEW_DASHBOARD
→ DashboardService calcula ventanas en la zona del comercio
→ JdbcDashboardRepository agrega pedidos, historial e inventario
→ MySQL de ese comercio
→ DTO con importes/cantidades canónicas
→ tarjetas y lista de stock crítico
```

El dashboard no mantiene una base analítica ni caché en el MVP. Lee agregados de
la base tenant bajo demanda. Esto evita duplicar datos mientras el volumen es
pequeño; métricas históricas complejas podrán usar proyecciones en una versión
posterior sin convertir ahora el sistema en microservicios.

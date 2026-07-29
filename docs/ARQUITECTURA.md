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

- Las operaciones que cambian estado requieren el par `XSRF-TOKEN` /
  `X-XSRF-TOKEN`.
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

## Versiones aprobadas y candidatas verificadas el 2026-07-23

- Angular 22 + Node 24 LTS: compatibles según la tabla oficial de Angular.
- Spring Boot 3.5 + Java 21 LTS: línea aprobada por estabilidad.
- MySQL 8.4 LTS.
- Flyway y Maven; versiones exactas se fijarán al inicializar.

Fuentes: [Angular](https://angular.dev/reference/versions),
[Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html),
[MySQL 8.4](https://dev.mysql.com/doc/refman/8.4/en/).

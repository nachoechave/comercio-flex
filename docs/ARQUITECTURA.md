# Arquitectura actual

## Estilo arquitectónico

Comercio Flex utiliza un **monolito modular**. Una SPA Angular y una aplicación
Spring Boot se construyen como un único artefacto desplegable y comparten origen
HTTPS. No es una arquitectura de microservicios.

La modularidad busca separar responsabilidades y permitir evolución interna sin
sumar red, consistencia distribuida y operación de múltiples servicios antes de
que exista una necesidad comprobada.

```text
Navegador
  → Angular: storefront / admin / auth / superadmin
  → API REST Spring Boot
  → módulos de aplicación y dominio
  → adaptadores JDBC, storage, SMTP y Mercado Pago
  → MySQL / Cloudflare R2 / Resend / Mercado Pago
```

## Backend

Los módulos reales bajo `com.comercioflex` son:

- `tenant`: resolución del comercio, settings, branding, provisioning y routing;
- `identity`: usuarios, sesión, membresías, roles y autorización;
- `catalog`: categorías, productos, variantes, opciones, precios y publicación;
- `inventory`: balances, movimientos, recepción y stock bajo;
- `order`: checkout invitado, reservas, pedidos, estados e historial;
- `payment`: Mercado Pago y transferencia bancaria;
- `media`: procesamiento y almacenamiento de imágenes;
- `notification`: templates, outbox y envío de emails;
- `dashboard`: agregados operativos por comercio;
- `platformadmin`: administración global de tenants;
- `shared` y `config`: infraestructura transversal acotada.

La organización preferida dentro de cada módulo es:

```text
api → application → domain
          ↑
infrastructure implementa puertos de aplicación
```

Los módulos colaboran mediante casos de uso y puertos internos, no mediante HTTP
entre partes del mismo proceso.

## Frontend

Angular usa componentes standalone, signals para estado local y RxJS para flujos
HTTP. Las rutas principales se cargan de forma diferida y se organizan en:

- `core`: sesión, routing y servicios transversales;
- `features/storefront`: tienda pública, carrito, checkout, pagos y pedidos;
- `features/admin`: operación del comercio;
- `features/auth`: autenticación;
- `features/superadmin`: administración de plataforma;
- `layouts`: shells de storefront, admin y superadmin;
- `shared`: pipes, modelos y UI reutilizable.

Los guards mejoran navegación y UX; la autorización real siempre la aplica el
backend.

## Multi-tenancy

El modelo vigente combina una base de control con una base separada por tenant.

### Base de control

Contiene identidad global, sesiones, tenants, membresías, estado, metadatos de
routing, provisioning, conexiones de pago y bandeja de webhooks global cuando el
flujo lo necesita.

### Base por tenant

Contiene settings, catálogo, imágenes como metadata, inventario, pedidos,
reservas, pagos y outbox de notificaciones del comercio.

```text
storeSlug
  → lookup en control DB
  → tenant ACTIVE + database_key interna
  → pool Hikari registrado
  → TenantContext de la solicitud
  → transacción en una sola base tenant
  → limpieza garantizada del contexto
```

`AbstractRoutingDataSource` no utiliza un datasource por defecto. El navegador no
envía nombres de base ni claves JDBC como autoridad, y los contratos públicos no
exponen esos datos.

### Ventajas y costos

Ventajas:

- mayor aislamiento de datos;
- menor riesgo de contaminación accidental por consultas sin filtro;
- backup/restore y mantenimiento individual por comercio;
- posibilidad de mover tenants sin cambiar el modelo de dominio.

Costos:

- más pools y conexiones;
- Flyway debe coordinarse en todas las bases;
- provisioning, observabilidad y backups son más complejos;
- no existe una transacción ACID única entre control y tenant.

Por estos costos, el modelo no se presenta como una solución infinita. Las
operaciones que cruzan control y tenant usan estados recuperables e idempotencia.

## Identidad y autorización

- La sesión se persiste por JDBC en control DB y se entrega mediante cookie
  `HttpOnly`, `Secure` y `SameSite=Lax` en producción.
- `OWNER`, `ADMIN` y `STAFF` pertenecen a un tenant mediante membresías.
- `SUPER_ADMIN` es un rol global separado y no obtiene acceso implícito a los
  datos de un comercio.
- CSRF protege operaciones basadas en sesión.
- Un tenant suspendido deja de resolver tanto storefront como panel.

## Catálogo e inventario

Una variante es la unidad vendible: posee SKU, precio, opciones genéricas y
balance de stock. `Talle` y `Color` son opciones compatibles, no columnas que
limitan el modelo futuro.

El stock no se sobrescribe directamente:

```text
inventory_balances
  + inventory_movements append-only
  + inventory_reservations temporales
```

Cada recepción, salida o ajuste registra un movimiento auditable con actor,
motivo e idempotencia. Crear un pedido reserva; confirmar consume; cancelar o
vencer libera. La disponibilidad pública es booleana y no expone cantidades
exactas.

## Pedidos y carrito

El carrito se persiste en `localStorage` con una clave por `storeSlug`; es un
snapshot descartable y se revalida contra backend. El checkout invitado nunca
confía en precio o disponibilidad enviados por el navegador.

“Mis pedidos” conserva localmente identificadores y lookup tokens asociados al
comercio, pero consulta siempre el backend para obtener el estado actual. El
token no se muestra en UI ni se considera una sesión de cliente.

## Pagos

Existen dos flujos independientes.

### Mercado Pago

```text
pedido
  → preferencia Checkout Pro
  → navegador en Mercado Pago
  → webhook firmado / retorno acotado
  → consulta server-to-server al proveedor
  → validación de seller, referencia, importe y moneda
  → aplicación idempotente sobre pedido y stock
```

La redirección del navegador no confirma un pago. Los webhooks se reciben en un
inbox durable, se procesan con lease y pueden reintentarse sin duplicar efectos.
Los tokens de vendedor se cifran y nunca se devuelven al frontend.

### Transferencia bancaria

```text
pedido
  → intento de transferencia
  → instrucciones privadas
  → comprobante en storage privado
  → revisión administrativa
  → aprobación o rechazo
```

La aprobación confirma y consume la reserva mediante reglas idempotentes. Un
rechazo no consume stock, registra el motivo y permite un nuevo comprobante si
el pedido sigue siendo elegible. Este flujo no crea transacciones de Mercado
Pago.

## Notificaciones

Los emails usan una outbox tenant para desacoplar el efecto comercial de la
entrega SMTP:

```text
evento de dominio
  → transactional_email_outbox
  → worker por lotes y lease
  → renderer HTML + text/plain con branding tenant
  → adaptador SMTP
  → Resend
```

Actualmente existen templates para confirmación de pedido y comprobante bancario
rechazado. La clave de evento evita encolados duplicados; los fallos temporales
usan reintentos y backoff. La entrega SMTP es al menos una vez, por lo que un
crash después de enviar puede producir una repetición excepcional. En ningún
caso un error de email revierte pedido, pago o inventario.

## Medios y objetos privados

`ProductImageService` verifica y recodifica JPEG/PNG, corrige orientación y genera
una versión de exhibición y una miniatura. MySQL conserva metadata y claves; los
bytes viven fuera de la base.

En producción Cloudflare R2 se usa mediante puertos compatibles con S3. Los usos
se separan por función:

- media pública controlada de productos y branding;
- comprobantes bancarios privados, servidos sólo tras autorización;
- backups privados operados fuera del runtime de la aplicación.

MySQL y R2 no comparten transacción; los servicios aplican compensaciones y deben
contar con procedimientos de reconciliación/recuperación acordes al riesgo.

## Despliegue productivo

```text
Internet
  → Cloudflare: DNS y edge HTTPS
  → DonWeb Cloud Server
  → Easypanel
  → contenedor Angular + Spring Boot
  → MySQL 8.4 / R2 / Resend / Mercado Pago
```

El `Dockerfile` multi-stage compila frontend y backend, y ejecuta Java 21 con un
usuario sin privilegios. Liveness y readiness están expuestos mediante Actuator;
los detalles sensibles no forman parte de la respuesta pública.

`railway.json` permanece como artefacto legacy de una alternativa anterior. No
describe el deployment principal y puede eliminarse en una tarea de limpieza si
se confirma que no hay consumidores externos.

## Backups y recuperación

La copia debe abarcar control DB y todas las bases tenant, y coordinarse con los
objetos privados. El script del repositorio crea dumps consistentes de InnoDB,
valida gzip y aplica retención local configurable; la programación y copia a R2
pertenecen a la operación del entorno.

Un backup no se considera validado hasta realizar una restauración de prueba. El
restore debe ejecutarse primero en una base aislada y validar Flyway, tenants,
pedidos, inventario y referencias a objetos.

## CI y calidad

GitHub Actions ejecuta exactamente:

1. `npm ci`, suite frontend y build Angular;
2. suite backend con Maven;
3. Docker build sin publicación, después de frontend y backend.

Las integraciones de persistencia usan Testcontainers con MySQL 8.4. CI no hace
deploy ni reemplaza smoke tests, observabilidad o restore drills.

## Documentos relacionados

- [`API.md`](API.md): contratos HTTP.
- [`MODELO_DE_DATOS.md`](MODELO_DE_DATOS.md): tablas e invariantes.
- [`REGISTRO_DE_DECISIONES.md`](REGISTRO_DE_DECISIONES.md): contexto histórico.
- [`GUIA_DE_DESPLIEGUE.md`](GUIA_DE_DESPLIEGUE.md): operación conceptual actual.

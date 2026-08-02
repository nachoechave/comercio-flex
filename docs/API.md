# API

> Actualizado durante INV-01 el 2026-07-29. Los contratos de autenticación,
> catálogo e inventario administrativo están documentados.

## Health check

```http
GET /actuator/health
```

No requiere autenticación.

Respuesta saludable:

```json
{
  "status": "UP"
}
```

El endpoint no expone detalles internos.

## Configuración pública de una tienda

```http
GET /api/v1/stores/{slug}/settings
```

No requiere autenticación. El `slug` localiza el comercio en la base de control;
no representa un nombre de base ni permite elegir una conexión.

Respuesta:

```json
{
  "slug": "tienda-a",
  "storeName": "Tienda A",
  "currencyCode": "ARS",
  "timezone": "America/Argentina/Buenos_Aires"
}
```

Si el comercio no existe, está inactivo o no tiene una conexión configurada:

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json
```

```json
{
  "type": "https://comercio-flex.local/problems/store-not-found",
  "title": "Tienda no encontrada",
  "status": 404,
  "detail": "No existe una tienda activa para la dirección solicitada.",
  "instance": "/api/v1/stores/no-existe/settings"
}
```

Headers, query params o body con `database_key`, URL JDBC o nombres de base no se
usan para el routing. Los endpoints administrativos permanecen cerrados salvo los
contratos de autenticación incorporados en CORE-02. No se exponen entidades
JPA como respuestas HTTP.

## Autenticación administrativa

La autenticación es global: primero identifica a la persona y luego informa sus
membresías activas. La presencia de una sesión no autoriza por sí sola el acceso a
una tienda.

### Obtener token CSRF

```http
GET /api/v1/auth/csrf
```

Es público y materializa la cookie legible por Angular `XSRF-TOKEN`. Las
solicitudes que cambian estado deben copiar su valor al header
`X-XSRF-TOKEN`. Esta cookie no contiene la sesión ni reemplaza la cookie
`HttpOnly` de autenticación.

También devuelve los nombres del header y parámetro junto con el token. La
interfaz usa el convenio de cookies; el cuerpo facilita diagnóstico y clientes
que no sean un navegador.

### Iniciar sesión

```http
POST /api/v1/auth/login
Content-Type: application/json
X-XSRF-TOKEN: <token>
```

```json
{
  "email": "persona@ejemplo.com",
  "password": "valor-no-registrado-en-logs"
}
```

Una autenticación válida renueva el identificador de sesión y devuelve el estado
del usuario con sus membresías activas. Un fallo usa una respuesta genérica y no
confirma si el correo existe. El endpoint está sujeto a un límite de intentos.

### Consultar la sesión

```http
GET /api/v1/auth/session
```

El bootstrap anónimo responde `200`:

```json
{
  "authenticated": false
}
```

Una sesión válida responde `200` con identidad y membresías:

```json
{
  "authenticated": true,
  "user": {
    "id": "identificador-opaco",
    "email": "persona@ejemplo.com",
    "displayName": "Ana"
  },
  "memberships": [
    {
      "storeSlug": "tienda-a",
      "storeName": "Tienda A",
      "role": "OWNER"
    }
  ]
}
```

La respuesta no incluye hashes, identificadores internos, `database_key` ni
credenciales. Una membresía sirve para presentar o seleccionar una tienda; cada
endpoint administrativo vuelve a autorizarla en el servidor.

### Cerrar sesión

```http
POST /api/v1/auth/logout
X-XSRF-TOKEN: <token>
```

Invalida la sesión en el servidor. Reutilizar su cookie después del logout no debe
dar acceso.

## Administración de categorías

Todas las rutas requieren sesión, membresía activa en `{storeSlug}` y autorización
backend. `OWNER` y `ADMIN` pueden escribir; `STAFF` sólo puede consultar. Las
mutaciones requieren `X-XSRF-TOKEN`.

### Listar

```http
GET /api/v1/stores/{storeSlug}/admin/categories?status=ALL
```

`status` admite `ALL`, `ACTIVE` o `INACTIVE`; el panel usa `ALL` por defecto.

```json
[
  {
    "id": "8ab5aef2-85f2-4ced-b864-1077ee1fd69c",
    "name": "Remeras",
    "slug": "remeras",
    "active": true,
    "createdAt": "2026-07-28T16:00:00Z",
    "updatedAt": "2026-07-28T16:00:00Z"
  }
]
```

### Crear

```http
POST /api/v1/stores/{storeSlug}/admin/categories
Content-Type: application/json
X-XSRF-TOKEN: <token>
```

```json
{
  "name": "Remeras de Niño"
}
```

Responde `201 Created`, header `Location` y la categoría. El backend normaliza
espacios, genera `remeras-de-nino` y nunca acepta un slug enviado por el cliente.

### Consultar y renombrar

```http
GET /api/v1/stores/{storeSlug}/admin/categories/{categoryId}
PUT /api/v1/stores/{storeSlug}/admin/categories/{categoryId}
```

El `PUT` recibe `{"name":"Remeras infantiles"}`. Renombrar no cambia el slug.

### Archivar o restaurar

```http
PATCH /api/v1/stores/{storeSlug}/admin/categories/{categoryId}/status
Content-Type: application/json
X-XSRF-TOKEN: <token>
```

```json
{
  "active": false
}
```

`false` archiva y `true` restaura. No existe borrado físico de categorías en el
MVP.

### Errores de categorías

- `400 Bad Request`: nombre ausente o inválido, con `errors.name` cuando aplica.
- `401 Unauthorized`: no existe una sesión válida.
- `403 Forbidden`: falta CSRF, membresía o permiso.
- `404 Not Found`: comercio o UUID público inexistente en la base seleccionada.
- `409 Conflict`: nombre o slug duplicado.

La API sólo expone el UUID público. No recibe ni devuelve `tenant_id`, clave
interna `BIGINT`, `database_key`, URL JDBC ni credenciales.

## Administración de productos y variantes

Todas las rutas parten de:

```http
/api/v1/stores/{storeSlug}/admin/products
```

Requieren sesión y membresía activa. `OWNER` y `ADMIN` pueden modificar;
`STAFF` sólo puede consultar. Toda mutación requiere CSRF.

### Listar con paginación

```http
GET /api/v1/stores/{storeSlug}/admin/products?page=0&size=20&status=ALL&categoryId={uuid}&q=remera
```

`size` admite de 1 a 100, `page` de 0 a 1.000.000, `status` admite `ALL`,
`DRAFT`, `PUBLISHED` o `ARCHIVED`, y `q` busca por nombre o SKU. La respuesta
incluye `items`, `page`, `size`, `totalItems` y `totalPages`.

### Crear de forma atómica

```http
POST /api/v1/stores/{storeSlug}/admin/products
Content-Type: application/json
X-XSRF-TOKEN: <token>
```

```json
{
  "name": "Remera clásica",
  "description": "Algodón",
  "categoryId": "8ab5aef2-85f2-4ced-b864-1077ee1fd69c",
  "variants": [
    {
      "sku": "REM-CL-M-AZ",
      "price": "15900.00",
      "size": "M",
      "color": "Azul"
    }
  ]
}
```

Producto y variantes se confirman en una sola transacción. La respuesta es
`201 Created`, empieza en `DRAFT` y expone UUID y versión de cada recurso. El
precio siempre viaja como string decimal canónico, por ejemplo `"15900.00"`.

### Consultar y editar

```http
GET /api/v1/stores/{storeSlug}/admin/products/{productId}
PUT /api/v1/stores/{storeSlug}/admin/products/{productId}
```

El `PUT` modifica nombre, descripción o categoría y debe incluir la `version`
leída. El slug no cambia al renombrar.

### Cambiar estado

```http
PATCH /api/v1/stores/{storeSlug}/admin/products/{productId}/status
```

```json
{
  "status": "PUBLISHED",
  "version": 0
}
```

Publicar exige categoría activa y al menos una variante activa. Archivar no
elimina filas; restaurar un archivado lo devuelve a `DRAFT`.

### Agregar, editar o desactivar variantes

```http
POST  /api/v1/stores/{storeSlug}/admin/products/{productId}/variants
PUT   /api/v1/stores/{storeSlug}/admin/products/{productId}/variants/{variantId}
PATCH /api/v1/stores/{storeSlug}/admin/products/{productId}/variants/{variantId}/status
```

Editar o cambiar estado exige la versión de la variante. No se puede desactivar
la última variante activa de un producto publicado.

### Errores de productos

- `400 Bad Request`: formato, límites, precio, SKU o atributos inválidos.
- `401 Unauthorized`: no existe una sesión válida.
- `403 Forbidden`: falta CSRF, membresía o permiso.
- `404 Not Found`: producto o variante no pertenece al comercio seleccionado.
- `409 Conflict`: versión obsoleta, SKU/combinación/slug duplicado o transición
  incompatible con la categoría y variantes.

## Administración de inventario

Todas las rutas parten de:

```http
/api/v1/stores/{storeSlug}/admin/inventory
```

`OWNER`, `ADMIN` y `STAFF` poseen los permisos independientes
`VIEW_INVENTORY` y `ADJUST_STOCK`. Las mutaciones requieren CSRF.

### Listar balances por variante

```http
GET /api/v1/stores/{storeSlug}/admin/inventory?page=0&size=20&q=&availability=ALL
```

`availability` admite `ALL`, `IN_STOCK` y `OUT_OF_STOCK`; `q` busca por producto
o SKU. La respuesta pagina variantes, incluso cuando todavía no tienen una fila
de balance: en ese caso la cantidad lógica es `"0.000"`.

### Consultar balance e historial

```http
GET /api/v1/stores/{storeSlug}/admin/inventory/variants/{variantId}
GET /api/v1/stores/{storeSlug}/admin/inventory/variants/{variantId}/movements?page=0&size=20
```

El balance incluye contexto de producto y variante, cantidad y versión. El
historial devuelve movimientos inmutables ordenados desde el más reciente, con
dirección, delta, cantidad anterior, cantidad resultante, motivo, nota, actor y
fecha.

### Registrar una entrada o salida

```http
POST /api/v1/stores/{storeSlug}/admin/inventory/variants/{variantId}/adjustments
Content-Type: application/json
X-XSRF-TOKEN: <token>
Idempotency-Key: <uuid por intento>
```

```json
{
  "direction": "DECREASE",
  "quantity": "2",
  "reason": "DAMAGE",
  "note": "Prenda dañada"
}
```

Los motivos admitidos son `RECEIPT`, `CORRECTION`, `DAMAGE`, `RETURN` y
`OTHER`; este último exige nota. Durante el piloto manual la cantidad debe ser
entera positiva, aunque balance y ledger se persisten con tres decimales.

La primera aplicación responde `201 Created`. Repetir la misma clave y payload
responde `200 OK` con el movimiento original, sin modificar nuevamente el saldo.
Reutilizar la clave con otro payload devuelve `409 Conflict`.

### Errores de inventario

- `400 Bad Request`: formato, cantidad, motivo, nota o parámetros inválidos.
- `401 Unauthorized`: no existe una sesión válida.
- `403 Forbidden`: falta CSRF, membresía o permiso.
- `404 Not Found`: la variante no existe en el comercio seleccionado.
- `409 Conflict`: stock insuficiente, capacidad decimal excedida o clave de
  idempotencia reutilizada con otro payload.

El navegador nunca envía como autoridad el saldo final, delta firmado, actor,
fechas, versión resultante, `tenant_id` ni información de conexión.

## Catálogo público

Las consultas públicas parten de:

```http
/api/v1/stores/{storeSlug}/catalog
```

Sólo `GET` es anónimo. Todas las respuestas usan `Cache-Control: no-store`
porque incluyen disponibilidad derivada del inventario.

### Categorías visibles

```http
GET /api/v1/stores/{storeSlug}/catalog/categories
```

Devuelve categorías activas que contienen al menos un producto publicado con
una variante activa. No expone estados administrativos.

### Productos públicos

```http
GET /api/v1/stores/{storeSlug}/catalog/products?page=0&size=24&q=&category=
```

`size` admite de 1 a 60, `page` de 0 a 10.000 y `q` busca por nombre con un
máximo de 100 caracteres. `category` recibe el slug de una categoría. El orden
es alfabético estable. Si la página supera el total, la API devuelve una página
vacía sin ejecutar un `OFFSET` innecesario.

Cada elemento incluye UUID público, nombre, slug, categoría, `priceFrom`,
`priceTo` y `available`. Los precios son strings con dos decimales. Un producto
agotado continúa visible con `available: false`.

### Detalle público

```http
GET /api/v1/stores/{storeSlug}/catalog/products/{productSlug}
```

Devuelve descripción, categoría y variantes activas con UUID, precio, talle,
color y disponibilidad booleana. Un borrador, archivado, producto con categoría
inactiva o slug inexistente responde `404`.

La API pública nunca entrega SKU, cantidad exacta, ledger, versiones, timestamps,
IDs internos, `tenant_id` ni `database_key`. La disponibilidad es informativa:
ORD-01 deberá releer precio, estado y stock al confirmar una compra.

### Uso del catálogo por CART-01

CART-01 no agrega endpoints. El carrito se conserva localmente y, al abrirlo,
agrupa líneas por `productSlug` para consultar nuevamente el detalle público.
Un `404` marca el producto como retirado; un error transitorio conserva el
snapshot como estado desconocido y permite reintentar. El navegador no envía
ningún total al backend en este sprint.

## Checkout invitado y consulta de pedidos

Las rutas son públicas, pero se resuelven contra la base del comercio indicado
por `storeSlug`. Las respuestas usan `Cache-Control: no-store`.

### Crear pedido con retiro

```http
POST /api/v1/stores/{storeSlug}/orders
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
X-XSRF-TOKEN: {token de la cookie XSRF-TOKEN}
Content-Type: application/json
```

```json
{
  "customerName": "Ana Pérez",
  "customerPhone": "11 5555 1234",
  "customerEmail": "ana@example.com",
  "notes": "Cortado fino",
  "items": [
    {
      "variantId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaa0101",
      "quantity": "2"
    }
  ]
}
```

El request no acepta precio, subtotal, SKU, moneda, estado ni identificadores
internos. Spring bloquea las variantes en orden estable, relee precio y
disponibilidad, calcula el total y crea pedido, items y reservas dentro de una
sola transacción.

Una creación nueva responde `201`; repetir el mismo comando con la misma clave
responde `200` y `replayed: true`. Reutilizarla con otro comando responde `409`.
La clave debe ser un UUID v4.

La respuesta contiene `order` con UUID, número `ORD-xxxxxx`, estado, retiro,
contacto enmascarado, moneda, subtotal e items; los decimales se expresan como
strings. También contiene `lookupToken`, un valor URL-safe de 43 caracteres.

El token se deriva de forma unidireccional desde el UUID v4 idempotente para
poder devolverlo ante un timeout; MySQL guarda únicamente otro hash SHA-256.
No debe registrarse en logs ni enviarse a otra tienda.

### Consultar confirmación

```http
GET /api/v1/stores/{storeSlug}/orders/{orderId}?token={lookupToken}
```

Devuelve el objeto `order` sin teléfono, correo completos ni token. Una
combinación de UUID/token incorrecta, o perteneciente a otro comercio, responde
el mismo `404` genérico. Al consultar una reserva ya vencida, el pedido pasa a
`EXPIRED` y sus reservas activas se marcan `EXPIRED`.

### Errores del checkout

- `400`: campos, cantidad, UUID v4 o encabezado inválidos.
- `403`: falta o es inválido el token CSRF del `POST`.
- `404`: comercio o combinación privada de pedido no encontrados.
- `409 order-item-unavailable`: publicación o cantidad no disponible.
- `409 idempotency-conflict`: clave reutilizada con otro comando.

## Operación administrativa de pedidos

Todas las rutas exigen sesión, membresía activa y permiso `MANAGE_ORDERS`.

```http
GET /api/v1/stores/{storeSlug}/admin/orders?page=0&size=20&q=ORD-000001&status=CONFIRMED
GET /api/v1/stores/{storeSlug}/admin/orders/{orderId}
```

El listado se ordena por creación descendente. `q` busca únicamente por número;
`status` es opcional. El detalle entrega contacto completo, observaciones, items,
versión e historial al operador autorizado del mismo tenant.

```http
POST /api/v1/stores/{storeSlug}/admin/orders/{orderId}/transitions
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
X-XSRF-TOKEN: {token}
Content-Type: application/json

{
  "targetStatus": "CONFIRMED",
  "note": "Stock revisado"
}
```

El actor se obtiene de la sesión. Angular no envía estado anterior, saldo,
movimientos, versión ni datos de auditoría. Las transiciones inválidas, el stock
insuficiente y una clave reutilizada con otra intención responden `409`.
Un replay idéntico no repite movimientos ni historial y devuelve el detalle
actual del pedido; si el pedido avanzó después del primer intento, la respuesta
refleja ese estado más reciente.

## Base interna de pagos — PAY-01A

PAY-01A no agrega endpoints HTTP. `PaymentApplicationService`, `PaymentGateway`
y el adaptador falso son internos y se ejercitan únicamente mediante pruebas.
El navegador no puede seleccionar resultados falsos, consultar intentos ni
recibir identificadores o material cifrado.

Los contratos existentes de catálogo, carrito, pedido y administración no
cambian. La transición administrativa responde `409` mediante su manejo de error
existente cuando intenta confirmar un pedido con pago activo o cancelar un pedido
ya cobrado. El contrato en desarrollo para iniciar Checkout Pro y consultar su
estado se describe en la sección PAY-01C de este documento.

Las colisiones concurrentes internas se normalizan como conflictos de negocio y
el intento que recibió un resultado externo no aplicable queda
`REQUIRES_REVIEW`. Este estado aún no se expone como contrato HTTP en PAY-01A.

## Errores de seguridad

- `401 Unauthorized`: la operación exige una sesión válida.
- `403 Forbidden`: la sesión existe, pero falta CSRF o permiso.
- `429 Too Many Requests`: se superó temporalmente el límite de login.

Los errores de login no distinguen cuenta inexistente, deshabilitada o contraseña
incorrecta. Este comportamiento está confirmado por las pruebas de contrato de
CORE-02.

## Conexión vendedora de Mercado Pago — PAY-01B

Las rutas administrativas exigen sesión, membresía activa y permiso
`MANAGE_PAYMENTS`, que en el modelo actual pertenece únicamente a `OWNER`.

```http
GET /api/v1/stores/{storeSlug}/admin/payment-connection
POST /api/v1/stores/{storeSlug}/admin/payment-connection/authorization
DELETE /api/v1/stores/{storeSlug}/admin/payment-connection
```

El `GET` devuelve sólo `provider`, `environment`, `status`, `connectedAt` y
`connectedAccountLabel`. Esta última propiedad contiene el `nickname` público
verificado; nunca incluye tokens, email, nombre legal ni credenciales.

El `POST` genera un `state` de un uso y PKCE S256, y devuelve
`authorizationUrl` y `expiresAt`. Angular redirige en la misma pestaña. Mercado
Pago vuelve al callback fijo:

```http
GET /api/v1/integrations/mercado-pago/oauth/callback?code={code}&state={state}
```

El callback requiere la misma sesión autenticada que inició la operación,
recupera tenant y ambiente exclusivamente desde el intento guardado, verifica
`user_id` contra `GET /users/me` y redirige a Angular con `oauth=connected`,
`cancelled` o `failed`. El parámetro visual nunca es autoridad: Angular consulta
nuevamente el `GET` anterior.

La desconexión responde `204`, elimina localmente ambos tokens y conserva sólo
auditoría e identidad mínima. El propietario debe revocar también el permiso en
Mercado Pago si desea invalidarlo del lado del proveedor.

## Checkout Pro y confirmación — PAY-01C

> Contrato implementado y cubierto por la regresión automática. El recorrido
> integrado con Mercado Pago TEST sigue pendiente antes de cerrar la historia.

### Iniciar Checkout Pro

La operación parte del pedido ya creado y exige su `lookupToken`. El contrato no
acepta precio, moneda, seller, access token, URL de retorno ni estado de pago como
datos autoritativos.

```http
POST /api/v1/stores/{storeSlug}/orders/{orderId}/payments/checkout-pro?token={lookupToken}
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
X-XSRF-TOKEN: {token CSRF}
Content-Type: application/json

{}
```

Respuesta `201 Created` al crear o `200 OK` al repetir la misma clave:

```json
{
  "checkoutUrl": "https://www.mercadopago.com.ar/checkout/...",
  "paymentAttemptId": "550e8400-e29b-41d4-a716-446655440000",
  "expiresAt": "2026-07-31T21:00:00Z",
  "replayed": false
}
```

Angular valida que `checkoutUrl` sea HTTPS y pertenezca al host permitido, evita
un segundo inicio y navega automáticamente en la misma pestaña. El backend sólo
responde si el pedido es elegible, la conexión técnica está utilizable y la
habilitación comercial del tenant está activa.

### Retorno y consulta acotada

Mercado Pago vuelve a una ruta frontend específica. El token opaco identifica el
seguimiento permitido, pero no contiene ni concede autoridad financiera. Debe
tratarse como secreto temporal, no persistirse en `localStorage` y ocultarse de
logs y referencias del navegador.

```text
/stores/{storeSlug}/payment-return/{returnToken}
```

La pantalla consulta el estado autoritativo mediante un endpoint de sólo lectura:

```http
GET /api/v1/stores/{storeSlug}/payment-returns/{returnToken}
```

La respuesta pública contiene `orderId`, `orderNumber`, `orderStatus`,
`paymentStatus`, `canRetry` y `updatedAt`. Los estados de pago públicos son
`CREATED`, `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED` y `REQUIRES_REVIEW`.
El polling se ejecuta cada 3 segundos durante un máximo aproximado de 30 segundos; al
agotar el límite se detiene y ofrece actualización manual. Un `status` recibido
en la back URL nunca cambia el pedido.

### Webhook público

```http
POST /api/v1/integrations/mercado-pago/webhooks?route={opaqueRoute}&data.id={resourceId}
x-signature: ts={timestamp},v1={digest}
x-request-id: {requestId}
Content-Type: application/json
```

TEST y producción exigen firma válida y timestamp dentro de tolerancia. El
receptor valida los valores firmados, persiste metadatos mínimos en el inbox de
control y recién entonces responde `200` o `201`. Si no puede persistir devuelve
un error reintentable; firma ausente o inválida responde `401` sin crear trabajo.

La aceptación HTTP no significa que el pedido ya esté confirmado. Un worker
consulta el recurso a Mercado Pago con la credencial del vendedor y verifica
seller, ambiente, referencia, preferencia, importe y moneda antes de aplicar un
estado. El payload recibido no se devuelve ni se usa como fuente financiera.

### Operación de webhooks agotados

El `OWNER` puede consultar hasta 100 eventos `DEAD` de su comercio. La respuesta
no incluye payload, IDs internos, request ID, notification ID, recurso de Mercado
Pago, vendedor ni datos del comprador.

```http
GET /api/v1/stores/{storeSlug}/admin/payment-webhooks?status=DEAD
```

```json
[
  {
    "eventId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "DEAD",
    "attemptCount": 8,
    "safeErrorCode": "PAYMENT_LOOKUP_FAILED",
    "occurredAt": "2026-08-01T21:00:00Z",
    "retryAllowed": true
  }
]
```

La recuperación es explícita, requiere CSRF y vuelve a colocar el evento en la
cola durable. Repetir el mismo POST no crea otra auditoría ni otro contador.

```http
POST /api/v1/stores/{storeSlug}/admin/payment-webhooks/{eventId}/retry
X-XSRF-TOKEN: {token CSRF}
Content-Type: application/json

{}
```

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RETRY_SCHEDULED",
  "scheduledAt": "2026-08-01T21:05:00Z"
}
```

El backend obtiene tenant y ambiente desde la sesión y configuración confiable.
Un evento ajeno responde como inexistente; uno ya procesado no se modifica.

Errores públicos implementados:

- `400`: formato, token temporal o clave idempotente inválidos;
- `401`: token de pedido/retorno o firma webhook inválidos;
- `404`: pedido o seguimiento no encontrado, sin revelar otro tenant;
- `409`: pedido no elegible, pago incompatible o habilitación ausente;
- `502`: operación remota con Mercado Pago fallida.

Las respuestas nunca incluyen access/refresh token, secreto de firma, digest,
seller interno, payload completo, ciphertext ni error crudo del SDK.

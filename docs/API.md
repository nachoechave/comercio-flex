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

## Errores de seguridad

- `401 Unauthorized`: la operación exige una sesión válida.
- `403 Forbidden`: la sesión existe, pero falta CSRF o permiso.
- `429 Too Many Requests`: se superó temporalmente el límite de login.

Los errores de login no distinguen cuenta inexistente, deshabilitada o contraseña
incorrecta. Este comportamiento está confirmado por las pruebas de contrato de
CORE-02.

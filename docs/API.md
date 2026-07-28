# API

> Actualizado al cerrar CORE-02 el 2026-07-28. Los contratos de autenticación
> están implementados y verificados.

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

## Errores de seguridad

- `401 Unauthorized`: la operación exige una sesión válida.
- `403 Forbidden`: la sesión existe, pero falta CSRF o permiso.
- `429 Too Many Requests`: se superó temporalmente el límite de login.

Los errores de login no distinguen cuenta inexistente, deshabilitada o contraseña
incorrecta. Este comportamiento está confirmado por las pruebas de contrato de
CORE-02.

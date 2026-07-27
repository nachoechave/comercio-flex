# API

> Actualizado durante el Sprint 2 el 2026-07-27.

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
usan para el routing. Los demás endpoints continúan cerrados hasta implementar la
sesión aprobada. No se exponen entidades JPA como respuestas HTTP.

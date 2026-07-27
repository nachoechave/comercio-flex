# API

> Contrato inicial del Sprint 1. Los endpoints de negocio se documentarán antes
> de implementarlos.

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

El endpoint no expone detalles internos. Los demás endpoints permanecen cerrados
hasta que se implemente la sesión aprobada.

## Contratos futuros

La API de negocio utilizará `/api/v1`. Los errores se expresarán como Problem
Details (`application/problem+json`) cuando se implemente el primer caso de uso.
No se expondrán entidades JPA como respuestas HTTP.

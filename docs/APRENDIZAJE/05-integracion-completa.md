# 05 — Integración completa

## Flujo verificable del Sprint 1

```text
Angular :4200
→ proxy de desarrollo
→ Spring Boot :8080
→ Spring Security
→ Actuator
→ JSON de salud
→ Angular muestra Disponible
```

El health check no consulta una entidad de negocio, pero el arranque de Spring sí
depende de MySQL y Flyway. Si la base o la migración fallan, la aplicación no debe
presentarse como preparada.

## Cómo probar

1. Iniciar Docker Desktop.
2. Levantar MySQL siguiendo `GUIA_DE_DESARROLLO.md`.
3. Iniciar backend con el perfil `local`.
4. Ejecutar `Invoke-RestMethod` contra el health check.
5. Iniciar Angular.
6. Abrir `http://localhost:4200` y observar “Disponible”.
7. Detener backend y recargar para observar “Sin conexión”.

Este circuito pequeño enseña la separación entre interfaz, API, configuración y
base de datos antes de introducir reglas comerciales.

## Ejemplo implementado en Sprint 2

```text
GET /api/v1/stores/tienda-a/settings
→ filtro extrae "tienda-a"
→ JPA consulta la base de control
→ verifica estado ACTIVE
→ obtiene la clave lógica "tenant-a"
→ comprueba la allowlist del servidor
→ guarda la clave durante la solicitud
→ el router obtiene una conexión del pool A
→ JdbcTemplate consulta store_settings en A
→ construye el DTO
→ limpia la clave del hilo
```

El slug no es el nombre de la base y la base de control no guarda la contraseña.
Esto separa información pública, metadatos de plataforma y secretos.

### Por qué se limpia el contexto

Tomcat reutiliza hilos. Si una solicitud A dejara su clave en un `ThreadLocal`, una
solicitud posterior podría heredarla. `TenantContext.Scope` usa `remove()` al
cerrarse, tanto en éxito como en error. Las pruebas ejecutan A, B, excepciones y
solicitudes concurrentes para detectar contaminación.

### Límite transaccional

La consulta de control termina antes de abrir la transacción tenant. No existe una
transacción única que abarque ambas bases. Para el MVP evitamos casos de uso que
necesiten confirmar cambios en control y tenant al mismo tiempo.

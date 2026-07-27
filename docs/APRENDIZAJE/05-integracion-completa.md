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

# 03 — Spring Boot

## Problema resuelto en el Sprint 1

El backend necesita arrancar de forma reproducible, conectarse a MySQL, aplicar
migraciones y exponer únicamente un indicador público de salud.

## Conceptos

- **Maven Wrapper:** ejecuta Maven sin exigir una instalación global.
- **Starter:** conjunto compatible de dependencias Spring.
- **Actuator:** endpoints operativos como `health`.
- **SecurityFilterChain:** reglas HTTP de acceso.
- **Perfil:** configuración que se activa para un ambiente, como `local`.
- **Testcontainers:** inicia MySQL descartable para pruebas reales.

Spring Security permite `/actuator/health` y responde `401` para recursos cerrados.
Flyway crea la tabla de control; Hibernate sólo valida y nunca modifica el esquema.

Estudiar después: anotaciones, beans, inyección de dependencias, configuración,
Spring MVC, Spring Security, JPA y transacciones.

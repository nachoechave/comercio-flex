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

## Diseño aprobado para CORE-02

Spring Security separa tres preguntas:

1. **Autenticación:** ¿quién es la persona?
2. **Autorización:** ¿tiene una membresía activa y el rol necesario?
3. **Routing tenant:** después de autorizar, ¿qué base de negocio corresponde?

```text
credenciales
→ PasswordEncoder compara el hash
→ SecurityContext identifica al usuario global
→ Spring Session JDBC persiste la sesión
→ membership vigente determina el rol para el slug
→ TenantContext habilita la conexión correcta
```

Un `PasswordEncoder` usa un hash lento y adaptativo; no cifra para luego
descifrar. La sesión evita repetir ese cálculo costoso en cada solicitud. Spring
renueva el identificador al autenticar para evitar fijación de sesión.

CSRF sigue habilitado porque el navegador envía cookies automáticamente. La SPA
obtiene `XSRF-TOKEN` y lo devuelve en `X-XSRF-TOKEN` para las operaciones con
estado. El limitador de login agrega otra defensa frente a fuerza bruta y consumo
deliberado de CPU.

Conceptos para estudiar: `SecurityFilterChain`, `AuthenticationManager`,
`SecurityContext`, `PasswordEncoder`, Spring Session, CSRF, CORS y autorización
por roles.

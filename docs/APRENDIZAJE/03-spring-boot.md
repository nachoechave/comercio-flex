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

CSRF sigue habilitado donde el navegador usa una sesión autenticada, porque envía
esa cookie automáticamente. La SPA obtiene `XSRF-TOKEN` y lo devuelve en
`X-XSRF-TOKEN` para esas operaciones. Los POST públicos del checkout invitado no
usan autoridad de sesión y se excluyen según ADR-109. El limitador de login agrega
otra defensa frente a fuerza bruta y consumo deliberado de CPU.

Conceptos para estudiar: `SecurityFilterChain`, `AuthenticationManager`,
`SecurityContext`, `PasswordEncoder`, Spring Session, CSRF, CORS y autorización
por roles.
## Aprendizaje del cierre: procesamiento y puertos de medios

El controlador multipart recibe el archivo, pero el caso de uso vive en
`ProductImageService`. `ProductImageProcessor` verifica firma, decodifica, aplica
EXIF, limita píxeles y recodifica; no se debe confiar en extensión ni MIME enviado
por el navegador.

`ProductImageStorage` es un puerto: desarrollo usa filesystem y producción S3/R2.
Así la regla de negocio no depende del proveedor. Como MySQL y el bucket no
comparten una transacción, el servicio aplica compensaciones: si falla metadata,
borra el objeto nuevo; si reemplaza, elimina los objetos anteriores al completar.
Esto reduce inconsistencias, aunque una reconciliación periódica es una mejora
futura razonable.

El filtro de correlación agrega `X-Request-Id` al contexto de logs. Liveness
responde si el proceso vive; readiness decide si debe recibir tráfico. Son señales
operativas diferentes.

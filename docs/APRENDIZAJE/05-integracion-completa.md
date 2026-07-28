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

## Flujo aprobado para CORE-02

```text
Usuario
→ Angular solicita token CSRF
→ usuario envía correo y contraseña
→ Spring Security limita y verifica el intento
→ control DB devuelve la identidad global
→ Spring Session JDBC persiste la sesión
→ Angular consulta usuario y memberships
→ usuario selecciona tienda-a
→ backend resuelve tienda-a en control DB
→ verifica membership ACTIVE y rol
→ abre TenantContext para tenant-a
→ ejecuta el caso de uso autorizado
→ limpia el contexto
```

Autenticar y autorizar no son lo mismo. La cookie demuestra que el navegador tiene
una sesión válida, pero sólo una membresía activa concede acceso a un comercio.
Además, el rol determina qué acciones puede ejecutar dentro de ese comercio.

### Pruebas que cerraron la historia

CORE-02 se marcó `Terminada` después de verificar, entre otros casos:

- login válido e inválido sin enumeración de cuentas;
- rechazo de POST sin CSRF;
- persistencia e invalidación de la sesión;
- usuario A rechazado en el comercio B;
- una persona con roles distintos en dos comercios;
- usuario bloqueado/deshabilitado o membership inactiva;
- cada permiso de `OWNER`, `ADMIN` y `STAFF`;
- límite temporal de intentos de login.

## Flujo implementado en CAT-01

```text
Administrador abre /tiendas/tienda-a/admin/categorias
→ Angular obtiene storeSlug desde la ruta activa
→ solicita categorías mediante CategoryApiService
→ Spring valida sesión y membresía de tienda-a
→ VIEW_CATALOG permite lectura a OWNER, ADMIN y STAFF
→ TenantContext selecciona la base A
→ JdbcCategoryRepository consulta categories
→ Angular muestra nombre, slug y estado
```

Para crear o modificar, Spring exige `MANAGE_CATALOG`, disponible sólo para
`OWNER` y `ADMIN`, además del token CSRF. El guard Angular evita una navegación
inútil, pero la autorización real siempre ocurre en el backend.

Si Angular reutiliza el componente al navegar de tienda A a B, los parámetros se
observan reactivamente: se cancela la solicitud anterior, se limpia el estado de
A y recién después se consulta B. Esto evita mostrar o modificar accidentalmente
información del comercio anterior.

La categoría usa dos identificadores: un `BIGINT` eficiente dentro de MySQL y un
UUID público para la API. Al crear, el backend normaliza el nombre y genera el
slug. Al renombrar, conserva el slug para que futuras URLs no se rompan. Al
desactivar, cambia el estado a `INACTIVE`; la fila puede reactivarse.

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

## Flujo implementado en CAT-02

```text
Administrador completa producto y filas de variantes
→ Angular valida forma y evita doble envío
→ POST envía categoría, nombre y variantes
→ Spring autoriza MANAGE_CATALOG
→ ProductService abre una transacción tenant
→ valida categoría, SKU, precio, talle y color
→ inserta products y product_variants
→ confirma todo o revierte todo
→ responde UUID, estado DRAFT y versiones
→ Angular navega al detalle
```

El precio se recibe como texto decimal y se convierte a `BigDecimal`; así no
hereda errores de representación binaria de `number`/`double`. Producto y cada
variante tienen su propia versión: si dos personas editan el mismo recurso, la
segunda escritura con una versión vieja recibe `409` y debe recargar.

Publicar y desactivar variantes son operaciones distintas que podrían ocurrir al
mismo tiempo. Ambas bloquean primero la fila de producto y vuelven a comprobar la
regla dentro de la transacción. La prueba concurrente confirma que nunca queda un
producto `PUBLISHED` sin variantes activas.

## Flujo implementado en INV-01

```text
Operador selecciona Entrada o Salida
→ Angular genera una Idempotency-Key
→ envía dirección, cantidad, motivo y nota
→ backend obtiene el actor desde la sesión
→ bloquea la variante y su balance
→ reconoce reintentos ya aplicados
→ calcula antes + delta = después
→ actualiza balance e inserta movimiento
→ confirma ambos o revierte ambos
→ Angular presenta el nuevo saldo y el movimiento
```

El balance permite responder rápido cuánto stock existe. El ledger permite
explicar por qué existe esa cantidad. Guardar ambos exige una transacción: si
fallara el movimiento después de actualizar el balance, toda la operación se
revierte.

El lock de fila evita que dos salidas lean simultáneamente el mismo saldo y ambas
lo gasten. La clave de idempotencia resuelve otro problema: una misma operación
repetida por pérdida de respuesta. Concurrencia e idempotencia son protecciones
distintas y complementarias.

## Flujo público de STORE-01

```text
Visitante abre /tiendas/tienda-a
→ Angular lee storeSlug y query params
→ solicita settings, categorías y productos
→ Spring resuelve tienda-a en la base de control
→ TenantContext selecciona únicamente la base A
→ el repositorio filtra publicación y estados activos
→ inventario se reduce a available true/false
→ Angular muestra tarjetas, filtros y enlaces reales
```

Una API pública necesita su propio contrato. Reutilizar la respuesta
administrativa sería cómodo, pero podría filtrar SKU, cantidades, versiones o
estados internos. Por eso STORE-01 usa DTOs públicos deliberadamente más
pequeños.

El precio y la disponibilidad mostrados no son una promesa de compra. Entre la
consulta y el checkout otra persona puede modificar precio o stock. ORD-01
deberá releer esos datos en el backend y calcular el total; nunca confiará en una
copia enviada por Angular.

## Flujo local de CART-01

```text
Visitante elige talle/color y cantidad
→ ProductDetail llama a CartService
→ CartService valida 1–99 y disponibilidad
→ actualiza signals de la tienda actual
→ persiste un snapshot mínimo por storeSlug
→ la cabecera actualiza el contador
→ CartPage relee el detalle público
→ actualiza precio o marca la línea como no disponible/desconocida
```

`localStorage` es cómodo, pero el usuario puede editarlo desde DevTools y otro
script del mismo origen puede accederlo. Por eso no guarda datos personales ni
secretos y se valida cada campo al leer. Un valor corrupto se descarta.

El subtotal usa centavos enteros con `BigInt`: precio decimal → centavos →
multiplicación por cantidad → string decimal. Esto evita errores como
`0.1 + 0.2` propios del punto flotante.

Revalidar mejora la información presentada, pero todavía no garantiza una
compra. No existe reserva y otra operación puede consumir el último stock un
instante después. La garantía real aparecerá cuando ORD-01 bloquee y valide las
filas necesarias dentro de una transacción MySQL.

## Flujo transaccional de ORD-01

```text
Formulario Angular
→ obtiene CSRF
→ envía contacto + UUID de variante + cantidad + Idempotency-Key
→ Spring resuelve el comercio
→ normaliza y genera fingerprint
→ bloquea variantes en orden estable
→ resta reservas vigentes del saldo físico
→ toma precio y moneda desde MySQL
→ inserta pedido, snapshots y reservas
→ devuelve referencia pública y token privado
→ Angular vacía el carrito y abre la confirmación
```

El navegador es entrada no confiable: puede mostrar un precio para la UX, pero
nunca decide qué se cobra ni cuánto stock existe. La transacción hace indivisible
el cambio; si falla una línea, no queda un pedido incompleto ni una reserva
huérfana.

La idempotencia protege contra doble clic y respuestas perdidas. El mismo UUID v4
y fingerprint recuperan el pedido original; cambiar el comando produce
conflicto. El lock de variante resuelve otro problema: dos clientes que compiten
por el último stock.

## Flujo operativo de ORD-02

```text
Operador abre Pedidos
→ Angular solicita listado o detalle del tenant actual
→ Spring verifica sesión, membresía y MANAGE_ORDERS
→ el servicio bloquea el pedido para cambiarlo
→ valida la máquina de estados
→ al confirmar descuenta stock y consume reservas
→ al cancelar genera el movimiento compensatorio
→ guarda actor, nota y transición en el historial
→ Angular reemplaza el detalle con la respuesta actual
```

Una **máquina de estados** enumera los cambios válidos. No alcanza con recibir un
texto como `COMPLETED`: el backend debe comprobar desde qué estado se intenta
llegar. Esto evita, por ejemplo, completar directamente un pedido pendiente.

La confirmación y la cancelación no borran movimientos. La cancelación agrega un
movimiento compensatorio que devuelve la cantidad. Así el saldo vuelve al valor
anterior sin perder la evidencia de lo ocurrido.

La expiración también es una transición. Primero actualiza el pedido de forma
atómica y sólo si ese cambio ganó la carrera libera la reserva y agrega historial.
Una restricción única por pedido y estado funciona como última defensa contra
eventos duplicados.

La `Idempotency-Key` protege los reintentos de la misma acción; el bloqueo de fila
protege a dos operadores que intentan acciones diferentes al mismo tiempo. Igual
que en ORD-01, resuelven problemas relacionados pero no equivalentes.

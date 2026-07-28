# Backlog inicial

> Estimación relativa: XS, S, M, L, XL. Todas las tareas están en Fase 0 o pendientes.

| ID | Épica | Historia o tarea | Descripción | Prioridad | Estado | Dependencias | Responsable | Criterios de aceptación | Riesgos | Est. |
|---|---|---|---|---|---|---|---|---|---|---|
| F0-01 | Descubrimiento | Aprobar vertical piloto | Indumentaria aprobada; falta seleccionar comercio real | Alta | En análisis | — | PO | Comercio entrevistado y flujo crítico documentado | Supuestos sin validar | S |
| F0-02 | Arquitectura | Aprobar ADR iniciales | Identidad y stock aprobados; frontend definitivo y hosting real continúan pendientes | Alta | En análisis | F0-01 | PO/Arquitectura | ADR con responsable, fecha y consecuencias | Retrabajo | M |
| BT-01 | Base técnica | Inicializar repositorio | Git, convenciones y estructura aprobada | Alta | Terminada | F0-02 | Arquitectura | Estado revisable, guía y propuesta de commit | Mezcla de cambios | S |
| BT-02 | Base técnica | Inicializar backend | Spring Boot, módulos, health y error base | Alta | Terminada | F0-02 | Backend | Build/test/health/documentación | Flyway advierte soporte no probado para MySQL 8.4 | M |
| BT-03 | Base técnica | Inicializar frontend | Angular, shells, rutas y health UI | Alta | Terminada | F0-02 | Frontend | Build/test/responsive/documentación | SSR diferido por decisión | M |
| BT-04 | Datos | MySQL y Flyway multi-base | Compose, primera migración y Testcontainers | Alta | Terminada | BT-02 | Datos/Backend | Tres bases, misma versión, tests y prueba manual | Drift de esquema | L |
| CORE-01 | Tenant | Resolver comercio y conexión | Resolver path, consultar control DB y enrutar a la base correcta | Alta | Terminada | BT-02, BT-04 | Backend | A/B aislados, fallo seguro y API documentada | Conexión incorrecta | XL |
| CORE-02 | Identidad | Login y roles | Sesión JDBC segura, autorización por membresía y logout | Alta | Terminada | CORE-01 | Backend/Frontend/Calidad | Login global, roles fijos, CSRF, límite de intentos y pruebas negativas | Cookies, fuerza bruta y autorización tenant | L |
| CAT-01 | Catálogo | Gestionar categorías | CRUD administrativo por tenant | Alta | Terminada | CORE-02 | Backend/Frontend | Validación, aislamiento, pruebas y manual | Slugs duplicados | M |
| CAT-02 | Catálogo | Gestionar productos de indumentaria | Alta/edición/publicación, talle, color, SKU y precio | Alta | Pendiente | CAT-01 | Backend/Frontend | Precio > 0, categoría y variante válidos | Combinaciones inválidas | XL |
| INV-01 | Inventario | Ajustar stock | Existencia por variante y movimiento auditable | Alta | Pendiente | CAT-02 | Backend/Frontend | No negativo, concurrencia y pruebas | Sobreventa | L |
| STORE-01 | Tienda | Navegar catálogo | Listar, buscar y ver detalle público | Alta | Pendiente | CAT-02 | Frontend/Backend | Responsive, estados y tenant correcto | Rendimiento/SEO | L |
| ORD-01 | Compra | Carrito y checkout | Carrito local, cliente, entrega y observaciones | Alta | Pendiente | STORE-01, INV-01 | Frontend/Backend | Backend recalcula y persiste snapshot | Precio manipulado | XL |
| ORD-02 | Pedidos | Operar pedidos | Listado, detalle y transiciones válidas | Alta | Pendiente | ORD-01 | Backend/Frontend | Roles, historial y pruebas | Estados ambiguos | L |
| PAY-01 | Pagos | OAuth y Checkout Pro sandbox | Conectar comercio, preferencia, retorno y webhook idempotente | Alta | Pendiente | ORD-01, F0-02 | Backend/Calidad | OAuth state, firma, importe, duplicados y pruebas | Fraude/secretos | XL |
| DASH-01 | Dashboard | Métricas mínimas | Día, mes, pendientes y stock bajo | Media | Pendiente | ORD-02 | Backend/Frontend | Datos por tenant y definiciones documentadas | Métricas inconsistentes | M |
| OPS-01 | Operación | Despliegue piloto | HTTPS, secretos, logs, backup y restore | Alta | Pendiente | PAY-01 | Infraestructura | Smoke, backup y restauración probada | Costo/caída | L |
| SEC-01 | Seguridad | Separar credenciales runtime por base | Usuario de mínimo privilegio para control y para cada tenant | Alta | Pendiente | CORE-01 | Datos/Infraestructura | Un usuario tenant no accede a control ni a otra base | Movimiento lateral | M |
| AUTH-02 | Identidad | Evaluar Firebase Authentication | Analizarlo como proveedor externo para clientes y administradores sin trasladar membresías ni permisos | Baja | Pendiente | MVP validado | Arquitectura/Seguridad/PO | ADR de migración, costos, sesiones, revocación y coexistencia aprobado | Dependencia externa y migración de cuentas | L |

## Definición de terminado

Una tarea sólo está terminada si cumple criterios, tiene pruebas adecuadas, fue
revisada, está documentada y puede probarse manualmente.

## CORE-01 — Resolver comercio y conexión

### Historia

Como visitante de una tienda quiero que la plataforma identifique el comercio de
la URL para recibir únicamente su configuración y sus datos.

### Criterios de aceptación

- Una ruta pública con `tienda-a` consulta la base de control y obtiene datos
  exclusivamente de la base de negocio A.
- La misma operación con `tienda-b` obtiene datos exclusivamente de la base B.
- Un slug inexistente o un comercio que no esté activo devuelve `404` mediante
  Problem Details, sin revelar nombres de bases, credenciales ni datos internos.
- El navegador no puede seleccionar una conexión mediante headers, parámetros,
  body, `database_key` ni un nombre de base.
- La traducción de `database_key` a URL y credenciales utiliza solamente
  configuración confiable del backend.
- La ausencia de una conexión configurada falla de forma cerrada y no utiliza
  otra base como valor predeterminado.
- El contexto del comercio se elimina al terminar la solicitud, incluso si ocurre
  una excepción.
- Las pruebas de integración utilizan una base de control y dos bases de negocio
  con datos deliberadamente diferentes.
- Existen pruebas negativas de comercio desconocido, inactivo, conexión no
  configurada e intentos de influir en la selección desde el cliente.
- La API, el flujo, las carpetas nuevas y los pasos de prueba manual quedan
  documentados.

## CORE-02 — Login, sesión y roles

> Estado: terminada el 2026-07-28 después de implementar, revisar, probar y
> documentar el flujo manual completo.

### Historia

Como integrante de un comercio quiero iniciar y cerrar sesión para operar
únicamente las tiendas y funciones autorizadas por mis membresías.

### Criterios de aceptación

- Un usuario activo puede iniciar sesión con correo y contraseña válidos sin que
  la respuesta exponga el hash ni otros datos sensibles.
- Un login inválido devuelve un error genérico que no revela si el correo existe.
- La contraseña se guarda con un hash adaptativo y nunca en texto plano.
- La sesión se persiste mediante Spring Session JDBC en la base de control y su
  cookie es `HttpOnly`, `SameSite=Lax` y `Secure` fuera del perfil local.
- El identificador de sesión se renueva al autenticar y el logout invalida la
  sesión en el servidor.
- `GET /api/v1/auth/session` devuelve `200` con `{"authenticated":false}` cuando
  no existe una sesión, y cuando está autenticado devuelve el usuario y sólo sus
  membresías activas.
- Las operaciones con estado, incluidos login y logout, requieren un token CSRF
  válido usando la cookie `XSRF-TOKEN` y el header `X-XSRF-TOKEN`.
- El frontend ofrece login global, selecciona automáticamente una única membresía
  o permite elegir entre varias, y no usa `localStorage` para guardar credenciales
  o sesiones.
- Antes de abrir una conexión tenant, el backend comprueba usuario, comercio,
  membresía activa y permiso. Un usuario del comercio A no puede operar B.
- `OWNER`, `ADMIN` y `STAFF` cumplen la matriz fija de ADR-019 y existen pruebas
  negativas por rol.
- Un usuario bloqueado o deshabilitado, una membresía inactiva o un comercio
  inactivo pierde el acceso aunque todavía presente una cookie.
- El login aplica un límite básico por IP y correo normalizado sin permitir que el
  cliente elija una identidad, rol o tenant.
- El primer `OWNER` puede provisionarse mediante un procedimiento operativo
  idempotente y sin secretos versionados.
- Las migraciones incluyen restricciones únicas para correo normalizado y para la
  relación usuario-comercio.
- Las pruebas cubren autenticación, CSRF, logout, persistencia de sesión,
  membresías múltiples, roles, CORS y aislamiento entre dos tenants.
- La API, estructura, conceptos, configuración y prueba manual quedan
  documentados antes de marcar la historia como `Terminada`.

### Evidencia de cierre

- Suite backend completa: 29 pruebas sin fallos después de integrar identidad con
  los health checks y el routing tenant existente.
- Suite específica de identidad ampliada: 10 pruebas sin fallos para login, CSRF,
  sesión JDBC, logout, CORS, rate limiting, membresías múltiples y aislamiento.
- Frontend: 14 pruebas sin fallos y build de producción correcto.
- Prueba manual local: `OWNER` autenticado en `tienda-a`; acceso a su comercio,
  rechazo `403` en `tienda-b` y sesión anónima después del logout.
- Revisión: una cuenta deshabilitada se revalida antes de abrir el datasource
  tenant y la matriz fija de permisos tiene pruebas unitarias.

## CAT-01 — Gestión de categorías

> Estado: terminada el 2026-07-28 después de implementar, revisar, probar y
> documentar el flujo completo. Las decisiones ADR-025 a ADR-028 fueron aprobadas
> por el Product Owner antes de iniciar la implementación.

### Historia

Como administrador de un comercio quiero gestionar categorías para organizar los
productos que luego publicaré en mi tienda.

### Criterios de aceptación

- `OWNER` y `ADMIN` pueden crear, consultar, renombrar, desactivar y reactivar
  categorías únicamente dentro de un comercio con membresía activa.
- `STAFF` puede consultar categorías, pero cualquier intento de modificarlas
  devuelve `403`; una persona anónima recibe `401`.
- El nombre es obligatorio, se normalizan espacios y admite entre 2 y 120
  caracteres; una entrada inválida devuelve `400` con un error claro.
- Nombre y slug son únicos dentro de cada base tenant. Una colisión concurrente
  devuelve `409` y no sobrescribe datos.
- El backend genera un slug válido al crear y no lo modifica cuando cambia el
  nombre.
- El alta devuelve `201`, ubicación e identificador UUID público; la API nunca
  expone el `BIGINT`, `database_key` ni credenciales.
- Desactivar no borra la fila, es reversible y deja de incluirla en las consultas
  de categorías activas.
- Una categoría creada en `tienda-a` no aparece ni puede consultarse o modificarse
  desde `tienda-b`; el mismo nombre puede existir en ambos comercios.
- Las operaciones de escritura requieren el mecanismo CSRF ya establecido.
- La interfaz muestra estados de carga, vacío, error y éxito; evita doble envío,
  funciona con teclado y no comunica estados únicamente mediante color.
- La migración Flyway se aplica de forma consistente a las bases tenant y el
  cambio persiste en MySQL.
- Existen pruebas unitarias, de API, autorización, integración multiempresa y
  frontend; también un procedimiento de prueba manual documentado.

### Evidencia de cierre

- Suite backend completa: 40 pruebas sin fallos ni errores.
- CAT-01 backend: 3 pruebas unitarias y 8 pruebas de integración con MySQL 8.4,
  seguridad, CSRF, permisos y dos bases tenant.
- Frontend: 29 pruebas sin fallos y build de producción correcto; categorías se
  entrega mediante rutas lazy.
- Prueba manual en navegador: alta, slug automático, renombrado estable,
  desactivación, reactivación, conflicto duplicado y rechazo de otro comercio.
- Vista móvil verificada a 390 px sin desborde horizontal ni errores de consola.
- Revisión independiente: sin defectos bloqueantes; aislamiento A/B, cambio
  reactivo de tenant y carrera de duplicados verificados.

### Deuda técnica registrada

- Incorporar control de versión o `ETag` antes de operación multiadministrador
  intensa para evitar que dos ediciones simultáneas se pisen.
- Fijar o validar explícitamente la collation de cada nueva base tenant.
- Agregar paginación cuando el volumen real deje de justificar una lista completa.
- Mejorar el diálogo accesible de confirmación con contención de foco y Escape.
- Reducir el ruido del scheduler de limpieza de Spring Session entre contextos de
  Testcontainers.

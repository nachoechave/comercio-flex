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
| CAT-02 | Catálogo | Gestionar productos de indumentaria | Alta/edición/publicación, talle, color, SKU y precio | Alta | Terminada | CAT-01 | Backend/Frontend | Precio > 0, categoría y variante válidos | Combinaciones inválidas | XL |
| INV-01 | Inventario | Ajustar stock | Existencia por variante y movimiento auditable | Alta | Terminada | CAT-02 | Backend/Frontend | No negativo, concurrencia y pruebas | Sobreventa | L |
| STORE-01 | Tienda | Navegar catálogo | Listar, buscar y ver detalle público | Alta | Terminada | CAT-02 | Frontend/Backend | Responsive, estados y tenant correcto | Rendimiento/SEO | L |
| MEDIA-01 | Medios | Gestionar imagen principal | Carga, almacenamiento externo, thumbnail y texto alternativo | Alta | Pendiente | STORE-01 | Frontend/Backend/Infraestructura | MIME/tamaño, aislamiento, fallback y eliminación segura | Costo, contenido malicioso y archivos huérfanos | L |
| CART-01 | Compra | Carrito local | Selección de variante, cantidades, persistencia local y revalidación | Alta | Terminada | STORE-01 | Frontend | Aislado por tienda, accesible, sin datos personales y revalidado | Datos locales obsoletos | L |
| ORD-01 | Compra | Checkout invitado | Retiro, contacto, reserva temporal y creación transaccional del pedido | Alta | Terminada | CART-01, INV-01 | Frontend/Backend | Backend recalcula, reserva y persiste snapshot idempotente | Precio manipulado, abuso y sobreventa | XL |
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

## CAT-02 — Gestión de productos y variantes

> Estado: terminada el 2026-07-29. Las decisiones ADR-029 a ADR-036 fueron
> aprobadas antes de iniciar la implementación.

### Historia

Como administrador de un comercio quiero crear y mantener productos con variantes
vendibles para preparar el catálogo de indumentaria.

### Criterios de aceptación

- `OWNER` y `ADMIN` pueden crear, consultar, editar, publicar, despublicar y
  archivar productos de su comercio; `STAFF` sólo puede consultar.
- El alta persiste producto y al menos una variante en una única transacción; si
  una variante falla, no queda un producto parcial.
- El producto tiene una categoría activa, nombre obligatorio de 2 a 160
  caracteres, descripción opcional de hasta 2000 y slug automático e inmutable.
- Cada variante posee UUID público, SKU obligatorio y único por comercio, precio
  decimal mayor que cero, talle y color opcionales y estado activo/inactivo.
- Talle y color vacíos representan una variante base; una combinación normalizada
  no puede repetirse dentro del producto.
- El precio vive exclusivamente en la variante, se persiste como `DECIMAL(15,2)`
  y viaja en JSON como string canónico.
- Un producto `PUBLISHED` exige categoría activa y al menos una variante activa.
  No se puede desactivar su última variante activa sin volverlo antes a `DRAFT`.
- Archivar no borra producto ni variantes; restaurar devuelve el producto a
  `DRAFT`.
- Una versión obsoleta de producto o variante devuelve `409` y no sobrescribe la
  edición más reciente.
- El listado es paginado, permite búsqueda por nombre o SKU y filtros por estado
  y categoría, con tamaño máximo 100.
- Producto, variante o categoría de `tienda-a` no pueden leerse ni modificarse
  desde `tienda-b`; el mismo SKU puede existir en bases tenant diferentes.
- Las mutaciones requieren CSRF y `MANAGE_CATALOG`; la lectura requiere
  `VIEW_CATALOG`.
- La API no expone `BIGINT`, `tenant_id`, `database_key`, conexiones ni entidades
  de persistencia.
- Angular cancela y limpia datos al cambiar de tenant, maneja carga, vacío,
  errores de campo, conflicto y doble envío, y funciona con teclado.
- Stock, imágenes, catálogo público, promociones, múltiples categorías y
  atributos genéricos no forman parte de CAT-02.
- Migraciones, pruebas backend/frontend, revisión, documentación y prueba manual
  están completas antes de marcar la historia como `Terminada`.

### Evidencia de cierre

- Backend: 54 pruebas en verde con MySQL 8.4/Testcontainers; 14 pertenecen a la
  suite específica de productos y validación.
- Frontend: 44 pruebas en verde y build de producción correcto.
- Revisión independiente: sin defectos bloqueantes; incluye atomicidad,
  aislamiento A/B, CSRF, roles, versiones y carreras de publicación.
- Prueba manual: login `OWNER`, alta, edición de datos y precio, publicación,
  archivado y restauración a borrador verificados en la aplicación local.

### Deuda no bloqueante

- Definir explícitamente el `collation` común de todas las bases tenant.
- Escalar la búsqueda `%term%` cuando el volumen real justifique índices o un
  motor de búsqueda.
- Hacer que ediciones de variantes actualicen el orden de “modificado
  recientemente” del producto si el piloto demuestra esa necesidad.
- Mejorar el diálogo accesible con focus trap y cierre por Escape.

## INV-01 — Inventario por variante

> Estado: terminada el 2026-07-29. Las decisiones ADR-037 a ADR-044 fueron
> aprobadas antes de iniciar la implementación.

### Historia

Como operador de un comercio quiero registrar entradas y salidas de mercadería
para conocer la existencia actual de cada variante y explicar cada cambio.

### Criterios de aceptación

- Cada variante presenta una única existencia lógica dentro de su base tenant;
  si aún no existe una fila materializada, su cantidad es cero.
- Las cantidades se persisten como `DECIMAL(15,3)`, viajan como strings canónicos
  y la interfaz de indumentaria sólo admite unidades enteras durante INV-01.
- El operador elige entrada o salida e ingresa una cantidad positiva; el backend
  calcula el delta y nunca acepta saldo, actor, timestamps o resultado enviados
  como autoridad por el navegador.
- Un ajuste válido actualiza el balance y agrega exactamente un movimiento
  inmutable dentro de una única transacción.
- La existencia nunca puede quedar negativa; un intento insuficiente devuelve
  `409` sin cambiar el balance ni agregar movimiento.
- Cada intento usa una clave de idempotencia. Repetir la misma clave y payload
  devuelve el movimiento original sin aplicar nuevamente el ajuste; reutilizarla
  con datos distintos devuelve `409`.
- Los motivos permitidos son recepción, corrección, daño/merma, devolución y
  otro; `OTHER` requiere una nota.
- `OWNER`, `ADMIN` y `STAFF` con membresía activa pueden consultar y ajustar
  inventario mediante permisos específicos. Las mutaciones requieren CSRF.
- Una variante o movimiento de `tienda-a` no puede consultarse ni modificarse
  desde `tienda-b`.
- Las variantes inactivas y productos archivados conservan inventario ajustable,
  mostrando claramente su estado comercial.
- El panel incluye listado paginado por variante, detalle, ajuste e historial
  paginado ordenado desde el movimiento más reciente.
- Dos incrementos concurrentes se acumulan sin pérdida y dos salidas concurrentes
  nunca producen existencia negativa.
- El cambio de tenant cancela solicitudes y limpia balance, historial, filtros y
  formulario del comercio anterior.
- No existen endpoints para editar o borrar movimientos.
- Umbrales de stock bajo, reservas, descuento por pedidos, depósitos, compras,
  transferencias e importación quedan fuera de INV-01.
- Migración, pruebas backend/frontend, revisión, documentación y prueba manual
  deben completarse antes de marcar la historia como `Terminada`.

### Evidencia de cierre

- Suite integral backend: 67 pruebas, 0 fallos, 0 errores y 0 omitidas.
- Suite focalizada de inventario: 13 pruebas de integración sobre MySQL 8.4,
  incluyendo concurrencia, rollback, idempotencia, roles, CSRF y aislamiento A/B.
- Frontend: 53 pruebas aprobadas y build de producción exitoso.
- Revisión final de seguridad y calidad: sin bloqueantes.
- Prueba manual en navegador: login `OWNER`; saldo inicial `0.000`; entrada de
  10; salida de 3; saldo final `7.000`; historial con dos movimientos, actor y
  notas; salida de 8 rechazada sin modificar el saldo.
- API, modelo de datos, arquitectura, estructura, glosario, guía y material de
  aprendizaje actualizados.

### Deuda técnica no bloqueante

- Evaluar una matriz motivo/dirección si el piloto necesita reportes más
  estrictos; hoy cualquier motivo válido puede combinarse con entrada o salida.
- Persistir temporalmente clave y fingerprint del último intento en
  `sessionStorage` para conservar el retry seguro si el usuario abandona el
  formulario durante un timeout.
- Endurecer en producción los permisos SQL del ledger para impedir
  `UPDATE`/`DELETE` incluso fuera de la API.
- Agregar pruebas explícitas de controles ISO y nota de 501 caracteres, de
  continuidad de `balance_version` y de reutilización de una misma clave entre
  dos bases tenant.
- Escalar la búsqueda `%q%` y exponer `balance_version` sólo si el volumen o la
  auditoría operativa lo justifican.
- Revisar la versión de Flyway cuando declare compatibilidad probada con MySQL
  8.4 y reducir el ruido de cierre de pools entre contextos de Testcontainers.

## STORE-01 — Catálogo público

> Estado: terminada el 2026-07-29. Las decisiones ADR-045 a ADR-050 fueron
> aprobadas antes de iniciar la implementación.

### Historia

Como visitante quiero navegar el catálogo público de un comercio para encontrar
productos, consultar sus variantes y conocer si están disponibles.

### Criterios de aceptación

- La tienda se abre en `/tiendas/{storeSlug}` y el detalle en
  `/tiendas/{storeSlug}/productos/{productSlug}`.
- Sólo se exponen productos `PUBLISHED`, categorías `ACTIVE` y variantes
  `ACTIVE`; borradores, archivados y recursos retirados responden como no
  encontrados.
- Las categorías públicas tienen al menos un producto visible.
- El catálogo permite búsqueda por nombre, filtro por una categoría y
  paginación; estos valores se reflejan y restauran desde la URL.
- El orden es alfabético y estable.
- Los precios viajan como strings decimales y se presentan con la moneda de la
  configuración pública del comercio.
- Un producto agotado permanece visible. Sólo se expone `available`; nunca la
  cantidad, SKU, ledger, versiones, timestamps, BIGINT ni `database_key`.
- La ausencia de balance representa disponibilidad falsa.
- Los endpoints del catálogo son anónimos sólo para `GET`, responden
  `Cache-Control: no-store` y no relajan la seguridad administrativa.
- Un slug desconocido, inactivo o no conectado falla de forma segura. El mismo
  slug de producto puede existir en A y B sin cruzar datos.
- La interfaz distingue carga, catálogo vacío, búsqueda vacía, error recuperable,
  tienda no encontrada y producto retirado.
- Cambiar de comercio cancela solicitudes anteriores y limpia resultados.
- La interfaz funciona con teclado, comunica disponibilidad mediante texto y no
  presenta desbordamiento horizontal entre 320 y 1280 píxeles.
- El título y la descripción HTML cambian por tienda y producto dentro de las
  limitaciones CSR aceptadas para el MVP.
- STORE-01 usa un placeholder accesible. Carga y almacenamiento real de imágenes,
  carrito, checkout, promociones, favoritos, filtros avanzados, SSR y SEO
  avanzado quedan fuera.
- Pruebas backend/frontend, aislamiento A/B, revisión, documentación y prueba
  manual deben completarse antes de marcar la historia como `Terminada`.

### Evidencia de cierre

- Suite backend completa: 75 pruebas, 0 fallos, 0 errores y 0 omitidas.
- Suite focalizada del catálogo público: 8 pruebas de integración sobre MySQL
  8.4 con visibilidad, aislamiento A/B, autorización y respuestas sin caché.
- Frontend: 72 pruebas en verde y build de producción correcto.
- Revisión cruzada de backend, frontend, seguridad y documentación sin bloqueos
  abiertos.
- Prueba manual completada sobre Tienda A y Tienda B: catálogo, búsqueda sin
  resultados, detalle, producto inexistente, tienda vacía, tienda inexistente,
  cambio de metadatos y ausencia de desbordamiento horizontal en escritorio.
- La interfaz es mobile-first y sus componentes incluyen puntos de corte desde
  320 píxeles; la comprobación visual específica en dispositivos reales queda
  incluida en el smoke test previo al piloto.

### Deuda técnica aceptada

- MEDIA-01 incorporará carga, almacenamiento y optimización de imágenes antes
  del piloto.
- SSR/SEO avanzado, CDN, rate limiting y búsqueda full-text se reevaluarán con
  tráfico y volumen de catálogo reales.
- Antes del primer cliente se ejecutará un análisis `EXPLAIN` con un conjunto de
  datos representativo y una matriz visual en dispositivos móviles reales.

## CART-01 — Carrito local

> Estado: terminada el 2026-07-30. Las decisiones ADR-051 a ADR-057 fueron
> aprobadas antes de iniciar la implementación.

### Historia

Como visitante quiero guardar variantes en un carrito del comercio para revisar
cantidades y subtotal antes de iniciar el checkout.

### Criterios de aceptación

- Una variante sólo puede agregarse desde el detalle del producto y después de
  seleccionarla explícitamente.
- Las variantes sin disponibilidad no pueden seleccionarse ni agregarse.
- La cantidad por línea es un entero entre 1 y 99.
- Agregar nuevamente la misma variante acumula la cantidad sin superar 99.
- El visitante puede abrir `/tiendas/{storeSlug}/carrito`, cambiar cantidades,
  eliminar una línea y vaciar el carrito con confirmación.
- La cabecera muestra el total de unidades y lo actualiza inmediatamente.
- El carrito persiste en `localStorage` usando una clave versionada y separada
  por `storeSlug`; nunca almacena datos personales ni secretos.
- Datos corruptos, incompatibles o fuera de rango se descartan de forma segura.
- Al abrir el carrito se relee cada producto público. Precio, nombre y opciones
  se actualizan; una variante retirada o sin stock queda marcada y no participa
  del subtotal accionable.
- Un fallo de red conserva el snapshot local, distingue el estado desconocido y
  permite reintentar sin borrar el carrito.
- Cambiar de comercio muestra únicamente el carrito de ese comercio.
- La moneda proviene de la configuración pública y el subtotal usa aritmética
  decimal, no punto flotante.
- No se crea pedido, no se descuenta stock y no se solicitan datos del cliente.
- La interfaz funciona con teclado, anuncia acciones importantes y no presenta
  desbordamiento horizontal entre 320 y 1280 píxeles.
- Pruebas, build, revisión, documentación y prueba manual deben completarse antes
  de marcar la historia como `Terminada`.

### Evidencia de cierre

- Frontend: 83 pruebas en 25 archivos, sin fallos.
- Build de producción correcto y sin advertencias de presupuesto.
- Prettier y `git diff --check` sin errores.
- Prueba manual real con Angular, Spring Boot y MySQL: selección explícita,
  cantidad 2, acumulación hasta 3, persistencia tras recarga, contador reactivo,
  subtotal exacto de ARS 50.702,25 y metadatos de Tienda A.
- Aislamiento manual: Tienda B mostró carrito vacío y contador cero mientras
  Tienda A conservó tres unidades.
- Vista de carrito comprobada a 320 píxeles sin desbordamiento horizontal; rutas,
  encabezados, controles, mensajes y confirmación se expusieron semánticamente.
- La validación manual detectó y corrigió una carrera de evento en la cantidad
  del detalle; quedó cubierta con una prueba de regresión.
- No se modificaron backend, esquema ni datos comerciales: el producto de prueba
  y su stock permanecen iguales.

### Deuda técnica aceptada

- ORD-01 reemplazará el botón deshabilitado por checkout invitado y validará
  autoritativamente precio y saldo dentro de MySQL.
- El carrito no sincroniza pestañas, dispositivos ni cuentas; el carrito servidor
  permanece en V2.
- Productos por peso requerirán unidad de medida y cantidades decimales después
  de validar ese vertical.

## ORD-01 — Checkout invitado con retiro

> Estado: terminada el 2026-07-30. Las decisiones ADR-058 a ADR-067 fueron
> aprobadas antes de iniciar la implementación.

### Historia

Como visitante quiero confirmar mi carrito para registrar un pedido con retiro y
recibir una referencia segura que me permita consultar la confirmación.

### Criterios de aceptación

- El checkout se abre desde un carrito no vacío y sólo ofrece retiro en el MVP
  inicial; envío, zonas, tarifas y franjas quedan fuera.
- Nombre y teléfono son obligatorios; correo y observaciones son opcionales.
- Los datos de contacto se guardan como snapshot del pedido, sin crear una cuenta
  ni deduplicar clientes.
- El frontend envía UUID de variante y cantidad; nunca precio, subtotal, stock,
  SKU, estado, nombre de base ni identificadores internos como autoridad.
- El backend vuelve a leer publicación, categoría, variante, precio, moneda y
  stock dentro de una transacción tenant.
- CART-01 continúa admitiendo enteros 1–99. `order_items.quantity` y reservas usan
  `DECIMAL(15,3)` y guardan `UNIT` para permitir una evolución futura por peso.
- Variantes repetidas se rechazan; todo pedido contiene de 1 a 50 líneas y el
  subtotal debe caber en `DECIMAL(15,2)`.
- Las filas de variante y balance se bloquean en orden estable. El stock
  disponible es balance físico menos reservas `ACTIVE` no vencidas.
- El pedido y todas sus reservas se crean juntos o se revierten juntos.
- Las reservas vencen 30 minutos después de crear el pedido. Reservas vencidas
  no reducen disponibilidad aunque su limpieza física sea posterior.
- El pedido nace `PENDING_CONFIRMATION`; estado de pedido y pago permanecen
  separados y CART-01 no crea pagos.
- Crear exige `Idempotency-Key`. Un replay idéntico devuelve el pedido original;
  reutilizar la clave con otro payload responde `409`.
- La respuesta de alta entrega UUID público, número visible y un token opaco
  derivado de una clave aleatoria del intento. MySQL conserva sólo SHA-256 del
  token.
- La consulta pública exige UUID y token; valores incorrectos responden `404` sin
  revelar si el pedido existe.
- Tienda A nunca crea, reserva ni consulta datos de Tienda B.
- La confirmación pública no expone teléfono completo, correo completo, token
  hash, claves internas, SKU ni información de conexión.
- La interfaz previene doble envío, distingue timeout incierto y permite repetir
  con la misma clave.
- Validaciones, errores, migración, pruebas concurrentes, revisión, documentación
  y prueba manual deben completarse antes de marcar `Terminada`.

### Evidencia de cierre

- Backend: regresión completa final de 82 pruebas y 7 pruebas focalizadas de
  ORD-01, todas sin fallos.
- La prueba concurrente se repitió tres veces por ejecución: en todos los casos
  una reserva obtuvo `201` y la competidora `409`, sin superar el balance físico.
- Frontend: 86 pruebas en 26 archivos, sin fallos; Prettier aplicado a los
  archivos modificados.
- Build Angular de producción correcto. Queda una advertencia no bloqueante:
  `cart-page.scss` supera por 98 bytes el presupuesto preventivo de 4 kB.
- Prueba manual real con Angular, Spring Boot y MySQL: carrito de tres unidades,
  formulario de contacto, retiro, pedido `PENDING_CONFIRMATION`, subtotal
  recalculado, reserva a treinta minutos y vaciado del carrito.
- La confirmación mostró contacto enmascarado y el enlace con token modificado
  respondió con el estado genérico “No encontramos el pedido”.
- La confirmación se revisó en escritorio y a 390 píxeles, sin desbordamiento
  horizontal del documento.
- La prueba manual creó únicamente datos ficticios en la base local ignorada por
  Git; no se usaron credenciales ni datos personales reales.

### Deuda técnica aceptada

- ORD-02 debe agregar operación administrativa, historial y transiciones de
  estado; PAY-01 debe consumir o liberar la reserva según el pago.
- La expiración cambia el estado al consultar o reintentar; una limpieza
  programada y la duración configurable quedan fuera de este MVP.
- Envíos, clientes persistentes y cantidades fraccionarias visibles quedan para
  las historias ya separadas del vertical carnicería.
- La infraestructura de pruebas completa emite avisos de cierre de conexiones
  entre contextos y Flyway advierte sobre MySQL 8.4; no produjeron fallos, pero
  conviene depurarlos en una historia de calidad.

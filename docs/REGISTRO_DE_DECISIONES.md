# Registro de decisiones

> Las entradas siguientes son propuestas ADR pendientes. Responsable de aprobación:
> Product Owner.

| ADR | Tema | Recomendación | Estado |
|---|---|---|---|
| ADR-001 | Vertical piloto | Indumentaria | Aceptada |
| ADR-002 | Multiempresa | Una base MySQL por comercio | Aceptada |
| ADR-003 | Autenticación | Sesión/cookie HttpOnly para web MVP | Aceptada |
| ADR-004 | Identidad | Usuario global + membresía por comercio | Aceptada |
| ADR-005 | URL de tienda | Path y SPA | Aceptada |
| ADR-006 | Frontend | Una app Angular con shells lazy | Pendiente |
| ADR-007 | Renderizado | CSR (SPA), sin SSR en MVP | Aceptada |
| ADR-008 | Producto vendible | Toda venta usa variante vendible | Aceptada |
| ADR-009 | Inventario | Una ubicación y stock por variante | Aceptada |
| ADR-010 | Stack backend | Spring Boot 3.5 + Java 21 LTS | Aceptada |
| ADR-011 | Pagos | Checkout Pro y OAuth desde la integración comercial | Aceptada |
| ADR-012 | Hosting real | MySQL administrado para primer cliente | Pendiente |
| ADR-013 | Registro de tenants | Base de control compartida | Aceptada |
| ADR-014 | Frontend base | Angular 22, Node 24 y SPA zoneless | Aceptada |
| ADR-015 | Contenedores locales | Docker Desktop con WSL 2 | Aceptada |
| ADR-016 | Routing tenant | Contexto por solicitud y datasource sin fallback | Aceptada |
| ADR-017 | Persistencia de sesión | Spring Session JDBC en la base de control | Aceptada |
| ADR-018 | Acceso administrativo | Login global y selección de comercio | Aceptada |
| ADR-019 | Autorización | Matriz fija `OWNER`, `ADMIN`, `STAFF` | Aceptada |
| ADR-020 | Alta del propietario | Provisión operativa controlada | Aceptada |
| ADR-021 | Recuperación de contraseña | Autoservicio diferido a V2 | Aceptada |
| ADR-022 | Protección del login | Límite básico por IP y cuenta | Aceptada |
| ADR-023 | Expiración de sesión | 30 minutos de inactividad | Aceptada |
| ADR-024 | Proveedor de identidad | Autenticación propia durante el MVP | Aceptada |
| ADR-025 | Identificadores tenant | BIGINT interno y UUID público | Aceptada |
| ADR-026 | Baja de categorías | Archivado lógico reversible | Aceptada |
| ADR-027 | Slug de categoría | Automático, único e inmutable | Aceptada |
| ADR-028 | Jerarquía de categorías | Categorías planas para el MVP | Aceptada |
| ADR-029 | Atributos de variante | Talle y color opcionales | Aceptada |
| ADR-030 | Editor de variantes | Filas manuales para el MVP | Aceptada |
| ADR-031 | Alta de producto | Producto y variantes en una transacción | Aceptada |
| ADR-032 | Ciclo de producto | Borrador, publicado y archivado | Aceptada |
| ADR-033 | SKU | Obligatorio y único por comercio | Aceptada |
| ADR-034 | Precio | Decimal exclusivamente por variante | Aceptada |
| ADR-035 | Concurrencia | Versión explícita y rechazo de edición obsoleta | Aceptada |
| ADR-036 | Listado de productos | Paginación desde CAT-02 | Aceptada |
| ADR-037 | Cantidades | Decimal con tres posiciones | Aceptada |
| ADR-038 | Ajustes | Entrada o salida, sin saldo absoluto | Aceptada |
| ADR-039 | Idempotencia | Clave obligatoria por ajuste | Aceptada |
| ADR-040 | Balance inicial | Cero lógico y materialización lazy | Aceptada |
| ADR-041 | Estado comercial | Inventario ajustable aunque esté retirado | Aceptada |
| ADR-042 | Motivos | Catálogo fijo y nota para `OTHER` | Aceptada |
| ADR-043 | Auditoría | Historial paginado visible | Aceptada |
| ADR-044 | Stock bajo | Umbral diferido a DASH-01 | Aceptada |
| ADR-045 | Productos agotados | Visibles con estado no disponible | Aceptada |
| ADR-046 | Stock público | Sólo disponibilidad booleana | Aceptada |
| ADR-047 | Imágenes | Placeholder y MEDIA-01 antes del piloto | Aceptada |
| ADR-048 | Categorías públicas | Sólo categorías con productos visibles | Aceptada |
| ADR-049 | Orden público | Alfabético estable | Aceptada |
| ADR-050 | Caché del catálogo | `no-store` durante el MVP | Aceptada |

## Plantilla ADR

### ADR-XXX — Título

- **Fecha:**
- **Estado:** Propuesta / Aceptada / Rechazada / Reemplazada
- **Responsable de aprobación:** Product Owner
- **Contexto:**
- **Problema:**
- **Alternativas:**
- **Decisión:**
- **Consecuencias:**

## ADR aceptadas el 2026-07-23

### ADR-001 — Indumentaria como vertical piloto

- **Fecha:** 2026-07-23
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** El producto contempla varios rubros, pero el MVP necesita una
  experiencia concreta.
- **Alternativas:** carnicería, indumentaria, comercio general.
- **Decisión:** validar primero indumentaria.
- **Consecuencias:** talle, color, SKU y combinaciones de variantes entran al MVP;
  peso y franjas horarias quedan fuera.

### ADR-002 — Base MySQL separada por comercio

- **Fecha:** 2026-07-23
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Los datos de un comercio no pueden mezclarse con otro.
- **Alternativas:** esquema compartido con `tenant_id`; base separada por comercio.
- **Decisión:** una base de negocio separada por comercio.
- **Consecuencias:** mejora el aislamiento y restore individual, pero aumenta
  provisión, conexiones, migraciones, monitoreo, backups y costo. Todas las bases
  deben conservar el mismo esquema. El registro/enrutamiento central fue resuelto
  posteriormente por ADR-013.

### ADR-003 — Sesión web en cookie segura

- **Fecha:** 2026-07-23
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Alternativas:** sesión/cookie; JWT y refresh token.
- **Decisión:** sesión en cookie `HttpOnly`, `Secure` y `SameSite`.
- **Consecuencias:** se debe diseñar CSRF, CORS, logout y persistencia de sesiones.

### ADR-004 — Usuario global y membresías por comercio

- **Fecha:** 2026-07-27
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Una persona puede administrar uno o más comercios, mientras que
  sus permisos deben quedar aislados por comercio.
- **Problema:** Definir dónde vive la identidad y cómo se autoriza el acceso a una
  base de comercio antes de enrutar una operación.
- **Alternativas:** identidad global en la base de control con membresías por
  comercio; usuarios independientes dentro de cada base de comercio.
- **Decisión:** almacenar la identidad global en la base de control y representar
  el acceso a cada comercio mediante una membresía con rol.
- **Consecuencias:** una persona puede usar una misma cuenta en varios comercios y
  la recuperación de acceso se centraliza. La base de control pasa a contener
  datos sensibles y toda autorización deberá comprobar una membresía activa antes
  de seleccionar la base del comercio. Una membresía nunca concede acceso a otro
  comercio por compartir usuario.

### ADR-005/007 — Tienda por path como SPA

- **Fecha:** 2026-07-23
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Alternativas:** path SPA; subdominio; SSR/híbrido.
- **Decisión:** path de tienda y renderizado en navegador para el MVP.
- **Consecuencias:** despliegue simple; SEO y dominios propios se difieren.

### ADR-008 — Variante vendible uniforme

- **Fecha:** 2026-07-23
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Alternativas:** variante uniforme; producto simple con variantes opcionales.
- **Decisión:** toda venta referencia una variante; la UI puede ocultar una variante base.
- **Consecuencias:** precio, SKU y stock tienen un único modelo.

### ADR-009 — Una existencia por variante y comercio

- **Fecha:** 2026-07-27
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** El MVP de indumentaria necesita controlar existencias sin modelar
  todavía sucursales, depósitos ni transferencias.
- **Problema:** Definir el nivel de detalle inicial del inventario.
- **Alternativas:** una existencia por variante en el comercio; existencias por
  variante y ubicación física.
- **Decisión:** mantener una única existencia disponible por variante dentro de la
  base de cada comercio.
- **Consecuencias:** simplifica ventas, ajustes y alertas de stock del MVP. Se
  conservarán movimientos auditables para poder explicar cada cambio. Múltiples
  ubicaciones y transferencias quedan fuera del MVP y requerirán una migración
  explícita del modelo.

### ADR-010 — Spring Boot 3.5 y Java 21

- **Fecha:** 2026-07-23
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Alternativas:** Boot 3.5/Java 21; Boot 4.1/Java 25.
- **Decisión:** línea madura Spring Boot 3.5 con Java 21 LTS.
- **Consecuencias:** menor novedad y compatibilidad amplia; habrá una actualización
  mayor futura planificada.

### ADR-011 — OAuth y Checkout Pro

- **Fecha:** 2026-07-23
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Alternativas:** token manual temporal; OAuth por comercio.
- **Decisión:** OAuth forma parte de la integración comercial con Checkout Pro.
- **Consecuencias:** mayor esfuerzo de onboarding y seguridad, sin intercambio
  manual de credenciales del vendedor.

### ADR-013 — Base de control compartida

- **Fecha:** 2026-07-23
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** El backend necesita resolver qué base separada corresponde a cada
  tienda sin aceptar nombres de base proporcionados por el navegador.
- **Alternativas:** base de control compartida; configuración externa por despliegue.
- **Decisión:** usar una base de control con comercio, slug, estado, referencia
  lógica de conexión y versión de migración.
- **Consecuencias:** facilita onboarding, enrutamiento y operación central, pero
  agrega una base crítica. Las credenciales permanecerán en secretos externos o
  cifradas con una clave externa; nunca se almacenarán en texto plano.

### ADR-014 — Angular 22 y Node 24

- **Fecha:** 2026-07-27
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** El Sprint 1 necesita fijar el toolchain del frontend.
- **Alternativas:** Angular 22 activo; Angular 21 LTS.
- **Decisión:** Angular 22 con Node 24.15, TypeScript 6 administrado por Angular,
  componentes standalone, modo estricto, ejecución zoneless, SCSS y Vitest.
- **Consecuencias:** mayor ventana de soporte y APIs actuales; las librerías nuevas
  deberán demostrar compatibilidad con Angular 22 y TypeScript 6.

### ADR-015 — Docker Desktop para desarrollo

- **Fecha:** 2026-07-27
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** MySQL reproducible y Testcontainers requieren un runtime compatible.
- **Alternativas:** Docker Desktop; Podman Desktop; MySQL nativo.
- **Decisión:** Docker Desktop con backend WSL 2 para desarrollo.
- **Consecuencias:** integración directa con Compose/Testcontainers y consumo de
  recursos local. La elegibilidad de licencia deberá revisarse si cambia el tamaño
  o naturaleza comercial de la organización.

### ADR-016 — Routing tenant sin fallback

- **Fecha:** 2026-07-27
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** CORE-01 debe seleccionar una base tenant después de resolver el
  comercio, sin aceptar topología proporcionada por el cliente.
- **Problema:** Mantener separado el datasource de control y elegir de forma segura
  una conexión de negocio durante una solicitud.
- **Alternativas:** router con `AbstractRoutingDataSource` y contexto acotado;
  factoría explícita de repositorios por operación; cambiar el catálogo de un pool
  compartido.
- **Decisión:** JPA usa la base de control. El acceso tenant usa pools
  independientes detrás de `AbstractRoutingDataSource`, sin datasource por defecto
  y con fallback deshabilitado. Un filtro establece una clave validada en un
  `ThreadLocal` y la elimina siempre al finalizar.
- **Consecuencias:** los repositorios tenant futuros podrán trabajar contra una
  abstracción estable y una ausencia de contexto falla cerrada. Deben limitarse
  los pools por tenant, cerrar pools retirados y prohibir async tenant hasta
  implementar propagación segura. Control y tenant no forman una transacción
  atómica.

## ADR aceptadas el 2026-07-28

### ADR-017 — Sesiones JDBC en la base de control

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** La sesión web aprobada debe sobrevivir a reinicios y permitir que
  más de una instancia del backend reconozca al mismo usuario.
- **Problema:** Elegir dónde persistir las sesiones del panel administrativo.
- **Alternativas:** memoria del proceso; Spring Session JDBC en la base de
  control; Redis como servicio adicional.
- **Decisión:** usar Spring Session JDBC con tablas administradas por Flyway en la
  base de control. Redis queda fuera del MVP.
- **Consecuencias:** las sesiones no dependen de una instancia concreta, pero la
  base de control aumenta su criticidad y carga. La sesión conservará sólo la
  identidad global mínima; los roles por comercio se consultarán en membresías
  vigentes para que una revocación tenga efecto inmediato.

### ADR-018 — Login global y selección de comercio

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Una identidad global puede pertenecer a más de un comercio.
- **Problema:** Decidir si el usuario inicia sesión dentro de una tienda o una vez
  para toda la plataforma.
- **Alternativas:** login global con selector; login independiente dentro del path
  de cada comercio.
- **Decisión:** autenticar globalmente y, después, mostrar las membresías activas.
  Si existe una sola, la interfaz puede seleccionarla automáticamente.
- **Consecuencias:** la autenticación no concede acceso a ningún tenant por sí
  sola. Cada solicitud administrativa conserva el slug en la URL y el backend
  vuelve a validar la membresía antes de abrir la conexión tenant.

### ADR-019 — Roles fijos para el MVP

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** El MVP necesita autorización verificable sin construir todavía un
  editor de permisos.
- **Problema:** Definir el alcance inicial de `OWNER`, `ADMIN` y `STAFF`.
- **Alternativas:** matriz fija de roles; permisos configurables por comercio.
- **Decisión:** usar una matriz fija. `OWNER` administra todo el comercio,
  membresías y conexión de pagos; `ADMIN` opera catálogo, inventario, pedidos,
  dashboard y configuración básica; `STAFF` consulta catálogo e inventario,
  ajusta stock y opera pedidos. Sólo `OWNER` puede gestionar membresías o datos
  sensibles de pago.
- **Consecuencias:** las reglas son simples de explicar y probar, pero un comercio
  no puede personalizarlas en el MVP. Los guards frontend sólo orientan la
  navegación; Spring Security aplica la autorización real.

### ADR-020 — Provisión operativa del primer `OWNER`

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** La gestión completa de usuarios desde la interfaz pertenece a la
  segunda versión, pero el piloto necesita una cuenta propietaria.
- **Problema:** Crear la primera identidad y membresía sin versionar una
  contraseña ni adelantar un sistema de invitaciones.
- **Alternativas:** proceso operativo idempotente con secretos externos;
  invitaciones por correo desde el MVP.
- **Decisión:** proveer el primer `OWNER` mediante un comando o proceso operativo
  explícito, deshabilitado por defecto y alimentado por variables de entorno.
- **Consecuencias:** el onboarding inicial requiere intervención de plataforma.
  El proceso nunca guardará una contraseña plana, no reemplazará credenciales de
  forma silenciosa y deberá poder auditarse.

### ADR-021 — Recuperación de contraseña en V2

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** La recuperación autoservicio requiere correo transaccional,
  tokens de un solo uso, límites contra abuso e invalidación de sesiones.
- **Problema:** Decidir si esa capacidad bloquea el núcleo del MVP.
- **Alternativas:** rotación operativa controlada durante el piloto; recuperación
  autoservicio por correo desde CORE-02.
- **Decisión:** mantener la recuperación autoservicio en la segunda versión. El
  piloto utilizará una rotación operativa segura.
- **Consecuencias:** se reduce el alcance de CORE-02, a cambio de un procedimiento
  manual temporal. La solución futura almacenará sólo el hash del token, tendrá
  vencimiento corto e invalidará sesiones al cambiar la contraseña.

### ADR-022 — Límite básico de intentos de login

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Verificar contraseñas es deliberadamente costoso y el endpoint de
  login está expuesto a fuerza bruta y agotamiento de CPU.
- **Problema:** Agregar una protección razonable sin incorporar Redis al MVP.
- **Alternativas:** limitador acotado en memoria por IP y cuenta; almacenamiento
  distribuido desde el inicio; depender sólo de infraestructura externa.
- **Decisión:** aplicar un límite básico y acotado por combinación de IP confiable
  y correo normalizado, con respuestas que no revelen si la cuenta existe.
- **Consecuencias:** protege la primera instancia, pero el contador no se comparte
  entre réplicas. En el despliegue real se complementará con el borde de red y se
  evaluará un almacén distribuido al escalar.

### ADR-023 — Expiración por inactividad

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** El MVP no incluye la opción “Recordarme” y debe equilibrar la
  seguridad de equipos compartidos con la operación cotidiana del comercio.
- **Problema:** Definir cuánto tiempo puede permanecer inactiva una sesión.
- **Alternativas:** 30 minutos de inactividad; 8 horas de inactividad.
- **Decisión:** expirar la sesión después de 30 minutos sin actividad. Cada
  solicitud válida renueva el período y `SESSION_TIMEOUT` permite configurarlo
  por ambiente sin cambiar código.
- **Consecuencias:** una computadora abandonada conserva acceso durante menos
  tiempo, a cambio de requerir un nuevo login después de pausas prolongadas.

### ADR-024 — Autenticación propia durante el MVP

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** CORE-02 ya implementa autenticación propia, sesiones seguras,
  identidades globales y membresías por comercio. Firebase Authentication podría
  simplificar en el futuro el login social, la verificación de correo y la
  recuperación de acceso para clientes y administradores.
- **Problema:** decidir si incorporar ahora un proveedor externo de identidad o
  completar primero el flujo comercial del MVP.
- **Alternativas:** mantener autenticación propia para el MVP; sustituir ahora la
  validación de credenciales por Firebase Authentication.
- **Decisión:** conservar la autenticación propia durante el MVP y analizar
  Firebase después, como proveedor de identidad desacoplado de los roles,
  membresías y autorizaciones almacenados en Comercio Flex.
- **Consecuencias:** no se agrega ahora el SDK, configuración, costo ni dependencia
  operativa de Firebase. El modelo interno seguirá usando identificadores propios
  para evitar que una integración futura condicione las claves del negocio. Una
  eventual adopción deberá definir migración de cuentas, vinculación por UID,
  sesiones, revocación y coexistencia de proveedores mediante un ADR específico.

## ADR aceptadas el 2026-07-28 para Sprint 4

### ADR-025 — Identificadores internos y públicos en bases tenant

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Categorías será la primera entidad comercial que establecerá el
  patrón reutilizado por productos, variantes, inventario y pedidos.
- **Problema:** definir una clave eficiente para relaciones internas sin exponer
  identificadores correlativos en la API.
- **Alternativas:** `BIGINT` interno más UUID público; UUID como clave primaria.
- **Decisión:** usar `BIGINT` como clave primaria interna y UUID almacenado como
  `BINARY(16)` como identificador público.
- **Consecuencias:** los índices y claves foráneas permanecen compactos y las URLs
  usan identificadores opacos. Los repositorios deben convertir ambos formatos y
  la API nunca expone el `BIGINT`.

### ADR-026 — Archivado lógico de categorías

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una categoría podrá ser referenciada por productos e históricos.
- **Problema:** definir cómo retirar una categoría sin perder trazabilidad.
- **Alternativas:** activación/desactivación reversible; borrado físico cuando no
  existan productos.
- **Decisión:** retirar categorías mediante estado `INACTIVE`, permitiendo su
  reactivación y sin exponer borrado físico en el MVP.
- **Consecuencias:** el historial y las referencias futuras permanecen intactos.
  Las consultas deben distinguir categorías activas e inactivas.

### ADR-027 — Slug automático e inmutable

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el slug podrá formar parte de navegación y URLs públicas futuras.
- **Problema:** evitar enlaces rotos y colisiones al renombrar categorías.
- **Alternativas:** slug generado e inmutable; slug editable o regenerado.
- **Decisión:** el backend genera un slug único al crear la categoría y no lo
  modifica cuando cambia el nombre.
- **Consecuencias:** las URLs permanecen estables y el administrador no controla
  SEO avanzado en el MVP. Una colisión se informa explícitamente.

### ADR-028 — Categorías planas para el MVP

- **Fecha:** 2026-07-28
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el piloto de indumentaria necesita organizar el catálogo sin que
  se haya validado una necesidad concreta de subcategorías.
- **Problema:** decidir si modelar desde ahora una jerarquía arbitraria.
- **Alternativas:** categorías planas; árbol con relación padre.
- **Decisión:** utilizar una lista plana durante el MVP.
- **Consecuencias:** se evitan ciclos, profundidad, breadcrumbs y reglas de
  archivado de ramas. Una jerarquía futura requerirá un nuevo ADR y migración.

## ADR aceptadas el 2026-07-29 para Sprint 5

### ADR-029 — Talle y color opcionales

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el piloto de indumentaria necesita talle y color, pero el núcleo
  también debe representar productos simples.
- **Problema:** elegir entre atributos explícitos o un motor genérico de opciones.
- **Alternativas:** columnas opcionales de talle/color; tablas genéricas de
  definiciones, opciones y valores.
- **Decisión:** cada variante tendrá talle y color opcionales. Ambos vacíos
  representan la variante base de un producto simple.
- **Consecuencias:** CAT-02 evita un modelo EAV y cubre el piloto. Incorporar otros
  atributos requerirá una migración y un ADR después de validar otro rubro.

### ADR-030 — Editor manual de variantes

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una matriz automática talle por color mejora la carga masiva, pero
  aumenta considerablemente la lógica del formulario.
- **Problema:** equilibrar experiencia operativa con alcance de Sprint 5.
- **Alternativas:** filas manuales; generador cartesiano de combinaciones.
- **Decisión:** cargar variantes mediante filas manuales en el MVP.
- **Consecuencias:** el primer flujo es más simple de explicar y probar, aunque
  cargar muchas combinaciones será más lento. La matriz queda como mejora validada
  por el uso del piloto.

### ADR-031 — Alta atómica de producto y variantes

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** por ADR-008 todo producto vendible necesita al menos una variante.
- **Problema:** evitar productos abandonados sin una unidad vendible.
- **Alternativas:** alta atómica con variantes; crear producto vacío y completar
  después.
- **Decisión:** crear el producto y al menos una variante en una única transacción.
- **Consecuencias:** si cualquier variante es inválida, no se persiste ninguna
  parte del agregado. Después del alta, cada variante conserva UUID y ciclo propio.

### ADR-032 — Ciclo de publicación y archivado

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** guardar información no debe publicarla accidentalmente.
- **Problema:** distinguir preparación, visibilidad y retiro.
- **Alternativas:** estados `DRAFT`, `PUBLISHED`, `ARCHIVED`; booleano publicado.
- **Decisión:** utilizar la máquina de estados explícita. Las variantes persistidas
  se activan o desactivan, pero no se borran físicamente.
- **Consecuencias:** publicar exige categoría activa y al menos una variante
  activa. Para desactivar la última variante de un producto publicado primero hay
  que volverlo a borrador. Restaurar un archivado siempre vuelve a `DRAFT`.

### ADR-033 — SKU obligatorio y único por comercio

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** inventario y pedidos necesitarán identificar inequívocamente cada
  variante.
- **Problema:** definir alcance, normalización y reutilización del SKU.
- **Alternativas:** obligatorio y único en la base tenant; opcional o único por
  producto.
- **Decisión:** normalizar a mayúsculas, exigirlo y mantenerlo único por comercio.
  Puede corregirse conservando el UUID de la variante y no se reutiliza mientras
  exista su fila.
- **Consecuencias:** mejora la operación e integración futura, pero exige que el
  administrador asigne un SKU a cada variante.

### ADR-034 — Precio decimal por variante

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** ADR-008 ubica precio, SKU y stock en la variante vendible.
- **Problema:** evitar dos fuentes de verdad y pérdida de precisión.
- **Alternativas:** precio sólo por variante; precio base con ajuste por variante.
- **Decisión:** almacenar `DECIMAL(15,2)`, manipular `BigDecimal` y transportar el
  valor como string decimal canónico en JSON.
- **Consecuencias:** frontend y backend validan explícitamente el formato; la
  moneda pertenece a la configuración del comercio y no se repite por variante.

### ADR-035 — Concurrencia optimista mediante versión

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** dos administradores pueden editar un producto o variante al mismo
  tiempo.
- **Problema:** impedir que una edición obsoleta sobrescriba otra silenciosamente.
- **Alternativas:** campo `version` en el contrato; headers `ETag` e `If-Match`.
- **Decisión:** cada recurso editable expone y recibe una versión. Un `UPDATE`
  incrementa la versión sólo si coincide con la leída; en otro caso responde
  conflicto.
- **Consecuencias:** Angular debe ofrecer recarga ante `409`. ETag puede adoptarse
  posteriormente sin eliminar el control optimista interno.

### ADR-036 — Paginación administrativa de productos

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el volumen de productos y búsquedas por SKU será mayor que el de
  categorías.
- **Problema:** evitar cargar el catálogo administrativo completo.
- **Alternativas:** paginación desde CAT-02; lista completa inicial.
- **Decisión:** paginar en el servidor con 20 elementos por defecto y 100 como
  máximo, incorporando búsqueda y filtros de categoría/estado.
- **Consecuencias:** el contrato es más elaborado desde el comienzo, pero evita
  retrabajo y consumo creciente en Angular y MySQL.

## ADR aceptadas el 2026-07-29 para Sprint 6

### ADR-037 — Cantidades decimales preparadas para peso

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** indumentaria usa unidades enteras, pero otros rubros venderán por
  peso.
- **Problema:** elegir una representación que no obligue a migrar el ledger.
- **Alternativas:** `DECIMAL(15,3)` desde ahora; cantidades enteras y migración
  futura.
- **Decisión:** persistir cantidades decimales y transportarlas como strings. La
  interfaz inicial restringe indumentaria a unidades enteras.
- **Consecuencias:** el esquema queda preparado para peso sin afirmar todavía que
  exista una unidad de venta configurable.

### ADR-038 — Ajustes mediante entrada o salida

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** inventario es un registro contable, no un campo libre.
- **Problema:** decidir entre variación y saldo absoluto.
- **Alternativas:** entrada/salida con cantidad positiva; fijar saldo final.
- **Decisión:** INV-01 sólo permite entrada o salida. El backend deriva el delta
  y el saldo resultante.
- **Consecuencias:** cada movimiento es explícito y concurrentemente componible.
  Un conteo físico absoluto requerirá un caso de uso futuro.

### ADR-039 — Idempotencia obligatoria para ajustes

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** un timeout puede ocultar una operación ya confirmada.
- **Problema:** impedir ajustes duplicados por reintentos.
- **Alternativas:** clave persistida por intento; confiar en el doble-submit de UI.
- **Decisión:** cada ajuste exige `Idempotency-Key`. Un replay idéntico devuelve
  el resultado original y una reutilización incompatible produce conflicto.
- **Consecuencias:** se agrega unicidad y comparación de payload, reutilizable por
  pedidos futuros.

### ADR-040 — Saldo cero lógico y materialización diferida

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** catálogo no debe depender directamente de la implementación de
  inventario.
- **Problema:** decidir cuándo crear la fila de balance.
- **Alternativas:** cero lógico con fila lazy; balance cero creado con variante.
- **Decisión:** la ausencia de balance representa cero y la fila se materializa
  bajo bloqueo en el primer ajuste.
- **Consecuencias:** se reduce acoplamiento; listados deben usar `LEFT JOIN` y
  `COALESCE` de manera consistente.

### ADR-041 — Inventario independiente del estado comercial

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** retirar una variante de venta no elimina la mercadería física.
- **Problema:** decidir si permitir ajustes sobre variantes inactivas o archivadas.
- **Alternativas:** permitir con advertencia; bloquear.
- **Decisión:** cualquier variante persistida puede consultarse y ajustarse.
- **Consecuencias:** devoluciones, mermas y conciliaciones siguen siendo posibles;
  la interfaz debe mostrar el estado comercial.

### ADR-042 — Motivos estructurados

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el historial debe ser comprensible y reportable.
- **Problema:** elegir entre motivos controlados o texto libre.
- **Alternativas:** catálogo fijo más nota; texto libre.
- **Decisión:** usar `RECEIPT`, `CORRECTION`, `DAMAGE`, `RETURN` y `OTHER`;
  `OTHER` exige nota.
- **Consecuencias:** los datos son comparables y agregar motivos requerirá una
  evolución explícita.

### ADR-043 — Historial visible desde INV-01

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** registrar auditoría sin poder consultarla limita su valor operativo.
- **Problema:** decidir si incluir la UI y API de movimientos.
- **Alternativas:** historial paginado visible; sólo persistencia interna.
- **Decisión:** incluir consulta paginada, más reciente primero.
- **Consecuencias:** aumenta el alcance del sprint, pero la trazabilidad puede
  verificarse manualmente.

### ADR-044 — Umbral de stock bajo diferido

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** DASH-01 necesitará definir stock bajo.
- **Problema:** agregar ahora una regla aún no validada por el piloto.
- **Alternativas:** umbral por variante en INV-01; decidirlo en DASH-01.
- **Decisión:** diferir umbrales y alertas.
- **Consecuencias:** Sprint 6 se concentra en balance y ledger; DASH-01 deberá
  definir alcance y migración antes de implementar alertas.

## ADR aceptadas el 2026-07-29 para Sprint 7

### ADR-045 — Productos agotados visibles

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una publicación comercial no debe aparecer y desaparecer por un
  cambio temporal de inventario.
- **Problema:** decidir si ocultar los productos publicados sin existencia.
- **Alternativas:** mantenerlos visibles como no disponibles; ocultarlos.
- **Decisión:** mostrar productos publicados agotados con un estado explícito de
  falta de stock.
- **Consecuencias:** las URLs y el descubrimiento permanecen estables; el futuro
  carrito deberá impedir seleccionar una variante no disponible.

### ADR-046 — Disponibilidad pública sin cantidad exacta

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el saldo cambia y constituye información operativa del comercio.
- **Problema:** definir cuánto inventario exponer anónimamente.
- **Alternativas:** booleano disponible/no disponible; cantidad exacta.
- **Decisión:** exponer sólo disponibilidad booleana.
- **Consecuencias:** se protege información comercial y el contrato sirve tanto
  para unidades como para peso. Checkout deberá revalidar el saldo real.

### ADR-047 — Imágenes separadas de STORE-01

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** todavía no existe almacenamiento, carga ni optimización de medios.
- **Problema:** evitar que el catálogo público incorpore silenciosamente una
  arquitectura de archivos.
- **Alternativas:** placeholder profesional y una historia MEDIA-01; agregar
  almacenamiento y administración durante STORE-01.
- **Decisión:** STORE-01 usa un placeholder accesible. MEDIA-01 se priorizará
  antes de la demostración comercial y no almacenará binarios pesados en MySQL.
- **Consecuencias:** Sprint 7 conserva tamaño L; el piloto real sigue requiriendo
  resolver object storage, formatos, límites, thumbnails y seguridad.

### ADR-048 — Categorías públicas no vacías

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una categoría activa puede no contener productos publicados.
- **Problema:** evitar filtros públicos que no aporten resultados.
- **Alternativas:** sólo categorías con productos visibles; todas las activas.
- **Decisión:** listar únicamente categorías activas que contengan al menos un
  producto público.
- **Consecuencias:** la navegación es útil, aunque la lista pública no replica
  literalmente toda la configuración administrativa.

### ADR-049 — Orden alfabético estable

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** no existe todavía ranking ni orden manual.
- **Problema:** elegir un orden predecible y compatible con paginación.
- **Alternativas:** nombre estable; última modificación.
- **Decisión:** ordenar por nombre y usar el identificador interno sólo como
  desempate técnico.
- **Consecuencias:** editar un producto no cambia su posición inesperadamente;
  orden manual y destacados quedan para una historia futura.

### ADR-050 — Catálogo sin caché pública inicial

- **Fecha:** 2026-07-29
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** las respuestas incluyen disponibilidad derivada del inventario.
- **Problema:** una caché compartida podría servir stock desactualizado o mezclar
  claves incompletas entre comercios y filtros.
- **Alternativas:** `Cache-Control: no-store`; caché pública corta.
- **Decisión:** responder `no-store` durante el MVP.
- **Consecuencias:** se prioriza consistencia y aislamiento. CDN, ETag e
  invalidación se evaluarán con métricas reales.

## ADR aceptadas el 2026-07-30 para Sprint 8

### ADR-051 — Separar carrito de checkout y pedido

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** ORD-01 reunía estado frontend, datos personales, entrega,
  persistencia, inventario y concurrencia en una historia XL.
- **Problema:** mantener Sprint 8 revisable sin ocultar riesgos transaccionales.
- **Alternativas:** CART-01 local seguido de ORD-01; implementar todo junto.
- **Decisión:** Sprint 8 implementa CART-01. Checkout y creación del pedido
  permanecen en ORD-01.
- **Consecuencias:** no hay migración ni descuento de stock en Sprint 8; la
  validación autoritativa continúa siendo responsabilidad del futuro checkout.

### ADR-052 — Persistencia local sin datos personales

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el visitante debe conservar su selección al recargar.
- **Problema:** elegir persistencia antes de disponer de identidad de cliente.
- **Alternativas:** `localStorage`; memoria de la pestaña; carrito servidor.
- **Decisión:** usar una clave versionada de `localStorage` y almacenar sólo
  identificadores públicos, nombres visibles, opciones, precio y cantidad.
- **Consecuencias:** el carrito no se sincroniza entre dispositivos y cualquier
  contenido leído debe validarse como entrada no confiable.

### ADR-053 — Un carrito independiente por comercio

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** un navegador puede visitar más de una tienda.
- **Problema:** evitar mezclar productos entre tenants.
- **Alternativas:** clave por `storeSlug`; vaciar al cambiar de tienda.
- **Decisión:** conservar un carrito separado por comercio.
- **Consecuencias:** cambiar de ruta selecciona otro estado y contador sin
  eliminar selecciones anteriores.

### ADR-054 — Alta desde el detalle con variante explícita

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** indumentaria puede requerir talle y color.
- **Problema:** impedir que un alta rápida seleccione una opción incorrecta.
- **Alternativas:** selector en detalle; alta rápida desde tarjetas.
- **Decisión:** agregar sólo desde el detalle y exigir selección explícita.
- **Consecuencias:** el flujo tiene un paso adicional pero es claro, accesible y
  reduce errores.

### ADR-055 — Revalidar el snapshot al abrir el carrito

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** precio, publicación y disponibilidad cambian después del alta.
- **Problema:** comunicar datos vigentes sin convertir el carrito en servidor.
- **Alternativas:** releer el catálogo público; confiar en el snapshot.
- **Decisión:** agrupar líneas por producto, releer cada detalle y actualizar
  datos visibles. Las líneas retiradas se conservan marcadas para que el usuario
  comprenda el cambio.
- **Consecuencias:** se realizan varias lecturas pequeñas; ORD-01 igualmente
  deberá validar precio y stock dentro de su transacción.

### ADR-056 — Cantidad entera para el piloto

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el vertical piloto es indumentaria; venta por peso es futura.
- **Problema:** evitar introducir unidades configurables sin validación comercial.
- **Alternativas:** enteros ahora; decimales y unidades ahora.
- **Decisión:** CART-01 admite cantidades enteras. El futuro pedido mantendrá
  capacidad decimal para otros rubros.
- **Consecuencias:** productos por peso requerirán evolucionar catálogo y UI, no
  sólo cambiar el control de cantidad.

### ADR-057 — Máximo local de 99 unidades

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** la API pública no revela el saldo exacto.
- **Problema:** limitar valores accidentales sin prometer disponibilidad.
- **Alternativas:** rango 1–99; sin máximo frontend.
- **Decisión:** validar de 1 a 99 y explicar que el checkout confirmará stock.
- **Consecuencias:** el límite es una protección de UX, no una reserva ni una
  garantía comercial.

## ADR aceptadas el 2026-07-30 para Sprint 9

### ADR-058 — Preparar pedidos para cantidades futuras

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una futura carnicería necesitará peso, pero el piloto actual es
  indumentaria.
- **Problema:** evitar una migración estructural sin ampliar ahora la interfaz.
- **Alternativas:** persistencia decimal y unidad `UNIT`; enteros solamente.
- **Decisión:** items y reservas usan `DECIMAL(15,3)` y guardan unidad; ORD-01
  valida enteros y `UNIT`.
- **Consecuencias:** venta por peso todavía requerirá reglas de catálogo y UI,
  pero el histórico del pedido ya admite cantidades decimales.

### ADR-059 — Retiro como único método inicial

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** todavía no existen zonas, tarifas ni métodos configurables.
- **Problema:** no inventar reglas de envío sin comercio piloto.
- **Alternativas:** retiro; retiro más envío.
- **Decisión:** ORD-01 implementa `PICKUP`.
- **Consecuencias:** envío se incorporará como ORD-01B con configuración propia.

### ADR-060 — Contacto mínimo del invitado

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el comercio debe poder coordinar el retiro.
- **Problema:** equilibrar contacto y minimización de datos.
- **Alternativas:** nombre y teléfono obligatorios con correo opcional; exigir
  también correo.
- **Decisión:** nombre y teléfono obligatorios; correo y observaciones opcionales.
- **Consecuencias:** Mercado Pago podrá requerir completar o validar correo en
  PAY-01.

### ADR-061 — Cliente como snapshot del pedido

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** no existe identidad pública ni CRM validado.
- **Problema:** evitar deduplicación automática y consentimiento prematuro.
- **Alternativas:** snapshot; entidad cliente reutilizable.
- **Decisión:** ORD-01 guarda contacto dentro de `orders`.
- **Consecuencias:** gestión de clientes se diseñará separadamente.

### ADR-062 — Reserva separada del balance físico

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** crear un pedido no equivale todavía a una venta confirmada.
- **Problema:** evitar sobreventa sin falsificar el inventario físico.
- **Alternativas:** reserva; descuento inmediato.
- **Decisión:** `inventory_reservations` compromete cantidad sin modificar
  `inventory_balances`.
- **Consecuencias:** disponibilidad es balance menos reservas activas y PAY-01
  deberá consumirlas al confirmar.

### ADR-063 — Reserva de treinta minutos

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** un checkout abandonado no debe bloquear stock indefinidamente.
- **Problema:** fijar una ventana inicial sin configuración comercial.
- **Alternativas:** 30 minutos; sin vencimiento.
- **Decisión:** expirar lógicamente a los 30 minutos en UTC.
- **Consecuencias:** limpieza programada y duración configurable quedan como
  evolución; las consultas ignoran inmediatamente reservas vencidas. Las
  conexiones tenant fijan explícitamente UTC para que Java y MySQL comparen el
  mismo instante sin depender de la zona horaria de la máquina.

### ADR-064 — Pedido pendiente de confirmación

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** aún no existe pago ni aceptación administrativa.
- **Problema:** no prometer una compra confirmada antes de esas etapas.
- **Alternativas:** `PENDING_CONFIRMATION`; confirmado.
- **Decisión:** usar `PENDING_CONFIRMATION` y mantener pago como máquina separada.
- **Consecuencias:** ORD-02 definirá aceptación, rechazo y cancelación.

### ADR-065 — Consulta mediante token con hash

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el invitado no tiene cuenta.
- **Problema:** permitir consulta sin habilitar enumeración de pedidos.
- **Alternativas:** UUID más token; número y teléfono.
- **Decisión:** entregar un token aleatorio una vez y persistir sólo SHA-256.
- **Consecuencias:** perder el token exige soporte; respuestas inválidas fallan
  como no encontradas.

### ADR-066 — Creación idempotente

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** doble clic y timeout pueden repetir el POST.
- **Problema:** impedir pedidos y reservas duplicados.
- **Alternativas:** `Idempotency-Key`; protección sólo frontend.
- **Decisión:** clave UUID única por tenant más fingerprint canónico del comando.
- **Consecuencias:** replay igual devuelve el original; payload distinto produce
  conflicto.

### ADR-067 — Contrato sin precio enviado como autoridad

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** `localStorage` puede manipularse.
- **Problema:** decidir qué datos comerciales acepta la creación.
- **Alternativas:** enviar sólo variante/cantidad; aceptar snapshots del carrito.
- **Decisión:** el request no contiene precios ni totales autoritativos.
- **Consecuencias:** Spring recalcula snapshots y subtotal dentro de MySQL.

## ADR aceptadas el 2026-07-30 para Sprint 10

### ADR-068 — Integración y rama de ORD-02

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** ORD-01 estaba terminada en una rama independiente.
- **Problema:** iniciar ORD-02 sin mezclar historias ni perder trazabilidad.
- **Alternativas:** integrar y abrir una rama nueva; continuar en la rama de ORD-01.
- **Decisión:** integrar ORD-01 por fast-forward y desarrollar ORD-02 en
  `codex/feat-order-management`.
- **Consecuencias:** las historias conservan revisión y reversión independientes.

### ADR-069 — Ciclo operativo del pedido

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el checkout crea pedidos pendientes que necesitan preparación.
- **Problema:** definir un recorrido operativo explícito y verificable.
- **Alternativas:** ciclo completo de estados; ciclo reducido pendiente/finalizado.
- **Decisión:** usar `PENDING_CONFIRMATION`, `CONFIRMED`, `READY_FOR_PICKUP`,
  `COMPLETED`, `REJECTED`, `CANCELLED` y `EXPIRED` con transiciones explícitas.
- **Consecuencias:** los estados terminales son de sólo lectura.

### ADR-070 — Consumo de stock al confirmar

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el checkout reserva disponibilidad sin alterar el balance físico.
- **Problema:** decidir cuándo una venta debe descontar existencias reales.
- **Alternativas:** descontar al crear; descontar al confirmar; descontar al entregar.
- **Decisión:** confirmar consume reservas, descuenta el balance y genera
  movimientos auditables en una sola transacción.
- **Consecuencias:** cancelar después de confirmar requiere un movimiento inverso.

### ADR-071 — Reposición automática al cancelar

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una cancelación puede ocurrir después de descontar el stock.
- **Problema:** evitar que el inventario quede artificialmente reducido.
- **Alternativas:** reposición automática; ajuste manual posterior.
- **Decisión:** `CANCELLED` restaura automáticamente las cantidades consumidas.
- **Consecuencias:** la idempotencia impide una reposición duplicada.

### ADR-072 — Permiso operativo

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** la preparación diaria puede distribuirse entre distintos roles.
- **Problema:** determinar quién puede ver contacto y cambiar estados.
- **Alternativas:** sólo OWNER/ADMIN; OWNER/ADMIN/STAFF con permiso común.
- **Decisión:** OWNER, ADMIN y STAFF operan pedidos mediante `MANAGE_ORDERS`.
- **Consecuencias:** acceden al contacto necesario dentro de su tenant.

### ADR-073 — Listado mínimo

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el operador necesita encontrar rápidamente trabajo reciente.
- **Problema:** acotar filtros y volumen para el MVP.
- **Alternativas:** listado mínimo paginado; buscador avanzado por cliente y fechas.
- **Decisión:** paginar de a 20, ordenar por fecha descendente, filtrar por estado
  y buscar por número.
- **Consecuencias:** búsqueda por datos personales y fechas queda fuera.

### ADR-074 — Historial auditable

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** un único estado actual no explica quién realizó cada cambio.
- **Problema:** conservar evidencia suficiente para soporte y control.
- **Alternativas:** sólo estado actual; historial persistente por transición.
- **Decisión:** persistir cada transición con actor, estados, fecha y nota.
- **Consecuencias:** soporte y métricas pueden reconstruir el recorrido.

### ADR-075 — Control de alcance de ORD-02

- **Fecha:** 2026-07-30
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** pedidos conecta naturalmente con pagos, logística y comunicación.
- **Problema:** impedir que ORD-02 crezca más allá del objetivo del sprint.
- **Alternativas:** incorporar integraciones ahora; reservarlas para historias futuras.
- **Decisión:** excluir pagos, envíos, notificaciones, impresión, edición de
  líneas y devoluciones.
- **Consecuencias:** ORD-02 permanece enfocado en operación e inventario.

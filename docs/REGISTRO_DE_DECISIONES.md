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

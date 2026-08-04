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
| ADR-051 | Separación funcional | Carrito separado de checkout y pedido | Aceptada |
| ADR-052 | Persistencia del carrito | Local y sin datos personales | Aceptada |
| ADR-053 | Aislamiento del carrito | Una clave por comercio | Aceptada |
| ADR-054 | Alta al carrito | Desde detalle y con variante explícita | Aceptada |
| ADR-055 | Revalidación | Actualizar el snapshot al abrir el carrito | Aceptada |
| ADR-056 | Cantidad piloto | Unidades enteras | Aceptada |
| ADR-057 | Límite local | Máximo de 99 unidades | Aceptada |
| ADR-058 | Cantidades de pedido | Persistencia preparada para decimales | Aceptada |
| ADR-059 | Entrega inicial | Sólo retiro | Aceptada |
| ADR-060 | Contacto invitado | Nombre y teléfono obligatorios | Aceptada |
| ADR-061 | Cliente del pedido | Snapshot sin cuenta persistente | Aceptada |
| ADR-062 | Reserva de inventario | Separada del balance físico | Aceptada |
| ADR-063 | Duración de reserva | Treinta minutos | Aceptada |
| ADR-064 | Estado inicial | Pedido pendiente de confirmación | Aceptada |
| ADR-065 | Consulta pública | Token opaco conservado como hash | Aceptada |
| ADR-066 | Creación de pedido | Idempotencia obligatoria | Aceptada |
| ADR-067 | Autoridad comercial | Backend calcula precios y totales | Aceptada |
| ADR-068 | Integración de ORD-02 | Rama independiente después de ORD-01 | Aceptada |
| ADR-069 | Ciclo operativo | Estados y transiciones explícitos | Aceptada |
| ADR-070 | Consumo de stock | Al confirmar el pedido | Aceptada |
| ADR-071 | Cancelación | Reposición automática del stock | Aceptada |
| ADR-072 | Operación de pedidos | OWNER, ADMIN y STAFF | Aceptada |
| ADR-073 | Listado administrativo | Paginado, estado y número | Aceptada |
| ADR-074 | Auditoría de pedidos | Historial persistente por transición | Aceptada |
| ADR-075 | Alcance de ORD-02 | Excluir integraciones posteriores | Aceptada |
| ADR-076 | Entrega de pagos | PAY-01 dividido en cuatro incrementos | Aceptada |
| ADR-077 | Inicio del pago | Crear primero el pedido | Aceptada |
| ADR-078 | Aprobación del pago | Confirmación automática verificada | Aceptada |
| ADR-079 | Medios de pago | Sólo medios en línea en el MVP | Aceptada |
| ADR-080 | Webhooks | Inbox durable en MySQL | Aceptada |
| ADR-081 | Credenciales por comercio | OAuth con PKCE y tokens cifrados | Aceptada |
| ADR-082 | Excepciones de pago | Revisión tardía y cancelación protegida | Aceptada |
| ADR-083 | Cliente Mercado Pago | SDK oficial detrás de `PaymentGateway` | Aceptada |
| ADR-084 | Modelo comercial | Sin comisión transaccional en el MVP | Aceptada |
| ADR-085 | Persistencia OAuth | Conexiones en la base de control | Aceptada |
| ADR-086 | Retorno OAuth | Callback fijo en el backend | Aceptada |
| ADR-087 | Exclusividad de cuenta | Una cuenta activa por tenant y ambiente | Aceptada |
| ADR-088 | Cambio de cuenta | Desconectar antes de reemplazar | Aceptada |
| ADR-089 | Renovación de tokens | Refresh seguro bajo demanda | Aceptada |
| ADR-090 | Desconexión | Borrado local y guía de revocación externa | Aceptada |
| ADR-091 | Interfaz OAuth | Pantalla Angular mínima completa | Aceptada |
| ADR-092 | Ambiente de pagos | Determinado por despliegue | Aceptada |
| ADR-093 | Cliente OAuth | `RestClient`; SDK reservado para Checkout | Aceptada |
| ADR-094 | Identidad vendedora | `user_id` verificado y `nickname` mínimo | Aceptada |
| ADR-095 | Aplicación OAuth | Una aplicación central de Comercio Flex | Aceptada |
| ADR-096 | Credencial TEST central | Sólo tenant demo y prohibida en producción | Aceptada |
| ADR-097 | Apertura de Checkout Pro | Automática en la misma pestaña | Aceptada |
| ADR-098 | Retorno del comprador | Token opaco y polling acotado | Aceptada |
| ADR-099 | Webhooks de pago | Inbox global, worker, reintentos y `DEAD` | Aceptada |
| ADR-100 | Habilitación de cobros | Separada de la conexión técnica | Aceptada |
| ADR-101 | Firma de webhook | Obligatoria en TEST y producción | Aceptada |
| ADR-102 | Ambiente del pago verificado | Credencial y coincidencias comerciales, no `live_mode` | Aceptada |
| ADR-103 | Observabilidad de pagos | Micrometer con baja cardinalidad | Aceptada |
| ADR-104 | Recuperación de webhooks | Reintento manual mínimo y auditado | Aceptada |
| ADR-105 | Entrega del hardening | Rama y commits por responsabilidad | Aceptada |
| ADR-106 | Fechas operativas | UTC al persistir y zona del comercio al mostrar | Aceptada |
| ADR-107 | Retorno demorado | Reconciliación verificada bajo demanda | Aceptada |
| ADR-108 | Retorno sin pago | Inspección autoritativa sin mutar el negocio | Aceptada |
| ADR-109 | CSRF público | Sólo operaciones basadas en sesión | Aceptada |
| ADR-110 | Configuración frontend | Separar CORS y URL pública | Aceptada |
| ADR-111 | Ventas del dashboard | Primera confirmación y estado actual válido | Aceptada |
| ADR-112 | Pedidos abiertos | Confirmados y listos para retirar | Aceptada |
| ADR-113 | Stock bajo | Umbral global tenant, decimal y configurable | Aceptada |
| ADR-114 | Imagen de producto | Una imagen principal con display y thumbnail | Aceptada |
| ADR-115 | Almacenamiento de medios | Puerto privado con adaptadores local y S3 compatible | Aceptada |
| ADR-116 | Seguridad de imágenes | JPEG/PNG verificados y recodificados por el backend | Aceptada |
| ADR-117 | Consistencia de medios | Compensación entre objetos y metadatos tenant | Aceptada |
| ADR-118 | Configuración de tienda | Contacto, retiro y cuatro temas por tenant | Aceptada |
| ADR-119 | Despliegue MVP | Angular y Spring Boot bajo un mismo origen Docker | Aceptada |
| ADR-120 | Operación productiva | MySQL administrado, storage privado y restore probado | Aceptada |

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

## ADR aceptadas el 2026-07-31 para PAY-01

### ADR-076 — PAY-01 dividido en cuatro entregas

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** pagos combina dominio, credenciales, redirecciones, notificaciones
  externas y efectos sobre pedidos e inventario.
- **Problema:** mantener incrementos revisables sin ocultar riesgos detrás de una
  única historia demasiado grande.
- **Alternativas:** implementar toda la integración en un sprint; dividirla en
  base interna, conexión OAuth, checkout/webhook y validación sandbox.
- **Decisión:** dividir PAY-01 en PAY-01A, PAY-01B, PAY-01C y PAY-01D.
- **Consecuencias:** cada entrega tendrá criterios y pruebas propios; Mercado Pago
  real no se incorporará durante PAY-01A.

### ADR-077 — El pedido existe antes del pago

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** ORD-01 ya crea un pedido pendiente con importes recalculados y una
  reserva temporal de inventario.
- **Problema:** definir cuál es la fuente interna de verdad al iniciar Checkout Pro.
- **Alternativas:** pagar un carrito todavía no persistido; crear primero el pedido
  y luego asociarle un intento de pago.
- **Decisión:** el pago sólo se inicia desde un pedido existente y elegible.
- **Consecuencias:** importe, moneda, líneas y referencia externa se derivan del
  pedido; una preferencia no crea ni modifica por sí sola un pedido.

### ADR-078 — Aprobación verificada confirma automáticamente

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** ORD-02 dispone de una operación transaccional que confirma el
  pedido, consume reservas y descuenta stock.
- **Problema:** decidir cómo impacta un pago aprobado sobre la operación comercial.
- **Alternativas:** confirmación manual posterior; confirmación automática después
  de verificar el pago con el proveedor.
- **Decisión:** un pago aprobado y verificado invoca la misma regla compartida de
  confirmación del pedido, exactamente una vez.
- **Consecuencias:** pago, pedido e inventario conservan estados separados pero
  coordinados; ni la redirección ni el payload del webhook bastan como autoridad.

### ADR-079 — Sólo medios de pago en línea

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Checkout Pro puede exponer medios con acreditación diferida fuera
  de línea, que prolongan reservas y agregan reglas operativas.
- **Problema:** evitar que el primer MVP requiera reservas extensas, vencimientos y
  conciliación adicional.
- **Alternativas:** admitir todos los medios ofrecidos; excluir medios fuera de
  línea en el MVP.
- **Decisión:** PAY-01 admite únicamente medios de pago en línea.
- **Consecuencias:** efectivo, cupones y otros medios offline quedan para una
  segunda versión con una política específica de reservas.

### ADR-080 — Inbox durable para webhooks

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una notificación puede repetirse, llegar desordenada o fallar a
  mitad de procesamiento.
- **Problema:** responder rápido sin perder trazabilidad ni ejecutar dos veces los
  efectos comerciales.
- **Alternativas:** procesar todo dentro de la solicitud HTTP; persistir primero un
  evento único y procesarlo con reintentos.
- **Decisión:** guardar cada evento en un inbox MySQL idempotente antes del
  procesamiento de negocio.
- **Consecuencias:** se requieren estados, contador de intentos, último error,
  monitoreo y política de reintentos; la unicidad impide efectos duplicados.

### ADR-081 — OAuth por comercio con PKCE y cifrado

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** cada comercio debe cobrar en su propia cuenta de Mercado Pago.
- **Problema:** conectar cuentas sin compartir tokens entre tenants ni almacenar
  secretos legibles.
- **Alternativas:** credencial manual por comercio; OAuth Authorization Code con
  `state`, PKCE y tokens cifrados.
- **Decisión:** un OWNER conecta su cuenta mediante OAuth, validando `state` y
  PKCE; los tokens se cifran antes de persistirse.
- **Consecuencias:** la aplicación deberá gestionar conexión, renovación,
  revocación y rotación de la clave de cifrado sin exponer tokens al frontend.

### ADR-082 — Pagos tardíos y cancelaciones pagadas

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una aprobación puede llegar después de vencer la reserva y un
  operador puede intentar cancelar un pedido ya cobrado.
- **Problema:** evitar descuentos de stock inválidos y pérdida de dinero sin un
  flujo de devolución implementado.
- **Alternativas:** confirmar siempre; ignorar el pago; exigir revisión ante una
  aprobación tardía y bloquear cancelaciones pagadas.
- **Decisión:** una aprobación sin reserva válida pasa a `REQUIRES_REVIEW`; un
  pedido pagado no puede cancelarse mientras no exista un flujo de reembolso.
- **Consecuencias:** soporte deberá resolver excepciones explícitas; reembolsos y
  su conciliación quedan fuera del MVP inicial.

### ADR-083 — SDK oficial aislado por un puerto

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** la integración requiere llamadas autenticadas por comercio y debe
  poder probarse sin red.
- **Problema:** aprovechar el cliente oficial sin acoplar el dominio ni usar una
  credencial global incompatible con multiempresa.
- **Alternativas:** HTTP manual; SDK oficial usado directamente; SDK oficial detrás
  de una interfaz propia.
- **Decisión:** usar el SDK Java oficial 3.3.1 detrás de `PaymentGateway`, con token
  y timeouts definidos por solicitud.
- **Consecuencias:** PAY-01A usará un adaptador falso y no agregará aún el SDK; el
  adaptador real podrá cambiar sin contaminar las reglas de negocio.

### ADR-084 — Sin comisión transaccional de plataforma

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una plataforma puede monetizar mediante suscripción o cobrando una
  comisión dentro de cada pago.
- **Problema:** elegir un modelo inicial sin sumar conciliación fiscal y financiera
  al alcance técnico de pagos.
- **Alternativas:** usar `marketplace_fee`; cobrar el SaaS por separado.
- **Decisión:** no cobrar comisión transaccional en el MVP y facturar el servicio
  de plataforma por fuera del checkout del comercio.
- **Consecuencias:** PAY-01 no calcula ni concilia comisiones; un modelo marketplace
  se evaluará como producto y arquitectura futura.

## ADR aceptadas el 2026-07-31 para PAY-01B

### ADR-085 — Conexiones OAuth en la base de control

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** las conexiones pertenecen a un comercio, pero algunas reglas deben
  comprobar cuentas vendedoras entre tenants antes de enrutar a una base tenant.
- **Problema:** elegir una ubicación que permita aislamiento y restricciones
  globales sin consultar dinámicamente todas las bases de comercios.
- **Alternativas:** persistir cada conexión en su base tenant; centralizar conexiones
  e intentos OAuth en la base de control.
- **Decisión:** guardar en la base de control las conexiones, intentos OAuth y
  metadatos mínimos, ligados al identificador global del tenant y al ambiente.
- **Consecuencias:** la base de control concentra secretos cifrados y requiere
  permisos mínimos, migraciones y auditoría reforzada; el tenant sigue siendo una
  dimensión obligatoria en toda operación.

### ADR-086 — Callback OAuth fijo en el backend

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Mercado Pago debe volver a una URL registrada después de autorizar
  una cuenta y el frontend se sirve bajo rutas de comercio.
- **Problema:** evitar callbacks variables que permitan manipular tenant, ambiente o
  destino y simplificar la configuración externa.
- **Alternativas:** callback por tenant o frontend; callback único y fijo del backend.
- **Decisión:** usar un callback HTTPS fijo del backend. El contexto se recupera
  exclusivamente desde un `state` server-side impredecible, vencible y de un uso.
- **Consecuencias:** el callback no confía en parámetros de tenant ni en URLs de
  retorno aportadas por el navegador; tras resolver el resultado redirige a una
  ruta Angular interna permitida.

### ADR-087 — Una cuenta activa por tenant y ambiente

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** conectar la misma cuenta vendedora a varios comercios puede mezclar
  cobros, conciliación y responsabilidad operativa.
- **Problema:** definir si una identidad Mercado Pago puede reutilizarse entre
  tenants de Comercio Flex.
- **Alternativas:** permitir reutilización; impedirla globalmente por ambiente.
- **Decisión:** una cuenta vendedora sólo puede estar activa en un tenant dentro del
  mismo ambiente. La base de control debe aplicar la unicidad de forma concurrente.
- **Consecuencias:** prueba y producción se evalúan por separado; un conflicto no
  revela qué otro tenant posee la conexión y requiere resolución operativa segura.

### ADR-088 — Desconectar antes de cambiar de cuenta

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una nueva autorización puede corresponder a la misma cuenta o a una
  cuenta distinta de la ya conectada.
- **Problema:** impedir que un callback o error de operación sustituya sin aviso el
  destino financiero de un comercio.
- **Alternativas:** reemplazo automático; confirmación dentro del callback; exigir
  desconexión previa y un flujo OAuth nuevo.
- **Decisión:** una cuenta activa no se reemplaza. La misma identidad puede renovar
  credenciales; una identidad diferente produce conflicto hasta que el `OWNER`
  desconecte explícitamente e inicie una conexión nueva.
- **Consecuencias:** el cambio requiere más pasos, pero queda visible y auditable;
  los tokens recibidos en un intento conflictivo no se persisten ni se registran.

### ADR-089 — Refresh seguro bajo demanda

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** los access tokens vencen y Mercado Pago entrega refresh tokens que
  pueden rotar al renovarse.
- **Problema:** mantener una conexión utilizable sin incorporar todavía tareas
  programadas, coordinación distribuida y monitoreo avanzado.
- **Alternativas:** no renovar; scheduler preventivo; renovar bajo demanda.
- **Decisión:** PAY-01B implementa refresh bajo demanda, con bloqueo, actualización
  atómica de ambos tokens y verificación de que la identidad canónica no cambie.
- **Consecuencias:** el scheduler se difiere a PAY-01C o PAY-01D; fallos definitivos
  dejan la conexión en estado de reconexión requerida sin exponer errores sensibles.

### ADR-090 — Desconexión local y revocación externa guiada

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** eliminar secretos locales impide que Comercio Flex siga usándolos,
  pero no garantiza por sí mismo la revocación del permiso en Mercado Pago.
- **Problema:** ofrecer una desconexión segura aun sin depender de un contrato de
  revocación remota dentro de esta entrega.
- **Alternativas:** conservar tokens inactivos; exigir revocación remota automática;
  borrar localmente y guiar al propietario para revocar en el proveedor.
- **Decisión:** al desconectar se eliminan tokens y secretos recuperables de la base
  local y se muestra una guía para revocar también el permiso en Mercado Pago.
- **Consecuencias:** se conserva únicamente auditoría mínima sin PII; la interfaz
  debe explicar con precisión la diferencia entre desconectar y revocar.

### ADR-091 — Interfaz Angular mínima completa

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una conexión exclusivamente backend no permite al propietario
  reconocer el estado, la cuenta asociada ni resolver errores normales.
- **Problema:** decidir si PAY-01B entrega sólo API o un flujo administrativo usable.
- **Alternativas:** API solamente; pantalla temporal; interfaz mínima completa.
- **Decisión:** entregar en Angular estados desconectado, conectando, conectado,
  reconexión requerida, retorno exitoso o fallido y desconexión confirmada.
- **Consecuencias:** PAY-01B incluye frontend y pruebas accesibles, pero no incorpora
  checkout del comprador ni paneles avanzados de conciliación.

### ADR-092 — Ambiente determinado por despliegue

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** prueba y producción utilizan credenciales y consecuencias distintas.
- **Problema:** evitar que un usuario mezcle ambientes mediante una selección en UI
  o que un callback altere el ambiente esperado.
- **Alternativas:** selector por comercio; ambiente fijo por despliegue.
- **Decisión:** el ambiente de Mercado Pago se obtiene de configuración validada al
  iniciar la aplicación y no puede elegirse desde Angular ni desde el callback.
- **Consecuencias:** cada despliegue opera en un solo ambiente; las restricciones e
  identidades se particionan por ambiente y una promoción exige configuración
  operativa explícita.

### ADR-093 — `RestClient` para OAuth y SDK reservado para Checkout

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** PAY-01B requiere endpoints OAuth y de identidad, mientras el SDK
  oficial 3.3.1 fue seleccionado para las operaciones de Checkout Pro.
- **Problema:** no forzar el SDK sobre contratos OAuth que pueden modelarse de forma
  pequeña y explícita ni duplicar clientes sin límites homogéneos.
- **Alternativas:** SDK para todo; cliente HTTP de bajo nivel; `RestClient` tipado
  para OAuth y SDK detrás de `PaymentGateway` en PAY-01C.
- **Decisión:** usar Spring `RestClient` con DTO, timeouts y errores sanitizados para
  token, refresh y perfil; reservar el SDK 3.3.1 para Checkout Pro en PAY-01C.
- **Consecuencias:** los contratos externos quedan aislados por adaptadores y ningún
  DTO de Mercado Pago atraviesa hacia el dominio o el frontend.

### ADR-094 — Identidad vendedora mínima y verificada

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el intercambio OAuth devuelve `user_id` y el perfil autenticado de
  `/users/me` permite mostrar al propietario qué cuenta autorizó.
- **Problema:** verificar la cuenta sin persistir datos personales innecesarios ni
  usar atributos mutables como autoridad de seguridad.
- **Alternativas:** confiar sólo en una etiqueta; conservar el perfil completo;
  validar IDs y guardar únicamente `user_id` y `nickname`.
- **Decisión:** `user_id` es la identidad canónica. Después del intercambio se llama
  a `GET /users/me` y su `id` debe coincidir; si difiere se aborta sin persistir los
  secretos. Sólo se guardan `user_id` y el `nickname` público para construir la
  etiqueta visible.
- **Consecuencias:** email, nombre legal y demás PII no se solicitan como parte del
  contrato interno, no se persisten y no se devuelven; `nickname` es sólo
  presentación y nunca autoriza reemplazo, refresh ni exclusividad.

### ADR-095 — Aplicación OAuth central de Comercio Flex

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Mercado Pago exige `Client ID` y `Client Secret` para identificar
  a la aplicación que solicita autorización, pero el comerciante sólo necesita
  decidir qué cuenta vendedora conecta.
- **Problema:** definir si cada comercio debe crear y configurar su propia
  aplicación técnica o si Comercio Flex administra esa infraestructura.
- **Alternativas:** una aplicación OAuth central configurada por despliegue; una
  aplicación y secretos cargados manualmente por cada vendedor.
- **Decisión:** Comercio Flex configura una única aplicación OAuth central en el
  backend. El vendedor nunca carga secretos: inicia sesión en Mercado Pago,
  presta consentimiento y reconoce la cuenta conectada desde el panel.
- **Consecuencias:** `Client ID` y `Client Secret` son secretos operativos de la
  plataforma; los access y refresh tokens continúan siendo distintos, cifrados y
  aislados por tenant. La rotación de las credenciales centrales requiere un
  procedimiento operativo, pero no una acción de cada comercio.

## ADR aceptadas el 2026-07-31 para PAY-01C

### ADR-096 — Credencial TEST central limitada al tenant demo

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una credencial vendedora TEST central facilita una demostración
  inicial, pero no representa el aislamiento OAuth real entre comercios.
- **Problema:** permitir una prueba controlada sin convertir una credencial común
  en el mecanismo normal de cobro multiempresa.
- **Alternativas:** usarla en todos los tenants; prohibirla por completo; limitarla
  mediante configuración a un único tenant demo y rechazarla en producción.
- **Decisión:** la credencial vendedora TEST central sólo puede habilitarse para un
  tenant demo identificado explícitamente. El backend debe fallar al iniciar si
  esa modalidad aparece en ambiente `PRODUCTION` o fuera del tenant autorizado.
- **Consecuencias:** sirve para smoke tests y demostraciones, pero no acredita
  aislamiento por vendedor. El recorrido de aceptación multiempresa utiliza las
  conexiones OAuth TEST propias de cada comercio.

### ADR-097 — Checkout Pro se abre automáticamente en la misma pestaña

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** después de crear la preferencia el comprador debe continuar hacia
  la pantalla alojada por Mercado Pago.
- **Problema:** elegir una navegación predecible que no dependa de pop-ups ni deje
  al usuario ante un segundo botón ambiguo.
- **Alternativas:** mostrar un enlace manual; abrir otra pestaña; navegar
  automáticamente en la pestaña actual.
- **Decisión:** tras recibir una preferencia válida Angular navega al `init_point`
  permitido en la misma pestaña.
- **Consecuencias:** el botón entra en estado ocupado para impedir dobles inicios;
  si la creación falla, permanece en Comercio Flex con un error recuperable.

### ADR-098 — Retorno específico con token opaco y polling acotado

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el navegador vuelve antes, después o sin que el webhook haya sido
  procesado, y los parámetros de retorno pueden alterarse.
- **Problema:** mostrar el resultado del pedido sin tratar la redirección como
  autoridad ni exponer identificadores internos.
- **Alternativas:** confiar en `status` de Mercado Pago; volver al detalle general;
  usar una ruta de resultado con token opaco y consultar el backend por tiempo
  limitado.
- **Decisión:** cada inicio genera un token opaco específico para el retorno. La
  pantalla consulta el estado autoritativo con polling acotado, se detiene ante un
  estado terminal o al agotar tiempo/intentos y ofrece actualización manual.
- **Consecuencias:** el token se guarda sólo como hash, tiene alcance y vencimiento
  limitados y no confirma pagos. Las URLs aplican HTTPS y política de no referencia;
  el frontend no conserva el token en almacenamiento persistente.

### ADR-099 — Inbox global en control DB con worker y estado `DEAD`

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** un webhook llega antes de abrir una base tenant y puede repetirse,
  desordenarse o fallar después de aplicar el efecto comercial.
- **Problema:** confirmar recepción con rapidez sin perder eventos ni duplicar el
  pedido, el pago o el stock entre dos bases.
- **Alternativas:** procesar todo en la solicitud; inbox por tenant; inbox global
  en control DB y procesamiento posterior.
- **Decisión:** el receptor valida firma, persiste metadatos mínimos en un inbox
  global y responde. Un worker con lease procesa, reintenta con backoff y pasa a
  `DEAD` al agotar el límite. La aplicación tenant continúa siendo idempotente.
- **Consecuencias:** existe consistencia eventual entre control DB y tenant DB. Si
  ocurre una caída entre ambos commits, el replay es seguro por las unicidades de
  pago y las transiciones idempotentes; `DEAD` exige alerta y reproceso controlado.

### ADR-100 — Habilitación comercial separada de la conexión técnica

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una cuenta OAuth conectada demuestra acceso técnico, pero no que el
  comercio esté listo para ofrecer pagos a compradores.
- **Problema:** evitar cobros accidentales durante configuración, soporte o prueba.
- **Alternativas:** cobrar automáticamente al conectar; usar un único estado;
  mantener una habilitación comercial explícita e independiente.
- **Decisión:** crear preferencias requiere conexión técnica utilizable y
  habilitación comercial activa para ese tenant. Cambiar la habilitación no borra
  ni renueva credenciales.
- **Consecuencias:** el backend falla cerrado y la tienda oculta o deshabilita la
  acción de pago cuando cualquiera de las dos condiciones falta. La gestión de esa
  habilitación debe ser auditable.

### ADR-101 — Firma obligatoria en TEST y producción

- **Fecha:** 2026-07-31
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Mercado Pago firma las notificaciones con una clave configurada
  para la aplicación y recomienda validar su origen.
- **Problema:** impedir que TEST desarrolle un camino menos seguro que producción
  o que un payload no autenticado ingrese al inbox.
- **Alternativas:** validar sólo en producción; aceptar sin firma y verificar luego;
  exigir firma válida en ambos ambientes.
- **Decisión:** TEST y producción requieren firma válida, timestamp dentro de la
  tolerancia y secretos externos separados. Una notificación inválida no se
  persiste como evento procesable ni se consulta al proveedor.
- **Consecuencias:** la prueba local sin Mercado Pago usa dobles explícitos; la
  prueba integrada necesita HTTPS y el secreto de webhook correspondiente. La
  rotación de secretos debe contemplar una transición operativa controlada.

### ADR-102 — Ambiente determinado por credencial y coincidencias comerciales

- **Fecha:** 2026-08-01
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Checkout Pro puede devolver `live_mode=true` al consultar por API
  un pago realizado entre cuentas de prueba, aun cuando la preferencia se haya
  creado con la credencial TEST y se haya abierto mediante `sandbox_init_point`.
- **Problema:** usar `live_mode` como equivalencia de TEST/producción rechaza un
  pago de prueba legítimo y no representa fielmente el aislamiento efectivo.
- **Alternativas:** mantener esa igualdad y crear una reconciliación manual
  exclusiva de TEST; determinar el ambiente por la credencial usada para consultar
  al proveedor y validar todas las coincidencias comerciales.
- **Decisión:** `live_mode` se conserva como dato informativo, pero no clasifica el
  ambiente del pago verificado. La aceptación exige que la consulta autenticada con
  la credencial del comercio coincida en vendedor, preferencia, referencia externa,
  importe y moneda. La firma y el ambiente del webhook se validan antes del inbox.
- **Consecuencias:** TEST reproduce el comportamiento real de Checkout Pro sin un
  camino manual alternativo. Producción conserva la validación de firma, cuenta,
  preferencia y valores; una discrepancia sigue fallando cerrada.

## ADR aceptadas el 2026-08-01 para Sprint 11

### ADR-103 — Observabilidad local mediante Micrometer y Actuator

- **Fecha:** 2026-08-01
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el inbox durable ya reintenta fallos, pero una operación real
  necesita reconocer recepciones, duplicados, reintentos y eventos agotados.
- **Problema:** obtener señales operativas sin contratar todavía una plataforma de
  monitoreo ni exponer datos financieros o secretos.
- **Alternativas:** usar sólo logs y consultas SQL; instrumentar con Micrometer y
  Actuator manteniendo las métricas fuera de la superficie pública.
- **Decisión:** instrumentar el flujo con métricas de baja cardinalidad y conservar
  su exposición restringida. No se usan como etiquetas tenant, pago, pedido,
  vendedor, URL, error crudo ni ningún token.
- **Consecuencias:** el backend queda preparado para Prometheus u otro colector
  futuro sin incorporarlo a este sprint; logs y respuestas también deben permanecer
  sanitizados.

### ADR-104 — Recuperación manual mínima para webhooks agotados

- **Fecha:** 2026-08-01
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** un evento `DEAD` agotó sus reintentos automáticos y, sin una acción
  operativa, requeriría acceso directo a MySQL.
- **Problema:** permitir recuperación segura sin construir una consola general de
  colas ni permitir efectos duplicados.
- **Alternativas:** procedimiento SQL exclusivo de soporte; vista `OWNER` con
  listado mínimo y reintento explícito, tenant-safe e idempotente.
- **Decisión:** el panel de pagos listará sólo metadatos sanitizados de eventos
  agotados del comercio y permitirá reprogramarlos. Nunca expone payload, IDs
  internos, request IDs, credenciales, comprador ni identificadores del proveedor.
- **Consecuencias:** la mutación exige sesión, CSRF y `MANAGE_PAYMENTS`; queda
  auditada y no modifica eventos ya procesados. El reproceso masivo permanece fuera.

### ADR-105 — Rama y commits del hardening de pagos

- **Fecha:** 2026-08-01
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** PAY-01D combina pruebas, observabilidad, recuperación y
  documentación.
- **Problema:** mantener revisable el cierre de pagos y evitar cambios directos en
  `main`.
- **Alternativas:** un único commit sobre `main`; rama dedicada y commits por
  responsabilidad.
- **Decisión:** trabajar en `codex/feat-payment-hardening` con commits separados
  para pruebas, observabilidad, recuperación y documentación.
- **Consecuencias:** el merge y el push requieren autorización posterior del Product
  Owner, aun cuando los commits del sprint ya estén autorizados.

### ADR-106 — UTC en persistencia y zona del comercio en presentación

- **Fecha:** 2026-08-01
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** la prueba manual de recuperación mostró un evento tres horas
  desplazado y, simultáneamente, un error OAuth no relacionado con el reintento.
- **Problema:** evitar que el driver interprete dos veces la zona de un `TIMESTAMP`
  y que el operador confunda mensajes de subsistemas independientes.
- **Alternativas:** usar la zona del navegador; guardar horas locales; conservar
  instantes UTC y presentarlos con `store_settings.timezone`.
- **Decisión:** la sesión JDBC de control DB se fuerza a UTC y Angular formatea los
  eventos con la zona IANA configurada para el comercio, mostrándola explícitamente.
  Los avisos del inbox quedan dentro de su tarjeta y los errores OAuth usan un
  mensaje independiente y específico.
- **Consecuencias:** los instantes son comparables entre ambientes y cada comercio
  ve su hora operativa aunque el navegador esté en otra zona. Toda nueva conexión
  JDBC que lea `TIMESTAMP` debe mantener la misma política UTC.

### ADR-107 — Reconciliación verificada desde el retorno demorado

- **Fecha:** 2026-08-01
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** Mercado Pago devolvió un pago aprobado, pero el webhook no llegó a
  tiempo y la pantalla seguía mostrando el intento como pendiente.
- **Problema:** el botón `Actualizar estado` sólo releía la base local y no podía
  resolver una demora del webhook.
- **Alternativas:** esperar indefinidamente el webhook; confiar en los parámetros
  de retorno; consultar al proveedor bajo demanda y aplicar el mismo flujo seguro.
- **Decisión:** el retorno conserva lectura y polling acotados. Tras el timeout, el
  comprador puede enviar el `payment_id` recibido mediante POST y token opaco. El
  backend consulta Mercado Pago con la credencial esperada y valida todas las
  coincidencias comerciales antes de confirmar. La exigencia original de CSRF para
  este POST público fue reemplazada por ADR-109.
- **Consecuencias:** la recuperación no crea otra preferencia ni otro cobro y es
  idempotente. La URL nunca es fuente financiera; un identificador falso, una
  credencial distinta o cualquier discrepancia fallan cerrados.

### ADR-108 — Retorno de Checkout Pro sin pago registrado

- **Fecha:** 2026-08-02
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una compra de prueba rechazada volvió sin `payment_id`. Mercado
  Pago mantenía la orden comercial abierta con una lista de pagos vacía, mientras
  la pantalla de Comercio Flex mostraba el mensaje genérico de pago pendiente.
- **Problema:** afirmar que se estaba confirmando un pago inexistente confundía al
  comprador, pero confiar en el estado visible de la URL podía cancelar un pago
  real o liberar stock incorrectamente.
- **Alternativas:** conservar siempre el mensaje pendiente; marcar rechazado desde
  la redirección; consultar al proveedor y exponer un resultado sólo informativo.
- **Decisión:** se elige la inspección autoritativa. El backend usa la preferencia
  almacenada, valida vendedor y referencia externa, y sólo informa
  `PAYMENT_NOT_RECORDED` cuando la orden comercial coincidente tiene cero pagos.
  El estado interno permanece pendiente y la reserva conserva su vencimiento.
- **Consecuencias:** Angular puede explicar que no hubo cobro y permitir reintento
  sin crear efectos financieros. Una respuesta ausente, ambigua o errónea vuelve
  al mensaje conservador; ningún parámetro del navegador modifica pedido o stock.

### ADR-109 — CSRF limitado a operaciones basadas en sesión

- **Fecha:** 2026-08-03
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el checkout invitado falló antes de crear el pedido porque las
  operaciones públicas exigían una cookie CSRF pese a no depender de una sesión
  autenticada.
- **Problema:** conservar CSRF en estos contratos agregaba estado de navegador y
  puntos de fallo sin proteger una acción realizada con autoridad de usuario.
- **Alternativas:** transportar explícitamente un token CSRF entre Angular y
  Spring Boot; excluir únicamente los POST públicos y mantener las defensas de
  negocio y todas las protecciones administrativas.
- **Decisión:** pedidos invitados, inicio de Checkout Pro, inspección,
  reconciliación y webhook no requieren CSRF. Siguen sujetos a validación,
  idempotencia, tokens opacos, verificación contra Mercado Pago y aislamiento
  tenant. Autenticación y toda mutación administrativa conservan CSRF.
- **Consecuencias:** el checkout deja de depender de una cookie previa. El spam y
  la automatización abusiva se abordarán con rate limiting porque CSRF no sustituye
  ese control. Las pruebas de seguridad deben demostrar ambos lados de la frontera.

### ADR-110 — Separación entre orígenes CORS y URL pública de retorno

- **Fecha:** 2026-08-03
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** la prueba local necesitaba aceptar `http://localhost:4200`, mientras
  Mercado Pago exige una URL HTTPS pública para regresar al frontend.
- **Problema:** una sola variable `FRONTEND_ORIGIN` representaba dos conceptos y
  hacía que habilitar el retorno público bloqueara los POST del frontend local.
- **Alternativas:** usar siempre el túnel para toda la navegación; permitir todos
  los orígenes; separar la lista CORS de la URL pública de retorno.
- **Decisión:** `FRONTEND_ORIGINS` define una lista explícita de orígenes CORS y
  `PUBLIC_FRONTEND_BASE_URI` define una única base HTTPS para OAuth y Checkout Pro.
  Se conserva `FRONTEND_ORIGIN` como fallback compatible para entornos simples.
- **Consecuencias:** desarrollo local y retorno público pueden coexistir sin
  wildcard CORS. Cada nuevo túnel requiere actualizar ambas variables explícitas
  y reiniciar el backend.

### ADR-111 — Ventas según primera confirmación y estado actual válido

- **Fecha:** 2026-08-03
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** DASH-01 debe mostrar ventas del día y del mes sin confundir pagos
  pendientes, rechazos o cancelaciones con ingresos operativos.
- **Problema:** un pedido puede cambiar de estado después de confirmarse y su fecha
  de creación no representa necesariamente la fecha de venta.
- **Alternativas:** sumar por creación; sumar por pago; usar la primera confirmación
  y exigir un estado actual válido.
- **Decisión:** se usa la primera transición a `CONFIRMED`, dentro de la zona
  horaria del comercio. Sólo suman estados actuales `CONFIRMED`,
  `READY_FOR_PICKUP` y `COMPLETED`.
- **Consecuencias:** cancelados, rechazados, vencidos y pendientes quedan fuera. Un
  futuro módulo contable podrá definir ventas netas, reembolsos e impuestos aparte.

### ADR-112 — Pedidos abiertos operativos

- **Fecha:** 2026-08-03
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el dashboard necesita una señal pequeña para la operación diaria.
- **Problema:** incluir pendientes de pago o pedidos terminados genera un número
  que no representa trabajo accionable.
- **Alternativas:** todos los no cancelados; sólo pendientes; confirmados y listos.
- **Decisión:** `openOrders` cuenta `CONFIRMED` y `READY_FOR_PICKUP`.
- **Consecuencias:** la tarjeta enlaza a gestión de pedidos; un desglose por estado
  y tiempos de preparación queda fuera del MVP.

### ADR-113 — Umbral global de stock bajo por comercio

- **Fecha:** 2026-08-03
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** ADR-044 había diferido la definición hasta DASH-01.
- **Problema:** un umbral por variante ofrece precisión, pero aumenta mucho la
  configuración inicial y el costo de administración.
- **Alternativas:** valor fijo; umbral por variante; umbral global configurable.
- **Decisión:** cada tenant tiene un umbral global decimal, inicial `5.000`. Se
  consideran variantes activas de productos no archivados y saldo menor o igual;
  la lista prioriza las cinco cantidades más bajas.
- **Consecuencias:** cubre unidades y preparación futura para peso con una interfaz
  simple. Los umbrales por categoría o variante quedan para una versión posterior.

### ADR-114 — Una imagen principal con dos derivados

- **Fecha:** 2026-08-04
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** la tienda pública necesita fotografías reales antes de una demo,
  pero una galería completa amplía carga, ordenamiento y administración.
- **Problema:** ofrecer una presentación visual útil sin convertir MEDIA-01 en un
  gestor general de archivos.
- **Alternativas:** URL manual; una imagen original; una imagen principal con dos
  tamaños; galería de múltiples imágenes.
- **Decisión:** cada producto admite una imagen principal y el backend genera una
  versión de detalle de hasta 1600 px y un thumbnail de hasta 480 px. El texto
  alternativo es obligatorio y el frontend conserva un fallback cuando no existe.
- **Consecuencias:** catálogo y detalle cargan el tamaño adecuado. Galerías, zoom y
  variantes con fotografías propias quedan fuera del MVP.

### ADR-115 — Almacenamiento privado y compatible con S3

- **Fecha:** 2026-08-04
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** los binarios pesados no deben almacenarse en MySQL y desarrollo no
  debe depender de una cuenta cloud.
- **Problema:** mantener URLs estables, aislamiento tenant y portabilidad entre un
  disco local y Cloudflare R2, S3 o un proveedor compatible.
- **Alternativas:** BLOB en MySQL; URLs públicas pegadas al producto; un puerto de
  almacenamiento con adaptadores local y S3 compatible.
- **Decisión:** MySQL conserva únicamente metadatos y claves opacas. El bucket es
  privado y el backend sirve el contenido mediante rutas tenant. Desarrollo usa
  una raíz local fuera de Git y producción un storage S3 compatible mediante AWS
  SDK Java v2 alineado con BOM.
- **Consecuencias:** Angular no conoce credenciales ni rutas físicas y puede
  cambiarse de proveedor sin alterar sus contratos. El proxy podrá reemplazarse
  por CDN o URLs firmadas cuando el tráfico lo justifique.

### ADR-116 — Verificación y recodificación de imágenes

- **Fecha:** 2026-08-04
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** nombre y `Content-Type` enviados por el navegador no prueban que un
  archivo sea una imagen segura.
- **Problema:** reducir contenido activo, metadatos sensibles y bombas de
  descompresión sin incorporar un pipeline multimedia excesivo.
- **Alternativas:** confiar en el navegador; aceptar formatos modernos sin
  procesar; limitar a JPEG/PNG y reconstruir los píxeles.
- **Decisión:** se aceptan JPEG y PNG de hasta 5 MiB y 10 megapíxeles, se valida la
  firma real, se normaliza la orientación EXIF, se decodifica y se vuelve a
  codificar para producir ambos derivados.
  No se aceptan SVG ni nombres de ruta proporcionados por el cliente.
- **Consecuencias:** se eliminan EXIF y estructuras agregadas. WebP y AVIF pueden
  evaluarse después con una biblioteca de procesamiento específica.

### ADR-117 — Consistencia compensatoria entre MySQL y objetos

- **Fecha:** 2026-08-04
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** una transacción MySQL no puede incluir atómicamente un bucket de
  objetos externo.
- **Problema:** evitar referencias rotas al reemplazar o eliminar una imagen.
- **Alternativas:** escribir primero la base; escribir primero los objetos sin
  compensación; objetos nuevos, commit tenant y limpieza compensatoria.
- **Decisión:** se almacenan ambos objetos nuevos antes del upsert. Si falla MySQL,
  se eliminan los nuevos; después del commit se retiran los anteriores. Al borrar,
  primero se elimina la referencia tenant y luego los objetos inaccesibles.
- **Consecuencias:** un fallo de limpieza puede dejar un objeto huérfano, pero nunca
  una imagen pública apuntando a contenido ausente. La recolección periódica de
  huérfanos queda como mejora operativa.

### ADR-118 — Configuración básica del comercio sin ampliar logística

- **Fecha:** 2026-08-04
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el piloto necesita mostrar identidad, contacto y lugar de retiro
  propios de cada tenant.
- **Problema:** personalizar la tienda sin incorporar constructor visual, envíos ni
  reglas logísticas que amplíen el MVP.
- **Alternativas:** valores fijos; configuración completa de diseño/logística;
  nombre, contacto, retiro y paleta acotada.
- **Decisión:** `OWNER`/`ADMIN` editan nombre, teléfono/correo, dirección e
  instrucciones de retiro y uno de cuatro temas (`VIOLET`, `BURGUNDY`, `FOREST`,
  `NAVY`). `STAFF` se orienta a pedidos y no administra estas opciones. El checkout
  continúa exclusivamente con `PICKUP`.
- **Consecuencias:** cada comercio tiene una identidad útil sin duplicar Angular.
  Logo, tipografías arbitrarias, dominio propio y envío quedan para versiones
  posteriores.

### ADR-119 — Monolito desplegable bajo un mismo origen

- **Fecha:** 2026-08-04
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** la SPA usa sesión HttpOnly y CSRF; separar dominios en el piloto
  agrega CORS, cookies cross-site y dos despliegues coordinados.
- **Problema:** producir un artefacto simple, reproducible y compatible con la
  seguridad ya implementada.
- **Alternativas:** Cloudflare Pages + API separada; reverse proxy adicional;
  compilar Angular dentro del JAR Spring Boot.
- **Decisión:** un Docker multi-stage empaqueta Angular en Spring Boot, corre Java
  21 con usuario no-root y publica SPA/API bajo un único origen HTTPS. Railway es
  la primera opción de demo; readiness determina recepción de tráfico.
- **Consecuencias:** se simplifican cookies, CSRF y operación. La primera versión
  usa una réplica; escalar requiere resolver sesiones compartidas/sticky sessions.

### ADR-120 — Servicios administrados, mínimo privilegio y restore como puerta

- **Fecha:** 2026-08-04
- **Estado:** Aceptada
- **Responsable de aprobación:** Product Owner
- **Contexto:** el código no puede garantizar continuidad si base, objetos,
  secretos y copias se operan sin responsables ni pruebas.
- **Problema:** definir una salida comercial responsable sin declarar desplegados
  servicios todavía no contratados.
- **Alternativas:** MySQL/storage dentro del contenedor; servicios administrados
  sin restore; MySQL administrado + S3/R2 privado + backups restaurados.
- **Decisión:** el piloto usa MySQL administrado, bucket privado, secretos del
  proveedor, usuarios runtime separados y usuario DDL de migración. Backup y
  restore aislado con evidencia son condición de OPS-01; CI/build no los reemplaza.
- **Consecuencias:** aumenta preparación operativa y costo externo, pero reduce
  pérdida de datos y movimiento lateral. Hosting, dominio, credenciales y prueba
  real permanecen pendientes del Product Owner/proveedor.

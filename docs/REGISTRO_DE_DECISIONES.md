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

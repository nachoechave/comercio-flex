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
| CORE-01 | Tenant | Resolver comercio y conexión | Resolver path, consultar control DB y enrutar a la base correcta | Alta | Pendiente | BT-02, BT-04 | Backend | A/B aislados, fallo seguro y API documentada | Conexión incorrecta | XL |
| CORE-02 | Identidad | Login y roles | Sesión segura, autorización y logout | Alta | Pendiente | CORE-01 | Backend/Frontend | Roles, CSRF y pruebas negativas | Diseño de cookies | L |
| CAT-01 | Catálogo | Gestionar categorías | CRUD administrativo por tenant | Alta | Pendiente | CORE-02 | Backend/Frontend | Validación, aislamiento, pruebas y manual | Slugs duplicados | M |
| CAT-02 | Catálogo | Gestionar productos de indumentaria | Alta/edición/publicación, talle, color, SKU y precio | Alta | Pendiente | CAT-01 | Backend/Frontend | Precio > 0, categoría y variante válidos | Combinaciones inválidas | XL |
| INV-01 | Inventario | Ajustar stock | Existencia por variante y movimiento auditable | Alta | Pendiente | CAT-02 | Backend/Frontend | No negativo, concurrencia y pruebas | Sobreventa | L |
| STORE-01 | Tienda | Navegar catálogo | Listar, buscar y ver detalle público | Alta | Pendiente | CAT-02 | Frontend/Backend | Responsive, estados y tenant correcto | Rendimiento/SEO | L |
| ORD-01 | Compra | Carrito y checkout | Carrito local, cliente, entrega y observaciones | Alta | Pendiente | STORE-01, INV-01 | Frontend/Backend | Backend recalcula y persiste snapshot | Precio manipulado | XL |
| ORD-02 | Pedidos | Operar pedidos | Listado, detalle y transiciones válidas | Alta | Pendiente | ORD-01 | Backend/Frontend | Roles, historial y pruebas | Estados ambiguos | L |
| PAY-01 | Pagos | OAuth y Checkout Pro sandbox | Conectar comercio, preferencia, retorno y webhook idempotente | Alta | Pendiente | ORD-01, F0-02 | Backend/Calidad | OAuth state, firma, importe, duplicados y pruebas | Fraude/secretos | XL |
| DASH-01 | Dashboard | Métricas mínimas | Día, mes, pendientes y stock bajo | Media | Pendiente | ORD-02 | Backend/Frontend | Datos por tenant y definiciones documentadas | Métricas inconsistentes | M |
| OPS-01 | Operación | Despliegue piloto | HTTPS, secretos, logs, backup y restore | Alta | Pendiente | PAY-01 | Infraestructura | Smoke, backup y restauración probada | Costo/caída | L |

## Definición de terminado

Una tarea sólo está terminada si cumple criterios, tiene pruebas adecuadas, fue
revisada, está documentada y puede probarse manualmente.

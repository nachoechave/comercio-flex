# Alcance propuesto

> Estado: alcance base aprobado el 2026-07-23; sujeto a validación con el comercio piloto

## MVP

### Tienda pública

- Identificación del comercio y branding básico.
- Inicio simple, catálogo paginado, categorías y búsqueda por texto.
- Detalle de producto.
- Producto con una o más variantes vendibles.
- Talle y color como atributos iniciales para el piloto de indumentaria.
- Carrito local separado por comercio.
- Checkout invitado con datos mínimos, observaciones, retiro o envío básico.
- Creación y consulta segura del pedido.
- Mercado Pago Checkout Pro en pruebas, retorno informativo y webhook idempotente.
- Diseño responsive y accesibilidad básica.

### Administración

- Inicio/cierre de sesión y roles mínimos `OWNER`, `ADMIN`, `STAFF`.
- Gestión de categorías, productos, variantes base, precio y stock.
- Listado, detalle y cambio válido de estado de pedidos.
- Configuración básica del comercio y métodos de entrega.
- Dashboard mínimo: ventas del día, ventas del mes, pedidos pendientes y stock bajo.

### Plataforma y calidad

- Base de control compartida y una base MySQL separada por comercio.
- Enrutamiento de conexiones, provisión y migración consistente de las bases.
- Migraciones Flyway, validación backend, errores centralizados y OpenAPI.
- Secretos por variables de entorno; contraseñas hasheadas.
- Pruebas unitarias, integración con MySQL real y pruebas negativas entre tenants.
- Docker Compose para MySQL local y health checks.
- Documentación y prueba manual por historia.

## Segunda versión

- Variantes avanzadas de talle/color y filtros combinados.
- Franjas horarias y cupos de entrega.
- Gestión completa de clientes y usuarios/roles desde UI.
- Reportes y gráficos ampliados.
- Mejoras del onboarding OAuth de Mercado Pago y renovación operativa.
- Recuperación de contraseña, auditoría ampliada y carrito en servidor.
- Dominios propios, promociones, cupones y optimización de imágenes.

## Futuro

- Múltiples sucursales y depósitos.
- POS y omnicanalidad.
- Reembolsos, disputas y múltiples proveedores de pago.
- Constructor visual, reseñas, PWA, i18n y notificaciones.
- Analítica avanzada, API pública, aplicaciones móviles.
- Aislamiento de base por tenant como plan premium.
- Microservicios sólo si escala/equipos aportan evidencia.

## Exclusiones explícitas del MVP

- Motor genérico EAV/reglas para cualquier rubro.
- Facturación fiscal, marketplace, comisiones y logística avanzada.
- Búsqueda externa, colas distribuidas, Redis y Kubernetes.
- Todos los indicadores de dashboard solicitados.

## Condición previa restante

El vertical aprobado es indumentaria. Falta seleccionar y entrevistar un comercio
piloto concreto para validar talles, colores, SKUs, stock, entrega y operación.

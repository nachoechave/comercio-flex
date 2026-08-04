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
- Checkout invitado con datos mínimos, observaciones y retiro en el comercio
  (`PICKUP`). Envío, tarifas, zonas y franjas quedan fuera del MVP.
- Creación y consulta segura del pedido.
- Mercado Pago Checkout Pro en pruebas: pedido previo al pago, medios online,
  retorno informativo, webhook idempotente y confirmación automática verificada.
- Diseño responsive y accesibilidad básica.

### Administración

- Inicio/cierre de sesión y roles mínimos `OWNER`, `ADMIN`, `STAFF`.
- Gestión de categorías, productos, variantes base, precio y stock.
- Listado, detalle y cambio válido de estado de pedidos.
- Configuración básica del comercio: nombre, teléfono/correo, dirección e
  instrucciones de retiro y tema visual.
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
- Medios de pago offline, reservas de larga duración y configuración de cuotas.
- Mejoras del onboarding OAuth de Mercado Pago y renovación operativa.
- Recuperación de contraseña, auditoría ampliada y carrito en servidor.
- Evaluación de Firebase Authentication como proveedor de identidad, manteniendo
  roles, membresías y autorización multiempresa en Comercio Flex.
- Dominios propios, promociones y cupones.

## Futuro

- Múltiples sucursales y depósitos.
- POS y omnicanalidad.
- Reembolsos automáticos, disputas y múltiples proveedores de pago.
- Constructor visual, reseñas, PWA, i18n y notificaciones.
- Analítica avanzada, API pública, aplicaciones móviles.
- Aislamiento de base por tenant como plan premium.
- Microservicios sólo si escala/equipos aportan evidencia.

## Exclusiones explícitas del MVP

- Motor genérico EAV/reglas para cualquier rubro.
- Facturación fiscal, marketplace, comisiones por transacción y logística avanzada.
- Búsqueda externa, colas distribuidas, Redis y Kubernetes.
- Todos los indicadores de dashboard solicitados.
- Envío a domicilio, zonas, tarifas, transportistas y franjas horarias.

## Cierre técnico preparado

- Una imagen principal por producto, JPEG/PNG de hasta 5 MiB, corregida según
  orientación EXIF y recodificada por el backend. Se generan una imagen de hasta
  1600 px y una miniatura de hasta 480 px.
- Almacenamiento local para desarrollo y objeto privado S3/R2 en producción;
  MySQL sólo conserva metadatos y claves.
- Despliegue de un único origen: Angular se sirve desde Spring Boot para conservar
  cookies y protección CSRF sin depender de CORS entre dominios.
- Imagen Docker sin privilegios, CI, endpoints de liveness/readiness,
  identificador de correlación y scripts de backup/restore.

Estos puntos están implementados o preparados en el repositorio. No significan
que exista ya un entorno productivo contratado o validado.

## Condición previa restante

El vertical aprobado es indumentaria. Falta seleccionar y entrevistar un comercio
piloto concreto para validar talles, colores, SKUs, stock, entrega y operación.

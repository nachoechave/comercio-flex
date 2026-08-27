# Alcance original del MVP y estado actual

> Este archivo conserva el nombre histórico para no romper enlaces. El alcance
> base fue aprobado el 2026-07-23; desde entonces varias capacidades inicialmente
> posteriores al MVP fueron implementadas. El estado vigente se resume abajo.

## Implementado

### Autenticación y tenants

- Login y cierre de sesión con sesión JDBC.
- Roles tenant fijos `OWNER`, `ADMIN` y `STAFF`, más administración global
  `SUPER_ADMIN` separada.
- Routing seguro por slug, base de control y una base MySQL aislada por tenant.
- Provisioning, migración y registro de pools para nuevos comercios.
- Branding y configuración propios por tenant.

### Catálogo y storefront

- Categorías, búsqueda y catálogo público paginado.
- Productos con ciclo borrador, publicado y archivado.
- Variantes genéricas nombre/valor; `Talle` y `Color` continúan soportados por
  compatibilidad, pero no son un modelo cerrado.
- SKU, precio y disponibilidad por variante.
- Una imagen principal procesada, con versión de exhibición y miniatura.
- Detalle de producto, carrito persistente aislado por `storeSlug` y checkout
  invitado con retiro (`PICKUP`).
- Historial local “Mis pedidos”: el navegador conserva referencias y tokens por
  comercio, pero cada consulta vuelve al backend como fuente autoritativa.

### Inventario y pedidos

- Balance de stock por variante y movimientos auditables de entrada, salida y
  ajuste.
- Stock inicial, recepción de mercadería y umbral configurable de stock bajo.
- Reservas temporales al crear pedidos, consumo al confirmar y liberación al
  cancelar o vencer.
- Historial y transiciones administrativas de pedidos.

### Pagos

- Mercado Pago Checkout Pro con cuenta vendedora por comercio, retorno acotado,
  webhook, consulta server-to-server e idempotencia.
- Transferencia bancaria como flujo separado: instrucciones privadas asociadas al
  pedido, comprobante JPEG/PNG/PDF en storage privado, revisión administrativa,
  aprobación, rechazo y nuevo intento cuando corresponde.

### Emails

- Confirmación de pedido.
- Aviso de comprobante de transferencia rechazado.
- Branding por tenant en HTML y texto plano.
- Outbox persistente, worker, lease, reintentos y backoff.
- Un error SMTP no revierte pedido, pago ni inventario.

### Administración, plataforma y calidad

- Dashboard operativo, productos, categorías, inventario, pedidos,
  transferencias, pagos y configuración del comercio.
- Administración global de empresas, usuarios, provisioning, actividad,
  infraestructura sanitizada y apariencia.
- Flyway, tests unitarios y de integración, Testcontainers con MySQL 8.4, CI y
  construcción de una imagen Docker de mismo origen.
- Despliegue productivo en Cloudflare + DonWeb + Easypanel.

## Límites actuales

- El vertical inicial continúa orientado a indumentaria, aunque las opciones de
  variante ya son genéricas.
- El checkout ofrece retiro; no existe logística avanzada de envíos, tarifas,
  zonas ni transportistas.
- El carrito y el índice de pedidos recientes viven en el navegador; no existe
  todavía una cuenta cliente cross-device.
- Existe una sola imagen principal por producto.
- No se implementaron facturación fiscal, marketplace, cupones, devoluciones,
  reembolsos automáticos, reseñas, recomendaciones ni aplicaciones móviles.
- No hay microservicios, Redis, Kubernetes ni una promesa de escalabilidad
  ilimitada.

## Próxima versión

- Identidad visual definitiva y landing comercial.
- Validación con el primer comercio piloto real.
- Habilitación y checklist controlado de Mercado Pago en producción.
- Observabilidad operacional y documentación de incidentes.
- Restore drill con evidencia sobre una base aislada.
- Recuperación de contraseña y gestión ampliada de usuarios.
- Envíos configurables y múltiples imágenes por producto.

## Futuro potencial

- Clientes registrados y continuidad cross-device.
- Cupones, descuentos y promociones.
- Reportes avanzados y proyecciones históricas.
- Devoluciones y reembolsos.
- Productos relacionados y recomendaciones.
- Múltiples sucursales, depósitos, POS y omnicanalidad.
- API pública o aplicaciones móviles si existe una necesidad comercial validada.

El aislamiento database-per-tenant no es una capacidad futura ni un plan premium:
es el modelo actual para todos los comercios. Sus beneficios y costos están
documentados en [`ARQUITECTURA.md`](ARQUITECTURA.md).

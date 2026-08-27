# Roadmap

> El roadmap no asigna fechas. Las prioridades deben validarse con operación y
> aprendizaje del piloto; una mención aquí no constituye un compromiso comercial.

## Implementado

- Monolito modular Angular + Spring Boot servido desde un único origen.
- Login, sesiones, roles tenant y administración global.
- Base de control y database-per-tenant con provisioning y Flyway.
- Catálogo, categorías, productos, publicación, imagen principal y variantes
  genéricas.
- Inventario por variante, movimientos auditables, recepción y alertas de stock.
- Storefront, carrito persistente, checkout invitado, reservas y seguimiento de
  pedidos recientes.
- Operación administrativa de pedidos y dashboard.
- Mercado Pago Checkout Pro con webhook y confirmación server-to-server.
- Transferencia bancaria con comprobante privado y revisión manual.
- Emails transaccionales con branding, outbox y reintentos.
- Storage compatible con S3 sobre Cloudflare R2.
- CI para frontend, backend y Docker.
- Despliegue productivo bajo `comercioflex.com.ar` mediante Cloudflare, DonWeb y
  Easypanel.

## Próximos pasos

- Definir la identidad visual definitiva.
- Publicar una landing comercial separada de la tienda de cada tenant.
- Incorporar y acompañar al primer comercio/piloto real.
- Completar la habilitación controlada de Mercado Pago producción.
- Mejorar métricas, alertas y trazabilidad operacional.
- Ejecutar y registrar un restore drill completo.
- Consolidar runbooks de incidentes, despliegue, backup y recuperación.
- Adoptar versionado semántico para releases comerciales.

## Futuro

- Clientes registrados y recuperación de contraseña.
- Cuenta cliente y continuidad de carrito/pedidos entre dispositivos.
- Envíos avanzados, tarifas, zonas y transportistas.
- Cupones, descuentos y promociones.
- Múltiples imágenes por producto.
- Reportes y analítica avanzada.
- Devoluciones y reembolsos.
- Productos relacionados y recomendaciones.
- Múltiples sucursales o depósitos cuando el caso de uso lo justifique.

## Criterios de avance

Una capacidad no se considera operativamente cerrada sólo porque compila. Según
el riesgo debe contar con pruebas, documentación, observabilidad, configuración
segura y validación manual. En particular:

- los pagos reales requieren verificación server-to-server e idempotencia;
- los secretos nunca se incorporan al repositorio;
- los backups deben incluir control y tenants, además de coordinarse con objetos;
- un backup no se considera validado hasta realizar una restauración de prueba.

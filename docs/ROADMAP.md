# Roadmap

> Las fechas se definirán después de medir la velocidad del equipo.

## Fase 0 — Descubrimiento

Propuesta de alcance, arquitectura, datos, riesgos y decisiones. Termina con la
aprobación del Product Owner, no con este documento.

## Fase 1 — Base técnica

Repositorio, Angular SPA, Spring Boot 3.5/Java 21, base de control, dos bases tenant
de prueba, Flyway coordinado, entornos, health checks y ejecución reproducible.

## Fase 2 — Núcleo

Tenant, enrutamiento de conexiones, identidad, roles, categorías, productos,
variantes de talle/color y stock.

## Fase 3 — Compra

Catálogo público, carrito, checkout invitado, pedidos, estados y entrega básica.

Avance al 2026-07-30:

- Terminados: catálogo público, detalle de producto, carrito local aislado por
  comercio, checkout invitado con retiro y reserva temporal, y operación
  administrativa de pedidos con historial y consistencia de inventario.
- Siguiente: revisión del cierre de `ORD-02` y selección de la próxima historia
  prioritaria por el Product Owner.
- Fuera de esta etapa: envíos configurables, pago y clientes persistentes.

## Fase 4 — Pagos

OAuth, Checkout Pro de prueba, conexión por comercio, webhooks, validación e
idempotencia.

## Fase 5 — Operación

Dashboard mínimo, mejoras administrativas y alertas de stock.

## Fase 6 — Piloto

Despliegue, dominio/HTTPS, logs, backups, restauración y monitoreo.

## Puertas de control

Cada fase necesita criterios aceptados, demostración, pruebas y documentación. Una
fase no habilita automáticamente funcionalidades que estén fuera del MVP.

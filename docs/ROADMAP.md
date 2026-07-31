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

Avance al 2026-07-31:

- Terminados: catálogo público, detalle de producto, carrito local aislado por
  comercio, checkout invitado con retiro y reserva temporal, y operación
  administrativa de pedidos con historial y consistencia de inventario.
- Terminadas `ORD-02` y `PAY-01A`; `PAY-01B` está en pruebas con backend,
  frontend, identidad vendedora visible y tokens cifrados implementados. Falta
  el recorrido manual con credenciales TEST; todavía no crea cobros.
- Fuera de esta etapa: envíos configurables, pago y clientes persistentes.

## Fase 4 — Pagos

PAY-01 se divide en entregas revisables:

1. `PAY-01A`: dominio, estados, migraciones, cifrado y proveedor falso.
2. `PAY-01B`: OAuth Authorization Code con PKCE por comercio.
3. `PAY-01C`: preferencia, retorno Angular, inbox de webhooks y coordinación
   idempotente con pedidos/stock.
4. `PAY-01D`: sandbox real, HTTPS público controlado, observabilidad y hardening.

El MVP excluye medios offline, reembolsos automáticos, disputas y comisiones de
marketplace.

## Fase 5 — Operación

Dashboard mínimo, mejoras administrativas y alertas de stock.

## Fase 6 — Piloto

Despliegue, dominio/HTTPS, logs, backups, restauración y monitoreo.

## Puertas de control

Cada fase necesita criterios aceptados, demostración, pruebas y documentación. Una
fase no habilita automáticamente funcionalidades que estén fuera del MVP.

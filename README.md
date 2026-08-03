# Comercio Flex

Plataforma ecommerce multiempresa orientada inicialmente a comercios de
indumentaria, con un núcleo configurable para incorporar otros rubros. El proyecto
está en el Sprint 11: cierre operativo y observabilidad de Checkout Pro.

## Componentes

- `frontend/`: SPA Angular 22.
- `backend/`: API Spring Boot 3.5 sobre Java 21.
- `infra/`: MySQL 8.4 para desarrollo local.
- `docs/`: producto, arquitectura, backlog, decisiones y aprendizaje.

## Estado actual

La base técnica, identidad multiempresa, catálogo, variantes, inventario, tienda
pública, carrito, checkout invitado, operación de pedidos y Checkout Pro TEST ya
están implementados. El trabajo activo agrega observabilidad y recuperación
segura de webhooks antes del despliegue piloto.

La documentación principal está disponible en:

- [`docs/VISION_DEL_PRODUCTO.md`](docs/VISION_DEL_PRODUCTO.md)
- [`docs/ALCANCE_MVP.md`](docs/ALCANCE_MVP.md)
- [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md)
- [`docs/GUIA_DE_DESARROLLO.md`](docs/GUIA_DE_DESARROLLO.md)

Los comandos de instalación, ejecución y pruebas están en
[`docs/GUIA_DE_DESARROLLO.md`](docs/GUIA_DE_DESARROLLO.md).

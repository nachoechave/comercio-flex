# Comercio Flex

Plataforma ecommerce multiempresa para pequeños y medianos comercios. El MVP
usa un monolito modular: Angular 22 se compila dentro del artefacto Spring Boot
3.5/Java 21, MySQL 8.4 separa la base de control de las bases de cada comercio y
Checkout Pro procesa los pagos.

## Estado del MVP

El recorrido funcional incluye identidad y roles, catálogo, variantes, stock,
imagen principal, tienda pública, carrito, checkout invitado con **retiro en el
comercio**, pedidos, Checkout Pro TEST, operación de pedidos, dashboard y
configuración básica del comercio.

También están preparados:

- imagen JPEG/PNG verificada y recodificada, con versión de exhibición y miniatura;
- contacto, dirección/instrucciones de retiro y cuatro temas visuales;
- contenedor de producción de un solo origen, configuración Railway y CI;
- health/readiness, correlación de solicitudes y guías de backup/restore.

La aplicación está preparada para desplegar, pero **no se declara desplegada ni
habilitada para cobros reales**. Faltan recursos y decisiones externas: servicio
MySQL administrado, bucket S3/R2, dominio/DNS, secretos productivos de Mercado
Pago, comercio piloto y una prueba real de restauración.

## Componentes

- `frontend/`: SPA Angular y tienda/panel administrativo.
- `backend/`: API y aplicación Spring Boot modular.
- `infra/`: MySQL local, plantilla productiva y scripts operativos.
- `.github/workflows/`: integración continua.
- `docs/`: producto, arquitectura, API, backlog, decisiones y aprendizaje.

## Primeros pasos

La instalación, ejecución y pruebas están en
[`docs/GUIA_DE_DESARROLLO.md`](docs/GUIA_DE_DESARROLLO.md). La preparación de un
entorno real, sus variables y verificaciones están en
[`docs/GUIA_DE_DESPLIEGUE.md`](docs/GUIA_DE_DESPLIEGUE.md).

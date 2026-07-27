# Arquitectura propuesta

> Estado: decisiones principales aprobadas el 2026-07-23

## Vista general

Se propone un monolito modular: una aplicación Angular, una API Spring Boot y una
base MySQL. Es un despliegue sencillo, pero el código se divide por dominios para
evitar un monolito desordenado.

```text
Navegador
  → Angular (tienda pública / administración)
  → API REST Spring Boot
  → módulos de aplicación y dominio
  → adaptadores JPA / Mercado Pago
  → MySQL / API de Mercado Pago
```

## Módulos backend

- `identity`: credenciales, sesión, roles y permisos.
- `tenant`: comercio, configuración y capacidades activadas.
- `catalog`: categorías, productos, variantes, precios y publicación.
- `inventory`: existencias y movimientos.
- `customer`: datos mínimos del comprador.
- `order`: pedidos, ítems, totales, estados e historial.
- `delivery`: retiro y envío.
- `payment`: intentos, proveedor, webhooks e idempotencia.
- `reporting`: consultas simples, de solo lectura.
- `shared`: tipos transversales mínimos; no debe ser un cajón de sastre.

Dentro de cada módulo:

```text
api → application → domain
          ↑
infrastructure (implementa puertos)
```

Un módulo no accede directamente a repositorios o entidades internas de otro.

## Frontend

Una SPA Angular con componentes standalone y rutas lazy para dos shells:
tienda pública y administración. Organización por funcionalidad:

```text
page → componente UI → data-access/store → HttpClient → API
```

Signals administrará estado local y RxJS las operaciones HTTP. No se propone NgRx
para el MVP. Los guards mejoran la experiencia, pero la autorización real siempre
la aplica el backend.

## Multiempresa

Se aprobó una estrategia de **base de datos separada por comercio**. En MySQL,
`SCHEMA` y `DATABASE` son equivalentes. También se aprobó una base de control
compartida para registrar y resolver los comercios:

- una base de control compartida para identificar comercios, estado, slug,
  enrutamiento de conexión y metadatos operativos;
- una base de negocio por comercio para catálogo, inventario, clientes, pedidos y
  pagos;
- un router de conexiones backend que elige la base después de resolver el comercio;
- Flyway aplicado de forma controlada y verificable a todas las bases de comercio.

Aunque la separación física reduce el impacto de una consulta sin filtro, el
backend seguirá validando el tenant y nunca aceptará un identificador enviado por
el navegador como autoridad. Las pruebas con comercios A y B continúan siendo
obligatorias. La caché y el carrito frontend también se separan por comercio.

Consecuencias aceptadas: mayor aislamiento y restauración individual, a cambio de
más conexiones, provisión, migraciones, monitoreo, backups y costo operativo.

## Identidad y autorización

La identidad es global y reside en la base de control. Una `membership` vincula a
un usuario con un comercio y su rol. La sesión identifica al usuario, pero no
autoriza por sí sola el acceso a una base de negocio.

```text
Sesión autenticada
  → resolver comercio por path
  → comprobar membresía activa y rol
  → obtener referencia de conexión confiable
  → seleccionar base del comercio
  → ejecutar operación
  → limpiar contexto del tenant
```

El backend no aceptará un nombre de base, identificador interno de tenant ni rol
enviado por el navegador como fuente de autoridad.

## Inventario del MVP

Cada variante vendible tiene una única existencia dentro de la base del comercio.
Los movimientos registran aumentos y disminuciones con motivo, fecha y actor. El
modelo no incluye sucursales, depósitos ni transferencias en el MVP; estas
capacidades quedan preparadas como evolución explícita y no como complejidad
anticipada.

## Pagos

El dominio depende de un puerto `PaymentGateway`; Mercado Pago es un adaptador.
Checkout Pro reduce la superficie de datos de tarjeta. El backend recalcula el
importe, crea la preferencia y valida el pago servidor a servidor. El retorno del
navegador nunca confirma el pedido. Webhooks firmados e idempotentes actualizan el
estado después de verificar cuenta, referencia, moneda e importe.

## Versiones aprobadas y candidatas verificadas el 2026-07-23

- Angular 22 + Node 24 LTS: compatibles según la tabla oficial de Angular.
- Spring Boot 3.5 + Java 21 LTS: línea aprobada por estabilidad.
- MySQL 8.4 LTS.
- Flyway y Maven; versiones exactas se fijarán al inicializar.

Fuentes: [Angular](https://angular.dev/reference/versions),
[Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html),
[MySQL 8.4](https://dev.mysql.com/doc/refman/8.4/en/).

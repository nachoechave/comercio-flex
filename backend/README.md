# Backend de Comercio Flex

Aplicación Spring Boot/Java 21 que implementa el monolito modular de Comercio
Flex. Expone la API, sirve el build Angular en producción y coordina MySQL,
Cloudflare R2, Resend SMTP y Mercado Pago mediante adaptadores.

## Estructura

`src/main/java/com/comercioflex/` contiene módulos de dominio:

- `tenant`, `identity` y `platformadmin`;
- `catalog`, `inventory` y `order`;
- `payment`, `media` y `notification`;
- `dashboard`, `config` y `shared`.

Los módulos separan `api`, `application`, `domain` e `infrastructure` cuando el
tamaño lo requiere. El sistema es un monolito modular, no microservicios.

## Bases y migraciones

Flyway mantiene dos líneas de migración:

- `db/migration/control`: identidad global, tenants, sesiones y coordinación;
- `db/migration/tenant`: catálogo, inventario, pedidos, pagos y notificaciones.

El perfil productivo migra control y todas las bases tenant registradas usando
credenciales DDL separadas. Hibernate valida el esquema y no lo genera.

## Profiles

- configuración base: `application.yml`;
- desarrollo local: `application-local.yml`;
- producción: `application-prod.yml`.

Los valores sensibles se reciben mediante variables de entorno. Nunca completar
los YAML con passwords, claves R2, credenciales SMTP o tokens de Mercado Pago.

## Ejecución local

Primero levantar MySQL según [`../infra/README.md`](../infra/README.md). Luego,
desde `backend/`, configurar las contraseñas locales requeridas y ejecutar:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:CONTROL_DB_PASSWORD = "<secret>"
$env:MIGRATION_DB_PASSWORD = "<secret>"
$env:TENANT_A_DB_PASSWORD = "<secret>"
$env:TENANT_B_DB_PASSWORD = "<secret>"
./mvnw.cmd spring-boot:run
```

La configuración detallada y los fixtures locales están en
[`../docs/GUIA_DE_DESARROLLO.md`](../docs/GUIA_DE_DESARROLLO.md).

## Tests

```powershell
./mvnw.cmd test
```

En Linux/macOS:

```sh
./mvnw test
```

Las integraciones relevantes usan Testcontainers con MySQL 8.4; Docker debe
estar disponible para que esas pruebas se ejecuten realmente.

## Build

```powershell
./mvnw.cmd -DskipTests package
```

El build backend aislado no incorpora automáticamente el frontend. El
`Dockerfile` de la raíz construye ambas aplicaciones y copia el resultado Angular
a `src/main/resources/static` antes de empaquetar el JAR.

## Grupos de configuración

- Database/tenant: `CONTROL_DB_*`, `TENANT_*`, `MIGRATION_DB_*`.
- Media: `MEDIA_*`.
- Comprobantes: `PAYMENT_RECEIPT_*`.
- Mercado Pago: `PAYMENTS_*`, `PAYMENT_*`, `MP_*`.
- Email: `EMAIL_*`, `SMTP_*`.
- Bootstrap: `SUPER_ADMIN_BOOTSTRAP_*`.

Usar `infra/production.env.example` como inventario de nombres, nunca como
archivo de secretos productivos.

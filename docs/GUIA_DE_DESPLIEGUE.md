# Guía de despliegue

> Documento conceptual del entorno actual. No contiene IP, credenciales, nombres
> internos de servicios ni procedimientos que otorguen acceso administrativo.

## Producción

- Dominio: [https://comercioflex.com.ar](https://comercioflex.com.ar)
- DNS y edge HTTPS: Cloudflare.
- Servidor: DonWeb Cloud Server.
- Panel y deployment: Easypanel.
- Aplicación: contenedor de mismo origen con Angular y Spring Boot.
- Datos: MySQL 8.4, base de control y una base por tenant.
- Objetos: Cloudflare R2.
- Email: Resend mediante SMTP.
- Pagos: Mercado Pago y transferencia bancaria.

```text
Cloudflare
  → DonWeb
  → Easypanel
  → Comercio Flex
      ├── MySQL 8.4
      ├── Cloudflare R2
      ├── Resend SMTP
      └── Mercado Pago
```

Angular se compila dentro del JAR y Spring Boot sirve SPA y API bajo un único
origen. Esto mantiene cookies, sesión y CSRF sin depender de CORS entre dos
deployments.

## Artefacto desplegable

El `Dockerfile` multi-stage:

1. instala dependencias y construye Angular con Node;
2. empaqueta Spring Boot con Java 21/Maven e incorpora los archivos estáticos;
3. ejecuta el JAR con un usuario sin privilegios;
4. expone el puerto configurado por `PORT`.

La imagen no incluye `.env`, secretos, datos locales ni credenciales. GitHub
Actions construye la imagen para validarla, pero no la publica ni hace deploy.

## Configuración por entorno

Los valores reales pertenecen al gestor de variables de Easypanel. El archivo
`infra/production.env.example` documenta nombres y placeholders, no valores que
puedan utilizarse directamente en producción.

### Aplicación y URLs

- `SPRING_PROFILES_ACTIVE`
- `PORT`
- `PUBLIC_FRONTEND_BASE_URI`
- `PUBLIC_BACKEND_BASE_URI`
- `FRONTEND_ORIGINS`
- `SESSION_COOKIE_SECURE`

Las URLs públicas deben usar HTTPS y el dominio esperado. No registrar URLs con
tokens, lookup tokens o parámetros sensibles.

### Base de datos y tenants

- `CONTROL_DB_URL`, `CONTROL_DB_USER`, `CONTROL_DB_PASSWORD`
- `TENANT_PROVISIONING_ENABLED`, `TENANT_DB_URL_TEMPLATE`
- `TENANT_DATABASE_PREFIX`, `TENANT_DATABASE_USER_HOST`
- `TENANT_SHARED_DB_USER`, `TENANT_SHARED_DB_PASSWORD`
- `TENANT_PROVISIONING_DB_URL`, `TENANT_PROVISIONING_DB_USER`,
  `TENANT_PROVISIONING_DB_PASSWORD`
- `MIGRATION_DB_USER`, `MIGRATION_DB_PASSWORD`

El usuario runtime sólo necesita DML. Flyway utiliza credenciales de migración;
el usuario de provisioning requiere únicamente las capacidades necesarias para
crear la base y otorgar permisos. Las conexiones estáticas `TENANT_A_*` y
`TENANT_B_*` existen sólo como compatibilidad y desarrollo.

### Media y comprobantes

- `MEDIA_STORAGE`
- `MEDIA_S3_BUCKET`, `MEDIA_S3_REGION`, `MEDIA_S3_ENDPOINT`
- `MEDIA_S3_ACCESS_KEY`, `MEDIA_S3_SECRET_KEY`, `MEDIA_S3_PATH_STYLE`
- `PAYMENT_RECEIPT_STORAGE`
- `PAYMENT_RECEIPT_S3_BUCKET`, `PAYMENT_RECEIPT_S3_REGION`,
  `PAYMENT_RECEIPT_S3_ENDPOINT`
- `PAYMENT_RECEIPT_S3_ACCESS_KEY`, `PAYMENT_RECEIPT_S3_SECRET_KEY`,
  `PAYMENT_RECEIPT_S3_PATH_STYLE`

Producto/branding y comprobantes deben utilizar buckets o credenciales separados
según su función. Los comprobantes son privados: no se publican con URL permanente.

### Email

- `EMAIL_ENABLED`
- `EMAIL_PROVIDER`, `EMAIL_FROM_NAME`, `EMAIL_FROM_ADDRESS`
- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_STARTTLS`
- `EMAIL_SMTP_*_TIMEOUT_MS`
- `EMAIL_OUTBOX_WORKER_ENABLED`, `EMAIL_OUTBOX_POLL_INTERVAL_MS`
- `EMAIL_OUTBOX_BATCH_SIZE`, `EMAIL_OUTBOX_MAX_ATTEMPTS`
- `EMAIL_OUTBOX_INITIAL_BACKOFF_SECONDS`, `EMAIL_OUTBOX_MAX_BACKOFF_SECONDS`
- `EMAIL_OUTBOX_SENDING_TIMEOUT_SECONDS`

El proveedor actual es Resend a través de SMTP. `EMAIL_ENABLED` permanece `false`
por defecto en el código; habilitarlo en un entorno exige que remitente, SMTP y
templates hayan sido verificados. No documentar ni copiar la API key/contraseña.

### Mercado Pago

- `PAYMENTS_MERCADO_PAGO_ENABLED`, `PAYMENTS_CHECKOUT_PRO_ENABLED`
- `PAYMENT_MODE`
- `MP_CLIENT_ID`, `MP_CLIENT_SECRET`, `MP_OAUTH_REDIRECT_URI`
- `MP_TEST_ACCESS_TOKEN`, `MP_TEST_SELLER_ACCOUNT_ID`,
  `MP_TEST_DEMO_TENANT_SLUG`
- `MP_WEBHOOK_SECRET`
- `PAYMENT_TOKEN_ACTIVE_KEY_ID`, `PAYMENT_TOKEN_ENCRYPTION_KEY_V1`

La habilitación productiva se realiza sólo después de validar dominio, callback,
webhook, cuenta vendedora, cifrado, reconciliación e idempotencia. Nunca usar el
estado recibido por la URL de retorno como confirmación.

### Bootstrap global

Las variables `SUPER_ADMIN_BOOTSTRAP_*` permiten crear únicamente la primera
cuenta global. El procedimiento es de un solo uso: se habilita, se valida el
acceso, se vuelve a deshabilitar y se retira la contraseña del entorno. No debe
quedar en archivos, tickets, logs ni capturas.

## Secuencia de deployment

1. Confirmar CI verde y revisar el diff/versionado que se desplegará.
2. Construir la imagen usando el `Dockerfile` del repositorio.
3. Configurar variables en Easypanel sin copiarlas al repositorio.
4. Verificar conectividad y permisos de MySQL, R2, Resend y Mercado Pago.
5. Aplicar Flyway con el usuario de migración y arrancar la aplicación.
6. Esperar readiness antes de dirigir tráfico.
7. Ejecutar smoke tests de storefront, login, catálogo, checkout y operación.
8. Revisar logs mediante `X-Request-Id`, sin exponer PII ni secretos.
9. Verificar backup automático y el estado del último restore drill.

Esta guía no automatiza cambios en Easypanel ni reemplaza la revisión humana del
entorno productivo.

## Health checks

- `/actuator/health/liveness`: confirma que el proceso está vivo.
- `/actuator/health/readiness`: confirma que puede recibir tráfico.

Los detalles permanecen ocultos. Un health check correcto no prueba pagos,
emails, aislamiento tenant ni recuperación de datos.

## Pagos

### Mercado Pago

La confirmación es server-to-server: el webhook o una reconciliación autorizada
disparan una consulta al proveedor, y el backend valida vendedor, preferencia,
referencia, importe, moneda e idempotencia antes de modificar el pedido.

### Transferencia bancaria

Es un flujo independiente. El cliente inicia el intento, recibe instrucciones
privadas, sube un comprobante y espera revisión. El administrador descarga el
objeto privado y aprueba o rechaza; un rechazo puede habilitar un nuevo intento.
No utilizar buckets públicos para comprobantes.

## Emails transaccionales

```text
evento
  → transactional_email_outbox
  → worker
  → SMTP
  → Resend
```

La outbox aplica clave interna de evento, lease, reintentos y backoff. Los errores
de SMTP se registran de forma sanitizada y no revierten pedido, pago o inventario.

## Backups

La operación productiva automatiza los dumps de las bases configuradas y su
almacenamiento privado en R2. La estrategia completa incluye:

- backup automatizado de control DB y todas las bases tenant;
- almacenamiento privado en el bucket de backups de R2;
- monitoreo de ejecución y tamaño esperado;
- retención configurada por operación;
- coordinación con objetos de producto y comprobantes.

`infra/operations/mysql-backup.sh` genera un dump consistente para InnoDB,
comprime, valida gzip y aplica `BACKUP_RETENTION_DAYS` al destino donde se ejecuta.
El scheduling y la transferencia a R2 son responsabilidad del entorno y no están
implementados por GitHub Actions.

## Restore seguro

Nunca restaurar primero sobre producción. Utilizar una base aislada, habilitar
explícitamente `RESTORE_CONFIRMED=yes` y validar:

- historial Flyway;
- tenants esperados;
- pedidos e importes críticos;
- balances y movimientos de inventario;
- referencias y acceso a objetos privados.

**Un backup no se considera validado hasta realizar una restauración de prueba.**

## Railway, Render y Aiven

Render y Aiven fueron alternativas documentadas durante la preparación inicial;
ya no describen la infraestructura principal. `railway.json` permanece en la raíz
como archivo legacy y no es consumido por el deployment actual de Easypanel.

Recomendación: conservarlo temporalmente para no romper integraciones externas
desconocidas y eliminarlo en una tarea separada cuando se confirme que ninguna
automatización lo usa.

# Guía de despliegue

> Estado al 2026-08-04: preparación versionada; no existe un despliegue
> productivo declarado ni autoriza contratar servicios o activar cobros reales.

## Arquitectura recomendada para el primer piloto

Un contenedor sirve Angular y Spring Boot desde un único origen HTTPS. Railway
puede construir el `Dockerfile` y validar `/actuator/health/readiness`. La base
debe ser MySQL administrada y las imágenes deben vivir en un bucket privado
compatible con S3, como Cloudflare R2.

Esta opción evita cookies cross-site y CORS entre frontend/backend. Se inicia con
una réplica porque la sesión vive en memoria; escalar horizontalmente requiere
sticky sessions o un almacén compartido de sesiones.

## Qué ya está preparado

- `Dockerfile` multi-stage: Node 24 construye Angular, Maven/Java 21 empaqueta el
  backend y el runtime corre como usuario `comercioflex` sin privilegios.
- `.dockerignore` evita enviar secretos, builds y datos locales al contexto.
- `railway.json` selecciona Docker, readiness y reinicio ante fallos.
- `application-prod.yml` activa S3 y credenciales separadas de migración/runtime.
- `infra/production.env.example` enumera variables sin valores reales.
- `.github/workflows/ci.yml` prueba frontend/backend y construye la imagen.
- `infra/operations/mysql-backup.sh` y `mysql-restore.sh` preparan recuperación.

## Recursos externos necesarios

1. Proyecto Railway (u otro runtime Docker) con dominio/HTTPS.
2. MySQL administrado con base de control y una base por tenant.
3. Bucket S3/R2 privado y credenciales limitadas al prefijo de Comercio Flex.
4. Gestor de secretos del proveedor.
5. Cuenta vendedora, aplicación y credenciales de Mercado Pago.
6. Destino externo para backups y responsable de incidentes.

No usar el filesystem efímero del contenedor para imágenes o backups productivos.

## Variables de producción

Copiar conceptualmente `infra/production.env.example` al gestor de secretos; no
crear un `.env` productivo versionado.

Grupos principales:

- URLs/origen: `PUBLIC_FRONTEND_BASE_URI`, `PUBLIC_BACKEND_BASE_URI`,
  `FRONTEND_ORIGINS`, `SESSION_COOKIE_SECURE`.
- Base de control: `CONTROL_DB_URL`, `CONTROL_DB_USER`, `CONTROL_DB_PASSWORD`.
- Tenant: `TENANT_*_DB_URL`, `TENANT_*_DB_USER`, `TENANT_*_DB_PASSWORD`.
- Migraciones: `MIGRATION_DB_USER`, `MIGRATION_DB_PASSWORD`.
- Medios: `MEDIA_S3_BUCKET`, `MEDIA_S3_REGION`, `MEDIA_S3_ENDPOINT`,
  `MEDIA_S3_ACCESS_KEY`, `MEDIA_S3_SECRET_KEY`, `MEDIA_S3_PATH_STYLE`.
- Pagos: modo, token TEST/productivo, OAuth, secreto webhook y clave AES-256
  versionada para cifrar tokens.
- Primer acceso global: las cuatro variables `SUPER_ADMIN_BOOTSTRAP_*`, sólo
  durante el despliegue inicial descrito abajo.

Usar un usuario DML diferente para control y para cada tenant. Sólo el usuario de
migración recibe DDL. El usuario del bucket necesita leer/escribir/eliminar objetos
del bucket/prefijo asignado, no administrar la cuenta completa.

### Crear el primer `SUPER_ADMIN` en producción

El bootstrap productivo es explícito, idempotente para el mismo correo y sólo
puede crear el primer `SUPER_ADMIN`. No reemplaza contraseñas, no convierte un
usuario normal en administrador y no crea membresías tenant.

1. Cargar en el entorno privado del backend:

   ```text
   SUPER_ADMIN_BOOTSTRAP_ENABLED=true
   SUPER_ADMIN_BOOTSTRAP_EMAIL=correo-real-del-operador
   SUPER_ADMIN_BOOTSTRAP_PASSWORD=contraseña-única-de-al-menos-12-caracteres
   SUPER_ADMIN_BOOTSTRAP_DISPLAY_NAME=Administrador de plataforma
   ```

2. Guardar y desplegar. El arranque aplica primero las migraciones y luego crea
   la cuenta con contraseña cifrada.
3. Esperar que el servicio quede saludable e iniciar sesión con ese correo y
   contraseña. Confirmar que abre `/superadmin`.
4. Inmediatamente establecer `SUPER_ADMIN_BOOTSTRAP_ENABLED=false`, eliminar
   `SUPER_ADMIN_BOOTSTRAP_PASSWORD` del proveedor y volver a desplegar. También
   se pueden retirar el correo y el nombre, porque ya no se necesitan.
5. Confirmar que el segundo despliegue queda saludable y que la misma cuenta
   sigue pudiendo iniciar sesión.

Nunca guardar los valores reales en Git ni compartir la contraseña en logs o
capturas. Si ya existe un administrador con otro correo, el arranque falla de
forma intencional para impedir una elevación accidental.

## Secuencia de primera demostración

1. Crear MySQL y usuarios de mínimo privilegio.
2. Crear bucket privado; bloquear acceso público.
3. Configurar variables TEST y URLs HTTPS definitivas.
4. Conectar repositorio y desplegar con el `Dockerfile`.
5. Confirmar CI verde y readiness `UP`.
6. Ejecutar smoke manual: tienda, imagen, carrito, `PICKUP`, pedido, pago TEST,
   webhook, administración y aislamiento tenant.
7. Revisar logs por `X-Request-Id`, sin tokens ni datos sensibles.
8. Ejecutar backup y practicar restore en otra base; guardar evidencia.

## Health checks y diagnóstico

- `/actuator/health/liveness`: proceso vivo; no debe depender de un servicio
  externo inestable.
- `/actuator/health/readiness`: instancia apta para tráfico; Railway lo consulta.
- `X-Request-Id`: copiarlo al reportar un error. Nunca pegar tokens o URLs privadas
  de pedido en tickets públicos.

Un health check correcto no reemplaza un smoke funcional ni verifica Mercado Pago.

## Backup

Ejemplo en un job seguro que tenga cliente MySQL y destino persistente:

```sh
MYSQL_HOST=mysql.internal \
MYSQL_USER=backup_user \
MYSQL_PASSWORD='secret-manager-value' \
MYSQL_DATABASES='comercio_flex_control comercio_flex_tenant_a' \
BACKUP_DIRECTORY=/mounted-backups \
BACKUP_RETENTION_DAYS=14 \
./infra/operations/mysql-backup.sh
```

El script usa transacción consistente para InnoDB y valida el gzip. Cifrar el
destino, restringir acceso y monitorear que el job realmente genere archivos.
Respaldar/versionar también el bucket de imágenes con la política del proveedor.

## Restore seguro

Nunca restaurar primero sobre producción. Crear una base aislada y ejecutar:

```sh
MYSQL_HOST=recovery.internal \
MYSQL_USER=restore_user \
MYSQL_PASSWORD='secret-manager-value' \
BACKUP_FILE=/backups/comercio-flex-fecha.sql.gz \
RESTORE_CONFIRMED=yes \
./infra/operations/mysql-restore.sh
```

Después verificar Flyway, cantidad de tenants, totales de pedidos, inventario,
referencias de imágenes y una muestra de lectura desde el bucket. Registrar fecha,
archivo, responsable y resultado. Sin esta práctica, OPS-01 sigue en pruebas.

## Paso de TEST a producción de Mercado Pago

Mantener `PAYMENT_MODE=TEST` y pagos reales deshabilitados hasta contar con dominio
estable, webhook HTTPS, cuenta vendedora correcta y checklist aprobado. Luego:

1. cargar secretos productivos sólo en el gestor del proveedor;
2. comprobar en administración “Conectada a” la cuenta receptora;
3. realizar un cobro pequeño y controlado;
4. verificar el pago por API/webhook, no sólo por redirección;
5. comprobar idempotencia y conciliación del pedido;
6. documentar reversión y soporte.

## Alternativas y crecimiento

- Demostración/primer piloto: Railway + MySQL administrado + R2, una réplica.
- Primer cliente con mayores garantías: plataforma de contenedores y MySQL
  administrado con backups/PITR y soporte contratado.
- Crecimiento: RDS MySQL o equivalente, CDN para medios, sesión compartida y más
  réplicas después de medir carga.

Los planes gratuitos con suspensión no son apropiados para un cliente real. Antes
de contratar, revisar precios, backups, región, soporte, egress y política de
inactividad actuales.

## Criterio de terminado

El repositorio está “listo para desplegar” cuando CI y build son verdes. El
despliegue real sólo está “Terminado” cuando HTTPS, secrets, smoke, monitoreo,
backup y restore tienen evidencia. Credenciales, dominio, proveedor y comercio
piloto son pendientes externos y no deben marcarse automáticamente.

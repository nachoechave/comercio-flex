# Guía de despliegue

> Estado al 2026-08-18: despliegue previsto en Render con Aiven MySQL. Los
> secretos y las operaciones de Aiven siguen siendo responsabilidad del operador.

## Arquitectura recomendada para el primer piloto

Un contenedor sirve Angular y Spring Boot desde un único origen HTTPS. Render
construye el `Dockerfile` y puede validar `/actuator/health/readiness`. Aiven
aloja la base de control y una base MySQL aislada por tenant; las imágenes viven
en un bucket privado compatible con S3, como Cloudflare R2.

Esta opción evita cookies cross-site y CORS entre frontend/backend. Las sesiones
se guardan por JDBC en la base de control, por lo que no dependen de la memoria
de una réplica concreta.

## Qué ya está preparado

- `Dockerfile` multi-stage: Node 24 construye Angular, Maven/Java 21 empaqueta el
  backend y el runtime corre como usuario `comercioflex` sin privilegios.
- `.dockerignore` evita enviar secretos, builds y datos locales al contexto.
- `railway.json` selecciona Docker, readiness y reinicio ante fallos.
- `application-prod.yml` activa S3 y migraciones; las altas nuevas usan el
  provider dinámico y no requieren bloques `TENANT_C`, `TENANT_D`, etc.
- `infra/production.env.example` enumera variables sin valores reales.
- `.github/workflows/ci.yml` prueba frontend/backend y construye la imagen.
- `infra/operations/mysql-backup.sh` y `mysql-restore.sh` preparan recuperación.

## Recursos externos necesarios

1. Web Service de Render (u otro runtime Docker) con dominio/HTTPS.
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
- Provider dinámico: `TENANT_PROVISIONING_ENABLED`, `TENANT_DB_URL_TEMPLATE`,
  `TENANT_DATABASE_PREFIX`, `TENANT_PROVISIONING_DB_URL`,
  `TENANT_PROVISIONING_DB_USER`, `TENANT_PROVISIONING_DB_PASSWORD`.
- Runtime tenant: `TENANT_SHARED_DB_USER`, `TENANT_SHARED_DB_PASSWORD`.
- Migraciones control/tenant: `MIGRATION_DB_USER`, `MIGRATION_DB_PASSWORD`.
- Medios: `MEDIA_S3_BUCKET`, `MEDIA_S3_REGION`, `MEDIA_S3_ENDPOINT`,
  `MEDIA_S3_ACCESS_KEY`, `MEDIA_S3_SECRET_KEY`, `MEDIA_S3_PATH_STYLE`.
- Pagos: modo, token TEST/productivo, OAuth, secreto webhook y clave AES-256
  versionada para cifrar tokens.
- Primer acceso global: las cuatro variables `SUPER_ADMIN_BOOTSTRAP_*`, sólo
  durante el despliegue inicial descrito abajo.

No reutilizar credenciales entre control, runtime tenant, migración y provider.
El usuario runtime tenant sólo recibe DML; el de migración recibe DDL sobre cada
base administrada; el provider crea bases y otorga esos permisos. `avnadmin`, si
resulta necesario por las restricciones de Aiven, se usa sólo como secreto del
provider. El usuario del bucket necesita leer/escribir/eliminar objetos del
bucket/prefijo asignado, no administrar la cuenta completa.

Las conexiones fijas `tenant-a` y `tenant-b` son opcionales en producción. Para
habilitar una se deben definir juntas sus variables `*_DB_URL`, `*_DB_USER` y
`*_DB_PASSWORD`; si las tres están vacías se omite, y una configuración parcial
detiene el arranque para evitar una empresa aparentemente activa pero inaccesible.

### Aprovisionamiento dinámico en Render + Aiven

El adapter actual implementa el puerto desacoplado `TenantProvisioner` mediante
MySQL estándar. No necesita la API de Aiven: crea la base por SQL, ejecuta Flyway,
inicializa `store_settings`, registra el datasource en memoria y persiste sólo
metadatos sanitizados (`database_name`, estado y motivo seguro). Las contraseñas
MySQL permanecen como secretos de Render y nunca se guardan en la base de control.

Preparación única en Aiven:

1. Crear `comercio_flex_control` y los service users de control runtime, runtime
   tenant y migración. Aiven administra la creación de service users desde su
   consola, CLI o API.
2. Dar al usuario de control sólo DML sobre `comercio_flex_control`. Dar al de
   migración el DDL requerido por Flyway, pero no permisos globales para crear
   bases.
3. Configurar como provider una cuenta separada capaz de `CREATE DATABASE` y
   `GRANT`. Los usuarios runtime/migración deben existir antes, porque el adapter
   sólo les concede permisos exactos sobre la nueva base.
4. Copiar a Render las variables de `infra/production.env.example`, usando el
   host y puerto de Aiven y `sslMode=REQUIRED`. No pegar el Service URI completo
   en logs, tickets ni Git.
5. Desplegar una vez y abrir `/superadmin/empresas/nueva`. La pantalla consulta
   la capacidad del provider y explica qué variable falta sin mostrar valores.

Para cada alta posterior no se agregan variables ni se redespliega: el estado
pasa por `PROVISIONING`; si base, Flyway, configuración o registro runtime fallan,
queda `PROVISIONING_FAILED` con motivo seguro y puede reintentarse. Sólo después
de completar todos los pasos pasa al estado solicitado (`ACTIVE` o `INACTIVE`).
Al reiniciar, las bases dinámicas `READY` se migran y registran desde la metadata
de control usando el template, por lo que no dependen de `TENANT_A/TENANT_B`.

El runtime usa una cuenta de servicio compartida por el backend, pero cada
datasource apunta a una sola base y el routing exige el `TenantContext` resuelto
desde una empresa activa. Esa cuenta no se entrega a propietarios ni tenants y
carece de DDL. La prueba de integración verifica que el propietario de una
empresa no puede usar endpoints administrativos de otra.

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
4. Conectar el repositorio a Render y desplegar con el `Dockerfile`.
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

- Demostración/primer piloto: Render + Aiven MySQL + R2, una réplica.
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

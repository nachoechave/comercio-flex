# Infraestructura local

Esta carpeta levanta MySQL para desarrollo sin instalar el servidor directamente
en Windows. La imagen está fijada en MySQL `8.4.10`, versión LTS publicada por
Oracle el 16 de junio de 2026.

## Qué se crea

- `comercio_flex_control`: registro y configuración global de comercios.
- `comercio_flex_tenant_a`: datos aislados del comercio de desarrollo A.
- `comercio_flex_tenant_b`: datos aislados del comercio de desarrollo B.
- Un usuario de aplicación con permisos `SELECT`, `INSERT`, `UPDATE` y `DELETE`.
- Un usuario de migraciones con permisos de datos y DDL, sólo sobre estas tres
  bases.
- Un volumen nombrado `comercio-flex-mysql-data`.

La cuenta `root` queda reservada para inicialización y tareas administrativas.
El backend no debe conectarse con ella.

## Requisitos

- Docker Desktop iniciado y configurado para contenedores Linux con WSL 2.
- Docker Compose v2, incluido en Docker Desktop.
- Puerto `3306` libre, o definir otro valor para `MYSQL_PORT`.

Comprobar la instalación desde PowerShell:

```powershell
docker version
docker compose version
```

## Primera ejecución en PowerShell

Desde la raíz del repositorio:

```powershell
Copy-Item -LiteralPath .\infra\.env.example -Destination .\infra\.env
```

Editar `infra/.env` y reemplazar todas las contraseñas de ejemplo. Los caracteres
aceptados por el script inicial son letras, números y `_ @ % + = : , . -`.
El archivo `.env` está ignorado por Git.

Validar la configuración y levantar MySQL:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yaml config
docker compose --env-file .\infra\.env -f .\infra\compose.yaml up -d
docker compose --env-file .\infra\.env -f .\infra\compose.yaml ps
```

Seguir el arranque:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yaml logs -f mysql
```

Detenerlo sin perder datos:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yaml down
```

## Verificación manual

Esperar hasta que `docker compose ... ps` muestre `healthy` y ejecutar:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yaml exec mysql mysql --user="$((Get-Content .\infra\.env | Where-Object { $_ -match '^MYSQL_APP_USER=' }) -replace '^MYSQL_APP_USER=','')" --password="$((Get-Content .\infra\.env | Where-Object { $_ -match '^MYSQL_APP_PASSWORD=' }) -replace '^MYSQL_APP_PASSWORD=','')" --execute="SHOW DATABASES;"
```

El usuario de aplicación debe ver las tres bases. Para comprobar sus permisos:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yaml exec mysql mysql --user="$((Get-Content .\infra\.env | Where-Object { $_ -match '^MYSQL_APP_USER=' }) -replace '^MYSQL_APP_USER=','')" --password="$((Get-Content .\infra\.env | Where-Object { $_ -match '^MYSQL_APP_PASSWORD=' }) -replace '^MYSQL_APP_PASSWORD=','')" --execute="SHOW GRANTS;"
```

El cliente puede advertir que proporcionar la contraseña por línea de comandos
no es seguro. Estos comandos son únicamente para verificación local; no deben
usarse en automatización ni producción.

## Inicialización e idempotencia

MySQL ejecuta los archivos de `mysql/init/` automáticamente **sólo cuando el
directorio de datos está vacío**. El script usa `IF NOT EXISTS` y puede volver a
ejecutarse manualmente para reparar bases, usuarios o permisos:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yaml exec mysql bash /docker-entrypoint-initdb.d/01-create-development-databases.sh
```

Cambiar una contraseña en `.env` no modifica automáticamente el usuario dentro
de un volumen ya inicializado. Después de cambiarla, ejecutar otra vez el script
anterior.

## Advertencia sobre el volumen

Este comando elimina contenedores pero conserva los datos:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yaml down
```

Agregar `--volumes` elimina definitivamente el volumen local, todas las tablas y
todos los datos. No ejecutarlo salvo que se quiera reinicializar desde cero:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yaml down --volumes
```

El volumen facilita el desarrollo, pero **no es un backup**. Nunca debe contener
la única copia de información importante.

## Problemas frecuentes

- `docker` no se reconoce: terminar la instalación, iniciar Docker Desktop y
  abrir una terminal nueva.
- El daemon no responde: comprobar que Docker Desktop esté ejecutándose y use
  contenedores Linux.
- El puerto `3306` está ocupado: cambiar `MYSQL_PORT` en `.env`, por ejemplo a
  `3307`; dentro de la red Docker MySQL continúa escuchando en `3306`.
- No aparecen las bases después de cambiar el script: el volumen ya estaba
  inicializado. Reejecutar el script manualmente o, si se pueden perder todos los
  datos, recrear el volumen.
- `Access denied`: verificar el usuario/contraseña y reejecutar el script de
  inicialización después de modificar `.env`.
- Un valor de usuario o contraseña es rechazado: usar solamente los caracteres
  permitidos documentados arriba.

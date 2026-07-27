# Guía de desarrollo

## Requisitos locales

- Git.
- Java 21.
- Node 24.15 o una versión compatible con Angular 22.
- Docker Desktop iniciado con contenedores Linux.

No es necesario instalar Maven globalmente: `backend/mvnw.cmd` descarga y fija la
versión necesaria.

## 1. Preparar MySQL

Desde la raíz, copiar el ejemplo local:

```powershell
Copy-Item -LiteralPath .\infra\.env.example -Destination .\infra\.env
```

Cambiar las contraseñas ficticias en `infra/.env` y ejecutar:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yaml up -d
docker compose --env-file .\infra\.env -f .\infra\compose.yaml ps
```

El estado debe ser `healthy`. `infra/README.md` explica permisos, problemas
frecuentes y cómo detener el contenedor sin borrar datos.

## 2. Ejecutar el backend

Abrir PowerShell en `backend/`:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:CONTROL_DB_PASSWORD = "<MYSQL_APP_PASSWORD de infra/.env>"
$env:MIGRATION_DB_PASSWORD = "<MYSQL_MIGRATION_PASSWORD de infra/.env>"
.\mvnw.cmd spring-boot:run
```

El perfil `local` migra la base de control y las dos bases tenant conocidas. La
aplicación queda en `http://localhost:8080`.

Verificar:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

## 3. Ejecutar el frontend

En otra terminal, desde `frontend/`:

```powershell
npm.cmd install
npm.cmd start
```

Abrir `http://localhost:4200`. La página debe mostrar “Disponible”. El proxy
redirige `/actuator` al backend y evita fijar una URL de desarrollo dentro del
servicio Angular.

## Pruebas

```powershell
Set-Location .\backend
.\mvnw.cmd test

Set-Location ..\frontend
npm.cmd test -- --watch=false
npm.cmd run build
```

Las pruebas backend necesitan Docker porque usan MySQL real mediante Testcontainers.

## Convenciones

- Acordar criterios verificables antes de implementar.
- Revisar rama y estado de Git.
- Secretos fuera del repositorio.
- No exponer entidades JPA directamente.
- No aceptar precio, total, tenant o nombre de base del cliente como autoridad.
- Las migraciones aplicadas son inmutables.
- No hacer push, merge, rebase ni borrar ramas sin autorización del Product Owner.

## Commits sugeridos

```text
chore: initialize development environment
feat: add product category management
fix: validate stock before confirming order
test: add tenant isolation integration tests
docs: explain backend folder structure
```

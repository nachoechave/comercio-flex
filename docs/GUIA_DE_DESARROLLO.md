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
$env:TENANT_A_DB_PASSWORD = "<MYSQL_APP_PASSWORD de infra/.env>"
$env:TENANT_B_DB_PASSWORD = "<MYSQL_APP_PASSWORD de infra/.env>"
$env:SESSION_TIMEOUT = "30m"
.\mvnw.cmd spring-boot:run
```

El perfil `local` migra la base de control y las dos bases tenant conocidas. La
aplicación queda en `http://localhost:8080`.

Verificar:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Para probar CORE-01, la base `comercio_flex_control` debe tener comercios activos
con `database_key` `tenant-a` y `tenant-b`, y cada base tenant debe tener una fila
en `store_settings`. Luego:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/stores/tienda-a/settings
Invoke-RestMethod http://localhost:8080/api/v1/stores/tienda-b/settings

try {
    Invoke-WebRequest http://localhost:8080/api/v1/stores/no-existe/settings
} catch {
    [int]$_.Exception.Response.StatusCode
}
```

Las dos primeras respuestas deben mostrar comercios diferentes y la última debe
mostrar `404`. Este intento no debe cambiar la conexión:

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/stores/tienda-a/settings `
    -Headers @{ "X-Database-Key" = "tenant-b" }
```

Debe continuar respondiendo con los datos de `tienda-a`.

### Crear un `OWNER` sólo para desarrollo local

El alta local está desactivada por defecto. Para crearla de forma idempotente,
definir antes de iniciar Spring:

```powershell
$env:LOCAL_OWNER_ENABLED = "true"
$env:LOCAL_OWNER_EMAIL = "owner.local@example.test"
$env:LOCAL_OWNER_PASSWORD = "<contraseña local de al menos 12 caracteres>"
$env:LOCAL_OWNER_DISPLAY_NAME = "Owner local"
$env:LOCAL_OWNER_STORE_SLUG = "tienda-a"
```

La contraseña no debe escribirse en un archivo versionado. Si el correo ya
existe, el proceso no reemplaza silenciosamente su contraseña.

## 3. Ejecutar el frontend

En otra terminal, desde `frontend/`:

```powershell
npm.cmd install
npm.cmd start
```

Abrir `http://localhost:4200`. La página debe mostrar “Disponible”. El proxy
redirige `/actuator` y `/api` al backend y evita fijar una URL de desarrollo
dentro de los servicios Angular. El login administrativo está en `/admin/login`.

### Probar categorías manualmente

1. Iniciar sesión con un `OWNER` local.
2. Abrir `/tiendas/tienda-a/admin/categorias`.
3. Crear `Remeras de Niño` y comprobar que el slug sea `remeras-de-nino`.
4. Renombrarla y comprobar que el slug no cambie.
5. Desactivarla, verificar el estado `Inactiva` y volver a activarla.
6. Intentar crear un nombre equivalente y comprobar el error de duplicado.
7. Cambiar a otro comercio autorizado y verificar que sus categorías sean
   independientes.
8. Con una membresía `STAFF`, comprobar que el listado sea visible pero no haya
   acciones de alta, edición o cambio de estado.

La API administrativa utilizada por la pantalla está documentada en `API.md`.
Las operaciones de escritura requieren la cookie de sesión y el header CSRF; no
deben probarse copiando credenciales o identificadores de base en la URL.

### Probar productos manualmente

1. Iniciar sesión como `OWNER` o `ADMIN` y asegurar que exista una categoría activa.
2. Abrir `/tiendas/tienda-a/admin/productos`.
3. Crear un producto con nombre, categoría y al menos una variante con SKU y precio.
4. Confirmar que el producto comience en borrador y que el precio conserve dos decimales.
5. Editar nombre, descripción, SKU o precio y comprobar que los cambios persistan.
6. Publicar, intentar desactivar la última variante activa y comprobar el rechazo.
7. Volver a borrador, desactivar/restaurar una variante y archivar/restaurar el producto.
8. Buscar por nombre o SKU y probar filtros y paginación.
9. Cambiar de tienda y verificar que no aparezcan datos del comercio anterior.
10. Con `STAFF`, verificar lectura sin botones de modificación.

### Probar inventario manualmente

1. Iniciar sesión como `OWNER`, `ADMIN` o `STAFF`.
2. Abrir `/tiendas/tienda-a/admin/inventario`.
3. Elegir una variante sin movimientos y comprobar el saldo `"0.000"`.
4. Registrar una entrada de 10 unidades con motivo recepción.
5. Registrar una salida de 3 y comprobar saldo 7 y dos movimientos.
6. Intentar una salida de 8; debe informar stock insuficiente sin agregar
   movimiento.
7. Abrir el historial y comprobar antes, variación, después, actor y fecha.
8. Ajustar una variante inactiva y verificar la advertencia de estado.
9. Cambiar a otro comercio y comprobar que lista, detalle y formulario se limpien.
10. Ante un timeout simulado, verificar el historial antes de repetir; la
    aplicación no reintenta automáticamente.

### Probar el catálogo público manualmente

1. Publicar al menos dos productos en `tienda-a`, con categorías y variantes
   activas; dejar uno con stock y otro agotado.
2. Abrir `/tiendas/tienda-a` sin iniciar sesión.
3. Comprobar nombre del comercio, moneda, categorías, orden alfabético y ambos
   estados de disponibilidad.
4. Buscar por nombre, filtrar por categoría y navegar de página; recargar la URL
   y verificar que filtros y página se conservan.
5. Abrir `/tiendas/tienda-a/productos/{productSlug}` y revisar descripción,
   variantes, precios y placeholder.
6. Confirmar que el navegador no recibe SKU, cantidad exacta, versiones ni datos
   de conexión.
7. Despublicar el producto o desactivar su categoría y verificar que el detalle
   responda como no encontrado.
8. Abrir `tienda-b` y comprobar que no aparezcan datos de A.
9. Probar a 320, 390, 768 y 1280 píxeles, además de navegación por teclado.
10. Confirmar que `/tiendas/tienda-a/admin` continúa exigiendo autenticación.

### Probar el carrito local manualmente

1. Abrir el detalle de un producto publicado con una variante disponible.
2. Comprobar que “Agregar al carrito” permanezca deshabilitado hasta elegir una
   variante y que las opciones agotadas no puedan seleccionarse.
3. Elegir variante, indicar una cantidad entre 1 y 99 y agregarla.
4. Verificar que el contador de cabecera cambie y sobreviva una recarga.
5. Abrir `/tiendas/tienda-a/carrito`, cambiar la cantidad y revisar subtotal.
6. Modificar el precio desde administración y volver a abrir el carrito: debe
   actualizar el valor e informar el cambio.
7. Agotar o retirar la variante: la línea debe conservarse marcada y quedar fuera
   del subtotal disponible.
8. Simular un fallo del backend y comprobar que el snapshot no se borre y exista
   una acción para reintentar.
9. Abrir `tienda-b`, agregar otro producto y comprobar que ambos carritos y
   contadores sean independientes.
10. Quitar una línea, probar la confirmación de vaciado y recorrer todo con
    teclado a 320, 390, 768 y 1280 píxeles.

Para reiniciar sólo los datos de esta historia desde DevTools puede eliminarse
una clave `comercio-flex:cart:v1:{storeSlug}`. No se debe ejecutar
`localStorage.clear()` en una aplicación real porque podría borrar datos de otros
sistemas alojados en el mismo origen.

## Pruebas

```powershell
Set-Location .\backend
.\mvnw.cmd test

Set-Location ..\frontend
npm.cmd test -- --watch=false
npm.cmd run build
```

Las pruebas backend necesitan Docker porque usan MySQL real mediante Testcontainers.
La prueba de routing levanta control, tenant A y tenant B en contenedores separados.

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

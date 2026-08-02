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

### Probar el checkout invitado manualmente

1. Preparar un producto publicado con variante activa y stock físico.
2. Agregar una cantidad al carrito y confirmar que todas las líneas queden
   disponibles.
3. Pulsar `Continuar`, completar nombre y teléfono; probar también correo y
   observaciones opcionales.
4. Confirmar y verificar la ruta
   `/tiendas/{slug}/pedidos/{uuid}?token=...`.

5. Comprobar número, items, subtotal, retiro, contacto enmascarado y vencimiento.
6. Recargar el enlace: debe recuperar el pedido sin exponer teléfono, correo
   completos ni SKU.
7. Reservar todo el saldo y comprobar que el catálogo marque la variante como no
   disponible aunque el balance físico no cambie.
8. Consultar el UUID con otro token y desde otro `storeSlug`: ambos deben
   responder como no encontrados.
9. Simular un error de red y reintentar sin cambiar el formulario: debe usar la
   misma `Idempotency-Key` y no duplicar el pedido.
10. Probar teclado y anchos de 320, 390, 768 y 1280 píxeles.

Las reservas vencidas dejan de reducir disponibilidad inmediatamente. Su estado
se materializa al consultar el pedido o abrir el listado administrativo; una
limpieza programada independiente queda como evolución operativa.

### Probar la operación administrativa de pedidos

1. Crear un pedido invitado y conservar su número.
2. Iniciar sesión como OWNER, ADMIN o STAFF.
3. Abrir `/tiendas/tienda-a/admin/pedidos`.
4. Buscar el número y abrir el detalle.
5. Confirmarlo y verificar que aparezcan el actor y la nota en el historial.
6. Revisar inventario: el balance debe disminuir y existir un movimiento
   `ORDER_CONFIRMED`.
7. Marcarlo listo y luego cancelarlo.
8. Confirmar que el balance vuelva al valor anterior y exista
   `ORDER_CANCELLED`.
9. En la UI, confirmar que un estado terminal ya no ofrece acciones.
10. Mediante la API, reenviar exactamente la misma transición con la misma
    `Idempotency-Key`: debe responder sin duplicar historial ni stock. Reutilizar
    esa clave con otro estado o nota debe responder `409`.
11. Verificar que rechazo y vencimiento liberan reservas sin cambiar el balance.
12. Entrar con un usuario sin membresía en el comercio: la API debe responder
    `403`; consultar el UUID desde otro tenant no debe revelar el pedido.
13. Aplicar filtros por estado y número; con más de 20 registros, verificar las
    páginas anterior y siguiente.

### Probar la base interna de pagos

PAY-01A no tiene botón, endpoint ni credenciales externas. Su recorrido manual
consiste en ejecutar los escenarios identificados contra MySQL real:

```powershell
Set-Location .\backend
.\mvnw.cmd "-Dtest=GuestOrderIntegrationTests" test
```

La prueba crea pedidos ficticios y verifica:

1. `APPROVED` confirma el pedido, consume la reserva y descuenta stock una vez.
2. El replay devuelve el mismo intento sin duplicar transacción ni movimiento.
3. `PENDING` y `REJECTED` no cambian pedido ni balance físico.
4. Una aprobación tardía queda `REQUIRES_REVIEW` y no descuenta stock.
5. Dos hilos concurrentes producen un solo efecto financiero y de inventario.
6. El UUID de un pedido de tenant A no puede iniciar un pago desde tenant B.
7. Un pedido cobrado no puede cancelarse mientras no exista reembolso.
8. Dos pedidos que compiten por una clave idempotente o un identificador externo
   reciben un conflicto controlado; no queda una excepción JDBC expuesta.
9. Un importe o moneda externos inconsistentes dejan el intento en revisión sin
   transacción, movimiento ni descuento parcial.

Las pruebas criptográficas no usan claves reales:

```powershell
.\mvnw.cmd "-Dtest=AesGcmCredentialCipherTests,FakePaymentGatewayTests" test
```

En PAY-01B las claves se leerán desde variables de entorno y no tendrán valores
por defecto. Nunca debe agregarse un token real para probar PAY-01A.

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

## Probar la conexión Mercado Pago de PAY-01B

La suite automática usa dobles HTTP y no necesita credenciales reales:

```powershell
Set-Location .\backend
.\mvnw.cmd "-Dtest=MerchantPaymentConnectionServiceTests,MercadoPagoOAuthClientAdapterTests" test
.\mvnw.cmd "-Dtest=ComercioFlexBackendApplicationTests" test

Set-Location ..\frontend
npm.cmd test -- --watch=false
npm.cmd run build
```

Para una prueba manual se usan exclusivamente credenciales de prueba. Generá una
clave local de 32 bytes codificada en Base64 y exportala como
`PAYMENT_TOKEN_ENCRYPTION_KEY_V1`; no la copies al repositorio. Configurá además
`PAYMENTS_MERCADO_PAGO_ENABLED=true`, `PAYMENT_MODE=TEST`, `MP_CLIENT_ID`,
`MP_CLIENT_SECRET` y el callback registrado en `MP_OAUTH_REDIRECT_URI`.

Luego iniciá sesión como OWNER, abrí
`/tiendas/{storeSlug}/admin/configuracion/pagos`, conectá la cuenta y comprobá el
texto `Conectada a: {nickname}`. Un ADMIN o STAFF no debe ver el enlace ni poder
usar la API. No pruebes producción ni credenciales reales en esta entrega.

## Preparar y probar PAY-01C

> PAY-01C quedó validado el 2026-08-01 con regresión automática y recorrido
> integrado de Checkout Pro TEST. Esta guía se conserva para repetir la prueba.

### Configuración segura

- Mantener `PAYMENT_MODE=TEST` durante desarrollo y demostración.
- Habilitar el módulo con `PAYMENTS_CHECKOUT_PRO_ENABLED=true` y configurar
  `MP_TEST_ACCESS_TOKEN`, `MP_TEST_SELLER_ACCOUNT_ID`,
  `MP_TEST_DEMO_TENANT_SLUG`, `MP_WEBHOOK_SECRET` y
  `PUBLIC_BACKEND_BASE_URI` mediante variables de entorno.
- `PAYMENT_RETURN_TOKEN_TTL` controla la vigencia del enlace opaco de retorno;
  el valor predeterminado es `24h`.
- Cargar OAuth, access token demo y secreto de webhook mediante variables de
  entorno o el gestor de secretos; nunca en Git, `.env` versionado, capturas o chat.
- La credencial vendedora TEST central sólo puede asociarse al slug/UUID del tenant
  demo configurado. No habilitarla para tiendas adicionales.
- El perfil de producción debe rechazar el token TEST central, secretos vacíos,
  URLs HTTP, `localhost` y una mezcla de secretos TEST/producción.
- Configurar una URL HTTPS pública temporal para webhook y back URLs. Mercado Pago
  no puede alcanzar `localhost`; un túnel sólo se usa durante la prueba controlada.
- Tratar el secreto de firma como distinto del `Client Secret`, access token,
  refresh token y clave AES. Rotar uno no implica rotar los demás.

### Recorrido manual reproducible

1. Levantar MySQL, backend y frontend con PAY-01C habilitado en TEST.
2. Confirmar que el tenant demo tenga conexión utilizable o la excepción TEST
   central explícita, y activar por separado su habilitación comercial.
3. Crear un pedido con reserva vigente y conservar su `lookupToken`.
4. Iniciar el pago una sola vez. Verificar que Angular muestre estado ocupado y
   navegue automáticamente al dominio HTTPS esperado en la misma pestaña.
5. Completar Checkout Pro con comprador de prueba diferente del vendedor.
   Mercado Pago no envía webhooks automáticos para pagos creados con credenciales
   de prueba; usar el simulador oficial de Webhooks para validar la recepción.
6. Confirmar que el retorno muestre estado de procesamiento sin confiar en los
   parámetros visibles y que el polling se detenga al alcanzar resultado terminal.
7. Verificar el recorrido `RECEIVED → PROCESSING → PROCESSED` en la inbox y que el
   pedido aprobado confirme y descuente stock exactamente una vez.
8. Reenviar la misma notificación desde el simulador y comprobar que no duplique
   transacción, historial, reserva consumida ni movimiento de inventario.
9. Probar firma ausente/alterada y timestamp fuera de tolerancia: debe rechazarse
   antes de crear trabajo procesable y no debe llamar a la API de pagos.
10. Simular indisponibilidad transitoria del proveedor: el evento debe pasar a
    `RETRY` con backoff; al agotar el límite debe quedar `DEAD` y generar señal
    operativa, no un bucle infinito.
11. Simular caída después del commit tenant y antes de cerrar el inbox: el replay
    debe finalizar sin repetir el efecto comercial.
12. Desactivar la habilitación comercial manteniendo OAuth conectado: el inicio
    debe fallar cerrado, mientras el estado técnico continúa visible al OWNER.
13. Intentar usar la credencial TEST central desde otro tenant y arrancar esa
    modalidad en producción: ambos casos deben rechazarse sin revelar secretos.
14. Dejar que el polling alcance su límite y comprobar que se detenga, explique la
    demora y permita actualización manual sin crear otra preferencia.

### Observabilidad y revisión

Revisar métricas de recibidos, firmas inválidas, duplicados, reintentos, `DEAD`,
edad de cola y latencia del proveedor. Buscar sentinelas de prueba en logs para
demostrar que no aparecen firma, secret, Authorization, tokens, callback/query
completa, payload remoto ni datos del comprador.

La validación del 2026-08-01 comprobó retorno HTTPS, pago acreditado consultado en
Mercado Pago, procesamiento firmado, confirmación única, consumo único de reserva,
replay idempotente y protección del pago tardío. PAY-01D conserva la ampliación a
escenarios rechazados/pendientes, observabilidad operativa y runbooks.

### Runbook de un webhook `DEAD`

1. Ingresar como `OWNER` al comercio correcto y abrir **Pagos**.
2. Revisar el código sanitizado y la cantidad de intentos. No buscar ni copiar
   tokens, firmas, payloads o datos del comprador en logs.
3. Confirmar que MySQL y la conexión de Mercado Pago estén disponibles. Si el
   código indica una discrepancia de credencial, no reintentar hasta corregirla.
4. Elegir **Reintentar** una sola vez. La acción exige sesión `OWNER`, CSRF y queda
   auditada con actor, fecha e intentos anteriores.
5. Actualizar la lista. El evento debe desaparecer de `DEAD`, pasar por el worker
   y terminar en `PROCESSED` o regresar a `DEAD` de manera acotada.
6. Verificar el pedido y el inventario. Un replay nunca debe crear una segunda
   transacción, confirmación ni salida de stock.
7. Si vuelve a `DEAD`, conservar el UUID público y el código seguro para soporte;
   no realizar actualizaciones SQL manuales ni compartir secretos.

Las fechas del inbox se conservan como instantes UTC. La conexión JDBC de la base
de control fuerza su sesión a UTC y Angular las presenta con la zona IANA de
`store_settings.timezone`. La pantalla muestra el nombre de esa zona para que el
operador no confunda la hora del comercio con la de su dispositivo.

Los avisos de reintento pertenecen a la tarjeta operativa. Un error de conexión
OAuth se muestra por separado y no significa que el reintento del inbox haya
fallado; el estado autoritativo se revisa en esa misma tarjeta.

Micrometer registra contadores internos bajo
`comercio.flex.payment.webhooks` con resultados cerrados: `received`,
`duplicate`, `processed`, `retried`, `dead_exhausted`, `dead_terminal` y
`manual_retry`. La separación permite distinguir reintentos agotados de errores
no recuperables. No usan tenant, pedido, pago, URL ni error libre como etiquetas.
En el MVP el endpoint global de métricas no se expone por HTTP; su conexión a un
colector pertenece a `OPS-01`.

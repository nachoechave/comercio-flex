# 04 — MySQL

## Topología local

Una instancia MySQL 8.4 contiene:

- una base de control;
- una base para el comercio A;
- una base para el comercio B.

Son bases lógicamente separadas. En producción podrán alojarse en servidores
distintos sin cambiar el concepto.

## Usuarios

- El usuario de aplicación puede consultar y modificar datos, pero no cambiar tablas.
- El usuario de migraciones puede ejecutar el DDL requerido por Flyway.
- `root` queda reservado para inicialización y administración local.

## Migraciones

`control/V001` crea el registro de tenants. `tenant/V001` crea la primera tabla de
configuración de tienda. Todas las bases tenant deben ejecutar el mismo conjunto
de migraciones y conservar la misma versión.

Estudiar después: esquema, tabla, clave primaria, índice, restricción, usuario,
privilegio, transacción y migración.

## Datos de identidad y sesión de CORE-02

La base de control también contiene:

- usuarios globales con correo normalizado y hash de contraseña;
- membresías únicas por usuario y comercio;
- rol y estado actual de cada membresía;
- sesiones HTTP y sus atributos mínimos.

Estas tablas no se duplican en cada base tenant. Así, una persona puede pertenecer
a dos comercios sin tener dos contraseñas. Las claves foráneas y restricciones
únicas impiden memberships huérfanas o duplicadas.

Spring Session usa tablas relacionales con vencimiento. El navegador conserva
sólo un identificador opaco; los datos reales permanecen del lado servidor. El
esquema se administra con Flyway igual que el resto de la base de control.
## Aprendizaje del cierre: metadatos, privilegios y recuperación

`product_images` guarda metadatos y claves, no los bytes. Una restricción única en
`product_id` expresa “una imagen principal” incluso ante concurrencia. Las FK y
checks protegen MIME, tamaños, texto alternativo y dimensiones.

V013 agrega contacto/retiro/tema como nullable para no inventar datos al migrar
tiendas existentes; la aplicación exige completarlos al guardar. Es un ejemplo de
migración compatible hacia adelante.

El usuario de runtime necesita DML, no DDL. Separar control y tenants limita el
impacto de una credencial filtrada. Un backup sólo se vuelve evidencia de
recuperación al restaurarlo en otra base y validar Flyway, tenants, pedidos y
stock. La copia de MySQL debe coordinarse con la retención del bucket de imágenes.

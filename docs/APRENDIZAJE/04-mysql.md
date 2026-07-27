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

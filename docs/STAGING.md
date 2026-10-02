# Entorno de staging

Comercio Flex usa dos lineas de despliegue:

- `develop` -> `https://staging.comercioflex.com.ar` -> pruebas.
- `main` -> `https://comercioflex.com.ar` -> produccion.

Las ramas `feature/*` deben abrir PR contra `develop`. Solo despues de validar
la funcionalidad en staging se abre un PR de `develop` hacia `main`.

## Aislamiento obligatorio

Staging no puede compartir con produccion:

- base de control;
- bases tenant;
- prefijo de bases tenant;
- credenciales runtime/migracion/provisioning;
- buckets de media y comprobantes;
- credenciales/tokens productivos de Mercado Pago;
- secretos de webhooks.

Los emails permanecen deshabilitados por defecto y Mercado Pago usa TEST.

## Easypanel

Crear un segundo servicio desde este repositorio:

1. Branch: `develop`.
2. Build: Dockerfile de la raiz.
3. Dominio: `staging.comercioflex.com.ar`.
4. Health check: `/actuator/health/readiness`.
5. Variables: partir de `infra/staging.env.example` y completar secretos en
   Easypanel, nunca en Git.
6. Crear las bases/usuarios de staging antes del primer arranque.
7. Verificar que ninguna variable apunte a recursos de produccion.
8. Hacer deploy y ejecutar smoke tests.

El servicio productivo existente debe continuar siguiendo `main`.

## Cloudflare

Crear el DNS de `staging.comercioflex.com.ar` hacia el mismo edge/servidor que
usa Easypanel, y asociar ese hostname exclusivamente al servicio de staging.
Mantener HTTPS activo.

## Flujo diario

```text
feature/*
   |
   v
develop -> staging.comercioflex.com.ar -> validar
   |
   v
main    -> comercioflex.com.ar         -> produccion
```

Nunca probar migraciones, checkout, promociones, stock o configuracion de pagos
directamente sobre `main`.

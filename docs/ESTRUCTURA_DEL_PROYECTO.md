# Estructura del proyecto

> Actualizado durante el Sprint 1 el 2026-07-27.

```text
comercio-flex/
├── backend/                # API Spring Boot y migraciones
├── frontend/               # SPA Angular
├── infra/                  # MySQL local con Docker Compose
├── docs/                   # Producto, arquitectura y aprendizaje
├── .gitignore              # Archivos que Git no debe versionar
├── .gitattributes          # Finales de línea consistentes
└── README.md               # Entrada al repositorio
```

## Frontend

```text
frontend/
├── public/                         # Archivos estáticos públicos
├── proxy.conf.json                 # Proxy local Angular → Spring Boot
└── src/app/
    ├── core/
    │   └── health/                 # Cliente del health check backend
    ├── layouts/
    │   ├── storefront-layout/      # Marco de la tienda pública
    │   └── admin-layout/           # Marco del panel administrativo
    ├── features/
    │   ├── storefront/home/        # Página pública inicial
    │   └── admin/dashboard/        # Placeholder administrativo
    ├── shared/ui/status-pill/      # UI reutilizable sin negocio
    ├── app.config.ts               # Proveedores globales
    └── app.routes.ts               # Rutas lazy
```

Reglas de dependencia:

- `features` puede usar `core` y `shared`.
- `shared` no conoce features ni reglas de negocio.
- Una feature no importa detalles internos de otra.
- Los guards futuros ayudan a la navegación; el backend aplica la seguridad real.

## Backend

```text
backend/src/main/
├── java/com/comercioflex/
│   ├── config/                     # Seguridad, CORS y migración tenant
│   ├── tenant/                     # Límite del módulo multiempresa
│   └── shared/                     # Tipos transversales mínimos
└── resources/
    ├── application.yml             # Configuración común sin secretos
    ├── application-local.yml       # Comportamiento del perfil local
    └── db/migration/
        ├── control/                 # Esquema de la base central
        └── tenant/                  # Esquema idéntico para cada comercio
```

Los módulos `identity`, `catalog`, `inventory`, `customer`, `order`, `delivery`,
`payment` y `reporting` se crearán al comenzar sus historias. No se agregan
carpetas vacías sólo para simular avance.

Dentro de un módulo de negocio se utilizarán, cuando hagan falta:

```text
api → application → domain
          ↑
infrastructure
```

`api` no accede directamente a JPA y `domain` no depende de HTTP.

## Infraestructura

```text
infra/
├── compose.yaml
├── .env.example
├── README.md
└── mysql/init/
    └── 01-create-development-databases.sh
```

Compose crea una instancia MySQL con tres bases lógicas:

- `comercio_flex_control`;
- `comercio_flex_tenant_a`;
- `comercio_flex_tenant_b`.

El volumen local no es un backup. Los secretos reales nunca se versionan.

## Flujo actual del health check

```text
Navegador
→ StorefrontHome de Angular
→ HealthService
→ proxy local /actuator
→ Spring Security permite health
→ Spring Boot Actuator
→ respuesta {"status":"UP"}
→ estado accesible en la pantalla
```

## Flujo futuro de negocio

```text
Usuario
→ página Angular
→ data-access Angular
→ API REST
→ Controller
→ caso de uso
→ dominio
→ Repository
→ base MySQL del comercio
→ DTO de respuesta
→ Angular
→ Usuario
```

La selección de base será:

```text
Path o sesión
→ Tenant Resolver
→ base de control
→ Connection Router
→ base registrada del comercio
```

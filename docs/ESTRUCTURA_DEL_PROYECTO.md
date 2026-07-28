# Estructura del proyecto

> Actualizado durante CORE-02 el 2026-07-28. Las carpetas de identidad descritas a
> continuación representan la estructura aprobada; su implementación y revisión
> están en curso.

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
    │   ├── auth/                   # Sesión global, CSRF, guards e interceptor
    │   └── health/                 # Cliente del health check backend
    ├── layouts/
    │   ├── storefront-layout/      # Marco de la tienda pública
    │   └── admin-layout/           # Marco del panel administrativo
    ├── features/
    │   ├── auth/                   # Pantalla de login global
    │   ├── storefront/home/        # Página pública inicial
    │   └── admin/
    │       ├── dashboard/          # Entrada administrativa protegida
    │       └── store-selector/     # Selector para usuarios con varias membresías
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
│   ├── config/                     # Seguridad, datasources y migraciones
│   ├── identity/
│   │   ├── api/                    # Contrato HTTP de sesión, login y logout
│   │   ├── application/            # Autenticación y autorización de casos de uso
│   │   ├── domain/                 # Usuario, membresía, roles y estados
│   │   └── infrastructure/
│   │       └── control/            # Persistencia de identidad en la base central
│   ├── tenant/
│   │   ├── api/                    # Endpoint, filtro y errores HTTP
│   │   ├── application/            # Resolución, contexto y caso de consulta
│   │   └── infrastructure/
│   │       ├── control/            # Entity/repository de la base central
│   │       └── routing/            # Pools y selección de conexión tenant
│   └── shared/                     # Tipos transversales mínimos
└── resources/
    ├── application.yml             # Configuración común sin secretos
    ├── application-local.yml       # Comportamiento del perfil local
    └── db/migration/
        ├── control/                 # Esquema de la base central
        └── tenant/                  # Esquema idéntico para cada comercio
```

Los módulos `catalog`, `inventory`, `customer`, `order`, `delivery`, `payment` y
`reporting` se crearán al comenzar sus historias. `identity` se incorpora en
CORE-02. No se agregan carpetas vacías sólo para simular avance.

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

## Flujo implementado de resolución multiempresa

```text
Usuario
→ GET /api/v1/stores/{slug}/settings
→ TenantResolutionFilter
→ TenantResolver
→ TenantRepository
→ base de control
→ database_key lógica
→ TenantContext
→ TenantRoutingDataSource
→ base MySQL del comercio
→ StoreSettingsResponse
→ Usuario
```

Al finalizar, `TenantContext.Scope.close()` elimina la clave del hilo aunque haya
una excepción. `api` depende de `application`; `application` usa el puerto
`TenantConnectionCatalog`; `infrastructure` implementa ese puerto. La capa de
aplicación no conoce URLs JDBC ni contraseñas.

## Flujo futuro completo

```text
Componente Angular
→ servicio Angular
→ API REST
→ Controller
→ caso de uso
→ dominio
→ Repository
→ base MySQL del comercio resuelto
→ DTO
→ Angular
```

## Flujo de autenticación y autorización de CORE-02

```text
LoginComponent
→ AuthService obtiene XSRF-TOKEN
→ POST /api/v1/auth/login con X-XSRF-TOKEN
→ Spring Security verifica credenciales
→ PlatformUserRepository consulta control DB
→ Spring Session guarda la sesión en control DB
→ Angular consulta /api/v1/auth/session
→ AuthService conserva usuario y memberships en memoria
→ selector navega al slug elegido
→ guard verifica el estado de navegación
→ backend valida nuevamente membership y rol
→ recién entonces TenantRoutingDataSource abre la base del comercio
```

Dependencias permitidas:

- `identity` puede consultar `tenant` en la base de control para describir y
  autorizar membresías, pero no accede a datos de negocio tenant.
- `tenant` no conoce componentes Angular ni acepta roles provenientes del cliente.
- `core/auth` puede ser usado por features y layouts; no depende de una pantalla
  administrativa concreta.
- `features/auth` usa `core/auth` y `shared`, pero ninguna feature de negocio debe
  importar detalles internos de la pantalla de login.

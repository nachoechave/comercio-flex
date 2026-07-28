# 02 — Angular

## Problema resuelto en el Sprint 1

Angular necesita una estructura que permita desarrollar tienda y administración
sin cargar todo el código al abrir la página. Las rutas lazy descargan cada área
cuando se visita.

## Conceptos

- **Componente standalone:** declara directamente lo que usa; no necesita NgModule.
- **Ruta lazy:** carga una pantalla bajo demanda.
- **Signal:** conserva estado reactivo, como `loading`, `up` o `error`.
- **Servicio:** encapsula la llamada HTTP para que la página no conozca detalles.
- **Proxy local:** reenvía una ruta de Angular al backend durante desarrollo.
- **Zoneless:** Angular actualiza la interfaz mediante señales y mecanismos
  explícitos, sin depender de Zone.js.

## Flujo implementado

```text
StorefrontHome
→ HealthService
→ HttpClient
→ /actuator/health
→ signal backendState
→ StatusPill
```

Estudiar después: componentes, templates, Signals, inyección de dependencias,
HttpClient, routing y pruebas con `HttpTestingController`.

## Diseño aprobado para CORE-02

Angular no guarda la cookie de sesión: el navegador la administra y no permite que
JavaScript la lea porque es `HttpOnly`. La aplicación mantiene en memoria sólo el
resultado de `GET /api/v1/auth/session`.

```text
arranque
→ obtener cookie XSRF-TOKEN
→ consultar sesión
→ mostrar login o usuario
→ una membership: navegar al comercio
→ varias memberships: mostrar selector
→ guard evita una navegación incoherente
```

El interceptor XSRF de `HttpClient` copia el token a `X-XSRF-TOKEN` en solicitudes
que modifican datos. El guard mejora la experiencia, pero no es una barrera de
seguridad: una persona puede modificar JavaScript en su navegador y por eso el
backend siempre vuelve a autorizar.

Conceptos para estudiar: cookies, credenciales HTTP, XSRF, interceptor, guard,
estado de sesión y rutas protegidas.

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

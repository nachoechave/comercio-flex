# 01 — Introducción al sistema

Comercio Flex se aprenderá construyendo flujos verticales pequeños. Un flujo
vertical atraviesa interfaz, API y base de datos, por ejemplo “crear categoría”.

```text
Formulario Angular
→ DTO HTTP
→ Controller
→ caso de uso
→ Repository
→ MySQL
→ DTO de respuesta
→ pantalla actualizada
```

El frontend no se conecta directamente a MySQL. Spring Boot protege las reglas,
la autorización y el tenant. La base conserva datos y restricciones. Cada historia
explicará problema, conceptos, archivos, alternativas, prueba manual, comandos,
errores comunes y temas para estudiar.

## Primeros conceptos

Leer en `GLOSARIO.md`: API REST, DTO, Controller, Service, Repository, Entity,
Dependency Injection, migración y multi-tenant.

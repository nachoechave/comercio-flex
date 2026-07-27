# Guía y alternativas de despliegue

> Estado: comparación inicial; no autoriza desplegar ni contratar servicios.
> Verificado el 2026-07-23. Los precios deben revisarse antes de contratar.

## Recomendación por etapa

| Etapa | Propuesta | Motivo |
|---|---|---|
| Desarrollo | Angular/Spring en host + MySQL en Docker Compose | Hot reload y aprendizaje |
| Demostración | Cloudflare Pages + Railway | Puesta en línea rápida, sin datos reales |
| Primer cliente | Cloudflare Pages + App Platform + Managed MySQL de DigitalOcean | Base administrada y costo más predecible |
| Crecimiento | Contenedores + AWS RDS MySQL o equivalente | Backups, alta disponibilidad y escala |

## Alternativas

Railway simplifica API y MySQL, pero su plantilla MySQL es administrada por el
equipo usuario: mantenimiento, observabilidad y restore siguen siendo nuestra
responsabilidad. Es adecuada para demo; para datos reales exige aceptar ese riesgo.

DigitalOcean separa App Platform y Managed MySQL. Tiene mayor costo base, pero
reduce trabajo operativo de la base. Render es útil para demos, aunque su oferta
administrada se orienta a Postgres; operar MySQL allí requiere disco y mantenimiento
propios. Los planes gratuitos con suspensión no son apropiados para un cliente real.

Fuentes:
[Cloudflare Pages](https://developers.cloudflare.com/pages/platform/limits/),
[Railway MySQL](https://docs.railway.com/databases/mysql),
[Railway pricing](https://docs.railway.com/pricing),
[DigitalOcean databases](https://www.digitalocean.com/pricing/managed-databases),
[Render free](https://render.com/docs/free),
[AWS RDS MySQL](https://aws.amazon.com/rds/mysql/pricing/).

## Requisitos antes del piloto

- HTTPS, variables de entorno y credenciales separadas.
- Logs sin secretos y health checks.
- Backup automático y restauración probada.
- Migraciones controladas y plan de reversión.
- Monitoreo, dominio, límites de costo y responsable de incidentes.

# Documentacion tecnica de Wattimizer

Este directorio centraliza la documentacion que acompana al proyecto DAW **Wattimizer**. La guia de despliegue ya existia en `docs/deployment/`; el nuevo bloque `memoria-tecnica/` esta pensado para usarse directamente como anexo de la memoria final.

## Indice

| Documento | Contenido |
|---|---|
| [Memoria tecnica del proyecto](memoria-tecnica/memoria-tecnica-wattimizer.md) | Introduccion, analisis funcional, diseno tecnico, implementacion, pruebas, despliegue, conclusiones y anexos tecnicos. |
| [Despliegue en Hetzner](deployment/hetzner-production.md) | Procedimiento real de produccion con Docker Compose, Nginx, Certbot, OAuth2, Mosquitto y TimescaleDB. |

## Anexos tecnicos incluidos

La memoria tecnica contiene anexos especificos sobre:

- Controladores REST de Spring Boot, endpoints, parametros y DTOs.
- Componentes y servicios Angular, con foco en Signals, RxJS y `@ngrx/signals`.
- Ingesta asincrona de telemetria con Spring Integration MQTT.
- Hypertable `readings` de TimescaleDB, scripts SQL y consultas analiticas reales.

Los diagramas se escriben en Mermaid dentro de Markdown para que sigan siendo versionables en Git y renderizables en GitHub o en editores compatibles.

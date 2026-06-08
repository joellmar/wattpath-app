# Documentación técnica del proyecto Wattimizer

Esta carpeta recoge los anexos técnicos preparados para la memoria final del proyecto DAW. La documentación se ha redactado a partir del código del repositorio y separa cada área para que pueda reutilizarse directamente en la entrega.

## Índice de anexos

1. [Anexo 01. Backend Spring Boot y API REST](./anexos/anexo-01-backend-spring-boot.md)
2. [Anexo 02. Frontend Angular, RxJS y NgRx Signals](./anexos/anexo-02-frontend-angular.md)
3. [Anexo 03. Ingesta MQTT con Spring Integration](./anexos/anexo-03-mqtt-spring-integration.md)
4. [Anexo 04. Persistencia analítica con PostgreSQL y TimescaleDB](./anexos/anexo-04-timescaledb-postgresql.md)

## Criterio seguido

Los anexos no describen una arquitectura ideal, sino la implementación real:

- controladores, DTOs, servicios, repositorios y entidades presentes en `backend/src/main/java`;
- componentes, servicios, interfaces y stores presentes en `frontend/src/app`;
- configuración de Mosquitto, WebSocket/STOMP y despliegue definida en `docker-compose.yml`;
- modelo de datos inferido de JPA y consultas analíticas existentes en los repositorios y servicios.

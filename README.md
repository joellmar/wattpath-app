# Wattimizer App

**Plataforma B2B de inteligencia financiera energetica.** Proyecto desarrollado
para el modulo de Proyecto Intermodular del ciclo de Desarrollo de Aplicaciones
Web (DAW).

## Descripcion del proyecto

Wattimizer resuelve el problema de la opacidad energetica en las pymes. A traves
de la monitorizacion IoT con enchufes inteligentes, la plataforma traduce consumo
electrico en gasto economico real, aplica tarifas TD, alerta de picos de potencia
y detecta consumos fantasma.

## Stack tecnologico

- **Frontend:** Angular 21.x y TypeScript.
- **Backend:** Spring Boot 4.0.5 y Java 26.
- **Base de datos:** PostgreSQL con TimescaleDB para series temporales.
- **IoT / mensajeria:** MQTT con Eclipse Mosquitto y Spring Integration.
- **Tiempo real:** STOMP sobre WebSocket.

## Documentacion tecnica para la memoria DAW

La documentacion academica del proyecto esta versionada en Markdown para poder
reutilizarse como anexos de la memoria final:

- [Memoria tecnica principal](docs/memoria/memoria-final-daw.md)
- [Anexo A. Backend REST con Spring Boot](docs/memoria/anexo-a-backend-rest.md)
- [Anexo B. Frontend Angular, RxJS y NgRx Signals](docs/memoria/anexo-b-frontend-angular.md)
- [Anexo C. Ingesta asincrona de telemetria con MQTT](docs/memoria/anexo-c-telemetria-mqtt.md)
- [Anexo D. TimescaleDB y analitica energetica](docs/memoria/anexo-d-timescaledb-analitica.md)
- [Vista visual de arquitectura y flujos](docs-canvas/arquitectura-wattimizer.md)

## Estado del proyecto

- [x] Fase 1: Analisis funcional.
- [x] Fase 2: Diseno tecnico.
- [x] Fase 3: Desarrollo backend.
- [x] Fase 4: Desarrollo frontend.
- [x] Fase 5: Despliegue.

## Autor

- **Jose Luis Lopez Martos** - Desarrollador Full-Stack.

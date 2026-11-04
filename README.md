# ⚡ Wattimizer App

**Plataforma B2B de inteligencia financiera energética.**
Proyecto desarrollado para el módulo de Proyecto Intermodular del ciclo de Desarrollo de Aplicaciones Web (DAW).

## 📖 Descripción del Proyecto
Wattimizer resuelve el problema de la opacidad energética en las pymes. A través de la monitorización IoT (enchufes inteligentes), la plataforma traduce el consumo eléctrico (kWh) en gasto económico real (€) aplicando las tarifas actuales (ej. 3.0TD), alertando de picos de potencia y consumos fantasma.

## 🛠️ Stack Tecnológico
* **Frontend:** Angular 21.0.0 (TypeScript)
* **Backend:** Spring Boot 4.0.5 (Java)
* **Base de Datos:** PostgreSQL + TimescaleDB (Series Temporales)
* **IoT / Mensajería:** MQTT (Eclipse Mosquitto)

## 📋 Estado del Proyecto
* [x] Fase 1: Análisis Funcional (Historias de Usuario y Backlog)
* [x] Fase 2: Diseño Técnico (Arquitectura, E/R, Wireframes)
* [x] Fase 3: Desarrollo Backend
* [x] Fase 4: Desarrollo Frontend
* [x] Fase 5: Despliegue (Deploy)

## 📚 Documentación técnica DAW
La memoria final y los anexos técnicos en español están en [`docs/memoria`](docs/memoria):

* [`00-memoria-final-daw.md`](docs/memoria/00-memoria-final-daw.md): memoria principal siguiendo el índice de clase.
* [`anexo-a-backend-rest.md`](docs/memoria/anexo-a-backend-rest.md): controladores REST, endpoints, seguridad y DTOs.
* [`anexo-b-frontend-angular.md`](docs/memoria/anexo-b-frontend-angular.md): Angular, RxJS, NgRx Signals y componentes.
* [`anexo-c-telemetria-mqtt.md`](docs/memoria/anexo-c-telemetria-mqtt.md): ingesta MQTT, WebSocket STOMP y simuladores.
* [`anexo-d-timescaledb-analitica.md`](docs/memoria/anexo-d-timescaledb-analitica.md): modelo relacional, hypertable y consultas analíticas.

## 👨‍💻 Autor
* **José Luis López Martos** - Desarrollador Full-Stack

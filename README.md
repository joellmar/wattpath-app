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
La memoria final y sus anexos técnicos están en [`docs/memoria/memoria-final-daw.md`](docs/memoria/memoria-final-daw.md).

Anexos disponibles:

* [Controladores REST de Spring Boot](docs/memoria/anexo-a-backend-rest.md)
* [Frontend Angular, RxJS y NgRx Signals](docs/memoria/anexo-b-frontend-angular.md)
* [Ingesta asíncrona de telemetría MQTT](docs/memoria/anexo-c-telemetria-mqtt.md)
* [TimescaleDB, estructura de tablas y analítica](docs/memoria/anexo-d-timescaledb-analitica.md)

## 👨‍💻 Autor
* **José Luis López Martos** - Desarrollador Full-Stack

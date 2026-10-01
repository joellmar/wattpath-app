# ⚡ Wattimizer App

**Plataforma B2B de inteligencia financiera energética.**
Proyecto desarrollado para el módulo de Proyecto Intermodular del ciclo de Desarrollo de Aplicaciones Web (DAW).

## 📖 Descripción del Proyecto
Wattimizer resuelve el problema de la opacidad energética en las pymes. A través de la monitorización IoT (enchufes inteligentes), la plataforma traduce el consumo eléctrico (kWh) en gasto económico real (€) aplicando las tarifas actuales (ej. 3.0TD), alertando de picos de potencia y consumos fantasma.

## 🛠️ Stack Tecnológico
* **Frontend:** Angular 21 (TypeScript), RxJS, NgRx Signals, PrimeNG y Chart.js.
* **Backend:** Spring Boot 4.0.5 (Java 26), Spring Security, JPA, WebSocket STOMP y Spring Integration MQTT.
* **Base de Datos:** PostgreSQL 17 + TimescaleDB para lecturas temporales.
* **IoT / Mensajería:** MQTT con Eclipse Mosquitto y simuladores internos de consumo.
* **Despliegue:** Docker Compose, Nginx, Certbot y GitHub Actions sobre VPS Hetzner.

## 📋 Estado del Proyecto
* [x] Fase 1: Análisis Funcional (Historias de Usuario y Backlog)
* [x] Fase 2: Diseño Técnico (Arquitectura, E/R, Wireframes)
* [x] Fase 3: Desarrollo Backend
* [x] Fase 4: Desarrollo Frontend
* [x] Fase 5: Despliegue (Deploy)

## 📚 Documentación técnica DAW

La memoria técnica y sus anexos están preparados en Markdown para poder usarse directamente como material de entrega del proyecto:

* [Memoria final DAW](docs/memoria/memoria-final-daw.md)
* [Anexo A — Backend REST Spring Boot](docs/memoria/anexo-a-backend-rest.md)
* [Anexo B — Frontend Angular, RxJS y NgRx Signals](docs/memoria/anexo-b-frontend-angular.md)
* [Anexo C — Telemetría MQTT](docs/memoria/anexo-c-telemetria-mqtt.md)
* [Anexo D — TimescaleDB y analítica](docs/memoria/anexo-d-timescaledb-analitica.md)
* [Canvas documental de arquitectura](docs-canvas/arquitectura-wattimizer.md)
* [Guía de despliegue Hetzner](docs/deployment/hetzner-production.md)

## 👨‍💻 Autor
* **José Luis López Martos** - Desarrollador Full-Stack

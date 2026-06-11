# ⚡ Wattimizer App

**Plataforma B2B de inteligencia financiera energética.**
Proyecto desarrollado para el módulo de Proyecto Intermodular del ciclo de Desarrollo de Aplicaciones Web (DAW).

## 📖 Descripción del Proyecto
Wattimizer resuelve el problema de la opacidad energética en las pymes. A través de la monitorización IoT (enchufes inteligentes), la plataforma traduce el consumo eléctrico (kWh) en gasto económico real (€) aplicando las tarifas actuales (ej. 3.0TD), alertando de picos de potencia y consumos fantasma.

## 🛠️ Stack Tecnológico
* **Frontend:** Angular 21.x (TypeScript, RxJS, @ngrx/signals, PrimeNG)
* **Backend:** Spring Boot 4.0.5 (Java)
* **Base de Datos:** PostgreSQL + TimescaleDB (Series Temporales)
* **IoT / Mensajería:** MQTT (Eclipse Mosquitto)

## 📚 Documentación Técnica

La documentación de memoria y anexos técnicos está en:

* [Memoria técnica del proyecto](docs/memoria/README.md)
* [Anexo A - Referencia de API REST](docs/technical/api-reference.md)
* [Anexo B - Frontend Angular, RxJS y NgRx Signals](docs/technical/frontend-angular.md)
* [Anexo C - Ingesta MQTT, WebSocket y telemetría](docs/technical/iot-mqtt-timescaledb.md)
* [Anexo D - Base de datos, TimescaleDB y consultas analíticas](docs/technical/database-analytics.md)
* [Guía de despliegue local en Windows](GUIA_DESPLIEGUE_LOCAL_WINDOWS.md)
* [Guía de despliegue en Hetzner](docs/deployment/hetzner-production.md)

## 📋 Estado del Proyecto
* [x] Fase 1: Análisis Funcional (Historias de Usuario y Backlog)
* [x] Fase 2: Diseño Técnico (Arquitectura, E/R, Wireframes)
* [x] Fase 3: Desarrollo Backend
* [x] Fase 4: Desarrollo Frontend
* [x] Fase 5: Despliegue (Deploy)

## 👨‍💻 Autor
* **José Luis López Martos** - Desarrollador Full-Stack

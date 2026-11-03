# ⚡ Wattimizer App

**Plataforma B2B de inteligencia financiera energética.**
Proyecto desarrollado para el módulo de Proyecto Intermodular del ciclo de Desarrollo de Aplicaciones Web (DAW).

## 📖 Descripción del Proyecto
Wattimizer resuelve el problema de la opacidad energética en las pymes. A través de la monitorización IoT (enchufes inteligentes), la plataforma traduce el consumo eléctrico (kWh) en gasto económico real (€) aplicando las tarifas actuales (ej. 3.0TD), alertando de picos de potencia y consumos fantasma.

## 🛠️ Stack Tecnológico
* **Frontend:** Angular 21 (TypeScript)
* **Backend:** Spring Boot 4.0.5 (Java)
* **Base de Datos:** PostgreSQL + TimescaleDB (Series Temporales)
* **IoT / Mensajería:** MQTT (Eclipse Mosquitto)

## 📋 Estado del Proyecto
* [x] Fase 1: Análisis Funcional (Historias de Usuario y Backlog)
* [x] Fase 2: Diseño Técnico (Arquitectura, E/R, Wireframes)
* [x] Fase 3: Desarrollo Backend
* [x] Fase 4: Desarrollo Frontend
* [x] Fase 5: Despliegue (Deploy)

## 📚 Documentación técnica
La memoria final y los anexos técnicos en español están en [`docs/memoria`](docs/memoria/memoria-final-daw.md). Incluyen la API REST de Spring Boot, Angular con RxJS/NgRx Signals, ingesta MQTT, simuladores y analítica TimescaleDB.

Como apoyo visual, el canvas de arquitectura está disponible en [`docs-canvas/arquitectura-wattimizer.md`](docs-canvas/arquitectura-wattimizer.md).

## 👨‍💻 Autor
* **José Luis López Martos** - Desarrollador Full-Stack

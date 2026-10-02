# Canvas documental: arquitectura de Wattimizer

Este documento resume visualmente la arquitectura para usarlo como apoyo de lectura junto a la memoria DAW. Los detalles completos estan en `docs/memoria/`.

## 1. Vista global

```mermaid
flowchart LR
    Usuario[Usuario empresa] --> Angular[Angular 21]
    Angular --> REST[API REST /api/v1]
    Angular --> WS[WebSocket STOMP /ws-iot]
    Shelly[Enchufe Shelly] --> MQTT[Eclipse Mosquitto]
    MQTT --> Spring[Spring Boot 4]
    Sim[Simuladores IoT] --> Spring
    REST --> Spring
    WS --> Spring
    Spring --> DB[(PostgreSQL + TimescaleDB)]
    Spring --> OAuth[Google/GitHub OAuth2]
```

## 2. Capas de la aplicacion

| Capa | Elementos | Responsabilidad |
|---|---|---|
| Presentacion | Angular, PrimeNG, Chart.js | Formularios, graficas y navegacion. |
| Estado frontend | NgRx Signals, RxJS | Telemetria, tarifas, loading, errores y seleccion de MAC. |
| API | Controladores Spring MVC | Endpoints REST versionados. |
| Dominio | Servicios Spring | Reglas de dispositivos, tarifas, analitica y alertas. |
| Ingesta | Spring Integration MQTT | Entrada asincrona desde Shelly. |
| Tiempo real | STOMP | Lecturas y alertas hacia Angular. |
| Persistencia | JPA + TimescaleDB | Datos relacionales y serie temporal `readings`. |
| Operacion | Docker Compose, Nginx, GitHub Actions | Despliegue y publicacion en VPS. |

## 3. Flujo de telemetria

```mermaid
sequenceDiagram
    participant Shelly
    participant Broker as Mosquitto
    participant Integration as Spring Integration
    participant Service as ReadingService
    participant DB as TimescaleDB
    participant STOMP as TelemetryBroadcaster
    participant UI as Angular Dashboard

    Shelly->>Broker: MQTT events/rpc o status/switch:0
    Broker->>Integration: Mensaje QoS 1
    Integration->>Integration: Ruta por sufijo de topic
    Integration->>Service: DTO EventsRpc o Status
    Service->>DB: INSERT readings
    Service->>STOMP: ReadingResponse
    STOMP->>UI: /topic/readings/{mac}
```

## 4. Flujo de simuladores

```mermaid
flowchart TD
    A[Usuario crea simulador o demo-pack] --> B[DeviceController]
    B --> C[DeviceService]
    C --> D[(devices is_simulated=true)]
    E[IotTelemetrySimulationJob cada 5s] --> F[Busca simuladores]
    F --> G[SimulationProfileRegistry]
    G --> H[SimulatedTelemetryProcessor]
    H --> I[(readings)]
    H --> J[AlertService]
    H --> K[STOMP readings]
```

## 5. Modelo de datos resumido

```mermaid
erDiagram
    users ||--o{ devices : posee
    devices ||--o{ readings : genera
    devices ||--o{ alerts : provoca
    users ||--o{ alerts : recibe
    users ||--o| tariffs : tiene
    tariffs ||--|{ periods : precios
    tariffs ||--|{ tariff_contracted_powers : potencias
```

## 6. Contratos REST principales

| Modulo | Endpoints clave |
|---|---|
| Auth | `POST /api/v1/auth/login`, `POST /api/v1/auth/register`, `POST /api/v1/auth/oauth/exchange` |
| Devices | `GET /api/v1/devices`, `POST /api/v1/devices/claim`, `POST /api/v1/devices/simulated`, `POST /api/v1/devices/simulated/demo-pack` |
| Readings | `GET /api/v1/readings/device/{mac}/recent`, `GET /api/v1/readings/latest/{mac}` |
| Analytics | `GET /api/v1/analytics/cost`, `GET /api/v1/analytics/ghost-consumption` |
| Tariffs | `GET /api/v1/tariffs`, `POST /api/v1/tariffs`, `POST /api/v1/users/me/tariff` |
| Alerts | `GET /api/v1/alerts`, `DELETE /api/v1/alerts/{id}` |

## 7. Secuencia de calculo economico

```mermaid
flowchart LR
    A[Lecturas ordenadas por time] --> B[Delta positivo de energy_total_kwh]
    B --> C[CalendarResolverService]
    C --> D[period_code]
    D --> E[Period.price_kwh]
    E --> F[delta kWh * precio]
    F --> G[totalCostEur]
```

## 8. Decisiones importantes

- La telemetria no entra por REST, sino por MQTT o por el job de simulacion.
- Angular no hace polling continuo para la grafica; escucha STOMP despues de cargar un historico inicial.
- La tarifa privada se gestiona con `/api/v1/users/me/tariff`, sin `userId`, para evitar IDOR.
- TimescaleDB se usa para particionar `readings`; la analitica actual se calcula en Java.
- La simulacion esta activada por configuracion para facilitar demostraciones sin hardware.

## 9. Referencias internas

- Memoria principal: `docs/memoria/memoria-final-daw.md`
- Backend REST: `docs/memoria/anexo-a-backend-rest.md`
- Frontend Angular: `docs/memoria/anexo-b-frontend-angular.md`
- MQTT: `docs/memoria/anexo-c-telemetria-mqtt.md`
- TimescaleDB: `docs/memoria/anexo-d-timescaledb-analitica.md`

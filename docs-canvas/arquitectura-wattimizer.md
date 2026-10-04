# Canvas documental — Arquitectura Wattimizer

Este documento complementa la memoria con diagramas Mermaid. No sustituye los anexos técnicos; sirve para explicar el sistema visualmente durante la defensa o en la memoria final.

## 1. Vista general

```mermaid
flowchart LR
    User[Usuario empresa / Admin] -->|HTTPS| Nginx[Nginx reverse proxy]
    Nginx -->|SPA| Angular[Frontend Angular 21]
    Nginx -->|/api| Backend[Spring Boot 4]
    Nginx -->|/ws-iot| Backend

    Shelly[Enchufe Shelly] -->|MQTT 1883| Mosquitto[Eclipse Mosquitto]
    Mosquitto -->|Spring Integration MQTT| Backend

    Backend -->|JPA| DB[(PostgreSQL + TimescaleDB)]
    Backend -->|STOMP topics| Angular

    CI[GitHub Actions] -->|SSH deploy| VPS[Hetzner VPS]
    VPS --> Nginx
```

Lectura rápida:

- Angular no accede ni a la base de datos ni al broker MQTT.
- Spring Boot concentra seguridad, negocio, ingesta y emisión WebSocket.
- TimescaleDB se usa para `readings`, la tabla de serie temporal.
- Nginx es la entrada pública web.

## 2. Flujo MQTT físico

```mermaid
sequenceDiagram
    participant Shelly as Shelly Plug S Gen3
    participant MQTT as Mosquitto
    participant Flow as IntegrationFlow
    participant Handler as DeviceMessageHandler
    participant DB as readings hypertable
    participant WS as STOMP / WebSocket
    participant UI as Dashboard Angular

    Shelly->>MQTT: Publica events/rpc o status/switch:0
    MQTT->>Flow: Mensaje MQTT QoS 1
    Flow->>Flow: Enruta por topic
    alt events/rpc
        Flow->>Handler: Message<EventsRpc>
    else status/switch:0
        Flow->>Handler: Message<Status>
    end
    Handler->>DB: Guarda Reading
    Handler->>WS: Publica /topic/readings/{mac}
    WS->>UI: ReadingResponse
```

## 3. Flujo de simuladores

```mermaid
sequenceDiagram
    participant Job as IotTelemetrySimulationJob
    participant Repo as DeviceRepository
    participant Processor as SimulatedTelemetryProcessor
    participant Registry as SimulationProfileRegistry
    participant Reading as ReadingService
    participant WS as TelemetryBroadcaster
    participant Alert as AlertService

    Job->>Job: @Scheduled cada simulation.interval-ms
    Job->>Repo: findBySimulatedTrue()
    loop Por dispositivo simulado
        Job->>Processor: processDevice(device, tickStart, intervalMs)
        Processor->>Registry: calculatePowerW(profile, readingTime, device)
        Processor->>Reading: saveSimulatedReading(...)
        Processor->>WS: broadcast(ReadingResponse)
        Processor->>Alert: checkPowerThreshold(reading)
    end
```

La transacción por dispositivo evita que un simulador roto frene el resto de la demostración.

## 4. Modelo de datos principal

```mermaid
erDiagram
    USERS ||--o{ DEVICES : owns
    USERS }o--|| TARIFFS : active_tariff
    USERS ||--o{ ALERTS : receives
    DEVICES ||--o{ READINGS : produces
    DEVICES ||--o{ ALERTS : triggers
    TARIFFS ||--o{ PERIODS : has_prices
    TARIFFS ||--o{ TARIFF_CONTRACTED_POWERS : has_limits

    USERS {
        bigint id
        string username
        string role
        boolean active
        bigint tariff_id
    }

    DEVICES {
        bigint id
        bigint user_id
        string name
        string mac_address
        boolean is_on
        boolean is_simulated
        string simulation_profile
    }

    READINGS {
        instant time
        bigint device_id
        decimal power_w
        decimal energy_total_kwh
        boolean is_on
    }

    TARIFFS {
        bigint id
        string name
        string market
        string access_tariff_code
        string geographic_zone
        string energy_company
    }

    PERIODS {
        bigint id
        bigint tariff_id
        string period_code
        decimal price_kwh
    }

    TARIFF_CONTRACTED_POWERS {
        bigint id
        bigint tariff_id
        string period_code
        decimal contracted_power_kw
    }

    ALERTS {
        bigint id
        bigint user_id
        bigint device_id
        string type
        string message
    }
```

`tariff_calendar_slots` se queda fuera del diagrama para no saturarlo: es una tabla global de calendario regulatorio y no pertenece a un usuario concreto.

## 5. Estado reactivo en Angular

```mermaid
flowchart TD
    Dashboard[DashboardComponent] --> TelemetryStore
    Devices[DevicesComponent] --> TelemetryStore
    TariffView[TariffComponent] --> TariffStore

    TelemetryStore -->|HTTP| DevicesApi[/api/v1/devices/]
    TelemetryStore -->|HTTP| RecentApi[/api/v1/readings/device/{mac}/recent/]
    TelemetryStore -->|STOMP| Topic[/topic/readings/{mac}/]

    TariffStore -->|HTTP| Catalog[/api/v1/tariffs/]
    TariffStore -->|HTTP| MyTariff[/api/v1/users/me/tariff/]

    TelemetryStore --> Chart[Chart.js]
    TariffStore --> Analytics[Coste y consumo fantasma]
```

Los componentes no guardan el estado global: invocan métodos del store y leen signals.

## 6. API REST resumida

| Área | Endpoint base | Controlador |
|---|---|---|
| Autenticación | `/api/v1/auth` | `AuthController` |
| Dispositivos | `/api/v1/devices` | `DeviceController` |
| Lecturas | `/api/v1/readings` | `ReadingController` |
| Analítica | `/api/v1/analytics` | `ConsumptionController` |
| Tarifas catálogo | `/api/v1/tariffs` | `TariffController` |
| Tarifa privada | `/api/v1/users/me/tariff` | `UserTariffController` |
| Alertas | `/api/v1/alerts` | `AlertController` |

## 7. Despliegue

```mermaid
flowchart LR
    Push[Push a main] --> GH[GitHub Actions]
    GH --> FE[Build Angular production]
    GH --> BE[Package Spring Boot]
    FE --> Deploy[SSH a VPS]
    BE --> Deploy
    Deploy --> Reset[git reset --hard origin/main]
    Reset --> Env[Regenera .env]
    Env --> Compose[docker compose up -d --build]
    Compose --> Restart[docker compose restart nginx]
```

El reinicio de Nginx evita 502 cuando Docker recrea backend o frontend y cambian las IPs internas.

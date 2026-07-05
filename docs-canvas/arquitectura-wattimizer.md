# Diagramas tecnicos de Wattimizer

Estos diagramas complementan la memoria tecnica. Estan escritos en Mermaid para que puedan renderizarse en GitHub, Cursor o exportarse a imagen para la memoria final.

## 1. Arquitectura general

```mermaid
flowchart TB
    Usuario[Usuario web] --> Angular[Frontend Angular 21]
    Angular -->|REST JSON /api/v1| Backend[Spring Boot 4]
    Angular -->|STOMP WebSocket /ws-iot| Backend

    Shelly[Shelly Plug S Gen3] -->|MQTT| Mosquitto[Eclipse Mosquitto]
    Mosquitto -->|Spring Integration MQTT| Backend

    Backend -->|JPA / JDBC| Timescale[(PostgreSQL + TimescaleDB)]
    Backend -->|/topic/readings/{mac}| Angular
    Backend -->|/topic/alerts/{username}| Angular

    subgraph Docker Compose
        Mosquitto
        Backend
        Timescale
        Nginx[Nginx reverse proxy]
    end

    Nginx --> Angular
```

## 2. Flujo de telemetria MQTT y simulada

```mermaid
sequenceDiagram
    participant SH as Shelly / Simulador
    participant MQ as Mosquitto
    participant SI as Spring Integration
    participant RS as ReadingService
    participant DB as TimescaleDB readings
    participant TB as TelemetryBroadcaster
    participant AS as AlertService
    participant FE as Dashboard Angular

    alt Hardware Shelly
        SH->>MQ: events/rpc o status/switch:0
        MQ->>SI: mensaje MQTT
        SI->>SI: route por sufijo de topic
    else Simulacion interna
        SH->>RS: lectura generada por perfil
    end

    SI->>RS: DTO EventsRpc o Status
    RS->>DB: INSERT Reading
    RS-->>TB: ReadingResponse
    TB-->>FE: /topic/readings/{mac}
    RS-->>AS: Reading
    AS->>DB: INSERT alert si supera potencia
```

## 3. Relacion principal de datos

```mermaid
erDiagram
    USERS ||--o{ DEVICES : posee
    USERS ||--o| TARIFFS : tarifa_privada
    DEVICES ||--o{ READINGS : genera
    DEVICES ||--o{ ALERTS : dispara
    USERS ||--o{ ALERTS : recibe
    TARIFFS ||--o{ PERIODS : define_precios
    TARIFFS ||--o{ TARIFF_CONTRACTED_POWERS : define_potencias
    TARIFF_CALENDAR_SLOTS }o--|| PERIODS : resuelve_periodo

    USERS {
        bigint id
        string username
    }
    DEVICES {
        bigint id
        string mac_address
        boolean is_simulated
        string simulation_profile
    }
    READINGS {
        timestamptz time
        bigint device_id
        decimal power_w
        decimal energy_total_kwh
        boolean is_on
    }
    TARIFFS {
        bigint id
        string access_tariff_code
        string geographic_zone
    }
    PERIODS {
        bigint id
        string period_code
        decimal price_kwh
    }
    TARIFF_CONTRACTED_POWERS {
        bigint id
        string period_code
        decimal contracted_power_kw
    }
    TARIFF_CALENDAR_SLOTS {
        bigint id
        int month_number
        string day_type
        time start_time
        time end_time
    }
    ALERTS {
        bigint id
        string type
        string message
    }
```

## 4. Flujo del dashboard Angular

```mermaid
sequenceDiagram
    participant U as Usuario
    participant D as DashboardComponent
    participant TS as TelemetryStore
    participant API as API REST
    participant WS as WebsocketService
    participant ST as TariffStore

    U->>D: abre /dashboard
    D->>TS: loadDevices()
    TS->>API: GET /api/v1/devices
    API-->>TS: Device[]
    TS->>TS: selecciona primera MAC

    D->>ST: loadMyTariff()
    ST->>API: GET /api/v1/users/me/tariff
    API-->>ST: TariffDto o 204

    D->>TS: setSelectedMac(mac)
    TS->>API: GET /api/v1/readings/device/{mac}/recent
    API-->>TS: ReadingResponse[]
    TS->>WS: connectTelemetry(mac)
    WS-->>TS: stream /topic/readings/{mac}
    TS-->>D: currentReadings()

    opt hay tarifa configurada
        D->>API: GET /api/v1/analytics/cost
        D->>API: GET /api/v1/analytics/ghost-consumption
    end
```

## 5. Resolucion de coste energetico

```mermaid
flowchart TD
    A[Lecturas ordenadas por time] --> B{Hay al menos 2?}
    B -- No --> Z[Coste 0]
    B -- Si --> C[Recorrer pares previous/current]
    C --> D[delta = current.kWh - previous.kWh]
    D --> E{delta positivo?}
    E -- No --> C
    E -- Si --> F[Resolver periodo con CalendarResolverService]
    F --> G{Periodo encontrado?}
    G -- No --> C
    G -- Si --> H[coste = delta * priceKwh]
    H --> I[Sumar al total]
    I --> C
    C --> J[Redondear a 2 decimales]
```

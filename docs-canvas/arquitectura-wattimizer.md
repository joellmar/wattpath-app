# Docs Canvas: arquitectura tecnica de Wattimizer

Este documento resume visualmente la arquitectura de Wattimizer. Sirve como apoyo grafico para la memoria DAW y enlaza con los anexos tecnicos de `docs/memoria/`.

## 1. Mapa general

```mermaid
flowchart TB
    subgraph Cliente["Navegador"]
        Angular["Angular 21\nPrimeNG + Signals + RxJS"]
        Store["TelemetryStore / TariffStore\n@ngrx/signals"]
        Angular --> Store
    end

    subgraph Backend["Spring Boot 4.0.5"]
        REST["Controladores REST\n/api/v1"]
        Security["JWT + OAuth2\nSpring Security"]
        MQTT["Spring Integration MQTT"]
        STOMP["STOMP WebSocket\n/ws-iot"]
        Services["Servicios de negocio\nDevice, Reading, Tariff, Consumption"]
        REST --> Security
        REST --> Services
        MQTT --> Services
        Services --> STOMP
    end

    subgraph Infra["Infraestructura"]
        Mosquitto["Eclipse Mosquitto\nMQTT :1883"]
        Timescale["PostgreSQL + TimescaleDB\nreadings hypertable"]
        Nginx["Nginx reverse proxy\nHTTP/HTTPS"]
    end

    Shelly["Shelly Plug S Gen3"] -->|MQTT| Mosquitto
    Mosquitto --> MQTT
    Services --> Timescale
    Angular -->|REST JSON| REST
    STOMP -->|/topic/readings/{mac}| Angular
    Nginx --> Angular
    Nginx --> REST
```

## 2. Flujo REST autenticado

```mermaid
sequenceDiagram
    participant U as Usuario
    participant A as Angular
    participant B as Backend
    participant DB as PostgreSQL/TimescaleDB

    U->>A: Login
    A->>B: POST /api/v1/auth/login
    B-->>A: LoginUserJwt
    A->>A: Guarda JWT en sessionStorage
    U->>A: Abre dashboard
    A->>B: GET /api/v1/devices + Bearer JWT
    B->>B: JwtValidatorFilter crea Principal
    B->>DB: Consulta dispositivos del usuario
    DB-->>B: devices
    B-->>A: List<DeviceDto>
```

## 3. Flujo de telemetria MQTT

```mermaid
flowchart LR
    A[Shelly publica JSON] --> B[Mosquitto]
    B --> C[MqttPahoMessageDrivenChannelAdapter]
    C --> D{Sufijo del topic}
    D -->|/events/rpc| E[EventsRpc]
    D -->|/status/switch:0| F[Status]
    D -->|otro| G[nullChannel]
    E --> H[EventsRpcMapper]
    F --> I[StatusMapper]
    H --> J[ReadingService]
    I --> J
    J --> K[(readings)]
    J --> L[TelemetryBroadcaster]
    J --> M[AlertService]
    L --> N[Angular Dashboard]
    M --> O[(alerts)]
```

## 4. Modelo de datos resumido

```mermaid
erDiagram
    USERS ||--o{ DEVICES : owns
    DEVICES ||--o{ READINGS : produces
    USERS ||--o{ ALERTS : receives
    DEVICES ||--o{ ALERTS : triggers
    USERS ||--o| TARIFFS : private_contract
    TARIFFS ||--o{ PERIODS : prices
    TARIFFS ||--o{ TARIFF_CONTRACTED_POWERS : powers

    USERS {
      bigint id
      string username
      string role
    }

    DEVICES {
      bigint id
      string mac_address
      boolean is_simulated
      string simulation_profile
    }

    READINGS {
      timestamp time
      bigint device_id
      decimal power_w
      decimal energy_total_kwh
    }

    TARIFFS {
      bigint id
      string access_tariff_code
      string geographic_zone
    }
```

## 5. Relacion entre anexos

| Documento | Contenido |
| --- | --- |
| [`memoria-final-daw.md`](../docs/memoria/memoria-final-daw.md) | Estructura completa de memoria DAW |
| [`anexo-a-backend-rest.md`](../docs/memoria/anexo-a-backend-rest.md) | Controladores REST, endpoints y DTOs |
| [`anexo-b-frontend-angular.md`](../docs/memoria/anexo-b-frontend-angular.md) | Componentes Angular, RxJS y NgRx Signals |
| [`anexo-c-telemetria-mqtt.md`](../docs/memoria/anexo-c-telemetria-mqtt.md) | Ingesta asincrona MQTT |
| [`anexo-d-timescaledb-analitica.md`](../docs/memoria/anexo-d-timescaledb-analitica.md) | Hypertable `readings` y analitica |

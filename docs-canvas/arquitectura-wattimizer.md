# Canvas documental: arquitectura de Wattimizer

## 1. Vista general

Este canvas resume visualmente la arquitectura documentada en la memoria técnica. Sirve como apoyo para explicar el proyecto en la defensa o como anexo grafico.

```mermaid
flowchart LR
    User["Usuario web"] --> Angular["Angular 21 SPA"]
    Angular -->|REST JSON con JWT| Spring["Spring Boot 4 API"]
    Angular -->|STOMP WebSocket| Spring
    Shelly["Shelly Plug S Gen3"] -->|MQTT QoS 1| Mosquitto["Mosquitto"]
    Mosquitto -->|Spring Integration MQTT| Spring
    Spring -->|JPA| DB["PostgreSQL + TimescaleDB"]
    Spring -->|/topic/readings y /topic/alerts| Angular
    Nginx["Nginx HTTPS"] --> Angular
    Nginx --> Spring
```

## 2. Flujo de autenticación

```mermaid
sequenceDiagram
    participant UI as Angular
    participant API as AuthController
    participant Sec as Spring Security
    participant JWT as JwtTokenService

    UI->>API: POST /api/v1/auth/login
    API->>Sec: Autentica credenciales
    Sec-->>API: Authentication valida
    API->>JWT: Genera token
    JWT-->>API: JWT con username y authorities
    API-->>UI: LoginUserJwt
    UI->>UI: Guarda auth_token en sessionStorage
```

## 3. Flujo de telemetría real

```mermaid
sequenceDiagram
    participant Plug as Shelly Plug
    participant Broker as Mosquitto
    participant Flow as MqttConfig
    participant Handler as DeviceMessageHandler
    participant Readings as readings hypertable
    participant Alert as AlertService
    participant WS as TelemetryBroadcaster
    participant UI as Dashboard Angular

    Plug->>Broker: Publica events/rpc o status/switch:0
    Broker->>Flow: Entrega mensaje al adaptador Paho
    Flow->>Flow: Route por sufijo del topic
    Flow->>Handler: DTO EventsRpc o Status
    Handler->>Readings: Guarda Reading
    Handler->>WS: /topic/readings/{mac}
    Handler->>Alert: Comprueba potencia contratada
    Alert-->>WS: /topic/alerts/{username} si hay OVERPOWER
    WS-->>UI: Actualizacion en tiempo real
```

## 4. Flujo de simuladores

```mermaid
flowchart TD
    Job["IotTelemetrySimulationJob cada simulation.interval-ms"] --> Repo["DeviceRepository.findBySimulatedTrue"]
    Repo --> Loop["Itera dispositivos simulados"]
    Loop --> Proc["SimulatedTelemetryProcessor"]
    Proc --> Profile["SimulationProfileRegistry"]
    Profile --> Calc["Calculador de potencia por perfil"]
    Calc --> Kwh["Suma energy_total_kwh"]
    Kwh --> Save["ReadingService.saveSimulatedReading"]
    Save --> Broadcast["TelemetryBroadcaster"]
    Save --> Alerts["AlertService.checkPowerThreshold"]
```

## 5. Modelo de datos reducido

```mermaid
erDiagram
    users ||--o{ devices : posee
    users ||--o{ alerts : recibe
    users }o--|| tariffs : tiene
    devices ||--o{ readings : genera
    devices ||--o{ alerts : dispara
    tariffs ||--o{ periods : contiene
    tariffs ||--o{ tariff_contracted_powers : contiene

    users {
        bigint id
        varchar username
        varchar role
        bigint tariff_id
    }
    devices {
        bigint id
        bigint user_id
        varchar name
        varchar mac_address
        boolean is_simulated
        varchar simulation_profile
    }
    readings {
        timestamptz time
        bigint device_id
        decimal power_w
        decimal energy_total_kwh
        boolean is_on
    }
    tariffs {
        bigint id
        varchar access_tariff_code
        varchar geographic_zone
        varchar energy_company
    }
```

## 6. Mapa rápido de anexos

| Tema | Documento |
|---|---|
| Memoria academica completa | `docs/memoria/memoria-final-daw.md` |
| REST Spring Boot | `docs/memoria/anexo-a-backend-rest.md` |
| Angular, RxJS y NgRx Signals | `docs/memoria/anexo-b-frontend-angular.md` |
| MQTT y WebSocket | `docs/memoria/anexo-c-telemetria-mqtt.md` |
| TimescaleDB y analítica | `docs/memoria/anexo-d-timescaledb-analitica.md` |

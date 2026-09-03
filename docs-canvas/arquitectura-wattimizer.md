# Docs Canvas. Diagramas técnicos de Wattimizer

Este documento agrupa diagramas en Mermaid para reutilizarlos en la memoria
final. Se ha colocado fuera de los anexos porque funciona como material visual
de apoyo.

## 1. Arquitectura de despliegue

```mermaid
flowchart TB
    User["Usuario web"] --> Nginx["Nginx proxy 80/443"]
    Shelly["Shelly Plug S Gen3"] --> Mosquitto["Mosquitto :1883"]

    subgraph Docker["Red Docker iot_net"]
        Nginx --> Frontend["Angular compilado"]
        Nginx --> Backend["Spring Boot API"]
        Backend --> DB["TimescaleDB/PostgreSQL 17"]
        Backend --> Mosquitto
        Backend --> WS["STOMP /ws-iot"]
    end

    Frontend -->|REST JSON /api/v1| Backend
    Frontend -->|WebSocket /ws-iot| WS
```

## 2. Flujo de telemetría real

```mermaid
sequenceDiagram
    participant S as Shelly
    participant M as Mosquitto
    participant I as Spring Integration
    participant H as DeviceMessageHandler
    participant R as ReadingService
    participant T as TimescaleDB
    participant W as STOMP
    participant A as Angular

    S->>M: events/rpc o status/switch:0
    M->>I: MQTT QoS 1
    I->>I: Router por sufijo de tópico
    I->>H: Message<EventsRpc o Status>
    H->>R: saveEntity(...)
    R->>T: INSERT readings
    H->>W: /topic/readings/{mac}
    W-->>A: ReadingResponse
```

## 3. Flujo de simulación

```mermaid
flowchart LR
    Device["Device is_simulated=true"] --> Job["IotTelemetrySimulationJob"]
    Job --> Processor["SimulatedTelemetryProcessor"]
    Processor --> Profile["SimulationProfileRegistry"]
    Profile --> Reading["ReadingService.saveSimulatedReading"]
    Reading --> DB["readings hypertable"]
    Reading --> Broadcast["TelemetryBroadcaster"]
    Reading --> Alerts["AlertService"]
    Broadcast --> Frontend["Angular dashboard"]
```

## 4. Estado reactivo del dashboard

```mermaid
flowchart TD
    Dashboard["DashboardComponent"] --> LoadDevices["TelemetryStore.loadDevices"]
    LoadDevices --> Devices["devices[]"]
    Devices --> Selected["selectedMac"]
    Selected --> Recent["loadRecentReadings(mac)"]
    Selected --> WebSocket["connectTelemetry(mac)"]
    Recent --> History["historicalReadings[mac]"]
    WebSocket --> History
    History --> Current["currentReadings computed"]
    Current --> Chart["chartData computed"]
    Selected --> Analytics["GET /analytics/cost y /ghost-consumption"]
```

## 5. Modelo de datos principal

```mermaid
erDiagram
    users }o--o| tariffs : "tariff_id"
    users |o--o{ devices : "user_id nullable"
    users ||--o{ alerts : "user_id"
    users ||--o{ federated_identities : "user_id"
    tariffs ||--|{ periods : "tariff_id"
    tariffs ||--|{ tariff_contracted_powers : "tariff_id"
    devices ||--o{ readings : "device_id"
    devices ||--o{ alerts : "device_id"
```

## 6. Cálculo de coste histórico

```mermaid
flowchart TD
    Start["macAddress + start + end"] --> Query["findReadingsInInterval"]
    Query --> Count{"menos de 2 lecturas"}
    Count -->|sí| Zero["0.00 EUR"]
    Count -->|no| Pair["recorrer pares consecutivos"]
    Pair --> Delta["delta = kWh actual - kWh anterior"]
    Delta --> Positive{"delta positivo"}
    Positive -->|no| Pair
    Positive -->|sí| Period["CalendarResolverService"]
    Period --> Price["Period.priceKwh"]
    Price --> Cost["delta * priceKwh"]
    Cost --> Sum["sumatorio"]
    Sum --> Result["totalCostEur escala 2"]
```

## 7. Resolución de periodo tarifario

```mermaid
flowchart TD
    Instant["Instant UTC"] --> Zone["ZoneId por zona geográfica"]
    Zone --> Local["Hora local"]
    Local --> Weekend{"sábado o domingo"}
    Weekend -->|sí| DayD["day_type D"]
    Weekend -->|no| Workday["findWorkdayType"]
    DayD --> Slot["findPeriodCode"]
    Workday --> Slot
    Slot --> Period["findByTariffIdAndPeriodCode"]
```

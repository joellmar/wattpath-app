# Canvas documental: arquitectura de Wattimizer

Este documento resume de forma visual los flujos principales de Wattimizer. Complementa la memoria técnica ubicada en `docs/memoria/`.

## 1. Mapa de arquitectura

```mermaid
flowchart LR
    %% El frontend y el dispositivo físico no entran por el mismo protocolo.
    %% Separarlos ayuda a ver por qué existen REST, MQTT y WebSocket a la vez.
    subgraph Cliente
        Angular[Angular SPA]
    end

    subgraph IoT
        Shelly[Shelly Plug S G3]
        Mosquitto[Eclipse Mosquitto]
    end

    subgraph Backend
        REST[Controladores REST /api/v1]
        MQTT[Spring Integration MQTT]
        STOMP[Broker STOMP /topic]
        Services[Servicios de negocio]
    end

    subgraph Datos
        Timescale[(PostgreSQL + TimescaleDB)]
    end

    Angular -->|REST JSON| REST
    Angular -->|WebSocket STOMP /ws-iot| STOMP
    Shelly -->|MQTT QoS 1| Mosquitto
    Mosquitto --> MQTT
    MQTT --> Services
    REST --> Services
    Services --> Timescale
    Services --> STOMP
    STOMP -->|/topic/readings/{mac}| Angular
```

## 2. Flujo de telemetría física

```mermaid
sequenceDiagram
    participant S as Shelly
    participant M as Mosquitto
    participant A as MqttPaho Adapter
    participant R as IntegrationFlow
    participant H as DeviceMessageHandler
    participant DB as readings
    participant W as STOMP
    participant UI as Dashboard

    S->>M: publish shellyplugsg3-.../events/rpc
    M->>A: mensaje MQTT
    A->>R: Message + RECEIVED_TOPIC
    R->>H: EventsRpc o Status
    H->>DB: guardar Reading
    H->>W: enviar ReadingResponse
    W-->>UI: /topic/readings/{mac}
```

## 3. Flujo de telemetría simulada

```mermaid
flowchart TD
    Job[IotTelemetrySimulationJob cada 5 s] --> Repo[DeviceRepository.findBySimulatedTrue]
    Repo --> Processor[SimulatedTelemetryProcessor]
    Processor --> Profile[SimulationProfileRegistry]
    Profile --> Power[Potencia W calculada]
    Power --> Energy[Odómetro kWh acumulado]
    Energy --> Save[ReadingService.saveSimulatedReading]
    Save --> Broadcast[TelemetryBroadcaster]
    Save --> Alert[AlertService.checkPowerThreshold]
    Broadcast --> Topic[/topic/readings/{mac}/]
    Alert --> Alerts[(alerts)]
```

## 4. Ciclo reactivo del dashboard Angular

```mermaid
flowchart TD
    Load[Dashboard constructor] --> Devices[TelemetryStore.loadDevices]
    Load --> Tariff[TariffStore.loadMyTariff]
    Devices --> Mac[selectedMac]
    Mac --> Recent[loadRecentReadings]
    Mac --> WS[connectTelemetry]
    Recent --> State[historicalReadings mac]
    WS --> State
    Tariff --> HasTariff{hasMyTariff}
    HasTariff -->|sí| Cost[GET /analytics/cost]
    HasTariff -->|sí| Ghost[GET /analytics/ghost-consumption]
    HasTariff -->|no| Placeholder[Mostrar métricas vacías]
    State --> Chart[PrimeNG Chart]
```

## 5. Modelo de datos esencial

```mermaid
erDiagram
    users ||--o| tariffs : "tarifa privada"
    users ||--o{ devices : "propietario"
    users ||--o{ alerts : "recibe"
    devices ||--o{ readings : "produce"
    devices ||--o{ alerts : "origina"
    tariffs ||--o{ periods : "precio kWh"
    tariffs ||--o{ tariff_contracted_powers : "potencia kW"
    tariff_calendar_slots {
        string access_tariff_code
        string geographic_zone
        int month_number
        string day_type
        string period_code
    }
```

## 6. Recorrido de una métrica de coste

```mermaid
flowchart LR
    UI[Dashboard] --> REST[GET /api/v1/analytics/cost]
    REST --> Service[ConsumptionService]
    Service --> Readings[findReadingsInInterval]
    Service --> Tariff[Device -> User -> Tariff]
    Service --> Calendar[CalendarResolverService]
    Calendar --> Slot[tariff_calendar_slots]
    Service --> Calc[delta kWh * priceKwh]
    Calc --> Result[totalCostEur]
    Result --> UI
```

## 7. Lectura rápida para defensa del proyecto

- **REST** resuelve operaciones de usuario: login, dispositivos, tarifas, alertas y analítica.
- **MQTT** resuelve la entrada asíncrona desde hardware.
- **WebSocket/STOMP** resuelve la salida en tiempo real hacia Angular.
- **TimescaleDB** prepara la tabla `readings` para crecer como serie temporal.
- **NgRx Signals + RxJS** evitan que el dashboard mezcle estado manual con suscripciones sueltas.
- **Simuladores** hacen que el proyecto sea demostrable sin depender siempre de un enchufe físico.

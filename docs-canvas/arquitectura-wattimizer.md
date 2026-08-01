# Canvas documental: arquitectura de Wattimizer

> Este documento funciona como vista visual de la memoria técnica. Resume los flujos principales y enlaza con los anexos detallados de `docs/memoria`.

## 1. Mapa general

```mermaid
flowchart LR
  subgraph Cliente
    A[Angular 21\nSignals + PrimeNG]
  end

  subgraph Backend
    B[Spring Boot 4\nREST + Security]
    M[Spring Integration MQTT]
    W[STOMP WebSocket]
    S[Servicios de negocio]
  end

  subgraph Infra
    MQ[Eclipse Mosquitto]
    DB[(PostgreSQL + TimescaleDB)]
    N[Nginx reverse proxy]
  end

  subgraph IoT
    SH[Shelly Plug S Gen3]
    SIM[Simuladores internos]
  end

  A -->|REST JSON| N
  A <-->|STOMP /ws-iot| N
  N --> B
  N --> W
  SH -->|MQTT| MQ
  MQ --> M
  M --> S
  SIM --> S
  B --> S
  S --> DB
  S --> W
  W --> A
```

## 2. Flujo de una lectura real

```mermaid
sequenceDiagram
  participant Shelly as Shelly Plug
  participant Mosq as Mosquitto
  participant MQTT as MqttConfig
  participant Handler as DeviceMessageHandler
  participant Readings as readings hypertable
  participant Alert as AlertService
  participant WS as STOMP
  participant UI as Dashboard Angular

  Shelly->>Mosq: publish /events/rpc o /status/switch:0
  Mosq->>MQTT: mensaje MQTT QoS 1
  MQTT->>Handler: DTO EventsRpc o Status
  Handler->>Readings: guardar Reading
  Handler->>WS: emitir ReadingResponse
  Handler->>Alert: comprobar potencia contratada
  WS->>UI: /topic/readings/{macAddress}
```

## 3. Flujo de analítica económica

```mermaid
flowchart TD
  A[Dashboard pide coste] --> B[ConsumptionController]
  B --> C[Verificar MAC del usuario]
  C --> D[ReadingRepository.findReadingsInInterval]
  D --> E[Ordenar lecturas por time]
  E --> F[Calcular delta positivo de kWh]
  F --> G[CalendarResolverService]
  G --> H[tariff_calendar_slots]
  H --> I[Period.priceKwh]
  I --> J[deltaKwh x priceKwh]
  J --> K[totalCostEur o ghostCostEur]
```

## 4. Flujo de estado Angular

```mermaid
flowchart LR
  R[Router protegido] --> L[MainLayout]
  L --> D[Dashboard]
  L --> DEV[Devices]
  L --> T[Tariffs]
  L --> AL[Alerts]

  D --> TS[TelemetryStore]
  D --> TFS[TariffStore]
  DEV --> TS
  T --> TFS

  TS -->|rxMethod| API[/REST API/]
  TS -->|watchReadings| WS[/STOMP topic/]
  TFS -->|rxMethod| API
  AL -->|HttpClient directo| API
```

## 5. Responsabilidades por capa

| Capa | Responsabilidad | Archivos de referencia |
| --- | --- | --- |
| Angular | Interfaz, sesión local, formularios, dashboard y stores reactivos. | `frontend/src/app/**` |
| REST Spring | Endpoints, validación de propietario y DTOs. | `backend/src/main/java/.../controllers/**` |
| Servicios Spring | Reglas de negocio: tarifas, simulación, coste, alertas y lecturas. | `backend/src/main/java/.../services/**` |
| MQTT | Entrada de telemetría física desde Mosquitto. | `MqttConfig.java`, `DeviceMessageHandler.java` |
| TimescaleDB | Persistencia de series temporales y consultas por intervalo. | `Reading.java`, `ReadingRepository.java`, scripts SQL |
| Infraestructura | Despliegue reproducible y reverse proxy. | `docker-compose.yml`, `nginx/default.conf`, `.github/workflows/deploy.yml` |

## 6. Lectura recomendada

1. [Memoria final DAW](../docs/memoria/memoria-final-daw.md)
2. [Anexo A: Backend REST](../docs/memoria/anexo-a-backend-rest.md)
3. [Anexo B: Frontend Angular](../docs/memoria/anexo-b-frontend-angular.md)
4. [Anexo C: Telemetría MQTT](../docs/memoria/anexo-c-telemetria-mqtt.md)
5. [Anexo D: TimescaleDB y analítica](../docs/memoria/anexo-d-timescaledb-analitica.md)

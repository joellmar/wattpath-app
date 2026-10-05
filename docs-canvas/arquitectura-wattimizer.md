# Docs Canvas - Arquitectura de Wattimizer

> Documento visual de apoyo para la memoria DAW. Resume las piezas tecnicas principales y enlaza mentalmente backend, frontend, MQTT y TimescaleDB.

## 1. Mapa general

```mermaid
flowchart LR
    subgraph IoT
        Shelly[Enchufe inteligente Shelly]
        Sim[Simuladores internos]
    end

    subgraph Infraestructura
        MQTT[(Mosquitto MQTT)]
        DB[(PostgreSQL + TimescaleDB)]
        Nginx[Nginx HTTPS]
    end

    subgraph Backend Spring Boot
        API[Controladores REST /api/v1]
        Integration[Spring Integration MQTT]
        Services[Servicios de dominio]
        WS[WebSocket STOMP]
    end

    subgraph Frontend Angular
        Components[Componentes standalone]
        Stores[NgRx Signals Stores]
        Charts[Dashboard Chart.js]
    end

    Shelly -->|MQTT| MQTT
    Sim -->|Scheduled job| Services
    MQTT --> Integration
    Integration --> Services
    API --> Services
    Services --> DB
    Services --> WS
    Nginx --> Components
    Components --> Stores
    Stores -->|REST JSON| API
    WS -->|/topic/readings y /topic/alerts| Stores
    Stores --> Charts
```

## 2. Capas de backend

```mermaid
flowchart TD
    Controller[Controllers REST] --> Service[Services]
    Service --> Repository[Repositories Spring Data]
    Service --> Mapper[MapStruct Mappers]
    Repository --> Entity[Entities JPA]
    Entity --> Database[(PostgreSQL / TimescaleDB)]

    Controller -. DTOs .-> Mapper
    Mapper -. convierte .-> Entity
```

### Lectura rapida

- **Controllers:** exponen endpoints, validan pertenencia basica y usan `Principal`.
- **Services:** concentran reglas: tarifas, costes, simulacion, alertas y borrado seguro.
- **Repositories:** contienen consultas JPA/JPQL.
- **Entities:** representan tablas reales.
- **DTOs:** evitan exponer entidades completas al frontend.

## 3. Flujo de telemetria real

```mermaid
sequenceDiagram
    participant Device as Dispositivo Shelly
    participant Broker as Mosquitto
    participant Flow as IntegrationFlow
    participant Handler as DeviceMessageHandler
    participant Reading as ReadingService
    participant DB as TimescaleDB
    participant Broadcaster as TelemetryBroadcaster
    participant Angular as Angular Dashboard

    Device->>Broker: Publica events/rpc o status/switch:0
    Broker->>Flow: Entrega mensaje MQTT
    Flow->>Flow: Rutea segun sufijo de topic
    Flow->>Handler: DTO EventsRpc o Status
    Handler->>Reading: saveEntity(...)
    Reading->>DB: INSERT readings
    Handler->>Broadcaster: broadcast(ReadingResponse)
    Broadcaster->>Angular: /topic/readings/{macAddress}
```

## 4. Flujo de analitica economica

```mermaid
flowchart TD
    A[Dashboard selecciona MAC] --> B[GET /api/v1/analytics/cost]
    B --> C[ConsumptionController]
    C --> D[ConsumptionService]
    D --> E[ReadingRepository.findReadingsInInterval]
    E --> F[Calcular deltas positivos de kWh]
    F --> G[CalendarResolverService]
    G --> H[tariff_calendar_slots]
    G --> I[periods]
    I --> J[Multiplicar delta_kWh por price_kWh]
    J --> K[totalCostEur]
```

La aplicacion calcula coste a partir del odometro acumulado. No suma potencia instantanea directamente, porque la energia consumida se mide mejor como diferencia entre lecturas de kWh.

## 5. Flujo de estado Angular

```mermaid
flowchart TD
    Component[DashboardComponent / DevicesComponent / TariffComponent] -->|invoca metodos| Store[NgRx signalStore]
    Store -->|rxMethod + switchMap| Http[HttpClient]
    Http --> API[Backend REST]
    API --> Http
    Http -->|tap next/error| Store
    Store -->|computed signals| Component

    WS[WebsocketService RxStomp] --> Store
```

### Stores

| Store | Estado principal | Uso |
| --- | --- | --- |
| `TelemetryStore` | dispositivos, MAC seleccionada, historial por MAC | Dashboard y gestion de dispositivos. |
| `TariffStore` | catalogo, tarifa privada, loading y errores | Pantalla de tarifas y metricas del dashboard. |

## 6. Modelo de datos resumido

```mermaid
erDiagram
    users ||--o{ devices : posee
    devices ||--o{ readings : produce
    users ||--o{ alerts : recibe
    devices ||--o{ alerts : genera
    users }o--o| tariffs : usa
    tariffs ||--o{ periods : precios
    tariffs ||--o{ tariff_contracted_powers : potencias

    readings {
      timestamptz time
      bigint device_id
      numeric power_w
      numeric energy_total_kwh
      boolean is_on
    }
```

## 7. Puntos clave para defender el proyecto

- La arquitectura separa **telemetria**, **negocio** y **presentacion**.
- TimescaleDB se usa donde tiene sentido: lecturas temporales.
- Las tarifas separan dos conceptos que suelen mezclarse: calendario regulatorio y precio contractual.
- El frontend no hace calculos criticos de coste; los solicita al backend.
- Los simuladores permiten probar el sistema aunque no haya hardware conectado.
- Las alertas se generan en backend, por lo que no dependen de que el usuario tenga abierta la pantalla.

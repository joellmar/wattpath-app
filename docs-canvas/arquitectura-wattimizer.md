# Canvas documental - Arquitectura Wattimizer

## 1. Vista general

Este canvas resume la arquitectura tecnica de Wattimizer para usarlo como apoyo visual en la memoria o en la presentacion. La aplicacion une Angular, Spring Boot, MQTT y TimescaleDB para transformar lecturas electricas en informacion economica.

```mermaid
flowchart LR
    Usuario[Usuario web] --> Angular[Angular 21]
    Angular -->|REST JSON| API[Spring Boot API]
    Angular <-->|STOMP WebSocket| WS[Spring WebSocket]
    Shelly[Shelly Plug S Gen3] -->|MQTT| Mosquitto[Eclipse Mosquitto]
    Mosquitto --> Integration[Spring Integration MQTT]
    Integration --> API
    Simuladores[Simuladores IoT] --> API
    API --> Timescale[(TimescaleDB readings)]
    API --> Postgres[(Tablas relacionales)]
```

## 2. Capas del sistema

| Capa | Pieza | Responsabilidad |
|---|---|---|
| UI | Angular + PrimeNG | Formularios, dashboard, rutas protegidas y experiencia de usuario. |
| Estado cliente | NgRx Signal Store + RxJS | Dispositivos, lecturas, tarifas y flujos HTTP/WebSocket. |
| API | Spring Boot REST | Controladores, DTOs, servicios, seguridad y errores. |
| Ingesta | Spring Integration MQTT | Suscripcion al broker y conversion de JSON Shelly. |
| Tiempo real | STOMP | Empuja lecturas y alertas al navegador. |
| Datos | PostgreSQL + TimescaleDB | Modelo relacional y serie temporal de lecturas. |
| Deploy | Docker Compose + Nginx + GitHub Actions | Ejecucion en Hetzner y publicacion automatica desde `main`. |

## 3. Mapa REST

```mermaid
mindmap
  root((/api/v1))
    auth
      login
      register
      register/admin
      oauth/exchange
    devices
      GET listado
      claim
      simulated
      simulated/demo-pack
      PUT id
      DELETE id
    readings
      latest/mac
      device/mac/recent
      search
    analytics
      cost
      ghost-consumption
    tariffs
      catalogo
      admin CRUD
    users/me/tariff
      GET
      POST
      DELETE
    alerts
      GET
      DELETE id
```

## 4. Telemetria real y simulada

```mermaid
sequenceDiagram
    participant Shelly as Shelly fisico
    participant Broker as Mosquitto
    participant Flow as MqttConfig
    participant Handler as DeviceMessageHandler
    participant Sim as SimulatedTelemetryProcessor
    participant Reading as ReadingService
    participant DB as TimescaleDB
    participant Alert as AlertService
    participant Angular as Dashboard Angular

    Shelly->>Broker: Publica MQTT events/rpc o status/switch:0
    Broker->>Flow: Entrega mensaje al adapter Paho
    Flow->>Handler: Rutea a canal EVENTS o STATUS
    Handler->>Reading: Guarda lectura real
    Sim->>Reading: Guarda lectura simulada
    Reading->>DB: INSERT readings
    Reading->>Angular: STOMP /topic/readings/{mac}
    Handler->>Alert: Comprueba potencia contratada
    Sim->>Alert: Comprueba potencia contratada
    Alert->>Angular: STOMP /topic/alerts/{username}
```

## 5. Modelo de datos

```mermaid
erDiagram
    USERS ||--o{ DEVICES : posee
    USERS }o--|| TARIFFS : tiene
    USERS ||--o{ FEDERATED_IDENTITIES : usa
    TARIFFS ||--o{ PERIODS : define_precios
    TARIFFS ||--o{ TARIFF_CONTRACTED_POWERS : define_potencias
    DEVICES ||--o{ READINGS : genera
    DEVICES ||--o{ ALERTS : provoca
    USERS ||--o{ ALERTS : recibe
```

## 6. Flujo de coste energetico

```mermaid
flowchart TB
    A[Dashboard solicita coste] --> B[ConsumptionController]
    B --> C{Dispositivo pertenece al usuario?}
    C -- No --> D[403 Forbidden]
    C -- Si --> E[ReadingRepository.findReadingsInInterval]
    E --> F[Ordenar lecturas por tiempo]
    F --> G[Calcular delta positivo de kWh]
    G --> H[CalendarResolverService]
    H --> I[tariff_calendar_slots]
    H --> J[periods.price_kwh]
    J --> K[delta_kWh * price_kWh]
    K --> L[totalCostEur]
```

## 7. Decisiones que conviene defender

- **JWT stateless:** simplifica despliegue y evita sesiones de servidor.
- **`Principal` como fuente del usuario:** el cliente no envia `userId`, reduciendo riesgos de acceso cruzado.
- **Plantillas clonadas:** un usuario puede ajustar su contrato sin mutar el catalogo global.
- **Hypertable solo en `readings`:** las series temporales se concentran donde hay volumen de datos.
- **Simuladores integrados en el mismo pipeline:** permiten demo sin hardware y prueban los mismos calculos que los datos reales.
- **Nginx como unica puerta HTTP/HTTPS:** backend y base de datos quedan dentro de la red Docker.

## 8. Puntos de mejora futuros

```mermaid
flowchart LR
    A[Estado actual] --> B[MQTT TLS o VPN]
    A --> C[Migraciones versionadas]
    A --> D[time_bucket para historicos]
    A --> E[PWA o app movil]
    A --> F[Notificaciones email]
```

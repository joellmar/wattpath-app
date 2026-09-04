# Anexo visual - Arquitectura de Wattimizer

Este documento funciona como apoyo visual para la memoria. No sustituye a los anexos técnicos, sino que resume los flujos principales para explicarlos en una defensa oral o incluirlos como capturas.

## 1. Vista general del sistema

```mermaid
flowchart LR
    U[Usuario empresa] -->|Navegador| FE[Angular 21]
    Admin[Administrador] -->|Navegador| FE
    FE -->|REST JSON + JWT| BE[Spring Boot 4]
    FE -->|STOMP WebSocket| WS[/ws-iot/]
    Shelly[Enchufe Shelly] -->|MQTT| Broker[Eclipse Mosquitto]
    Broker -->|Spring Integration MQTT| BE
    BE -->|JPA| DB[(PostgreSQL + TimescaleDB)]
    BE -->|lecturas y alertas| WS
    Sim[Simuladores de consumo] --> BE
```

## 2. Flujo de autenticación

```mermaid
sequenceDiagram
    participant Usuario
    participant Angular
    participant Backend
    participant OAuth as Google/GitHub

    alt Login clásico
        Usuario->>Angular: email y contraseña
        Angular->>Backend: POST /api/v1/auth/login
        Backend-->>Angular: JWT
    else Login social
        Usuario->>Angular: clic en proveedor
        Angular->>OAuth: redirección OAuth2
        OAuth-->>Backend: callback
        Backend-->>Angular: ticket temporal
        Angular->>Backend: POST /api/v1/auth/oauth/exchange
        Backend-->>Angular: JWT
    end
    Angular->>Angular: guarda JWT en sessionStorage
```

## 3. Flujo de alta de dispositivos

```mermaid
flowchart TD
    A[Formulario Dispositivos] --> B{Tipo elegido}
    B -->|Físico| C[Nombre + MAC Shelly]
    C --> D[POST /api/v1/devices/claim]
    D --> E[Dispositivo vinculado al usuario]
    B -->|Simulado| F[Nombre + perfil de consumo]
    F --> G[POST /api/v1/devices/simulated]
    G --> H[MAC SIM generada]
    A --> I[Botón pack demo]
    I --> J[POST /api/v1/devices/simulated/demo-pack]
    J --> K[Hasta 9 simuladores]
```

## 4. Ingesta MQTT y difusión en tiempo real

```mermaid
sequenceDiagram
    participant Shelly
    participant Mosquitto
    participant Integration as Spring Integration
    participant Handler as DeviceMessageHandler
    participant DB as TimescaleDB
    participant STOMP as WebSocket STOMP
    participant Angular

    Shelly->>Mosquitto: events/rpc o status/switch:0
    Mosquitto->>Integration: mensaje MQTT QoS 1
    Integration->>Integration: router por sufijo de topic
    Integration->>Handler: EventsRpc o Status
    Handler->>DB: guarda Reading
    Handler->>STOMP: /topic/readings/{mac}
    STOMP-->>Angular: nueva lectura
    Handler->>DB: crea alerta si supera potencia
    Handler->>STOMP: /topic/alerts/{username}
```

## 5. Dashboard multi-dispositivo

```mermaid
flowchart TD
    A[Dashboard carga] --> B[TelemetryStore.loadDevices]
    B --> C[devices]
    C --> D[selectedMac]
    D --> E[loadRecentReadings mac]
    E --> F[historicalReadings mac]
    D --> G[connectTelemetry mac]
    G --> H[WebSocket readings mac]
    H --> F
    F --> I[currentReadings computed]
    I --> J[Gráfica PrimeNG Chart]
    D --> K[Analytics cost]
    D --> L[Analytics ghost-consumption]
```

## 6. Cálculo de coste eléctrico

```mermaid
flowchart TD
    A[GET /api/v1/analytics/cost] --> B[Busca dispositivo por MAC]
    B --> C{Pertenece al usuario JWT}
    C -->|No| D[403 Forbidden]
    C -->|Sí| E{Tiene tarifa}
    E -->|No| F[Coste 0]
    E -->|Sí| G[Lecturas ordenadas por tiempo]
    G --> H[Delta positivo de kWh]
    H --> I[CalendarResolverService]
    I --> J[Periodo P1-P6]
    J --> K[Precio kWh de la tarifa]
    K --> L[Suma y redondeo a 2 decimales]
```

## 7. Modelo de datos simplificado

```mermaid
erDiagram
    USERS ||--o{ DEVICES : owns
    DEVICES ||--o{ READINGS : produces
    USERS ||--o{ ALERTS : receives
    DEVICES ||--o{ ALERTS : triggers
    USERS ||--o| TARIFFS : selected_contract
    TARIFFS ||--o{ PERIODS : prices
    TARIFFS ||--o{ TARIFF_CONTRACTED_POWERS : limits
    TARIFF_CALENDAR_SLOTS }o--|| PERIODS : resolves
```

## 8. Puntos clave para explicar en clase

- El backend no calcula coste a partir de potencia aislada, sino del delta de energía acumulada entre lecturas.
- TimescaleDB se usa solo donde aporta valor: la tabla `readings`.
- Angular mantiene históricos separados por MAC para que el cambio de medidor no mezcle datos.
- Los simuladores pasan por el mismo flujo de persistencia, WebSocket y alertas que los dispositivos reales.
- La suscripción MQTT actual está limitada a un Shelly concreto; es una mejora futura clara y defendible.

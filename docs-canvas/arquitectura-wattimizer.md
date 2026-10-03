# Canvas documental - Arquitectura de Wattimizer

> Documento visual de apoyo para la memoria tecnica. Resume los flujos que despues se explican con detalle en `docs/memoria`.

## 1. Vista general

```mermaid
flowchart LR
    Usuario[Usuario empresa] -->|Navegador| Angular[Angular 21]
    Angular -->|REST JSON /api/v1| Backend[Spring Boot 4]
    Angular -->|STOMP /ws-iot| Backend
    Shelly[Shelly Plug S Gen3] -->|MQTT| Mosquitto[Eclipse Mosquitto]
    Mosquitto -->|Spring Integration MQTT| Backend
    Backend -->|JPA| Timescale[(PostgreSQL + TimescaleDB)]
    Backend -->|JWT/OAuth2| Seguridad[Spring Security]
    GitHub[GitHub Actions] -->|SSH| Hetzner[VPS Hetzner]
```

La idea basica es que el dispositivo no habla con Angular. El Shelly publica MQTT, el backend normaliza y guarda la lectura, y despues Angular recibe datos ya filtrados por REST o WebSocket.

## 2. Capas de la aplicacion

| Capa | Ficheros principales | Responsabilidad |
|---|---|---|
| UI Angular | `frontend/src/app/components/*` | Formularios, dashboard, navegacion y mensajes de usuario. |
| Estado Angular | `telemetry.store.ts`, `tariff.store.ts` | Datos compartidos y flujos reactivos. |
| API REST | `backend/.../controllers/*` | Entrada HTTP versionada bajo `/api/v1`. |
| Negocio | `backend/.../services/*` | Reglas de tarifas, dispositivos, lecturas, alertas y simulacion. |
| Persistencia | `entities/*`, `repositories/*` | Modelo relacional e hypertable de lecturas. |
| IoT | `MqttConfig`, `DeviceMessageHandler` | Entrada MQTT y transformacion a lecturas. |
| Despliegue | `docker-compose.yml`, `.github/workflows/deploy.yml` | Ejecucion productiva y CI/CD. |

## 3. Flujo de login

```mermaid
sequenceDiagram
    participant U as Usuario
    participant A as Angular
    participant B as AuthController
    participant S as JwtTokenService

    U->>A: Email y contrasena
    A->>B: POST /api/v1/auth/login
    B->>B: autentica credenciales
    B->>S: genera JWT
    S-->>B: token firmado
    B-->>A: LoginUserJwt
    A->>A: guarda token en sessionStorage
    A->>U: navega a /dashboard
```

## 4. Flujo de telemetria real

```mermaid
sequenceDiagram
    participant D as Shelly
    participant M as Mosquitto
    participant I as Spring Integration
    participant H as DeviceMessageHandler
    participant R as ReadingService
    participant DB as TimescaleDB
    participant W as WebSocket
    participant F as Angular

    D->>M: publica events/rpc o status/switch:0
    M->>I: entrega mensaje MQTT
    I->>I: enruta por topic
    I->>H: DTO EventsRpc o Status
    H->>R: saveEntity(...)
    R->>DB: INSERT readings
    H->>W: /topic/readings/{mac}
    W-->>F: ReadingResponse
```

Punto critico: `events/rpc` solo conserva bien la MAC si el dispositivo ya existe en la base de datos; para la demo fisica conviene sembrar o reclamar el Shelly antes.

## 5. Flujo de simuladores

```mermaid
flowchart TD
    Job[IotTelemetrySimulationJob cada simulation.interval-ms] --> Repo[DeviceRepository.findBySimulatedTrue]
    Repo --> Processor[SimulatedTelemetryProcessor]
    Processor --> Registry[SimulationProfileRegistry]
    Registry --> Power[Potencia W segun perfil]
    Processor --> Reading[ReadingService.saveSimulatedReading]
    Reading --> DB[(readings)]
    Processor --> WS[TelemetryBroadcaster]
    Processor --> Alerts[AlertService.checkPowerThreshold]
```

Los simuladores no evitan la base de datos. Generan lecturas reales para que dashboard, costes y alertas se comporten igual que con hardware.

## 6. Flujo de calculo de coste

```mermaid
flowchart LR
    Request[GET /api/v1/analytics/cost] --> Validate[Comprobar propietario de MAC]
    Validate --> Readings[Lecturas ordenadas por intervalo]
    Readings --> Delta[Delta positivo de kWh]
    Delta --> Calendar[CalendarResolverService]
    Calendar --> Period[Period priceKwh]
    Period --> Sum[Suma delta * precio]
    Sum --> Response[totalCostEur]
```

El backend calcula coste con energia acumulada, no solo con potencia instantanea. Asi se evitan errores por muestras irregulares.

## 7. Modelo de datos resumido

```mermaid
erDiagram
    users ||--o{ devices : posee
    users }o--|| tariffs : tarifa_activa
    users ||--o{ alerts : recibe
    users ||--o{ federated_identities : login_social
    devices ||--o{ readings : genera
    devices ||--o{ alerts : dispara
    tariffs ||--o{ periods : precios
    tariffs ||--o{ tariff_contracted_powers : potencias
```

## 8. Lectura recomendada

1. `docs/memoria/memoria-final-daw.md` para la memoria principal.
2. `docs/memoria/anexo-a-backend-rest.md` para endpoints y DTOs.
3. `docs/memoria/anexo-b-frontend-angular.md` para componentes y reactividad.
4. `docs/memoria/anexo-c-telemetria-mqtt.md` para MQTT y simulacion.
5. `docs/memoria/anexo-d-timescaledb-analitica.md` para base de datos y consultas.

# Memoria técnica del proyecto DAW — Wattimizer

## 1. Introducción y justificación

### 1.1. Título del proyecto

**Wattimizer** es una plataforma web B2B de inteligencia financiera energética. La aplicación transforma lecturas de consumo eléctrico en información útil para una pyme: potencia instantánea, coste estimado, consumo fantasma y alertas por exceso de potencia contratada.

### 1.2. Descripción del problema

Muchas pequeñas empresas conocen el importe final de la factura eléctrica, pero no saben qué equipos provocan los picos de consumo ni en qué franjas horarias se dispara el gasto. Esa falta de visibilidad hace difícil ajustar hábitos, revisar una tarifa o detectar cargas que se quedan encendidas por la noche.

Wattimizer cubre esa necesidad conectando tres piezas: dispositivos IoT o simulados, una API Spring Boot que guarda y analiza las lecturas, y una interfaz Angular que muestra el estado en tiempo real. La decisión de incluir simuladores permite enseñar el proyecto completo aunque no haya siempre un Shelly físico disponible.

### 1.3. Objetivos

**Objetivo general:** desarrollar una aplicación web que monitorice dispositivos eléctricos, calcule costes según tarifa y alerte de consumos anómalos.

**Objetivos específicos:**

- Proteger la plataforma con JWT y OAuth2.
- Gestionar dispositivos físicos y simulados por usuario.
- Ingerir telemetría MQTT con Spring Integration.
- Persistir lecturas como serie temporal en PostgreSQL + TimescaleDB.
- Resolver periodos tarifarios TD mediante calendario regulatorio.
- Calcular coste energético y consumo fantasma por rangos de fechas.
- Emitir lecturas y alertas en tiempo real mediante STOMP/WebSocket.
- Construir una SPA Angular con stores reactivos basados en NgRx Signals y RxJS.
- Desplegar la aplicación con Docker Compose, Nginx y GitHub Actions.

### 1.4. Tipos de usuarios

| Usuario | Uso principal | Permisos |
|---|---|---|
| Usuario empresa | Consulta consumo, dispositivos, tarifa y alertas. | Opera solo sobre recursos propios resueltos por JWT. |
| Administrador | Mantiene plantillas de tarifas y altas administrativas. | Puede escribir en `/api/v1/tariffs` y usar registro admin protegido. |
| Dispositivo IoT | Publica lecturas eléctricas al broker MQTT. | Envía topics Shelly `events/rpc` y `status/switch:0`. |
| Simulador interno | Genera lecturas sintéticas para demos. | Procesa dispositivos con `is_simulated=true`. |

---

## 2. Fase 1: Análisis funcional

### 2.1. Mapa de funcionalidades

| Módulo | Funcionalidades | Código relacionado |
|---|---|---|
| Autenticación | Login, registro, OAuth2, emisión JWT. | `AuthController`, `SecurityConfig`, `SessionStorageService`. |
| Dispositivos | Listar, reclamar, crear, editar, borrar, pack demo. | `DeviceController`, `DeviceService`, `DevicesComponent`, `TelemetryStore`. |
| Telemetría | MQTT, persistencia, WebSocket y precarga de histórico. | `MqttConfig`, `DeviceMessageHandler`, `ReadingService`, `WebsocketService`. |
| Analítica | Coste diario y consumo fantasma. | `ConsumptionController`, `ConsumptionService`, `CalendarResolverService`. |
| Tarifas | Catálogo, contrato privado, precios P1-P6 y potencias. | `TariffController`, `UserTariffController`, `TariffComponent`, `TariffStore`. |
| Alertas | Detección de sobrepotencia y descarte. | `AlertService`, `AlertController`, `AlertsComponent`. |
| Despliegue | Build, contenedores, proxy y CI/CD. | `docker-compose.yml`, `nginx/default.conf`, `.github/workflows/deploy.yml`. |

### 2.2. Historias de usuario

| ID | Historia | Criterios de aceptación | Prioridad |
|---|---|---|---|
| HU-01 | Como usuario, quiero registrarme e iniciar sesión. | El backend devuelve JWT; las rutas privadas no cargan sin token válido. | MVP |
| HU-02 | Como usuario, quiero vincular un enchufe físico por MAC. | `POST /api/v1/devices/claim` asigna el dispositivo al `Principal`. | MVP |
| HU-03 | Como usuario, quiero crear simuladores de consumo. | `POST /api/v1/devices/simulated` exige nombre y `simulationProfile`. | MVP |
| HU-04 | Como usuario, quiero ver potencia en tiempo real. | Angular escucha `/topic/readings/{mac}` y actualiza la gráfica. | MVP |
| HU-05 | Como usuario, quiero conocer el coste del periodo consultado. | `/api/v1/analytics/cost` devuelve `totalCostEur`. | MVP |
| HU-06 | Como usuario, quiero detectar consumo fantasma nocturno. | `/ghost-consumption` calcula entre 00:00 y 05:59 hora local. | MVP |
| HU-07 | Como usuario, quiero asignar o editar mi tarifa privada. | `UserTariffService` clona plantillas y no modifica el catálogo. | MVP |
| HU-08 | Como administrador, quiero mantener el catálogo tarifario. | Escritura en `/api/v1/tariffs` requiere `ROLE_ADMIN`. | MVP |
| HU-09 | Como usuario, quiero recibir alertas por superar potencia. | `AlertService` compara `powerW/1000` con potencia contratada. | Opcional ampliada |
| HU-10 | Como usuario, quiero borrar dispositivos sin dejar datos colgando. | `DeviceService.deleteById` borra lecturas y alertas antes del dispositivo. | MVP |

### 2.3. Gestión del trabajo

- **Repositorio:** `https://github.com/joellmar/wattpath-app`.
- **Columnas Kanban usadas:** Backlog, Por hacer, En progreso, En revisión y Hecho.
- **Evidencia de evolución:** los commits recientes añaden perfiles de simulación, pack demo, panel multi-dispositivo, borrado seguro de datos asociados y ajuste de despliegue para reiniciar Nginx tras recrear contenedores.

### 2.4. Planificación inicial

| Fase | Historias | Dificultad | Resultado |
|---|---|---|---|
| Análisis y modelo | HU-01, HU-07, HU-08 | Media | Usuarios, tarifas, dispositivos y lecturas definidos. |
| Backend REST | HU-01, HU-02, HU-05, HU-06, HU-09 | Alta | API segura y reglas de negocio. |
| IoT y series temporales | HU-03, HU-04 | Alta | MQTT, STOMP y TimescaleDB. |
| Frontend Angular | HU-02, HU-03, HU-04, HU-07, HU-10 | Alta | Vistas protegidas, stores y formularios. |
| Pruebas y despliegue | Todas | Media | Tests, Docker Compose y CI/CD. |

---

## 3. Fase 2: Diseño técnico

### 3.1. Diseño de la base de datos

| Tabla | Propósito | Relaciones principales |
|---|---|---|
| `users` | Cuentas con rol, estado activo y tarifa asignada. | FK nullable `tariff_id`. |
| `devices` | Dispositivos físicos o simulados. | FK `user_id`, `mac_address` único. |
| `readings` | Lecturas de telemetría. | FK `device_id`, hypertable por `time`. |
| `alerts` | Incidencias por sobrepotencia. | FK a `users` y `devices`. |
| `tariffs` | Plantillas y contratos privados. | Relación con periodos y potencias. |
| `periods` | Precio €/kWh por periodo P1-P6. | Único `(tariff_id, period_code)`. |
| `tariff_contracted_powers` | Potencia contratada por periodo. | Único `(tariff_id, period_code)`. |
| `tariff_calendar_slots` | Calendario regulatorio zona/mes/hora -> periodo. | Tabla global sin propietario de usuario. |

El diagrama E/R se incluye en [`../../docs-canvas/arquitectura-wattimizer.md`](../../docs-canvas/arquitectura-wattimizer.md).

### 3.2. Arquitectura del sistema

| Capa | Tecnología | Función |
|---|---|---|
| Frontend | Angular 21, PrimeNG, Chart.js, RxJS, NgRx Signals | SPA, formularios, gráfica y estado reactivo. |
| Backend | Spring Boot 4.0.5, Java 26, Spring Security, JPA | API REST, seguridad, negocio y persistencia. |
| IoT | Mosquitto, Spring Integration MQTT, Eclipse Paho | Entrada asíncrona de telemetría. |
| Tiempo real | Spring WebSocket + STOMP | Emisión de lecturas a Angular. |
| Datos | PostgreSQL 17 + TimescaleDB | Relacional + series temporales. |
| Producción | Docker Compose, Nginx, Hetzner, GitHub Actions | Despliegue automatizado. |

### 3.3. Diseño de interfaz

| Ruta | Componente | Pantalla |
|---|---|---|
| `/login` | `LoginComponent` | Login por credenciales u OAuth2. |
| `/register` | `RegisterComponent` | Registro de usuario. |
| `/dashboard` | `DashboardComponent` | Gráfica, coste y consumo fantasma. |
| `/devices` | `DevicesComponent` | Alta, edición y borrado de dispositivos. |
| `/tariffs` | `TariffComponent` | Catálogo y tarifa privada. |
| `/alerts` | `AlertsComponent` | Historial de alertas. |

### 3.4. Relación entre historias y diseño

| Historia | Tablas | Backend | Frontend |
|---|---|---|---|
| HU-01 | `users`, `federated_identities` | `AuthController`, `SecurityConfig` | `LoginComponent`, `RegisterComponent`, `OAuthCallbackComponent` |
| HU-02 | `devices` | `DeviceController`, `DeviceService` | `DevicesComponent` |
| HU-03 | `devices`, `readings` | `IotTelemetrySimulationJob`, `SimulatedTelemetryProcessor` | `DevicesComponent`, `TelemetryStore` |
| HU-04 | `readings` | `TelemetryBroadcaster`, `ReadingController` | `WebsocketService`, `DashboardComponent` |
| HU-05 | `readings`, `tariffs`, `periods` | `ConsumptionService` | `DashboardComponent` |
| HU-06 | `readings`, `tariff_calendar_slots` | `CalendarResolverService` | `DashboardComponent` |
| HU-07 | `users`, `tariffs` | `UserTariffService` | `TariffStore`, `TariffComponent` |
| HU-08 | `tariffs`, `periods` | `TariffService` | `TariffService` frontend |
| HU-09 | `alerts`, `readings` | `AlertService` | `AlertsComponent` |
| HU-10 | `devices`, `readings`, `alerts` | `DeviceService.deleteById` | `DevicesComponent.onDelete` |

---

## 4. Fase 3: Implementación y desarrollo

### 4.1. Tecnologías utilizadas

| Área | Versión o herramienta |
|---|---|
| Java | 26 |
| Spring Boot | 4.0.5 |
| Angular | 21.x |
| TypeScript | 5.9.x |
| RxJS | 7.8.x |
| NgRx Signals | 21.1.x |
| PrimeNG | 21.1.x |
| Chart.js | 4.5.x |
| TimescaleDB | Imagen `timescale/timescaledb-ha:pg17` |
| MQTT | Mosquitto 2.1.2 Alpine |
| Calidad frontend | Biome y Vitest |

### 4.2. Desarrollo del backend

El backend usa controladores REST, servicios transaccionales, repositorios JPA, DTOs y MapStruct. La seguridad se resuelve con JWT y OAuth2. Las operaciones multitenant se apoyan en `Principal`, por lo que el cliente no decide qué usuario está operando.

`GlobalExceptionHandler` centraliza las respuestas de error y devuelve `ErrorResponse`, lo que permite a Angular mostrar mensajes de negocio sin exponer detalles internos.

### 4.3. Desarrollo del frontend

Angular usa componentes standalone, rutas lazy-loaded y stores de NgRx Signals. `TelemetryStore` mantiene dispositivos, MAC seleccionada e histórico por MAC. `TariffStore` mantiene catálogo, tarifa privada y estados de carga.

La lógica reactiva usa `rxMethod`, `switchMap`, `tap`, `catchError`, `filter` y `distinctUntilChanged`. El dashboard combina REST para cargar histórico inicial y WebSocket para lecturas nuevas.

### 4.4. Control de versiones

El historial reciente refleja commits pequeños por tema: simuladores, pack demo, correcciones de borrado, telemetría por perfil, panel multi-dispositivo y despliegue. La rama `main` dispara CI/CD, por lo que el trabajo se prepara en ramas antes de integrarse.

---

## 5. Fase 4: Pruebas y despliegue

### 5.1. Plan de pruebas

| Prueba | Archivo | Criterio |
|---|---|---|
| Crear simulador con perfil | `DeviceServiceTest` | MAC sintética, propietario y perfil correctos. |
| Borrado seguro de dispositivo | `DeviceServiceTest` | Elimina lecturas y alertas antes del dispositivo. |
| Simulación robusta | `IotTelemetrySimulationJobTest` | Un fallo no detiene el resto del tick. |
| Coste fantasma por zona | `ConsumptionServiceTest` | Canarias usa `Atlantic/Canary`. |
| Tarifas privadas | `UserTariffServiceTest` | Clonado, edición propia y desvinculación. |
| Validación tarifaria | `TariffServiceTest` | Periodos y potencias obligatorios. |
| Perfiles de consumo | `SimulationProfileRegistryTest` | Potencia no negativa y determinista. |
| Componentes Angular | `frontend/src/app/**/*.spec.ts` | Servicios, formularios y componentes principales. |

### 5.2. Manual de instalación

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Frontend:

```bash
cd frontend
npm ci --legacy-peer-deps
npm start
```

Docker Compose:

```bash
cp .env.example .env
docker compose --env-file .env up -d --build
```

Scripts de base de datos en una base limpia:

```text
backend/src/main/resources/db/dev-seed/00-extensions.sql
backend/src/main/resources/db/dev-seed/01-hypertable.sql
backend/src/main/resources/db/tariffs-td-schema.sql
backend/src/main/resources/db/seed-tariff-calendar-slots.sql
backend/src/main/resources/db/dev-seed/03-seed-users-dev.sql
backend/src/main/resources/db/dev-seed/04-seed-device-shelly.sql
backend/src/main/resources/db/dev-seed/05-seed-device-simulation.sql
```

La guía completa está en [`../deployment/hetzner-production.md`](../deployment/hetzner-production.md).

### 5.3. Despliegue

Producción usa Hetzner VPS, Docker Compose, Nginx, Mosquitto y TimescaleDB. GitHub Actions valida frontend y backend, conecta por SSH, actualiza el código, reconstruye contenedores y reinicia Nginx. Ese reinicio evita errores 502 cuando Docker recrea backend o frontend con IP interna nueva.

---

## 6. Conclusiones y líneas futuras

### 6.1. Grado de cumplimiento

El MVP está cubierto: login, dispositivos, telemetría, dashboard, tarifas, analítica, alertas y despliegue. Los simuladores hacen que el proyecto sea demostrable aunque no haya hardware físico conectado.

### 6.2. Dificultades

- Separar dos formatos MQTT distintos (`events/rpc` y `status/switch:0`).
- Convertir `readings` en hypertable fuera de Hibernate.
- Modelar tarifas TD separando precio contractual y calendario regulatorio.
- Evitar IDOR resolviendo siempre el propietario desde JWT.
- Ajustar Nginx tras recrear contenedores para no conservar IPs internas antiguas.

### 6.3. Mejoras futuras

- Migrar MQTT a TLS/8883 o VPN.
- Completar festivos y zonas regulatorias no sembradas.
- Añadir informes semanales y mensuales por dispositivo.
- Exportar datos a PDF o CSV.
- Crear PWA o app móvil para alertas.

---

## 7. Bibliografía y recursos

- Documentación oficial de Spring Boot, Spring Security y Spring Integration MQTT.
- Documentación de Angular, RxJS y NgRx Signals.
- Documentación de TimescaleDB sobre hypertables.
- Documentación de PrimeNG, Chart.js, Docker Compose, Nginx y GitHub Actions.
- Circular CNMC 3/2020 sobre peajes eléctricos TD.

## Anexos técnicos

- [Anexo A — Backend REST Spring Boot](./anexo-a-backend-rest.md)
- [Anexo B — Frontend Angular, RxJS y NgRx Signals](./anexo-b-frontend-angular.md)
- [Anexo C — Telemetría MQTT con Spring Integration](./anexo-c-telemetria-mqtt.md)
- [Anexo D — TimescaleDB y analítica energética](./anexo-d-timescaledb-analitica.md)
- [Canvas documental de arquitectura](../../docs-canvas/arquitectura-wattimizer.md)

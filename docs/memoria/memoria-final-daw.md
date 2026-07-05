# Memoria tecnica del proyecto Wattimizer

## 1. Introduccion y justificacion

### 1.1. Titulo del proyecto

**Wattimizer** es una aplicacion web B2B orientada al control del consumo electrico en pequenas empresas mediante dispositivos IoT, analitica energetica y calculo economico segun tarifas electricas.

### 1.2. Descripcion del problema

Muchas pymes conocen el importe final de su factura electrica, pero no tienen una vision clara de que dispositivos provocan ese gasto ni en que momentos se produce. Esta falta de informacion dificulta detectar consumos fantasma, picos de potencia o contratos mal ajustados. En la practica, el empresario suele actuar tarde: cuando ya ha recibido una factura elevada.

Wattimizer aborda ese problema conectando enchufes inteligentes Shelly, telemetria MQTT, una API REST en Spring Boot y un panel Angular. La aplicacion transforma lecturas tecnicas de potencia y energia en datos comprensibles: coste estimado, consumo nocturno, alertas de maximetro y graficas en tiempo real.

### 1.3. Objetivos

**Objetivo general**

Desarrollar una plataforma web capaz de monitorizar consumos electricos por dispositivo, calcular costes segun tarifas TD espanolas y avisar al usuario cuando se detecten situaciones de riesgo economico.

**Objetivos especificos**

- Implementar autenticacion con JWT y login social OAuth2 para proteger los datos de cada usuario.
- Registrar dispositivos IoT fisicos o simulados y asociarlos a una cuenta concreta.
- Ingerir telemetria MQTT desde un Shelly Plug S Gen3 usando Spring Integration.
- Persistir series temporales de lecturas en TimescaleDB mediante una hypertable.
- Calcular costes energeticos y consumo fantasma a partir de lecturas acumuladas de kWh.
- Permitir al usuario configurar una tarifa privada derivada del catalogo maestro.
- Mostrar una interfaz Angular reactiva con RxJS, NgRx Signals y WebSocket STOMP.
- Generar alertas cuando la potencia instantanea supere la potencia contratada del periodo aplicable.

### 1.4. Tipos de usuarios

| Rol | Descripcion | Acciones principales |
| --- | --- | --- |
| Usuario | Cliente de la plataforma. | Gestiona sus dispositivos, consulta dashboard, asigna su tarifa y revisa alertas. |
| Administrador | Perfil tecnico o gestor de la plataforma. | Mantiene el catalogo maestro de tarifas y puede crear cuentas admin mediante clave interna. |
| Sistema IoT | Broker MQTT y job de simulacion. | Publica o genera lecturas que el backend procesa de forma automatica. |

## 2. Fase 1: Analisis funcional

### 2.1. Mapa de funcionalidades

| Modulo | Funcionalidades implementadas |
| --- | --- |
| Autenticacion | Registro, login con email, login OAuth2, emision de JWT, guardado de sesion en `sessionStorage`. |
| Dispositivos | Listado, reclamacion de dispositivo fisico, alta manual, alta simulada, pack demo, edicion, encendido/apagado y borrado con lecturas/alertas asociadas. |
| Telemetria | Recepcion MQTT, simulacion periodica, persistencia de lecturas, emision WebSocket y precarga de historial reciente. |
| Tarifas | Catalogo maestro, tarifa privada del usuario, periodos P1-P6, potencia contratada por periodo y calendario regulatorio. |
| Analitica | Coste total por intervalo, coste fantasma nocturno y resolucion del periodo tarifario aplicable. |
| Alertas | Deteccion de sobrepotencia, broadcast STOMP y listado/borrado de alertas. |
| Despliegue | Docker Compose con TimescaleDB, Mosquitto, Spring Boot, Angular y Nginx. |

### 2.2. Historias de usuario

| ID | Historia | Criterios de aceptacion | Prioridad |
| --- | --- | --- | --- |
| HU-01 | Como usuario, quiero registrarme e iniciar sesion para acceder a mis datos energeticos. | El sistema crea cuenta, valida credenciales y devuelve JWT; rutas privadas redirigen a login si no hay sesion valida. | MVP |
| HU-02 | Como usuario, quiero vincular un enchufe inteligente para ver sus lecturas. | El endpoint `/api/v1/devices/claim` asocia la MAC al usuario autenticado y el dashboard muestra el dispositivo. | MVP |
| HU-03 | Como usuario, quiero consultar potencia en tiempo real para detectar consumos anormales. | El frontend se suscribe a `/topic/readings/{mac}` y actualiza la grafica sin recargar la pagina. | MVP |
| HU-04 | Como usuario, quiero configurar mi tarifa para obtener costes en euros. | El usuario puede clonar una tarifa del catalogo o guardar un contrato privado en `/api/v1/users/me/tariff`. | MVP |
| HU-05 | Como usuario, quiero saber cuanto gasto en un intervalo del dia. | `/api/v1/analytics/cost` devuelve `totalCostEur` para la MAC y rango indicados. | MVP |
| HU-06 | Como usuario, quiero identificar consumo fantasma nocturno. | `/api/v1/analytics/ghost-consumption` calcula coste solo entre las 00:00 y las 05:59 hora local. | MVP |
| HU-07 | Como usuario, quiero recibir alertas si supero la potencia contratada. | `AlertService.checkPowerThreshold` crea alerta `OVERPOWER` y la API `/api/v1/alerts` la lista. | MVP |
| HU-08 | Como administrador, quiero gestionar el catalogo de tarifas. | Las mutaciones de `/api/v1/tariffs` requieren `ROLE_ADMIN`. | MVP |
| HU-09 | Como alumno/desarrollador, quiero simular dispositivos sin hardware. | El usuario puede crear simuladores y el job `IotTelemetrySimulationJob` genera lecturas cada intervalo configurado. | MVP |
| HU-10 | Como responsable de despliegue, quiero levantar todo el sistema en Docker. | `docker-compose.yml` define base de datos, broker, backend, frontend y proxy Nginx. | MVP |

### 2.3. Gestion del trabajo

- **Repositorio:** `https://github.com/joellmar/wattpath-app`
- **Rama de documentacion:** `cursor/documentaci-n-t-cnica-del-proyecto-c7b2`
- **Flujo observado:** commits pequenos sobre `main`, con correcciones recientes de despliegue, simuladores, configuracion OAuth2, Mosquitto y Nginx.
- **Kanban recomendado para la memoria:** Backlog, Por hacer, En progreso, En revision y Hecho. La captura debe anadirse desde GitHub Projects si se entrega la memoria final en PDF.

### 2.4. Planificacion inicial

| Fase | Historias asociadas | Dificultad tecnica |
| --- | --- | --- |
| Analisis y seguridad | HU-01, HU-08 | Media: requiere JWT, roles y OAuth2. |
| IoT y persistencia | HU-02, HU-03, HU-09 | Alta: combina MQTT, WebSocket, JPA y TimescaleDB. |
| Tarifas y analitica | HU-04, HU-05, HU-06, HU-07 | Alta: hay reglas regulatorias por periodos, calendario y zonas horarias. |
| Frontend | HU-01 a HU-09 | Media-alta: estado reactivo, guards, stores y graficas. |
| Despliegue | HU-10 | Media: varios servicios coordinados por red Docker y proxy inverso. |

## 3. Fase 2: Diseno tecnico

### 3.1. Diseno de la base de datos

El modelo combina tablas relacionales clasicas con una tabla temporal principal:

- `users`, `roles` y `federated_identities` gestionan identidad local y social.
- `devices` representa enchufes fisicos o simulados.
- `readings` guarda la serie temporal de lecturas y se convierte en hypertable de TimescaleDB por la columna `time`.
- `tariffs`, `periods`, `tariff_contracted_powers` y `tariff_calendar_slots` modelan contratos energeticos TD.
- `alerts` registra incidencias de maximetro vinculadas a usuario y dispositivo.

El diagrama E/R y el diagrama de arquitectura estan en [`../../docs-canvas/arquitectura-wattimizer.md`](../../docs-canvas/arquitectura-wattimizer.md).

### 3.2. Arquitectura del sistema

| Capa | Tecnologia real del repositorio | Responsabilidad |
| --- | --- | --- |
| Frontend | Angular 21, TypeScript, PrimeNG, NgRx Signals, RxJS, Chart.js | Interfaz, formularios, rutas protegidas, estado reactivo y visualizacion. |
| Backend | Java 26, Spring Boot 4.0.5, Spring Security, Spring WebMVC, Spring Integration MQTT | API REST, seguridad, negocio energetico, ingesta MQTT y WebSocket STOMP. |
| Base de datos | PostgreSQL 17 + TimescaleDB | Persistencia relacional y serie temporal `readings`. |
| Broker IoT | Eclipse Mosquitto 2.1.2 | Recepcion de mensajes MQTT del Shelly. |
| Despliegue | Docker Compose + Nginx | Orquestacion de servicios y proxy HTTPS. |

La comunicacion principal es REST con JSON bajo `/api/v1`. La telemetria en vivo se publica por STOMP sobre WebSocket en `/ws-iot`.

### 3.3. Diseno de interfaz

La interfaz se organiza en:

- **Login y registro:** formularios publicos y entrada OAuth2.
- **Layout principal:** contenedor con navegacion lateral y logout.
- **Dashboard:** selector de dispositivo, grafica de potencia, coste del dia y consumo fantasma.
- **Dispositivos:** gestion de dispositivos fisicos y simulados.
- **Tarifas:** catalogo maestro y contrato privado del usuario.
- **Alertas:** listado de incidencias con opcion de descartarlas.

Los wireframes textuales y flujos visuales estan recogidos en `docs-canvas` para poder copiarlos a la memoria o recrearlos en una herramienta grafica.

### 3.4. Relacion entre historias y diseno

| Historia | Tablas principales | Codigo relacionado |
| --- | --- | --- |
| HU-01 | `users`, `roles`, `federated_identities` | `AuthController`, `SecurityConfig`, `SessionStorageService`, `authGuard`. |
| HU-02 | `devices` | `DeviceController`, `DeviceService`, `DevicesComponent`, `TelemetryStore`. |
| HU-03 | `readings` | `MqttConfig`, `DeviceMessageHandler`, `TelemetryBroadcaster`, `WebsocketService`. |
| HU-04 | `tariffs`, `periods`, `tariff_contracted_powers` | `UserTariffController`, `TariffStore`, `TariffComponent`. |
| HU-05 | `readings`, `tariffs`, `periods` | `ConsumptionController`, `ConsumptionService`, `DashboardComponent`. |
| HU-06 | `readings`, `tariffs` | `ConsumptionService.calculateGhostCost`. |
| HU-07 | `alerts`, `tariff_contracted_powers` | `AlertService`, `AlertController`, `AlertsComponent`. |
| HU-09 | `devices`, `readings` | `IotTelemetrySimulationJob`, `SimulatedTelemetryProcessor`, perfiles de simulacion. |

## 4. Fase 3: Implementacion y desarrollo

### 4.1. Tecnologias utilizadas

| Area | Versiones declaradas |
| --- | --- |
| Java | 26 (`pom.xml`) |
| Spring Boot | 4.0.5 |
| MapStruct | 1.6.3 |
| JWT | `jjwt` 0.12.5 |
| MQTT | Spring Integration MQTT, Eclipse Paho 1.2.5, HiveMQ client 1.3.13 |
| Angular | 21.x |
| TypeScript | 5.9.x |
| RxJS | 7.8.x |
| NgRx Signals | 21.1.x |
| PrimeNG | 21.1.x |
| TimescaleDB | `timescale/timescaledb-ha:pg17` |
| Mosquitto | 2.1.2 Alpine |

### 4.2. Desarrollo del backend

El backend se estructura por controladores, servicios, repositorios, entidades, DTOs y mappers. La seguridad es stateless: cada peticion protegida debe incluir `Authorization: Bearer <jwt>`. La API evita exponer `userId` en operaciones sensibles como la tarifa privada, usando siempre el `Principal` del token.

La logica energetica se concentra en servicios:

- `ReadingService` persiste lecturas reales y simuladas.
- `ConsumptionService` calcula coste por delta positivo de kWh acumulado.
- `CalendarResolverService` resuelve el periodo P1-P6 segun peaje, zona, mes, tipo de dia y hora local.
- `AlertService` compara potencia instantanea contra potencia contratada del periodo.
- `DeviceService` gestiona propiedad, simuladores y borrado en cascada de lecturas/alertas.

Los controladores y DTOs REST se detallan en [`anexo-a-backend-rest.md`](anexo-a-backend-rest.md).

### 4.3. Desarrollo del frontend

Angular usa componentes standalone y rutas con lazy loading. La sesion se guarda en `sessionStorage` y el interceptor adjunta el JWT a las peticiones `/api/v1`. El estado mas importante no se deja repartido entre componentes, sino que se concentra en stores:

- `TelemetryStore`: dispositivos, MAC seleccionada e historial de potencia por dispositivo.
- `TariffStore`: catalogo maestro, tarifa privada y errores de carga/guardado.

El uso de `switchMap` en el flujo WebSocket es una decision importante: al cambiar de dispositivo se cancela la suscripcion anterior y se evita mezclar lecturas de distintas MAC en la grafica.

El frontend se documenta con mas detalle en [`anexo-b-frontend-angular.md`](anexo-b-frontend-angular.md).

### 4.4. Control de versiones

El historial reciente muestra un flujo basado en commits descriptivos:

- Correcciones de despliegue (`fix(deploy)`, `fix(nginx)`, `fix(config)`).
- Evolucion funcional de simuladores (`feat(devices)`, `feat(prod)`).
- Ajustes de CI y documentacion de produccion.

Para esta entrega se anade documentacion tecnica sin modificar comportamiento de la aplicacion.

## 5. Fase 4: Pruebas y despliegue

### 5.1. Plan de pruebas

| Prueba | Criterio validado | Resultado esperado |
| --- | --- | --- |
| Login con credenciales validas | HU-01 | Se recibe JWT y se navega a dashboard. |
| Ruta privada sin token | HU-01 | `authGuard` redirige a `/login`. |
| Crear simulador | HU-09 | API devuelve `DeviceDto` con `simulated=true` y perfil asignado. |
| Seleccionar dispositivo en dashboard | HU-03 | Se carga historial reciente y se abre stream STOMP de esa MAC. |
| Calcular coste sin tarifa | HU-05 | El servicio devuelve `0` y no rompe la vista. |
| Calcular coste con tarifa | HU-05 | Se suman deltas positivos de kWh por precio del periodo. |
| Ventana fantasma | HU-06 | Solo se consideran lecturas entre 00:00 y 05:59 hora local. |
| Superar potencia contratada | HU-07 | Se crea alerta `OVERPOWER` y aparece en `/alerts`. |
| Mutar tarifa sin admin | HU-08 | Spring Security responde 403. |
| Borrar dispositivo | HU-02 | Se eliminan lecturas y alertas relacionadas antes de borrar el dispositivo. |

El repositorio incluye tests de servicios como `ConsumptionServiceTest`, `TariffServiceTest`, `UserTariffServiceTest`, `DeviceServiceTest`, `IotTelemetrySimulationJobTest` y tests de componentes Angular.

### 5.2. Manual de instalacion y uso tecnico

Pasos principales para desarrollo local:

```bash
# Backend
cd backend
./mvnw test
./mvnw spring-boot:run

# Frontend
cd frontend
npm install
npm start

# Stack completo
docker compose up -d --build
```

Scripts SQL relevantes:

1. `backend/src/main/resources/db/dev-seed/00-extensions.sql`
2. `backend/src/main/resources/db/dev-seed/01-hypertable.sql`
3. `backend/src/main/resources/db/tariffs-td-schema.sql`
4. `backend/src/main/resources/db/seed-tariff-calendar-slots.sql`
5. Seeds de usuarios y dispositivos en `db/dev-seed`.

### 5.3. Despliegue

El despliegue de produccion se apoya en `docker-compose.yml`:

- TimescaleDB no expone puerto al host.
- Mosquitto expone `1883` para el Shelly fisico, aunque el propio compose marca TLS como deuda tecnica.
- Backend se conecta a `timescaledb` y `mosquitto` por nombres de servicio internos.
- Frontend se sirve desde un contenedor Nginx interno.
- Nginx principal publica los puertos 80 y 443 y monta certificados de Let's Encrypt.

La guia operacional esta en [`../deployment/hetzner-production.md`](../deployment/hetzner-production.md).

## 6. Conclusiones y lineas futuras

### 6.1. Grado de cumplimiento

El MVP esta cubierto: autenticacion, dispositivos, telemetria, dashboard, tarifas, analitica, alertas y despliegue estan presentes en codigo. Ademas, se ha anadido simulacion IoT para que el proyecto pueda demostrarse sin depender siempre del hardware fisico.

### 6.2. Dificultades tecnicas

- **Telemetria asincroma:** se resolvio con Spring Integration MQTT y canales internos, separando `events/rpc` y `status/switch:0`.
- **Series temporales:** Hibernate crea la tabla, pero TimescaleDB requiere un script posterior para convertir `readings` en hypertable.
- **Tarifas TD:** el coste no depende solo del kWh, sino del periodo, zona geografica, mes, tipo de dia y hora.
- **Tiempo real frontend:** se uso STOMP y `switchMap` para evitar suscripciones duplicadas al cambiar de dispositivo.
- **Despliegue:** se corrigieron detalles de Nginx, OAuth2 y nombres de variables para que Docker y GitHub Actions no chocasen con configuraciones reservadas.

### 6.3. Mejoras futuras

- Soportar varios Shelly fisicos mediante topics MQTT dinamicos o wildcard multi-dispositivo.
- Usar funciones especificas de TimescaleDB como `time_bucket`, compresion, politicas de retencion y agregados continuos.
- Anadir TLS al broker MQTT o aislar el puerto 1883 mediante VPN.
- Normalizar todas las respuestas 403/401 con el mismo formato `ErrorResponse`.
- Crear comandos MQTT outbound para controlar el enchufe desde la aplicacion.
- Preparar una app movil o PWA para consultar alertas fuera del panel web.

## 7. Bibliografia y recursos

- Documentacion oficial de Spring Boot, Spring Security y Spring Integration MQTT.
- Documentacion de Angular, RxJS y NgRx Signals.
- Documentacion de TimescaleDB sobre hypertables.
- Documentacion de Eclipse Mosquitto.
- Circular CNMC 3/2020 para periodos tarifarios TD.
- Documentacion oficial de PrimeNG y Chart.js.

## Anexos tecnicos generados

- [Anexo A. Backend REST Spring Boot](anexo-a-backend-rest.md)
- [Anexo B. Frontend Angular, RxJS y NgRx Signals](anexo-b-frontend-angular.md)
- [Anexo C. Ingesta MQTT y simulacion IoT](anexo-c-telemetria-mqtt.md)
- [Anexo D. TimescaleDB y analitica energetica](anexo-d-timescaledb-analitica.md)
- [Diagramas en docs-canvas](../../docs-canvas/arquitectura-wattimizer.md)

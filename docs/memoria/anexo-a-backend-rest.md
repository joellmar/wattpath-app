# Anexo A. Backend REST con Spring Boot

## 1. Vision general del backend

El backend esta en `backend/src/main/java/com/joselumartos/jwtauthbackenddemo` y
usa Spring Boot 4.0.5. La API sigue el prefijo `/api/v1` y expone recursos para
autenticacion, dispositivos, lecturas, analitica, alertas y tarifas.

La estructura principal es:

| Capa | Paquete | Responsabilidad |
| --- | --- | --- |
| Controladores | `controllers` | Definen rutas HTTP y respuesta REST. |
| Servicios | `services` | Contienen reglas de negocio y coordinan repositorios. |
| DTOs | `dtos` | Separan el contrato JSON de las entidades JPA. |
| Entidades | `entities` | Modelo persistente de usuarios, dispositivos, lecturas, tarifas y alertas. |
| Repositorios | `repositories` | Acceso a datos mediante Spring Data JPA y JPQL. |
| Seguridad | `security`, `config` | JWT, OAuth2, CORS, roles y filtros. |
| Mappers | `mappers` | Transformacion entre DTOs MQTT/REST y entidades. |

## 2. Seguridad aplicada a la API

La clase `SecurityConfig` configura una API sin estado (`SessionCreationPolicy.STATELESS`),
desactiva CSRF y permite CORS desde `app.cors.allowed-origins`. El filtro
`JwtValidatorFilter` valida tokens `Bearer` antes de `BasicAuthenticationFilter`.

Rutas publicas:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/register/admin`
- `POST /api/v1/auth/oauth/exchange`
- `/oauth2/authorization/**`
- `/login/oauth2/code/**`
- `/ws-iot/**`

Reglas destacadas:

- `GET /api/v1/tariffs/**` requiere autenticacion.
- Escritura en `/api/v1/tariffs/**` requiere `ROLE_ADMIN`.
- El resto de endpoints requiere usuario autenticado.

El JWT generado por `JwtTokenService` incluye:

| Claim | Significado |
| --- | --- |
| `username` | Email usado como identificador funcional. |
| `authorities` | Roles separados por coma, por ejemplo `ROLE_USER`. |
| `issuer` | Valor fijo `store-security`. |
| `exp` | Expiracion a 8 horas. |

## 3. Controladores REST activos

### 3.1. `AuthController`

Archivo: `controllers/AuthController.java`
Ruta base: `/api/v1/auth`

| Metodo | Ruta | Entrada | Salida | Estado esperado |
| --- | --- | --- | --- | --- |
| `POST` | `/login` | `LoginUser` | `LoginUserJwt` | `200 OK`; credenciales invalidas `401`. |
| `POST` | `/register` | `RegisterRequest` | Sin cuerpo | `201 Created`. |
| `POST` | `/register/admin` | `RegisterRequest` + cabecera `X-Wattimizer-Admin-Secret` | Sin cuerpo | `201 Created`; secreto incorrecto `403`. |
| `POST` | `/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | `200 OK`; ticket invalido `400`. |

La decision importante es separar el login clasico del flujo OAuth2. OAuth2 no
devuelve el JWT directamente en la redireccion, sino un ticket temporal de un solo
uso. Angular llama despues a `/oauth/exchange`, lo que evita dejar el JWT final
expuesto en la URL del navegador.

### 3.2. `DeviceController`

Archivo: `controllers/DeviceController.java`
Ruta base: `/api/v1/devices`

| Metodo | Ruta | Parametros | Entrada | Salida |
| --- | --- | --- | --- | --- |
| `GET` | `/` | `Principal` | - | `List<DeviceDto>` del usuario autenticado. |
| `GET` | `/{id}` | `id` | - | `DeviceDto` si pertenece al usuario. |
| `POST` | `/` | - | `DeviceDto` | `DeviceDto` creado. |
| `POST` | `/claim` | `Principal` | `DeviceDto` con `macAddress` y `name` | Dispositivo reclamado. |
| `POST` | `/simulated/demo-pack` | `Principal` | - | Lista de simuladores creados. |
| `POST` | `/simulated` | `Principal` | `CreateSimulatedDeviceRequest` | Simulador creado. |
| `PUT` | `/{id}` | `id`, `Principal` | `DeviceDto` | Dispositivo actualizado. |
| `DELETE` | `/{id}` | `id`, `Principal` | - | `204 No Content`. |

El controlador valida propiedad en `GET /{id}` y `DELETE /{id}` comparando
`device.username()` con `principal.getName()`. En `PUT /{id}` esa comprobacion se
delega en `DeviceService.updateDevice`.

Nota tecnica: `POST /api/v1/devices` no recibe `Principal`; por tanto se apoya en
lo que venga en el DTO. Para el uso normal de la interfaz se prefieren
`/claim` y `/simulated`, que si usan el usuario autenticado.

### 3.3. `ReadingController`

Archivo: `controllers/ReadingController.java`
Ruta base: `/api/v1/readings`

| Metodo | Ruta | Parametros | Salida |
| --- | --- | --- | --- |
| `GET` | `/` | `Principal` | Todas las lecturas de dispositivos del usuario. |
| `GET` | `/latest/{macAddress}` | `macAddress` | Ultima `ReadingResponse`. |
| `GET` | `/device/{macAddress}/recent` | `macAddress`, query `seconds` por defecto `120` | Lista reciente de lecturas. |
| `GET` | `/search` | query `time` ISO y `macAddress` | Lectura por clave compuesta. |
| `DELETE` | `/search` | query `time` ISO y `macAddress` | Borrado por clave compuesta. |

Antes de consultar por MAC, el controlador recupera el dispositivo y valida que el
propietario sea el usuario del JWT. Esto es clave porque la MAC va en la URL y no
debe permitir acceder a datos de otro cliente.

### 3.4. `ConsumptionController`

Archivo: `controllers/ConsumptionController.java`
Ruta base: `/api/v1/analytics`

| Metodo | Ruta | Query params | Respuesta |
| --- | --- | --- | --- |
| `GET` | `/cost` | `macAddress`, `start`, `end` | `macAddress`, `totalCostEur`, `start`, `end`. |
| `GET` | `/ghost-consumption` | `macAddress`, `start`, `end` | `macAddress`, `ghostCostEur`, `start`, `end`. |

Ambos endpoints verifican propiedad del dispositivo. El resultado se devuelve como
`Map<String,Object>` en vez de un DTO especifico. La logica economica no esta en
el controlador, sino en `ConsumptionService`.

### 3.5. `AlertController`

Archivo: `controllers/AlertController.java`
Ruta base: `/api/v1/alerts`

| Metodo | Ruta | Parametros | Salida |
| --- | --- | --- | --- |
| `GET` | `/` | `Principal` | `List<AlertDto>` del usuario. |
| `DELETE` | `/{id}` | `id`, `Principal` | `204 No Content` si se borra. |

El borrado se hace mediante `AlertService.deleteAlertForUser`, por lo que el ID
solo se elimina si la alerta pertenece al usuario autenticado.

### 3.6. `TariffController`

Archivo: `controllers/TariffController.java`
Ruta base: `/api/v1/tariffs`

| Metodo | Ruta | Seguridad | Entrada | Salida |
| --- | --- | --- | --- | --- |
| `GET` | `/` | Usuario autenticado | - | `List<TariffDto>`. |
| `GET` | `/{id}` | Usuario autenticado | `id` | `TariffDto`. |
| `POST` | `/` | `ROLE_ADMIN` | `TariffDto` | Tarifa creada. |
| `POST` | `/{id}` | `ROLE_ADMIN` | `id`, `TariffDto` | Tarifa actualizada. |
| `DELETE` | `/{id}` | `ROLE_ADMIN` | `id` | `204 No Content`. |

La actualizacion usa `POST /{id}` en vez de `PUT`. Es una decision atipica para
REST, pero el frontend esta adaptado a ella en `TariffService.updateCatalogTariff`.

### 3.7. `UserTariffController`

Archivo: `controllers/UserTariffController.java`
Ruta base: `/api/v1/users/me/tariff`

| Metodo | Ruta | Entrada | Salida |
| --- | --- | --- | --- |
| `GET` | `/` | - | `TariffDto` si hay tarifa, `204` si no hay. |
| `POST` | `/` | `UserTariffRequest` | Tarifa privada guardada. |
| `DELETE` | `/` | - | `204 No Content`. |

Este controlador no acepta `userId`. La identidad siempre sale del `Principal`,
lo que reduce el riesgo de IDOR, porque un usuario no puede indicar en la URL que
quiere modificar la tarifa de otro.

## 4. DTOs principales

### 4.1. Autenticacion

| DTO | Campos | Uso |
| --- | --- | --- |
| `LoginUser` | `username`, `password` | Entrada de login. |
| `LoginUserJwt` | `statusCode`, `jwt` | Respuesta del login y del intercambio OAuth2. |
| `RegisterRequest` | `username`, `password`, `confirmPassword`, `tariffId` | Registro de usuario/admin. |
| `OAuthTicketExchangeRequest` | `ticket` | Intercambio de ticket temporal por JWT. |
| `ErrorResponse` | `status`, `error`, `message`, `timestamp` | Cuerpo normalizado de errores. |

### 4.2. Dispositivos

`DeviceDto` representa el dispositivo que viaja por REST:

| Campo | Tipo | Significado |
| --- | --- | --- |
| `id` | `Long` | Identificador interno. |
| `username` | `String` | Propietario funcional. |
| `name` | `String` | Nombre visible en interfaz. |
| `macAddress` | `String` | Identificador fisico/logico del enchufe. |
| `isOn` | `Boolean` | Estado del interruptor. |
| `simulated` | `Boolean` | Indica si lo alimenta el simulador. |
| `simulationProfile` | `SimulationProfile` | Perfil de consumo simulado. |

`CreateSimulatedDeviceRequest` recibe `name` y `simulationProfile`.

### 4.3. Lecturas y alertas

`ReadingResponse` es el contrato comun para REST y WebSocket:

| Campo | Tipo | Significado |
| --- | --- | --- |
| `time` | `Instant` | Instante de la lectura. |
| `macAddress` | `String` | Dispositivo asociado. |
| `powerW` | `BigDecimal` | Potencia activa instantanea en vatios. |
| `energyTotalKwh` | `BigDecimal` | Energia acumulada del contador. |
| `isOn` | `Boolean` | Estado ON/OFF si el origen lo aporta. |

`AlertDto` contiene `id`, `macAddress`, `username`, `type`, `message` y
`createdAt`. El tipo generado actualmente por `AlertService` es `OVERPOWER`.

### 4.4. Tarifas

`TariffDto` agrupa la informacion contractual:

| Campo | Tipo | Significado |
| --- | --- | --- |
| `id` | `Long` | Identificador. |
| `name` | `String` | Nombre comercial o interno. |
| `market` | `String` | Mercado. |
| `accessTariffCode` | `String` | Peaje: `2.0TD`, `3.0TD`, `6.1TD`, `6.2TD`. |
| `geographicZone` | `String` | Zona usada para calendario horario. |
| `energyCompany` | `String` | Comercializadora. |
| `periods` | `List<PeriodDto>` | Precio por kWh en P1-P6. |
| `contractedPowers` | `List<TariffContractedPowerDto>` | Potencia contratada por periodo. |

`UserTariffRequest` admite dos caminos:

- `templateTariffId`: clonar una tarifa de catalogo.
- `contract`: crear o actualizar una tarifa privada del usuario.

## 5. Servicios de negocio

### 5.1. Autenticacion y registro

`AuthRegistrationService` valida email, contrasena minima y confirmacion antes de
crear `UserEntity`. Para usuarios normales asigna `ROLE_USER`; para registro
administrativo asigna `ROLE_ADMIN` despues de comprobar el secreto en el
controlador.

`JwtTokenService` centraliza la generacion del JWT para que el login local y OAuth2
usen el mismo formato de token.

`OAuth2LoginTicketService` mantiene tickets en memoria con TTL de 60 segundos. Se
elige este paso intermedio porque es mas seguro que redirigir al frontend con el
JWT final en la URL.

### 5.2. Dispositivos

`DeviceService` concentra reglas de multitenencia:

- `listByUsername` lista solo dispositivos del usuario.
- `claimOrRegisterDevice` permite reclamar dispositivos sin propietario o de
  `SYSTEM`, pero no de otro usuario real.
- `createSimulatedDevice` genera dispositivos simulados con MAC `SIM...`.
- `deleteById` elimina lecturas y alertas relacionadas antes de borrar el
  dispositivo.

### 5.3. Lecturas

`ReadingService` tiene dos responsabilidades:

1. Consultar lecturas para REST.
2. Persistir lecturas procedentes de MQTT o simulacion.

El metodo `listByUsername` filtra en memoria despues de `findAll()`. Funciona para
el proyecto, pero es una zona mejorable si el historico crece mucho.

### 5.4. Tarifas

`TariffService` valida que los periodos y potencias sean coherentes con el peaje
TD. Tambien impide borrar tarifas que esten asignadas a usuarios.

`UserTariffService` crea la tarifa privada de cada usuario. La decision de clonar
plantillas evita que un usuario modifique el catalogo global al personalizar
precios o potencias.

### 5.5. Analitica y alertas

`ConsumptionService` calcula costes usando deltas de `energyTotalKwh`; no estima
el consumo total solo desde potencia instantanea. Este enfoque es mas estable
porque aprovecha el contador acumulado del Shelly.

`AlertService` compara `powerW / 1000` con `contractedPowerKw` del periodo actual.
Si se supera el limite, guarda una alerta y la publica por WebSocket mediante
`TelemetryBroadcaster`.

## 6. Manejo de errores

`GlobalExceptionHandler` traduce excepciones habituales a respuestas HTTP:

| Excepcion | Estado |
| --- | --- |
| `EntityNotFoundException` | `404 Not Found` |
| `BadCredentialsException` | `401 Unauthorized` |
| `IllegalStateException` | `400 Bad Request` |
| `UsernameNotFoundException` | `401 Unauthorized` |
| `ForbiddenException` | `403 Forbidden` |
| `DataIntegrityViolationException` | `400`, `409` o `500` segun el mensaje |
| `Exception` | `500 Internal Server Error` |

Hay endpoints que devuelven `403` sin cuerpo directamente desde el controlador.
Esto convive con el handler global y explica por que no todos los errores tienen
la misma forma JSON.

## 7. Observaciones tecnicas

- El filtro JWT excluye solo el path exacto `/api/v1/auth/`, por lo que tambien se
  ejecuta en rutas publicas como `/api/v1/auth/login`. No bloquea si no hay token,
  pero la exclusion parece mas estrecha de lo previsto.
- `POST /api/v1/tariffs/{id}` actualiza tarifas aunque lo habitual seria `PUT`.
- `DeviceCommandController`, `DeviceStateController` y
  `ReactiveDeviceStateController` estan comentados y no forman parte de la API viva.
- La seguridad WebSocket permite `/ws-iot/**` sin JWT; la proteccion real esta en
  no exponer operaciones de escritura STOMP, pero las suscripciones a topics
  podrian endurecerse en una version futura.

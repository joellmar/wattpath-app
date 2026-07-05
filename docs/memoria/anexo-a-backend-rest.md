# Anexo A. Controladores REST de Spring Boot

Este anexo documenta la API REST real implementada en `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers`. Todas las rutas de negocio usan el prefijo `/api/v1`.

## 1. Seguridad comun de la API

La configuracion principal esta en `SecurityConfig`.

- La sesion del backend es **stateless** (`SessionCreationPolicy.STATELESS`).
- Las rutas protegidas requieren `Authorization: Bearer <jwt>`.
- `JwtValidatorFilter` valida el token y rellena el `SecurityContext`.
- Las mutaciones del catalogo de tarifas requieren `ROLE_ADMIN`.
- CORS se configura desde `app.cors.allowed-origins` y en produccion desde `APP_CORS_ALLOWED_ORIGINS`.

Rutas publicas:

```text
POST /api/v1/auth/login
POST /api/v1/auth/register
POST /api/v1/auth/register/admin
POST /api/v1/auth/oauth/exchange
GET  /oauth2/authorization/**
GET  /login/oauth2/code/**
GET  /ws-iot/**
```

Formato habitual de error:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Mensaje de negocio",
  "timestamp": "2026-07-05T22:00:00"
}
```

Hay una excepcion importante: algunos controladores devuelven `403` vacio directamente con `ResponseEntity.status(HttpStatus.FORBIDDEN).build()`, por ejemplo al intentar consultar una MAC de otro usuario.

## 2. `AuthController` - autenticacion

**Base path:** `/api/v1/auth`

### `POST /login`

Autentica usuario y devuelve un JWT.

| Elemento | Detalle |
| --- | --- |
| DTO entrada | `LoginUser` |
| Campos entrada | `username`, `password` |
| DTO salida | `LoginUserJwt` |
| Campos salida | `statusCode`, `jwt` |
| Servicio | `UserProviderDetailsManager`, `JwtTokenService` |
| Error principal | `BadCredentialsException` -> 401 |

Ejemplo:

```json
{
  "username": "admin@wattimizer.dev",
  "password": "secret"
}
```

### `POST /register`

Registra un usuario normal.

| Elemento | Detalle |
| --- | --- |
| DTO entrada | `RegisterRequest` |
| Campos entrada | `username`, `password`, `confirmPassword`, `tariffId` opcional |
| Respuesta | 201 sin cuerpo |
| Servicio | `AuthRegistrationService.registerUser` |

La intencion de `tariffId` es permitir que el alta pueda quedar asociada a una tarifa inicial si se elige una plantilla del catalogo.

### `POST /register/admin`

Crea un administrador.

| Elemento | Detalle |
| --- | --- |
| Header | `X-Wattimizer-Admin-Secret` |
| DTO entrada | `RegisterRequest` |
| Respuesta | 201 sin cuerpo |
| Servicio | `AuthRegistrationService.registerAdmin` |
| Error | Secreto incorrecto -> `ForbiddenException` 403 |

La decision de usar un header secreto evita publicar un endpoint de alta admin abierto. No sustituye a una consola interna, pero es suficiente para el alcance del proyecto.

### `POST /oauth/exchange`

Canjea un ticket OAuth2 temporal por un JWT propio de Wattimizer.

| Elemento | Detalle |
| --- | --- |
| DTO entrada | `OAuthTicketExchangeRequest` |
| Campos entrada | `ticket` |
| DTO salida | `LoginUserJwt` |
| Servicio | `OAuth2LoginTicketService.consumeTicket` |

El ticket es de un solo uso y tiene vida corta. Asi el frontend no recibe directamente datos internos del login social hasta que el backend valida el flujo.

## 3. `DeviceController` - dispositivos

**Base path:** `/api/v1/devices`

DTO principal:

```json
{
  "id": 1,
  "username": "usuario@wattimizer.dev",
  "name": "Nevera",
  "macAddress": "SIM000000001",
  "isOn": true,
  "simulated": true,
  "simulationProfile": "FRIDGE"
}
```

### Endpoints

| Metodo y ruta | Entrada | Salida | Uso |
| --- | --- | --- | --- |
| `GET /api/v1/devices` | JWT | `List<DeviceDto>` | Lista los dispositivos del usuario autenticado. |
| `GET /api/v1/devices/{id}` | `id` path | `DeviceDto` | Devuelve un dispositivo si pertenece al usuario. |
| `POST /api/v1/devices` | `DeviceDto` body | `DeviceDto` 201 | Alta manual de dispositivo. |
| `POST /api/v1/devices/claim` | `DeviceDto` body con `macAddress`, `name` | `DeviceDto` | Reclama una MAC para el usuario del JWT. |
| `POST /api/v1/devices/simulated` | `CreateSimulatedDeviceRequest` | `DeviceDto` 201 | Crea un simulador individual. |
| `POST /api/v1/devices/simulated/demo-pack` | JWT | `List<DeviceDto>` 201 | Crea un conjunto de simuladores de demostracion. |
| `PUT /api/v1/devices/{id}` | `DeviceDto` body | `DeviceDto` | Actualiza nombre, estado y perfil simulado. |
| `DELETE /api/v1/devices/{id}` | `id` path | 204 | Borra dispositivo tras limpiar lecturas y alertas asociadas. |

DTO para simulacion:

```json
{
  "name": "Horno simulado",
  "simulationProfile": "OVEN"
}
```

Perfiles disponibles segun el enum `SimulationProfile`:

```text
SINE_WAVE, OVEN, WASHING_MACHINE, TELEVISION, FAN,
DESKTOP_PC, FRIDGE, STANDBY, CONSTANT_HIGH_LOAD
```

Decision de diseno: los endpoints de consulta y borrado comparan `device.username()` con `principal.getName()` para evitar acceso a dispositivos ajenos. El endpoint `POST /devices` acepta el `DeviceDto` recibido y no fuerza el usuario desde el `Principal`; para uso normal el frontend utiliza `claim` o simuladores, que si toman el usuario del token.

## 4. `ReadingController` - lecturas energeticas

**Base path:** `/api/v1/readings`

DTO salida:

```json
{
  "time": "2026-07-05T22:00:00Z",
  "macAddress": "SIM000000001",
  "powerW": 125.40,
  "energyTotalKwh": 2.4581,
  "isOn": true
}
```

| Metodo y ruta | Parametros | Salida | Servicio |
| --- | --- | --- | --- |
| `GET /api/v1/readings` | JWT | `List<ReadingResponse>` | `ReadingService.listByUsername` |
| `GET /api/v1/readings/latest/{macAddress}` | MAC path | `ReadingResponse` | `ReadingService.findByDevice` |
| `GET /api/v1/readings/device/{macAddress}/recent` | `seconds` query, default `120` | `List<ReadingResponse>` | `ReadingService.listRecentByMacAddress` |
| `GET /api/v1/readings/search` | `time`, `macAddress` query | `ReadingResponse` | `ReadingService.findByTimeAndMacAddress` |
| `DELETE /api/v1/readings/search` | `time`, `macAddress` query | 204 | `ReadingService.deleteByTimeAndMacAddress` |

La clave funcional de una lectura es compuesta: instante (`time`) y dispositivo. Por eso las busquedas concretas piden `time` y `macAddress`.

## 5. `ConsumptionController` - analitica energetica

**Base path:** `/api/v1/analytics`

### `GET /cost`

Calcula el coste de energia de una MAC en un intervalo.

| Query param | Tipo | Descripcion |
| --- | --- | --- |
| `macAddress` | `String` | Dispositivo a analizar. |
| `start` | `Instant` ISO-8601 | Inicio del intervalo. |
| `end` | `Instant` ISO-8601 | Fin del intervalo. |

Respuesta:

```json
{
  "macAddress": "SIM000000001",
  "totalCostEur": 1.24,
  "start": "2026-07-05T00:00:00Z",
  "end": "2026-07-05T22:00:00Z"
}
```

El servicio no multiplica potencia instantanea sin mas. Recorre lecturas ordenadas, calcula deltas positivos de `energyTotalKwh` y aplica el precio del periodo tarifario resuelto para cada instante.

### `GET /ghost-consumption`

Misma entrada que `/cost`, pero la suma se limita a lecturas entre las 00:00 y las 05:59 hora local del contrato. Esta ventana no se confunde con el periodo valle regulatorio: se usa para detectar aparatos que siguen consumiendo cuando el negocio deberia estar inactivo.

Respuesta:

```json
{
  "macAddress": "SIM000000001",
  "ghostCostEur": 0.18,
  "start": "2026-07-05T00:00:00Z",
  "end": "2026-07-05T22:00:00Z"
}
```

## 6. `TariffController` - catalogo maestro

**Base path:** `/api/v1/tariffs`

DTO principal:

```json
{
  "id": 1,
  "name": "Tarifa 3.0TD Base",
  "market": "REGULATED",
  "accessTariffCode": "3.0TD",
  "geographicZone": "PENINSULA",
  "energyCompany": "Wattimizer",
  "periods": [
    { "id": 1, "periodCode": "P1", "priceKwh": 0.20 }
  ],
  "contractedPowers": [
    { "id": 1, "periodCode": "P1", "contractedPowerKw": 4.60 }
  ]
}
```

| Metodo y ruta | Entrada | Salida | Seguridad |
| --- | --- | --- | --- |
| `GET /api/v1/tariffs` | JWT | `List<TariffDto>` | Usuario autenticado |
| `GET /api/v1/tariffs/{id}` | `id` path | `TariffDto` | Usuario autenticado |
| `POST /api/v1/tariffs` | `TariffDto` | `TariffDto` 201 | `ROLE_ADMIN` |
| `POST /api/v1/tariffs/{id}` | `TariffDto` | `TariffDto` | `ROLE_ADMIN` |
| `DELETE /api/v1/tariffs/{id}` | `id` path | 204 | `ROLE_ADMIN` |

El catalogo maestro excluye clones privados de usuario. Esto separa las plantillas comunes de los contratos individuales.

## 7. `UserTariffController` - tarifa privada del usuario

**Base path:** `/api/v1/users/me/tariff`

Este controlador esta disenado para evitar IDOR: no recibe `userId` en ruta ni en cuerpo. El usuario se obtiene siempre desde el `Principal`.

| Metodo y ruta | Entrada | Salida | Uso |
| --- | --- | --- | --- |
| `GET /api/v1/users/me/tariff` | JWT | `TariffDto` o 204 | Consulta la tarifa privada activa. |
| `POST /api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` | Crea o actualiza tarifa privada. |
| `DELETE /api/v1/users/me/tariff` | JWT | 204 | Desvincula la tarifa del usuario. |

`UserTariffRequest` admite varios casos:

```json
{
  "templateTariffId": 1,
  "contract": {
    "name": "Contrato de mi local",
    "accessTariffCode": "3.0TD",
    "geographicZone": "PENINSULA",
    "periods": [],
    "contractedPowers": []
  }
}
```

- Solo `templateTariffId`: clona una plantilla.
- `templateTariffId` y `contract`: clona y aplica cambios.
- Solo `contract`: crea contrato privado desde cero.
- `contract.id`: edita la tarifa privada activa si pertenece al usuario.

## 8. `AlertController` - alertas de maximetro

**Base path:** `/api/v1/alerts`

DTO:

```json
{
  "id": 10,
  "macAddress": "SIM000000009",
  "username": "usuario@wattimizer.dev",
  "type": "OVERPOWER",
  "message": "Potencia superada en periodo P1",
  "createdAt": "2026-07-05T22:00:00"
}
```

| Metodo y ruta | Entrada | Salida |
| --- | --- | --- |
| `GET /api/v1/alerts` | JWT | `List<AlertDto>` |
| `DELETE /api/v1/alerts/{id}` | `id` path | 204 |

El borrado llama a `AlertService.deleteAlertForUser(id, username)`. Si no se borra ninguna fila, se lanza `EntityNotFoundException`, porque puede que la alerta no exista o que pertenezca a otro usuario.

## 9. Controladores comentados o no activos

Existen clases relacionadas con estado reactivo o comandos (`DeviceCommandController`, `DeviceStateController`, `ReactiveDeviceStateController`) que no forman parte de la API REST activa en el estado actual. La comunicacion en tiempo real implementada para el frontend se hace por WebSocket STOMP desde `TelemetryBroadcaster`.

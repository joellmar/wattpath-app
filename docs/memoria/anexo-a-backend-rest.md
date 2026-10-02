# Anexo A: Controladores REST de Spring Boot

Este anexo documenta los controladores REST activos del backend de Wattimizer. Se han dejado fuera los controladores que aparecen comentados en el codigo, como `DeviceCommandController`, `DeviceStateController` y `ReactiveDeviceStateController`, porque no exponen funcionalidad real en la aplicacion actual.

## 1. Vision general

El backend publica su API principal bajo el prefijo `/api/v1`. La seguridad se configura en `SecurityConfig`:

- Rutas publicas: login, registro, registro de administrador, exchange OAuth2, flujo OAuth2 y endpoint WebSocket `/ws-iot`.
- Lectura de tarifas: usuario autenticado.
- Escritura del catalogo de tarifas: `ROLE_ADMIN`.
- Resto de rutas: JWT obligatorio.

La API devuelve JSON y usa DTOs de tipo `record` en Java. La propiedad de los recursos se comprueba con el `Principal` del token en operaciones sensibles, por ejemplo lecturas por MAC, dispositivos y analiticas.

## 2. DTOs principales

| DTO | Archivo | Uso |
|---|---|---|
| `LoginUser` | `dtos/LoginUser.java` | Entrada de login: `username`, `password`. |
| `LoginUserJwt` | `dtos/LoginUserJwt.java` | Salida de login y OAuth exchange: `statusCode`, `jwt`. |
| `RegisterRequest` | `dtos/RegisterRequest.java` | Registro: `username`, `password`, `confirmPassword`, `tariffId`. |
| `OAuthTicketExchangeRequest` | `dtos/OAuthTicketExchangeRequest.java` | Ticket temporal de OAuth2. |
| `DeviceDto` | `dtos/DeviceDto.java` | Dispositivo: `id`, `username`, `name`, `macAddress`, `isOn`, `simulated`, `simulationProfile`. |
| `CreateSimulatedDeviceRequest` | `dtos/CreateSimulatedDeviceRequest.java` | Alta de simulador: `name`, `simulationProfile`. |
| `ReadingResponse` | `dtos/ReadingResponse.java` | Lectura: `time`, `macAddress`, `powerW`, `energyTotalKwh`, `isOn`. |
| `AlertDto` | `dtos/AlertDto.java` | Alerta: `id`, `macAddress`, `username`, `type`, `message`, `createdAt`. |
| `TariffDto` | `dtos/TariffDto.java` | Tarifa completa: datos generales, `periods`, `contractedPowers`. |
| `PeriodDto` | `dtos/PeriodDto.java` | Periodo de energia: `periodCode`, `priceKwh`. |
| `TariffContractedPowerDto` | `dtos/TariffContractedPowerDto.java` | Potencia por periodo: `periodCode`, `contractedPowerKw`. |
| `UserTariffRequest` | `dtos/UserTariffRequest.java` | Asignacion o edicion de tarifa privada. |
| `ErrorResponse` | `dtos/ErrorResponse.java` | Error normalizado: `status`, `error`, `message`, `timestamp`. |

## 3. `AuthController`

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/AuthController.java`

### `POST /api/v1/auth/login`

Autentica usuario y contrasena con `UserProviderDetailsManager`. Si la autenticacion es valida, `JwtTokenService` emite un JWT con nombre de usuario y autoridades.

**Entrada**

```json
{
  "username": "usuario@example.com",
  "password": "********"
}
```

**Salida correcta**

```json
{
  "statusCode": "200 OK",
  "jwt": "..."
}
```

**Errores relevantes**

- `401 Unauthorized` si las credenciales no son validas.

### `POST /api/v1/auth/register`

Crea una cuenta de usuario normal. El servicio de registro valida email, contrasena y confirmacion.

**Entrada**

```json
{
  "username": "usuario@example.com",
  "password": "********",
  "confirmPassword": "********",
  "tariffId": 1
}
```

**Salida**

- `201 Created` sin cuerpo.

### `POST /api/v1/auth/register/admin`

Registra un administrador. Es una ruta publica en `SecurityConfig`, pero protegida por la cabecera `X-Wattimizer-Admin-Secret`.

**Entrada**

- Body: `RegisterRequest`.
- Header: `X-Wattimizer-Admin-Secret`.

**Salida**

- `201 Created` si la cabecera coincide con `app.admin.secret`.
- `403 Forbidden` si la cabecera no coincide.

### `POST /api/v1/auth/oauth/exchange`

Intercambia el ticket temporal de OAuth2 por un JWT de sesion. El ticket se consume una sola vez y tiene TTL corto.

**Entrada**

```json
{
  "ticket": "ticket-temporal"
}
```

**Salida**

```json
{
  "statusCode": "200 OK",
  "jwt": "..."
}
```

## 4. `DeviceController`

Archivo: `controllers/DeviceController.java`

Gestiona dispositivos fisicos y simulados. La regla principal es que las operaciones privadas se resuelven con el usuario del token. El endpoint `POST /api/v1/devices` existe, pero es mas directo y conserva el `username` enviado en `DeviceDto`; para el flujo normal de usuario se usa `claim` o `simulated`.

| Metodo | Ruta | Entrada | Salida | Intencion |
|---|---|---|---|---|
| GET | `/api/v1/devices` | JWT | `List<DeviceDto>` | Lista dispositivos del usuario autenticado. |
| GET | `/api/v1/devices/{id}` | `id` | `DeviceDto` | Recupera un dispositivo si pertenece al usuario. |
| POST | `/api/v1/devices` | `DeviceDto` | `DeviceDto` `201` | Alta directa/legacy de dispositivo. |
| POST | `/api/v1/devices/claim` | `DeviceDto` con `macAddress`, `name` | `DeviceDto` | Reclama o registra un dispositivo fisico. |
| POST | `/api/v1/devices/simulated` | `CreateSimulatedDeviceRequest` | `DeviceDto` `201` | Crea un simulador para el usuario. |
| POST | `/api/v1/devices/simulated/demo-pack` | JWT | `List<DeviceDto>` `201` | Crea un simulador por perfil que el usuario no tenga. |
| PUT | `/api/v1/devices/{id}` | `DeviceDto` | `DeviceDto` | Actualiza nombre, estado y perfil si es simulado. |
| DELETE | `/api/v1/devices/{id}` | `id` | `204` | Borra dispositivo y datos asociados. |

### Reglas de negocio destacadas

- `claimOrRegisterDevice` permite vincular una MAC nueva, sin usuario o del propio usuario.
- Si un dispositivo pertenece a otra cuenta, se lanza una regla de negocio para evitar IDOR.
- Los simuladores reciben una MAC generada con prefijo `SIM` y nueve digitos.
- `createDemoSimulatorPack` recorre todos los valores de `SimulationProfile` y evita duplicar perfiles ya existentes para ese usuario.
- `deleteById` borra lecturas y alertas antes de borrar el dispositivo. Esto evita errores por claves foraneas y mantiene coherencia al eliminar simuladores.

### Ejemplo de alta de simulador

```json
{
  "name": "Simulador horno",
  "simulationProfile": "OVEN"
}
```

## 5. `ReadingController`

Archivo: `controllers/ReadingController.java`

Expone lecturas ya persistidas. No recibe telemetria por HTTP; la telemetria real entra por MQTT o por el job de simulacion.

| Metodo | Ruta | Parametros | Salida | Uso |
|---|---|---|---|---|
| GET | `/api/v1/readings` | JWT | `List<ReadingResponse>` | Todas las lecturas de dispositivos del usuario. |
| GET | `/api/v1/readings/latest/{macAddress}` | Path `macAddress` | `ReadingResponse` | Ultima lectura de una MAC propia. |
| GET | `/api/v1/readings/device/{macAddress}/recent` | Path `macAddress`, query `seconds` default `120` | `List<ReadingResponse>` | Historico reciente para inicializar la grafica. |
| GET | `/api/v1/readings/search` | `time` ISO, `macAddress` | `ReadingResponse` | Busca una lectura por clave compuesta. |
| DELETE | `/api/v1/readings/search` | `time` ISO, `macAddress` | `204` | Borra una lectura concreta. |

El controlador comprueba que la MAC consultada pertenece al usuario autenticado. Si no coincide, devuelve `403 Forbidden` directamente.

## 6. `ConsumptionController`

Archivo: `controllers/ConsumptionController.java`

Agrupa endpoints analiticos bajo `/api/v1/analytics`. Ambos reciben `macAddress`, `start` y `end` como parametros de consulta. Las fechas se parsean como `Instant` con formato ISO-8601.

### `GET /api/v1/analytics/cost`

Calcula el coste total del periodo solicitado. La logica real esta en `ConsumptionService.calculateCostInPeriod`.

**Parametros**

| Parametro | Tipo | Descripcion |
|---|---|---|
| `macAddress` | string | MAC del dispositivo. |
| `start` | Instant ISO | Inicio del intervalo. |
| `end` | Instant ISO | Fin del intervalo. |

**Respuesta**

```json
{
  "macAddress": "9070694d3590",
  "totalCostEur": 1.23,
  "start": "2026-10-02T00:00:00Z",
  "end": "2026-10-02T22:00:00Z"
}
```

### `GET /api/v1/analytics/ghost-consumption`

Calcula el coste dentro de la ventana 00:00-05:59 hora local de la tarifa. Es una decision de producto: se usa una franja de inactividad, no se asume que coincida siempre con el periodo valle P6.

**Respuesta**

```json
{
  "macAddress": "9070694d3590",
  "ghostCostEur": 0.14,
  "start": "2026-10-02T00:00:00Z",
  "end": "2026-10-02T22:00:00Z"
}
```

## 7. `AlertController`

Archivo: `controllers/AlertController.java`

| Metodo | Ruta | Salida | Descripcion |
|---|---|---|---|
| GET | `/api/v1/alerts` | `List<AlertDto>` | Lista alertas del usuario autenticado. |
| DELETE | `/api/v1/alerts/{id}` | `204` | Descarta una alerta si pertenece al usuario. |

El borrado usa `AlertService.deleteAlertForUser(id, username)`. Si no se borra ninguna fila, se interpreta como alerta inexistente o no autorizada y se devuelve `404`.

## 8. `TariffController`

Archivo: `controllers/TariffController.java`

Este controlador gestiona el catalogo maestro de tarifas. Los usuarios autenticados pueden leerlo; solo administradores pueden mutarlo.

| Metodo | Ruta | Entrada | Salida | Rol |
|---|---|---|---|---|
| GET | `/api/v1/tariffs` | JWT | `List<TariffDto>` | Autenticado |
| GET | `/api/v1/tariffs/{id}` | Path `id` | `TariffDto` | Autenticado |
| POST | `/api/v1/tariffs` | `TariffDto` | `TariffDto` `201` | `ROLE_ADMIN` |
| POST | `/api/v1/tariffs/{id}` | `TariffDto` | `TariffDto` | `ROLE_ADMIN` |
| DELETE | `/api/v1/tariffs/{id}` | Path `id` | `204` | `ROLE_ADMIN` |

La actualizacion usa `POST /{id}` en vez de `PUT`. El frontend respeta este contrato en `TariffService.updateCatalogTariff`.

### Validaciones de tarifa

`TariffService.validateTariffContract` aplica reglas basicas:

- `2.0TD`: energia P1, P2, P3; potencia P1 y P2.
- `3.0TD`, `6.1TD`, `6.2TD`: energia y potencia P1-P6.
- En peajes de seis periodos, la potencia debe cumplir el orden `P1 <= P2 <= ... <= P6`.
- Precios y potencias deben ser mayores que cero.

## 9. `UserTariffController`

Archivo: `controllers/UserTariffController.java`

Gestiona la tarifa privada del usuario autenticado. No acepta `userId` ni en ruta ni en body; la identidad siempre sale del `Principal`. Esta decision evita que un usuario intente modificar la tarifa de otra cuenta cambiando un identificador.

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| GET | `/api/v1/users/me/tariff` | JWT | `TariffDto` o `204 No Content` |
| POST | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` |
| DELETE | `/api/v1/users/me/tariff` | JWT | `204 No Content` |

### Modos de `UserTariffRequest`

```json
{
  "templateTariffId": 1,
  "contract": null
}
```

Clona una plantilla del catalogo.

```json
{
  "templateTariffId": null,
  "contract": {
    "id": 10,
    "name": "Contrato empresa",
    "market": "libre",
    "accessTariffCode": "3.0TD",
    "geographicZone": "PENINSULA",
    "energyCompany": "Comercializadora",
    "periods": [],
    "contractedPowers": []
  }
}
```

Crea o actualiza una tarifa privada directa. Si `contract.id` existe, el servicio comprueba que coincida con la tarifa activa del usuario.

## 10. Gestion de errores

Archivo: `controllers/GlobalExceptionHandler.java`

| Excepcion | HTTP | Mensaje / criterio |
|---|---|---|
| `EntityNotFoundException` | `404` | Recurso no localizado. |
| `BadCredentialsException` | `401` | Credenciales incorrectas. |
| `UsernameNotFoundException` | `401` | Usuario no localizado en sesion. |
| `IllegalStateException` | `400` | Regla de negocio incumplida. |
| `ForbiddenException` | `403` | Acceso denegado. |
| `DataIntegrityViolationException` por `users_username_key` | `400` | Email ya registrado. |
| `DataIntegrityViolationException` por FK en `readings` o `alerts` | `409` | No se puede borrar dispositivo con datos asociados. |
| Resto de excepciones | `500` | Error interno generico. |

La respuesta mantiene una forma estable:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Descripcion del problema",
  "timestamp": "2026-10-02T22:00:00"
}
```

## 11. Relacion con el frontend

| Backend | Consumidor Angular |
|---|---|
| `/api/v1/auth/*` | `AuthService`, `LoginComponent`, `RegisterComponent`, `OAuthCallbackComponent` |
| `/api/v1/devices*` | `TelemetryStore`, `DevicesComponent` |
| `/api/v1/readings/device/{mac}/recent` | `TelemetryStore.loadRecentReadings` |
| `/api/v1/analytics/*` | `DashboardComponent` |
| `/api/v1/tariffs*` | `TariffService`, `TariffStore`, `TariffComponent` |
| `/api/v1/users/me/tariff` | `TariffService`, `TariffStore`, `DashboardComponent`, `TariffComponent` |
| `/api/v1/alerts*` | `AlertsComponent` |

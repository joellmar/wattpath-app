# Anexo A - Controladores REST de Spring Boot

## 1. Vision general

El backend de Wattimizer expone una API REST bajo el prefijo `/api/v1`. Los controladores no reciben identificadores de usuario en las rutas privadas: el usuario se obtiene del `Principal`, que Spring Security rellena tras validar el JWT. Esta decision reduce el riesgo de IDOR, porque el cliente no puede pedir datos de otro usuario cambiando un `userId`.

Controladores activos:

| Controlador | Ruta base | Responsabilidad |
|---|---|---|
| `AuthController` | `/api/v1/auth` | Login, registro, alta admin e intercambio de ticket OAuth2. |
| `DeviceController` | `/api/v1/devices` | CRUD de dispositivos fisicos y simulados. |
| `ReadingController` | `/api/v1/readings` | Consulta y borrado de lecturas. |
| `ConsumptionController` | `/api/v1/analytics` | Calculo de coste y consumo fantasma. |
| `TariffController` | `/api/v1/tariffs` | Catalogo maestro de tarifas. |
| `UserTariffController` | `/api/v1/users/me/tariff` | Tarifa privada del usuario autenticado. |
| `AlertController` | `/api/v1/alerts` | Consulta y descarte de alertas. |

Los controladores `DeviceStateController`, `ReactiveDeviceStateController` y `DeviceCommandController` estan comentados, por lo que no forman parte de la API real.

## 2. Seguridad comun

La configuracion principal esta en `SecurityConfig`:

- Endpoints publicos:
  - `/api/v1/auth/login`
  - `/api/v1/auth/register`
  - `/api/v1/auth/register/admin`
  - `/api/v1/auth/oauth/exchange`
  - `/oauth2/authorization/**`
  - `/login/oauth2/code/**`
  - `/ws-iot/**`
- Lectura de catalogo de tarifas:
  - `GET /api/v1/tariffs/**` requiere usuario autenticado.
- Escritura de catalogo:
  - `/api/v1/tariffs/**` con metodos de mutacion requiere `ROLE_ADMIN`.
- Resto de rutas:
  - requieren JWT valido.

El filtro `JwtValidatorFilter` lee la cabecera:

```http
Authorization: Bearer <jwt>
```

El JWT incluye los claims `username` y `authorities`, usados por Angular y Spring Security.

## 3. DTOs principales

### 3.1. Autenticacion

```java
public record LoginUser(
    String username,
    String password
) {}

public record LoginUserJwt(
    String statusCode,
    String jwt
) {}

public record RegisterRequest(
    String username,
    String password,
    String confirmPassword,
    Long tariffId
) {}

public record OAuthTicketExchangeRequest(String ticket) {}
```

`RegisterRequest.tariffId` es opcional. Si se informa, `AuthRegistrationService` intenta asociar esa tarifa al usuario durante el alta.

### 3.2. Dispositivos

```java
public record DeviceDto(
    Long id,
    String username,
    String name,
    String macAddress,
    Boolean isOn,
    Boolean simulated,
    SimulationProfile simulationProfile
) {}

public record CreateSimulatedDeviceRequest(
    String name,
    SimulationProfile simulationProfile
) {}
```

`simulationProfile` puede ser:

```text
SINE_WAVE, OVEN, WASHING_MACHINE, TELEVISION, FAN,
DESKTOP_PC, FRIDGE, STANDBY, CONSTANT_HIGH_LOAD
```

### 3.3. Lecturas

```java
public record ReadingResponse(
    Instant time,
    String macAddress,
    BigDecimal powerW,
    BigDecimal energyTotalKwh,
    Boolean isOn
) {}
```

La respuesta serializa `Instant` como fecha ISO-8601. En Angular se acepta como `string | number` para tolerar formatos de serializacion.

### 3.4. Tarifas

```java
public record TariffDto(
    Long id,
    String name,
    String market,
    String accessTariffCode,
    String geographicZone,
    String energyCompany,
    List<PeriodDto> periods,
    List<TariffContractedPowerDto> contractedPowers
) {}

public record PeriodDto(
    Long id,
    String periodCode,
    BigDecimal priceKwh
) {}

public record TariffContractedPowerDto(
    Long id,
    String periodCode,
    BigDecimal contractedPowerKw
) {}

public record UserTariffRequest(
    Long templateTariffId,
    TariffDto contract
) {}
```

`UserTariffRequest` admite tres usos:

1. solo `templateTariffId`: clona una plantilla del catalogo;
2. `templateTariffId` mas `contract`: clona y aplica cambios;
3. solo `contract`: crea o actualiza un contrato privado directo.

### 3.5. Alertas y errores

```java
public record AlertDto(
    Long id,
    String macAddress,
    String username,
    String type,
    String message,
    LocalDateTime createdAt
) {}

public record ErrorResponse(
    int status,
    String error,
    String message,
    LocalDateTime timestamp
) {}
```

## 4. Endpoints de autenticacion

### 4.1. Login clasico

| Campo | Valor |
|---|---|
| Metodo | `POST` |
| Ruta | `/api/v1/auth/login` |
| Entrada | `LoginUser` |
| Salida | `LoginUserJwt` |
| Seguridad | Publico |

Ejemplo:

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin@wattimizer.dev",
  "password": "password"
}
```

Respuesta:

```json
{
  "statusCode": "200 OK",
  "jwt": "<token>"
}
```

Flujo interno:

1. Se crea un `UsernamePasswordAuthenticationToken`.
2. `UserProviderDetailsManager` autentica contra los usuarios persistidos.
3. `JwtTokenService` genera un JWT de 8 horas.
4. Angular guarda el token en `sessionStorage`.

### 4.2. Registro de usuario

| Campo | Valor |
|---|---|
| Metodo | `POST` |
| Ruta | `/api/v1/auth/register` |
| Entrada | `RegisterRequest` |
| Salida | `201 Created` sin cuerpo |
| Seguridad | Publico |

`AuthRegistrationService` normaliza el email en minusculas, valida formato, longitud minima de contrasena y coincidencia con `confirmPassword`.

### 4.3. Registro de administrador

| Campo | Valor |
|---|---|
| Metodo | `POST` |
| Ruta | `/api/v1/auth/register/admin` |
| Entrada | `RegisterRequest` |
| Cabecera requerida | `X-Wattimizer-Admin-Secret` |
| Salida | `201 Created` sin cuerpo |
| Seguridad | Publico, protegido por secreto de aplicacion |

Si la cabecera no coincide con `app.admin.secret`, se lanza `ForbiddenException`.

### 4.4. Intercambio de ticket OAuth2

| Campo | Valor |
|---|---|
| Metodo | `POST` |
| Ruta | `/api/v1/auth/oauth/exchange` |
| Entrada | `OAuthTicketExchangeRequest` |
| Salida | `LoginUserJwt` |
| Seguridad | Publico |

El backend no envia el JWT directamente en la URL tras OAuth2. Primero genera un ticket temporal de un solo uso y Angular lo intercambia por el JWT definitivo.

## 5. Endpoints de dispositivos

### 5.1. Listar dispositivos del usuario

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| `GET` | `/api/v1/devices` | JWT | `List<DeviceDto>` |

Usa `deviceService.listByUsername(principal.getName())`.

### 5.2. Obtener dispositivo por id

| Metodo | Ruta | Parametros | Salida |
|---|---|---|---|
| `GET` | `/api/v1/devices/{id}` | `id: Long` | `DeviceDto` |

Antes de devolver el recurso, compara `device.username()` con `principal.getName()`. Si no coincide, responde `403 Forbidden`.

### 5.3. Crear dispositivo directo

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| `POST` | `/api/v1/devices` | `DeviceDto` | `201 Created` + `DeviceDto` |

Este endpoint persiste el DTO recibido. En la interfaz actual se usa principalmente `/claim` para dispositivos fisicos porque garantiza la asociacion con el usuario autenticado.

### 5.4. Reclamar o registrar dispositivo fisico

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| `POST` | `/api/v1/devices/claim` | `DeviceDto` con `macAddress` y `name` | `DeviceDto` |

Reglas:

- si la MAC no existe, crea el dispositivo y lo asigna al usuario;
- si existe y pertenece a `SYSTEM` o al propio usuario, lo reasigna o actualiza;
- si pertenece a otra empresa, lanza error de negocio.

### 5.5. Crear pack de simuladores

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| `POST` | `/api/v1/devices/simulated/demo-pack` | JWT | `201 Created` + `List<DeviceDto>` |

Crea como maximo un dispositivo por perfil de simulacion para el usuario. Si ya los tiene, devuelve lista vacia.

### 5.6. Crear simulador individual

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| `POST` | `/api/v1/devices/simulated` | `CreateSimulatedDeviceRequest` | `201 Created` + `DeviceDto` |

`DeviceService` genera una MAC secuencial con prefijo `SIM`, asigna `simulated=true` y guarda el perfil.

### 5.7. Actualizar dispositivo

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| `PUT` | `/api/v1/devices/{id}` | `DeviceDto` | `DeviceDto` |

Solo permite modificar dispositivos del usuario autenticado. Actualiza nombre, estado `isOn` y, si es simulado, `simulationProfile`.

### 5.8. Eliminar dispositivo

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| `DELETE` | `/api/v1/devices/{id}` | `id: Long` | `204 No Content` |

Antes de borrar el dispositivo, `DeviceService.deleteById` elimina lecturas y alertas asociadas para evitar conflictos de clave foranea.

## 6. Endpoints de lecturas

| Metodo | Ruta | Parametros | Salida | Intencion |
|---|---|---|---|---|
| `GET` | `/api/v1/readings` | JWT | `List<ReadingResponse>` | Lecturas de dispositivos del usuario. |
| `GET` | `/api/v1/readings/latest/{macAddress}` | `macAddress` | `ReadingResponse` | Ultima lectura del dispositivo. |
| `GET` | `/api/v1/readings/device/{macAddress}/recent` | `seconds`, por defecto `120` | `List<ReadingResponse>` | Historico reciente para graficas. |
| `GET` | `/api/v1/readings/search` | `time`, `macAddress` | `ReadingResponse` | Busqueda por clave compuesta. |
| `DELETE` | `/api/v1/readings/search` | `time`, `macAddress` | `204 No Content` | Borrado por clave compuesta. |

Todos los endpoints que reciben `macAddress` verifican que el dispositivo pertenezca al usuario autenticado.

Ejemplo de busqueda:

```http
GET /api/v1/readings/search?time=2026-10-03T10:00:00Z&macAddress=SIM000000001
Authorization: Bearer <jwt>
```

## 7. Endpoints de analitica

### 7.1. Coste energetico

| Metodo | Ruta | Query params | Salida |
|---|---|---|---|
| `GET` | `/api/v1/analytics/cost` | `macAddress`, `start`, `end` | `Map<String,Object>` |

Respuesta:

```json
{
  "macAddress": "SIM000000001",
  "totalCostEur": 1.42,
  "start": "2026-10-03T00:00:00Z",
  "end": "2026-10-03T23:59:59Z"
}
```

`ConsumptionService.calculateCostInPeriod` recorre lecturas ordenadas, calcula deltas positivos de `energyTotalKwh` y multiplica por el precio del periodo resuelto en ese instante.

### 7.2. Consumo fantasma

| Metodo | Ruta | Query params | Salida |
|---|---|---|---|
| `GET` | `/api/v1/analytics/ghost-consumption` | `macAddress`, `start`, `end` | `Map<String,Object>` |

Respuesta:

```json
{
  "macAddress": "SIM000000008",
  "ghostCostEur": 0.18,
  "start": "2026-10-03T00:00:00Z",
  "end": "2026-10-03T23:59:59Z"
}
```

La ventana fantasma se define como 00:00-05:59 en la zona horaria del contrato, no como el periodo valle regulatorio.

## 8. Endpoints de tarifas

### 8.1. Catalogo maestro

| Metodo | Ruta | Seguridad | Salida |
|---|---|---|---|
| `GET` | `/api/v1/tariffs` | Autenticado | `List<TariffDto>` |
| `GET` | `/api/v1/tariffs/{id}` | Autenticado | `TariffDto` |
| `POST` | `/api/v1/tariffs` | `ROLE_ADMIN` | `201 Created` + `TariffDto` |
| `POST` | `/api/v1/tariffs/{id}` | `ROLE_ADMIN` | `TariffDto` |
| `DELETE` | `/api/v1/tariffs/{id}` | `ROLE_ADMIN` | `204 No Content` |

El backend usa `POST /{id}` para actualizar, no `PUT`. Angular respeta esa decision en `TariffService.updateCatalogTariff`.

### 8.2. Tarifa privada del usuario

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| `GET` | `/api/v1/users/me/tariff` | JWT | `200 TariffDto` o `204 No Content` |
| `POST` | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` |
| `DELETE` | `/api/v1/users/me/tariff` | JWT | `204 No Content` |

Este controlador no acepta `userId`. La tarifa se busca con `principal.getName()`.

## 9. Endpoints de alertas

| Metodo | Ruta | Entrada | Salida |
|---|---|---|---|
| `GET` | `/api/v1/alerts` | JWT | `List<AlertDto>` |
| `DELETE` | `/api/v1/alerts/{id}` | `id: Long` | `204 No Content` |

El borrado usa `deleteByIdAndUserUsername`, asi que una alerta solo se puede descartar si pertenece al usuario autenticado.

## 10. Gestion de errores

`GlobalExceptionHandler` traduce excepciones a respuestas JSON:

| Excepcion | HTTP | Mensaje |
|---|---|---|
| `EntityNotFoundException` | `404` | Recurso no localizado. |
| `BadCredentialsException` | `401` | Credenciales incorrectas. |
| `UsernameNotFoundException` | `401` | Usuario no encontrado en sesion. |
| `ForbiddenException` | `403` | Acceso denegado. |
| `IllegalStateException` | `400` | Regla de negocio incumplida. |
| `DataIntegrityViolationException` con `users_username_key` | `400` | Email ya registrado. |
| `DataIntegrityViolationException` con FK de lecturas/alertas | `409` | Dispositivo con datos asociados. |
| Excepcion no controlada | `500` | Error interno de procesamiento. |

La estructura comun evita que Angular tenga que interpretar HTML o trazas de error del servidor.

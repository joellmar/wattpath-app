# Anexo A — Backend REST con Spring Boot

## 1. Visión general

El backend de Wattimizer está implementado con Spring Boot 4.0.5 y Java 26. La API se agrupa bajo `/api/v1` y utiliza JSON para entrada y salida. La estructura es clásica: controladores REST, servicios de negocio, repositorios JPA, DTOs y mappers MapStruct.

| Paquete | Responsabilidad |
|---|---|
| `controllers` | Endpoints REST, validación de propietario y códigos HTTP. |
| `services` | Reglas de negocio, transacciones y validaciones. |
| `repositories` | Consultas Spring Data JPA. |
| `dtos` | Contratos JSON de entrada y salida. |
| `mappers` | Conversión DTO-entidad. |
| `security` y `config` | JWT, OAuth2, CORS, MQTT, WebSocket y JPA. |

## 2. Seguridad de la API

`SecurityConfig` configura la aplicación como stateless: no se guarda sesión en servidor. El JWT se valida con `JwtValidatorFilter`, añadido antes de `BasicAuthenticationFilter`.

Rutas públicas:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/register/admin`
- `POST /api/v1/auth/oauth/exchange`
- `/oauth2/authorization/**`
- `/login/oauth2/code/**`
- `/ws-iot/**`

Reglas relevantes:

- Las lecturas de `/api/v1/tariffs/**` exigen autenticación.
- Las escrituras de `/api/v1/tariffs/**` exigen `ROLE_ADMIN`.
- El resto de rutas exige usuario autenticado.
- Las operaciones multitenant usan `Principal`; no se aceptan IDs de usuario desde el cliente.

## 3. Autenticación

Clase: `AuthController`
Ruta base: `/api/v1/auth`

| Método | Endpoint | Entrada | Salida | Intención |
|---|---|---|---|---|
| `POST` | `/login` | `LoginUser` | `LoginUserJwt` | Autentica usuario y contraseña. |
| `POST` | `/register` | `RegisterRequest` | `201 Created` | Registra usuario estándar. |
| `POST` | `/register/admin` | `RegisterRequest` + `X-Wattimizer-Admin-Secret` | `201 Created` | Registra admin si la cabecera coincide con la clave configurada. |
| `POST` | `/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | Intercambia ticket OAuth2 de un solo uso por JWT. |

DTOs:

```java
public record LoginUser(String username, String password) { }
public record LoginUserJwt(String statusCode, String jwt) { }
public record RegisterRequest(String username, String password, String confirmPassword, Long tariffId) { }
public record OAuthTicketExchangeRequest(String ticket) { }
```

Decisión técnica: el flujo OAuth2 no entrega el JWT final directamente en una URL pública. El backend redirige con un ticket temporal, y Angular llama después a `/oauth/exchange`.

## 4. Dispositivos

Clase: `DeviceController`
Ruta base: `/api/v1/devices`

| Método | Endpoint | Parámetros | Entrada | Salida | Lógica |
|---|---|---|---|---|---|
| `GET` | `/` | `Principal` | — | `List<DeviceDto>` | Lista dispositivos del usuario autenticado. |
| `GET` | `/{id}` | `id`, `Principal` | — | `DeviceDto` | Devuelve solo si pertenece al usuario. |
| `POST` | `/` | — | `DeviceDto` | `201 DeviceDto` | Alta directa de dispositivo. |
| `POST` | `/claim` | `Principal` | `DeviceDto` | `DeviceDto` | Reclama una MAC o registra dispositivo nuevo. |
| `POST` | `/simulated/demo-pack` | `Principal` | — | `201 List<DeviceDto>` | Crea un simulador por perfil que falte. |
| `POST` | `/simulated` | `Principal` | `CreateSimulatedDeviceRequest` | `201 DeviceDto` | Crea dispositivo simulado. |
| `PUT` | `/{id}` | `id`, `Principal` | `DeviceDto` | `DeviceDto` | Actualiza nombre, estado y perfil si procede. |
| `DELETE` | `/{id}` | `id`, `Principal` | — | `204 No Content` | Borra lecturas, alertas y dispositivo. |

DTOs:

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

`DeviceService` contiene las reglas importantes:

- `claimOrRegisterDevice` permite reclamar dispositivos sin dueño o asociados a `SYSTEM`.
- Si la MAC ya pertenece a otro usuario, lanza una regla de negocio.
- `createSimulatedDevice` exige perfil y nombre mínimo de 3 caracteres.
- Las MAC simuladas usan prefijo `SIM` y una secuencia numérica.
- `deleteById` elimina lecturas y alertas antes de borrar el dispositivo, evitando conflictos de FK.

Perfiles de simulación persistidos como texto:

```java
SINE_WAVE, OVEN, WASHING_MACHINE, TELEVISION, FAN,
DESKTOP_PC, FRIDGE, STANDBY, CONSTANT_HIGH_LOAD
```

## 5. Lecturas

Clase: `ReadingController`
Ruta base: `/api/v1/readings`

| Método | Endpoint | Parámetros | Salida | Uso |
|---|---|---|---|---|
| `GET` | `/` | `Principal` | `List<ReadingResponse>` | Lecturas de dispositivos propios. |
| `GET` | `/latest/{macAddress}` | `macAddress`, `Principal` | `ReadingResponse` | Última lectura de una MAC. |
| `GET` | `/device/{macAddress}/recent` | `macAddress`, `seconds`, `Principal` | `List<ReadingResponse>` | Histórico reciente para la gráfica. |
| `GET` | `/search` | `time`, `macAddress`, `Principal` | `ReadingResponse` | Búsqueda por clave temporal y MAC. |
| `DELETE` | `/search` | `time`, `macAddress`, `Principal` | `204 No Content` | Borrado de una lectura concreta. |

DTO:

```java
public record ReadingResponse(
    Instant time,
    String macAddress,
    BigDecimal powerW,
    BigDecimal energyTotalKwh,
    Boolean isOn
) {}
```

`seconds` tiene valor por defecto `120`. El servicio calcula `[now - seconds, now]` y consulta `ReadingRepository.findReadingsInInterval` ordenando por `time ASC`.

## 6. Analítica energética

Clase: `ConsumptionController`
Ruta base: `/api/v1/analytics`

| Método | Endpoint | Query params | Respuesta | Lógica |
|---|---|---|---|---|
| `GET` | `/cost` | `macAddress`, `start`, `end` | `macAddress`, `totalCostEur`, `start`, `end` | Coste total por deltas de kWh y precio del periodo. |
| `GET` | `/ghost-consumption` | `macAddress`, `start`, `end` | `macAddress`, `ghostCostEur`, `start`, `end` | Coste entre 00:00 y 05:59 hora local. |

Ejemplo de respuesta:

```json
{
  "macAddress": "9070694d3590",
  "totalCostEur": 1.24,
  "start": "2026-10-04T00:00:00Z",
  "end": "2026-10-04T22:00:00Z"
}
```

`ConsumptionService` ignora deltas nulos, negativos o reinicios de odómetro. Si el dispositivo no tiene usuario o tarifa, devuelve coste `0` y registra el caso en logs.

## 7. Tarifas

### 7.1. Catálogo maestro

Clase: `TariffController`
Ruta base: `/api/v1/tariffs`

| Método | Endpoint | Entrada | Salida | Restricción |
|---|---|---|---|---|
| `GET` | `/` | — | `List<TariffDto>` | Autenticado. |
| `GET` | `/{id}` | — | `TariffDto` | Autenticado. |
| `POST` | `/` | `TariffDto` | `201 TariffDto` | `ROLE_ADMIN`. |
| `POST` | `/{id}` | `TariffDto` | `TariffDto` | `ROLE_ADMIN`. |
| `DELETE` | `/{id}` | — | `204 No Content` | `ROLE_ADMIN`. |

La actualización usa `POST /{id}`. El frontend lo respeta en `TariffService.updateCatalogTariff`.

### 7.2. Tarifa privada

Clase: `UserTariffController`
Ruta base: `/api/v1/users/me/tariff`

| Método | Endpoint | Entrada | Salida | Lógica |
|---|---|---|---|---|
| `GET` | `/` | — | `200 TariffDto` o `204` | Devuelve tarifa activa propia. |
| `POST` | `/` | `UserTariffRequest` | `TariffDto` | Clona plantilla o guarda contrato directo. |
| `DELETE` | `/` | — | `204 No Content` | Desvincula y elimina tarifa privada. |

DTOs:

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

public record PeriodDto(Long id, String periodCode, BigDecimal priceKwh) { }
public record TariffContractedPowerDto(Long id, String periodCode, BigDecimal contractedPowerKw) { }
public record UserTariffRequest(Long templateTariffId, TariffDto contract) {}
```

Reglas de `TariffService.validateTariffContract`:

- `accessTariffCode` y `geographicZone` son obligatorios.
- `2.0TD` exige energía `P1`, `P2`, `P3` y potencia `P1`, `P2`.
- `3.0TD`, `6.1TD` y `6.2TD` exigen `P1` a `P6` en energía y potencia.
- En seis periodos, la potencia debe cumplir `P1 <= P2 <= ... <= P6`.
- Precios y potencias deben ser mayores que cero.

## 8. Alertas

Clase: `AlertController`
Ruta base: `/api/v1/alerts`

| Método | Endpoint | Parámetros | Salida | Uso |
|---|---|---|---|---|
| `GET` | `/` | `Principal` | `List<AlertDto>` | Lista alertas propias. |
| `DELETE` | `/{id}` | `id`, `Principal` | `204 No Content` | Borra alerta si pertenece al usuario. |

DTO:

```java
public record AlertDto(
    Long id,
    String macAddress,
    String username,
    String type,
    String message,
    LocalDateTime createdAt
) { }
```

`AlertService.checkPowerThreshold` se llama después de guardar cada lectura. Resuelve el periodo tarifario, obtiene la potencia contratada de ese periodo y crea una alerta `OVERPOWER` si la lectura supera el límite.

## 9. Errores

`GlobalExceptionHandler` devuelve `ErrorResponse`:

```java
public record ErrorResponse(
    int status,
    String error,
    String message,
    LocalDateTime timestamp
) {}
```

| Excepción | HTTP | Motivo |
|---|---|---|
| `EntityNotFoundException` | 404 | Recurso no localizado. |
| `BadCredentialsException` | 401 | Login incorrecto. |
| `UsernameNotFoundException` | 401 | Usuario de sesión no localizado. |
| `ForbiddenException` | 403 | Acceso denegado. |
| `IllegalStateException` | 400 | Regla de negocio incumplida. |
| `DataIntegrityViolationException` | 400/409/500 | Unicidad, FK o error de integridad. |
| `Exception` | 500 | Error no controlado. |

La ventaja práctica es que Angular puede leer `err.error.message` y mostrar mensajes de negocio sin exponer trazas internas.

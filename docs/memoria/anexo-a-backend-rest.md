# Anexo A: API REST del backend Spring Boot

Este anexo documenta los controladores REST implementados en `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers`. La API se agrupa bajo el prefijo `/api/v1` y usa JSON como formato de entrada y salida.

## 1. Seguridad y criterios comunes

La seguridad se configura en `SecurityConfig`:

- La API es **stateless**, por lo que no depende de sesión HTTP en servidor.
- Las rutas de autenticación (`/api/v1/auth/**` concretas), OAuth2 y `/ws-iot/**` quedan permitidas sin JWT.
- El resto de endpoints requiere autenticación.
- El catálogo de tarifas permite lectura a usuarios autenticados, pero escritura solo a `ROLE_ADMIN`.
- La identidad del usuario se obtiene con `Principal`; por diseño no se aceptan `userId` en las peticiones de usuario para evitar accesos cruzados.

## 2. Controlador de autenticación

Archivo: `AuthController.java`

| Método | Endpoint | Entrada | Salida | Uso |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `LoginUser` | `LoginUserJwt` | Autentica usuario y devuelve JWT. |
| `POST` | `/api/v1/auth/register` | `RegisterRequest` | `201 Created` | Registra usuario normal. |
| `POST` | `/api/v1/auth/register/admin` | `RegisterRequest` + cabecera `X-Wattimizer-Admin-Secret` | `201 Created` | Registra administrador si la cabecera coincide con `app.admin.secret`. |
| `POST` | `/api/v1/auth/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | Intercambia un ticket OAuth de un solo uso por JWT. |

DTOs:

```java
public record LoginUser(String username, String password) {}
public record LoginUserJwt(String statusCode, String jwt) {}
public record RegisterRequest(
    String username,
    String password,
    String confirmPassword,
    Long tariffId
) {}
```

La decisión de devolver un JWT propio después del OAuth evita que Angular tenga que tratar directamente con credenciales del proveedor externo. El frontend recibe un ticket temporal en la URL de callback y lo intercambia por el token de la aplicación.

## 3. Controlador de dispositivos

Archivo: `DeviceController.java`

Base path: `/api/v1/devices`

| Método | Endpoint | Parámetros | Entrada | Salida | Observaciones |
|---|---|---|---|---|---|
| `GET` | `/api/v1/devices` | - | - | `List<DeviceDto>` | Lista solo dispositivos del usuario autenticado. |
| `GET` | `/api/v1/devices/{id}` | `id` path | - | `DeviceDto` | Comprueba que el dispositivo pertenece al `Principal`. |
| `POST` | `/api/v1/devices` | - | `DeviceDto` | `201 DeviceDto` | Alta directa. Es una excepción histórica: no recibe `Principal`. Para uso normal se recomienda `/claim`. |
| `POST` | `/api/v1/devices/claim` | - | `DeviceDto` con `macAddress` y `name` | `DeviceDto` | Vincula una MAC existente o crea el dispositivo para el usuario autenticado. |
| `POST` | `/api/v1/devices/simulated` | - | `CreateSimulatedDeviceRequest` | `201 DeviceDto` | Crea un dispositivo simulado con MAC sintética `SIM...`. |
| `POST` | `/api/v1/devices/simulated/demo-pack` | - | `{}` | `201 List<DeviceDto>` | Crea un dispositivo por cada perfil de simulación que el usuario aún no tenga. |
| `PUT` | `/api/v1/devices/{id}` | `id` path | `DeviceDto` | `DeviceDto` | Actualiza nombre, estado y perfil si es simulado. |
| `DELETE` | `/api/v1/devices/{id}` | `id` path | - | `204 No Content` | Borra lecturas y alertas antes de eliminar el dispositivo. |

DTO principal:

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
```

DTO para simuladores:

```java
public record CreateSimulatedDeviceRequest(
    String name,
    SimulationProfile simulationProfile
) {}
```

Perfiles disponibles:

```java
SINE_WAVE, OVEN, WASHING_MACHINE, TELEVISION, FAN,
DESKTOP_PC, FRIDGE, STANDBY, CONSTANT_HIGH_LOAD
```

La lógica de negocio vive en `DeviceService`. En el borrado se llama a `readingRepository.deleteAllByDeviceMacAddress(macAddress)` y `alertRepository.deleteByDeviceId(id)` antes de `deviceRepository.delete(device)`. Esta decisión evita errores de clave foránea cuando existen lecturas históricas o alertas asociadas.

## 4. Controlador de lecturas

Archivo: `ReadingController.java`

Base path: `/api/v1/readings`

| Método | Endpoint | Parámetros | Salida | Uso |
|---|---|---|---|---|
| `GET` | `/api/v1/readings` | - | `List<ReadingResponse>` | Devuelve lecturas de los dispositivos del usuario autenticado. |
| `GET` | `/api/v1/readings/latest/{macAddress}` | `macAddress` path | `ReadingResponse` | Última lectura de una MAC concreta. |
| `GET` | `/api/v1/readings/device/{macAddress}/recent` | `seconds` query, por defecto `120` | `List<ReadingResponse>` | Lecturas recientes de una MAC, usadas para precargar la gráfica. |
| `GET` | `/api/v1/readings/search` | `time` ISO-8601, `macAddress` | `ReadingResponse` | Busca por clave compuesta temporal. |
| `DELETE` | `/api/v1/readings/search` | `time` ISO-8601, `macAddress` | `204 No Content` | Borra una lectura exacta por clave compuesta. |

DTO de salida:

```java
public record ReadingResponse(
    Instant time,
    String macAddress,
    BigDecimal powerW,
    BigDecimal energyTotalKwh,
    Boolean isOn
) {}
```

Todos los endpoints que reciben `macAddress` comprueban primero que el dispositivo pertenece al usuario autenticado. Esa comprobación se hace antes de consultar o borrar lecturas para no exponer datos de otra cuenta.

## 5. Controlador de analítica energética

Archivo: `ConsumptionController.java`

Base path: `/api/v1/analytics`

| Método | Endpoint | Query params | Salida | Uso |
|---|---|---|---|---|
| `GET` | `/api/v1/analytics/cost` | `macAddress`, `start`, `end` | `macAddress`, `totalCostEur`, `start`, `end` | Coste económico acumulado del intervalo. |
| `GET` | `/api/v1/analytics/ghost-consumption` | `macAddress`, `start`, `end` | `macAddress`, `ghostCostEur`, `start`, `end` | Coste de consumo fantasma entre 00:00 y 05:59 hora local. |

Las fechas se reciben como `Instant` con `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)`.

La lógica no multiplica una potencia aislada sin contexto, sino que recorre lecturas ordenadas y calcula el delta positivo de `energyTotalKwh` entre lecturas consecutivas. Ese delta se multiplica por el precio del periodo tarifario resuelto para el instante correspondiente.

## 6. Controlador de tarifas del catálogo

Archivo: `TariffController.java`

Base path: `/api/v1/tariffs`

| Método | Endpoint | Entrada | Salida | Permiso |
|---|---|---|---|---|
| `GET` | `/api/v1/tariffs` | - | `List<TariffDto>` | Usuario autenticado |
| `GET` | `/api/v1/tariffs/{id}` | - | `TariffDto` | Usuario autenticado |
| `POST` | `/api/v1/tariffs` | `TariffDto` | `201 TariffDto` | `ROLE_ADMIN` |
| `POST` | `/api/v1/tariffs/{id}` | `TariffDto` | `TariffDto` | `ROLE_ADMIN` |
| `DELETE` | `/api/v1/tariffs/{id}` | - | `204 No Content` | `ROLE_ADMIN` |

> Nota técnica: la actualización usa `POST /{id}` en lugar de `PUT`. El frontend lo respeta en `TariffService.updateCatalogTariff`.

DTO principal:

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
```

Reglas aplicadas por `TariffService`:

- `2.0TD` exige energía `P1`, `P2`, `P3` y potencia `P1`, `P2`.
- `3.0TD`, `6.1TD` y `6.2TD` exigen energía y potencia de `P1` a `P6`.
- En tarifas de seis periodos se valida `P1 <= P2 <= P3 <= P4 <= P5 <= P6` para potencia contratada.
- Precios y potencias deben ser mayores que cero.

## 7. Controlador de tarifa privada del usuario

Archivo: `UserTariffController.java`

Base path: `/api/v1/users/me/tariff`

| Método | Endpoint | Entrada | Salida | Uso |
|---|---|---|---|---|
| `GET` | `/api/v1/users/me/tariff` | - | `200 TariffDto` o `204 No Content` | Consulta tarifa privada del usuario autenticado. |
| `POST` | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` | Asigna, clona o actualiza la tarifa privada. |
| `DELETE` | `/api/v1/users/me/tariff` | - | `204 No Content` | Desvincula y borra el clon privado. |

DTO de entrada:

```java
public record UserTariffRequest(
    Long templateTariffId,
    TariffDto contract
) {}
```

`UserTariffService` permite tres casos:

1. Solo `templateTariffId`: clona una plantilla global.
2. `templateTariffId` y `contract`: clona plantilla y aplica cambios.
3. Solo `contract`: crea o actualiza una tarifa privada directamente.

El clon es importante porque evita modificar la plantilla global cuando un usuario cambia sus precios o potencias.

## 8. Controlador de alertas

Archivo: `AlertController.java`

Base path: `/api/v1/alerts`

| Método | Endpoint | Parámetros | Salida | Uso |
|---|---|---|---|---|
| `GET` | `/api/v1/alerts` | - | `List<AlertDto>` | Lista alertas del usuario autenticado. |
| `DELETE` | `/api/v1/alerts/{id}` | `id` path | `204 No Content` | Borra una alerta si pertenece al usuario. |

DTO:

```java
public record AlertDto(
    Long id,
    String macAddress,
    String username,
    String type,
    String message,
    LocalDateTime createdAt
) {}
```

El borrado no acepta simplemente el `id`; llama a `deleteAlertForUser(id, principal.getName())` para que un usuario no pueda limpiar alertas de otra cuenta.

## 9. Gestión de errores

`GlobalExceptionHandler` transforma excepciones en respuestas JSON con `ErrorResponse`:

| Excepción | HTTP | Motivo |
|---|---|---|
| `EntityNotFoundException` | `404` | Recurso no encontrado. |
| `BadCredentialsException` | `401` | Login incorrecto. |
| `UsernameNotFoundException` | `401` | Usuario no localizado en sesión. |
| `ForbiddenException` | `403` | Operación denegada. |
| `IllegalStateException` | `400` | Regla de negocio incumplida. |
| `DataIntegrityViolationException` por FK de lecturas/alertas | `409` | Dispositivo con datos asociados. |
| Excepción no controlada | `500` | Error interno genérico. |

La ventaja de centralizar las excepciones es que Angular recibe una estructura previsible cuando el error nace de una excepción controlada. Aun así, algunos controladores devuelven respuestas explícitas sin cuerpo, por ejemplo ciertos `403 Forbidden` cuando el recurso no pertenece al usuario autenticado.

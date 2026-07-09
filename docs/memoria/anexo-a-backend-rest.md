# Anexo A. Backend REST con Spring Boot

## 1. Vision general

El backend de Wattimizer esta construido con Spring Boot 4.0.5 y Java 26. Su API publica se concentra bajo `/api/v1` y usa JSON como formato de intercambio con Angular. La autenticacion se basa en JWT y OAuth2; una vez autenticado, el usuario se identifica en los controladores mediante `Principal.getName()`.

La decision mas importante de diseno es que los recursos sensibles no reciben un `userId` desde el cliente. Por ejemplo, la tarifa personal se gestiona en `/api/v1/users/me/tariff` y los dispositivos se filtran por el usuario que va dentro del token. Esto reduce el riesgo de IDOR, porque el cliente no puede pedir datos de otro usuario cambiando un parametro.

## 2. Seguridad HTTP

La configuracion principal esta en `SecurityConfig`.

| Regla | Rutas |
|---|---|
| Publicas | `/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/register/admin`, `/api/v1/auth/oauth/exchange`, `/oauth2/authorization/**`, `/login/oauth2/code/**`, `/ws-iot/**` |
| Autenticadas | `GET /api/v1/tariffs/**` y el resto de rutas privadas |
| Solo admin | Mutaciones de `/api/v1/tariffs/**` y `/admin/**` |

El backend trabaja en modo stateless:

```java
session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
```

Angular adjunta el token en el interceptor HTTP solamente para rutas `/api/v1` privadas. Las rutas de login, registro y canje OAuth quedan excluidas porque todavia no hay JWT de sesion.

## 3. Controladores REST

### 3.1. `AuthController`

Ruta base: `/api/v1/auth`

| Metodo | Endpoint | Entrada | Salida | Intencion |
|---|---|---|---|---|
| `POST` | `/login` | `LoginUser` | `LoginUserJwt` | Autentica email/password y emite JWT. |
| `POST` | `/register` | `RegisterRequest` | `201 No Content` | Registra usuario normal. |
| `POST` | `/register/admin` | `RegisterRequest` + header `X-Wattimizer-Admin-Secret` | `201 No Content` | Crea usuario admin si el secreto coincide con `app.admin.secret`. |
| `POST` | `/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | Canjea un ticket OAuth de un solo uso por JWT. |

DTOs:

| DTO | Campos | Uso |
|---|---|---|
| `LoginUser` | `username`, `password` | Credenciales de login. |
| `LoginUserJwt` | `statusCode`, `jwt` | Respuesta de autenticacion. |
| `RegisterRequest` | `username`, `password`, `confirmPassword`, `tariffId` | Alta de usuario. |
| `OAuthTicketExchangeRequest` | `ticket` | Canje posterior a OAuth2. |

### 3.2. `DeviceController`

Ruta base: `/api/v1/devices`

| Metodo | Endpoint | Parametros | Entrada | Salida |
|---|---|---|---|---|
| `GET` | `/api/v1/devices` | Token JWT | - | `List<DeviceDto>` |
| `GET` | `/api/v1/devices/{id}` | `id` | - | `DeviceDto` o `403` |
| `POST` | `/api/v1/devices` | - | `DeviceDto` | `DeviceDto`, `201` |
| `POST` | `/api/v1/devices/claim` | Token JWT | `DeviceDto` con `macAddress` y `name` | `DeviceDto` |
| `POST` | `/api/v1/devices/simulated` | Token JWT | `CreateSimulatedDeviceRequest` | `DeviceDto`, `201` |
| `POST` | `/api/v1/devices/simulated/demo-pack` | Token JWT | - | `List<DeviceDto>`, `201` |
| `PUT` | `/api/v1/devices/{id}` | `id` | `DeviceDto` | `DeviceDto` |
| `DELETE` | `/api/v1/devices/{id}` | `id` | - | `204 No Content` |

`GET`, `PUT` y `DELETE` comprueban la propiedad del dispositivo. En el borrado, antes de eliminar la fila de `devices`, el servicio elimina las lecturas y alertas asociadas. Esta decision evita errores de integridad referencial y deja el sistema limpio.

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
```

```java
public record CreateSimulatedDeviceRequest(
        String name,
        SimulationProfile simulationProfile
) {}
```

Perfiles de simulacion disponibles:

```text
SINE_WAVE, OVEN, WASHING_MACHINE, TELEVISION, FAN,
DESKTOP_PC, FRIDGE, STANDBY, CONSTANT_HIGH_LOAD
```

### 3.3. `ReadingController`

Ruta base: `/api/v1/readings`

| Metodo | Endpoint | Parametros | Salida |
|---|---|---|---|
| `GET` | `/api/v1/readings` | Token JWT | `List<ReadingResponse>` |
| `GET` | `/api/v1/readings/latest/{macAddress}` | `macAddress` | Ultima lectura del dispositivo |
| `GET` | `/api/v1/readings/device/{macAddress}/recent` | `macAddress`, `seconds` con valor por defecto `120` | Lecturas recientes ordenadas |
| `GET` | `/api/v1/readings/search` | `time`, `macAddress` | Lectura por clave compuesta |
| `DELETE` | `/api/v1/readings/search` | `time`, `macAddress` | `204 No Content` |

El DTO de salida es:

```java
public record ReadingResponse(
        Instant time,
        String macAddress,
        BigDecimal powerW,
        BigDecimal energyTotalKwh,
        Boolean isOn
) {}
```

La clave practica de este controlador es que primero busca el dispositivo por MAC y comprueba que pertenece al usuario autenticado. Solo despues consulta o borra la lectura.

### 3.4. `ConsumptionController`

Ruta base: `/api/v1/analytics`

| Metodo | Endpoint | Query params | Salida |
|---|---|---|---|
| `GET` | `/cost` | `macAddress`, `start`, `end` | `macAddress`, `totalCostEur`, `start`, `end` |
| `GET` | `/ghost-consumption` | `macAddress`, `start`, `end` | `macAddress`, `ghostCostEur`, `start`, `end` |

No existe un DTO especifico para estas respuestas; el controlador construye un `Map<String, Object>`. La logica de calculo vive en `ConsumptionService`.

Flujo del coste:

1. Se verifica que la MAC pertenece al usuario autenticado.
2. Se cargan lecturas del intervalo.
3. Se calcula el delta positivo entre lecturas consecutivas usando `energyTotalKwh`.
4. Se resuelve el periodo tarifario aplicable con `CalendarResolverService`.
5. Se multiplica energia por precio del periodo y se redondea a dos decimales.

El consumo fantasma usa la misma base, pero filtra pasos dentro de la ventana local `00:00-05:59`. Esta ventana es una decision funcional del proyecto y no se confunde con el periodo valle regulatorio.

### 3.5. `AlertController`

Ruta base: `/api/v1/alerts`

| Metodo | Endpoint | Entrada | Salida |
|---|---|---|---|
| `GET` | `/api/v1/alerts` | Token JWT | `List<AlertDto>` |
| `DELETE` | `/api/v1/alerts/{id}` | `id` | `204 No Content` o `404` |

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

La eliminacion llama a `deleteAlertForUser(id, username)`. Si no borra ninguna fila, el controlador lanza `EntityNotFoundException`, porque el id no existe o no pertenece al usuario.

### 3.6. `TariffController`

Ruta base: `/api/v1/tariffs`

| Metodo | Endpoint | Entrada | Salida | Seguridad |
|---|---|---|---|---|
| `GET` | `/api/v1/tariffs` | - | `List<TariffDto>` | Autenticado |
| `GET` | `/api/v1/tariffs/{id}` | - | `TariffDto` | Autenticado |
| `POST` | `/api/v1/tariffs` | `TariffDto` | `TariffDto`, `201` | `ROLE_ADMIN` |
| `POST` | `/api/v1/tariffs/{id}` | `TariffDto` | `TariffDto` | `ROLE_ADMIN` |
| `DELETE` | `/api/v1/tariffs/{id}` | - | `204 No Content` | `ROLE_ADMIN` |

El endpoint de actualizacion usa `POST /{id}` en lugar de `PUT`. El frontend lo respeta desde `TariffService.updateCatalogTariff`.

### 3.7. `UserTariffController`

Ruta base: `/api/v1/users/me/tariff`

| Metodo | Endpoint | Entrada | Salida |
|---|---|---|---|
| `GET` | `/api/v1/users/me/tariff` | Token JWT | `TariffDto` o `204 No Content` |
| `POST` | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` |
| `DELETE` | `/api/v1/users/me/tariff` | Token JWT | `204 No Content` |

El comentario del propio controlador indica la intencion de diseno: no aceptar IDs de usuario en path ni body para evitar accesos indebidos entre cuentas.

DTO principal:

```java
public record UserTariffRequest(
        Long templateTariffId,
        TariffDto contract
) {}
```

Modos de uso reales:

- Solo `templateTariffId`: clona una plantilla del catalogo.
- `templateTariffId` mas `contract`: clona y aplica cambios.
- Solo `contract`: crea o actualiza el contrato privado desde los datos enviados.

## 4. DTOs de tarifas

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

```java
public record PeriodDto(
        Long id,
        String periodCode,
        BigDecimal priceKwh
) {}
```

```java
public record TariffContractedPowerDto(
        Long id,
        String periodCode,
        BigDecimal contractedPowerKw
) {}
```

La separacion entre `PeriodDto` y `TariffContractedPowerDto` es necesaria porque el precio de energia y la potencia contratada son conceptos distintos. El precio afecta al coste por kWh; la potencia contratada se usa para detectar excesos de maximetro.

## 5. DTOs de telemetria MQTT

Los DTOs de MQTT estan preparados para ignorar campos desconocidos:

```java
@JsonIgnoreProperties(ignoreUnknown = true)
```

Esto es importante porque los dispositivos Shelly pueden enviar mas informacion de la que Wattimizer necesita.

| DTO | Campos relevantes | Origen |
|---|---|---|
| `EventsRpc` | `source` mapeado desde `src`, `params` | Topic `.../events/rpc` |
| `Params` | `timestamp` desde `ts`, `switchData` desde `switch:0` | Cuerpo anidado |
| `Switch` | `activeEnergy`, `activePower` | Relé del enchufe |
| `Status` | `output`, `activePower`, `activeEnergy` | Topic `.../status/switch:0` |
| `ActiveEnergy` | `total` | Odometro de energia |

## 6. Gestion de errores

`GlobalExceptionHandler` centraliza los errores REST:

| Excepcion | HTTP | Mensaje funcional |
|---|---|---|
| `EntityNotFoundException` | 404 | Recurso no encontrado. |
| `BadCredentialsException` | 401 | Credenciales incorrectas. |
| `IllegalStateException` | 400 | Violacion de regla de negocio. |
| `UsernameNotFoundException` | 401 | Usuario no encontrado en sesion. |
| `ForbiddenException` | 403 | Acceso denegado. |
| `DataIntegrityViolationException` por usuario duplicado | 400 | Correo ya registrado. |
| `DataIntegrityViolationException` por FK de lecturas/alertas | 409 | No se puede eliminar dispositivo con datos asociados. |
| `Exception` generica | 500 | Error interno de telemetria. |

Formato comun:

```java
public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {}
```

## 7. Controladores inactivos

En el paquete `controllers` existen `DeviceCommandController`, `DeviceStateController` y `ReactiveDeviceStateController`, pero su codigo esta comentado. No forman parte del API activo y no deben documentarse como endpoints disponibles.

## 8. Pruebas asociadas

| Archivo | Cobertura |
|---|---|
| `DeviceServiceTest` | Alta simulada, cambio de perfil, ownership, pack demo y borrado en cascada. |
| `IotTelemetrySimulationJobTest` | Activacion/desactivacion del job, integracion de energia y resiliencia por dispositivo. |
| `SimulationProfileRegistryTest` | Potencia determinista y no negativa para todos los perfiles. |
| `ConsumptionServiceTest` | Calculo de coste, consumo fantasma y zonas horarias. |
| `TariffServiceTest` | Validacion de tarifas TD, periodos y potencias. |
| `UserTariffServiceTest` | Tarifa privada, clonado de plantilla y aislamiento por usuario. |

No hay pruebas especificas de `MqttConfig`, `DeviceMessageHandler` ni WebSocket STOMP. Es una mejora futura razonable porque esas piezas son importantes para el flujo IoT completo.

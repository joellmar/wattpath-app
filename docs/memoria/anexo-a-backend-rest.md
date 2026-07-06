# Anexo A. Controladores REST de Spring Boot

Este anexo describe la API REST implementada en `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers`. La API usa Spring Boot 4.0.5, Java 26, Spring Security con JWT y DTOs en `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/dtos`.

## A.1. Seguridad común

La seguridad se configura en `config/SecurityConfig.java`:

- La sesión HTTP es `STATELESS`, por lo que el servidor no mantiene sesión tradicional.
- El token JWT se valida en `security/JwtValidatorFilter.java`.
- Las rutas `/api/v1/auth/*`, OAuth2 y `/ws-iot/**` quedan permitidas sin JWT.
- `GET /api/v1/tariffs/**` requiere autenticación.
- Las mutaciones de tarifas (`POST`, `DELETE`) requieren `ROLE_ADMIN` mediante `@PreAuthorize`.
- El resto de endpoints requieren usuario autenticado.

El patrón de propiedad se basa en `Principal principal`. El backend no confía en un `userId` recibido desde el cliente; usa `principal.getName()` para consultar o modificar los datos del usuario autenticado.

## A.2. Respuesta de error global

`GlobalExceptionHandler.java` devuelve el DTO `ErrorResponse`:

| Campo | Tipo | Significado |
| --- | --- | --- |
| `status` | `int` | Código HTTP. |
| `error` | `String` | Nombre resumido del error. |
| `message` | `String` | Explicación concreta. |
| `timestamp` | `LocalDateTime` | Momento en el que se generó la respuesta. |

No todos los errores pasan por este handler. Algunos controladores devuelven `403 FORBIDDEN` sin cuerpo cuando el recurso no pertenece al usuario.

## A.3. `AuthController`

**Archivo:** `controllers/AuthController.java`
**Base:** `/api/v1/auth`
**Acceso:** público.

| Método | Ruta | Entrada | Salida | Lógica |
| --- | --- | --- | --- | --- |
| `POST` | `/login` | `LoginUser` | `LoginUserJwt` | Autentica con `UserProviderDetailsManager` y genera JWT con `JwtTokenService`. |
| `POST` | `/register` | `RegisterRequest` | `201 Created` sin cuerpo | Registra usuario estándar con `AuthRegistrationService.registerUser`. |
| `POST` | `/register/admin` | `RegisterRequest` + header `X-Wattimizer-Admin-Secret` | `201 Created` sin cuerpo | Registra administrador si la clave coincide con `app.admin.secret`. |
| `POST` | `/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | Canjea ticket OAuth2 de un solo uso por JWT propio de la aplicación. |

DTOs:

| DTO | Campos |
| --- | --- |
| `LoginUser` | `username`, `password` |
| `LoginUserJwt` | `statusCode`, `jwt` |
| `RegisterRequest` | `username`, `password`, `confirmPassword`, `tariffId` |
| `OAuthTicketExchangeRequest` | `ticket` |

## A.4. `DeviceController`

**Archivo:** `controllers/DeviceController.java`
**Base:** `/api/v1/devices`
**Acceso:** usuario autenticado.

| Método | Ruta | Entrada | Salida | Control de acceso |
| --- | --- | --- | --- | --- |
| `GET` | `/` | Sin body | `List<DeviceDto>` | Lista solo dispositivos del usuario autenticado. |
| `GET` | `/{id}` | `id` path | `DeviceDto` | Compara `device.username()` con `principal.getName()`. Si no coincide, `403` sin cuerpo. |
| `POST` | `/` | `DeviceDto` | `DeviceDto` con `201 Created` | Alta directa. Es una ruta más abierta; no asigna propietario en el controlador. |
| `POST` | `/claim` | `DeviceDto` con `macAddress` y `name` | `DeviceDto` | Reclama o registra dispositivo para el usuario autenticado. |
| `POST` | `/simulated/demo-pack` | Sin body | `List<DeviceDto>` con `201 Created` | Crea un pack de simuladores para el usuario. |
| `POST` | `/simulated` | `CreateSimulatedDeviceRequest` | `DeviceDto` con `201 Created` | Crea un simulador con perfil de consumo. |
| `PUT` | `/{id}` | `id` + `DeviceDto` | `DeviceDto` | `DeviceService.updateDevice` valida propiedad. |
| `DELETE` | `/{id}` | `id` path | `204 No Content` | El controlador comprueba propietario antes de borrar. |

DTOs:

| DTO | Campos | Uso |
| --- | --- | --- |
| `DeviceDto` | `id`, `username`, `name`, `macAddress`, `isOn`, `simulated`, `simulationProfile` | Representa dispositivos físicos y simulados. |
| `CreateSimulatedDeviceRequest` | `name`, `simulationProfile` | Alta de simulador desde la UI. |

La eliminación de dispositivos se resolvió en `DeviceService.deleteById`: primero se borran lecturas y alertas asociadas. La razón es evitar fallos por claves foráneas en `readings` y `alerts`.

## A.5. `ReadingController`

**Archivo:** `controllers/ReadingController.java`
**Base:** `/api/v1/readings`
**Acceso:** usuario autenticado.

| Método | Ruta | Parámetros | Salida | Lógica |
| --- | --- | --- | --- | --- |
| `GET` | `/` | Sin parámetros | `List<ReadingResponse>` | Devuelve lecturas de dispositivos del usuario. |
| `GET` | `/latest/{macAddress}` | `macAddress` path | `ReadingResponse` | Busca última lectura si la MAC pertenece al usuario. |
| `GET` | `/device/{macAddress}/recent` | `macAddress` path, `seconds` query con valor por defecto `120` | `List<ReadingResponse>` | Devuelve lecturas recientes ordenadas por tiempo. |
| `GET` | `/search` | `time` ISO DATE_TIME, `macAddress` | `ReadingResponse` | Busca lectura por clave compuesta. |
| `DELETE` | `/search` | `time` ISO DATE_TIME, `macAddress` | `204 No Content` | Borra una lectura concreta si el dispositivo pertenece al usuario. |

DTO de salida:

| DTO | Campos |
| --- | --- |
| `ReadingResponse` | `time`, `macAddress`, `powerW`, `energyTotalKwh`, `isOn` |

La entidad `Reading` usa clave compuesta `(time, device_id)`, definida mediante `ReadingId`. Esto encaja con TimescaleDB porque `time` forma parte de la partición temporal.

## A.6. `ConsumptionController`

**Archivo:** `controllers/ConsumptionController.java`
**Base:** `/api/v1/analytics`
**Acceso:** usuario autenticado.

| Método | Ruta | Parámetros | Salida |
| --- | --- | --- | --- |
| `GET` | `/cost` | `macAddress`, `start`, `end` como `Instant` ISO | `Map<String,Object>` con `macAddress`, `totalCostEur`, `start`, `end` |
| `GET` | `/ghost-consumption` | `macAddress`, `start`, `end` como `Instant` ISO | `Map<String,Object>` con `macAddress`, `ghostCostEur`, `start`, `end` |

Antes de calcular, el controlador busca el dispositivo por MAC y comprueba que el usuario autenticado sea el propietario. Si no lo es, devuelve `403`.

La respuesta no usa un DTO tipado, sino un `Map`. Para una versión futura sería más mantenible crear `EnergyCostResponse` y `GhostCostResponse` también en backend, igual que ya existen interfaces equivalentes en Angular.

## A.7. `AlertController`

**Archivo:** `controllers/AlertController.java`
**Base:** `/api/v1/alerts`
**Acceso:** usuario autenticado.

| Método | Ruta | Entrada | Salida | Lógica |
| --- | --- | --- | --- | --- |
| `GET` | `/` | Sin body | `List<AlertDto>` | Lista alertas del usuario autenticado. |
| `DELETE` | `/{id}` | `id` path | `204 No Content` | Borra alerta solo si pertenece al usuario; si no se borra ninguna fila lanza `EntityNotFoundException`. |

DTO:

| DTO | Campos |
| --- | --- |
| `AlertDto` | `id`, `macAddress`, `username`, `type`, `message`, `createdAt` |

Las alertas actuales se generan por potencia excesiva, con tipo funcional `OVERPOWER`.

## A.8. `TariffController`

**Archivo:** `controllers/TariffController.java`
**Base:** `/api/v1/tariffs`

| Método | Ruta | Entrada | Salida | Rol |
| --- | --- | --- | --- | --- |
| `GET` | `/` | Sin body | `List<TariffDto>` | Usuario autenticado |
| `GET` | `/{id}` | `id` path | `TariffDto` | Usuario autenticado |
| `POST` | `/` | `TariffDto` | `TariffDto` con `201 Created` | `ROLE_ADMIN` |
| `POST` | `/{id}` | `id` + `TariffDto` | `TariffDto` | `ROLE_ADMIN` |
| `DELETE` | `/{id}` | `id` path | `204 No Content` | `ROLE_ADMIN` |

El uso de `POST /{id}` para actualizar es una decisión concreta del código actual. No se usa `PUT` ni `PATCH`.

DTOs de tarifa:

| DTO | Campos |
| --- | --- |
| `TariffDto` | `id`, `name`, `market`, `accessTariffCode`, `geographicZone`, `energyCompany`, `periods`, `contractedPowers` |
| `PeriodDto` | `id`, `periodCode`, `priceKwh` |
| `TariffContractedPowerDto` | `id`, `periodCode`, `contractedPowerKw` |

## A.9. `UserTariffController`

**Archivo:** `controllers/UserTariffController.java`
**Base:** `/api/v1/users/me/tariff`
**Acceso:** usuario autenticado.

| Método | Ruta | Entrada | Salida | Lógica |
| --- | --- | --- | --- | --- |
| `GET` | `/` | Sin body | `TariffDto` o `204 No Content` | Devuelve la tarifa privada del usuario. |
| `POST` | `/` | `UserTariffRequest` | `TariffDto` | Guarda o actualiza la tarifa privada. |
| `DELETE` | `/` | Sin body | `204 No Content` | Desvincula la tarifa del usuario. |

`UserTariffRequest` contiene:

| Campo | Significado |
| --- | --- |
| `templateTariffId` | Identificador opcional de una tarifa de catálogo para clonar. |
| `contract` | Contrato completo con precios y potencias modificables por el usuario. |

Esta ruta está diseñada para evitar IDOR: no aparece ningún `userId` en la URL ni en el body.

## A.10. Controladores no activos

Hay tres controladores comentados:

| Archivo | Estado | Función prevista |
| --- | --- | --- |
| `DeviceStateController.java` | Comentado | Consulta de último estado e histórico. |
| `ReactiveDeviceStateController.java` | Comentado | Variante reactiva con `Mono`. |
| `DeviceCommandController.java` | Comentado | Comandos STOMP hacia dispositivos. |

No forman parte de la API actual y no deben documentarse como funcionalidad disponible.

## A.11. Resumen de errores relevantes

| Situación | Respuesta |
| --- | --- |
| Credenciales incorrectas | `401 Unauthorized` |
| JWT expirado | `401` con JSON generado por `JwtValidatorFilter` |
| Recurso de otro usuario | Normalmente `403` sin cuerpo |
| Registro duplicado o estado inválido | `400 Bad Request` |
| Entidad inexistente | `404 Not Found` |
| Borrado bloqueado por FK | `409 Conflict` cuando lo detecta `GlobalExceptionHandler` |
| Error inesperado | `500 Internal Server Error` |

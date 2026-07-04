# Anexo A - Backend REST con Spring Boot

## 1. Alcance del anexo

Este anexo documenta la API REST real del backend Spring Boot ubicado en `backend/src/main/java/com/joselumartos/jwtauthbackenddemo`. Todos los endpoints activos están versionados con el prefijo `/api/v1`.

La API se organiza en 8 controladores:

| Controlador | Ruta base | Responsabilidad |
| --- | --- | --- |
| `AuthController` | `/api/v1/auth` | Login, registro y canje OAuth2 |
| `DeviceController` | `/api/v1/devices` | Gestión de dispositivos físicos y simulados |
| `ReadingController` | `/api/v1/readings` | Consulta y borrado de lecturas |
| `ConsumptionController` | `/api/v1/analytics` | Coste energético y consumo fantasma |
| `TariffController` | `/api/v1/tariffs` | Catálogo maestro de tarifas |
| `UserTariffController` | `/api/v1/users/me/tariff` | Tarifa privada del usuario autenticado |
| `AlertController` | `/api/v1/alerts` | Consulta y limpieza de alertas |
| `GlobalExceptionHandler` | Global | Formato común de errores |

## 2. Seguridad de la API

La configuración de seguridad está en `config/SecurityConfig.java`. El backend trabaja sin sesión de servidor (`STATELESS`), con CSRF deshabilitado y autenticación por JWT en la cabecera:

```http
Authorization: Bearer <jwt>
```

Las rutas públicas son:

- `POST /api/v1/auth/login`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/register/admin`
- `POST /api/v1/auth/oauth/exchange`
- `/oauth2/authorization/**`
- `/login/oauth2/code/**`
- `/ws-iot/**`

El resto de rutas requieren autenticación. Además, las mutaciones del catálogo de tarifas exigen rol `ROLE_ADMIN` mediante `@PreAuthorize("hasRole('ADMIN')")`.

Una decisión importante del diseño es que las lecturas, actualizaciones y borrados de recursos privados se filtran por el `Principal` extraído del JWT. Por ejemplo, en dispositivos y lecturas no se acepta un `userId` enviado desde Angular, porque eso permitiría intentar acceder a datos de otro usuario cambiando un parámetro. La excepción documentada aparte es `POST /api/v1/devices`, que recibe un `DeviceDto` y persiste el DTO tal como lo procesa el servicio.

## 3. Formato de errores

El DTO de error común es `ErrorResponse`:

```java
public record ErrorResponse(
    int status,
    String error,
    String message,
    LocalDateTime timestamp
) {}
```

El `GlobalExceptionHandler` transforma errores frecuentes:

| Excepción | HTTP | Mensaje |
| --- | --- | --- |
| `EntityNotFoundException` | 404 | Mensaje de la excepción |
| `BadCredentialsException` | 401 | `Credenciales de acceso incorrectas.` |
| `IllegalStateException` | 400 | Regla de negocio incumplida |
| `UsernameNotFoundException` | 401 | Usuario no localizado |
| `ForbiddenException` | 403 | Acceso denegado |
| `DataIntegrityViolationException` | 400, 409 o 500 | Duplicados, FK o error genérico |
| `Exception` | 500 | Error interno genérico |

Algunos controladores devuelven `403` sin cuerpo cuando el usuario autenticado no es propietario del recurso. Es una respuesta simple pero coherente con el objetivo de no dar más información de la necesaria.

## 4. Controlador de autenticación

**Archivo:** `controllers/AuthController.java`
**Ruta base:** `/api/v1/auth`

| Método | Endpoint | Entrada | Salida | Uso |
| --- | --- | --- | --- | --- |
| `POST` | `/login` | `LoginUser` | `LoginUserJwt` | Login con email y contraseña |
| `POST` | `/register` | `RegisterRequest` | `201 No Content` | Registro de usuario normal |
| `POST` | `/register/admin` | `RegisterRequest` + `X-Wattimizer-Admin-Secret` | `201 No Content` | Registro de administrador |
| `POST` | `/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | Canje de ticket OAuth2 por JWT |

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

public record OAuthTicketExchangeRequest(String ticket) {}
```

La parte OAuth2 no devuelve el JWT directamente en la URL. Primero se genera un ticket de un solo uso con TTL de 60 segundos y Angular lo canjea en `/oauth/exchange`. Esta decisión evita exponer el token principal en el historial del navegador.

## 5. Controlador de dispositivos

**Archivo:** `controllers/DeviceController.java`
**Ruta base:** `/api/v1/devices`

| Método | Endpoint | Parámetros | Body | Respuesta |
| --- | --- | --- | --- | --- |
| `GET` | `/` | `Principal` | - | `List<DeviceDto>` |
| `GET` | `/{id}` | `id`, `Principal` | - | `DeviceDto` o `403` |
| `POST` | `/` | - | `DeviceDto` | `201 DeviceDto` |
| `POST` | `/claim` | `Principal` | `DeviceDto` con `macAddress` y `name` | `DeviceDto` |
| `POST` | `/simulated/demo-pack` | `Principal` | - | `201 List<DeviceDto>` |
| `POST` | `/simulated` | `Principal` | `CreateSimulatedDeviceRequest` | `201 DeviceDto` |
| `PUT` | `/{id}` | `id`, `Principal` | `DeviceDto` | `DeviceDto` |
| `DELETE` | `/{id}` | `id`, `Principal` | - | `204 No Content` |

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

La operación `/claim` es la más relevante para dispositivos físicos. Recibe una MAC y un nombre, y el servicio `DeviceService.claimOrRegisterDevice` vincula el dispositivo al usuario autenticado. Para la demo, `/simulated/demo-pack` crea un conjunto de simuladores con perfiles de consumo distintos.

## 6. Controlador de lecturas

**Archivo:** `controllers/ReadingController.java`
**Ruta base:** `/api/v1/readings`

| Método | Endpoint | Parámetros | Respuesta |
| --- | --- | --- | --- |
| `GET` | `/` | `Principal` | Lecturas del usuario autenticado |
| `GET` | `/latest/{macAddress}` | `macAddress`, `Principal` | Última lectura del dispositivo |
| `GET` | `/device/{macAddress}/recent` | `macAddress`, query `seconds` con valor por defecto `120` | Lecturas recientes |
| `GET` | `/search` | query `time`, `macAddress`, `Principal` | Lectura por clave compuesta |
| `DELETE` | `/search` | query `time`, `macAddress`, `Principal` | Borrado por clave compuesta |

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

Antes de devolver lecturas, el controlador comprueba que la MAC pertenece al usuario autenticado. Esta validación es necesaria porque la MAC viaja como parámetro de URL y no debe permitir consultas cruzadas entre cuentas.

## 7. Controlador de analíticas

**Archivo:** `controllers/ConsumptionController.java`
**Ruta base:** `/api/v1/analytics`

| Método | Endpoint | Query params | Respuesta |
| --- | --- | --- | --- |
| `GET` | `/cost` | `macAddress`, `start`, `end` | `macAddress`, `totalCostEur`, `start`, `end` |
| `GET` | `/ghost-consumption` | `macAddress`, `start`, `end` | `macAddress`, `ghostCostEur`, `start`, `end` |

Ejemplo de llamada:

```http
GET /api/v1/analytics/cost?macAddress=9070694d3590&start=2026-07-04T00:00:00Z&end=2026-07-04T22:00:00Z
```

La respuesta se construye como `Map<String, Object>`, no como DTO dedicado:

```json
{
  "macAddress": "9070694d3590",
  "totalCostEur": 1.42,
  "start": "2026-07-04T00:00:00Z",
  "end": "2026-07-04T22:00:00Z"
}
```

`ConsumptionService` no calcula el coste con una tarifa fija. Primero obtiene lecturas ordenadas, calcula deltas positivos de `energyTotalKwh` y resuelve el periodo tarifario aplicable para cada tramo.

## 8. Controlador de tarifas del catálogo

**Archivo:** `controllers/TariffController.java`
**Ruta base:** `/api/v1/tariffs`

| Método | Endpoint | Rol | Body | Respuesta |
| --- | --- | --- | --- | --- |
| `GET` | `/` | Usuario autenticado | - | `List<TariffDto>` |
| `GET` | `/{id}` | Usuario autenticado | - | `TariffDto` |
| `POST` | `/` | `ROLE_ADMIN` | `TariffDto` | `201 TariffDto` |
| `POST` | `/{id}` | `ROLE_ADMIN` | `TariffDto` | `TariffDto` |
| `DELETE` | `/{id}` | `ROLE_ADMIN` | - | `204 No Content` |

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

DTOs anidados:

```java
public record PeriodDto(Long id, String periodCode, BigDecimal priceKwh) {}

public record TariffContractedPowerDto(
    Long id,
    String periodCode,
    BigDecimal contractedPowerKw
) {}
```

El catálogo representa plantillas maestras. Las tarifas privadas de usuario se gestionan en otro controlador para no mezclar contratos personales con plantillas compartidas.

## 9. Controlador de tarifa privada del usuario

**Archivo:** `controllers/UserTariffController.java`
**Ruta base:** `/api/v1/users/me/tariff`

| Método | Endpoint | Body | Respuesta |
| --- | --- | --- | --- |
| `GET` | `/` | - | `200 TariffDto` o `204 No Content` |
| `POST` | `/` | `UserTariffRequest` | `200 TariffDto` |
| `DELETE` | `/` | - | `204 No Content` |

DTO:

```java
public record UserTariffRequest(
    Long templateTariffId,
    TariffDto contract
) {}
```

El diseño evita IDOR porque la ruta no contiene `userId`. El usuario se obtiene siempre desde `Principal`. El servicio acepta varios escenarios: clonar una plantilla, clonar con cambios, crear contrato propio o actualizar la tarifa privada existente.

## 10. Controlador de alertas

**Archivo:** `controllers/AlertController.java`
**Ruta base:** `/api/v1/alerts`

| Método | Endpoint | Parámetros | Respuesta |
| --- | --- | --- | --- |
| `GET` | `/` | `Principal` | `List<AlertDto>` |
| `DELETE` | `/{id}` | `id`, `Principal` | `204 No Content` o `404` |

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

El borrado se hace con `deleteAlertForUser(id, username)`. Si no se borra ninguna fila, se lanza `EntityNotFoundException`; así no se diferencia entre alerta inexistente y alerta de otro usuario.

## 11. Resumen completo de endpoints

| # | Método | Ruta | Entrada | Salida |
| --- | --- | --- | --- | --- |
| 1 | `POST` | `/api/v1/auth/login` | `LoginUser` | `LoginUserJwt` |
| 2 | `POST` | `/api/v1/auth/register` | `RegisterRequest` | `201` |
| 3 | `POST` | `/api/v1/auth/register/admin` | `RegisterRequest` + header secreto | `201` |
| 4 | `POST` | `/api/v1/auth/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` |
| 5 | `GET` | `/api/v1/devices` | - | `List<DeviceDto>` |
| 6 | `GET` | `/api/v1/devices/{id}` | path `id` | `DeviceDto` |
| 7 | `POST` | `/api/v1/devices` | `DeviceDto` | `DeviceDto` |
| 8 | `POST` | `/api/v1/devices/claim` | `DeviceDto` | `DeviceDto` |
| 9 | `POST` | `/api/v1/devices/simulated/demo-pack` | - | `List<DeviceDto>` |
| 10 | `POST` | `/api/v1/devices/simulated` | `CreateSimulatedDeviceRequest` | `DeviceDto` |
| 11 | `PUT` | `/api/v1/devices/{id}` | `DeviceDto` | `DeviceDto` |
| 12 | `DELETE` | `/api/v1/devices/{id}` | path `id` | `204` |
| 13 | `GET` | `/api/v1/readings` | - | `List<ReadingResponse>` |
| 14 | `GET` | `/api/v1/readings/latest/{macAddress}` | path `macAddress` | `ReadingResponse` |
| 15 | `GET` | `/api/v1/readings/device/{macAddress}/recent` | query `seconds` | `List<ReadingResponse>` |
| 16 | `GET` | `/api/v1/readings/search` | `time`, `macAddress` | `ReadingResponse` |
| 17 | `DELETE` | `/api/v1/readings/search` | `time`, `macAddress` | `204` |
| 18 | `GET` | `/api/v1/analytics/cost` | `macAddress`, `start`, `end` | Map de coste |
| 19 | `GET` | `/api/v1/analytics/ghost-consumption` | `macAddress`, `start`, `end` | Map de coste fantasma |
| 20 | `GET` | `/api/v1/tariffs` | - | `List<TariffDto>` |
| 21 | `GET` | `/api/v1/tariffs/{id}` | path `id` | `TariffDto` |
| 22 | `POST` | `/api/v1/tariffs` | `TariffDto` | `TariffDto` |
| 23 | `POST` | `/api/v1/tariffs/{id}` | `TariffDto` | `TariffDto` |
| 24 | `DELETE` | `/api/v1/tariffs/{id}` | path `id` | `204` |
| 25 | `GET` | `/api/v1/users/me/tariff` | - | `TariffDto` o `204` |
| 26 | `POST` | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` |
| 27 | `DELETE` | `/api/v1/users/me/tariff` | - | `204` |
| 28 | `GET` | `/api/v1/alerts` | - | `List<AlertDto>` |
| 29 | `DELETE` | `/api/v1/alerts/{id}` | path `id` | `204` |

## 12. Decisiones arquitectónicas destacables

- **API versionada:** `/api/v1` deja margen para cambios futuros sin romper clientes.
- **Separación catálogo/contrato privado:** evita que editar la tarifa de un usuario modifique una plantilla global.
- **Autorización por `Principal`:** reduce el riesgo de acceso cruzado entre usuarios.
- **DTOs explícitos:** el backend no expone entidades JPA directamente.
- **MapStruct:** se usa para convertir entidades a DTOs y mantener controlado el contrato REST.
- **Errores centralizados:** la mayoría de excepciones pasan por `GlobalExceptionHandler`.

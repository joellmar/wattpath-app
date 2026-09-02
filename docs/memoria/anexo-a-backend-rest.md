# Anexo A - Backend REST con Spring Boot

## 1. Papel del backend en Wattimizer

El backend es la capa que protege los datos del usuario, recibe peticiones REST desde Angular, transforma DTOs en entidades JPA y aplica las reglas de negocio energeticas. El codigo se encuentra en `backend/src/main/java/com/joselumartos/jwtauthbackenddemo`.

La estructura real es:

- `controllers`: entrada HTTP.
- `services`: reglas de negocio y transacciones.
- `dtos`: objetos que viajan por JSON.
- `entities`: modelo persistido en PostgreSQL/TimescaleDB.
- `repositories`: acceso a datos con Spring Data JPA.
- `config` y `security`: seguridad, MQTT, WebSocket, CORS y auditoria.

## 2. Seguridad general de la API

La clase `SecurityConfig` define una API principalmente stateless:

- `SessionCreationPolicy.STATELESS`: no se guarda sesion HTTP en servidor.
- `JwtValidatorFilter`: valida el token en las peticiones privadas.
- CORS configurable desde `app.cors.allowed-origins`.
- `@EnableMethodSecurity`: permite proteger metodos con `@PreAuthorize`.

Rutas publicas:

| Ruta | Motivo |
|---|---|
| `POST /api/v1/auth/login` | Necesaria para obtener JWT. |
| `POST /api/v1/auth/register` | Alta publica de usuarios. |
| `POST /api/v1/auth/register/admin` | Alta de administradores protegida con cabecera secreta. |
| `POST /api/v1/auth/oauth/exchange` | Intercambio de ticket OAuth temporal por JWT. |
| `/oauth2/authorization/**` y `/login/oauth2/code/**` | Flujo OAuth2 de Spring Security. |
| `/ws-iot/**` | Handshake WebSocket STOMP. |

Las operaciones de escritura sobre tarifas del catalogo solo aceptan `ROLE_ADMIN`. El resto de rutas de negocio exige usuario autenticado.

## 3. Controladores REST activos

### 3.1. `AuthController`

**Archivo:** `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/AuthController.java`
**Ruta base:** `/api/v1/auth`

| Metodo | Endpoint | Entrada | Salida | Logica |
|---|---|---|---|---|
| `POST` | `/login` | `LoginUser` | `LoginUserJwt` | Autentica con `UserProviderDetailsManager` y genera JWT con `JwtTokenService`. |
| `POST` | `/register` | `RegisterRequest` | `201 Created` sin body | Crea usuario normal mediante `AuthRegistrationService.registerUser`. |
| `POST` | `/register/admin` | `RegisterRequest` + cabecera `X-Wattimizer-Admin-Secret` | `201 Created` sin body | Verifica la cabecera contra `app.admin.secret` y registra `ROLE_ADMIN`. |
| `POST` | `/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | Consume un ticket OAuth de un solo uso y devuelve JWT propio de Wattimizer. |

DTOs:

```java
public record LoginUser(String username, String password) {}
public record RegisterRequest(String username, String password, String confirmPassword, Long tariffId) {}
public record LoginUserJwt(String statusCode, String jwt) {}
public record OAuthTicketExchangeRequest(String ticket) {}
```

La decision importante es que Angular no trabaja directamente con la sesion OAuth2 de Spring. El backend la convierte en un JWT propio para mantener el mismo modelo de seguridad en login clasico y social.

### 3.2. `DeviceController`

**Archivo:** `controllers/DeviceController.java`
**Ruta base:** `/api/v1/devices`

| Metodo | Endpoint | Parametros | Entrada | Salida | Servicio |
|---|---|---|---|---|---|
| `GET` | `/api/v1/devices` | `Principal` | No | `List<DeviceDto>` | `listByUsername(principal.getName())` |
| `GET` | `/api/v1/devices/{id}` | `id`, `Principal` | No | `DeviceDto` o `403` | Comprueba propietario antes de responder. |
| `POST` | `/api/v1/devices` | No | `DeviceDto` | `201 DeviceDto` | Alta directa heredada mediante `save`. |
| `POST` | `/api/v1/devices/claim` | `Principal` | `DeviceDto` parcial | `DeviceDto` | Reclama o registra un Shelly fisico por MAC. |
| `POST` | `/api/v1/devices/simulated/demo-pack` | `Principal` | No | `201 List<DeviceDto>` | Crea perfiles demo que falten. |
| `POST` | `/api/v1/devices/simulated` | `Principal` | `CreateSimulatedDeviceRequest` | `201 DeviceDto` | Crea simulador individual. |
| `PUT` | `/api/v1/devices/{id}` | `id`, `Principal` | `DeviceDto` | `DeviceDto` | Actualiza nombre, estado y perfil si es simulado. |
| `DELETE` | `/api/v1/devices/{id}` | `id`, `Principal` | No | `204 No Content` | Borra lecturas, alertas y dispositivo. |

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

Para simuladores se usa:

```java
public record CreateSimulatedDeviceRequest(
        String name,
        SimulationProfile simulationProfile
) {}
```

El flujo recomendado para usuarios es `claim` o `simulated`, porque en ambos casos el propietario se toma del `Principal`. El endpoint directo `POST /devices` sigue activo y persiste el `DeviceDto` recibido, por lo que se considera un endpoint legado o de administracion tecnica.

### 3.3. `ReadingController`

**Archivo:** `controllers/ReadingController.java`
**Ruta base:** `/api/v1/readings`

| Metodo | Endpoint | Parametros | Salida |
|---|---|---|---|
| `GET` | `/api/v1/readings` | `Principal` | Todas las lecturas de los dispositivos del usuario. |
| `GET` | `/api/v1/readings/latest/{macAddress}` | `macAddress`, `Principal` | Ultima lectura de esa MAC. |
| `GET` | `/api/v1/readings/device/{macAddress}/recent` | `macAddress`, query `seconds` con defecto `120`, `Principal` | Lecturas recientes por intervalo. |
| `GET` | `/api/v1/readings/search` | query `time` ISO-8601, `macAddress`, `Principal` | Lectura por clave compuesta. |
| `DELETE` | `/api/v1/readings/search` | query `time` ISO-8601, `macAddress`, `Principal` | Borra una lectura concreta. |

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

Todos los endpoints que reciben una MAC verifican primero que el dispositivo pertenece al usuario autenticado. Esto es importante porque la MAC no debe actuar como llave publica para consultar datos de otros usuarios.

### 3.4. `ConsumptionController`

**Archivo:** `controllers/ConsumptionController.java`
**Ruta base:** `/api/v1/analytics`

| Metodo | Endpoint | Query params | Salida |
|---|---|---|---|
| `GET` | `/api/v1/analytics/cost` | `macAddress`, `start`, `end` | `Map` con `macAddress`, `totalCostEur`, `start`, `end`. |
| `GET` | `/api/v1/analytics/ghost-consumption` | `macAddress`, `start`, `end` | `Map` con `macAddress`, `ghostCostEur`, `start`, `end`. |

Los dos endpoints validan propiedad del dispositivo antes de llamar a `ConsumptionService`. No hay DTO de respuesta como clase Java; la respuesta se construye como `Map<String, Object>`.

Ejemplo de llamada:

```http
GET /api/v1/analytics/cost?macAddress=SIM000000001&start=2026-09-02T00:00:00Z&end=2026-09-02T22:00:00Z
Authorization: Bearer <jwt>
```

### 3.5. `TariffController`

**Archivo:** `controllers/TariffController.java`
**Ruta base:** `/api/v1/tariffs`

| Metodo | Endpoint | Entrada | Salida | Seguridad |
|---|---|---|---|---|
| `GET` | `/api/v1/tariffs` | No | `List<TariffDto>` | Autenticado. |
| `GET` | `/api/v1/tariffs/{id}` | No | `TariffDto` | Autenticado. |
| `POST` | `/api/v1/tariffs` | `TariffDto` | `201 TariffDto` | `ROLE_ADMIN`. |
| `POST` | `/api/v1/tariffs/{id}` | `TariffDto` | `TariffDto` | `ROLE_ADMIN`. |
| `DELETE` | `/api/v1/tariffs/{id}` | No | `204 No Content` | `ROLE_ADMIN`. |

El update usa `POST /{id}` en vez de `PUT`. Angular lo respeta en `TariffService.updateCatalogTariff`.

DTO:

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

### 3.6. `UserTariffController`

**Archivo:** `controllers/UserTariffController.java`
**Ruta base:** `/api/v1/users/me/tariff`

| Metodo | Endpoint | Entrada | Salida |
|---|---|---|---|
| `GET` | `/api/v1/users/me/tariff` | No | `200 TariffDto` o `204 No Content`. |
| `POST` | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto`. |
| `DELETE` | `/api/v1/users/me/tariff` | No | `204 No Content`. |

DTO de entrada:

```java
public record UserTariffRequest(
        Long templateTariffId,
        TariffDto contract
) {}
```

El controlador no acepta `userId`. El usuario sale siempre del JWT (`Principal`). Esta decision simplifica la seguridad porque impide que un usuario envie el id de otro en el body.

Modos soportados por el servicio:

1. `templateTariffId`: clona una plantilla.
2. `templateTariffId + contract`: clona y aplica cambios.
3. `contract`: crea o actualiza el contrato privado directamente.

### 3.7. `AlertController`

**Archivo:** `controllers/AlertController.java`
**Ruta base:** `/api/v1/alerts`

| Metodo | Endpoint | Parametros | Salida |
|---|---|---|---|
| `GET` | `/api/v1/alerts` | `Principal` | `List<AlertDto>` del usuario autenticado. |
| `DELETE` | `/api/v1/alerts/{id}` | `id`, `Principal` | `204 No Content` o `404` si no pertenece al usuario. |

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

La alerta se crea en `AlertService.checkPowerThreshold`, no en el controlador. El controlador solo lista y permite descartar alertas ya generadas.

## 4. Gestion de errores

`GlobalExceptionHandler` centraliza la respuesta de errores:

| Excepcion | HTTP | Mensaje |
|---|---|---|
| `EntityNotFoundException` | 404 | Recurso no encontrado. |
| `BadCredentialsException` | 401 | Credenciales incorrectas. |
| `IllegalStateException` | 400 | Regla de negocio incumplida. |
| `UsernameNotFoundException` | 401 | Usuario no valido en sesion. |
| `ForbiddenException` | 403 | Acceso denegado. |
| `DataIntegrityViolationException` | 400, 409 o 500 | Depende de unicidad o FK. |
| `Exception` | 500 | Error interno generico. |

DTO de error:

```java
public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {}
```

## 5. Servicios y decisiones de negocio

### `DeviceService`

Gestiona dispositivos fisicos y simulados. Las decisiones mas importantes son:

- Las MAC simuladas usan prefijo `SIM` y una secuencia textual (`SIM000000001`, etc.).
- `createDemoSimulatorPack` recorre todos los `SimulationProfile` y solo crea los que el usuario no tiene.
- `deleteById` borra primero lecturas y alertas, y despues el dispositivo. Este orden evita que PostgreSQL bloquee el borrado por claves foraneas.

### `TariffService`

Valida contratos antes de persistir:

- `2.0TD`: energia en P1-P3 y potencia en P1-P2.
- `3.0TD`, `6.1TD`, `6.2TD`: energia y potencia en P1-P6.
- En peajes de seis periodos, la potencia debe cumplir P1 <= P2 <= P3 <= P4 <= P5 <= P6.

En update aplica `clear + flush + rebuild` sobre colecciones para evitar choques contra la restriccion unica `(tariff_id, period_code)`.

### `UserTariffService`

Clona plantillas del catalogo para crear contratos privados. La plantilla global queda intacta, y el usuario puede editar precios y potencias sin afectar a otros usuarios.

### `ConsumptionService`

Calcula coste recorriendo lecturas ordenadas:

1. Recupera lecturas por MAC y rango temporal.
2. Calcula delta positivo entre odometros `energyTotalKwh`.
3. Resuelve el periodo aplicable con `CalendarResolverService`.
4. Multiplica el delta por el precio `priceKwh`.
5. Redondea el total a dos decimales.

El consumo fantasma usa la misma base, pero filtra por hora local 00:00-05:59.

## 6. Endpoints no REST relacionados

| Tipo | Ruta o destino | Uso |
|---|---|---|
| WebSocket STOMP | `/ws-iot` | Endpoint de handshake para Angular. |
| Broker STOMP | `/topic/readings/{macAddress}` | Lecturas en tiempo real. |
| Broker STOMP | `/topic/alerts/{username}` | Alertas de maximetro. |
| OAuth2 | `/oauth2/authorization/{provider}` | Inicio del login social. |
| OAuth2 callback | `/login/oauth2/code/{provider}` | Callback gestionado por Spring Security. |

## 7. Controladores no activos

Existen `DeviceStateController`, `ReactiveDeviceStateController` y `DeviceCommandController`, pero estan comentados. No forman parte de la API activa y por eso no se han incluido como endpoints disponibles.

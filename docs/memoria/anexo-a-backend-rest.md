# Anexo A. Controladores REST de Spring Boot

Este anexo documenta los controladores activos del backend. Solo se incluyen rutas reales anotadas con `@RestController`; las clases `DeviceStateController`, `ReactiveDeviceStateController` y `DeviceCommandController` están comentadas y no exponen endpoints.

## A.1. Seguridad común de la API

La configuración principal está en `SecurityConfig`.

- La API funciona sin sesión de servidor (`SessionCreationPolicy.STATELESS`).
- El token JWT se valida antes de las rutas privadas mediante `JwtValidatorFilter`.
- Son públicas las rutas:
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/register/admin`
  - `POST /api/v1/auth/oauth/exchange`
  - `/oauth2/authorization/**`
  - `/login/oauth2/code/**`
  - `/ws-iot/**`
- `GET /api/v1/tariffs/**` requiere usuario autenticado.
- Las mutaciones de `/api/v1/tariffs/**` requieren `ROLE_ADMIN`.
- El resto de rutas requiere autenticación.

El frontend envía el token con:

```http
Authorization: Bearer <jwt>
X-Requested-With: XMLHttpRequest
```

## A.2. Gestión de errores

`GlobalExceptionHandler` centraliza las respuestas de error con el DTO `ErrorResponse`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Mensaje concreto de la regla de negocio",
  "timestamp": "2026-11-06T22:00:00"
}
```

| Excepción | HTTP | Uso real |
|---|---:|---|
| `EntityNotFoundException` | 404 | Recurso no encontrado: dispositivo, lectura o tarifa. |
| `BadCredentialsException` | 401 | Login con credenciales incorrectas. |
| `UsernameNotFoundException` | 401 | Usuario del token no localizado. |
| `ForbiddenException` | 403 | Alta de administrador con secreto inválido. |
| `IllegalStateException` | 400 | Validación de negocio: tarifa inválida, dispositivo ajeno, datos incompletos. |
| `DataIntegrityViolationException` | 400/409/500 | Colisiones de unicidad o conflictos de FK. |

## A.3. `AuthController`

Ruta base: `/api/v1/auth`.

| Método | Endpoint | Entrada | Salida | Descripción |
|---|---|---|---|---|
| `POST` | `/login` | `LoginUser` | `LoginUserJwt` | Autentica usuario y contraseña y emite JWT. |
| `POST` | `/register` | `RegisterRequest` | `204/201 sin cuerpo` | Registra usuario normal `ROLE_USER`. |
| `POST` | `/register/admin` | `RegisterRequest` + cabecera `X-Wattimizer-Admin-Secret` | `201 sin cuerpo` | Crea administrador si la cabecera coincide con la configuración. |
| `POST` | `/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | Intercambia ticket OAuth2 temporal por JWT propio de la aplicación. |

### DTOs

```java
public record LoginUser(String username, String password) {}

public record RegisterRequest(
        String username,
        String password,
        String confirmPassword,
        Long tariffId
) {}

public record LoginUserJwt(String statusCode, String jwt) {}

public record OAuthTicketExchangeRequest(String ticket) {}
```

### Intención del controlador

El controlador no contiene la validación completa de registro. Esa lógica vive en `AuthRegistrationService`, que normaliza el email a minúsculas, exige contraseña de al menos 6 caracteres y cifra la contraseña con `PasswordEncoder`. La generación del JWT se delega en `JwtTokenService`, que añade los claims `username` y `authorities` con caducidad de 8 horas.

## A.4. `DeviceController`

Ruta base: `/api/v1/devices`.

| Método | Endpoint | Parámetros | Entrada | Salida | Descripción |
|---|---|---|---|---|---|
| `GET` | `/` | `Principal` | - | `List<DeviceDto>` | Lista los dispositivos del usuario autenticado. |
| `GET` | `/{id}` | `id` | - | `DeviceDto` | Devuelve un dispositivo solo si pertenece al usuario. |
| `POST` | `/` | - | `DeviceDto` | `DeviceDto` | Guarda un dispositivo directamente. Es una excepción interna: no asigna propietario desde `Principal`. |
| `POST` | `/claim` | `Principal` | `DeviceDto` | `DeviceDto` | Vincula o registra un dispositivo físico por MAC para el usuario actual. |
| `POST` | `/simulated/demo-pack` | `Principal` | - | `List<DeviceDto>` | Crea los simuladores de demostración que el usuario aún no tenga. |
| `POST` | `/simulated` | `Principal` | `CreateSimulatedDeviceRequest` | `DeviceDto` | Crea un simulador individual. |
| `PUT` | `/{id}` | `id`, `Principal` | `DeviceDto` | `DeviceDto` | Actualiza nombre, estado y perfil si es simulado. |
| `DELETE` | `/{id}` | `id`, `Principal` | - | `204` | Borra dispositivo tras comprobar propietario. |

### DTOs

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

### Reglas de negocio

- `claimOrRegisterDevice()` busca la MAC. Si no existe, crea un dispositivo físico asociado al usuario.
- Si existe y pertenece a otro usuario que no sea `SYSTEM`, se rechaza para evitar apropiación de dispositivos.
- Los simuladores usan MAC generadas con prefijo `SIM` y secuencia numérica.
- `createDemoSimulatorPack()` recorre todos los `SimulationProfile` y evita duplicar perfiles ya existentes en la cuenta.
- `deleteById()` elimina primero lecturas y alertas del dispositivo, después borra el registro de `devices`.

## A.5. `ReadingController`

Ruta base: `/api/v1/readings`.

| Método | Endpoint | Parámetros | Salida | Descripción |
|---|---|---|---|---|
| `GET` | `/` | `Principal` | `List<ReadingResponse>` | Todas las lecturas de dispositivos del usuario. |
| `GET` | `/latest/{macAddress}` | `macAddress`, `Principal` | `ReadingResponse` | Última lectura conocida de una MAC propia. |
| `GET` | `/device/{macAddress}/recent` | `macAddress`, `seconds` opcional, `Principal` | `List<ReadingResponse>` | Lecturas recientes en los últimos segundos; por defecto 120. |
| `GET` | `/search` | `time`, `macAddress`, `Principal` | `ReadingResponse` | Busca por clave compuesta temporal. |
| `DELETE` | `/search` | `time`, `macAddress`, `Principal` | `204` | Borra una lectura concreta por tiempo y MAC. |

### DTO

```java
public record ReadingResponse(
        Instant time,
        String macAddress,
        BigDecimal powerW,
        BigDecimal energyTotalKwh,
        Boolean isOn
) {}
```

### Flujo de autorización

Antes de consultar o borrar por MAC, el controlador recupera el dispositivo con `DeviceService.findByMacAddress(macAddress)` y compara `device.username()` con `principal.getName()`. Así no basta con conocer una MAC para consultar lecturas de otra empresa.

## A.6. `ConsumptionController`

Ruta base: `/api/v1/analytics`.

| Método | Endpoint | Query params | Salida | Descripción |
|---|---|---|---|---|
| `GET` | `/cost` | `macAddress`, `start`, `end` | `Map<String,Object>` | Coste energético acumulado del intervalo. |
| `GET` | `/ghost-consumption` | `macAddress`, `start`, `end` | `Map<String,Object>` | Coste del consumo entre las 00:00 y las 05:59 en hora local del contrato. |

Ejemplo de respuesta de coste:

```json
{
  "macAddress": "9070694d3590",
  "totalCostEur": 1.42,
  "start": "2026-11-06T00:00:00Z",
  "end": "2026-11-06T22:00:00Z"
}
```

### Lógica de cálculo

`ConsumptionService` obtiene las lecturas ordenadas por tiempo con `findReadingsInInterval`. Después calcula deltas positivos entre valores consecutivos de `energyTotalKwh`. Si el contador baja o hay nulos, se ignora ese tramo porque puede indicar reinicio del dispositivo o mensaje incompleto.

Para cada delta válido:

1. Resuelve el periodo regulatorio con `CalendarResolverService`.
2. Busca el precio contractual en `periods`.
3. Multiplica delta kWh por precio €/kWh.
4. Redondea el total a dos decimales.

## A.7. `AlertController`

Ruta base: `/api/v1/alerts`.

| Método | Endpoint | Parámetros | Salida | Descripción |
|---|---|---|---|---|
| `GET` | `/` | `Principal` | `List<AlertDto>` | Lista alertas del usuario. |
| `DELETE` | `/{id}` | `id`, `Principal` | `204` | Borra una alerta si pertenece al usuario. |

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

`AlertService.checkPowerThreshold()` se ejecuta después de guardar cada lectura. La alerta solo se genera si existe usuario, tarifa, periodo aplicable y potencia contratada para ese periodo.

## A.8. `TariffController`

Ruta base: `/api/v1/tariffs`.

| Método | Endpoint | Rol | Entrada | Salida | Descripción |
|---|---|---|---|---|---|
| `GET` | `/` | Autenticado | - | `List<TariffDto>` | Lista el catálogo maestro, excluyendo clones privados asignados a usuarios. |
| `GET` | `/{id}` | Autenticado | - | `TariffDto` | Devuelve una tarifa por id. |
| `POST` | `/` | `ROLE_ADMIN` | `TariffDto` | `TariffDto` | Crea tarifa de catálogo. |
| `POST` | `/{id}` | `ROLE_ADMIN` | `TariffDto` | `TariffDto` | Actualiza tarifa. El backend usa `POST` y no `PUT`. |
| `DELETE` | `/{id}` | `ROLE_ADMIN` | - | `204` | Borra tarifa si no está asignada a usuarios. |

### DTOs de tarifa

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

public record PeriodDto(Long id, String periodCode, BigDecimal priceKwh) {}

public record TariffContractedPowerDto(
        Long id,
        String periodCode,
        BigDecimal contractedPowerKw
) {}
```

### Validaciones principales

`TariffService.validateTariffContract()` aplica reglas de la Circular CNMC 3/2020:

- `2.0TD`: energía `P1`, `P2`, `P3`; potencia `P1`, `P2`.
- `3.0TD`, `6.1TD`, `6.2TD`: energía y potencia `P1` a `P6`.
- En peajes de seis periodos, las potencias deben cumplir `P1 <= P2 <= ... <= P6`.
- No se permiten precios ni potencias menores o iguales a cero.
- No se permiten códigos de periodo duplicados dentro de la misma tarifa.

## A.9. `UserTariffController`

Ruta base: `/api/v1/users/me/tariff`.

| Método | Endpoint | Entrada | Salida | Descripción |
|---|---|---|---|---|
| `GET` | `/` | - | `TariffDto` o `204` | Devuelve la tarifa privada del usuario autenticado. |
| `POST` | `/` | `UserTariffRequest` | `TariffDto` | Asigna, clona o actualiza la tarifa privada. |
| `DELETE` | `/` | - | `204` | Desvincula y elimina la tarifa privada. |

```java
public record UserTariffRequest(
        Long templateTariffId,
        TariffDto contract
) {}
```

El diseño evita recibir `userId` desde Angular. El usuario propietario se obtiene siempre del `Principal`, lo que reduce el riesgo de modificar contratos de otra cuenta.

## A.10. Resumen de endpoints

| Área | Endpoints principales |
|---|---|
| Auth | `POST /api/v1/auth/login`, `POST /api/v1/auth/register`, `POST /api/v1/auth/register/admin`, `POST /api/v1/auth/oauth/exchange` |
| Devices | `GET/POST /api/v1/devices`, `GET/PUT/DELETE /api/v1/devices/{id}`, `POST /api/v1/devices/claim`, `POST /api/v1/devices/simulated`, `POST /api/v1/devices/simulated/demo-pack` |
| Readings | `GET /api/v1/readings`, `GET /api/v1/readings/latest/{macAddress}`, `GET /api/v1/readings/device/{macAddress}/recent`, `GET/DELETE /api/v1/readings/search` |
| Analytics | `GET /api/v1/analytics/cost`, `GET /api/v1/analytics/ghost-consumption` |
| Alerts | `GET /api/v1/alerts`, `DELETE /api/v1/alerts/{id}` |
| Tariffs | `GET/POST /api/v1/tariffs`, `GET/POST/DELETE /api/v1/tariffs/{id}` |
| User tariff | `GET/POST/DELETE /api/v1/users/me/tariff` |

# Anexo A. Controladores REST de Spring Boot

Este anexo documenta los controladores REST activos del backend de Wattimizer. La API se encuentra bajo el prefijo `/api/v1` y usa JSON como formato de intercambio. La autenticación general se basa en JWT enviado en la cabecera `Authorization: Bearer <token>`.

## A.1. Visión general

| Controlador | Ruta base | Responsabilidad |
|---|---|---|
| `AuthController` | `/api/v1/auth` | Login, registro, registro admin e intercambio de ticket OAuth2. |
| `DeviceController` | `/api/v1/devices` | CRUD de dispositivos físicos y simulados. |
| `ReadingController` | `/api/v1/readings` | Consulta y borrado de lecturas. |
| `ConsumptionController` | `/api/v1/analytics` | Cálculo de coste energético y consumo fantasma. |
| `TariffController` | `/api/v1/tariffs` | Catálogo maestro de tarifas. |
| `UserTariffController` | `/api/v1/users/me/tariff` | Tarifa privada del usuario autenticado. |
| `AlertController` | `/api/v1/alerts` | Consulta y borrado de alertas. |

La decisión de separar catálogo y tarifa privada es importante. El catálogo representa plantillas reutilizables, mientras que `/users/me/tariff` trabaja siempre con la identidad del JWT y evita recibir `userId` por URL o body. Así se reduce el riesgo de que un usuario acceda a recursos de otro.

## A.2. Seguridad y autorización

`SecurityConfig` configura:

- Sesión `STATELESS`.
- CSRF desactivado, porque la API se consume con JWT y no con sesión de servidor.
- CORS configurable mediante `app.cors.allowed-origins`.
- `JwtValidatorFilter` antes de `BasicAuthenticationFilter`.
- Rutas públicas: login, registro, registro admin, intercambio OAuth2, endpoints OAuth2 y `/ws-iot/**`.
- Lectura del catálogo de tarifas para usuarios autenticados.
- Mutaciones del catálogo de tarifas solo para `ROLE_ADMIN`.

La autorización por recurso se realiza principalmente con `Principal`:

```java
if (!device.username().equals(principal.getName())) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

Este patrón aparece en dispositivos, lecturas y analítica. En cambio, el endpoint legado `POST /api/v1/devices` guarda el `DeviceDto` recibido sin usar `Principal`; por eso la interfaz actual prefiere `POST /claim` para dispositivos físicos y `POST /simulated` para simuladores.

## A.3. Autenticación

### Endpoints

| Método | Ruta | Entrada | Salida | Estado |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `LoginUser` | `LoginUserJwt` | `200 OK` |
| `POST` | `/api/v1/auth/register` | `RegisterRequest` | Sin cuerpo | `201 Created` |
| `POST` | `/api/v1/auth/register/admin` | `RegisterRequest` + `X-Wattimizer-Admin-Secret` | Sin cuerpo | `201 Created` |
| `POST` | `/api/v1/auth/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | `200 OK` |

### DTOs

```java
public record LoginUser(String username, String password) {}
```

```java
public record LoginUserJwt(String statusCode, String jwt) {}
```

```java
public record RegisterRequest(
        String username,
        String password,
        String confirmPassword,
        Long tariffId
) {}
```

```java
public record OAuthTicketExchangeRequest(String ticket) {}
```

### Flujo OAuth2

El flujo OAuth2 no expone directamente el JWT en la URL. Tras autenticarse con Google o GitHub, el backend crea un ticket temporal y redirige al frontend. Angular llama después a `/api/v1/auth/oauth/exchange` para cambiar ese ticket por el JWT propio de la aplicación. La intención es que la URL de callback no transporte credenciales largas.

## A.4. Dispositivos

### Endpoints

| Método | Ruta | Parámetros | Body | Salida | Estado |
|---|---|---|---|---|---|
| `GET` | `/api/v1/devices` | `Principal` | - | `List<DeviceDto>` | `200 OK` |
| `GET` | `/api/v1/devices/{id}` | `id`, `Principal` | - | `DeviceDto` | `200 OK` o `403` |
| `POST` | `/api/v1/devices` | - | `DeviceDto` | `DeviceDto` | `201 Created` |
| `POST` | `/api/v1/devices/claim` | `Principal` | `DeviceDto` | `DeviceDto` | `200 OK` |
| `POST` | `/api/v1/devices/simulated/demo-pack` | `Principal` | - | `List<DeviceDto>` | `201 Created` |
| `POST` | `/api/v1/devices/simulated` | `Principal` | `CreateSimulatedDeviceRequest` | `DeviceDto` | `201 Created` |
| `PUT` | `/api/v1/devices/{id}` | `id`, `Principal` | `DeviceDto` | `DeviceDto` | `200 OK` |
| `DELETE` | `/api/v1/devices/{id}` | `id`, `Principal` | - | Sin cuerpo | `204 No Content` |

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
```

```java
public record CreateSimulatedDeviceRequest(
        String name,
        SimulationProfile simulationProfile
) {}
```

### Intención de diseño

El controlador distingue tres formas de alta:

1. **Alta directa:** `POST /devices`. Existe en la API, pero no comprueba propietario en el controlador.
2. **Reclamación de físico:** `POST /devices/claim`. Usa la MAC y vincula el dispositivo al usuario del JWT.
3. **Simulador:** `POST /devices/simulated`. Genera una MAC `SIM...`, asigna perfil de consumo y marca `is_simulated=true`.

El pack demo crea un simulador por cada valor de `SimulationProfile` si el usuario todavía no tiene ese perfil. Esto permite demostrar el panel con horno, lavadora, nevera, standby o carga alta sin depender del Shelly físico.

## A.5. Lecturas

### Endpoints

| Método | Ruta | Parámetros | Salida | Estado |
|---|---|---|---|---|
| `GET` | `/api/v1/readings` | `Principal` | `List<ReadingResponse>` | `200 OK` |
| `GET` | `/api/v1/readings/latest/{macAddress}` | `macAddress`, `Principal` | `ReadingResponse` | `200 OK` o `403` |
| `GET` | `/api/v1/readings/device/{macAddress}/recent` | `macAddress`, `seconds=120`, `Principal` | `List<ReadingResponse>` | `200 OK` o `403` |
| `GET` | `/api/v1/readings/search` | `time`, `macAddress`, `Principal` | `ReadingResponse` | `200 OK` o `403` |
| `DELETE` | `/api/v1/readings/search` | `time`, `macAddress`, `Principal` | Sin cuerpo | `204 No Content` |

Los parámetros temporales usan `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)`, por lo que el frontend envía fechas ISO-8601.

### DTO de salida

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

Los endpoints por MAC primero buscan el dispositivo y después comparan su propietario con `principal.getName()`. Así, aunque un usuario conozca la MAC de otro dispositivo, no puede consultar sus lecturas.

## A.6. Analítica de consumo

### Endpoints

| Método | Ruta | Query params | Salida |
|---|---|---|---|
| `GET` | `/api/v1/analytics/cost` | `macAddress`, `start`, `end` | Mapa JSON con `macAddress`, `totalCostEur`, `start`, `end`. |
| `GET` | `/api/v1/analytics/ghost-consumption` | `macAddress`, `start`, `end` | Mapa JSON con `macAddress`, `ghostCostEur`, `start`, `end`. |

Estos endpoints no usan un record específico de respuesta. Construyen un `Map<String, Object>` porque devuelven pocas métricas y el contrato todavía es simple.

### Lógica

`ConsumptionService` obtiene lecturas ordenadas del intervalo y calcula el coste por pares consecutivos:

1. Calcula `deltaKwh = current.energyTotalKwh - previous.energyTotalKwh`.
2. Descarta deltas nulos o negativos, porque pueden indicar reinicio del contador.
3. Resuelve el periodo tarifario en `CalendarResolverService`.
4. Multiplica el delta por `priceKwh`.
5. Redondea a dos decimales.

El consumo fantasma reutiliza la misma lógica, pero solo suma lecturas cuya hora local está entre las 00:00 y las 05:59.

## A.7. Tarifas

### Catálogo maestro

| Método | Ruta | Body | Salida | Seguridad |
|---|---|---|---|---|
| `GET` | `/api/v1/tariffs` | - | `List<TariffDto>` | Usuario autenticado |
| `GET` | `/api/v1/tariffs/{id}` | - | `TariffDto` | Usuario autenticado |
| `POST` | `/api/v1/tariffs` | `TariffDto` | `TariffDto` | `ROLE_ADMIN` |
| `POST` | `/api/v1/tariffs/{id}` | `TariffDto` | `TariffDto` | `ROLE_ADMIN` |
| `DELETE` | `/api/v1/tariffs/{id}` | - | Sin cuerpo | `ROLE_ADMIN` |

La actualización usa `POST /{id}` en lugar de `PUT`. El frontend lo respeta en `TariffService.updateCatalogTariff`.

### Tarifa privada de usuario

| Método | Ruta | Body | Salida |
|---|---|---|---|
| `GET` | `/api/v1/users/me/tariff` | - | `TariffDto` o `204 No Content` |
| `POST` | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` |
| `DELETE` | `/api/v1/users/me/tariff` | - | `204 No Content` |

`UserTariffController` no acepta IDs de usuario. El propietario siempre sale del token JWT, que es una decisión correcta para evitar IDOR.

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
```

```java
public record PeriodDto(Long id, String periodCode, BigDecimal priceKwh) {}
```

```java
public record TariffContractedPowerDto(
        Long id,
        String periodCode,
        BigDecimal contractedPowerKw
) {}
```

```java
public record UserTariffRequest(
        Long templateTariffId,
        TariffDto contract
) {}
```

`UserTariffRequest` permite tres usos: clonar una plantilla, clonar y sobrescribir valores, o guardar directamente un contrato privado.

## A.8. Alertas

| Método | Ruta | Parámetros | Salida |
|---|---|---|---|
| `GET` | `/api/v1/alerts` | `Principal` | `List<AlertDto>` |
| `DELETE` | `/api/v1/alerts/{id}` | `id`, `Principal` | `204 No Content` |

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

Las alertas se generan fuera del controlador, después de persistir cada lectura. `AlertService.checkPowerThreshold` resuelve el periodo aplicable, busca la potencia contratada y compara el valor real en kW. Si el consumo supera el límite, guarda una alerta `OVERPOWER`.

## A.9. Manejo de errores

`GlobalExceptionHandler` transforma errores técnicos en respuestas JSON más estables:

| Excepción | Estado |
|---|---|
| `EntityNotFoundException` | `404 Not Found` |
| `BadCredentialsException` | `401 Unauthorized` |
| `IllegalStateException` | `400 Bad Request` |
| `UsernameNotFoundException` | `401 Unauthorized` |
| `ForbiddenException` | `403 Forbidden` |
| `DataIntegrityViolationException` | `400`, `409` o `500` según el caso |
| `Exception` | `500 Internal Server Error` |

DTO de error:

```java
public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {}
```

Hay algunos `403` devueltos directamente con `ResponseEntity.status(HttpStatus.FORBIDDEN).build()`, por lo que no todos los errores de autorización tienen cuerpo JSON.

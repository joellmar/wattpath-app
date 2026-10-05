# Anexo A - Backend REST con Spring Boot

## 1. Vision general

El backend de Wattimizer esta desarrollado con **Spring Boot 4.0.5** y expone una API REST bajo el prefijo `/api/v1`. La aplicacion esta organizada en controladores, servicios, repositorios, entidades JPA, DTOs y mappers. Esta separacion permite que el controlador sea fino: recibe la peticion, obtiene el usuario autenticado cuando hace falta y delega la regla de negocio en servicios.

La decision de seguridad en los endpoints principales de usuario es resolver el propietario desde `Principal`, que viene del JWT validado por Spring Security. Esto se ve claramente en lecturas, alertas, tarifa privada, `claim`, simuladores, actualizacion y borrado de dispositivos. La ruta de alta directa `POST /api/v1/devices` es una excepcion del codigo actual: persiste el `DeviceDto` recibido y, por tanto, el flujo recomendado para usuarios finales es `/api/v1/devices/claim`.

## 2. Seguridad y autenticacion

La configuracion principal esta en `SecurityConfig`:

- La sesion es **stateless** (`SessionCreationPolicy.STATELESS`).
- CSRF esta desactivado porque el cliente usa JWT y API JSON.
- CORS se configura con la propiedad `app.cors.allowed-origins`.
- Las rutas de login, registro, OAuth2 y WebSocket son publicas.
- Las rutas de tarifas permiten lectura a usuarios autenticados, pero escritura solo a `ROLE_ADMIN`.
- El filtro `JwtValidatorFilter` se ejecuta antes de `BasicAuthenticationFilter`.

### Endpoints de autenticacion

| Metodo | Ruta | Entrada | Salida | Codigo esperado |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/auth/login` | `LoginUser` | `LoginUserJwt` | `200 OK` |
| `POST` | `/api/v1/auth/register` | `RegisterRequest` | Sin cuerpo | `201 Created` |
| `POST` | `/api/v1/auth/register/admin` | `RegisterRequest` + cabecera `X-Wattimizer-Admin-Secret` | Sin cuerpo | `201 Created` |
| `POST` | `/api/v1/auth/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | `200 OK` |

#### DTOs de autenticacion

```java
public record LoginUser(String username, String password) {}

public record RegisterRequest(
    String username,
    String password,
    String confirmPassword,
    Long tariffId
) {}

public record OAuthTicketExchangeRequest(String ticket) {}

public record LoginUserJwt(String status, String jwt) {}
```

El login valida credenciales con `UserProviderDetailsManager`. Si la autenticacion es correcta, `JwtTokenService` genera un JWT con el nombre de usuario y sus roles. En OAuth2 no se entrega el JWT directamente en la redireccion: primero se genera un ticket temporal de un solo uso y Angular lo intercambia en `/oauth/exchange`.

## 3. Gestion de dispositivos

Controlador: `DeviceController`
Servicio principal: `DeviceService`

### Endpoints

| Metodo | Ruta | Parametros | Body | Respuesta | Uso |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/v1/devices` | Usuario desde JWT | No | `List<DeviceDto>` | Lista solo los dispositivos del usuario autenticado. |
| `GET` | `/api/v1/devices/{id}` | `id` path | No | `DeviceDto` | Devuelve un dispositivo si pertenece al usuario. |
| `POST` | `/api/v1/devices` | No | `DeviceDto` | `DeviceDto` con `201` | Alta directa de dispositivo; no usa `Principal`, por lo que no es el flujo preferente para usuario final. |
| `POST` | `/api/v1/devices/claim` | Usuario desde JWT | `DeviceDto` parcial | `DeviceDto` | Reclama un dispositivo por MAC o lo crea si no existe. |
| `POST` | `/api/v1/devices/simulated` | Usuario desde JWT | `CreateSimulatedDeviceRequest` | `DeviceDto` con `201` | Crea un simulador con perfil de consumo. |
| `POST` | `/api/v1/devices/simulated/demo-pack` | Usuario desde JWT | Vacio | `List<DeviceDto>` con `201` | Crea un simulador por perfil disponible. |
| `PUT` | `/api/v1/devices/{id}` | `id` path | `DeviceDto` | `DeviceDto` | Actualiza nombre, estado y perfil si es simulado. |
| `DELETE` | `/api/v1/devices/{id}` | `id` path | No | Sin cuerpo | Elimina dispositivo tras borrar lecturas y alertas. |

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

### Decisiones de implementacion

- `claimOrRegisterDevice` permite vincular un dispositivo sin propietario o perteneciente a `SYSTEM`, pero bloquea dispositivos asignados a otra empresa.
- Los simuladores usan MACs sinteticas con prefijo `SIM` y una secuencia numerica. Esto evita colisiones con hardware real.
- `createDemoSimulatorPack` evita duplicar perfiles ya existentes para el mismo usuario.
- `deleteById` borra primero `readings` y `alerts` relacionados. La razon es practica: al existir claves foraneas, borrar el dispositivo antes podria provocar conflictos de integridad.

## 4. Lecturas electricas

Controlador: `ReadingController`
Servicio principal: `ReadingService`

### Endpoints

| Metodo | Ruta | Parametros | Respuesta | Uso |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/readings` | Usuario desde JWT | `List<ReadingResponse>` | Lecturas de dispositivos del usuario. |
| `GET` | `/api/v1/readings/latest/{macAddress}` | `macAddress` path | `ReadingResponse` | Ultima lectura de un dispositivo autorizado. |
| `GET` | `/api/v1/readings/device/{macAddress}/recent` | `macAddress`, query `seconds` por defecto `120` | `List<ReadingResponse>` | Historial reciente para inicializar la grafica. |
| `GET` | `/api/v1/readings/search` | `time`, `macAddress` | `ReadingResponse` | Busca una lectura por clave compuesta. |
| `DELETE` | `/api/v1/readings/search` | `time`, `macAddress` | Sin cuerpo | Borra una lectura concreta. |

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

La lectura se identifica por `time` y por dispositivo. El frontend representa `time` como `string | number` porque el JSON puede llegar serializado como texto ISO.

## 5. Analitica de consumo

Controlador: `ConsumptionController`
Servicio principal: `ConsumptionService`

### Endpoints

| Metodo | Ruta | Query params | Respuesta |
| --- | --- | --- | --- |
| `GET` | `/api/v1/analytics/cost` | `macAddress`, `start`, `end` como `Instant` ISO-8601 | `macAddress`, `totalCostEur`, `start`, `end` |
| `GET` | `/api/v1/analytics/ghost-consumption` | `macAddress`, `start`, `end` como `Instant` ISO-8601 | `macAddress`, `ghostCostEur`, `start`, `end` |

### Logica de coste

`ConsumptionService` no calcula el coste con una sola muestra aislada. Recupera lecturas ordenadas por tiempo y calcula el delta positivo entre el odometro `energy_total_kwh` de una lectura y la anterior. Despues pregunta a `CalendarResolverService` que periodo regulatorio aplica en ese instante y multiplica el delta por el precio `priceKwh` de la tarifa del usuario.

```text
delta_kWh = lectura_actual.energy_total_kwh - lectura_anterior.energy_total_kwh
coste = delta_kWh * precio_del_periodo
```

Si no hay al menos dos lecturas, si el dispositivo no tiene usuario, si el usuario no tiene tarifa o si el calendario regulatorio no resuelve periodo, el servicio devuelve `0`. Es una degradacion controlada: la API sigue funcionando aunque falte configuracion.

El consumo fantasma no equivale al periodo barato de la tarifa. En este proyecto se define como consumo entre las **00:00 y 05:59** de la zona horaria del contrato.

## 6. Tarifas

Controladores: `TariffController` y `UserTariffController`
Servicios: `TariffService` y `UserTariffService`

### Catalogo maestro

| Metodo | Ruta | Body | Rol | Respuesta |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/tariffs` | No | Autenticado | `List<TariffDto>` |
| `GET` | `/api/v1/tariffs/{id}` | No | Autenticado | `TariffDto` |
| `POST` | `/api/v1/tariffs` | `TariffDto` | `ROLE_ADMIN` | `TariffDto` con `201` |
| `POST` | `/api/v1/tariffs/{id}` | `TariffDto` | `ROLE_ADMIN` | `TariffDto` |
| `DELETE` | `/api/v1/tariffs/{id}` | No | `ROLE_ADMIN` | Sin cuerpo |

Aunque lo habitual seria usar `PUT` para actualizar, el backend actual expone `POST /api/v1/tariffs/{id}` y el frontend lo respeta en `TariffService.updateCatalogTariff`.

### Tarifa privada del usuario

| Metodo | Ruta | Body | Respuesta |
| --- | --- | --- | --- |
| `GET` | `/api/v1/users/me/tariff` | No | `200 TariffDto` o `204 No Content` |
| `POST` | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` |
| `DELETE` | `/api/v1/users/me/tariff` | No | Sin cuerpo |

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

### Validaciones de tarifa

`TariffService.validateTariffContract` aplica reglas basadas en tarifas TD:

- `2.0TD`: exige energia en `P1`, `P2`, `P3` y potencia en `P1`, `P2`.
- `3.0TD`, `6.1TD`, `6.2TD`: exigen energia y potencia en `P1` a `P6`.
- En tarifas de seis periodos, las potencias deben cumplir `P1 <= P2 <= ... <= P6`.
- Todos los importes y potencias deben ser mayores que cero.

Para actualizar colecciones hijas (`periods` y `contractedPowers`), el servicio usa el patron `clear + flush + rebuild`. Se hace asi porque Hibernate puede intentar insertar los nuevos periodos antes de borrar los anteriores, chocando con la restriccion unica `(tariff_id, period_code)`.

## 7. Alertas

Controlador: `AlertController`
Servicio principal: `AlertService`

### Endpoints

| Metodo | Ruta | Parametros | Respuesta |
| --- | --- | --- | --- |
| `GET` | `/api/v1/alerts` | Usuario desde JWT | `List<AlertDto>` |
| `DELETE` | `/api/v1/alerts/{id}` | `id` path y usuario desde JWT | Sin cuerpo |

### DTO

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

Las alertas se generan al guardar una lectura. `AlertService.checkPowerThreshold` resuelve el periodo aplicable, busca la potencia contratada de ese periodo y compara `powerW / 1000` contra el limite en kW. Si se supera, crea una alerta `OVERPOWER` y la envia por WebSocket a `/topic/alerts/{username}`.

## 8. Gestion de errores

`GlobalExceptionHandler` convierte excepciones en respuestas JSON uniformes:

| Excepcion | HTTP | Uso |
| --- | --- | --- |
| `EntityNotFoundException` | `404` | Recurso inexistente. |
| `BadCredentialsException` | `401` | Login incorrecto. |
| `UsernameNotFoundException` | `401` | Usuario no localizado desde sesion. |
| `ForbiddenException` | `403` | Acceso denegado, por ejemplo alta admin sin secreto correcto. |
| `IllegalStateException` | `400` | Violacion de regla de negocio. |
| `DataIntegrityViolationException` | `400`, `409` o `500` | Colisiones de unicidad o conflictos de FK. |

DTO de error:

```java
public record ErrorResponse(
    int status,
    String error,
    String message,
    LocalDateTime timestamp
) {}
```

## 9. Resumen de decisiones arquitectonicas

- **DTOs como records:** reducen codigo repetitivo y dejan claro el contrato JSON.
- **Servicios transaccionales:** las reglas con escritura se ejecutan dentro de transacciones para mantener consistencia.
- **Propietario desde JWT:** mejora la seguridad y simplifica las rutas REST.
- **Catalogo vs tarifa privada:** las plantillas globales no se modifican cuando un usuario personaliza su contrato; se clonan.
- **Lecturas como serie temporal:** la tabla `readings` esta preparada para TimescaleDB porque el volumen crece por tiempo, no por operaciones CRUD normales.

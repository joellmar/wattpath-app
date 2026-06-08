# Anexo 01. Backend Spring Boot y API REST

## 1. Papel del backend dentro de Wattimizer

El backend está implementado con Spring Boot y actúa como capa central entre el frontend Angular, la base de datos PostgreSQL/TimescaleDB y la entrada de telemetría MQTT. Su responsabilidad principal es exponer una API REST autenticada, almacenar dispositivos y lecturas, calcular indicadores económicos de consumo y publicar eventos en tiempo real mediante WebSocket/STOMP.

La aplicación usa una arquitectura por capas sencilla:

- **Controladores REST** en `controllers`, que reciben peticiones HTTP y devuelven DTOs.
- **Servicios de dominio** en `services`, donde se aplican reglas de negocio como la vinculación de dispositivos, el cálculo de costes o la generación de alertas.
- **Repositorios JPA** en `repositories`, que aíslan el acceso a datos.
- **DTOs y mappers MapStruct**, para no exponer directamente las entidades JPA al frontend.

## 2. Seguridad de la API

La configuración principal está en `SecurityConfig.java`. La API trabaja en modo **stateless**, por lo que no se mantiene sesión HTTP en el servidor. Cada petición protegida debe llevar un token JWT en la cabecera `Authorization`.

Reglas relevantes:

```java
.requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/ws-iot/**").permitAll()
.requestMatchers(HttpMethod.GET, "/api/v1/tariffs/**").authenticated()
.requestMatchers("/api/v1/tariffs/**").hasRole("ADMIN")
.requestMatchers("/admin/**").hasRole("ADMIN")
.anyRequest().authenticated()
```

Esto significa que:

- login, registro normal y WebSocket quedan abiertos;
- la lectura de tarifas exige usuario autenticado;
- la creación, edición y borrado de tarifas se restringe a administradores;
- el resto de endpoints requiere JWT válido.

El filtro `JwtValidatorFilter` se registra antes de `BasicAuthenticationFilter`, de forma que las peticiones REST se validan antes de llegar a los controladores.

## 3. Controlador de autenticación

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/AuthController.java`

Base URL: `/api/v1/auth`

| Método | Ruta | Entrada | Salida | Intención |
| --- | --- | --- | --- | --- |
| `POST` | `/login` | `LoginUser` | `LoginUserJwt` | Autentica credenciales y genera un JWT con usuario y roles. |
| `POST` | `/register` | `RegisterRequest` | `String` | Crea un usuario estándar con rol `ROLE_USER`. |
| `POST` | `/register/admin` | `RegisterRequest` + cabecera `X-Wattimizer-Admin-Secret` | `String` | Crea un usuario administrador si la clave de plataforma coincide. |

DTOs usados:

```java
public record LoginUser(String username, String password) {}

public record LoginUserJwt(String statusCode, String jwt) {}

public record RegisterRequest(String username, String password, Long tariffId) {}
```

El login delega en `UserProviderDetailsManager.authenticate`. Si la autenticación es correcta, genera un JWT firmado con la clave `jwt.secret` y una caducidad de ocho horas. En el token se guardan dos claims importantes: `username` y `authorities`.

En el registro normal, si `tariffId` viene informado, el controlador busca la tarifa con `TariffRepository.findById` y la asocia al usuario. Esta decisión permite que un usuario pueda registrarse ya vinculado a una configuración tarifaria.

## 4. Controlador de dispositivos

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/DeviceController.java`

Base URL: `/api/v1/devices`

| Método | Ruta | Parámetros / body | Salida | Regla aplicada |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/devices` | `Principal` | `List<DeviceDto>` | Lista solo los dispositivos del usuario autenticado. |
| `GET` | `/api/v1/devices/{id}` | `id`, `Principal` | `DeviceDto` | Comprueba que el dispositivo pertenezca al usuario. |
| `POST` | `/api/v1/devices` | `DeviceDto` | `DeviceDto` con `201 Created` | Crea un dispositivo desde el DTO recibido. |
| `POST` | `/api/v1/devices/claim` | `DeviceDto` con `macAddress` y `name` | `DeviceDto` | Reclama o registra un enchufe Shelly para el usuario actual. |
| `PUT` | `/api/v1/devices/{id}` | `id`, `DeviceDto`, `Principal` | `DeviceDto` | Actualiza nombre y estado virtual si el usuario es propietario. |
| `DELETE` | `/api/v1/devices/{id}` | `id`, `Principal` | `204 No Content` | Borra el dispositivo si pertenece al usuario. |

DTO principal:

```java
public record DeviceDto(
        Long id,
        String username,
        String name,
        String macAddress,
        Boolean isOn
) {}
```

La parte más específica del dominio está en `DeviceService.claimOrRegisterDevice`. Este método permite dos escenarios:

1. Si la MAC no existe, se crea un nuevo `Device` asociado al usuario.
2. Si la MAC ya existe pero no tiene dueño real, se vincula al usuario actual.

También impide que un usuario reclame un dispositivo ya asignado a otra cuenta empresarial.

## 5. Controlador de lecturas

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/ReadingController.java`

Base URL: `/api/v1/readings`

| Método | Ruta | Parámetros | Salida | Uso |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/readings` | `Principal` | `List<ReadingResponse>` | Recupera lecturas asociadas a dispositivos del usuario. |
| `GET` | `/api/v1/readings/latest/{macAddress}` | `macAddress`, `Principal` | `ReadingResponse` | Devuelve la última lectura conocida de una MAC concreta. |
| `GET` | `/api/v1/readings/search` | `time`, `macAddress`, `Principal` | `ReadingResponse` | Busca una lectura por clave compuesta temporal. |
| `DELETE` | `/api/v1/readings/search` | `time`, `macAddress`, `Principal` | `204 No Content` | Elimina una lectura concreta por tiempo y MAC. |

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

La clave de negocio es la pareja `time + device`. El controlador no permite consultar ni borrar lecturas de un dispositivo que no pertenezca al usuario autenticado. Esa comprobación se hace localizando primero el dispositivo por MAC y comparando `device.username()` con `principal.getName()`.

## 6. Controlador de tarifas

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/TariffController.java`

Base URL: `/api/v1/tariffs`

| Método | Ruta | Entrada | Salida | Restricción |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/tariffs` | - | `List<TariffDto>` | Usuario autenticado. |
| `GET` | `/api/v1/tariffs/{id}` | `id` | `TariffDto` | Usuario autenticado. |
| `POST` | `/api/v1/tariffs` | `TariffDto` | `TariffDto` con `201 Created` | `ROLE_ADMIN`. |
| `POST` | `/api/v1/tariffs/{id}` | `id`, `TariffDto` | `TariffDto` | `ROLE_ADMIN`. |
| `DELETE` | `/api/v1/tariffs/{id}` | `id` | `204 No Content` | `ROLE_ADMIN`. |

DTOs:

```java
public record TariffDto(
        Long id,
        String name,
        String type,
        String market,
        BigDecimal contractedPowerKw,
        String energyCompany,
        List<PeriodDto> periods
) {}

public record PeriodDto(
        Long id,
        String name,
        BigDecimal priceKwh,
        LocalTime startHour,
        LocalTime endHour,
        DayType dayType,
        Integer startMonth,
        Integer endMonth
) {}
```

`TariffService.save` fuerza la relación bidireccional entre tarifa y periodos antes de persistir. Esta decisión evita que los periodos queden descolgados cuando JPA aplica la cascada `CascadeType.ALL` y `orphanRemoval = true` desde la entidad `Tariff`.

## 7. Controlador de analítica de consumo

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/ConsumptionController.java`

Base URL: `/api/v1/analytics`

| Método | Ruta | Query params | Salida |
| --- | --- | --- | --- |
| `GET` | `/api/v1/analytics/cost` | `macAddress`, `start`, `end` | `macAddress`, `totalCostEur`, `start`, `end` |
| `GET` | `/api/v1/analytics/ghost-consumption` | `macAddress`, `start`, `end` | `macAddress`, `ghostCostEur`, `start`, `end` |

Los parámetros `start` y `end` se reciben como `Instant` en formato ISO-8601. Antes de calcular, el controlador comprueba que la MAC pertenezca al usuario autenticado.

En estos dos endpoints no existe un DTO Java específico de respuesta. El controlador construye un `Map<String, Object>` con las claves que consume Angular:

```java
response.put("macAddress", macAddress);
response.put("totalCostEur", totalEur);
response.put("start", start);
response.put("end", end);
```

Para consumo fantasma se mantiene la misma estructura, cambiando `totalCostEur` por `ghostCostEur`.

El cálculo no se basa en sumar potencias instantáneas, sino en la diferencia entre lecturas acumuladas de energía (`energyTotalKwh`). Este enfoque es más estable para telemetría IoT porque el contador acumulado del enchufe permite estimar el consumo real entre dos puntos:

```java
BigDecimal deltaKwh = current.getEnergyTotalKwh().subtract(previous.getEnergyTotalKwh());
BigDecimal stepCost = deltaKwh.multiply(priceKwh);
```

Para elegir el precio, `ConsumptionService` convierte la fecha a `Europe/Madrid` y consulta el periodo tarifario activo con `PeriodRepository.findApplicablePeriod`.

## 8. Controlador de alertas

Archivo: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/AlertController.java`

Base URL: `/api/v1/alerts`

| Método | Ruta | Parámetros | Salida | Uso |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/alerts` | `Principal` | `List<AlertDto>` | Lista alertas del usuario. |
| `DELETE` | `/api/v1/alerts/{id}` | `id`, `Principal` | `204 No Content` | Descarta una alerta si pertenece al usuario. |

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

La generación de alertas se realiza en `AlertService.checkPowerThreshold`. Cada lectura entrante se compara con la potencia contratada de la tarifa del usuario. Si `powerW / 1000` supera `contractedPowerKw`, se crea una alerta `OVERPOWER` y se publica por WebSocket en `/topic/alerts/{username}`.

## 9. Gestión de errores

El backend define un DTO común de error:

```java
public record ErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {}
```

El controlador global `GlobalExceptionHandler` centraliza excepciones como entidades no encontradas, errores de estado y credenciales inválidas. Esto mantiene los controladores más limpios, porque no tienen que construir manualmente todas las respuestas de error.

## 10. Resumen del flujo REST principal

1. Angular autentica al usuario con `/api/v1/auth/login`.
2. El backend devuelve un JWT con usuario y autoridades.
3. El interceptor del frontend adjunta el token en cada llamada a `/api/v1`.
4. Los controladores reciben el `Principal` generado a partir del JWT.
5. Los servicios validan propiedad del recurso y aplican reglas de dominio.
6. Los repositorios JPA consultan o modifican las tablas.
7. El resultado vuelve al frontend siempre como DTO, no como entidad JPA directa.

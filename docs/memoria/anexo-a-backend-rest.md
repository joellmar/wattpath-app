# Anexo A. Controladores REST de Spring Boot

## 1. Vision general

El backend de Wattimizer esta desarrollado con **Spring Boot 4.0.5** y Java 26. La API principal se agrupa bajo el prefijo `/api/v1` y se protege con JWT salvo las rutas publicas de autenticacion y OAuth2.

El paquete base es:

```text
com.joselumartos.jwtauthbackenddemo
```

Los controladores activos estan en:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers
```

La aplicacion no usa sesiones HTTP tradicionales. El navegador guarda el JWT y lo envia en la cabecera:

```http
Authorization: Bearer <token>
```

El `Principal` que reciben los controladores se usa como fuente de verdad para saber que usuario esta operando. Esto evita que un usuario pueda consultar o modificar recursos de otro pasando un `userId` manipulado en la URL.

---

## 2. Seguridad y manejo de errores

### 2.1. Seguridad HTTP

La configuracion esta en:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/config/SecurityConfig.java
```

Decisiones principales:

- CSRF desactivado porque la API trabaja como backend stateless.
- Sesiones configuradas como `STATELESS`.
- CORS configurable mediante `app.cors.allowed-origins`.
- Filtro `JwtValidatorFilter` antes de la autenticacion basica.
- Rutas publicas:
  - `/api/v1/auth/login`
  - `/api/v1/auth/register`
  - `/api/v1/auth/register/admin`
  - `/api/v1/auth/oauth/exchange`
  - `/oauth2/authorization/**`
  - `/login/oauth2/code/**`
  - `/ws-iot/**`
- Mutaciones de tarifas protegidas con `ROLE_ADMIN`.

### 2.2. Respuesta de error comun

El tratamiento global esta en:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/GlobalExceptionHandler.java
```

DTO:

```java
public record ErrorResponse(
    int status,
    String error,
    String message,
    LocalDateTime timestamp
) {}
```

| Excepcion | HTTP | Uso en la aplicacion |
| --- | --- | --- |
| `EntityNotFoundException` | 404 | Recurso no encontrado |
| `BadCredentialsException` | 401 | Login incorrecto |
| `UsernameNotFoundException` | 401 | Usuario no encontrado en autenticacion |
| `ForbiddenException` | 403 | Acceso denegado con cuerpo JSON |
| `IllegalStateException` | 400 | Regla de negocio incumplida |
| `DataIntegrityViolationException` | 400/409/500 | Duplicados, FK y otros errores de integridad |
| `Exception` | 500 | Error no controlado |

Algunos controladores devuelven `403` directamente con `ResponseEntity.status(HttpStatus.FORBIDDEN).build()`. En esos casos la respuesta no lleva `ErrorResponse`, porque el controlador corta antes de lanzar una excepcion.

---

## 3. `AuthController`

Archivo:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/AuthController.java
```

Ruta base:

```text
/api/v1/auth
```

### 3.1. Endpoints

| Metodo | Ruta | Entrada | Salida | Descripcion |
| --- | --- | --- | --- | --- |
| `POST` | `/login` | `LoginUser` | `LoginUserJwt` | Autentica email y contrasena, y devuelve JWT |
| `POST` | `/register` | `RegisterRequest` | Sin cuerpo, `201` | Registra usuario normal |
| `POST` | `/register/admin` | `RegisterRequest` + header `X-Wattimizer-Admin-Secret` | Sin cuerpo, `201` | Registra administrador si el secreto coincide |
| `POST` | `/oauth/exchange` | `OAuthTicketExchangeRequest` | `LoginUserJwt` | Canjea ticket OAuth2 temporal por JWT |

### 3.2. DTOs

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

### 3.3. Flujo de autenticacion

1. El frontend envia usuario y contrasena a `/login`.
2. `UserProviderDetailsManager` valida credenciales.
3. `JwtTokenService` genera un token con `username` y `authorities`.
4. El frontend guarda el token y lo envia en llamadas posteriores.

En OAuth2 el backend no devuelve el JWT directamente en la URL. Primero crea un ticket temporal con `OAuth2LoginTicketService`, redirige al frontend con `?ticket=...` y el componente Angular lo canjea por JWT. Es una decision correcta porque evita exponer el token principal en historiales de navegador o logs intermedios.

---

## 4. `DeviceController`

Archivo:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/DeviceController.java
```

Ruta base:

```text
/api/v1/devices
```

### 4.1. Endpoints

| Metodo | Ruta | Parametros | Body | Salida | Observaciones |
| --- | --- | --- | --- | --- | --- |
| `GET` | `/api/v1/devices` | `Principal` | - | `List<DeviceDto>` | Lista dispositivos del usuario autenticado |
| `GET` | `/api/v1/devices/{id}` | `id`, `Principal` | - | `DeviceDto` | Devuelve `403` si el dispositivo no pertenece al usuario |
| `POST` | `/api/v1/devices` | - | `DeviceDto` | `DeviceDto`, `201` | Endpoint directo/legacy; no usa `Principal` para asignar propietario |
| `POST` | `/api/v1/devices/claim` | `Principal` | `DeviceDto` con `macAddress` y `name` | `DeviceDto` | Reclama dispositivo fisico existente o registra uno nuevo |
| `POST` | `/api/v1/devices/simulated` | `Principal` | `CreateSimulatedDeviceRequest` | `DeviceDto`, `201` | Crea simulador con MAC sintetica |
| `POST` | `/api/v1/devices/simulated/demo-pack` | `Principal` | - | `List<DeviceDto>`, `201` | Crea un simulador por perfil que el usuario aun no tenga |
| `PUT` | `/api/v1/devices/{id}` | `id`, `Principal` | `DeviceDto` | `DeviceDto` | Actualiza nombre, estado y perfil si es simulado |
| `DELETE` | `/api/v1/devices/{id}` | `id`, `Principal` | - | Sin cuerpo, `204` | Borra lecturas y alertas asociadas antes del dispositivo |

### 4.2. DTOs

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

`SimulationProfile` incluye perfiles como `SINE_WAVE`, `OVEN`, `WASHING_MACHINE`, `TELEVISION`, `FAN`, `DESKTOP_PC`, `FRIDGE`, `STANDBY` y `CONSTANT_HIGH_LOAD`.

### 4.3. Intencion de diseno

El controlador separa dos casos:

- **Dispositivo fisico:** llega por MQTT o se reclama manualmente mediante MAC.
- **Dispositivo simulado:** se crea desde la UI para generar lecturas de demo.

El metodo mas importante es `claim`, porque soluciona un caso real del sistema: un dispositivo puede aparecer primero en la base de datos por telemetria MQTT sin estar asociado a ningun usuario. Despues, el usuario lo reclama desde el frontend y el backend comprueba que no pertenezca a otra cuenta.

---

## 5. `ReadingController`

Archivo:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/ReadingController.java
```

Ruta base:

```text
/api/v1/readings
```

### 5.1. Endpoints

| Metodo | Ruta | Parametros | Salida | Descripcion |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/readings` | `Principal` | `List<ReadingResponse>` | Devuelve lecturas de dispositivos del usuario |
| `GET` | `/api/v1/readings/latest/{macAddress}` | `macAddress`, `Principal` | `ReadingResponse` | Ultima lectura de un dispositivo |
| `GET` | `/api/v1/readings/device/{macAddress}/recent?seconds=120` | `macAddress`, `seconds`, `Principal` | `List<ReadingResponse>` | Ventana reciente usada por el dashboard |
| `GET` | `/api/v1/readings/search?time=...&macAddress=...` | `time`, `macAddress`, `Principal` | `ReadingResponse` | Busca por clave compuesta |
| `DELETE` | `/api/v1/readings/search?time=...&macAddress=...` | `time`, `macAddress`, `Principal` | Sin cuerpo, `204` | Elimina por clave compuesta |

### 5.2. DTO de salida

```java
public record ReadingResponse(
    Instant time,
    String macAddress,
    BigDecimal powerW,
    BigDecimal energyTotalKwh,
    Boolean isOn
) {}
```

### 5.3. Detalles importantes

La clave real de una lectura es `(time, device_id)`, aunque en la API se trabaja con `time` y `macAddress` porque la MAC es mas natural para el frontend. Antes de devolver o borrar una lectura, el controlador comprueba que la MAC pertenece al usuario autenticado.

El endpoint `recent` se incorporo para el panel multi-dispositivo. Sin el, al cambiar de medidor el usuario solo veria nuevas lecturas desde WebSocket y no tendria contexto inmediato.

---

## 6. `ConsumptionController`

Archivo:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/ConsumptionController.java
```

Ruta base:

```text
/api/v1/analytics
```

### 6.1. Endpoints

| Metodo | Ruta | Query params | Salida |
| --- | --- | --- | --- |
| `GET` | `/api/v1/analytics/cost` | `macAddress`, `start`, `end` | `macAddress`, `totalCostEur`, `start`, `end` |
| `GET` | `/api/v1/analytics/ghost-consumption` | `macAddress`, `start`, `end` | `macAddress`, `ghostCostEur`, `start`, `end` |

Ejemplo de llamada:

```http
GET /api/v1/analytics/cost?macAddress=9070694d3590&start=2026-08-02T00:00:00Z&end=2026-08-02T22:00:00Z
Authorization: Bearer <jwt>
```

### 6.2. Logica aplicada

Antes de calcular nada, el controlador valida que la MAC sea del usuario autenticado. Despues delega en `ConsumptionService`.

`calculateCostInPeriod`:

1. Recupera lecturas ordenadas por tiempo.
2. Calcula diferencias positivas de `energyTotalKwh`.
3. Resuelve el periodo tarifario aplicable mediante `CalendarResolverService`.
4. Multiplica kWh consumidos por precio del periodo.

`calculateGhostCost` aplica la misma idea, pero solo suma lecturas dentro de la ventana 00:00-05:59 en la zona horaria de la tarifa del usuario. No es lo mismo que decir "periodo valle", porque la logica se basa en hora local de madrugada.

---

## 7. `AlertController`

Archivo:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/AlertController.java
```

Ruta base:

```text
/api/v1/alerts
```

| Metodo | Ruta | Parametros | Salida | Descripcion |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/alerts` | `Principal` | `List<AlertDto>` | Lista alertas del usuario |
| `DELETE` | `/api/v1/alerts/{id}` | `id`, `Principal` | Sin cuerpo, `204` | Borra la alerta si pertenece al usuario |

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

Las alertas se generan en `AlertService.checkPowerThreshold(Reading)`. Cuando una lectura supera la potencia contratada del periodo correspondiente, se persiste una alerta `OVERPOWER` y se publica por WebSocket en el topic del usuario.

---

## 8. `TariffController`

Archivo:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/TariffController.java
```

Ruta base:

```text
/api/v1/tariffs
```

| Metodo | Ruta | Rol | Body | Salida |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/tariffs` | Usuario autenticado | - | `List<TariffDto>` |
| `GET` | `/api/v1/tariffs/{id}` | Usuario autenticado | - | `TariffDto` |
| `POST` | `/api/v1/tariffs` | `ROLE_ADMIN` | `TariffDto` | `TariffDto`, `201` |
| `POST` | `/api/v1/tariffs/{id}` | `ROLE_ADMIN` | `TariffDto` | `TariffDto` |
| `DELETE` | `/api/v1/tariffs/{id}` | `ROLE_ADMIN` | - | Sin cuerpo, `204` |

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

DTOs hijos:

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

El controlador usa `POST /{id}` para actualizar, en lugar de `PUT`. No es la convencion REST mas clasica, pero documentar esto es importante porque el frontend consume exactamente esa ruta.

---

## 9. `UserTariffController`

Archivo:

```text
backend/src/main/java/com/joselumartos/jwtauthbackenddemo/controllers/UserTariffController.java
```

Ruta base:

```text
/api/v1/users/me/tariff
```

| Metodo | Ruta | Body | Salida | Descripcion |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/users/me/tariff` | - | `TariffDto` o `204` | Devuelve la tarifa privada del usuario |
| `POST` | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` | Crea o actualiza la tarifa privada |
| `DELETE` | `/api/v1/users/me/tariff` | - | Sin cuerpo, `204` | Desvincula la tarifa |

DTO:

```java
public record UserTariffRequest(
    Long templateTariffId,
    TariffDto contract
) {}
```

Este controlador tiene una decision de seguridad clara: no se acepta ningun identificador de usuario. El usuario se obtiene siempre con `Principal.getName()`. Asi se evita un fallo IDOR, donde alguien podria intentar modificar `/users/otro/tariff`.

---

## 10. Relacion entre controladores y servicios

| Controlador | Servicio principal | Responsabilidad delegada |
| --- | --- | --- |
| `AuthController` | `AuthRegistrationService`, `JwtTokenService`, `OAuth2LoginTicketService` | Registro, JWT y OAuth |
| `DeviceController` | `DeviceService` | Propiedad, simuladores, MAC y borrado en cascada |
| `ReadingController` | `ReadingService`, `DeviceService` | Consulta y autorizacion por MAC |
| `ConsumptionController` | `ConsumptionService`, `DeviceService` | Calculo economico |
| `AlertController` | `AlertService` | Listado y borrado de alertas |
| `TariffController` | `TariffService` | Catalogo maestro |
| `UserTariffController` | `UserTariffService` | Contrato privado del usuario |

---

## 11. Controladores no activos

Hay tres clases relacionadas con estado/comandos de dispositivo que estan comentadas:

- `DeviceCommandController.java`
- `DeviceStateController.java`
- `ReactiveDeviceStateController.java`

No deben documentarse como API disponible. Son restos o pruebas de una linea de trabajo anterior, pero no exponen endpoints en la aplicacion actual.

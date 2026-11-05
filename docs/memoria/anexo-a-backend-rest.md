# Anexo A: Controladores REST de Spring Boot

## 1. Vision general

El backend expone una API REST bajo el prefijo `/api/v1`. La mayoria de endpoints requieren JWT, salvo autenticacion, registro, OAuth2 y el endpoint WebSocket. La autenticacion se valida en `JwtValidatorFilter`, que lee la cabecera `Authorization: Bearer ...` y crea el `Authentication` de Spring Security con las autoridades incluidas en el token.

La regla general de propiedad es sencilla: cuando un recurso pertenece a un usuario, el backend no acepta `userId` desde el cliente, sino que usa `Principal.getName()`. Esto evita que un usuario pueda consultar o modificar datos de otro cambiando un parametro.

## 2. Seguridad aplicada a la API

Archivo principal: `backend/src/main/java/com/joselumartos/jwtauthbackenddemo/config/SecurityConfig.java`.

| Ruta | Acceso |
|---|---|
| `/api/v1/auth/login` | Publico |
| `/api/v1/auth/register` | Publico |
| `/api/v1/auth/register/admin` | Publico, pero exige cabecera interna `X-Wattimizer-Admin-Secret` |
| `/api/v1/auth/oauth/exchange` | Publico, porque el ticket temporal actua como credencial |
| `/oauth2/authorization/**` | Publico |
| `/login/oauth2/code/**` | Publico |
| `/ws-iot/**` | Publico para handshake WebSocket |
| `GET /api/v1/tariffs/**` | Usuario autenticado |
| Mutaciones `/api/v1/tariffs/**` | `ROLE_ADMIN` |
| Resto de `/api/v1/**` | Usuario autenticado |

El frontend anade automaticamente:

```http
X-Requested-With: XMLHttpRequest
Authorization: Bearer <jwt>
```

La segunda cabecera solo se adjunta para rutas `/api/v1` que no sean de autenticacion publica.

## 3. Manejo de errores

Controlador global: `GlobalExceptionHandler`.

DTO de error:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Device not found",
  "timestamp": "2026-11-05T22:00:00"
}
```

| Excepcion | HTTP | Intencion |
|---|---:|---|
| `EntityNotFoundException` | 404 | Recurso inexistente o no accesible. |
| `BadCredentialsException` | 401 | Login incorrecto. |
| `UsernameNotFoundException` | 401 | Usuario del token no localizado. |
| `ForbiddenException` | 403 | Acceso denegado por regla explicita. |
| `IllegalStateException` | 400 | Violacion de regla de negocio. |
| `DataIntegrityViolationException` por `users_username_key` | 400 | Email duplicado. |
| `DataIntegrityViolationException` por FK de `readings` o `alerts` | 409 | Conflicto al borrar dispositivo con dependencias. |
| Resto de excepciones | 500 | Error interno controlado. |

## 4. `AuthController`

Base path: `/api/v1/auth`.

### 4.1. `POST /login`

Autentica un usuario con email y contrasena.

**Entrada: `LoginUser`**

```json
{
  "username": "usuario@empresa.com",
  "password": "contrasena"
}
```

**Salida: `LoginUserJwt`**

```json
{
  "statusCode": "200 OK",
  "jwt": "<token>"
}
```

**Flujo interno**

1. Construye un `UsernamePasswordAuthenticationToken` sin autenticar.
2. Delega en `UserProviderDetailsManager`.
3. Si Spring Security confirma la autenticacion, `JwtTokenService` emite un JWT con usuario y roles.
4. Si falla, se lanza `BadCredentialsException`.

### 4.2. `POST /register`

Registra un usuario normal.

**Entrada: `RegisterRequest`**

```json
{
  "username": "usuario@empresa.com",
  "password": "secreto123",
  "confirmPassword": "secreto123",
  "tariffId": 1
}
```

`tariffId` es opcional. Si existe, se asocia una tarifa inicial al usuario. El servicio valida email, contrasena minima de 6 caracteres y confirmacion.

**Salida**

- `201 Created` sin cuerpo.

### 4.3. `POST /register/admin`

Registra un administrador. Usa el mismo `RegisterRequest`, pero exige la cabecera:

```http
X-Wattimizer-Admin-Secret: <valor_configurado_en_entorno>
```

No se documenta el valor real de la clave porque pertenece a configuracion sensible.

**Salida**

- `201 Created` si la cabecera coincide.
- `403 Forbidden` si el secreto no coincide.

### 4.4. `POST /oauth/exchange`

Intercambia el ticket temporal generado tras OAuth2 por un JWT de la aplicacion.

**Entrada: `OAuthTicketExchangeRequest`**

```json
{
  "ticket": "ticket-de-un-solo-uso"
}
```

**Salida: `LoginUserJwt`**

```json
{
  "statusCode": "200 OK",
  "jwt": "<token>"
}
```

La decision importante es no enviar el JWT en la URL de callback. El backend redirige con un ticket de corta duracion y Angular lo cambia por el token definitivo.

## 5. `DeviceController`

Base path: `/api/v1/devices`.

DTO principal:

```json
{
  "id": 1,
  "username": "usuario@empresa.com",
  "name": "Nevera oficina",
  "macAddress": "A1B2C3D4E5F6",
  "isOn": true,
  "simulated": false,
  "simulationProfile": null
}
```

### Endpoints

| Metodo | Ruta | Entrada | Salida | Seguridad y logica |
|---|---|---|---|---|
| GET | `/api/v1/devices` | - | `List<DeviceDto>` | Devuelve dispositivos cuyo `user.username` coincide con el `Principal`. |
| GET | `/api/v1/devices/{id}` | `id` path | `DeviceDto` | Busca por id y comprueba propietario antes de responder. |
| POST | `/api/v1/devices` | `DeviceDto` parcial | `DeviceDto` creado | Guarda el DTO directamente. Es una excepcion tecnica: no toma propietario desde `Principal`. Para uso normal se prefiere `/claim`. |
| POST | `/api/v1/devices/claim` | `DeviceDto` con `macAddress` y `name` | `DeviceDto` | Vincula un dispositivo existente sin usuario real o crea uno nuevo para el usuario autenticado. |
| POST | `/api/v1/devices/simulated` | `CreateSimulatedDeviceRequest` | `DeviceDto` | Crea un dispositivo simulado con MAC generada `SIM000000001`, etc. |
| POST | `/api/v1/devices/simulated/demo-pack` | cuerpo vacio | `List<DeviceDto>` | Crea los simuladores de perfiles que el usuario aun no tenga. |
| PUT | `/api/v1/devices/{id}` | `DeviceDto` | `DeviceDto` | Permite cambiar nombre, `isOn` y perfil si el dispositivo es simulado. |
| DELETE | `/api/v1/devices/{id}` | `id` path | 204 | Comprueba propietario y borra lecturas y alertas antes del dispositivo. |

### `CreateSimulatedDeviceRequest`

```json
{
  "name": "Simulador horno",
  "simulationProfile": "OVEN"
}
```

Perfiles aceptados:

```text
SINE_WAVE, OVEN, WASHING_MACHINE, TELEVISION, FAN,
DESKTOP_PC, FRIDGE, STANDBY, CONSTANT_HIGH_LOAD
```

### Decisiones de diseno

- Los dispositivos simulados se distinguen con `simulated=true` y un `simulationProfile`.
- La MAC simulada se genera con prefijo `SIM` y secuencia numerica para evitar colisiones.
- El pack demo evita duplicados revisando si el usuario ya tiene un dispositivo simulado por perfil.
- En el borrado se ejecuta:
  1. `readingRepository.deleteAllByDeviceMacAddress(macAddress)`.
  2. `alertRepository.deleteByDeviceId(id)`.
  3. `deviceRepository.delete(device)`.

## 6. `ReadingController`

Base path: `/api/v1/readings`.

DTO de salida:

```json
{
  "time": "2026-11-05T22:00:00Z",
  "macAddress": "A1B2C3D4E5F6",
  "powerW": 125.50,
  "energyTotalKwh": 14.2350,
  "isOn": true
}
```

| Metodo | Ruta | Parametros | Salida | Seguridad y logica |
|---|---|---|---|---|
| GET | `/api/v1/readings` | - | `List<ReadingResponse>` | Filtra en servicio las lecturas de dispositivos del usuario. |
| GET | `/api/v1/readings/latest/{macAddress}` | `macAddress` path | `ReadingResponse` | Comprueba que la MAC pertenezca al usuario y devuelve la ultima lectura. |
| GET | `/api/v1/readings/device/{macAddress}/recent` | `seconds` query, por defecto `120` | `List<ReadingResponse>` | Devuelve lecturas entre `now - seconds` y `now`, ordenadas ascendente por tiempo. |
| GET | `/api/v1/readings/search` | `time` ISO-8601, `macAddress` | `ReadingResponse` | Busca por la clave compuesta logica: instante + MAC. |
| DELETE | `/api/v1/readings/search` | `time` ISO-8601, `macAddress` | 204 | Borra una lectura concreta tras comprobar propietario. |

Ejemplo de busqueda:

```http
GET /api/v1/readings/search?macAddress=A1B2C3D4E5F6&time=2026-11-05T22:00:00Z
```

## 7. `ConsumptionController`

Base path: `/api/v1/analytics`.

### 7.1. `GET /cost`

Calcula el coste financiero de un dispositivo en un intervalo.

**Parametros**

| Parametro | Tipo | Descripcion |
|---|---|---|
| `macAddress` | string | MAC del dispositivo. |
| `start` | `Instant` ISO-8601 | Inicio del intervalo. |
| `end` | `Instant` ISO-8601 | Fin del intervalo. |

**Salida**

```json
{
  "macAddress": "A1B2C3D4E5F6",
  "totalCostEur": 1.42,
  "start": "2026-11-05T00:00:00Z",
  "end": "2026-11-05T22:00:00Z"
}
```

El calculo usa diferencias positivas de `energyTotalKwh` entre lecturas consecutivas. Si hay menos de dos lecturas, no hay delta y devuelve `0`.

### 7.2. `GET /ghost-consumption`

Calcula el coste dentro de la ventana 00:00-05:59 en la zona horaria de la tarifa.

**Salida**

```json
{
  "macAddress": "A1B2C3D4E5F6",
  "ghostCostEur": 0.18,
  "start": "2026-11-05T00:00:00Z",
  "end": "2026-11-05T22:00:00Z"
}
```

No reutiliza directamente el periodo valle regulatorio. El codigo define "consumo fantasma" como consumo nocturno entre medianoche y las 05:59 hora local del contrato.

## 8. `TariffController`

Base path: `/api/v1/tariffs`.

DTO principal:

```json
{
  "id": 1,
  "name": "Tarifa empresa 3.0TD",
  "market": "libre",
  "accessTariffCode": "3.0TD",
  "geographicZone": "PENINSULA",
  "energyCompany": "Compania",
  "periods": [
    { "id": 10, "periodCode": "P1", "priceKwh": 0.200000 }
  ],
  "contractedPowers": [
    { "id": 20, "periodCode": "P1", "contractedPowerKw": 5.75 }
  ]
}
```

| Metodo | Ruta | Entrada | Salida | Seguridad |
|---|---|---|---|---|
| GET | `/api/v1/tariffs` | - | `List<TariffDto>` | Autenticado. Devuelve solo catalogo maestro. |
| GET | `/api/v1/tariffs/{id}` | `id` | `TariffDto` | Autenticado. |
| POST | `/api/v1/tariffs` | `TariffDto` | `TariffDto` creado | `ROLE_ADMIN`. |
| POST | `/api/v1/tariffs/{id}` | `TariffDto` | `TariffDto` actualizado | `ROLE_ADMIN`. El backend usa POST para actualizar, no PUT. |
| DELETE | `/api/v1/tariffs/{id}` | `id` | 204 | `ROLE_ADMIN`. No permite borrar si la tarifa esta asignada a un usuario. |

Validaciones de `TariffService`:

- `accessTariffCode` y `geographicZone` son obligatorios.
- No puede haber periodos duplicados.
- Los precios `priceKwh` deben ser mayores que cero.
- Las potencias contratadas deben ser mayores que cero.
- `2.0TD` requiere energia `P1`, `P2`, `P3` y potencia `P1`, `P2`.
- `3.0TD`, `6.1TD` y `6.2TD` requieren `P1` a `P6`.
- En peajes de seis periodos se comprueba el orden legal de potencias: `P1 <= P2 <= ... <= P6`.

## 9. `UserTariffController`

Base path: `/api/v1/users/me/tariff`.

Este controlador gestiona la tarifa privada del usuario autenticado. La decision de usar `/me` es importante: el propietario se deduce del JWT y no del cuerpo de la peticion.

| Metodo | Ruta | Entrada | Salida | Logica |
|---|---|---|---|---|
| GET | `/api/v1/users/me/tariff` | - | `TariffDto` o 204 | Devuelve la tarifa activa del usuario. |
| POST | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` | Clona una plantilla, aplica overrides o guarda un contrato directo. |
| DELETE | `/api/v1/users/me/tariff` | - | 204 | Desvincula y elimina la tarifa privada. |

`UserTariffRequest`:

```json
{
  "templateTariffId": 1,
  "contract": null
}
```

Modos admitidos:

1. Solo `templateTariffId`: clona la plantilla del catalogo.
2. `templateTariffId` + `contract`: clona y aplica cambios.
3. Solo `contract`: crea o actualiza contrato privado.

## 10. `AlertController`

Base path: `/api/v1/alerts`.

DTO:

```json
{
  "id": 15,
  "macAddress": "A1B2C3D4E5F6",
  "username": "usuario@empresa.com",
  "type": "OVERPOWER",
  "message": "Alerta de maximetro...",
  "createdAt": "2026-11-05T22:00:00"
}
```

| Metodo | Ruta | Salida | Logica |
|---|---|---|---|
| GET | `/api/v1/alerts` | `List<AlertDto>` | Lista alertas del usuario autenticado. |
| DELETE | `/api/v1/alerts/{id}` | 204 | Borra solo si el ID pertenece al usuario. Si no borra filas, lanza 404. |

Las alertas se generan en `AlertService.checkPowerThreshold(Reading reading)`, despues de guardar una lectura MQTT o simulada.

## 11. Controladores desactivados

En el paquete `controllers` existen clases comentadas como `DeviceStateController`, `ReactiveDeviceStateController` y `DeviceCommandController`. No forman parte de la API activa porque sus anotaciones `@RestController` o `@Controller` estan comentadas.

## 12. Resumen de contratos DTO

| DTO | Uso |
|---|---|
| `LoginUser` | Entrada de login. |
| `LoginUserJwt` | Salida de login y de intercambio OAuth2. |
| `RegisterRequest` | Registro normal y administrativo. |
| `OAuthTicketExchangeRequest` | Ticket temporal OAuth2. |
| `DeviceDto` | Entrada/salida de dispositivos. |
| `CreateSimulatedDeviceRequest` | Alta de dispositivo simulado. |
| `ReadingResponse` | Salida de lecturas historicas y tiempo real. |
| `EventsRpc`, `Params`, `Switch`, `ActiveEnergy` | Payload MQTT `events/rpc`. |
| `Status` | Payload MQTT `status/switch:0`. |
| `TariffDto`, `PeriodDto`, `TariffContractedPowerDto` | Catalogo y contratos de tarifa. |
| `UserTariffRequest` | Asignacion o actualizacion de tarifa privada. |
| `AlertDto` | Alertas de maximetro. |
| `ErrorResponse` | Respuestas de error normalizadas. |

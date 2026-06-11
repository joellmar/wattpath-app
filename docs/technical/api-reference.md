# Anexo A - Referencia tecnica de la API REST

## 1. Alcance

Este anexo documenta los controladores REST reales del backend Spring Boot. Todas las rutas parten directamente de las anotaciones `@RequestMapping`, porque el proyecto no define un `context-path` global.

La API usa JSON y esta protegida por JWT salvo las rutas publicas de autenticacion. El frontend consume estas rutas desde Angular mediante servicios HTTP, stores con `rxMethod` y componentes standalone.

## 2. Seguridad y formato de errores

### 2.1. Seguridad global

La configuracion de seguridad esta en `SecurityConfig.java`:

- La sesion es **stateless** (`SessionCreationPolicy.STATELESS`).
- CSRF esta deshabilitado porque la API se usa con JWT.
- CORS se configura con `app.cors.allowed-origins`.
- El filtro JWT lee `Authorization: Bearer <token>`.
- Rutas publicas:
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/register/admin`
  - `POST /api/v1/auth/oauth/exchange`
  - `/oauth2/authorization/**`
  - `/login/oauth2/code/**`
  - `/ws-iot/**`
- `GET /api/v1/tariffs/**` requiere usuario autenticado.
- El resto de operaciones sobre `/api/v1/tariffs/**` requiere `ROLE_ADMIN`.

El JWT se genera con `JwtTokenService` e incluye:

- `username`
- `authorities`
- `issuer = store-security`
- expiracion de 8 horas

### 2.2. ErrorResponse

El manejador global `GlobalExceptionHandler` devuelve, en la mayoria de errores, el DTO:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Device not found",
  "timestamp": "2026-06-11T22:00:00"
}
```

Hay excepciones: algunos controladores devuelven `403 Forbidden` con cuerpo vacio cuando detectan que el recurso no pertenece al usuario autenticado.

## 3. DTOs principales

| DTO | Campos | Uso |
| --- | --- | --- |
| `LoginUser` | `username`, `password` | Entrada de login. |
| `LoginUserJwt` | `statusCode`, `jwt` | Salida de login y OAuth2 exchange. |
| `RegisterRequest` | `username`, `password`, `confirmPassword`, `tariffId` | Registro de usuario normal o admin. |
| `OAuthTicketExchangeRequest` | `ticket` | Intercambio de ticket OAuth2 por JWT. |
| `DeviceDto` | `id`, `username`, `name`, `macAddress`, `isOn`, `simulated` | Dispositivos IoT. |
| `ReadingResponse` | `time`, `macAddress`, `powerW`, `energyTotalKwh`, `isOn` | Lecturas persistidas y emitidas. |
| `AlertDto` | `id`, `macAddress`, `username`, `type`, `message`, `createdAt` | Alertas de usuario. |
| `TariffDto` | `id`, `name`, `market`, `accessTariffCode`, `geographicZone`, `energyCompany`, `periods`, `contractedPowers` | Tarifa completa. |
| `PeriodDto` | `id`, `periodCode`, `priceKwh` | Precio por periodo. |
| `TariffContractedPowerDto` | `id`, `periodCode`, `contractedPowerKw` | Potencia contratada por periodo. |
| `UserTariffRequest` | `templateTariffId`, `contract` | Asignacion o edicion de tarifa propia. |

---

## 4. `AuthController`

Ruta base: `/api/v1/auth`.

### `POST /api/v1/auth/login`

Autentica al usuario y devuelve un JWT.

| Elemento | Detalle |
| --- | --- |
| Seguridad | Publico. |
| Body | `LoginUser`. |
| Respuesta correcta | `200 OK` con `LoginUserJwt`. |
| Errores | `401` si las credenciales no son validas. |

Ejemplo de entrada:

```json
{
  "username": "usuario@correo.com",
  "password": "secret123"
}
```

Ejemplo de salida:

```json
{
  "statusCode": "200 OK",
  "jwt": "eyJhbGciOi..."
}
```

### `POST /api/v1/auth/register`

Crea un usuario con rol normal.

| Elemento | Detalle |
| --- | --- |
| Seguridad | Publico. |
| Body | `RegisterRequest`. |
| Respuesta correcta | `201 Created` sin cuerpo. |
| Validaciones | Email obligatorio y valido, password minima de 6 caracteres, confirmacion coincidente. |
| Errores | `400` por validacion o email duplicado. |

### `POST /api/v1/auth/register/admin`

Crea un usuario administrador, pero exige un secreto de administracion.

| Elemento | Detalle |
| --- | --- |
| Seguridad | Publico en Spring Security, protegido por header propio. |
| Header | `X-Wattimizer-Admin-Secret`. |
| Body | `RegisterRequest`. |
| Respuesta correcta | `201 Created` sin cuerpo. |
| Errores | `403` si el secreto no coincide con `app.admin.secret`. |

Esta decision permite registrar el primer administrador sin tener todavia una sesion admin, pero mantiene una barrera externa basada en variable de entorno.

### `POST /api/v1/auth/oauth/exchange`

Intercambia un ticket temporal OAuth2 por un JWT.

| Elemento | Detalle |
| --- | --- |
| Seguridad | Publico. |
| Body | `OAuthTicketExchangeRequest`. |
| Respuesta correcta | `200 OK` con `LoginUserJwt`. |
| Errores | `400` si el ticket no existe, ya se uso o expiro. |

El ticket lo crea `OAuth2AuthenticationSuccessHandler` tras validar el proveedor social. El TTL es de 60 segundos. Asi se evita incluir el JWT directamente en la URL de redireccion al frontend.

---

## 5. `DeviceController`

Ruta base: `/api/v1/devices`.

### Resumen de endpoints

| Metodo | Ruta | Parametros | Body | Salida |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/devices` | Ninguno; usa `Principal` | No | `List<DeviceDto>` |
| `GET` | `/api/v1/devices/{id}` | `id: Long` | No | `DeviceDto` |
| `POST` | `/api/v1/devices` | No | `DeviceDto` | `DeviceDto` |
| `POST` | `/api/v1/devices/claim` | No | `DeviceDto` con `macAddress`, `name` | `DeviceDto` |
| `PUT` | `/api/v1/devices/{id}` | `id: Long` | `DeviceDto` | `DeviceDto` |
| `DELETE` | `/api/v1/devices/{id}` | `id: Long` | No | Sin cuerpo |

### Logica por operacion

- `GET /api/v1/devices` lista solo dispositivos cuyo `user.username` coincide con `Principal.getName()`.
- `GET /api/v1/devices/{id}` comprueba manualmente que el dispositivo pertenece al usuario autenticado.
- `POST /api/v1/devices` crea un dispositivo desde el DTO recibido. Es una zona a vigilar: el controlador no usa `Principal` y la asociacion de usuario depende del `username` del cuerpo.
- `POST /api/v1/devices/claim` es el flujo recomendado para usuario final. Busca por MAC; si el dispositivo no existe lo crea, y si estaba huerfano o asignado a `SYSTEM`, lo vincula al usuario actual.
- `PUT /api/v1/devices/{id}` actualiza nombre y estado logico con comprobacion de propiedad en el servicio.
- `DELETE /api/v1/devices/{id}` borra solo si el dispositivo pertenece al usuario.

---

## 6. `ReadingController`

Ruta base: `/api/v1/readings`.

### Resumen de endpoints

| Metodo | Ruta | Parametros | Salida |
| --- | --- | --- | --- |
| `GET` | `/api/v1/readings` | Ninguno; usa `Principal` | `List<ReadingResponse>` |
| `GET` | `/api/v1/readings/latest/{macAddress}` | `macAddress: String` | `ReadingResponse` |
| `GET` | `/api/v1/readings/search` | `time: Instant`, `macAddress: String` | `ReadingResponse` |
| `DELETE` | `/api/v1/readings/search` | `time: Instant`, `macAddress: String` | Sin cuerpo |

`time` se recibe con formato ISO date-time, por ejemplo:

```http
GET /api/v1/readings/search?macAddress=9070694d3590&time=2026-06-11T22:00:00Z
```

### Reglas de seguridad

El controlador busca primero el dispositivo por MAC y compara su usuario con el `Principal`. Si no coincide, devuelve `403 Forbidden` sin cuerpo. Si la lectura no existe, se delega en `EntityNotFoundException` y se devuelve `404`.

---

## 7. `ConsumptionController`

Ruta base: `/api/v1/analytics`.

### `GET /api/v1/analytics/cost`

Calcula el coste total en euros de un dispositivo dentro de un intervalo.

| Parametro | Tipo | Descripcion |
| --- | --- | --- |
| `macAddress` | `String` | MAC del dispositivo. |
| `start` | `Instant` ISO | Inicio del intervalo. |
| `end` | `Instant` ISO | Fin del intervalo. |

Respuesta:

```json
{
  "macAddress": "9070694d3590",
  "totalCostEur": 1.42,
  "start": "2026-06-11T00:00:00Z",
  "end": "2026-06-11T23:59:59Z"
}
```

La logica esta en `ConsumptionService.calculateCostInPeriod`. El servicio:

1. Obtiene lecturas ordenadas por `time`.
2. Calcula deltas positivos entre odometros `energyTotalKwh`.
3. Resuelve el periodo tarifario del instante actual.
4. Multiplica `deltaKwh * priceKwh`.
5. Redondea a 2 decimales.

### `GET /api/v1/analytics/ghost-consumption`

Calcula coste de consumo fantasma.

| Parametro | Tipo | Descripcion |
| --- | --- | --- |
| `macAddress` | `String` | MAC del dispositivo. |
| `start` | `Instant` ISO | Inicio del intervalo. |
| `end` | `Instant` ISO | Fin del intervalo. |

Respuesta:

```json
{
  "macAddress": "9070694d3590",
  "ghostCostEur": 0.23,
  "start": "2026-06-11T00:00:00Z",
  "end": "2026-06-11T23:59:59Z"
}
```

El criterio real del codigo no es "periodo valle", sino ventana de inactividad `00:00-05:59` en la zona local del contrato. Por eso el servicio delega la zona horaria a `CalendarResolverService`; en Canarias usa `Atlantic/Canary` y en la Peninsula usa `Europe/Madrid`.

---

## 8. `AlertController`

Ruta base: `/api/v1/alerts`.

| Metodo | Ruta | Parametros | Salida |
| --- | --- | --- | --- |
| `GET` | `/api/v1/alerts` | Ninguno; usa `Principal` | `List<AlertDto>` |
| `DELETE` | `/api/v1/alerts/{id}` | `id: Long` | Sin cuerpo |

`GET /api/v1/alerts` devuelve solo las alertas del usuario autenticado. `DELETE /api/v1/alerts/{id}` borra por `id` y `username`, de forma que un usuario no puede borrar alertas de otro. Si no se borra ninguna fila, la API responde `404`, ocultando si el problema era inexistencia o falta de autorizacion.

---

## 9. `TariffController`

Ruta base: `/api/v1/tariffs`.

### Resumen de endpoints

| Metodo | Ruta | Seguridad | Body | Salida |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/tariffs` | Autenticado | No | `List<TariffDto>` |
| `GET` | `/api/v1/tariffs/{id}` | Autenticado | No | `TariffDto` |
| `POST` | `/api/v1/tariffs` | `ROLE_ADMIN` | `TariffDto` | `TariffDto` |
| `POST` | `/api/v1/tariffs/{id}` | `ROLE_ADMIN` | `TariffDto` | `TariffDto` |
| `DELETE` | `/api/v1/tariffs/{id}` | `ROLE_ADMIN` | No | Sin cuerpo |

La actualizacion usa `POST /{id}` en vez de `PUT`. El frontend respeta esta decision en `TariffService.updateCatalogTariff`.

### Validacion tarifaria

`TariffService.validateTariffContract` aplica reglas de negocio:

- `accessTariffCode` obligatorio.
- `geographicZone` obligatoria.
- Al menos un periodo de energia.
- `priceKwh > 0`.
- Al menos una potencia contratada.
- `contractedPowerKw > 0`.
- `2.0TD` exige energia `P1`, `P2`, `P3` y potencias `P1`, `P2`.
- `3.0TD`, `6.1TD` y `6.2TD` exigen `P1-P6` y potencias ascendentes.

### Ejemplo de `TariffDto`

```json
{
  "id": 1,
  "name": "Tarifa Pyme 2.0TD",
  "market": "LIBRE",
  "accessTariffCode": "2.0TD",
  "geographicZone": "PENINSULA",
  "energyCompany": "Comercializadora X",
  "periods": [
    { "id": 10, "periodCode": "P1", "priceKwh": 0.180000 },
    { "id": 11, "periodCode": "P2", "priceKwh": 0.130000 },
    { "id": 12, "periodCode": "P3", "priceKwh": 0.090000 }
  ],
  "contractedPowers": [
    { "id": 20, "periodCode": "P1", "contractedPowerKw": 4.60 },
    { "id": 21, "periodCode": "P2", "contractedPowerKw": 4.60 }
  ]
}
```

---

## 10. `UserTariffController`

Ruta base: `/api/v1/users/me/tariff`.

El controlador no acepta `userId` ni en ruta ni en body. El usuario propietario siempre sale de `Principal`, reduciendo el riesgo de IDOR.

| Metodo | Ruta | Body | Salida |
| --- | --- | --- | --- |
| `GET` | `/api/v1/users/me/tariff` | No | `TariffDto` o `204 No Content` |
| `POST` | `/api/v1/users/me/tariff` | `UserTariffRequest` | `TariffDto` |
| `DELETE` | `/api/v1/users/me/tariff` | No | Sin cuerpo |

### Modos de guardado

`UserTariffRequest` permite tres escenarios:

```json
{
  "templateTariffId": 1,
  "contract": null
}
```

Asigna una plantilla clonada del catalogo.

```json
{
  "templateTariffId": 1,
  "contract": {
    "energyCompany": "Mi comercializadora",
    "periods": [
      { "periodCode": "P1", "priceKwh": 0.170000 }
    ]
  }
}
```

Clona una plantilla y aplica cambios parciales.

```json
{
  "templateTariffId": null,
  "contract": {
    "name": "Contrato propio",
    "accessTariffCode": "2.0TD",
    "geographicZone": "PENINSULA",
    "periods": [],
    "contractedPowers": []
  }
}
```

Crea o actualiza una tarifa privada del usuario.

---

## 11. Controladores no activos

Existen clases con anotaciones comentadas:

- `DeviceStateController`
- `ReactiveDeviceStateController`
- `DeviceCommandController`

No se documentan como contrato publico porque no estan activas como controladores REST en el estado actual del codigo.

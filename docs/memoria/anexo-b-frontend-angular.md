# Anexo B — Frontend Angular, RxJS y NgRx Signals

## 1. Visión general

El frontend de Wattimizer está desarrollado con Angular 21 y componentes standalone. La aplicación usa PrimeNG para la interfaz, Chart.js para la gráfica de potencia, RxJS para flujos asíncronos y NgRx Signals para el estado global.

| Carpeta | Responsabilidad |
|---|---|
| `components` | Pantallas principales. |
| `services` | HTTP, sesión JWT, WebSocket y recursos auxiliares. |
| `store` | Estado reactivo de telemetría y tarifas. |
| `interfaces` | Tipos TypeScript equivalentes a DTOs Java. |
| `guards` | Protección de rutas privadas. |
| `interceptors` | Inserción de JWT y gestión de 401. |

## 2. Routing y seguridad en cliente

`app.routes.ts` define rutas públicas (`/login`, `/register`, `/auth/oauth/callback`) y rutas privadas dentro de `MainLayoutComponent`.

```ts
canActivate: [authGuard],
canActivateChild: [authGuard]
```

`authGuard` consulta `SessionStorageService.isLoggedIn()`. Este servicio decodifica el JWT con `jwt-decode`, comprueba `exp` y evita entrar en rutas privadas si el token ha caducado.

`httpInterceptor` añade `X-Requested-With: XMLHttpRequest` a todas las peticiones y añade `Authorization: Bearer <jwt>` a rutas `/api/v1` que no sean públicas. Si el backend responde 401, borra sesión y redirige a `/login`.

## 3. Gestión de sesión

Archivo: `services/session-storage.service.ts`

El token se guarda en `sessionStorage`, no en `localStorage`. Así desaparece al cerrar la sesión del navegador y no queda persistido indefinidamente.

Métodos principales:

| Método | Uso |
|---|---|
| `saveToken` | Guarda el JWT tras login u OAuth2. |
| `getToken` | Lo lee para el interceptor. |
| `logout` | Borra solo la clave del token. |
| `isLoggedIn` | Verifica existencia y expiración. |
| `getAuthorities` | Extrae roles serializados por el backend. |
| `hasRole` | Permite mostrar acciones de admin. |
| `getUsername` | Muestra el usuario en el layout. |

## 4. Store de telemetría

Archivo: `store/telemetry.store.ts`

### 4.1. Estado

```ts
interface TelemetryState {
  devices: Device[];
  selectedMac: string | null;
  historicalReadings: {
    [mac: string]: {
      timestamps: string[];
      powerW: number[];
    };
  };
  isLoadingDevices: boolean;
}
```

El histórico se guarda por MAC. Esto permite cambiar entre varios dispositivos sin mezclar los puntos de la gráfica.

### 4.2. Selector calculado

`currentReadings` depende de `selectedMac`. Si hay dispositivo seleccionado, devuelve su serie; si no, devuelve arrays vacíos. Es una decisión simple pero importante: el dashboard no necesita comprobar si existe histórico, solo consume la señal.

### 4.3. Métodos HTTP

| Método | Endpoint | Efecto |
|---|---|---|
| `loadDevices` | `GET /api/v1/devices` | Carga dispositivos y selecciona primera MAC si no hay una activa. |
| `claimDevice` | `POST /api/v1/devices/claim` | Añade dispositivo físico reclamado. |
| `createSimulatedDevice` | `POST /api/v1/devices/simulated` | Añade simulador creado. |
| `addDevice` | `POST /api/v1/devices` | Alta directa. |
| `updateDevice` | `PUT /api/v1/devices/{id}` | Sustituye el dispositivo actualizado. |
| `deleteDevice` | `DELETE /api/v1/devices/{id}` | Elimina del array y recoloca selección. |
| `loadRecentReadings` | `GET /api/v1/readings/device/{mac}/recent` | Precarga últimos puntos de gráfica. |

`rxMethod` permite lanzar operaciones asíncronas desde componentes sin suscribirse manualmente en cada caso. Internamente se combinan `tap` para efectos de estado y `switchMap` para cancelar flujos anteriores cuando entra una nueva llamada.

### 4.4. WebSocket con RxJS

`connectTelemetry` recibe una MAC y usa `WebsocketService.watchReadings(mac)`.

```ts
connectTelemetry: rxMethod<string | null>(
  pipe(
    distinctUntilChanged(),
    switchMap((mac) => {
      if (!mac) return of(null);
      return wsService.watchReadings(mac).pipe(
        filter((r) => (r.powerW as number | null | undefined) != null),
        distinctUntilChanged((prev, curr) => prev.time === curr.time),
        tap({ next: (reading) => { /* patchState */ } })
      );
    })
  )
)
```

Decisiones:

- `distinctUntilChanged()` evita reconectar si la MAC no cambia.
- `switchMap` corta la suscripción anterior cuando el usuario cambia de dispositivo.
- `filter` descarta lecturas incompletas.
- La deduplicación por `time` evita pintar el mismo instante dos veces.
- El histórico se limita a 20 puntos para mantener la gráfica ligera.

## 5. Store de tarifas

Archivo: `store/tariff.store.ts`

Estado:

```ts
interface TariffState {
  catalog: TariffResponse[];
  myTariff: TariffResponse | null;
  isLoadingCatalog: boolean;
  isLoadingMyTariff: boolean;
  errorMessage: string | null;
}
```

Computed signals:

- `hasMyTariff`: indica si el usuario tiene contrato activo.
- `isCatalogEmpty`: indica si el catálogo está vacío.

Métodos:

| Método | Endpoint | Comentario |
|---|---|---|
| `loadCatalog` | `GET /api/v1/tariffs` | Carga plantillas. |
| `loadMyTariff` | `GET /api/v1/users/me/tariff` | Acepta 200 con cuerpo o 204 sin cuerpo. |
| `saveMyTariff` | `POST /api/v1/users/me/tariff` | Guarda contrato privado. |
| `unlinkMyTariff` | `DELETE /api/v1/users/me/tariff` | Limpia `myTariff`. |
| `refreshAfterCatalogMutation` | `GET /api/v1/tariffs` | Recarga catálogo tras cambios admin. |

El store usa `catchError(() => EMPTY)` para cortar flujos fallidos sin romper las señales. El error se guarda como texto para que el componente decida cómo mostrarlo.

## 6. Servicios Angular

### 6.1. `AuthService`

Ruta base: `/api/v1/auth`

| Método | Endpoint |
|---|---|
| `authentication` | `POST /login` |
| `register` | `POST /register` |
| `exchangeOAuthTicket` | `POST /oauth/exchange` |

### 6.2. `TariffService`

Separa catálogo maestro y tarifa privada:

| Método | Endpoint |
|---|---|
| `getCatalog` | `GET /api/v1/tariffs` |
| `getById` | `GET /api/v1/tariffs/{id}` |
| `createCatalogTariff` | `POST /api/v1/tariffs` |
| `updateCatalogTariff` | `POST /api/v1/tariffs/{id}` |
| `deleteCatalogTariff` | `DELETE /api/v1/tariffs/{id}` |
| `getMyTariff` | `GET /api/v1/users/me/tariff` |
| `saveMyTariff` | `POST /api/v1/users/me/tariff` |
| `unlinkMyTariff` | `DELETE /api/v1/users/me/tariff` |

`getMyTariff` usa `observe: "response"` porque el backend puede devolver `204 No Content` si el usuario todavía no tiene tarifa.

### 6.3. `WebsocketService`

Construye la URL con el protocolo actual:

```ts
const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
const wsUrl = `${protocol}//${window.location.host}/ws-iot`;
```

Así funciona tanto en local como en producción detrás de Nginx. Se suscribe a `/topic/readings/{macAddress}` y convierte el body JSON a `ReadingResponse`.

## 7. Componentes principales

### 7.1. `DashboardComponent`

Responsabilidades:

- Cargar dispositivos y tarifa privada.
- Precargar lecturas recientes.
- Conectar el WebSocket de la MAC seleccionada.
- Consultar coste y consumo fantasma si hay tarifa.
- Preparar `chartData` y `chartOptions` para Chart.js.

Usa `effect` para reaccionar a cambios de `selectedMac` y de `hasMyTariff`. Si no hay tarifa, pone `totalCostEur` y `ghostCostEur` a `null`, evitando mostrar costes inventados.

### 7.2. `DevicesComponent`

Gestiona dispositivos físicos y simulados. El formulario cambia validadores según `deviceKind`:

- físico: exige MAC de 12 caracteres hexadecimales (`^[0-9A-Fa-f]{12}$`);
- simulado: desactiva MAC y exige `simulationProfile`.

Operaciones reales:

- reclamar físico con `POST /api/v1/devices/claim`;
- crear simulado con `POST /api/v1/devices/simulated`;
- añadir pack demo con `POST /api/v1/devices/simulated/demo-pack`;
- editar nombre/perfil con `PUT /api/v1/devices/{id}`;
- borrar con `DELETE /api/v1/devices/{id}`.

### 7.3. `TariffComponent`

Permite gestionar catálogo si el usuario tiene `ROLE_ADMIN` y gestionar la tarifa privada para cualquier usuario autenticado.

Decisión importante: al editar la tarifa privada se bloquean campos estructurales (`name`, `market`, `accessTariffCode`, `geographicZone`, `energyCompany`) y solo se editan precios y potencias. Se usa `{ emitEvent: false }` al desactivar controles para no disparar `valueChanges` y reconstruir arrays con valores por defecto.

Validaciones locales:

- `ENERGY_PERIODS` y `POWER_PERIODS` definen periodos por peaje.
- `ascendingPowerValidator` exige que las potencias no bajen de un periodo al siguiente.
- `Validators.min(0.0001)` en precios y `Validators.min(0.001)` en potencias evitan valores cero.

### 7.4. `AlertsComponent`

Carga `GET /api/v1/alerts` al iniciar, muestra tabla y permite descartar incidencias con `DELETE /api/v1/alerts/{id}`. Los mensajes de éxito/error se limpian con `effect` y temporizadores.

### 7.5. `MainLayoutComponent`

Centraliza logout:

1. llama a `telemetryStore.connectTelemetry(null)` para cortar flujo activo;
2. resetea `TelemetryStore` y `TariffStore`;
3. borra JWT;
4. navega a `/login` con `replaceUrl`.

Esta limpieza evita que un usuario posterior vea datos cacheados de la sesión anterior.

## 8. Interfaces principales

```ts
export interface Device {
  id: number;
  username: string;
  name: string;
  macAddress: string;
  isOn: boolean;
  simulated: boolean;
  simulationProfile: SimulationProfile | null;
}
```

```ts
export interface ReadingResponse {
  time: string | number;
  macAddress: string;
  powerW: number;
  energyTotalKwh: number;
  isOn: boolean;
}
```

```ts
export interface UserTariffRequest {
  templateTariffId: number | null;
  contract: TariffRequest | null;
}
```

`ReadingResponse.time` acepta `string | number` porque el backend serializa `Instant` como ISO-8601, pero algunas rutas de representación convierten el valor al montar datos de gráfica.

## 9. Flujo de datos del dashboard

1. `DashboardComponent` crea la pantalla.
2. `TelemetryStore.loadDevices()` carga dispositivos.
3. Se selecciona una MAC inicial.
4. `loadRecentReadings(mac)` trae histórico REST.
5. `connectTelemetry(mac)` abre STOMP.
6. Cada lectura actualiza `historicalReadings[mac]`.
7. `chartData` se recalcula por signals.
8. Chart.js repinta la línea de potencia.

La mezcla REST + WebSocket tiene una razón clara: REST da contexto inicial y WebSocket mantiene la vista viva.

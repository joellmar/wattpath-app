# Anexo 02. Frontend Angular, RxJS y NgRx Signals

## 1. Organización general del frontend

El frontend está desarrollado con Angular y se encuentra en `frontend/src/app`. La aplicación usa componentes standalone, rutas lazy y un estado global ligero con `@ngrx/signals` para la telemetría.

Archivos de entrada:

- `main.ts`: arranca la aplicación.
- `app.config.ts`: registra router, cliente HTTP, interceptor, animaciones y PrimeNG.
- `app.routes.ts`: define las rutas públicas y privadas.
- `app.ts` y `app.html`: componente raíz con `RouterOutlet`.

Rutas principales:

```ts
export const routes: Routes = [
  { path: "login", loadComponent: () => import("./components/login/login.component") },
  { path: "register", loadComponent: () => import("./components/register/register.component") },
  { path: "tariffs", loadComponent: () => import("./components/tariff/tariff.component"), canActivate: [authGuard] },
  { path: "devices", loadComponent: () => import("./components/devices/devices.component"), canActivate: [authGuard] },
  { path: "alerts", loadComponent: () => import("./components/alerts/alerts.component"), canActivate: [authGuard] },
  { path: "dashboard", loadComponent: () => import("./components/dashboard/dashboard.component"), canActivate: [authGuard] }
];
```

La intención de esta estructura es separar las pantallas por responsabilidad y cargar cada componente bajo demanda. Las pantallas de negocio quedan protegidas con `authGuard`, mientras que login y registro permanecen públicas.

## 2. Autenticación en cliente

### 2.1 Servicio de autenticación

Archivo: `frontend/src/app/services/auth.service.ts`

`AuthService` encapsula las dos operaciones públicas de autenticación:

| Método | Endpoint | Entrada | Salida |
| --- | --- | --- | --- |
| `authentication` | `POST /api/v1/auth/login` | `LoginUser` | `LoginUserJwt` |
| `register` | `POST /api/v1/auth/register` | `RegisterRequest` | `void` |

Interfaces relacionadas:

```ts
export interface LoginUser {
  username: string;
  password: string;
}

export interface LoginUserJwt {
  statusCode: string;
  jwt: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  tariffId?: number;
}
```

### 2.2 Persistencia del JWT

Archivo: `frontend/src/app/services/session-storage.service.ts`

El token se guarda en `sessionStorage` bajo la clave `auth_token`. No se usa `localStorage`, lo que limita la duración del token a la sesión del navegador.

`isLoggedIn()` decodifica el JWT con `jwt-decode` y comprueba el campo `exp`. Si el token está caducado o no puede decodificarse, el usuario se considera no autenticado.

### 2.3 Interceptor HTTP

Archivo: `frontend/src/app/interceptors/http.interceptor.ts`

El interceptor añade dos elementos a las peticiones:

- `X-Requested-With: XMLHttpRequest`, como cabecera común.
- `Authorization: Bearer <token>` para rutas `/api/v1`, excepto login y registro.

También captura respuestas `401`. En ese caso borra el token y redirige a `/login`. Así se evita que el usuario permanezca en pantallas privadas con una sesión inválida.

## 3. Estado global de telemetría con NgRx Signals

Archivo: `frontend/src/app/store/telemetry.store.ts`

El store concentra el estado compartido de dispositivos y lecturas en tiempo real. No usa NgRx clásico con reducers y actions, sino `@ngrx/signals`, que encaja con el modelo moderno de Angular basado en señales.

Estado inicial:

```ts
const initialState: TelemetryState = {
  devices: [],
  selectedMac: null,
  historicalReadings: {},
  isLoadingDevices: false,
};
```

Interfaz:

```ts
export interface TelemetryState {
  devices: Device[];
  selectedMac: string | null;
  historicalReadings: {
    [mac: string]: {
      timestamps: number[];
      powerW: number[];
    };
  };
  isLoadingDevices: boolean;
}
```

### 3.1 Selector computado

`currentReadings` deriva las lecturas de la MAC seleccionada:

```ts
currentReadings: computed(() => {
  const mac = state.selectedMac();
  return mac
    ? (state.historicalReadings()[mac] ?? { timestamps: [], powerW: [] })
    : { timestamps: [], powerW: [] };
})
```

La decisión de guardar el histórico por MAC permite cambiar de dispositivo sin perder las últimas muestras recibidas para cada enchufe. Cada entrada mantiene dos arrays paralelos: marcas temporales y potencia activa.

### 3.2 Métodos reactivos con RxJS

El store usa `rxMethod` para conectar operaciones asíncronas con mutaciones de estado mediante `patchState`.

| Método | Flujo RxJS | Endpoint / origen | Efecto sobre estado |
| --- | --- | --- | --- |
| `loadDevices()` | `tap` + `switchMap` | `GET /api/v1/devices` | Carga dispositivos, desactiva loading y selecciona la primera MAC si no había selección. |
| `claimDevice(payload)` | `switchMap` + `tap` | `POST /api/v1/devices/claim` | Añade el dispositivo reclamado al listado local. |
| `addDevice(newDevice)` | `switchMap` + `tap` | `POST /api/v1/devices` | Inserta el dispositivo creado. |
| `updateDevice(updatedDevice)` | `switchMap` + `tap` | `PUT /api/v1/devices/{id}` | Sustituye en memoria el dispositivo actualizado. |
| `deleteDevice(deviceId)` | `switchMap` + `tap` | `DELETE /api/v1/devices/{id}` | Elimina el dispositivo y recalcula la MAC seleccionada. |
| `connectTelemetry(mac)` | `distinctUntilChanged` + `switchMap` + `tap` | WebSocket `/topic/readings/{mac}` | Añade lecturas al histórico limitado a 20 puntos. |

`switchMap` se usa porque cada invocación debe reemplazar el flujo anterior de la operación. En `connectTelemetry`, esto es importante: al cambiar de MAC, el flujo activo pasa a escuchar el topic del nuevo dispositivo.

El segundo `distinctUntilChanged` de `connectTelemetry` compara `powerW`:

```ts
distinctUntilChanged((previous, current) => previous.powerW === current.powerW)
```

Con esto se evita añadir puntos repetidos a la gráfica cuando la potencia no cambia entre mensajes consecutivos.

## 4. WebSocket/STOMP en Angular

Archivo: `frontend/src/app/services/websocket.service.ts`

`WebsocketService` crea un cliente `RxStomp` y calcula la URL según el protocolo actual:

```ts
const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
const wsUrl = `${protocol}//${window.location.host}/ws-iot`;
```

La conexión queda configurada con:

- `heartbeatOutgoing: 20000`;
- `reconnectDelay: 5000`;
- topic dinámico `/topic/readings/{macAddress}`.

Método principal:

```ts
watchReadings(macAddress: string): Observable<ReadingResponse> {
  const destination = `/topic/readings/${macAddress}`;

  return this.rxStomp
    .watch(destination)
    .pipe(map((message) => JSON.parse(message.body) as ReadingResponse));
}
```

La salida ya llega tipada como `ReadingResponse`, por lo que el store puede tratar cada mensaje como una lectura lista para graficar.

## 5. Servicios Angular de dominio

Además de `AuthService`, `SessionStorageService` y `WebsocketService`, el proyecto contiene dos servicios de dominio sencillos.

### 5.1 `DeviceService`

Archivo: `frontend/src/app/services/device.service.ts`

```ts
readonly devicesResource = httpResource<Device[]>(() => "/api/v1/devices", {
  defaultValue: [],
});
```

Este servicio declara un `httpResource` para cargar dispositivos desde `/api/v1/devices`. En la implementación actual, la gestión principal de dispositivos se hace desde `TelemetryStore` y desde `DevicesComponent`, pero este servicio deja preparada una forma declarativa de consumir el listado con la API moderna de Angular.

### 5.2 `TariffService`

Archivo: `frontend/src/app/services/tariff.service.ts`

```ts
createTariff(tariff: TariffRequest): Observable<void> {
  return this.http.post<void>(this.baseUrl, tariff);
}
```

Encapsula la creación de tarifas contra `/api/v1/tariffs`. El componente `TariffComponent` usa actualmente `HttpClient` directamente para poder gestionar también consulta y borrado de la tarifa activa desde la misma pantalla.

## 6. Dashboard de telemetría y analítica

Archivo: `frontend/src/app/components/dashboard/dashboard.component.ts`

El dashboard es la pantalla que une telemetría en tiempo real y cálculo económico.

Flujo al construir el componente:

1. Llama a `store.loadDevices()`.
2. Consulta si hay tarifas disponibles con `checkTariffStatus()`.
3. Crea un `effect()` que observa `store.selectedMac()`.
4. Cuando hay MAC seleccionada:
   - abre la suscripción WebSocket con `store.connectTelemetry(mac)`;
   - consulta coste total y consumo fantasma con `loadAnalyticsMetrics(mac)`.

Señales destacadas:

```ts
readonly totalCostEur = signal<number>(0.0);
readonly ghostCostEur = signal<number>(0.0);
readonly powerW = computed(() => this.historicalData().powerW);
```

La gráfica se alimenta con `chartData`, que transforma las marcas temporales a formato horario español (`es-ES`) y usa `powerW` como serie de datos. El dataset se muestra como "Consumo activo (W)".

Endpoints consumidos desde el dashboard:

| Endpoint | Uso |
| --- | --- |
| `GET /api/v1/devices` | Cargar medidores del usuario. |
| `GET /api/v1/tariffs` | Saber si la analítica económica puede mostrarse. |
| `GET /api/v1/analytics/cost` | Calcular coste acumulado del día. |
| `GET /api/v1/analytics/ghost-consumption` | Calcular coste de madrugada. |

El rango enviado a analítica va desde el inicio del día local hasta el instante actual:

```ts
const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 0, 0, 0, 0);
```

## 7. Gestión de dispositivos

Archivo: `frontend/src/app/components/devices/devices.component.ts`

Esta pantalla permite vincular, listar, eliminar y cambiar el estado virtual de dispositivos IoT.

Formulario:

- `name`: requerido, mínimo 3 caracteres.
- `macAddress`: requerido, con patrón `^[0-9A-Fa-f]{12}$`.

Aunque el store contiene métodos CRUD para dispositivos, este componente realiza las peticiones con `HttpClient` directamente y después llama a `store.loadDevices()` para refrescar la tabla. La intención práctica es que la vista siempre vuelva a sincronizarse con el backend tras una operación.

Operaciones:

| Acción | Endpoint | Resultado en UI |
| --- | --- | --- |
| Alta / vinculación | `POST /api/v1/devices/claim` | Muestra mensaje de éxito y recarga dispositivos. |
| Baja | `DELETE /api/v1/devices/{id}` | Refresca tabla tras confirmar. |
| Cambio de estado virtual | `PUT /api/v1/devices/{id}` | Invierte `isOn` y recarga dispositivos. |

## 8. Configuración de tarifas

Archivo: `frontend/src/app/components/tariff/tariff.component.ts`

El componente de tarifas gestiona un formulario reactivo con datos generales de contrato y una lista de periodos horarios.

Campos principales:

- `name`;
- `type`, con opciones `2.0TD`, `3.0TD` y `6.1TD`;
- `market`, con valores `libre` y `regulado`;
- `contractedPowerKw`;
- `energyCompany`;
- `periods`.

Periodos por defecto:

```ts
[
  { name: "P1 Punta", startHour: "08:00:00", endHour: "16:00:00" },
  { name: "P2 Llano", startHour: "16:00:00", endHour: "23:59:59" },
  { name: "P3 Valle", startHour: "00:00:00", endHour: "08:00:00" }
]
```

La hora final `23:59:59` evita enviar `24:00:00`, que no es un valor válido para `LocalTime` en Spring Boot.

El componente consulta `GET /api/v1/tariffs` al arrancar. Si existe una tarifa, se guarda en `activeTariff` para bloquear visualmente el flujo del MVP y evitar que el usuario configure varias tarifas desde esta pantalla.

## 9. Gestión de alertas

Archivo: `frontend/src/app/components/alerts/alerts.component.ts`

La pantalla de alertas usa señales locales:

```ts
readonly alertsList = signal<Alert[]>([]);
readonly isLoading = signal<boolean>(false);
readonly errorMessage = signal<string | null>(null);
readonly successMessage = signal<string | null>(null);
```

Endpoints:

| Acción | Endpoint | Estado modificado |
| --- | --- | --- |
| Cargar alertas | `GET /api/v1/alerts` | `alertsList`, `isLoading`, `errorMessage` |
| Descartar alerta | `DELETE /api/v1/alerts/{id}` | Mensaje de éxito y recarga de lista |

La interfaz `Alert` coincide con el DTO de backend:

```ts
export interface Alert {
  id: number;
  macAddress: string;
  username: string;
  type: string;
  message: string;
  createdAt: string;
}
```

## 10. Modelos compartidos con el backend

Los modelos TypeScript reflejan los DTOs Java que devuelve la API:

| Interfaz frontend | DTO/backend relacionado | Uso |
| --- | --- | --- |
| `Device` | `DeviceDto` | Listado y gestión de enchufes. |
| `ReadingResponse` | `ReadingResponse` | Mensajes WebSocket y gráfica. |
| `TariffRequest` | `TariffDto` / `PeriodDto` | Configuración de tarifa y periodos. |
| `EnergyCostResponse` | `Map<String,Object>` de `/analytics/cost` | Coste diario acumulado. |
| `GhostCostResponse` | `Map<String,Object>` de `/analytics/ghost-consumption` | Consumo fantasma. |
| `Alert` | `AlertDto` | Historial de alertas. |

## 11. Flujo completo de datos hasta la vista

1. El usuario inicia sesión y el token queda en `sessionStorage`.
2. El interceptor añade el JWT a cada llamada `/api/v1`.
3. `DashboardComponent` carga dispositivos mediante `TelemetryStore`.
4. El store selecciona una MAC activa.
5. El `effect()` del dashboard conecta WebSocket para esa MAC.
6. El backend publica lecturas en `/topic/readings/{macAddress}`.
7. `WebsocketService` convierte el mensaje STOMP en `ReadingResponse`.
8. `TelemetryStore` añade la muestra al histórico de la MAC.
9. El dashboard recalcula `chartData` con señales computadas.
10. PrimeNG/Chart.js actualiza la gráfica sin recargar la página.

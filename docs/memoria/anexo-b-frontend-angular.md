# Anexo B. Frontend Angular, RxJS y NgRx Signals

Este anexo describe el frontend real de Wattimizer ubicado en `frontend/src/app`. La aplicacion esta construida con Angular standalone, rutas lazy, PrimeNG, RxJS, NgRx Signals y STOMP sobre WebSocket.

## 1. Estructura general

| Carpeta | Responsabilidad |
| --- | --- |
| `components` | Vistas principales: login, registro, layout, dashboard, dispositivos, tarifas y alertas. |
| `services` | Comunicacion HTTP, WebSocket y sesion local. |
| `store` | Estado global con NgRx Signals para telemetria y tarifas. |
| `interfaces` | Contratos TypeScript alineados con DTOs Java. |
| `guards` | Proteccion de rutas privadas. |
| `interceptors` | Inyeccion de JWT y gestion de 401. |

Bootstrap principal:

- `app.config.ts` registra router, `HttpClient` con interceptor y PrimeNG.
- `app.routes.ts` define rutas publicas y privadas.
- `app.ts` actua como shell raiz con `RouterOutlet`.

## 2. Rutas y proteccion

| Ruta | Componente | Proteccion |
| --- | --- | --- |
| `/login` | `LoginComponent` | Publica |
| `/register` | `RegisterComponent` | Publica |
| `/auth/oauth/callback` | `OAuthCallbackComponent` | Publica |
| `/dashboard` | `DashboardComponent` dentro de `MainLayoutComponent` | `authGuard` |
| `/devices` | `DevicesComponent` dentro de `MainLayoutComponent` | `authGuard` |
| `/tariffs` | `TariffComponent` dentro de `MainLayoutComponent` | `authGuard` |
| `/alerts` | `AlertsComponent` dentro de `MainLayoutComponent` | `authGuard` |

El guard se aplica dos veces: en `canActivate` para entrar al layout y en `canActivateChild` para revalidar cada navegacion interna. Esta decision evita que un usuario con token caducado entre directamente a una URL privada desde el navegador.

## 3. Sesion, JWT e interceptor HTTP

### `SessionStorageService`

Guarda el JWT en `sessionStorage` con la clave `auth_token`. No usa `localStorage`, lo cual reduce la persistencia del token cuando se cierra el navegador.

Funciones principales:

- `saveToken(jwt)`: guarda el token recibido del backend.
- `getToken()`: devuelve el token actual.
- `isLoggedIn()`: decodifica el JWT y comprueba `exp`.
- `getUsername()`: extrae el usuario del payload.
- `getAuthorities()` y `hasRole(...)`: revisan roles como `ROLE_USER` o `ROLE_ADMIN`.
- `logout()`: elimina la sesion local.

### `httpInterceptor`

El interceptor aplica tres reglas:

1. Anade `X-Requested-With: XMLHttpRequest`.
2. Si la URL contiene `/api/v1` y no es una ruta publica de autenticacion, adjunta `Authorization: Bearer <token>`.
3. Si el backend responde `401`, hace logout y navega a `/login`.

Esta capa evita repetir codigo de autenticacion en cada servicio.

## 4. Componentes principales

### `LoginComponent`

Gestiona el formulario de email y contrasena. Llama a `AuthService.login`, guarda el JWT y navega al dashboard. Tambien inicia login social redirigiendo a:

```text
/oauth2/authorization/google
/oauth2/authorization/github
```

### `RegisterComponent`

Permite crear una cuenta normal. Valida contrasena y confirmacion en el cliente antes de llamar al backend. La validacion definitiva queda igualmente en el backend.

### `OAuthCallbackComponent`

Lee el `ticket` recibido en la URL tras OAuth2 y llama a `AuthService.exchangeOAuthTicket`. Si el ticket es valido, guarda el JWT y redirige a `/dashboard`; si no, muestra error y vuelve a login.

### `MainLayoutComponent`

Es el chasis privado de la aplicacion. Contiene navegacion y boton de salida. En logout realiza una limpieza completa:

1. Corta el stream de telemetria con `connectTelemetry(null)`.
2. Resetea `TelemetryStore`.
3. Resetea `TariffStore`.
4. Borra token de sesion.
5. Navega a `/login` con `replaceUrl`.

La limpieza de stores es importante para que un segundo usuario no vea datos cacheados del anterior.

### `DashboardComponent`

Vista principal de monitorizacion:

- Carga dispositivos desde `TelemetryStore`.
- Carga tarifa privada desde `TariffStore`.
- Permite seleccionar MAC.
- Muestra grafica de potencia con Chart.js.
- Consulta `/api/v1/analytics/cost` y `/api/v1/analytics/ghost-consumption`.
- Reacciona a cambios de dispositivo mediante signals y efectos.

### `DevicesComponent`

Gestiona alta, edicion y borrado de dispositivos. Aunque `TelemetryStore` tiene metodos CRUD, este componente hace varias llamadas HTTP directas y despues refresca el store con `loadDevices()`. Es una mezcla consciente pero mejorable: centralizar todo el CRUD en el store simplificaria el mantenimiento.

### `TariffComponent`

Permite al usuario trabajar con dos niveles:

- **Catalogo maestro:** visible para usuarios autenticados; editable solo por admin.
- **Tarifa privada:** contrato real del usuario, editable desde `/api/v1/users/me/tariff`.

El componente bloquea o habilita acciones segun roles obtenidos del JWT.

### `AlertsComponent`

Usa signals locales para cargar y borrar alertas:

- `GET /api/v1/alerts`
- `DELETE /api/v1/alerts/{id}`

No usa store global porque el flujo es simple y no se comparte con otras vistas salvo el posible broadcast STOMP futuro.

## 5. Servicios Angular

| Servicio | Responsabilidad | Endpoints o tecnologia |
| --- | --- | --- |
| `AuthService` | Login, registro y canje OAuth2. | `/api/v1/auth/*` |
| `TariffService` | Catalogo y tarifa privada. | `/api/v1/tariffs`, `/api/v1/users/me/tariff` |
| `WebsocketService` | Cliente RxStomp para lecturas. | `/ws-iot`, `/topic/readings/{mac}` |
| `SessionStorageService` | Token, roles y logout. | `sessionStorage`, `jwt-decode` |
| `DeviceService` | Recurso HTTP experimental para dispositivos. | `/api/v1/devices` |

`DeviceService` existe pero no es la via principal usada por los componentes; la gestion real se concentra en `TelemetryStore` y en HTTP directo desde `DevicesComponent`.

## 6. `TelemetryStore`

Archivo: `frontend/src/app/store/telemetry.store.ts`

Estado:

```typescript
{
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

Computed signal:

- `currentReadings`: devuelve el historico de la MAC seleccionada o arrays vacios si no hay seleccion.

Metodos principales:

| Metodo | Logica |
| --- | --- |
| `loadDevices` | `GET /api/v1/devices`; marca loading y selecciona la primera MAC si no habia seleccion previa. |
| `setSelectedMac` | Cambia MAC seleccionada y precarga lecturas recientes. |
| `loadRecentReadings` | `GET /api/v1/readings/device/{mac}/recent?seconds=120`; conserva como maximo 20 puntos. |
| `connectTelemetry` | Abre/cambia suscripcion STOMP segun MAC. |
| `claimDevice` | `POST /api/v1/devices/claim`. |
| `createSimulatedDevice` | `POST /api/v1/devices/simulated`. |
| `addDevice` | `POST /api/v1/devices`. |
| `updateDevice` | `PUT /api/v1/devices/{id}`. |
| `deleteDevice` | `DELETE /api/v1/devices/{id}`. |
| `reset` | Limpia el estado en logout. |

### Flujo RxJS de telemetria

```typescript
connectTelemetry: rxMethod<string | null>(
  pipe(
    distinctUntilChanged(),
    switchMap((mac) => {
      if (!mac) return of(null);
      return wsService.watchReadings(mac).pipe(
        filter((r) => r.powerW != null),
        distinctUntilChanged((prev, curr) => prev.time === curr.time),
        tap({ next: (reading) => { /* actualiza historicalReadings */ } })
      );
    })
  )
)
```

Decisiones importantes:

- `distinctUntilChanged()` evita reconectar si la MAC no cambia.
- `switchMap()` cancela la suscripcion anterior al cambiar de dispositivo.
- `filter()` descarta mensajes incompletos o sin potencia.
- La deduplicacion por `time` evita pintar dos veces una lectura emitida por canales cercanos.
- El buffer de 20 puntos mantiene la grafica ligera.

## 7. `TariffStore`

Archivo: `frontend/src/app/store/tariff.store.ts`

Estado:

```typescript
{
  catalog: TariffResponse[];
  myTariff: TariffResponse | null;
  isLoadingCatalog: boolean;
  isLoadingMyTariff: boolean;
  errorMessage: string | null;
}
```

Computed signals:

- `hasMyTariff`: `true` si el usuario ya tiene contrato.
- `isCatalogEmpty`: util para mostrar estados vacios.

Metodos:

| Metodo | Logica |
| --- | --- |
| `loadCatalog` | Carga catalogo maestro. |
| `loadMyTariff` | Carga tarifa privada; `204 No Content` se trata como `null` desde `TariffService`. |
| `saveMyTariff` | Guarda o actualiza contrato privado. |
| `unlinkMyTariff` | Desvincula la tarifa del usuario. |
| `refreshAfterCatalogMutation` | Recarga catalogo tras operaciones admin. |
| `setCatalogTariff`, `addToCatalog`, `removeFromCatalog` | Helpers sincronicos para mantener UI actualizada. |
| `patchMyTariff` | Actualiza tarifa privada en memoria. |
| `clearError`, `reset` | Limpieza de estado. |

El store usa `catchError(() => EMPTY)` para cortar el flujo despues de guardar el error en estado. Asi el error queda visible para la UI sin romper la cadena reactiva.

## 8. WebSocket STOMP

`WebsocketService` usa `@stomp/rx-stomp`.

Configuracion relevante:

- URL: `ws://<host>/ws-iot` o `wss://<host>/ws-iot` segun protocolo de la pagina.
- Heartbeat saliente: 20 segundos.
- Reconexion: 5 segundos.
- Topic de lecturas: `/topic/readings/{macAddress}`.

El servicio parsea el JSON recibido y lo entrega como `ReadingResponse`. La transformacion se hace aqui para que los componentes no dependan del formato bruto STOMP.

## 9. Interfaces principales

| Interface | Uso |
| --- | --- |
| `LoginUser` | Credenciales de login. |
| `LoginUserJwt` | Respuesta con JWT. |
| `RegisterRequest` | Alta de usuario. |
| `JwtPayload` | Decodificacion de `username`, `authorities` y `exp`. |
| `Device` | Dispositivo fisico o simulado. |
| `CreateSimulatedDeviceRequest` | Alta de simulador. |
| `SimulationProfile` | Enum TypeScript de perfiles simulados. |
| `ReadingResponse` | Lectura para grafica e historial. |
| `TariffResponse` | Contrato tarifario completo. |
| `UserTariffRequest` | Asignacion o edicion de tarifa privada. |
| `EnergyCostResponse` | Coste total del intervalo. |
| `GhostCostResponse` | Coste fantasma nocturno. |
| `Alert` | Alerta mostrada en la vista de alertas. |

## 10. Flujo de datos del dashboard

1. El usuario entra en `/dashboard`.
2. `authGuard` comprueba que el JWT no este caducado.
3. `DashboardComponent` solicita dispositivos y tarifa.
4. `TelemetryStore` selecciona una MAC.
5. Se precargan lecturas recientes por REST.
6. `connectTelemetry(mac)` abre el topic STOMP.
7. Cada lectura nueva actualiza `historicalReadings`.
8. La grafica se repinta desde `currentReadings`.
9. Si hay tarifa privada, el dashboard consulta coste total y coste fantasma.

El resultado es una pantalla que mezcla datos historicos recientes con tiempo real, sin tener que recargar la pagina.

## 11. Observaciones tecnicas

- La aplicacion usa estado global solo donde aporta valor: telemetria y tarifas.
- Alertas se mantienen locales porque su flujo actual es corto.
- El uso de signals reduce suscripciones manuales en componentes.
- La gestion de errores esta repartida entre interceptor, stores y componentes; funciona, aunque se podria unificar mas.
- Hay una duplicidad parcial entre metodos CRUD del `TelemetryStore` y llamadas directas de `DevicesComponent`.

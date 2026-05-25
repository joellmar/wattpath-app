import { signalStore, withState, withMethods, withComputed, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { computed, inject } from '@angular/core';
import { delay, distinctUntilChanged, of, pipe, switchMap, tap, timestamp } from 'rxjs';
import { DeviceService } from '../services/device.service';
import { WebsocketService } from '../services/websocket.service';
import { TelemetryState } from '../interfaces/telemetry-state.interface';
import { HttpClient } from '@angular/common/http';
import { Device } from '../interfaces/device.interface';
import { SessionStorageService } from '../services/session-storage.service';

const initialState: TelemetryState = {
  devices: [],
  selectedMac: null,
  historicalReadings: {},
  isLoadingDevices: false,
};

export const TelemetryStore = signalStore(
  { providedIn: 'root' },
  withState(initialState),

  // Selectores reactivos (Computed Signals)
  withComputed(state => ({
    currentReadings: computed(() => {
      const mac = state.selectedMac();
      return mac
        ? state.historicalReadings()[mac] ?? { timestamps: [], powerW: [] }
        : { timestamps: [], powerW: [] };
    }),
  })),

  // Métodos para alterar el estado de forma controlada e inmutable
  withMethods((
    store,
    http = inject(HttpClient),
    wsService = inject(WebsocketService)
  ) => ({
    setSelectedMac(mac: string | null): void {
      patchState(store, { selectedMac: mac });
    },

    // Corregido: Forzamos la resolución asíncrona diferida del token garantizando su lectura post-navegación
    loadDevices: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { isLoadingDevices: true })),
        switchMap(() => {
          // CRÍTICO: Añadida la barra inicial para que el interceptor cace la ruta
          return http.get<Device[]>("/api/v1/devices").pipe(
            tap({
              next: (devicesList) => {
                if (!devicesList) return;

                const firstMac = devicesList.length > 0 ? devicesList[0].macAddress : null;
                patchState(store, {
                  devices: devicesList,
                  selectedMac: store.selectedMac() ?? firstMac,
                  isLoadingDevices: false
                });
              },
              error: (err) => {
                // Manejo de errores estricto apagando el loading
                patchState(store, { isLoadingDevices: false });
                console.error("No se han podido cargar los dispositivos. Verifica tu sesión o conexión.", err);
              }
            })
          );
        })
      )
    ),

    // Escuchamos el WebSocket de forma reactiva según la MAC seleccionada
    connectTelemetry: rxMethod<string | null>(
      pipe(
        distinctUntilChanged(),
        switchMap(mac => {
          if (!mac) return of(null);
          return wsService.watchReadings(mac).pipe(
            distinctUntilChanged((previous, current) => previous.powerW === current.powerW),
            tap({
              next: (reading) => {
                const currentHistory = store.historicalReadings()[mac] ?? { timestamps: [], powerW: [] };
                const nextTimestamps = [...currentHistory.timestamps, reading.time];
                const nextPowerW = [...currentHistory.powerW, reading.powerW];

                const limit = 20;
                patchState(store, state => ({
                  historicalReadings: {
                    ...state.historicalReadings,
                    [mac]: {
                      timestamps: nextTimestamps.length > limit ? nextTimestamps.slice(1) : nextTimestamps,
                      powerW: nextPowerW.length > limit ? nextPowerW.slice(1) : nextPowerW,
                    }
                  }
                }));
              },
              error: (err) => console.error("Error en el flujo del WebSocket.", err)
            })
          );
        })
      )
    )
  }))
);

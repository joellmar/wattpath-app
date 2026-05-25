import { signalStore, withState, withMethods, withComputed, patchState } from '@ngrx/signals';
import { rxMethod } from '@ngrx/signals/rxjs-interop';
import { computed, inject } from '@angular/core';
import { distinctUntilChanged, of, pipe, switchMap, tap, timestamp } from 'rxjs';
import { DeviceService } from '../services/device.service';
import { WebsocketService } from '../services/websocket.service';
import { TelemetryState } from '../interfaces/telemetry-state.interface';

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
  withMethods((store, deviceService = inject(DeviceService), wsService = inject(WebsocketService)) => ({
    setSelectedMac(mac: string | null): void {
      patchState(store, { selectedMac: mac });
    },

    // Cargamos los dispositivos usando un flujo asíncrono
    loadDevices: rxMethod<void>(
      pipe(
        tap(() => patchState(store, { isLoadingDevices: true })),
        switchMap(() => {
          // Usamos el servicio HTTP de dispositivos (adaptado a señal o pipeline)
          // Nota: Asumimos que deviceService expone un método http tradicional o mapeamos su resource
          const devicesStream = deviceService.devicesResource
            ? of(deviceService.devicesResource.value() ?? [])
            : of([]);
          return devicesStream;
        }),
        tap(devicesList => {
          const firstMac = devicesList.length > 0 ? devicesList[0].macAddress : null;
          patchState(store, {
            devices: devicesList,
            selectedMac: store.selectedMac() ?? firstMac,
            isLoadingDevices: false
          });
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
            tap(reading => {
              // Actualizamos el histórico del mapa de lecturas de forma inmutable
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
            })
          );
        })
      )
    )
  }))
);

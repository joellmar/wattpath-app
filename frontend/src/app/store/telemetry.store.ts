import { HttpClient } from "@angular/common/http";
import { computed, inject } from "@angular/core";
import {
	patchState,
	signalStore,
	withComputed,
	withMethods,
	withState,
} from "@ngrx/signals";
import { rxMethod } from "@ngrx/signals/rxjs-interop";
import { distinctUntilChanged, filter, of, pipe, switchMap, tap } from "rxjs";
import type { Device } from "../interfaces/device.interface";
import type { TelemetryState } from "../interfaces/telemetry-state.interface";
import { WebsocketService } from "../services/websocket.service";

const initialState: TelemetryState = {
	devices: [],
	selectedMac: null,
	historicalReadings: {},
	isLoadingDevices: false,
};

export const TelemetryStore = signalStore(
	{ providedIn: "root" },
	withState(initialState),

	// Selectores reactivos (Computed Signals)
	withComputed((state) => ({
		currentReadings: computed(() => {
			const mac = state.selectedMac();
			return mac
				? (state.historicalReadings()[mac] ?? { timestamps: [], powerW: [] })
				: { timestamps: [], powerW: [] };
		}),
	})),

	// Métodos para alterar el estado de forma controlada e inmutable
	withMethods(
		(
			store,
			http = inject(HttpClient),
			wsService = inject(WebsocketService),
		) => ({
			setSelectedMac(mac: string | null): void {
				patchState(store, { selectedMac: mac });
			},

			// GET /api/v1/devices -> Recupera los medidores del usuario en sesión
			loadDevices: rxMethod<void>(
				pipe(
					tap(() => patchState(store, { isLoadingDevices: true })),
					switchMap(() => {
						// CRÍTICO: Añadida la barra inicial para que el interceptor cace la ruta
						return http.get<Device[]>("/api/v1/devices").pipe(
							tap({
								next: (devicesList) => {
									if (!devicesList) return;

									const firstMac =
										devicesList.length > 0 ? devicesList[0].macAddress : null;
									patchState(store, {
										devices: devicesList,
										selectedMac: store.selectedMac() ?? firstMac,
										isLoadingDevices: false,
									});
								},
								error: (err) => {
									// Manejo de errores estricto apagando el loading
									patchState(store, { isLoadingDevices: false });
									console.error(
										"No se han podido cargar los dispositivos. Verifica tu sesión o conexión.",
										err,
									);
								},
							}),
						);
					}),
				),
			),

			// POST /api/v1/devices/claim -> HU-11: Reclama un dispositivo huérfano asociado a SYSTEM
			claimDevice: rxMethod<{ name: string; macAddress: string }>(
				pipe(
					switchMap((payload) => {
						return http.post<Device>("/api/v1/devices/claim", payload).pipe(
							tap({
								next: (claimedDevice) => {
									const currentList = store.devices();
									const updatedList = [...currentList, claimedDevice];
									patchState(store, {
										devices: updatedList,
										selectedMac:
											store.selectedMac() ?? claimedDevice.macAddress,
									});
								},
								error: (err) => {
									console.error(
										"error de vinculación en el endpoint claim del backend:",
										err,
									);
								},
							}),
						);
					}),
				),
			),

			// NUEVO MÉTODO CRUD: Alta de dispositivo IoT (HU-11)
			addDevice: rxMethod<{ name: string; macAddress: string }>(
				pipe(
					switchMap((newDevice) => {
						return http.post<Device>("/api/v1/devices", newDevice).pipe(
							tap({
								next: (createdDevice) => {
									const updatedList = [...store.devices(), createdDevice];
									patchState(store, {
										devices: updatedList,
										selectedMac:
											store.selectedMac() ?? createdDevice.macAddress,
									});
								},
								error: (err) =>
									console.error(
										"error al registrar el dispositivo en el backend:",
										err,
									),
							}),
						);
					}),
				),
			),

			// PUT /api/v1/devices/{id} -> Actualización administrativa (Nombre/Estado virtual)
			updateDevice: rxMethod<{
				id: number;
				name: string;
				macAddress: string;
				isOn: boolean;
			}>(
				pipe(
					switchMap((updatedDevice) => {
						return http
							.put<Device>(`/api/v1/devices/${updatedDevice.id}`, updatedDevice)
							.pipe(
								tap({
									next: (response) => {
										const updatedList = store
											.devices()
											.map((d) => (d.id === response.id ? response : d));
										patchState(store, { devices: updatedList });
									},
									error: (err) =>
										console.error(
											"error al actualizar el medidor en el servidor:",
											err,
										),
								}),
							);
					}),
				),
			),

			// DELETE /api/v1/devices/{id} -> Elimina la vinculación del enchufe
			deleteDevice: rxMethod<number>(
				pipe(
					switchMap((deviceId) => {
						return http.delete<void>(`/api/v1/devices/${deviceId}`).pipe(
							tap({
								next: () => {
									const updatedList = store
										.devices()
										.filter((d) => d.id !== deviceId);
									const firstMac =
										updatedList.length > 0 ? updatedList[0].macAddress : null;
									patchState(store, {
										devices: updatedList,
										selectedMac: updatedList.some(
											(d) => d.macAddress === store.selectedMac(),
										)
											? store.selectedMac()
											: firstMac,
									});
								},
								error: (err) =>
									console.error(
										"error al eliminar el dispositivo del sistema:",
										err,
									),
							}),
						);
					}),
				),
			),

			// Escuchamos el WebSocket de forma reactiva según la MAC seleccionada
			connectTelemetry: rxMethod<string | null>(
				pipe(
					distinctUntilChanged(),
					switchMap((mac) => {
						if (!mac) return of(null);
						return wsService.watchReadings(mac).pipe(
							// Descartamos lecturas con powerW nulo (Shelly apagado o mensaje incompleto)
							// para evitar gaps en la línea de la gráfica.
							filter((r) => (r.powerW as number | null | undefined) != null),
							// Deduplicamos por timestamp exacto: si backend emite desde
							// statusChannel y eventsRpcChannel al mismo instante, solo pintamos uno.
							distinctUntilChanged((prev, curr) => prev.time === curr.time),
							tap({
								next: (reading) => {
									const currentHistory = store.historicalReadings()[mac] ?? {
										timestamps: [],
										powerW: [],
									};
									const nextTimestamps = [
										...currentHistory.timestamps,
										reading.time,
									];
									const nextPowerW = [...currentHistory.powerW, reading.powerW];

									const limit = 20;
									patchState(store, (state) => ({
										historicalReadings: {
											...state.historicalReadings,
											[mac]: {
												timestamps:
													nextTimestamps.length > limit
														? nextTimestamps.slice(1)
														: nextTimestamps,
												powerW:
													nextPowerW.length > limit
														? nextPowerW.slice(1)
														: nextPowerW,
											},
										},
									}));
								},
								error: (err) =>
									console.error("Error en el flujo del WebSocket.", err),
							}),
						);
					}),
				),
			),
		}),
	),
);

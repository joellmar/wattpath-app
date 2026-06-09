import { computed, inject } from "@angular/core";
import {
	patchState,
	signalStore,
	withComputed,
	withMethods,
	withState,
} from "@ngrx/signals";
import { rxMethod } from "@ngrx/signals/rxjs-interop";
import { EMPTY, pipe, switchMap, tap } from "rxjs";
import { catchError } from "rxjs/operators";
import type {
	TariffResponse,
	UserTariffRequest,
} from "../interfaces/tariff-request.interface";
import { TariffService } from "../services/tariff.service";

interface TariffState {
	catalog: TariffResponse[];
	myTariff: TariffResponse | null;
	isLoadingCatalog: boolean;
	isLoadingMyTariff: boolean;
	errorMessage: string | null;
}

const initialState: TariffState = {
	catalog: [],
	myTariff: null,
	isLoadingCatalog: false,
	isLoadingMyTariff: false,
	errorMessage: null,
};

export const TariffStore = signalStore(
	{ providedIn: "root" },
	withState(initialState),

	withComputed((state) => ({
		hasMyTariff: computed(() => state.myTariff() !== null),
		isCatalogEmpty: computed(() => state.catalog().length === 0),
	})),

	withMethods((store, tariffService = inject(TariffService)) => ({
		// Carga el catálogo maestro global
		loadCatalog: rxMethod<void>(
			pipe(
				tap(() =>
					patchState(store, { isLoadingCatalog: true, errorMessage: null }),
				),
				switchMap(() =>
					tariffService.getCatalog().pipe(
						tap({
							next: (catalog) =>
								patchState(store, { catalog, isLoadingCatalog: false }),
							error: (err) => {
								patchState(store, {
									isLoadingCatalog: false,
									errorMessage:
										err?.error?.message ??
										"No se ha podido cargar el catálogo de tarifas.",
								});
							},
						}),
						catchError(() => EMPTY),
					),
				),
			),
		),

		// Carga la tarifa privada del usuario autenticado
		loadMyTariff: rxMethod<void>(
			pipe(
				tap(() =>
					patchState(store, { isLoadingMyTariff: true, errorMessage: null }),
				),
				switchMap(() =>
					tariffService.getMyTariff().pipe(
						tap({
							next: (myTariff) =>
								patchState(store, { myTariff, isLoadingMyTariff: false }),
							error: (err) => {
								patchState(store, {
									isLoadingMyTariff: false,
									errorMessage:
										err?.error?.message ??
										"No se ha podido recuperar tu tarifa asignada.",
								});
							},
						}),
						catchError(() => {
							patchState(store, { isLoadingMyTariff: false });
							return EMPTY;
						}),
					),
				),
			),
		),

		// Guarda (crea o actualiza) la tarifa privada del usuario
		saveMyTariff: rxMethod<UserTariffRequest>(
			pipe(
				tap(() =>
					patchState(store, { isLoadingMyTariff: true, errorMessage: null }),
				),
				switchMap((payload) =>
					tariffService.saveMyTariff(payload).pipe(
						tap({
							next: (myTariff) =>
								patchState(store, { myTariff, isLoadingMyTariff: false }),
							error: (err) => {
								patchState(store, {
									isLoadingMyTariff: false,
									errorMessage:
										err?.error?.message ??
										"No se ha podido guardar la tarifa. Revisa los datos del contrato.",
								});
							},
						}),
						catchError(() => EMPTY),
					),
				),
			),
		),

		// Desvincula la tarifa privada del usuario (no toca el catálogo)
		unlinkMyTariff: rxMethod<void>(
			pipe(
				tap(() =>
					patchState(store, { isLoadingMyTariff: true, errorMessage: null }),
				),
				switchMap(() =>
					tariffService.unlinkMyTariff().pipe(
						tap({
							next: () =>
								patchState(store, { myTariff: null, isLoadingMyTariff: false }),
							error: (err) => {
								patchState(store, {
									isLoadingMyTariff: false,
									errorMessage:
										err?.error?.message ??
										"No se ha podido desvincular la tarifa.",
								});
							},
						}),
						catchError(() => EMPTY),
					),
				),
			),
		),

		// Mutación del catálogo maestro (ADMIN): recarga tras crear/editar/borrar
		refreshAfterCatalogMutation: rxMethod<void>(
			pipe(
				tap(() =>
					patchState(store, { isLoadingCatalog: true, errorMessage: null }),
				),
				switchMap(() =>
					tariffService.getCatalog().pipe(
						tap({
							next: (catalog) =>
								patchState(store, { catalog, isLoadingCatalog: false }),
							error: (err) => {
								patchState(store, {
									isLoadingCatalog: false,
									errorMessage:
										err?.error?.message ?? "No se pudo actualizar el catálogo.",
								});
							},
						}),
						catchError(() => EMPTY),
					),
				),
			),
		),

		// Helpers síncronos para actualizar el estado desde componentes sin HTTP
		setCatalogTariff(updated: TariffResponse): void {
			const catalog = store
				.catalog()
				.map((t) => (t.id === updated.id ? updated : t));
			patchState(store, { catalog });
		},

		addToCatalog(created: TariffResponse): void {
			patchState(store, { catalog: [...store.catalog(), created] });
		},

		removeFromCatalog(id: number): void {
			patchState(store, {
				catalog: store.catalog().filter((t) => t.id !== id),
			});
		},

		patchMyTariff(updated: TariffResponse): void {
			patchState(store, { myTariff: updated });
		},

		clearError(): void {
			patchState(store, { errorMessage: null });
		},
	})),
);

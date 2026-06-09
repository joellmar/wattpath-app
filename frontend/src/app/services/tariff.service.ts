import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import { EMPTY, type Observable } from "rxjs";
import { catchError, map } from "rxjs/operators";
import type {
	TariffRequest,
	TariffResponse,
	UserTariffRequest,
} from "../interfaces/tariff-request.interface";

@Injectable({
	providedIn: "root",
})
export class TariffService {
	private readonly http = inject(HttpClient);
	private readonly catalogUrl = "/api/v1/tariffs";
	private readonly myTariffUrl = "/api/v1/users/me/tariff";

	// ── Catálogo maestro (solo ROLE_ADMIN escribe; USER solo lee) ────────────

	getCatalog(): Observable<TariffResponse[]> {
		return this.http.get<TariffResponse[]>(this.catalogUrl);
	}

	getById(id: number): Observable<TariffResponse> {
		return this.http.get<TariffResponse>(`${this.catalogUrl}/${id}`);
	}

	createCatalogTariff(payload: TariffRequest): Observable<TariffResponse> {
		return this.http.post<TariffResponse>(this.catalogUrl, payload);
	}

	// El backend actual expone POST /{id} para actualizar (no PUT)
	updateCatalogTariff(
		id: number,
		payload: TariffRequest,
	): Observable<TariffResponse> {
		return this.http.post<TariffResponse>(`${this.catalogUrl}/${id}`, payload);
	}

	deleteCatalogTariff(id: number): Observable<void> {
		return this.http.delete<void>(`${this.catalogUrl}/${id}`);
	}

	// ── Tarifa privada del usuario autenticado ────────────────────────────────

	// El backend devuelve 200 + TariffDto si el usuario tiene tarifa,
	// o 204 sin cuerpo si no la tiene. Mapeamos 204/body-nulo a null.
	getMyTariff(): Observable<TariffResponse | null> {
		return this.http
			.get<TariffResponse | null>(this.myTariffUrl, { observe: "response" })
			.pipe(
				map((res) =>
					res.status === 204 || res.body === null ? null : res.body,
				),
				catchError(() => {
					// 401/403 se gestionan en el interceptor global; aquí apagamos el flujo
					return EMPTY;
				}),
			);
	}

	saveMyTariff(payload: UserTariffRequest): Observable<TariffResponse> {
		return this.http.post<TariffResponse>(this.myTariffUrl, payload);
	}

	unlinkMyTariff(): Observable<void> {
		return this.http.delete<void>(this.myTariffUrl);
	}
}

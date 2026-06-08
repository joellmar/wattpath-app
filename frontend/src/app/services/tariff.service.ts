import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import type { Observable } from "rxjs";
import type { TariffRequest } from "../interfaces/tariff-request.interface";

@Injectable({
	providedIn: "root",
})
export class TariffService {
	private readonly http = inject(HttpClient);
	private readonly baseUrl = "/api/v1/tariffs";

	createTariff(tariff: TariffRequest): Observable<void> {
		return this.http.post<void>(this.baseUrl, tariff);
	}
}

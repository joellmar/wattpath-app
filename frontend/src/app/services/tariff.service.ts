import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { TariffRequest } from '../interfaces/tariff-request.interface';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class TariffService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = "/api/v1/tariffs";

  createTariff(tariff: TariffRequest): Observable<void> {
    return this.http.post<void>(this.baseUrl, tariff);
  }
}

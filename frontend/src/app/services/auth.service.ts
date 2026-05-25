import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { LoginUser } from '../interfaces/login-user.interface';
import { LoginUserJwt } from '../interfaces/login-user-jwt.interface';
import { Observable } from 'rxjs';
import { RegisterRequest } from '../interfaces/register-request.interface';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = "/api/v1/auth";

  authentication(user: LoginUser): Observable<LoginUserJwt> {
    return this.http.post<LoginUserJwt>(`${this.baseUrl}/login`, user);
  }

  register(user: RegisterRequest): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/register`, user);
  }
}

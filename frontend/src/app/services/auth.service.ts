import { HttpClient } from "@angular/common/http";
import { Injectable, inject } from "@angular/core";
import type { Observable } from "rxjs";
import type { LoginUser } from "../interfaces/login-user.interface";
import type { LoginUserJwt } from "../interfaces/login-user-jwt.interface";
import type { RegisterRequest } from "../interfaces/register-request.interface";

@Injectable({
	providedIn: "root",
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

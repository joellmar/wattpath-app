import { DOCUMENT, Injectable, inject } from "@angular/core";
import { jwtDecode } from "jwt-decode";
import type { JwtPayload } from "../interfaces/jwt-payload.interface";

const TOKEN_KEY = "auth_token";

@Injectable({
	providedIn: "root",
})
export class SessionStorageService {
	private document = inject(DOCUMENT);
	private window = this.document.defaultView;

	public saveToken(token: string): void {
		if (this.window) {
			this.window.sessionStorage.removeItem(TOKEN_KEY);
			this.window.sessionStorage.setItem(TOKEN_KEY, token);
		}
	}

	public getToken(): string | null {
		return this.window ? this.window.sessionStorage.getItem(TOKEN_KEY) : null;
	}

	// Borramos solo la clave de sesión, respetando el resto del storage del usuario
	public logout(): void {
		if (this.window) {
			this.window.sessionStorage.removeItem(TOKEN_KEY);
		}
	}

	public isLoggedIn(): boolean {
		const token = this.getToken();
		if (!token) {
			return false;
		}

		try {
			const decoded = jwtDecode<JwtPayload>(token);

			if (!decoded.exp) {
				return true;
			}

			const currentTimeInSeconds = Math.floor(Date.now() / 1000);
			return decoded.exp > currentTimeInSeconds;
		} catch {
			return false;
		}
	}

	// El backend serializa los roles como string CSV: "ROLE_USER,ROLE_ADMIN"
	public getAuthorities(): string[] {
		const token = this.getToken();
		if (!token) return [];

		try {
			const decoded = jwtDecode<JwtPayload>(token);
			if (!decoded.authorities) return [];
			return decoded.authorities.split(",").map((r) => r.trim());
		} catch {
			return [];
		}
	}

	public hasRole(role: "ROLE_ADMIN" | "ROLE_USER"): boolean {
		return this.getAuthorities().includes(role);
	}

	public getUsername(): string | null {
		const token = this.getToken();
		if (!token) return null;

		try {
			const decoded = jwtDecode<JwtPayload>(token);
			return decoded.username ?? null;
		} catch {
			return null;
		}
	}
}

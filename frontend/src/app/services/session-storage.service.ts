import { DOCUMENT, Injectable, inject } from "@angular/core";
import { type JwtPayload, jwtDecode } from "jwt-decode";

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

	// Mejora de seguridad: Borramos solo lo correspondiente a la sesión, no todo el storage
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
}

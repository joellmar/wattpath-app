import { Component, inject, type OnInit, signal } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { Button } from "primeng/button";
import { Message } from "primeng/message";
import { AuthService } from "../../services/auth.service";
import { SessionStorageService } from "../../services/session-storage.service";

@Component({
	selector: "app-oauth-callback",
	standalone: true,
	imports: [Button, Message],
	template: `
    <div class="min-h-screen flex items-center justify-center bg-surface-950 p-4 font-sans text-surface-50">
      <div class="max-w-md w-full bg-surface-900 rounded-2xl p-8 shadow-2xl border border-surface-800 text-center">
        @if (isLoading()) {
          <div class="flex flex-col items-center gap-4">
            <i class="pi pi-spin pi-spinner text-4xl text-primary"></i>
            <p class="text-surface-300 text-sm">Verificando tu acceso, por favor espera...</p>
          </div>
        }
        @if (callbackError()) {
          <div class="flex flex-col items-center gap-4">
            <i class="pi pi-times-circle text-4xl text-red-400"></i>
            <p-message severity="error" fluid>{{ callbackError() }}</p-message>
            <p-button
              label="Volver al inicio de sesión"
              icon="pi pi-arrow-left"
              severity="secondary"
              (click)="goToLogin()">
            </p-button>
          </div>
        }
      </div>
    </div>
  `,
})
export default class OAuthCallbackComponent implements OnInit {
	private readonly route = inject(ActivatedRoute);
	private readonly router = inject(Router);
	private readonly authService = inject(AuthService);
	private readonly sessionStorageService = inject(SessionStorageService);

	readonly isLoading = signal<boolean>(true);
	readonly callbackError = signal<string | null>(null);

	ngOnInit(): void {
		const ticket = this.route.snapshot.queryParamMap.get("ticket");

		if (!ticket) {
			this.isLoading.set(false);
			this.callbackError.set(
				"No se ha recibido un código de acceso válido. Inicia sesión de nuevo.",
			);
			return;
		}

		this.authService.exchangeOAuthTicket(ticket).subscribe({
			next: (response) => {
				this.sessionStorageService.saveToken(response.jwt);
				this.router.navigate(["/dashboard"]);
			},
			error: () => {
				this.isLoading.set(false);
				this.callbackError.set(
					"El código de acceso ha caducado o ya fue utilizado. Inicia sesión de nuevo.",
				);
			},
		});
	}

	goToLogin(): void {
		this.router.navigate(["/login"]);
	}
}

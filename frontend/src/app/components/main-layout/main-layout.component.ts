import { Component, inject } from "@angular/core";
import {
	Router,
	RouterLink,
	RouterLinkActive,
	RouterOutlet,
} from "@angular/router";
import { Button } from "primeng/button";
import { SessionStorageService } from "../../services/session-storage.service";
import { TariffStore } from "../../store/tariff.store";
import { TelemetryStore } from "../../store/telemetry.store";

@Component({
	selector: "app-main-layout",
	templateUrl: "./main-layout.html",
	imports: [RouterOutlet, RouterLink, RouterLinkActive, Button],
})
export default class MainLayoutComponent {
	private readonly router = inject(Router);
	private readonly sessionStorageService = inject(SessionStorageService);
	private readonly telemetryStore = inject(TelemetryStore);
	private readonly tariffStore = inject(TariffStore);

	// Se lee del JWT en el momento de montar el layout; es estático durante
	// la sesión porque el token no cambia sin un nuevo login.
	protected readonly username =
		this.sessionStorageService.getUsername() ?? "Usuario";

	logout(): void {
		// 1. Desconecta el WebSocket activo antes de purgar el contexto;
		//    switchMap emite flujo vacío al recibir null, cerrando la suscripción.
		this.telemetryStore.connectTelemetry(null);
		// 2. Purga los stores para que el próximo usuario no vea datos cacheados.
		this.telemetryStore.reset();
		this.tariffStore.reset();
		// 3. Elimina el JWT del sessionStorage.
		this.sessionStorageService.logout();
		// 4. Redirige sustituyendo la entrada del historial para que el botón
		//    "atrás" del navegador no devuelva a una ruta privada vacía.
		this.router.navigateByUrl("/login", { replaceUrl: true });
	}
}

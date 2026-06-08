import { provideHttpClient, withInterceptors } from "@angular/common/http";
import {
	type ApplicationConfig,
	provideBrowserGlobalErrorListeners,
} from "@angular/core";
import { provideAnimationsAsync } from "@angular/platform-browser/animations/async";
import { provideRouter } from "@angular/router";
import Aura from "@primeuix/themes/aura";
import { providePrimeNG } from "primeng/config";
import { routes } from "./app.routes";
import { httpInterceptor } from "./interceptors/http.interceptor";

export const appConfig: ApplicationConfig = {
	providers: [
		provideBrowserGlobalErrorListeners(),
		provideRouter(routes),
		provideHttpClient(withInterceptors([httpInterceptor])),
		provideAnimationsAsync(),
		providePrimeNG({
			theme: {
				preset: Aura,
				options: {
					darkModeSelector: ".dark", // Para controlar el modo oscuro
				},
			},
		}),
	],
};

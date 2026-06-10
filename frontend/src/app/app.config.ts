import { provideHttpClient, withInterceptors } from "@angular/common/http";
import {
	type ApplicationConfig,
	provideBrowserGlobalErrorListeners,
} from "@angular/core";
import { provideAnimationsAsync } from "@angular/platform-browser/animations/async";
import { provideRouter } from "@angular/router";
import { definePreset } from "@primeuix/themes";
import Aura from "@primeuix/themes/aura";
import { providePrimeNG } from "primeng/config";
import { routes } from "./app.routes";
import { httpInterceptor } from "./interceptors/http.interceptor";

/*
 * Aura dark mode usa color-mix() con opacidad muy baja en p-message y p-toast,
 * lo que los hace casi invisibles sobre fondos oscuros. Se sobreescriben solo esos
 * tokens a colores sólidos para mantener contraste adecuado sin CSS global.
 */
const WattimizerPreset = definePreset(Aura, {
	components: {
		message: {
			colorScheme: {
				dark: {
					success: {
						background: "{green.700}",
						color: "{white}",
						borderColor: "{green.600}",
					},
					info: {
						background: "{blue.700}",
						color: "{white}",
						borderColor: "{blue.600}",
					},
					warn: {
						background: "{yellow.700}",
						color: "{white}",
						borderColor: "{yellow.600}",
					},
					error: {
						background: "{red.800}",
						color: "{white}",
						borderColor: "{red.700}",
					},
				},
			},
		},
		toast: {
			colorScheme: {
				dark: {
					success: { background: "{green.700}", color: "{white}" },
					info: { background: "{blue.700}", color: "{white}" },
					warn: { background: "{yellow.700}", color: "{white}" },
					error: { background: "{red.800}", color: "{white}" },
				},
			},
		},
	},
});

export const appConfig: ApplicationConfig = {
	providers: [
		provideBrowserGlobalErrorListeners(),
		provideRouter(routes),
		provideHttpClient(withInterceptors([httpInterceptor])),
		provideAnimationsAsync(),
		providePrimeNG({
			theme: {
				preset: WattimizerPreset,
				options: {
					darkModeSelector: ".dark",
				},
			},
		}),
	],
};

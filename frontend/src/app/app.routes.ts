import type { Routes } from "@angular/router";
import { authGuard } from "./guards/auth.guard";

export const routes: Routes = [
	{
		path: "login",
		loadComponent: () => import("./components/login/login.component"),
	},
	{
		path: "register",
		loadComponent: () => import("./components/register/register.component"),
	},
	{
		path: "auth/oauth/callback",
		loadComponent: () =>
			import("./components/oauth-callback/oauth-callback.component"),
	},
	{
		path: "",
		loadComponent: () =>
			import("./components/main-layout/main-layout.component"),
		// canActivate valida la entrada inicial al chasis; canActivateChild
		// re-valida la sesión en cada navegación interna entre rutas hijas,
		// protegiendo accesos directos por URL con token ya expirado.
		canActivate: [authGuard],
		canActivateChild: [authGuard],
		children: [
			{
				path: "dashboard",
				loadComponent: () =>
					import("./components/dashboard/dashboard.component"),
			},
			{
				path: "devices",
				loadComponent: () => import("./components/devices/devices.component"),
			},
			{
				path: "tariffs",
				loadComponent: () => import("./components/tariff/tariff.component"),
			},
			{
				path: "alerts",
				loadComponent: () => import("./components/alerts/alerts.component"),
			},
			{
				path: "",
				redirectTo: "dashboard",
				pathMatch: "full",
			},
		],
	},
	{
		path: "**",
		redirectTo: "dashboard",
	},
];

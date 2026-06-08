import { inject } from "@angular/core";
import { type CanActivateFn, Router } from "@angular/router";
import { SessionStorageService } from "../services/session-storage.service";

export const authGuard: CanActivateFn = () => {
	const router = inject(Router);
	const sessionStorageService = inject(SessionStorageService);

	const isLogged = sessionStorageService.isLoggedIn();
	return isLogged ? true : router.createUrlTree(["/login"]);
};

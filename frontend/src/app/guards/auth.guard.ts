import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionStorageService } from '../services/session-storage.service';

export const authGuard: CanActivateFn = (route, state) => {
  const router = inject(Router);
  const sessionStorageService = inject(SessionStorageService);

  const isLogged = sessionStorageService.isLoggedIn();
  return isLogged ? true : router.createUrlTree(["/login"]);
};

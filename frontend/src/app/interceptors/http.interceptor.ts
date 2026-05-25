import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionStorageService } from '../services/session-storage.service';

export const httpInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const sessionStorageService = inject(SessionStorageService);
  const token = sessionStorageService.getToken();

  let authReq = req.clone({
    headers: req.headers.set("X-Requested-With", "XMLHttpRequest")
  });

  // CRÍTICO: Enviamos el token a todo lo que vaya a /api/v1/ EXCEPTO a las rutas públicas de auth
  const isAuthRoute = req.url.includes("/api/v1/auth/login") || req.url.includes("/api/v1/auth/register");

  if (token && req.url.includes('/api/v1') && !isAuthRoute) {
    authReq = authReq.clone({
      headers: authReq.headers.set('Authorization', `Bearer ${token}`)
    });
  }

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        sessionStorageService.logout();
        router.navigate(["/login"]);
      }

      return throwError(() => err);
    })
  );
};

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { SessionStorageService } from '../services/session-storage.service';

export const httpInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const sessionStorageService = inject(SessionStorageService);

  // Añadimos siempre la cabecera común para peticiones AJAX
  let authReq = req.clone({
    headers: req.headers.set("X-Requested-With", "XMLHttpRequest")
  });

  // CRÍTICO: Enviamos el token a todo lo que vaya a /api/v1/ EXCEPTO a las rutas públicas de auth
  const isAuthRoute = req.url.includes("/api/v1/auth/login") || req.url.includes("/api/v1/auth/register");

  // SOLUCIÓN: Leemos el token en tiempo de evaluación de ruta, no al inicio de la función
  if (req.url.includes('/api/v1') && !isAuthRoute) {
    const currentToken = sessionStorageService.getToken();

    if (currentToken) {
      authReq = authReq.clone({
        headers: authReq.headers.set('Authorization', `Bearer ${currentToken}`)
      });
    }
  }

  return next(authReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        // Si el servidor nos devuelve un 401, limpiamos el rastro y forzamos el re-logueo
        sessionStorageService.logout();
        router.navigate(["/login"]);
      }

      return throwError(() => err);
    })
  );
};

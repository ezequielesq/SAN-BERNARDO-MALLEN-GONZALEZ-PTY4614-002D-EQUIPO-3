import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { NotificacionService } from '../notificaciones/notificacion.service';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const notificaciones = inject(NotificacionService);
  const router = inject(Router);

  const esApi = req.url.startsWith(environment.apiUrl);
  const esLogin = req.url === `${environment.apiUrl}/auth/login`;
  const esPublico = req.url.startsWith(`${environment.apiUrl}/publico/`);
  const token = auth.sesion()?.token;
  const peticion =
    esApi && !esLogin && !esPublico && token
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(peticion).pipe(
    catchError((err: unknown) => {
      if (esApi && !esLogin && !esPublico && err instanceof HttpErrorResponse) {
        if (err.status === 401) {
          auth.logout({ expirada: true, returnUrl: router.url });
        } else if (err.status === 403) {
          notificaciones.error('No tienes permiso para esta acción.');
        }
      }
      return throwError(() => err);
    }),
  );
};

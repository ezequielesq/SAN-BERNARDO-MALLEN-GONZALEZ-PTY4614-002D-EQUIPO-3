import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { paginaInicialPara } from '../layout/navegacion';
import { AuthService } from './auth.service';
import { Rol } from './sesion';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.estaAutenticado()
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const invitadoGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.estaAutenticado() ? router.createUrlTree([paginaInicialPara(auth.rol())]) : true;
};

export function rolGuard(roles: readonly Rol[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    const rol = auth.rol();
    return rol !== null && roles.includes(rol) ? true : router.createUrlTree([paginaInicialPara(rol)]);
  };
}

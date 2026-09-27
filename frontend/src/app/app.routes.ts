import { Routes } from '@angular/router';
import { authGuard, invitadoGuard, rolGuard } from './core/auth/auth.guards';
import { Shell } from './core/layout/shell';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Iniciar sesión · Smart RDP',
    canActivate: [invitadoGuard],
    loadComponent: () => import('./features/login/login').then(m => m.Login),
  },
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        path: 'productos',
        loadChildren: () => import('./features/productos/productos.routes').then(m => m.PRODUCTOS_ROUTES),
      },
      {
        path: 'administracion',
        canActivate: [rolGuard(['ADMIN'])],
        loadChildren: () => import('./features/administracion/administracion.routes').then(m => m.ADMINISTRACION_ROUTES),
      },
      { path: '', pathMatch: 'full', redirectTo: 'productos' },
    ],
  },
  { path: '**', redirectTo: '' },
];

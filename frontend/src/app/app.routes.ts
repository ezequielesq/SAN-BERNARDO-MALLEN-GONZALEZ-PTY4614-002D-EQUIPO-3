import { Routes } from '@angular/router';
import { authGuard, invitadoGuard } from './core/auth/auth.guards';
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
      // Task 7 agrega aquí la ruta 'administracion'.
      { path: '', pathMatch: 'full', redirectTo: 'productos' },
    ],
  },
  { path: '**', redirectTo: '' },
];

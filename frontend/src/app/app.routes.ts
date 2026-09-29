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
    canActivateChild: [authGuard],
    children: [
      {
        path: 'productos',
        loadChildren: () => import('./features/productos/productos.routes').then(m => m.PRODUCTOS_ROUTES),
      },
      {
        path: 'movimientos',
        loadChildren: () => import('./features/movimientos/movimientos.routes').then(m => m.MOVIMIENTOS_ROUTES),
      },
      {
        path: 'solicitudes',
        loadChildren: () => import('./features/solicitudes/solicitudes.routes').then(m => m.SOLICITUDES_ROUTES),
      },
      {
        path: 'analitica',
        canActivate: [rolGuard(['ADMIN', 'BODEGUERO'])],
        loadChildren: () => import('./features/analitica/analitica.routes').then(m => m.ANALITICA_ROUTES),
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

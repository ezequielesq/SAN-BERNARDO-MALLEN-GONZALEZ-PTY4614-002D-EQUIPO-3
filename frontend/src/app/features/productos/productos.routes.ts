import { Routes } from '@angular/router';
import { rolGuard } from '../../core/auth/auth.guards';
import { cambiosPendientesGuard } from '../../shared/confirmar-dialogo/cambios-pendientes.guard';

export const PRODUCTOS_ROUTES: Routes = [
  {
    path: '',
    title: 'Productos · Smart RDP',
    loadComponent: () => import('./producto-lista').then(m => m.ProductoLista),
  },
  {
    path: 'nuevo',
    title: 'Nuevo producto · Smart RDP',
    canActivate: [rolGuard(['ADMIN', 'BODEGUERO'])],
    canDeactivate: [cambiosPendientesGuard],
    loadComponent: () => import('./producto-formulario').then(m => m.ProductoFormulario),
  },
  {
    path: ':id',
    title: 'Editar producto · Smart RDP',
    canActivate: [rolGuard(['ADMIN', 'BODEGUERO'])],
    canDeactivate: [cambiosPendientesGuard],
    loadComponent: () => import('./producto-formulario').then(m => m.ProductoFormulario),
  },
];

import { Routes } from '@angular/router';

export const PRODUCTOS_ROUTES: Routes = [
  {
    path: '',
    title: 'Productos · Smart RDP',
    loadComponent: () => import('./producto-lista').then(m => m.ProductoLista),
  },
];

import { Routes } from '@angular/router';
import { rolGuard } from '../../core/auth/auth.guards';

export const MOVIMIENTOS_ROUTES: Routes = [
  {
    path: '',
    title: 'Movimientos · Smart RDP',
    loadComponent: () => import('./movimiento-lista').then((m) => m.MovimientoLista),
  },
  {
    path: 'entradas/nueva',
    title: 'Registrar entrada · Smart RDP',
    canActivate: [rolGuard(['ADMIN', 'BODEGUERO'])],
    loadComponent: () =>
      import('./movimiento-formulario-entrada').then((m) => m.MovimientoFormularioEntrada),
  },
  {
    path: 'salidas/nueva',
    title: 'Registrar salida · Smart RDP',
    canActivate: [rolGuard(['ADMIN', 'BODEGUERO'])],
    loadComponent: () =>
      import('./movimiento-formulario-salida').then((m) => m.MovimientoFormularioSalida),
  },
  {
    path: 'stock',
    title: 'Estado de stock · Smart RDP',
    loadComponent: () => import('./stock-lista').then((m) => m.StockLista),
  },
  {
    path: 'alertas',
    title: 'Alertas de vencimiento · Smart RDP',
    loadComponent: () => import('./alertas-lista').then((m) => m.AlertasLista),
  },
];

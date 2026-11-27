import { Routes } from '@angular/router';

export const MOVIMIENTOS_ROUTES: Routes = [
  {
    path: '',
    title: 'Movimientos · Smart RDP',
    loadComponent: () => import('./movimiento-lista').then((m) => m.MovimientoLista),
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
  {
    path: 'lotes',
    title: 'Lotes · Smart RDP',
    loadComponent: () => import('./lotes/lotes-lista').then((m) => m.LotesLista),
  },
  {
    path: 'lotes/:id',
    title: 'Detalle de lote · Smart RDP',
    loadComponent: () => import('./lotes/lote-detalle').then((m) => m.LoteDetalle),
  },
];

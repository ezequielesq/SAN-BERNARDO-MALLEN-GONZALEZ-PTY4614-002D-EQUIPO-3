import { Routes } from '@angular/router';

export const MOVIMIENTOS_ROUTES: Routes = [
  {
    path: '',
    title: 'Movimientos · Smart RDP',
    loadComponent: () => import('./movimiento-lista').then((m) => m.MovimientoLista),
  },
  {
    path: 'importar-entradas',
    title: 'Importar entradas · Smart RDP',
    loadComponent: () => import('./entrada-importar').then((m) => m.EntradaImportar),
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

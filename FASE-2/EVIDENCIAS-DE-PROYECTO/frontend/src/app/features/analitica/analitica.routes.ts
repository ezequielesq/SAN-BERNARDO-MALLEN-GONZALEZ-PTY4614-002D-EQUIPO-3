import { Routes } from '@angular/router';

export const ANALITICA_ROUTES: Routes = [
  {
    path: '',
    title: 'Analítica · Smart RDP',
    loadComponent: () => import('./analitica-lista').then((m) => m.AnaliticaLista),
  },
];

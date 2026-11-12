import { Routes } from '@angular/router';

export const CALENDARIO_ROUTES: Routes = [
  {
    path: '',
    title: 'Calendario · Smart RDP',
    loadComponent: () => import('./calendario-pagina').then((m) => m.CalendarioPagina),
  },
];

import { Routes } from '@angular/router';

export const SOLICITUDES_ROUTES: Routes = [
  {
    path: '',
    title: 'Solicitudes · Smart RDP',
    loadComponent: () => import('./solicitud-lista').then((m) => m.SolicitudLista),
  },
];

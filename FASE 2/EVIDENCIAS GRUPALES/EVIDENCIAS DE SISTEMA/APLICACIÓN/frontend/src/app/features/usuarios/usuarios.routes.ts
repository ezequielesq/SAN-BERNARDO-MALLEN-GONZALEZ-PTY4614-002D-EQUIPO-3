import { Routes } from '@angular/router';

export const USUARIOS_ROUTES: Routes = [
  {
    path: '',
    title: 'Usuarios · Smart RDP',
    loadComponent: () => import('./usuario-lista').then((m) => m.UsuarioLista),
  },
];

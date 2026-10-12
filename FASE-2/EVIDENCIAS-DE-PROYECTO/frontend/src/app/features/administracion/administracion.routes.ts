import { Routes } from '@angular/router';
import { ENDPOINT_CATEGORIAS, ENDPOINT_UNIDADES } from '../../shared/catalogo/catalogo.service';
import { ConfigCatalogo } from '../../shared/catalogo/catalogo-mantenedor';

const categorias: ConfigCatalogo = {
  titulo: 'Categorías',
  singular: 'categoría',
  endpoint: ENDPOINT_CATEGORIAS,
  conRequierePtv: true,
};
const unidades: ConfigCatalogo = { titulo: 'Unidades de medida', singular: 'unidad de medida', endpoint: ENDPOINT_UNIDADES };

export const ADMINISTRACION_ROUTES: Routes = [
  {
    path: 'categorias',
    title: 'Categorías · Smart RDP',
    data: { ...categorias },
    loadComponent: () => import('../../shared/catalogo/catalogo-mantenedor').then(m => m.CatalogoMantenedor),
  },
  {
    path: 'unidades-medida',
    title: 'Unidades de medida · Smart RDP',
    data: { ...unidades },
    loadComponent: () => import('../../shared/catalogo/catalogo-mantenedor').then(m => m.CatalogoMantenedor),
  },
  { path: '', pathMatch: 'full', redirectTo: 'categorias' },
];

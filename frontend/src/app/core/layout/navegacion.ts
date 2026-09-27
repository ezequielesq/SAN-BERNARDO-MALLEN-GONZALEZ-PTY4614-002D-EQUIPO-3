import { Rol } from '../auth/sesion';

export interface ItemNavegacion {
  etiqueta: string;
  ruta: string;
  roles: readonly Rol[];
  grupo?: string;
}

export interface GrupoNavegacion {
  clave: string;
  titulo: string | null;
  items: ItemNavegacion[];
}

export const NAVEGACION: readonly ItemNavegacion[] = [
  { etiqueta: 'Productos', ruta: '/productos', roles: ['ADMIN', 'BODEGUERO', 'TRABAJADOR'] },
  { etiqueta: 'Categorías', ruta: '/administracion/categorias', roles: ['ADMIN'], grupo: 'Administración' },
  { etiqueta: 'Unidades de medida', ruta: '/administracion/unidades-medida', roles: ['ADMIN'], grupo: 'Administración' },
];

export const ETIQUETA_ROL: Record<Rol, string> = {
  ADMIN: 'Administrador',
  BODEGUERO: 'Bodeguero',
  TRABAJADOR: 'Trabajador',
};

export function paginaInicialPara(rol: Rol | null): string {
  return rol === null ? '/login' : '/productos';
}

export function agruparNavegacion(rol: Rol | null): GrupoNavegacion[] {
  if (rol === null) return [];
  const grupos: GrupoNavegacion[] = [];
  for (const item of NAVEGACION.filter(i => i.roles.includes(rol))) {
    const titulo = item.grupo ?? null;
    const clave = titulo ?? 'principal';
    let grupo = grupos.find(g => g.clave === clave);
    if (!grupo) {
      grupo = { clave, titulo, items: [] };
      grupos.push(grupo);
    }
    grupo.items.push(item);
  }
  return grupos;
}

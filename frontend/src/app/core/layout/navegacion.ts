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
  { etiqueta: 'Productos', ruta: '/productos', roles: ['BODEGUERO'] },
  { etiqueta: 'Movimientos', ruta: '/movimientos', roles: ['BODEGUERO'] },
  { etiqueta: 'Estado de stock', ruta: '/movimientos/stock', roles: ['BODEGUERO'] },
  { etiqueta: 'Alertas de vencimiento', ruta: '/movimientos/alertas', roles: ['BODEGUERO'] },
  { etiqueta: 'Solicitudes', ruta: '/solicitudes', roles: ['BODEGUERO'] },
  { etiqueta: 'Analítica', ruta: '/analitica', roles: ['ADMIN', 'BODEGUERO'] },
  { etiqueta: 'Usuarios', ruta: '/usuarios', roles: ['ADMIN'] },
  { etiqueta: 'Categorías', ruta: '/administracion/categorias', roles: ['BODEGUERO'], grupo: 'Administración' },
  { etiqueta: 'Unidades de medida', ruta: '/administracion/unidades-medida', roles: ['BODEGUERO'], grupo: 'Administración' },
];

export const ETIQUETA_ROL: Record<Rol, string> = {
  ADMIN: 'Administrador',
  BODEGUERO: 'Bodeguero',
  EMPLEADO: 'Empleado',
};

export function paginaInicialPara(rol: Rol | null): string {
  if (rol === null) return '/login';
  if (rol === 'ADMIN') return '/analitica';
  if (rol === 'EMPLEADO') return '/empleado';
  return '/productos';
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

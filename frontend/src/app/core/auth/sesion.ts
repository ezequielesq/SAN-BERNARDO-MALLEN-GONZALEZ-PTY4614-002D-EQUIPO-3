export type Rol = 'ADMIN' | 'BODEGUERO' | 'TRABAJADOR';

export interface Sesion {
  token: string;
  email: string;
  nombre: string;
  rol: Rol;
}

export interface Credenciales {
  email: string;
  password: string;
}

const ROLES: readonly Rol[] = ['ADMIN', 'BODEGUERO', 'TRABAJADOR'];

export function esSesion(valor: unknown): valor is Sesion {
  if (typeof valor !== 'object' || valor === null) return false;
  const v = valor as Record<string, unknown>;
  return typeof v['token'] === 'string'
    && typeof v['email'] === 'string'
    && typeof v['nombre'] === 'string'
    && typeof v['rol'] === 'string'
    && (ROLES as readonly string[]).includes(v['rol']);
}

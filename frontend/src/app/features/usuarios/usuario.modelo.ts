import { Rol } from '../../core/auth/sesion';

export type RolAdministrable = 'BODEGUERO' | 'EMPLEADO';

export interface Usuario {
  id: number;
  nombre: string;
  email: string;
  rol: Rol;
  activo: boolean;
}

export interface UsuarioRequest {
  nombre: string;
  email: string;
  rol: RolAdministrable;
  /** En alta es obligatoria; en edición `null` = no cambiar la contraseña. */
  password: string | null;
}

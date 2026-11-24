import { Rol } from './sesion';

/**
 * Solo el bodeguero registra, edita o importa en bodega y calendario. La administradora los consulta en solo
 * lectura. La API ya responde 403 a las escrituras de la administradora; ocultar los controles es comodidad.
 */
export function puedeEscribirEnBodega(rol: Rol | null): boolean {
  return rol === 'BODEGUERO';
}

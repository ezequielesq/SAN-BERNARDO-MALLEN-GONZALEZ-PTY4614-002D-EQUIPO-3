import { paginar } from '../../../shared/paginacion/paginar';

/** Tamaño por defecto de la tabla «Ver como tabla» de los gráficos; también es el umbral para paginarla. */
export const TAMANO_TABLA_INICIAL = 10;

/** Solo se pagina cuando hay más filas que el tamaño inicial; con menos, la tabla se muestra completa. */
export function hayPaginasTabla(totalFilas: number): boolean {
  return totalFilas > TAMANO_TABLA_INICIAL;
}

/**
 * Filas de la tabla: todas si no hay paginador (aunque el tamaño elegido antes sea menor, para no dejar filas
 * inalcanzables cuando los datos se reducen); si lo hay, el tramo de la página con el tamaño elegido.
 */
export function filasVisibles<T>(filas: readonly T[], pagina: number, tamano: number): T[] {
  return hayPaginasTabla(filas.length) ? paginar(filas, pagina, tamano) : [...filas];
}

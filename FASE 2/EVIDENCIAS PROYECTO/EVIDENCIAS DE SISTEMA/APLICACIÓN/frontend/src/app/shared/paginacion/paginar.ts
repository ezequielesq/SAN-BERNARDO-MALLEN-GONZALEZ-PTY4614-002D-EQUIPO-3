function tamanoValido(tamano: number): boolean {
  return Number.isInteger(tamano) && tamano >= 1;
}

/** Cantidad de páginas (mínimo 1). Con un tamaño inválido todo cabe en una sola página. */
export function totalPaginas(totalItems: number, tamano: number): number {
  if (!tamanoValido(tamano)) return 1;
  return Math.max(1, Math.ceil(totalItems / tamano));
}

/** Lleva la página pedida al rango válido [1, última]; sirve cuando los datos se reducen y la página actual queda fuera. */
export function paginaAjustada(pagina: number, totalItems: number, tamano: number): number {
  return Math.min(Math.max(1, pagina), totalPaginas(totalItems, tamano));
}

/** Tramo de la lista que corresponde a la página (se ajusta si queda fuera de rango). No modifica la lista. */
export function paginar<T>(items: readonly T[], pagina: number, tamano: number): T[] {
  if (!tamanoValido(tamano)) return [...items];
  const inicio = (paginaAjustada(pagina, items.length, tamano) - 1) * tamano;
  return items.slice(inicio, inicio + tamano);
}

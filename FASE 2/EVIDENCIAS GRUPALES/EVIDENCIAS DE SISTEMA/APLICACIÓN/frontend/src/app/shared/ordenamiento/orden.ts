import { signal } from '@angular/core';

export type DireccionOrden = 'asc' | 'desc';
export type ValorOrden = string | number | null | undefined;
export type Extractores<T, C extends string> = Record<C, (item: T) => ValorOrden>;

/** Lo que necesitan los componentes de encabezado y selector; no depende del tipo de columnas. */
export interface OrdenVista {
  readonly direccion: () => DireccionOrden;
  alternar(columna: string): void;
  fijarColumna(columna: string): void;
  invertir(): void;
  icono(columna: string): string;
  ariaSort(columna: string): 'ascending' | 'descending' | null;
  esColumna(columna: string): boolean;
}

const comparador = new Intl.Collator('es', { numeric: true });

function comparar(a: ValorOrden, b: ValorOrden): number {
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return comparador.compare(String(a), String(b));
}

/** Estado de ordenamiento de una lista: columna activa y dirección. */
export class Orden<C extends string> implements OrdenVista {
  readonly columna = signal<C | null>(null);
  readonly direccion = signal<DireccionOrden>('asc');

  constructor(
    inicial: { columna: C; direccion?: DireccionOrden } | null = null,
    private readonly alCambiar?: () => void,
  ) {
    if (inicial) {
      this.columna.set(inicial.columna);
      this.direccion.set(inicial.direccion ?? 'asc');
    }
  }

  alternar(columna: C): void {
    if (this.columna() === columna) {
      this.direccion.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.columna.set(columna);
      this.direccion.set('asc');
    }
    this.alCambiar?.();
  }

  /** Cambia la columna conservando la dirección actual (para los selectores de las tarjetas). */
  fijarColumna(columna: C): void {
    this.columna.set(columna);
    this.alCambiar?.();
  }

  invertir(): void {
    this.direccion.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    this.alCambiar?.();
  }

  esColumna(columna: string): boolean {
    return this.columna() === columna;
  }

  icono(columna: C): string {
    if (this.columna() !== columna) return 'fa-sort';
    return this.direccion() === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  ariaSort(columna: C): 'ascending' | 'descending' | null {
    if (this.columna() !== columna) return null;
    return this.direccion() === 'asc' ? 'ascending' : 'descending';
  }
}

/**
 * Devuelve una copia ordenada de la lista. Los valores vacíos (null o undefined) van siempre al final,
 * sin importar la dirección. Léela dentro de un computed para que reaccione al cambio de orden.
 */
export function ordenar<T, C extends string>(
  lista: readonly T[],
  orden: Orden<C>,
  extractores: Extractores<T, C>,
): T[] {
  const columna = orden.columna();
  const copia = [...lista];
  if (columna === null) return copia;
  const extraer = extractores[columna];
  const sentido = orden.direccion() === 'asc' ? 1 : -1;
  return copia.sort((a, b) => {
    const va = extraer(a);
    const vb = extraer(b);
    const aVacio = va === null || va === undefined;
    const bVacio = vb === null || vb === undefined;
    if (aVacio || bVacio) return aVacio === bVacio ? 0 : aVacio ? 1 : -1;
    return comparar(va, vb) * sentido;
  });
}

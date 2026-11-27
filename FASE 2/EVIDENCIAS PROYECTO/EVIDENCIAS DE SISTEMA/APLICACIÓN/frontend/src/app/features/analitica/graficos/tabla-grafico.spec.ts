import { TAMANO_TABLA_INICIAL, filasVisibles, hayPaginasTabla } from './tabla-grafico';

const filas = (n: number) => Array.from({ length: n }, (_, i) => i + 1);

describe('hayPaginasTabla', () => {
  it('no hay paginador con el tamaño inicial de filas o menos', () => {
    expect(hayPaginasTabla(0)).toBe(false);
    expect(hayPaginasTabla(TAMANO_TABLA_INICIAL)).toBe(false);
  });

  it('hay paginador con más filas que el tamaño inicial', () => {
    expect(hayPaginasTabla(TAMANO_TABLA_INICIAL + 1)).toBe(true);
  });
});

describe('filasVisibles', () => {
  it('con el tamaño inicial de filas o menos muestra todas, aunque el tamaño elegido sea menor', () => {
    expect(filasVisibles(filas(10), 1, 5)).toEqual(filas(10));
    expect(filasVisibles(filas(3), 2, 5)).toEqual(filas(3));
    expect(filasVisibles([], 1, 5)).toEqual([]);
  });

  it('con más filas aplica el tamaño elegido', () => {
    expect(filasVisibles(filas(12), 1, 5)).toEqual([1, 2, 3, 4, 5]);
    expect(filasVisibles(filas(12), 3, 5)).toEqual([11, 12]);
  });

  it('con más filas y un tamaño mayor muestra todas', () => {
    expect(filasVisibles(filas(12), 1, 50)).toEqual(filas(12));
  });
});

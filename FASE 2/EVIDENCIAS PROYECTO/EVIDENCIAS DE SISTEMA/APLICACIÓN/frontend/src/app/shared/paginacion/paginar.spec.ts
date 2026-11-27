import { paginaAjustada, paginar, totalPaginas } from './paginar';

const diez = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

describe('paginar', () => {
  it('la primera página trae los primeros elementos', () => {
    expect(paginar(diez, 1, 4)).toEqual([1, 2, 3, 4]);
  });

  it('una página intermedia trae su tramo', () => {
    expect(paginar(diez, 2, 4)).toEqual([5, 6, 7, 8]);
  });

  it('la última página puede quedar incompleta', () => {
    expect(paginar(diez, 3, 4)).toEqual([9, 10]);
  });

  it('una página fuera de rango se ajusta a la última', () => {
    expect(paginar(diez, 9, 4)).toEqual([9, 10]);
  });

  it('una página menor que uno se ajusta a la primera', () => {
    expect(paginar(diez, 0, 4)).toEqual([1, 2, 3, 4]);
    expect(paginar(diez, -3, 4)).toEqual([1, 2, 3, 4]);
  });

  it('una lista vacía da una lista vacía', () => {
    expect(paginar([], 1, 4)).toEqual([]);
    expect(paginar([], 5, 4)).toEqual([]);
  });

  it('un tamaño inválido deja todos los elementos en una sola página', () => {
    expect(paginar(diez, 1, 0)).toEqual(diez);
    expect(paginar(diez, 2, -5)).toEqual(diez);
    expect(paginar(diez, 1, Number.NaN)).toEqual(diez);
    expect(paginar(diez, 1, 2.5)).toEqual(diez);
  });

  it('no modifica la lista original', () => {
    const original = [...diez];
    paginar(original, 2, 3);
    expect(original).toEqual(diez);
  });
});

describe('totalPaginas', () => {
  it('redondea hacia arriba', () => {
    expect(totalPaginas(10, 4)).toBe(3);
    expect(totalPaginas(12, 4)).toBe(3);
  });

  it('nunca baja de una página', () => {
    expect(totalPaginas(0, 4)).toBe(1);
  });

  it('con tamaño inválido hay una sola página', () => {
    expect(totalPaginas(10, 0)).toBe(1);
  });
});

describe('paginaAjustada', () => {
  it('conserva una página válida', () => {
    expect(paginaAjustada(2, 10, 4)).toBe(2);
  });

  it('una página fuera de rango pasa a la última', () => {
    expect(paginaAjustada(7, 10, 4)).toBe(3);
  });

  it('una página menor que uno pasa a la primera', () => {
    expect(paginaAjustada(0, 10, 4)).toBe(1);
  });

  it('sin elementos la página es 1', () => {
    expect(paginaAjustada(4, 0, 4)).toBe(1);
  });

  it('con tamaño inválido la página es 1', () => {
    expect(paginaAjustada(3, 10, 0)).toBe(1);
  });
});

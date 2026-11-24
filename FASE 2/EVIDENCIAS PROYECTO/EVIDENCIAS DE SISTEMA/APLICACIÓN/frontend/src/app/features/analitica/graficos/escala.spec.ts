import { marcas, maximoRedondo } from './escala';

describe('escala de ejes', () => {
  it('redondea el máximo hacia arriba a 1, 2, 5 o 10 por una potencia de diez', () => {
    expect(maximoRedondo(0)).toBe(1);
    expect(maximoRedondo(1200)).toBe(2000);
    expect(maximoRedondo(4000)).toBe(5000);
    expect(maximoRedondo(7300)).toBe(10000);
    expect(maximoRedondo(10000)).toBe(10000);
  });

  it('reparte las marcas del eje desde 0 hasta el máximo', () => {
    expect(marcas(10000, 4)).toEqual([0, 2500, 5000, 7500, 10000]);
  });
});

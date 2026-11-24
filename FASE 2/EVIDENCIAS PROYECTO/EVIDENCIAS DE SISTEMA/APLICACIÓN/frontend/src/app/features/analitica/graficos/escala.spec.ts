import { marcas, marcasDistintas, marcasEnteras, margenIzquierdo, maximoBarras, maximoRedondo } from './escala';

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

describe('máximo de barras', () => {
  it('con valores menores que 1 usa el mayor, sin subirlo a 1', () => {
    expect(maximoBarras([0.2, 0.5, 0.35])).toBe(0.5);
  });

  it('con valores enteros usa el mayor', () => {
    expect(maximoBarras([3, 12, 7])).toBe(12);
  });

  it('sin valores positivos devuelve 1 para no dividir por cero', () => {
    expect(maximoBarras([])).toBe(1);
    expect(maximoBarras([0, 0])).toBe(1);
    expect(maximoBarras([-4])).toBe(1);
  });

  it('el máximo redondo también sirve con decimales', () => {
    expect(maximoRedondo(0.35)).toBe(0.5);
    expect(maximoRedondo(0.2)).toBe(0.2);
  });
});

describe('marcas enteras del eje', () => {
  it('reparte las marcas y las redondea al entero', () => {
    expect(marcasEnteras(1000, 2000, 4)).toEqual([1000, 1250, 1500, 1750, 2000]);
  });

  it('con un rango diminuto no repite marcas', () => {
    const resultado = marcasEnteras(99.6, 101.4, 4);
    expect(new Set(resultado).size).toBe(resultado.length);
    expect(resultado).toEqual([100, 101]);
  });

  it('descarta las marcas que quedan fuera del rango', () => {
    expect(marcasEnteras(99.4, 99.6, 4)).toEqual([]);
  });
});

describe('margen izquierdo según las etiquetas', () => {
  it('mantiene el mínimo con etiquetas cortas', () => {
    expect(margenIzquierdo(['$0', '$500'])).toBe(76);
  });

  it('crece cuando hay etiquetas largas', () => {
    const ancho = margenIzquierdo(['$12.345.678']);
    expect(ancho).toBeGreaterThan(76);
    expect(margenIzquierdo(['$123.456.789'])).toBeGreaterThan(ancho);
  });

  it('sin etiquetas devuelve el mínimo', () => {
    expect(margenIzquierdo([])).toBe(76);
  });
});

describe('marcas distintas del eje', () => {
  const unDecimal = (n: number) => n.toFixed(1);
  const entero = (n: number) => String(Math.round(n));
  const etiquetas = (valores: number[], f: (n: number) => string) => valores.map(f);

  it('mantiene los cuatro tramos cuando las etiquetas no se repiten', () => {
    expect(marcasDistintas(10000, entero)).toEqual([0, 2500, 5000, 7500, 10000]);
  });

  it('con decimales cambia de tramos para que ninguna etiqueta se repita', () => {
    const valores = marcasDistintas(0.2, unDecimal);
    expect(valores).toEqual([0, 0.1, 0.2]);
    expect(new Set(etiquetas(valores, unDecimal)).size).toBe(valores.length);
  });

  it('con un máximo diminuto en pesos deja solo 0 y el máximo', () => {
    const valores = marcasDistintas(1, entero);
    expect(valores).toEqual([0, 1]);
  });

  it('nunca repite etiquetas, con cualquier máximo redondo', () => {
    for (const maximo of [0.01, 0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10, 250, 1000]) {
      for (const f of [unDecimal, entero]) {
        const textos = etiquetas(marcasDistintas(maximo, f), f);
        expect(new Set(textos).size).toBe(textos.length);
      }
    }
  });
});

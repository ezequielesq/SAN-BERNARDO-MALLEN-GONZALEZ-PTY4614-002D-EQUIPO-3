import { ResumenPanel } from './analitica.modelo';
import {
  MAXIMO_PRODUCTOS,
  alternarProducto,
  decidirMedida,
  diasDelRango,
  hayDatosEnResumen,
  limiteAlcanzado,
  rangoDeAtajo,
  sonIguales,
  sugerirAgrupacion,
  validarRango,
} from './panel-filtros';

describe('atajos de fecha', () => {
  const hoy = new Date(2026, 10, 24); // 24-11-2026

  it('sugiere día para 7 días, semana para 30, mes para 90 y día para el mes actual', () => {
    expect(sugerirAgrupacion('7d')).toBe('DIA');
    expect(sugerirAgrupacion('30d')).toBe('SEMANA');
    expect(sugerirAgrupacion('90d')).toBe('MES');
    expect(sugerirAgrupacion('mes')).toBe('DIA');
  });

  it('calcula el rango de cada atajo contando el día de hoy', () => {
    expect(rangoDeAtajo('7d', hoy)).toEqual({ desde: '2026-11-18', hasta: '2026-11-24' });
    expect(rangoDeAtajo('30d', hoy)).toEqual({ desde: '2026-10-26', hasta: '2026-11-24' });
    expect(rangoDeAtajo('90d', hoy)).toEqual({ desde: '2026-08-27', hasta: '2026-11-24' });
    expect(rangoDeAtajo('mes', hoy)).toEqual({ desde: '2026-11-01', hasta: '2026-11-24' });
  });
});

describe('validarRango', () => {
  it('acepta un rango válido, incluso de un solo día', () => {
    expect(validarRango('2026-11-01', '2026-11-24')).toBeNull();
    expect(validarRango('2026-11-24', '2026-11-24')).toBeNull();
  });

  it('pide las dos fechas', () => {
    expect(validarRango('', '2026-11-24')).toContain('fechas');
    expect(validarRango('2026-11-01', '')).toContain('fechas');
  });

  it('rechaza «desde» posterior a «hasta»', () => {
    expect(validarRango('2026-11-25', '2026-11-24')).toContain('posterior');
  });

  it('acepta hasta 366 días contando ambos extremos y rechaza más', () => {
    expect(diasDelRango('2026-01-01', '2027-01-01')).toBe(366);
    expect(validarRango('2026-01-01', '2027-01-01')).toBeNull();
    expect(validarRango('2026-01-01', '2027-01-02')).toContain('366');
  });
});

describe('decidirMedida', () => {
  it('respeta la preferencia cuando se puede', () => {
    expect(decidirMedida('valor', null)).toEqual({ medida: 'valor', aviso: null });
    expect(decidirMedida('cantidad', 'kg')).toEqual({ medida: 'cantidad', aviso: null });
  });

  it('con unidades mezcladas pasa a valor y lo avisa', () => {
    const r = decidirMedida('cantidad', null);
    expect(r.medida).toBe('valor');
    expect(r.aviso).toContain('unidades distintas');
  });
});

describe('hayDatosEnResumen', () => {
  const vacio: ResumenPanel = {
    consumoNetoValor: 0,
    cantidadNeta: null,
    unidadComun: null,
    pedidos: 0,
    personas: 0,
    solicitadoValor: 0,
    entregadoValor: 0,
    solicitadoCantidad: null,
    entregadoCantidad: null,
    perdidasValor: 0,
    lineasSinCosto: 0,
  };

  it('un resumen en ceros no tiene datos (no hay unidad que comparar)', () => {
    expect(hayDatosEnResumen(vacio)).toBe(false);
  });

  it('con pedidos, consumo o pérdidas sí hay datos', () => {
    expect(hayDatosEnResumen({ ...vacio, pedidos: 1 })).toBe(true);
    expect(hayDatosEnResumen({ ...vacio, consumoNetoValor: 500 })).toBe(true);
    expect(hayDatosEnResumen({ ...vacio, perdidasValor: 200 })).toBe(true);
    expect(hayDatosEnResumen({ ...vacio, lineasSinCosto: 2 })).toBe(true);
  });
});

describe('límite de productos elegidos', () => {
  const ids = (n: number) => Array.from({ length: n }, (_, i) => i + 1);

  it('el máximo coincide con el del servidor', () => {
    expect(MAXIMO_PRODUCTOS).toBe(50);
  });

  it('agrega y quita productos', () => {
    expect(alternarProducto([1, 2], 3, true)).toEqual([1, 2, 3]);
    expect(alternarProducto([1, 2, 3], 2, false)).toEqual([1, 3]);
  });

  it('no agrega dos veces el mismo producto', () => {
    expect(alternarProducto([1, 2], 2, true)).toEqual([1, 2]);
  });

  it('no deja pasar el producto 51', () => {
    expect(alternarProducto(ids(49), 100, true)).toHaveLength(50);
    expect(alternarProducto(ids(50), 100, true)).toEqual(ids(50));
  });

  it('con 50 elegidos todavía se puede quitar uno', () => {
    expect(alternarProducto(ids(50), 7, false)).toHaveLength(49);
  });

  it('informa si se alcanzó el límite', () => {
    expect(limiteAlcanzado(ids(49))).toBe(false);
    expect(limiteAlcanzado(ids(50))).toBe(true);
  });
});

describe('sonIguales (filtros de vencimientos)', () => {
  it('compara los productos por contenido, no por referencia', () => {
    expect(sonIguales({ dias: 30, categoriaId: null, productoIds: [1, 2] }, { dias: 30, categoriaId: null, productoIds: [1, 2] })).toBe(true);
  });

  it('distingue categoría, productos y demás campos', () => {
    const base = { dias: 30, categoriaId: 1, productoIds: [1, 2] };
    expect(sonIguales(base, { ...base, categoriaId: 2 })).toBe(false);
    expect(sonIguales(base, { ...base, productoIds: [1] })).toBe(false);
    expect(sonIguales(base, { ...base, productoIds: [2, 1] })).toBe(false);
    expect(sonIguales(base, { ...base, dias: 7 })).toBe(false);
  });

  it('con fechas distintas no son iguales', () => {
    const base = { desde: '2026-11-01', hasta: '2026-11-30', categoriaId: null, productoIds: [] as number[] };
    expect(sonIguales(base, { ...base, hasta: '2026-11-29' })).toBe(false);
  });
});

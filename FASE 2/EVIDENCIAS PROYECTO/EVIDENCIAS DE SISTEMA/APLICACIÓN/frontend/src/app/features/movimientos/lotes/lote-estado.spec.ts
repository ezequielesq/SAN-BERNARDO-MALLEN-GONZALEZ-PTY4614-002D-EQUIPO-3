import { describe, expect, it } from 'vitest';
import { FiltroLotes } from '../lote.modelo';
import {
  ESTADOS_LOTE,
  UMBRAL_POR_DEFECTO,
  esVueltaALotes,
  estadoLoteValido,
  etiquetaDias,
  etiquetaEstado,
  filtroDesdeParams,
  idDeParametro,
  normalizarDias,
  paramsDesdeFiltro,
  paramsLotes,
  paramsVerLotes,
  rangoEstado,
  tipoBadge,
} from './lote-estado';

describe('lote-estado', () => {
  it('etiqueta los días para vencer', () => {
    expect(etiquetaDias(null)).toBe('Sin vencimiento');
    expect(etiquetaDias(0)).toBe('Vence hoy');
    expect(etiquetaDias(1)).toBe('1 día');
    expect(etiquetaDias(5)).toBe('5 días');
    expect(etiquetaDias(-1)).toBe('Venció hace 1 día');
    expect(etiquetaDias(-4)).toBe('Venció hace 4 días');
  });

  it('etiqueta el estado', () => {
    expect(etiquetaEstado('POR_VENCER')).toBe('Próximo a vencer');
    expect(etiquetaEstado('AGOTADO')).toBe('Agotado');
    expect(etiquetaEstado('VENCIDO')).toBe('Vencido');
    expect(etiquetaEstado('VIGENTE')).toBe('Vigente');
  });

  it('acepta solo enteros no negativos como umbral', () => {
    expect(normalizarDias('10')).toBe(10);
    expect(normalizarDias('0')).toBe(0);
    expect(normalizarDias('-1')).toBeNull();
    expect(normalizarDias('2.5')).toBeNull();
    expect(normalizarDias('')).toBeNull();
  });

  it('arma los parámetros de la consulta sin los filtros vacíos', () => {
    const base = { productoId: null, categoriaId: null, proveedorId: null, estado: null, texto: '  ',
      diasPorVencer: 7, incluirAgotados: false };
    expect(paramsLotes(base)).toEqual({ diasPorVencer: 7, incluirAgotados: false });
    expect(paramsLotes({ ...base, proveedorId: 3, texto: ' L-1 ', estado: 'VENCIDO' })).toEqual({
      diasPorVencer: 7, incluirAgotados: false, proveedorId: 3, texto: 'L-1', estado: 'VENCIDO',
    });
  });

  it('traduce el estado del lote al semáforo', () => {
    expect(tipoBadge('POR_VENCER')).toBe('proximo');
    expect(tipoBadge('AGOTADO')).toBe('agotado');
    expect(tipoBadge('VENCIDO')).toBe('vencido');
    expect(tipoBadge('VIGENTE')).toBe('vigente');
  });

  it('ordena los estados por urgencia: vencido, por vencer, vigente, agotado', () => {
    expect(rangoEstado('VENCIDO')).toBeLessThan(rangoEstado('POR_VENCER'));
    expect(rangoEstado('POR_VENCER')).toBeLessThan(rangoEstado('VIGENTE'));
    expect(rangoEstado('VIGENTE')).toBeLessThan(rangoEstado('AGOTADO'));
  });

  it('reconoce solo estados válidos', () => {
    expect(estadoLoteValido('VENCIDO')).toBe('VENCIDO');
    expect(estadoLoteValido('otro')).toBeNull();
    expect(estadoLoteValido(null)).toBeNull();
  });

  it('no toma por estado las propiedades heredadas de Object', () => {
    expect(estadoLoteValido('constructor')).toBeNull();
    expect(estadoLoteValido('toString')).toBeNull();
    expect(estadoLoteValido('__proto__')).toBeNull();
  });

  it('ofrece una sola lista de estados, la misma que conoce el orden por urgencia', () => {
    expect([...ESTADOS_LOTE].sort()).toEqual(['AGOTADO', 'POR_VENCER', 'VENCIDO', 'VIGENTE']);
    for (const estado of ESTADOS_LOTE) expect(estadoLoteValido(estado)).toBe(estado);
  });

  it('lee un id de la URL solo si es un entero positivo', () => {
    expect(idDeParametro('7')).toBe(7);
    for (const malo of [null, '', '0', '-2', '1.5', 'abc']) expect(idDeParametro(malo)).toBeNull();
  });
});

describe('filtros de lotes en la URL', () => {
  const vacio: FiltroLotes = {
    productoId: null, categoriaId: null, proveedorId: null, estado: null, texto: '',
    diasPorVencer: UMBRAL_POR_DEFECTO, incluirAgotados: false,
  };
  const desde = (consulta: string) => filtroDesdeParams(new URLSearchParams(consulta));

  it('sin parámetros devuelve los valores por defecto', () => {
    expect(desde('')).toEqual(vacio);
  });

  it('lee todos los filtros de la URL', () => {
    expect(desde('productoId=4&categoriaId=2&proveedorId=9&estado=VENCIDO&texto=L-1&diasPorVencer=30&incluirAgotados=true'))
      .toEqual({ productoId: 4, categoriaId: 2, proveedorId: 9, estado: 'VENCIDO', texto: 'L-1',
        diasPorVencer: 30, incluirAgotados: true });
  });

  it('descarta valores manipulados y cae al valor por defecto', () => {
    expect(desde('productoId=abc&categoriaId=-1&proveedorId=0&estado=constructor&diasPorVencer=-5&incluirAgotados=si'))
      .toEqual(vacio);
  });

  it('un parámetro ausente vuelve a su valor por defecto, sin arrastrar el filtro anterior', () => {
    const anterior = desde('proveedorId=9&estado=VENCIDO&incluirAgotados=true&diasPorVencer=30&texto=x');
    expect(anterior.proveedorId).toBe(9);
    expect(desde('productoId=4')).toEqual({ ...vacio, productoId: 4 });
  });

  it('arma la URL solo con lo que se apartó del valor por defecto', () => {
    expect(paramsDesdeFiltro(vacio)).toEqual({});
    expect(paramsDesdeFiltro({ ...vacio, texto: '   ' })).toEqual({});
    expect(paramsDesdeFiltro({ ...vacio, proveedorId: 9, estado: 'POR_VENCER', diasPorVencer: 0, incluirAgotados: true,
      texto: 'L-1', productoId: 4, categoriaId: 2 }))
      .toEqual({ productoId: 4, categoriaId: 2, proveedorId: 9, estado: 'POR_VENCER', texto: 'L-1',
        diasPorVencer: 0, incluirAgotados: true });
  });

  it('ida y vuelta: lo que se escribe en la URL se lee igual', () => {
    const filtro: FiltroLotes = { productoId: 4, categoriaId: 2, proveedorId: 9, estado: 'AGOTADO', texto: 'ab 1 ',
      diasPorVencer: 15, incluirAgotados: true };
    const consulta = new URLSearchParams(
      Object.entries(paramsDesdeFiltro(filtro)).map(([k, v]) => [k, String(v)]),
    );
    expect(filtroDesdeParams(consulta)).toEqual(filtro);
  });

  it('el enlace «Ver lotes» de un producto agotado incluye los lotes agotados', () => {
    expect(paramsVerLotes(5, 'AGOTADO')).toEqual({ productoId: 5, incluirAgotados: true });
    expect(paramsVerLotes(5, 'CRITICO')).toEqual({ productoId: 5 });
    expect(paramsVerLotes(5, 'NORMAL')).toEqual({ productoId: 5 });
  });

  it('«Volver a lotes» retrocede solo si la página anterior de la app era la lista de lotes', () => {
    expect(esVueltaALotes('/movimientos/lotes')).toBe(true);
    expect(esVueltaALotes('/movimientos/lotes?proveedorId=3&diasPorVencer=7')).toBe(true);
    expect(esVueltaALotes('/movimientos/lotes/12?diasPorVencer=7')).toBe(false);
    expect(esVueltaALotes('/movimientos/alertas')).toBe(false);
    expect(esVueltaALotes('/movimientos/stock?x=/movimientos/lotes')).toBe(false);
    expect(esVueltaALotes(null)).toBe(false);
  });
});

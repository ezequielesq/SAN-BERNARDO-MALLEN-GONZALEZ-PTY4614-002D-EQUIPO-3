import { describe, expect, it } from 'vitest';
import { estadoLoteValido, etiquetaDias, etiquetaEstado, normalizarDias, paramsLotes, rangoEstado, tipoBadge } from './lote-estado';

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
    expect(etiquetaEstado('POR_VENCER')).toBe('Por vencer');
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
});

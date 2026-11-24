import { formatearVariacion } from './formato';

describe('formatearVariacion', () => {
  it('sin dato muestra una raya', () => {
    expect(formatearVariacion(null)).toBe('—');
  });

  it('un alza lleva signo más', () => {
    expect(formatearVariacion(12.5)).toBe('+12,5 %');
  });

  it('una baja conserva su signo menos', () => {
    expect(formatearVariacion(-3)).toBe('-3 %');
  });

  it('sin cambio no lleva signo', () => {
    expect(formatearVariacion(0)).toBe('0 %');
  });
});

import { contar, formatearVariacion } from './formato';

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

describe('contar', () => {
  it('usa el singular solo con uno', () => {
    expect(contar(1, 'persona', 'personas')).toBe('1 persona');
  });

  it('usa el plural con cero y con más de uno', () => {
    expect(contar(0, 'persona', 'personas')).toBe('0 personas');
    expect(contar(2, 'pedido', 'pedidos')).toBe('2 pedidos');
  });
});

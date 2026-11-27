import { contar, descripcionTop, filaCompra, filaProveedor, formatearVariacion } from './formato';

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

describe('descripcionTop', () => {
  it('con un producto usa el singular', () => {
    expect(descripcionTop(1)).toBe('Gráfico de barras horizontales con el producto más usado del período.');
  });

  it('con varios usa el plural', () => {
    expect(descripcionTop(7)).toBe('Gráfico de barras horizontales con los 7 productos más usados del período.');
  });
});

describe('filaProveedor', () => {
  it('formatea compras, último costo y promedio ponderado en pesos', () => {
    const fila = filaProveedor({ proveedorId: 1, nombre: 'Distribuidora Sur', ultimo: 1200, promedioPonderado: 1150.5, compras: 3 });
    expect(fila.nombre).toBe('Distribuidora Sur');
    expect(fila.compras).toBe('3');
    expect(fila.ultimo).toMatch(/1\.200/);
    expect(fila.ponderado).toMatch(/1\.15[01]/);
  });

  it('sin dato muestra una raya', () => {
    const fila = filaProveedor({ proveedorId: 1, nombre: 'A', ultimo: null, promedioPonderado: null, compras: 0 });
    expect(fila.ultimo).toBe('—');
    expect(fila.ponderado).toBe('—');
  });
});

describe('filaCompra', () => {
  it('muestra fecha corta, proveedor, cantidad y costo', () => {
    const fila = filaCompra({ fecha: '2026-11-03', costo: 1000, cantidad: 12, proveedorNombre: 'Distribuidora Sur' });
    expect(fila.fecha).toBe('03-11');
    expect(fila.proveedor).toBe('Distribuidora Sur');
    expect(fila.cantidad).toBe('12');
    expect(fila.costo).toMatch(/1\.000/);
  });

  it('una compra sin proveedor dice «Sin proveedor»', () => {
    expect(filaCompra({ fecha: '2026-11-03', costo: 1000, cantidad: 1, proveedorNombre: null }).proveedor).toBe('Sin proveedor');
  });
});

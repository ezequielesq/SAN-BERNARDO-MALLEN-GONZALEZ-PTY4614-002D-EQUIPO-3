import { EstadoFiltros } from './panel-estado';

describe('EstadoFiltros', () => {
  const hoy = new Date(2026, 10, 24);

  it('empieza con los últimos 30 días agrupados por semana', () => {
    const estado = new EstadoFiltros(hoy);
    expect(estado.desde()).toBe('2026-10-26');
    expect(estado.hasta()).toBe('2026-11-24');
    expect(estado.agrupar()).toBe('SEMANA');
    expect(estado.medida()).toBe('valor');
  });

  it('entrega el filtro completo cuando el rango es válido', () => {
    const estado = new EstadoFiltros(hoy);
    estado.categoriaId.set(3);
    estado.productoIds.set([5, 8]);
    estado.solicitanteId.set(12);
    estado.proveedorId.set(4);
    expect(estado.errorRango()).toBeNull();
    expect(estado.filtro()).toEqual({
      desde: '2026-10-26',
      hasta: '2026-11-24',
      agrupar: 'SEMANA',
      categoriaId: 3,
      productoIds: [5, 8],
      solicitanteId: 12,
      proveedorId: 4,
    });
  });

  it('sin proveedor elegido el filtro lleva proveedorId nulo', () => {
    const estado = new EstadoFiltros(hoy);
    expect(estado.proveedorId()).toBeNull();
    expect(estado.filtro()?.proveedorId).toBeNull();
  });

  it('elegir un proveedor cambia el filtro y quitarlo lo devuelve a nulo', () => {
    const estado = new EstadoFiltros(hoy);
    estado.proveedorId.set(9);
    expect(estado.filtro()?.proveedorId).toBe(9);
    estado.proveedorId.set(null);
    expect(estado.filtro()?.proveedorId).toBeNull();
  });

  it('con fechas invertidas no hay filtro y se explica el error', () => {
    const estado = new EstadoFiltros(hoy);
    estado.desde.set('2026-11-25');
    expect(estado.filtro()).toBeNull();
    expect(estado.errorRango()).toContain('posterior');
  });

  it('un atajo cambia el rango y sugiere la agrupación, que el usuario puede volver a cambiar', () => {
    const estado = new EstadoFiltros(hoy);
    estado.aplicarAtajo('7d', hoy);
    expect(estado.desde()).toBe('2026-11-18');
    expect(estado.agrupar()).toBe('DIA');
    estado.agrupar.set('MES');
    expect(estado.filtro()?.agrupar).toBe('MES');
  });
});

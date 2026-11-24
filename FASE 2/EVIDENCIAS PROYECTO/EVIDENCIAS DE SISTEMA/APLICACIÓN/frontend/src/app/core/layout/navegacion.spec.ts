import { agruparNavegacion, paginaInicialPara } from './navegacion';

describe('navegación por rol', () => {
  const rutas = (rol: 'ADMIN' | 'BODEGUERO') =>
    agruparNavegacion(rol).flatMap((g) => g.items.map((i) => i.ruta));

  it('la administradora ve analítica, usuarios y la bodega en lectura, pero no solicitudes ni catálogos', () => {
    const admin = rutas('ADMIN');
    expect(admin).toEqual(
      expect.arrayContaining([
        '/analitica', '/usuarios', '/productos', '/movimientos', '/movimientos/stock',
        '/movimientos/alertas', '/calendario',
      ]),
    );
    expect(admin).not.toContain('/solicitudes');
    expect(admin).not.toContain('/administracion/categorias');
    expect(admin).not.toContain('/administracion/unidades-medida');
  });

  it('el bodeguero conserva todo salvo usuarios, y la página inicial de la administradora sigue siendo analítica', () => {
    const bodeguero = rutas('BODEGUERO');
    expect(bodeguero).toContain('/solicitudes');
    expect(bodeguero).not.toContain('/usuarios');
    expect(paginaInicialPara('ADMIN')).toBe('/analitica');
  });
});

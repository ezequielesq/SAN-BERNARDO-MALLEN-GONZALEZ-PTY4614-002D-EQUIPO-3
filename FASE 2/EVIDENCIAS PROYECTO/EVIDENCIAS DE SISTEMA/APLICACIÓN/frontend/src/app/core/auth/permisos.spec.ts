import { puedeEscribirEnBodega } from './permisos';

describe('puedeEscribirEnBodega', () => {
  it('solo el bodeguero escribe; la administradora y el empleado solo leen', () => {
    expect(puedeEscribirEnBodega('BODEGUERO')).toBe(true);
    expect(puedeEscribirEnBodega('ADMIN')).toBe(false);
    expect(puedeEscribirEnBodega('EMPLEADO')).toBe(false);
    expect(puedeEscribirEnBodega(null)).toBe(false);
  });
});

import { describe, expect, it } from 'vitest';
import { proveedorObligatorio } from './proveedor-entrada';

describe('proveedorObligatorio', () => {
  it('solo la compra lo exige', () => {
    expect(proveedorObligatorio('COMPRA')).toBe(true);
    expect(proveedorObligatorio('INVENTARIO_INICIAL')).toBe(false);
    expect(proveedorObligatorio('AJUSTE_ENTRADA')).toBe(false);
    expect(proveedorObligatorio('')).toBe(false);
  });
});

import { describe, expect, it } from 'vitest';
import { digitoVerificador, errorDeRut, formatearRut, normalizarRut } from './rut';

describe('rut', () => {
  it('normaliza con puntos, guion y espacios', () => {
    expect(normalizarRut('12.345.678-5')).toBe('12345678-5');
    expect(normalizarRut(' 12345678-5 ')).toBe('12345678-5');
    expect(normalizarRut('123456785')).toBe('12345678-5');
  });

  it('acepta los tres tipos de dígito verificador', () => {
    expect(normalizarRut('11111111-1')).toBe('11111111-1');
    expect(normalizarRut('1000005-k')).toBe('1000005-K');
    expect(normalizarRut('1000030-0')).toBe('1000030-0');
  });

  it('rechaza dígito incorrecto y formatos inválidos', () => {
    expect(normalizarRut('12345678-9')).toBeNull();
    for (const malo of ['', 'abc', '1-9', '123456789012-3', '12.345.678-X']) {
      expect(normalizarRut(malo)).toBeNull();
    }
  });

  it('ignora los ceros a la izquierda del cuerpo', () => {
    expect(normalizarRut('01234567-4')).toBe('1234567-4');
    expect(normalizarRut('0012345678-5')).toBe('12345678-5');
    expect(normalizarRut('001.234.567-4')).toBe('1234567-4');
  });

  it('rechaza el todo ceros y los cuerpos que quedan cortos tras quitar los ceros', () => {
    expect(normalizarRut('00000000-0')).toBeNull();
    expect(normalizarRut('00000001-9')).toBeNull();
    expect(normalizarRut('00123456-K')).toBeNull();
  });

  it('calcula el dígito verificador', () => {
    expect(digitoVerificador('12345678')).toBe('5');
    expect(digitoVerificador('1000005')).toBe('K');
    expect(digitoVerificador('1000030')).toBe('0');
  });

  it('formatea con puntos para mostrar', () => {
    expect(formatearRut('12345678-5')).toBe('12.345.678-5');
    expect(formatearRut('1000005-K')).toBe('1.000.005-K');
  });

  describe('errorDeRut', () => {
    it('no da error con un RUT válido', () => {
      expect(errorDeRut('12.345.678-5')).toBeNull();
      expect(errorDeRut('01234567-4')).toBeNull();
    });

    it('pide el RUT cuando está vacío', () => {
      expect(errorDeRut('')).toBe('Ingresa el RUT del proveedor.');
      expect(errorDeRut('   ')).toBe('Ingresa el RUT del proveedor.');
    });

    it('distingue el formato inválido del dígito verificador incorrecto', () => {
      const formato = 'El RUT no es válido: usa el formato 12.345.678-5.';
      for (const malo of ['abc', '1-9', '123456789012-3', '12.345.678-X', '00000000-0']) {
        expect(errorDeRut(malo)).toBe(formato);
      }
      expect(errorDeRut('12345678-9')).toBe('El RUT no es válido: revisa el dígito verificador.');
    });
  });
});

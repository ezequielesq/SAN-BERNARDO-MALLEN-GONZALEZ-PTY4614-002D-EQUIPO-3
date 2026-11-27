/** Dígito verificador (módulo 11) de un cuerpo numérico. */
export function digitoVerificador(cuerpo: string): string {
  let suma = 0;
  let peso = 2;
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += Number(cuerpo[i]) * peso;
    peso = peso === 7 ? 2 : peso + 1;
  }
  const resto = 11 - (suma % 11);
  return resto === 11 ? '0' : resto === 10 ? 'K' : String(resto);
}

const MENSAJE_FORMATO = 'El RUT no es válido: usa el formato 12.345.678-5.';
const MENSAJE_DIGITO = 'El RUT no es válido: revisa el dígito verificador.';

/** Quita puntos, guiones y espacios, y separa el cuerpo (sin ceros a la izquierda) del dígito; null si el formato no sirve. */
function partesDeRut(texto: string): { cuerpo: string; dv: string } | null {
  const limpio = texto.replace(/[.\s-]/g, '').toUpperCase();
  if (!/^\d+[\dK]$/.test(limpio)) return null;
  const cuerpo = limpio.slice(0, -1).replace(/^0+/, '');
  // Mismo criterio que el servidor: 7 u 8 dígitos sin contar los ceros a la izquierda; el todo ceros no es un RUT.
  if (!/^[1-9]\d{6,7}$/.test(cuerpo)) return null;
  return { cuerpo, dv: limpio.slice(-1) };
}

/** «12.345.678-5», «12345678-5» o «123456785» → «12345678-5»; null si no es un RUT válido. Los ceros a la izquierda se ignoran. */
export function normalizarRut(texto: string): string | null {
  const partes = partesDeRut(texto);
  return partes !== null && digitoVerificador(partes.cuerpo) === partes.dv ? `${partes.cuerpo}-${partes.dv}` : null;
}

/** Mensaje de error de un RUT escrito por el usuario, o null si es válido; distingue el formato del dígito verificador. */
export function errorDeRut(texto: string): string | null {
  if (texto.trim() === '') return 'Ingresa el RUT del proveedor.';
  const partes = partesDeRut(texto);
  if (partes === null) return MENSAJE_FORMATO;
  return digitoVerificador(partes.cuerpo) === partes.dv ? null : MENSAJE_DIGITO;
}

/** RUT normalizado → «12.345.678-5» para mostrarlo. */
export function formatearRut(rut: string): string {
  const [cuerpo, dv] = rut.split('-');
  if (!cuerpo || !dv) return rut;
  return `${cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}-${dv}`;
}

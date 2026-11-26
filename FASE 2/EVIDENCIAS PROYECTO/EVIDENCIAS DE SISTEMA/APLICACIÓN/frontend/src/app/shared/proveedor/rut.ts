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

/** «12.345.678-5», «12345678-5» o «123456785» → «12345678-5»; null si no es un RUT válido. */
export function normalizarRut(texto: string): string | null {
  const limpio = texto.replace(/[.\s-]/g, '').toUpperCase();
  if (!/^\d{7,8}[\dK]$/.test(limpio)) return null;
  const cuerpo = limpio.slice(0, -1);
  const dv = limpio.slice(-1);
  return digitoVerificador(cuerpo) === dv ? `${cuerpo}-${dv}` : null;
}

/** RUT normalizado → «12.345.678-5» para mostrarlo. */
export function formatearRut(rut: string): string {
  const [cuerpo, dv] = rut.split('-');
  if (!cuerpo || !dv) return rut;
  return `${cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}-${dv}`;
}

/** Máximo del eje: el primer 1, 2, 5 o 10 por una potencia de diez que no queda por debajo del valor. */
export function maximoRedondo(maximo: number): number {
  if (maximo <= 0) return 1;
  const potencia = Math.pow(10, Math.floor(Math.log10(maximo)));
  const fraccion = maximo / potencia;
  const bonito = fraccion <= 1 ? 1 : fraccion <= 2 ? 2 : fraccion <= 5 ? 5 : 10;
  return bonito * potencia;
}

/** Marcas del eje de 0 al máximo, en `cantidad` tramos iguales. */
export function marcas(maximo: number, cantidad: number): number[] {
  return Array.from({ length: cantidad + 1 }, (_, i) => (maximo * i) / cantidad);
}

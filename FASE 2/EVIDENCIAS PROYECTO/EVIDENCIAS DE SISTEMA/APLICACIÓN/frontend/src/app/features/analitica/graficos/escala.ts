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

/** Mayor valor positivo (para dibujar barras); 1 si no hay ninguno, para no dividir por cero. Sirve con decimales. */
export function maximoBarras(valores: readonly number[]): number {
  const maximo = Math.max(0, ...valores);
  return maximo > 0 ? maximo : 1;
}

/** Marcas del eje redondeadas al entero, sin repetir y dentro del rango (con rangos diminutos quedan pocas). */
export function marcasEnteras(bajo: number, alto: number, cantidad: number): number[] {
  const valores = Array.from({ length: cantidad + 1 }, (_, i) => Math.round(bajo + ((alto - bajo) * i) / cantidad));
  return [...new Set(valores)].filter((v) => v >= bajo && v <= alto);
}

const MARGEN_IZQUIERDO_MINIMO = 76;
const ANCHO_POR_CARACTER = 6.5; // letra de 11 px
const AIRE_EJE = 14;

/** Margen izquierdo del gráfico: el mínimo, o más si alguna etiqueta del eje no cabe. */
export function margenIzquierdo(etiquetas: readonly string[]): number {
  const largo = Math.max(0, ...etiquetas.map((e) => e.length));
  return Math.max(MARGEN_IZQUIERDO_MINIMO, Math.ceil(largo * ANCHO_POR_CARACTER) + AIRE_EJE);
}

/**
 * Marcas del eje de 0 al máximo cuyas etiquetas (ya formateadas) no se repiten: prueba 4, 5, 2 y 1 tramos y usa el
 * primero sin repetidas; si ninguno sirve (máximo menor que la precisión del formato) deja solo la primera de cada etiqueta.
 */
export function marcasDistintas(maximo: number, formatear: (n: number) => string): number[] {
  const sinRepetir = (valores: number[]) => new Set(valores.map(formatear)).size === valores.length;
  for (const tramos of [4, 5, 2, 1]) {
    const valores = marcas(maximo, tramos);
    if (sinRepetir(valores)) return valores;
  }
  const vistas = new Set<string>();
  return marcas(maximo, 1).filter((v) => {
    const texto = formatear(v);
    if (vistas.has(texto)) return false;
    vistas.add(texto);
    return true;
  });
}

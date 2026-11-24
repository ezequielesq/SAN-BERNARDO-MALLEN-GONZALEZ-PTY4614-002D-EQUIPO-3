import { Agrupacion, Medida, ResumenPanel } from './analitica.modelo';

export type Atajo = '7d' | '30d' | '90d' | 'mes';

/** El servidor rechaza rangos de más de este número de días (ambos extremos incluidos). */
export const MAXIMO_DIAS = 366;

/** El servidor rechaza filtros con más de este número de productos. */
export const MAXIMO_PRODUCTOS = 50;

const MS_POR_DIA = 24 * 60 * 60 * 1000;

/** Fecha local como AAAA-MM-DD. */
export function aTexto(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, '0');
  const dia = String(fecha.getDate()).padStart(2, '0');
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

/** Agrupación que mejor se lee para el tamaño del atajo; el usuario puede cambiarla después. */
export function sugerirAgrupacion(atajo: Atajo): Agrupacion {
  switch (atajo) {
    case '7d':
      return 'DIA';
    case '30d':
      return 'SEMANA';
    case '90d':
      return 'MES';
    case 'mes':
      return 'DIA';
  }
}

/** Rango de un atajo terminado hoy (hoy cuenta como un día del rango). */
export function rangoDeAtajo(atajo: Atajo, hoy: Date): { desde: string; hasta: string } {
  const fin = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
  const inicio = new Date(fin);
  switch (atajo) {
    case '7d':
      inicio.setDate(fin.getDate() - 6);
      break;
    case '30d':
      inicio.setDate(fin.getDate() - 29);
      break;
    case '90d':
      inicio.setDate(fin.getDate() - 89);
      break;
    case 'mes':
      inicio.setDate(1);
      break;
  }
  return { desde: aTexto(inicio), hasta: aTexto(fin) };
}

function aUtc(texto: string): number {
  const [anio, mes, dia] = texto.split('-').map(Number);
  return Date.UTC(anio, mes - 1, dia);
}

/** Días del rango contando ambos extremos. */
export function diasDelRango(desde: string, hasta: string): number {
  return Math.round((aUtc(hasta) - aUtc(desde)) / MS_POR_DIA) + 1;
}

/** Mensaje de error del rango, o null si es válido. Debe coincidir con las reglas del servidor. */
export function validarRango(desde: string, hasta: string): string | null {
  if (desde === '' || hasta === '') return 'Indica las dos fechas.';
  if (desde > hasta) return 'La fecha «desde» no puede ser posterior a «hasta».';
  if (diasDelRango(desde, hasta) > MAXIMO_DIAS) return `El rango no puede superar ${MAXIMO_DIAS} días.`;
  return null;
}

/** Cantidad solo si el servidor encontró una unidad común; si no, se muestra el valor y se avisa. */
export function decidirMedida(
  preferida: Medida,
  unidadComun: string | null,
): { medida: Medida; aviso: string | null } {
  if (preferida === 'cantidad' && unidadComun === null) {
    return {
      medida: 'valor',
      aviso: 'Los productos elegidos usan unidades distintas: se muestra el valor en pesos.',
    };
  }
  return { medida: preferida, aviso: null };
}

/** Hay algo que mostrar en el resumen; sin datos no hay unidades que comparar y no corresponde avisar. */
export function hayDatosEnResumen(r: ResumenPanel): boolean {
  return (
    r.pedidos > 0 ||
    r.consumoNetoValor !== 0 ||
    r.entregadoValor !== 0 ||
    r.solicitadoValor !== 0 ||
    r.perdidasValor !== 0 ||
    r.lineasSinCosto > 0
  );
}

export function limiteAlcanzado(productoIds: readonly number[]): boolean {
  return productoIds.length >= MAXIMO_PRODUCTOS;
}

/** Marca o desmarca un producto; nunca pasa de MAXIMO_PRODUCTOS ni repite uno ya elegido. */
export function alternarProducto(productoIds: readonly number[], id: number, marcado: boolean): number[] {
  if (!marcado) return productoIds.filter((x) => x !== id);
  if (productoIds.includes(id) || limiteAlcanzado(productoIds)) return [...productoIds];
  return [...productoIds, id];
}

type ValorFiltro = string | number | null | readonly number[];

/** Igualdad por contenido de filtros planos (los arreglos se comparan elemento a elemento); sirve de `equal` de un computed. */
export function sonIguales<T extends Record<string, ValorFiltro>>(a: T, b: T): boolean {
  const claves = Object.keys(a);
  if (claves.length !== Object.keys(b).length) return false;
  return claves.every((clave) => {
    const x = a[clave];
    const y = b[clave];
    if (Array.isArray(x) && Array.isArray(y)) return x.length === y.length && x.every((v, i) => v === y[i]);
    return x === y;
  });
}

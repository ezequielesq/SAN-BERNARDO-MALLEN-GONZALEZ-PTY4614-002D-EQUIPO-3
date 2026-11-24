import { Agrupacion, Medida } from './analitica.modelo';

export type Atajo = '7d' | '30d' | '90d' | 'mes';

/** El servidor rechaza rangos de más de este número de días (ambos extremos incluidos). */
export const MAXIMO_DIAS = 366;

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

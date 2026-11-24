import { Agrupacion } from './analitica.modelo';

const PESOS = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 });
const NUMERO = new Intl.NumberFormat('es-CL', { maximumFractionDigits: 1 });
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export function formatearPesos(valor: number): string {
  return PESOS.format(valor);
}

export function formatearCantidad(cantidad: number, unidad: string | null): string {
  return unidad === null ? NUMERO.format(cantidad) : `${NUMERO.format(cantidad)} ${unidad}`;
}

/** «02-03» para AAAA-03-02. */
export function formatearFechaCorta(fecha: string): string {
  const [, mes, dia] = fecha.split('-');
  return `${dia}-${mes}`;
}

/** Etiqueta de un período: día «02-03», semana «sem 02-03», mes «mar 2026». */
export function formatearPeriodo(inicio: string, agrupar: Agrupacion): string {
  if (agrupar === 'MES') {
    const [anio, mes] = inicio.split('-');
    return `${MESES[Number(mes) - 1]} ${anio}`;
  }
  return agrupar === 'SEMANA' ? `sem ${formatearFechaCorta(inicio)}` : formatearFechaCorta(inicio);
}

/** Variación porcentual con signo explícito en las alzas: «+12,5 %»; sin dato, «—». */
export function formatearVariacion(porcentaje: number | null): string {
  if (porcentaje === null) return '—';
  return `${porcentaje > 0 ? '+' : ''}${NUMERO.format(porcentaje)} %`;
}

/** «1 persona», «2 personas»: singular solo con uno. */
export function contar(n: number, singular: string, plural: string): string {
  return `${n} ${n === 1 ? singular : plural}`;
}

/** Utilidades del calendario mensual (lunes a domingo) compartidas por las vistas con calendario. */
export interface DiaCalendario {
  tipo: 'vacio' | 'dia';
  fecha?: string;
  dia?: number;
}

export const DIAS_ES = ['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sa', 'Do'] as const;
export const MESES_ES = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
] as const;

export function formatearFecha(anio: number, mesIndex: number, dia: number): string {
  return `${anio}-${String(mesIndex + 1).padStart(2, '0')}-${String(dia).padStart(2, '0')}`;
}

export function construirDiasDelMes(anio: number, mesIndex: number): DiaCalendario[] {
  const primerDia = new Date(anio, mesIndex, 1);
  const ultimoDia = new Date(anio, mesIndex + 1, 0);
  const dias: DiaCalendario[] = [];

  let inicioSemana = primerDia.getDay();
  inicioSemana = inicioSemana === 0 ? 6 : inicioSemana - 1;
  for (let i = 0; i < inicioSemana; i++) dias.push({ tipo: 'vacio' });

  for (let d = 1; d <= ultimoDia.getDate(); d++) {
    dias.push({ tipo: 'dia', fecha: formatearFecha(anio, mesIndex, d), dia: d });
  }

  const resto = dias.length % 7;
  if (resto > 0) for (let i = resto; i < 7; i++) dias.push({ tipo: 'vacio' });

  return dias;
}

/** Rango yyyy-MM-dd del primer al último día del mes indicado (mesIndex 0-11). */
export function rangoDelMes(anio: number, mesIndex: number): { desde: string; hasta: string } {
  const ultimoDia = new Date(anio, mesIndex + 1, 0).getDate();
  return { desde: formatearFecha(anio, mesIndex, 1), hasta: formatearFecha(anio, mesIndex, ultimoDia) };
}

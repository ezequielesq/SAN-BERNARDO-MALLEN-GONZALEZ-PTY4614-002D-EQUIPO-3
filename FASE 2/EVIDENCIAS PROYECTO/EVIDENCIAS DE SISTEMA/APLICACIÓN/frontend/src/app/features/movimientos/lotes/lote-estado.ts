import { TipoEstado } from '../../../shared/estado-badge/estado-badge';
import { EstadoLote, FiltroLotes } from '../lote.modelo';

export function etiquetaDias(dias: number | null): string {
  if (dias === null) return 'Sin vencimiento';
  if (dias === 0) return 'Vence hoy';
  if (dias === 1) return '1 día';
  if (dias > 1) return `${dias} días`;
  return dias === -1 ? 'Venció hace 1 día' : `Venció hace ${-dias} días`;
}

export function etiquetaEstado(estado: EstadoLote): string {
  switch (estado) {
    case 'AGOTADO': return 'Agotado';
    case 'VENCIDO': return 'Vencido';
    case 'POR_VENCER': return 'Por vencer';
    case 'VIGENTE': return 'Vigente';
  }
}

/** Entero ≥ 0 o null si el texto no es un umbral válido. */
export function normalizarDias(texto: string): number | null {
  if (!/^\d+$/.test(texto.trim())) return null;
  return Number(texto.trim());
}

/** Parámetros de `GET /api/lotes`; los filtros vacíos no se envían. */
export function paramsLotes(f: FiltroLotes): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {
    diasPorVencer: f.diasPorVencer,
    incluirAgotados: f.incluirAgotados,
  };
  if (f.productoId !== null) params['productoId'] = f.productoId;
  if (f.categoriaId !== null) params['categoriaId'] = f.categoriaId;
  if (f.proveedorId !== null) params['proveedorId'] = f.proveedorId;
  if (f.estado !== null) params['estado'] = f.estado;
  if (f.texto.trim() !== '') params['texto'] = f.texto.trim();
  return params;
}

const BADGE_POR_ESTADO: Record<EstadoLote, TipoEstado> = {
  AGOTADO: 'agotado',
  VENCIDO: 'vencido',
  POR_VENCER: 'proximo',
  VIGENTE: 'vigente',
};

/** Variante del semáforo (`EstadoBadge`) que corresponde al estado del lote. */
export function tipoBadge(estado: EstadoLote): TipoEstado {
  return BADGE_POR_ESTADO[estado];
}

const RANGO_ESTADO: Record<EstadoLote, number> = { VENCIDO: 0, POR_VENCER: 1, VIGENTE: 2, AGOTADO: 3 };

/** Posición del estado al ordenar por urgencia (lo más urgente primero). */
export function rangoEstado(estado: EstadoLote): number {
  return RANGO_ESTADO[estado];
}

/** Devuelve el estado si es uno de los conocidos; null en otro caso (p. ej. un parámetro de URL manipulado). */
export function estadoLoteValido(valor: string | null): EstadoLote | null {
  return valor !== null && valor in RANGO_ESTADO ? (valor as EstadoLote) : null;
}

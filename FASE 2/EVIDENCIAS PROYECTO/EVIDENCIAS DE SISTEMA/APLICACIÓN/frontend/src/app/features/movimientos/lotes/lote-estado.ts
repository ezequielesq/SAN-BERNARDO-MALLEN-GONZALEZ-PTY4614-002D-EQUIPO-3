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
    case 'POR_VENCER': return 'Próximo a vencer';
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

/** Los estados conocidos, en el orden en que se ofrecen en el filtro. */
export const ESTADOS_LOTE = ['VIGENTE', 'POR_VENCER', 'VENCIDO', 'AGOTADO'] as const satisfies readonly EstadoLote[];

/** Posición del estado al ordenar por urgencia (lo más urgente primero). */
export function rangoEstado(estado: EstadoLote): number {
  return RANGO_ESTADO[estado];
}

/** Devuelve el estado si es uno de los conocidos; null en otro caso (p. ej. un parámetro de URL manipulado). */
export function estadoLoteValido(valor: string | null): EstadoLote | null {
  return valor !== null && Object.hasOwn(RANGO_ESTADO, valor) ? (valor as EstadoLote) : null;
}

/** Umbral de «por vencer» cuando la URL no trae uno válido. */
export const UMBRAL_POR_DEFECTO = 7;

/** Id numérico de un parámetro de URL; null si falta o no es un entero positivo. */
export function idDeParametro(valor: string | null): number | null {
  const n = valor !== null && /^\d+$/.test(valor) ? Number(valor) : NaN;
  return Number.isInteger(n) && n > 0 ? n : null;
}

/** Lo que de `ParamMap` y `URLSearchParams` necesita el parseo. */
interface ParametrosLeibles {
  get(nombre: string): string | null;
}

/**
 * Filtros de la lista leídos de la URL, que es su fuente: lo ausente o inválido vuelve a su valor por defecto,
 * para que no queden filtros viejos al cambiar la consulta. No confundir con `paramsLotes`, que arma la consulta a la API.
 */
export function filtroDesdeParams(params: ParametrosLeibles): FiltroLotes {
  return {
    productoId: idDeParametro(params.get('productoId')),
    categoriaId: idDeParametro(params.get('categoriaId')),
    proveedorId: idDeParametro(params.get('proveedorId')),
    estado: estadoLoteValido(params.get('estado')),
    texto: params.get('texto') ?? '',
    diasPorVencer: normalizarDias(params.get('diasPorVencer') ?? '') ?? UMBRAL_POR_DEFECTO,
    incluirAgotados: params.get('incluirAgotados') === 'true',
  };
}

/** Parámetros de la URL de la lista: solo lo que se aparta del valor por defecto (inverso de `filtroDesdeParams`). */
export function paramsDesdeFiltro(f: FiltroLotes): Record<string, string | number | boolean> {
  const params: Record<string, string | number | boolean> = {};
  if (f.productoId !== null) params['productoId'] = f.productoId;
  if (f.categoriaId !== null) params['categoriaId'] = f.categoriaId;
  if (f.proveedorId !== null) params['proveedorId'] = f.proveedorId;
  if (f.estado !== null) params['estado'] = f.estado;
  // Sin recortar: así lo que se escribe (y sus espacios) no cambia al volver desde la URL.
  if (f.texto.trim() !== '') params['texto'] = f.texto;
  if (f.diasPorVencer !== UMBRAL_POR_DEFECTO) params['diasPorVencer'] = f.diasPorVencer;
  if (f.incluirAgotados) params['incluirAgotados'] = true;
  return params;
}

/** Parámetros del enlace «Ver lotes» de un producto del stock: un producto agotado solo tiene lotes agotados. */
export function paramsVerLotes(productoId: number, estadoStock: string): Record<string, number | boolean> {
  return estadoStock === 'AGOTADO' ? { productoId, incluirAgotados: true } : { productoId };
}

/** La página anterior de la app era la lista de lotes: «Volver a lotes» puede retroceder y conservar sus filtros. */
export function esVueltaALotes(urlPrevia: string | null): boolean {
  return urlPrevia !== null && urlPrevia.split(/[?#]/)[0] === '/movimientos/lotes';
}

/** Columnas por las que se puede ordenar la lista de lotes. */
export const COLUMNAS_ORDEN_LOTE = ['productoNombre', 'fechaVencimiento', 'saldo', 'estado'] as const;
export type ColumnaOrdenLote = (typeof COLUMNAS_ORDEN_LOTE)[number];

/** Tamaños de página que ofrece la lista. */
export const TAMANOS_PAGINA_LOTE = [10, 15, 20, 50] as const;

/** Cómo se ve la lista: orden, página y tamaño de página (los filtros van aparte, en `FiltroLotes`). */
export interface VistaLotes {
  /** Sin columna se conserva el orden del servidor (primero lo que vence antes). */
  columna: ColumnaOrdenLote | null;
  direccion: 'asc' | 'desc';
  pagina: number;
  tamano: number;
}

export const VISTA_POR_DEFECTO: VistaLotes = { columna: null, direccion: 'asc', pagina: 1, tamano: 15 };

/** Orden, página y tamaño leídos de la URL; lo ausente o inválido vuelve a su valor por defecto. */
export function vistaDesdeParams(params: ParametrosLeibles): VistaLotes {
  const orden = params.get('orden');
  const columna =
    orden !== null && (COLUMNAS_ORDEN_LOTE as readonly string[]).includes(orden) ? (orden as ColumnaOrdenLote) : null;
  const pagina = params.get('pagina');
  const tamano = params.get('tamano');
  const tamanoLeido = tamano !== null && /^\d+$/.test(tamano) ? Number(tamano) : NaN;
  return {
    columna,
    direccion: columna !== null && params.get('dir') === 'desc' ? 'desc' : 'asc',
    pagina: pagina !== null && /^\d+$/.test(pagina) && Number(pagina) >= 1 ? Number(pagina) : 1,
    tamano: (TAMANOS_PAGINA_LOTE as readonly number[]).includes(tamanoLeido) ? tamanoLeido : VISTA_POR_DEFECTO.tamano,
  };
}

/** Parámetros de la URL para la vista: solo lo que se aparta del valor por defecto (inverso de `vistaDesdeParams`). */
export function paramsDesdeVista(v: VistaLotes): Record<string, string | number> {
  const params: Record<string, string | number> = {};
  if (v.columna !== null) {
    params['orden'] = v.columna;
    if (v.direccion === 'desc') params['dir'] = 'desc';
  }
  if (v.pagina > 1) params['pagina'] = v.pagina;
  if (v.tamano !== VISTA_POR_DEFECTO.tamano) params['tamano'] = v.tamano;
  return params;
}

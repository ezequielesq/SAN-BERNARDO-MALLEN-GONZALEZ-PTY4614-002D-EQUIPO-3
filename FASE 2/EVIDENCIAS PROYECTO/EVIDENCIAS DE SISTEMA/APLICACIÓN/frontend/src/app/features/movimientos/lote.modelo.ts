export type EstadoLote = 'AGOTADO' | 'VENCIDO' | 'POR_VENCER' | 'VIGENTE';

export interface Lote {
  id: number;
  productoId: number;
  productoNombre: string;
  categoriaNombre: string;
  numeroLote: string | null;
  proveedorId: number | null;
  proveedorNombre: string | null;
  fechaIngreso: string;
  fechaVencimiento: string | null;
  diasParaVencer: number | null;
  costoUnitario: number | null;
  cantidadInicial: number;
  saldo: number;
  valorEnStock: number | null;
  estado: EstadoLote;
}

export interface MovimientoLote {
  movimientoId: number;
  operacionId: number;
  fecha: string;
  tipo: string;
  tipoNombre: string;
  sentido: number;
  cantidad: number;
  saldoDespues: number;
  usuarioEmail: string | null;
  nota: string | null;
  solicitudId: number | null;
  solicitanteNombre: string | null;
}

export interface LoteDetalle {
  lote: Lote;
  trazabilidad: MovimientoLote[];
}

export interface FiltroLotes {
  productoId: number | null;
  categoriaId: number | null;
  proveedorId: number | null;
  estado: EstadoLote | null;
  texto: string;
  diasPorVencer: number;
  incluirAgotados: boolean;
}

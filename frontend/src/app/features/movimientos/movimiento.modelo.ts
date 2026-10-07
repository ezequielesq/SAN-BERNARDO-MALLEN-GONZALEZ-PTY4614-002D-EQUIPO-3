export type TipoMovimiento = 'ENTRADA' | 'SALIDA';

export interface Movimiento {
  id: number;
  productoId: number;
  productoNombre: string;
  tipo: TipoMovimiento;
  cantidad: number;
  motivo: string | null;
  costoUnitario: number | null;
  fecha: string;
  usuarioEmail: string | null;
}

export interface AlertaVencimiento {
  loteId: number;
  productoId: number;
  productoNombre: string;
  numeroLote: string | null;
  cantidadDisponible: number;
  fechaVencimiento: string;
  vencido: boolean;
}

export interface EntradaRequest {
  productoId: number;
  cantidad: number;
  fechaVencimiento: string | null;
  numeroLote: string | null;
  motivo: string | null;
}

export interface SalidaRequest {
  productoId: number;
  cantidad: number;
  motivo: string;
}

export type EstadoStock = 'NORMAL' | 'BAJO' | 'CRITICO' | 'AGOTADO';

export interface StockStatus {
  productoId: number;
  productoNombre: string;
  stockActual: number;
  estado: EstadoStock;
  fechaVencimientoProximo: string | null;
  costoUnitario: number | null;
  valorTotal: number | null;
}

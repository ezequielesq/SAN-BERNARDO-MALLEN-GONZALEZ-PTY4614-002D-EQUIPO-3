/** Una línea del libro de movimientos. `sentido` es +1 (entra stock) o -1 (sale); `tipo` es el código del tipo de operación. */
export interface Movimiento {
  id: number;
  operacionId: number;
  productoId: number;
  productoNombre: string;
  tipo: string;
  tipoNombre: string;
  sentido: number;
  cantidad: number;
  loteId: number;
  numeroLote: string | null;
  costoUnitario: number | null;
  nota: string | null;
  fecha: string;
  usuarioEmail: string | null;
  /** La operación ya tiene una corrección. */
  corregida: boolean;
  /** Esta operación corrige a otra. */
  esCorreccion: boolean;
}

/** Tipo de operación seleccionable a mano (catálogo `tipos_operacion`). */
export interface TipoOperacion {
  id: number;
  codigo: string;
  nombre: string;
  sentido: number;
}

export interface OperacionResponse {
  id: number;
  tipo: string;
  tipoNombre: string;
  fecha: string;
  usuarioEmail: string | null;
  nota: string | null;
  lineas: {
    productoId: number;
    productoNombre: string;
    loteId: number;
    numeroLote: string | null;
    cantidad: number;
  }[];
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

export interface LineaEntrada {
  productoId: number;
  cantidad: number;
  fechaVencimiento: string | null;
  numeroLote: string | null;
  costoUnitario: number | null;
}

export interface EntradaRequest {
  tipo: string;
  nota: string | null;
  lineas: LineaEntrada[];
}

export interface LineaSalida {
  productoId: number;
  cantidad: number;
}

export interface SalidaRequest {
  tipo: string;
  nota: string | null;
  lineas: LineaSalida[];
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

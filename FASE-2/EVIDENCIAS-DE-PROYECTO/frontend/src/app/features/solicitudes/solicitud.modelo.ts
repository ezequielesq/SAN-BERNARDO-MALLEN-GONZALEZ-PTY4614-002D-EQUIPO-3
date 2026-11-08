export type EstadoSolicitud = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';
export type TipoSolicitud = 'PEDIDO' | 'DEVOLUCION';

export interface DetalleSolicitud {
  productoId: number;
  productoNombre: string;
  cantidadSolicitada: number;
  cantidadEntregada: number | null;
  cantidadDevuelta: number | null;
  devolvible: number | null;
  agregado: boolean;
}

export interface Solicitud {
  id: number;
  solicitanteNombre: string;
  estado: EstadoSolicitud;
  tipo: TipoSolicitud;
  solicitudOrigenId: number | null;
  fecha: string;
  detalles: DetalleSolicitud[];
}

/** Cantidad por producto: lo que se entrega (pedido) o se acepta (devolución). Un producto fuera del pedido es un agregado. */
export interface ItemAprobado {
  productoId: number;
  cantidad: number;
}

export interface AprobarSolicitudRequest {
  items: ItemAprobado[];
}

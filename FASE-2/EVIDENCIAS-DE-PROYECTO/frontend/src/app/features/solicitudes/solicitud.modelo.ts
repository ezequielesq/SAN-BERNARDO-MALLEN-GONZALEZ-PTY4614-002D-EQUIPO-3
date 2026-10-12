export type EstadoSolicitud = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';
export type TipoSolicitud = 'PEDIDO' | 'DEVOLUCION';

export interface DetalleSolicitud {
  detalleId: number;
  productoId: number;
  productoNombre: string;
  cantidadSolicitada: number;
  cantidadEntregada: number | null;
  cantidadDevuelta: number | null;
  devolvible: number | null;
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

export interface ItemEntregado {
  detalleId: number;
  cantidadEntregada: number;
}

export interface AprobarSolicitudRequest {
  items: ItemEntregado[];
}

export type EstadoSolicitud = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';

export interface DetalleSolicitud {
  detalleId: number;
  productoId: number;
  productoNombre: string;
  cantidadSolicitada: number;
  cantidadEntregada: number | null;
}

export interface Solicitud {
  id: number;
  solicitanteNombre: string;
  estado: EstadoSolicitud;
  fecha: string;
  detalles: DetalleSolicitud[];
}

export interface ItemSolicitudRequest {
  productoId: number;
  cantidad: number;
}

export interface SolicitudRequest {
  items: ItemSolicitudRequest[];
}

export interface ItemEntregado {
  detalleId: number;
  cantidadEntregada: number;
}

export interface AprobarSolicitudRequest {
  items: ItemEntregado[];
}

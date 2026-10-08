export interface EmpleadoOpcion {
  id: number;
  nombre: string;
}

export interface ProductoOpcion {
  id: number;
  nombre: string;
  unidadMedida: string;
}

export interface CredencialesEmpleado {
  empleadoId: number;
  password: string;
}

export interface ItemPedido {
  productoId: number;
  cantidad: number;
}

export interface SolicitudPublicaRequest extends CredencialesEmpleado {
  items: ItemPedido[];
}

export interface ItemDevolucion {
  detalleId: number;
  cantidad: number;
}

export interface DevolucionRequest extends CredencialesEmpleado {
  solicitudOrigenId: number;
  items: ItemDevolucion[];
}

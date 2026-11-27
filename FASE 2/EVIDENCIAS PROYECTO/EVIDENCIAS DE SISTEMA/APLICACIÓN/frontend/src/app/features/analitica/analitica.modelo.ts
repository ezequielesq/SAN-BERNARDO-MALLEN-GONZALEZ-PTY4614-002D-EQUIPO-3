export type Agrupacion = 'DIA' | 'SEMANA' | 'MES';
export type Medida = 'valor' | 'cantidad';

/** Filtro compartido por todos los bloques del panel; las fechas son AAAA-MM-DD. */
export interface FiltroPanel {
  desde: string;
  hasta: string;
  agrupar: Agrupacion;
  categoriaId: number | null;
  productoIds: number[];
  solicitanteId: number | null;
  proveedorId: number | null;
}

export interface ResumenPanel {
  consumoNetoValor: number;
  cantidadNeta: number | null;
  unidadComun: string | null;
  pedidos: number;
  personas: number;
  solicitadoValor: number;
  entregadoValor: number;
  solicitadoCantidad: number | null;
  entregadoCantidad: number | null;
  perdidasValor: number;
  lineasSinCosto: number;
}

export interface PuntoConsumo {
  inicio: string;
  consumoValor: number;
  consumoCantidad: number | null;
  perdidasValor: number;
}

export interface SerieConsumo {
  unidadComun: string | null;
  puntos: PuntoConsumo[];
}

export interface ProductoTop {
  productoId: number;
  nombre: string;
  unidad: string;
  cantidad: number;
  valor: number;
}

/** Gasto en compras a un proveedor; los lotes sin proveedor vienen con `proveedorId` nulo. */
export interface CompraProveedor {
  proveedorId: number | null;
  nombre: string;
  valor: number;
  cantidad: number;
  compras: number;
}

export interface Importe {
  valor: number;
  cantidad: number | null;
}

export interface FilaPersona {
  usuarioId: number;
  nombre: string;
  pedidos: number;
  solicitado: Importe;
  entregado: Importe;
  devuelto: Importe;
  neto: Importe;
}

export interface PedidosPorPersona {
  unidadComun: string | null;
  filas: FilaPersona[];
}

export interface Opcion {
  id: number;
  nombre: string;
}

export interface ProductoClave {
  productoId: number;
  nombre: string;
}

export interface Compra {
  fecha: string;
  costo: number;
  cantidad: number;
  proveedorNombre: string | null;
}

/** Precios de compra de un producto a un proveedor en el rango. */
export interface ProveedorPrecio {
  proveedorId: number;
  nombre: string;
  ultimo: number | null;
  promedioPonderado: number | null;
  compras: number;
}

export interface HistorialPrecios {
  productoId: number;
  nombre: string;
  unidad: string;
  compras: Compra[];
  ultimo: number | null;
  anterior: number | null;
  variacionPct: number | null;
  minimo: number | null;
  maximo: number | null;
  promedio: number | null;
  promedioPonderado: number | null;
  /** Compara a todos los proveedores del producto en el rango, aunque haya uno elegido. */
  porProveedor: ProveedorPrecio[];
}

export interface VencimientoDto {
  productoId: number;
  nombre: string;
  cantidadEnRiesgo: number;
  fechaVencimiento: string;
  vencido: boolean;
}

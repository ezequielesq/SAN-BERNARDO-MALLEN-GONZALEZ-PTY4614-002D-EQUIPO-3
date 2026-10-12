export type AgruparPor = 'PRODUCTO' | 'CATEGORIA';

export interface ConsumoDto {
  id: number;
  nombre: string;
  entradaCantidad: number;
  entradaValor: number | null;
  salidaCantidad: number;
  salidaValor: number | null;
}

export interface ProductoMasUsadoDto {
  productoId: number;
  nombre: string;
  categoriaNombre: string;
  totalSalidas: number;
}

export interface VencimientoDto {
  productoId: number;
  nombre: string;
  cantidadEnRiesgo: number;
  fechaVencimiento: string;
  vencido: boolean;
}

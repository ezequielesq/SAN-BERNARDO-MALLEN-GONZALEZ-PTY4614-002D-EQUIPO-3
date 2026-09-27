export interface Producto {
  id: number;
  codigoPtv: string;
  nombre: string;
  categoriaId: number;
  categoriaNombre: string;
  unidadMedidaId: number;
  unidadMedidaNombre: string;
  esPerecible: boolean;
  stockMinimo: number;
  stockCritico: number;
  activo: boolean;
}

export interface ProductoRequest {
  codigoPtv: string;
  nombre: string;
  categoriaId: number;
  unidadMedidaId: number;
  esPerecible: boolean;
  stockMinimo: number;
  stockCritico: number;
}

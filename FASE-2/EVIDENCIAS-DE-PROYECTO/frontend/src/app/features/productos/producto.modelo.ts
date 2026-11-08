export interface Producto {
  id: number;
  codigoPtv: string | null;
  codigo: string | null;
  nombre: string;
  categoriaId: number;
  categoriaNombre: string;
  unidadMedidaId: number;
  unidadMedidaNombre: string;
  esPerecible: boolean;
  stockMinimo: number;
  stockCritico: number;
  costoUnitario: number | null;
  activo: boolean;
}

export interface ProductoRequest {
  codigoPtv: string | null;
  codigo: string | null;
  nombre: string;
  categoriaId: number;
  unidadMedidaId: number;
  esPerecible: boolean;
  stockMinimo: number;
  stockCritico: number;
  costoUnitario: number;
}

/** Una fila de la importación masiva; categoría y unidad van por id, los textos son lo que decía el Excel. */
export interface FilaImportacion {
  fila: number | null;
  codigoPtv: string | null;
  codigo: string | null;
  nombre: string | null;
  categoriaId: number | null;
  unidadMedidaId: number | null;
  categoriaTexto: string | null;
  unidadTexto: string | null;
  esPerecible: boolean;
  stockMinimo: number | null;
  stockCritico: number | null;
  costoUnitario: number | null;
}

/** `errores` va por campo (por ejemplo `nombre` o `categoriaId`); vacío = la fila se puede crear. */
export interface FilaValidada {
  datos: FilaImportacion;
  errores: Record<string, string>;
}

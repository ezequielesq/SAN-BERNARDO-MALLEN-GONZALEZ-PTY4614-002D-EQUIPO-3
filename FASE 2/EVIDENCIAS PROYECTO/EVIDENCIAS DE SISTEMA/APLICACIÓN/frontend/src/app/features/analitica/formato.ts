import { Agrupacion, Compra, CompraProveedor, ProveedorPrecio } from './analitica.modelo';
import type { ItemBarra } from './graficos/grafico-barras-horizontales';

const PESOS = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 });
const NUMERO = new Intl.NumberFormat('es-CL', { maximumFractionDigits: 1 });
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

export function formatearPesos(valor: number): string {
  return PESOS.format(valor);
}

export function formatearCantidad(cantidad: number, unidad: string | null): string {
  return unidad === null ? NUMERO.format(cantidad) : `${NUMERO.format(cantidad)} ${unidad}`;
}

/** «02-03» para AAAA-03-02. */
export function formatearFechaCorta(fecha: string): string {
  const [, mes, dia] = fecha.split('-');
  return `${dia}-${mes}`;
}

/** Etiqueta de un período: día «02-03», semana «sem 02-03», mes «mar 2026». */
export function formatearPeriodo(inicio: string, agrupar: Agrupacion): string {
  if (agrupar === 'MES') {
    const [anio, mes] = inicio.split('-');
    return `${MESES[Number(mes) - 1]} ${anio}`;
  }
  return agrupar === 'SEMANA' ? `sem ${formatearFechaCorta(inicio)}` : formatearFechaCorta(inicio);
}

/** Variación porcentual con signo explícito en las alzas: «+12,5 %»; sin dato, «—». */
export function formatearVariacion(porcentaje: number | null): string {
  if (porcentaje === null) return '—';
  return `${porcentaje > 0 ? '+' : ''}${NUMERO.format(porcentaje)} %`;
}

/** «1 persona», «2 personas»: singular solo con uno. */
export function contar(n: number, singular: string, plural: string): string {
  return `${n} ${n === 1 ? singular : plural}`;
}

/** Texto accesible del gráfico de productos más usados, en singular o plural según el número de productos. */
export function descripcionTop(n: number): string {
  const productos = n === 1 ? 'el producto más usado' : `los ${n} productos más usados`;
  return `Gráfico de barras horizontales con ${productos} del período.`;
}

/** Fila de la comparación por proveedor ya formateada; sin dato, «—». */
export function filaProveedor(p: ProveedorPrecio): { nombre: string; compras: string; ultimo: string; ponderado: string } {
  const pesos = (n: number | null) => (n === null ? '—' : formatearPesos(n));
  return { nombre: p.nombre, compras: String(p.compras), ultimo: pesos(p.ultimo), ponderado: pesos(p.promedioPonderado) };
}

/** Fila del listado de compras ya formateada; una compra sin proveedor lo dice. */
export function filaCompra(c: Compra): { fecha: string; proveedor: string; cantidad: string; costo: string } {
  return {
    fecha: formatearFechaCorta(c.fecha),
    proveedor: c.proveedorNombre ?? 'Sin proveedor',
    cantidad: NUMERO.format(c.cantidad),
    costo: formatearPesos(c.costo),
  };
}

/** Una barra por proveedor con lo gastado en pesos (las unidades de los productos no se pueden sumar). */
export function itemsComprasProveedor(lista: readonly CompraProveedor[]): ItemBarra[] {
  return lista.map((p) => ({ etiqueta: p.nombre, valor: p.valor, texto: formatearPesos(p.valor) }));
}

/** Texto accesible del gráfico de compras por proveedor. */
export function descripcionComprasProveedor(n: number): string {
  return `Gráfico de barras horizontales con el gasto en compras de ${contar(n, 'proveedor', 'proveedores')} del período.`;
}

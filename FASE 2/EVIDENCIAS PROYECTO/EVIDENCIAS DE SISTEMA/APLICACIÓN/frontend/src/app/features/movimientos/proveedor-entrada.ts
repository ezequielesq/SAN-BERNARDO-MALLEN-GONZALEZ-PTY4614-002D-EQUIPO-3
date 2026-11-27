/** En una compra el proveedor es obligatorio; en los demás tipos de entrada es opcional. */
export function proveedorObligatorio(tipo: string): boolean {
  return tipo === 'COMPRA';
}

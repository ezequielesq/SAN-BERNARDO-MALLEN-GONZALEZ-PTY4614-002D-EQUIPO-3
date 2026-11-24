let contador = 0;

/** Identificador distinto en cada llamada, para relacionar un botón con el bloque que controla (aria-controls). */
export function idUnico(prefijo: string): string {
  contador += 1;
  return `${prefijo}-${contador}`;
}

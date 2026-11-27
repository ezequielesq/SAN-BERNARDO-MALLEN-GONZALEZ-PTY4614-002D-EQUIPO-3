import { computed, signal } from '@angular/core';
import { Agrupacion, FiltroPanel, Medida } from './analitica.modelo';
import { Atajo, rangoDeAtajo, sugerirAgrupacion, validarRango } from './panel-filtros';

/**
 * Filtros compartidos por todos los bloques del panel. Son señales simples: los bloques las leen y la barra de
 * filtros las escribe. El filtro que se envía al servidor es `filtro` (nulo si el rango no es válido).
 */
export class EstadoFiltros {
  readonly desde = signal('');
  readonly hasta = signal('');
  readonly agrupar = signal<Agrupacion>('SEMANA');
  readonly categoriaId = signal<number | null>(null);
  readonly proveedorId = signal<number | null>(null);
  readonly productoIds = signal<number[]>([]);
  readonly solicitanteId = signal<number | null>(null);
  readonly medida = signal<Medida>('valor');

  readonly errorRango = computed(() => validarRango(this.desde(), this.hasta()));

  readonly filtro = computed<FiltroPanel | null>(() =>
    this.errorRango() !== null
      ? null
      : {
          desde: this.desde(),
          hasta: this.hasta(),
          agrupar: this.agrupar(),
          categoriaId: this.categoriaId(),
          productoIds: this.productoIds(),
          solicitanteId: this.solicitanteId(),
          proveedorId: this.proveedorId(),
        },
  );

  constructor(hoy: Date = new Date()) {
    this.aplicarAtajo('30d', hoy);
  }

  aplicarAtajo(atajo: Atajo, hoy: Date = new Date()): void {
    const { desde, hasta } = rangoDeAtajo(atajo, hoy);
    this.desde.set(desde);
    this.hasta.set(hasta);
    this.agrupar.set(sugerirAgrupacion(atajo));
  }
}

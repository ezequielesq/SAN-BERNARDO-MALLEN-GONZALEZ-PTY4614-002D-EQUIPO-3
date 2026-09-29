import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { EstadoBadge, TipoEstado } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { MovimientoService } from './movimiento.service';

type ColumnaOrdenStock = 'productoNombre' | 'stockActual' | 'estado' | 'fechaVencimientoProximo';

@Component({
  selector: 'app-stock-lista',
  imports: [EstadoVista, EstadoBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stock-lista.html',
})
export class StockLista {
  private readonly movimientoService = inject(MovimientoService);

  protected readonly stock = this.movimientoService.listarStock();
  protected readonly todos = computed(() => (this.stock.hasValue() ? this.stock.value() : []));

  protected readonly criticosYAgotados = computed(() =>
    this.todos().filter((p) => p.estado === 'CRITICO' || p.estado === 'AGOTADO'),
  );

  protected readonly columnaOrden = signal<ColumnaOrdenStock | null>(null);
  protected readonly direccionOrden = signal<'asc' | 'desc'>('asc');

  protected readonly ordenados = computed(() => {
    const columna = this.columnaOrden();
    const lista = [...this.todos()];
    if (columna === null) return lista;
    const dir = this.direccionOrden() === 'asc' ? 1 : -1;
    lista.sort((a, b) => {
      switch (columna) {
        case 'productoNombre':
          return a.productoNombre.localeCompare(b.productoNombre, 'es') * dir;
        case 'stockActual':
          return (a.stockActual - b.stockActual) * dir;
        case 'estado':
          return a.estado.localeCompare(b.estado, 'es') * dir;
        case 'fechaVencimientoProximo':
          return (
            (new Date(a.fechaVencimientoProximo ?? 0).getTime() -
              new Date(b.fechaVencimientoProximo ?? 0).getTime()) *
            dir
          );
      }
    });
    return lista;
  });

  protected ordenarPor(columna: ColumnaOrdenStock): void {
    if (this.columnaOrden() === columna) {
      this.direccionOrden.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.columnaOrden.set(columna);
      this.direccionOrden.set('asc');
    }
  }

  protected iconoOrden(columna: ColumnaOrdenStock): string {
    if (this.columnaOrden() !== columna) return 'fa-sort';
    return this.direccionOrden() === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  protected ariaSort(columna: ColumnaOrdenStock): 'ascending' | 'descending' | null {
    if (this.columnaOrden() !== columna) return null;
    return this.direccionOrden() === 'asc' ? 'ascending' : 'descending';
  }

  protected estadoBadge(estado: string): TipoEstado {
    return estado.toLowerCase() as TipoEstado;
  }
}

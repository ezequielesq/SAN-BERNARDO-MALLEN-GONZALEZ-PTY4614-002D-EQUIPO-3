import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { EstadoBadge, TipoEstado } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { OpcionOrden, SelectorOrden } from '../../shared/ordenamiento/selector-orden';
import { ThOrden } from '../../shared/ordenamiento/th-orden';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { StockStatus } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

type ColumnaOrdenStock =
  | 'productoNombre'
  | 'stockActual'
  | 'costoUnitario'
  | 'valorTotal'
  | 'estado'
  | 'fechaVencimientoProximo';

const EXTRACTORES_STOCK: Extractores<StockStatus, ColumnaOrdenStock> = {
  productoNombre: (p) => p.productoNombre,
  stockActual: (p) => p.stockActual,
  costoUnitario: (p) => p.costoUnitario,
  valorTotal: (p) => p.valorTotal,
  estado: (p) => p.estado,
  fechaVencimientoProximo: (p) =>
    p.fechaVencimientoProximo ? Date.parse(p.fechaVencimientoProximo) : null,
};

const OPCIONES_TARJETAS: readonly OpcionOrden[] = [
  { valor: 'stockActual', etiqueta: 'Stock actual' },
  { valor: 'productoNombre', etiqueta: 'Producto' },
  { valor: 'valorTotal', etiqueta: 'Valor' },
];

@Component({
  selector: 'app-stock-lista',
  imports: [EstadoVista, EstadoBadge, ThOrden, SelectorOrden, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './stock-lista.html',
})
export class StockLista {
  private readonly movimientoService = inject(MovimientoService);

  protected readonly stock = this.movimientoService.listarStock();
  protected readonly todos = computed(() => (this.stock.hasValue() ? this.stock.value() : []));

  protected readonly opcionesTarjetas = OPCIONES_TARJETAS;
  protected readonly paginaTarjetas = signal(1);
  protected readonly tamanoTarjetas = signal(15);
  protected readonly opcionesTamanoTarjetas = [10, 15, 20, 50] as const;
  protected readonly ordenTarjetas = new Orden<ColumnaOrdenStock>({ columna: 'stockActual' }, () =>
    this.paginaTarjetas.set(1),
  );
  protected readonly orden = new Orden<ColumnaOrdenStock>();

  protected readonly criticosYAgotados = computed(() =>
    ordenar(
      this.todos().filter((p) => p.estado === 'CRITICO' || p.estado === 'AGOTADO'),
      this.ordenTarjetas,
      EXTRACTORES_STOCK,
    ),
  );

  protected readonly tarjetasPaginadas = computed(() => {
    const inicio = (this.paginaTarjetas() - 1) * this.tamanoTarjetas();
    return this.criticosYAgotados().slice(inicio, inicio + this.tamanoTarjetas());
  });

  protected readonly ordenados = computed(() => ordenar(this.todos(), this.orden, EXTRACTORES_STOCK));

  protected cambiarPaginaTarjetas(pagina: number): void {
    this.paginaTarjetas.set(pagina);
  }

  protected cambiarTamanoTarjetas(tamano: number): void {
    this.tamanoTarjetas.set(tamano);
    this.paginaTarjetas.set(1);
  }

  protected estadoBadge(estado: string): TipoEstado {
    return estado.toLowerCase() as TipoEstado;
  }
}

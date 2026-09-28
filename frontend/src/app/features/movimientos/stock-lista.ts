import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { EstadoBadge, TipoEstado } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { MovimientoService } from './movimiento.service';

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

  protected estadoBadge(estado: string): TipoEstado {
    return estado.toLowerCase() as TipoEstado;
  }
}

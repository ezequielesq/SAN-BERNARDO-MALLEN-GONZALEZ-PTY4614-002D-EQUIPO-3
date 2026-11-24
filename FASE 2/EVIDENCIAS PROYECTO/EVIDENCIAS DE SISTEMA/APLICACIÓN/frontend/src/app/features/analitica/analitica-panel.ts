import { ChangeDetectionStrategy, Component } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { debounceTime } from 'rxjs';
import { AnaliticaConsumoPeriodo } from './analitica-consumo-periodo';
import { AnaliticaFiltros } from './analitica-filtros';
import { AnaliticaPedidosPersona } from './analitica-pedidos-persona';
import { AnaliticaPrecios } from './analitica-precios';
import { AnaliticaTarjetas } from './analitica-tarjetas';
import { AnaliticaTopProductos } from './analitica-top-productos';
import { AnaliticaVencimientos } from './analitica-vencimientos';
import { EstadoFiltros } from './panel-estado';

/** Panel de analítica: filtros compartidos y bloques que se recalculan al cambiarlos. */
@Component({
  selector: 'app-analitica-panel',
  imports: [
    AnaliticaFiltros,
    AnaliticaTarjetas,
    AnaliticaConsumoPeriodo,
    AnaliticaTopProductos,
    AnaliticaPedidosPersona,
    AnaliticaVencimientos,
    AnaliticaPrecios,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-panel.html',
})
export class AnaliticaPanel {
  protected readonly estado = new EstadoFiltros();

  /** Filtro que consultan los bloques: espera 250 ms para no pedir una vez por cada casilla marcada. */
  protected readonly filtroAplicado = toSignal(
    toObservable(this.estado.filtro).pipe(debounceTime(250)),
    {
      initialValue: this.estado.filtro(),
    },
  );
}

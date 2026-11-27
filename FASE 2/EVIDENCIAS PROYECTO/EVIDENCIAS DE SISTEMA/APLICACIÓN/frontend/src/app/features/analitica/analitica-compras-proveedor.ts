import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { FiltroPanel } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';
import { descripcionComprasProveedor, itemsComprasProveedor } from './formato';
import { GraficoBarrasHorizontales } from './graficos/grafico-barras-horizontales';

/** Gasto en compras por proveedor en el rango, de mayor a menor; siempre en pesos. */
@Component({
  selector: 'app-analitica-compras-proveedor',
  imports: [EstadoVista, GraficoBarrasHorizontales],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-compras-proveedor.html',
})
export class AnaliticaComprasProveedor {
  readonly filtro = input.required<FiltroPanel | null>();

  private readonly service = inject(AnaliticaService);

  protected readonly compras = this.service.comprasPorProveedor(this.filtro);
  protected readonly lista = computed(() => (this.compras.hasValue() ? this.compras.value() : []));

  /** El servidor ignora la persona en este bloque: las compras no se asignan a personas. */
  protected readonly conPersona = computed(() => this.filtro()?.solicitanteId != null);

  protected readonly items = computed(() => itemsComprasProveedor(this.lista()));
  protected readonly descripcion = computed(() => descripcionComprasProveedor(this.lista().length));
}

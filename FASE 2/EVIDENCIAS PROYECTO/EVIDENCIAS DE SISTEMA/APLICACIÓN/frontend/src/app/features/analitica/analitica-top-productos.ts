import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { FiltroPanel } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';
import { formatearCantidad, formatearPesos } from './formato';
import { GraficoBarrasHorizontales, ItemBarra } from './graficos/grafico-barras-horizontales';
import { EstadoFiltros } from './panel-estado';
import { decidirMedida } from './panel-filtros';

@Component({
  selector: 'app-analitica-top-productos',
  imports: [EstadoVista, GraficoBarrasHorizontales],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-top-productos.html',
})
export class AnaliticaTopProductos {
  readonly filtro = input.required<FiltroPanel | null>();
  readonly estado = input.required<EstadoFiltros>();

  private readonly service = inject(AnaliticaService);

  protected readonly top = this.service.productosTop(this.filtro, 7);
  protected readonly lista = computed(() => (this.top.hasValue() ? this.top.value() : []));

  /** En cantidad, solo si todos los productos del ranking comparten unidad. */
  protected readonly unidadComun = computed(() => {
    const unidades = new Set(this.lista().map((p) => p.unidad));
    return unidades.size === 1 ? [...unidades][0] : null;
  });
  /** Sin productos (cargando, vacío o con error) no hay unidad que comparar: se conserva la medida y no se avisa. */
  protected readonly decision = computed(() =>
    this.lista().length === 0
      ? { medida: this.estado().medida(), aviso: null }
      : decidirMedida(this.estado().medida(), this.unidadComun()),
  );
  protected readonly enCantidad = computed(() => this.decision().medida === 'cantidad');

  protected readonly items = computed<ItemBarra[]>(() =>
    this.lista().map((p) => ({
      etiqueta: p.nombre,
      valor: this.enCantidad() ? p.cantidad : p.valor,
      texto: this.enCantidad() ? formatearCantidad(p.cantidad, p.unidad) : formatearPesos(p.valor),
    })),
  );

  protected readonly descripcion = computed(
    () => `Gráfico de barras horizontales con los ${this.lista().length} productos más usados del período.`,
  );
}

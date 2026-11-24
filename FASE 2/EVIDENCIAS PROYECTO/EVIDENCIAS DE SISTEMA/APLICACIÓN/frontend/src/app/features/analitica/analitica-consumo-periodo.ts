import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { FiltroPanel } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';
import { formatearCantidad, formatearPeriodo, formatearPesos } from './formato';
import { GraficoBarras, PuntoBarra } from './graficos/grafico-barras';
import { EstadoFiltros } from './panel-estado';
import { decidirMedida } from './panel-filtros';

@Component({
  selector: 'app-analitica-consumo-periodo',
  imports: [EstadoVista, GraficoBarras],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-consumo-periodo.html',
})
export class AnaliticaConsumoPeriodo {
  readonly filtro = input.required<FiltroPanel | null>();
  readonly estado = input.required<EstadoFiltros>();

  private readonly service = inject(AnaliticaService);

  protected readonly serie = this.service.consumoPeriodo(this.filtro);
  protected readonly datos = computed(() => (this.serie.hasValue() ? this.serie.value() : null));
  protected readonly vacio = computed(() => {
    const d = this.datos();
    return d !== null && d.puntos.every((p) => p.consumoValor === 0 && p.perdidasValor === 0);
  });

  protected readonly decision = computed(() => decidirMedida(this.estado().medida(), this.datos()?.unidadComun ?? null));
  protected readonly enCantidad = computed(() => this.decision().medida === 'cantidad');

  protected readonly formatear = computed(() => {
    const unidad = this.datos()?.unidadComun ?? null;
    return this.enCantidad() ? (n: number) => formatearCantidad(n, unidad) : formatearPesos;
  });

  /** Las pérdidas siempre van en pesos (no hay cantidad común entre merma y vencimiento de distintos productos). */
  protected readonly puntos = computed<PuntoBarra[]>(() => {
    const d = this.datos();
    if (d === null) return [];
    const agrupar = this.filtro()?.agrupar ?? 'SEMANA';
    return d.puntos.map((p) => ({
      etiqueta: formatearPeriodo(p.inicio, agrupar),
      valor: this.enCantidad() ? (p.consumoCantidad ?? 0) : p.consumoValor,
      secundario: this.enCantidad() ? 0 : p.perdidasValor,
    }));
  });

  protected readonly descripcion = computed(() => {
    const agrupar = { DIA: 'día', SEMANA: 'semana', MES: 'mes' }[this.filtro()?.agrupar ?? 'SEMANA'];
    const unidad = this.enCantidad() ? 'cantidad' : 'valor en pesos';
    return `Gráfico de barras del consumo por ${agrupar} (${unidad}), con las pérdidas como segunda serie rayada.`;
  });
}

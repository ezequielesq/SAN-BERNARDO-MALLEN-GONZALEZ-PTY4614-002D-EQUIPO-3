import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { FiltroPanel } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';
import { formatearCantidad, formatearPesos } from './formato';
import { EstadoFiltros } from './panel-estado';
import { decidirMedida } from './panel-filtros';

/** Días del umbral de la quinta tarjeta (lotes por vencer). */
const DIAS_TARJETA_VENCIMIENTOS = 30;

@Component({
  selector: 'app-analitica-tarjetas',
  imports: [EstadoVista],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-tarjetas.html',
})
export class AnaliticaTarjetas {
  readonly filtro = input.required<FiltroPanel | null>();
  readonly estado = input.required<EstadoFiltros>();

  private readonly service = inject(AnaliticaService);

  protected readonly resumen = this.service.resumen(this.filtro);
  protected readonly r = computed(() => (this.resumen.hasValue() ? this.resumen.value() : null));

  private readonly filtroVencimientos = computed(() => ({
    dias: DIAS_TARJETA_VENCIMIENTOS,
    categoriaId: this.filtro()?.categoriaId ?? null,
    productoIds: this.filtro()?.productoIds ?? [],
  }));
  protected readonly vencimientos = this.service.vencimientos(this.filtroVencimientos);
  protected readonly lotesPorVencer = computed(() =>
    this.vencimientos.hasValue() ? this.vencimientos.value().length : 0,
  );
  protected readonly diasVencimientos = DIAS_TARJETA_VENCIMIENTOS;

  protected readonly decision = computed(() =>
    decidirMedida(this.estado().medida(), this.r()?.unidadComun ?? null),
  );
  protected readonly enCantidad = computed(() => this.decision().medida === 'cantidad');

  protected readonly consumo = computed(() => {
    const r = this.r();
    if (r === null) return '';
    return this.enCantidad() && r.cantidadNeta !== null
      ? formatearCantidad(r.cantidadNeta, r.unidadComun)
      : formatearPesos(r.consumoNetoValor);
  });

  protected readonly entregadoDeSolicitado = computed(() => {
    const r = this.r();
    if (r === null) return { porcentaje: null as number | null, texto: '' };
    const cantidad = this.enCantidad() && r.entregadoCantidad !== null && r.solicitadoCantidad !== null;
    const entregado = cantidad ? r.entregadoCantidad! : r.entregadoValor;
    const solicitado = cantidad ? r.solicitadoCantidad! : r.solicitadoValor;
    const mostrar = (n: number) => (cantidad ? formatearCantidad(n, r.unidadComun) : formatearPesos(n));
    return {
      porcentaje: solicitado === 0 ? null : Math.round((entregado / solicitado) * 100),
      texto: `${mostrar(entregado)} de ${mostrar(solicitado)}`,
    };
  });

  protected readonly perdidas = computed(() => {
    const r = this.r();
    return r === null ? '' : formatearPesos(r.perdidasValor);
  });

  protected readonly conPersona = computed(() => this.filtro()?.solicitanteId != null);

  /** Texto para lectores de pantalla cuando cambian las cifras. */
  protected readonly anuncio = computed(() => {
    const r = this.r();
    return r === null
      ? ''
      : `Panel actualizado: consumo neto ${this.consumo()}, ${r.pedidos} pedidos de ${r.personas} personas.`;
  });
}

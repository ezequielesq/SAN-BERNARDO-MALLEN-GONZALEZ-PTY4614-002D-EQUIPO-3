import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { FiltroPanel } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';
import { contar, formatearCantidad, formatearPesos } from './formato';
import { EstadoFiltros } from './panel-estado';
import { contarPorVencer, decidirMedida, hayDatosEnResumen, sonIguales } from './panel-filtros';

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

  /** `sonIguales`: un cambio de fechas, agrupación o persona no vuelve a pedir los vencimientos. */
  private readonly filtroVencimientos = computed(
    () => ({
      dias: DIAS_TARJETA_VENCIMIENTOS,
      categoriaId: this.filtro()?.categoriaId ?? null,
      productoIds: this.filtro()?.productoIds ?? [],
    }),
    { equal: sonIguales },
  );
  protected readonly vencimientos = this.service.vencimientos(this.filtroVencimientos);
  /** Nulo mientras carga o si falla: la tarjeta muestra «—» en vez de un 0 que no es real. */
  protected readonly lotesPorVencer = computed(() =>
    this.vencimientos.isLoading() || this.vencimientos.error() ? null : contarPorVencer(this.vencimientos.value()),
  );
  protected readonly contar = contar;
  protected readonly diasVencimientos = DIAS_TARJETA_VENCIMIENTOS;

  /** Sin datos reales no hay unidad que comparar: se conserva la medida y no se avisa. */
  protected readonly decision = computed(() => {
    const r = this.r();
    return r !== null && hayDatosEnResumen(r)
      ? decidirMedida(this.estado().medida(), r.unidadComun)
      : { medida: this.estado().medida(), aviso: null };
  });
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
      : `Panel actualizado: consumo neto ${this.consumo()}, ${contar(r.pedidos, 'pedido', 'pedidos')} de ${contar(r.personas, 'persona', 'personas')}.`;
  });
}

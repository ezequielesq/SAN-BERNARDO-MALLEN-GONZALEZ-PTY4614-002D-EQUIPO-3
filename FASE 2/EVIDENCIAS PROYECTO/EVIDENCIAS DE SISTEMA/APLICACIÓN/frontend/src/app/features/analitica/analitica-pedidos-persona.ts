import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { ThOrden } from '../../shared/ordenamiento/th-orden';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { paginaAjustada, paginar } from '../../shared/paginacion/paginar';
import { FilaPersona, FiltroPanel, Importe } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';
import { formatearCantidad, formatearPesos } from './formato';
import { EstadoFiltros } from './panel-estado';
import { decidirMedida, solicitadoAplica } from './panel-filtros';

type ColumnaPersona = 'nombre' | 'pedidos' | 'solicitado' | 'entregado' | 'devuelto' | 'neto';

@Component({
  selector: 'app-analitica-pedidos-persona',
  imports: [EstadoVista, Paginacion, ThOrden],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-pedidos-persona.html',
})
export class AnaliticaPedidosPersona {
  readonly filtro = input.required<FiltroPanel | null>();
  readonly estado = input.required<EstadoFiltros>();

  private readonly service = inject(AnaliticaService);

  protected readonly resultado = this.service.pedidosPorPersona(this.filtro);
  protected readonly datos = computed(() =>
    this.resultado.hasValue() ? this.resultado.value() : null,
  );

  /** Vuelve a la página 1 si cambia el filtro, la medida (reordena las filas), el orden o el tamaño de página. */
  protected readonly pagina = linkedSignal({
    source: () => ({ filtro: this.filtro(), medida: this.estado().medida() }),
    computation: () => 1,
  });
  protected readonly tamano = signal(10);
  protected readonly opcionesTamano = [5, 10, 20, 50] as const;

  protected readonly orden = new Orden<ColumnaPersona>(
    { columna: 'neto', direccion: 'desc' },
    () => this.pagina.set(1),
  );

  private readonly extractores = computed<Extractores<FilaPersona, ColumnaPersona>>(() => {
    const medida = (i: Importe) => (this.enCantidad() ? (i.cantidad ?? 0) : i.valor);
    return {
      nombre: (f) => f.nombre,
      pedidos: (f) => f.pedidos,
      solicitado: (f) => medida(f.solicitado),
      entregado: (f) => medida(f.entregado),
      devuelto: (f) => medida(f.devuelto),
      neto: (f) => medida(f.neto),
    };
  });

  protected readonly filas = computed(() =>
    ordenar(this.datos()?.filas ?? [], this.orden, this.extractores()),
  );

  /** Página efectiva: si los datos se reducen y la elegida queda fuera de rango, se ajusta a la última. */
  protected readonly paginaActual = computed(() =>
    paginaAjustada(this.pagina(), this.filas().length, this.tamano()),
  );
  protected readonly filasPaginadas = computed(() =>
    paginar(this.filas(), this.paginaActual(), this.tamano()),
  );
  protected readonly hayPaginas = computed(
    () => this.filas().length > Math.min(...this.opcionesTamano),
  );

  /** Hay filas reales que mostrar: ni cargando ni con error ni vacío. */
  protected readonly hayDatos = computed(() => (this.datos()?.filas.length ?? 0) > 0);

  /** Sin filas no hay unidad que comparar: se conserva la medida y no se avisa. */
  protected readonly decision = computed(() =>
    this.hayDatos()
      ? decidirMedida(this.estado().medida(), this.datos()?.unidadComun ?? null)
      : { medida: this.estado().medida(), aviso: null },
  );
  protected readonly enCantidad = computed(() => this.decision().medida === 'cantidad');

  /** Con proveedor elegido «Solicitado» no se compara con lo entregado (ver `solicitadoAplica`). */
  protected readonly muestraSolicitado = computed(() => {
    const filtro = this.filtro();
    return filtro === null || solicitadoAplica(filtro);
  });

  protected cambiarTamano(tamano: number): void {
    this.tamano.set(tamano);
    this.pagina.set(1);
  }

  protected importe(i: Importe): string {
    return this.enCantidad() && i.cantidad !== null
      ? formatearCantidad(i.cantidad, this.datos()?.unidadComun ?? null)
      : formatearPesos(i.valor);
  }
}

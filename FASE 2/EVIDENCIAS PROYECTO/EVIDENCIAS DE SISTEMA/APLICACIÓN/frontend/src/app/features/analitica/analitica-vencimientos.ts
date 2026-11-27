import { DatePipe } from '@angular/common';
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
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { OpcionOrden, SelectorOrden } from '../../shared/ordenamiento/selector-orden';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { paginaAjustada, paginar } from '../../shared/paginacion/paginar';
import { FiltroPanel, VencimientoDto } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';
import { sonIguales } from './panel-filtros';
import { VencimientoCalendario } from './vencimiento-calendario';

type ColumnaOrdenVencimiento = 'fechaVencimiento' | 'nombre' | 'cantidadEnRiesgo';

const EXTRACTORES_VENCIMIENTO: Extractores<VencimientoDto, ColumnaOrdenVencimiento> = {
  fechaVencimiento: (v) => Date.parse(v.fechaVencimiento),
  nombre: (v) => v.nombre,
  cantidadEnRiesgo: (v) => v.cantidadEnRiesgo,
};

const OPCIONES_VENCIMIENTO: readonly OpcionOrden[] = [
  { valor: 'fechaVencimiento', etiqueta: 'Fecha de vencimiento' },
  { valor: 'nombre', etiqueta: 'Producto' },
  { valor: 'cantidadEnRiesgo', etiqueta: 'Cantidad en riesgo' },
];

function formatearFecha(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-analitica-vencimientos',
  imports: [DatePipe, EstadoBadge, EstadoVista, Paginacion, SelectorOrden, VencimientoCalendario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-vencimientos.html',
})
export class AnaliticaVencimientos {
  /** Filtro del panel ya con la espera de 250 ms (no una petición por casilla marcada); nulo si el rango no es válido. */
  readonly filtro = input.required<FiltroPanel | null>();

  private readonly analiticaService = inject(AnaliticaService);

  protected readonly diasUmbral = signal(30);

  private readonly hoy = new Date();
  protected readonly desdeFiltro = signal(formatearFecha(this.hoy));
  protected readonly hastaFiltro = signal(
    formatearFecha(new Date(this.hoy.getFullYear(), this.hoy.getMonth() + 1, 0)),
  );

  /** `sonIguales`: un cambio de fechas, agrupación o persona del panel no vuelve a pedir los vencimientos. */
  private readonly filtroUmbral = computed(
    () => ({
      dias: this.diasUmbral(),
      categoriaId: this.filtro()?.categoriaId ?? null,
      proveedorId: this.filtro()?.proveedorId ?? null,
      productoIds: this.filtro()?.productoIds ?? [],
    }),
    { equal: sonIguales },
  );
  protected readonly tarjetas = this.analiticaService.vencimientos(this.filtroUmbral);
  protected readonly filasTarjetas = computed(() =>
    this.tarjetas.hasValue() ? this.tarjetas.value() : [],
  );
  /** Vuelve a la página 1 si cambian los datos pedidos (umbral, categoría o productos), el orden o el tamaño. */
  protected readonly paginaTarjetas = linkedSignal({ source: this.filtroUmbral, computation: () => 1 });
  protected readonly tamanoTarjetas = signal(12);
  protected readonly opcionesTamanoTarjetas = [6, 12, 24, 48] as const;
  protected readonly ordenTarjetas = new Orden<ColumnaOrdenVencimiento>(
    { columna: 'fechaVencimiento' },
    () => this.paginaTarjetas.set(1),
  );
  protected readonly opcionesTarjetas = OPCIONES_VENCIMIENTO;
  protected readonly tarjetasOrdenadas = computed(() =>
    ordenar(this.filasTarjetas(), this.ordenTarjetas, EXTRACTORES_VENCIMIENTO),
  );
  /** Página efectiva: si los datos se reducen y la elegida queda fuera de rango, se ajusta a la última. */
  protected readonly paginaTarjetasActual = computed(() =>
    paginaAjustada(this.paginaTarjetas(), this.tarjetasOrdenadas().length, this.tamanoTarjetas()),
  );
  protected readonly tarjetasPaginadas = computed(() =>
    paginar(this.tarjetasOrdenadas(), this.paginaTarjetasActual(), this.tamanoTarjetas()),
  );
  protected readonly hayPaginasTarjetas = computed(
    () => this.tarjetasOrdenadas().length > Math.min(...this.opcionesTamanoTarjetas),
  );

  private readonly filtroRango = computed(
    () => ({
      desde: this.desdeFiltro(),
      hasta: this.hastaFiltro(),
      categoriaId: this.filtro()?.categoriaId ?? null,
      proveedorId: this.filtro()?.proveedorId ?? null,
      productoIds: this.filtro()?.productoIds ?? [],
    }),
    { equal: sonIguales },
  );
  protected readonly detalle = this.analiticaService.vencimientosRango(this.filtroRango);
  protected readonly filasDetalle = computed(() =>
    this.detalle.hasValue() ? this.detalle.value() : [],
  );
  protected readonly paginaDetalle = linkedSignal({ source: this.filtroRango, computation: () => 1 });
  protected readonly tamanoDetalle = signal(10);
  protected readonly opcionesTamanoDetalle = [5, 10, 20, 50] as const;
  protected readonly paginaDetalleActual = computed(() =>
    paginaAjustada(this.paginaDetalle(), this.filasDetalle().length, this.tamanoDetalle()),
  );
  protected readonly detallePaginado = computed(() =>
    paginar(this.filasDetalle(), this.paginaDetalleActual(), this.tamanoDetalle()),
  );
  protected readonly hayPaginasDetalle = computed(
    () => this.filasDetalle().length > Math.min(...this.opcionesTamanoDetalle),
  );

  protected cambiarTamanoTarjetas(tamano: number): void {
    this.tamanoTarjetas.set(tamano);
    this.paginaTarjetas.set(1);
  }

  protected cambiarTamanoDetalle(tamano: number): void {
    this.tamanoDetalle.set(tamano);
    this.paginaDetalle.set(1);
  }

  protected cambiarUmbral(valor: string): void {
    const numero = Number(valor);
    if (Number.isInteger(numero) && numero > 0) this.diasUmbral.set(numero);
  }

  protected cambiarDesde(valor: string): void {
    if (valor !== '') this.desdeFiltro.set(valor);
  }

  protected cambiarHasta(valor: string): void {
    if (valor !== '') this.hastaFiltro.set(valor);
  }

  protected alSeleccionarDiaCalendario(fecha: string | null): void {
    if (fecha === null) return;
    this.desdeFiltro.set(fecha);
    this.hastaFiltro.set(fecha);
  }

  protected estadoBadge(vencido: boolean): 'vencido' | 'proximo' {
    return vencido ? 'vencido' : 'proximo';
  }
}

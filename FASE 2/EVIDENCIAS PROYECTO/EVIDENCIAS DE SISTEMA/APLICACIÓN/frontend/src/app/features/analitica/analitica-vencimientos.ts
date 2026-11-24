import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { OpcionOrden, SelectorOrden } from '../../shared/ordenamiento/selector-orden';
import { VencimientoDto } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';
import { EstadoFiltros } from './panel-estado';
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
  imports: [DatePipe, EstadoBadge, SelectorOrden, VencimientoCalendario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-vencimientos.html',
})
export class AnaliticaVencimientos {
  readonly estado = input.required<EstadoFiltros>();

  private readonly analiticaService = inject(AnaliticaService);

  protected readonly diasUmbral = signal(30);

  private readonly hoy = new Date();
  protected readonly desdeFiltro = signal(formatearFecha(this.hoy));
  protected readonly hastaFiltro = signal(
    formatearFecha(new Date(this.hoy.getFullYear(), this.hoy.getMonth() + 1, 0)),
  );

  private readonly filtroUmbral = computed(() => ({
    dias: this.diasUmbral(),
    categoriaId: this.estado().categoriaId(),
    productoIds: this.estado().productoIds(),
  }));
  protected readonly tarjetas = this.analiticaService.vencimientos(this.filtroUmbral);
  protected readonly filasTarjetas = computed(() =>
    this.tarjetas.hasValue() ? this.tarjetas.value() : [],
  );
  protected readonly ordenTarjetas = new Orden<ColumnaOrdenVencimiento>({
    columna: 'fechaVencimiento',
  });
  protected readonly opcionesTarjetas = OPCIONES_VENCIMIENTO;
  protected readonly tarjetasOrdenadas = computed(() =>
    ordenar(this.filasTarjetas(), this.ordenTarjetas, EXTRACTORES_VENCIMIENTO),
  );

  private readonly filtroRango = computed(() => ({
    desde: this.desdeFiltro(),
    hasta: this.hastaFiltro(),
    categoriaId: this.estado().categoriaId(),
    productoIds: this.estado().productoIds(),
  }));
  protected readonly detalle = this.analiticaService.vencimientosRango(this.filtroRango);
  protected readonly filasDetalle = computed(() =>
    this.detalle.hasValue() ? this.detalle.value() : [],
  );

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

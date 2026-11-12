import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { CalendarioMes, MarcaCalendario } from '../../shared/calendario-mes/calendario-mes';
import { rangoDelMes } from '../../shared/utils/calendario';
import { MovimientoService } from './movimiento.service';

const LEYENDA: readonly MarcaCalendario[] = [
  { clave: 'entrada', etiqueta: 'Entrada', color: '#008a00', descripcion: 'con entradas' },
  { clave: 'salida', etiqueta: 'Salida', color: '#a20025', descripcion: 'con salidas' },
];

@Component({
  selector: 'app-movimiento-calendario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CalendarioMes],
  template: `
    <app-calendario-mes
      [marcas]="marcasPorDia()"
      [leyenda]="leyenda"
      [diaActivo]="diaActivo()"
      [cargando]="movimientos.status() === 'loading'"
      (diaSeleccionado)="diaSeleccionado.emit($event)"
      (rangoCambiado)="alCambiarRango($event)"
    />
  `,
})
export class MovimientoCalendario {
  private readonly movimientoService = inject(MovimientoService);

  readonly productoId = input<number | null>(null);
  readonly diaActivo = input<string | null>(null);
  readonly diaSeleccionado = output<string | null>();

  protected readonly leyenda = LEYENDA;

  private readonly hoy = new Date();
  private readonly rango = signal(rangoDelMes(this.hoy.getFullYear(), this.hoy.getMonth()));

  private readonly filtro = computed(() => ({ productoId: this.productoId(), ...this.rango() }));

  protected readonly movimientos = this.movimientoService.listar(this.filtro);

  protected readonly marcasPorDia = computed(() => {
    const mapa = new Map<string, string[]>();
    if (!this.movimientos.hasValue()) return mapa;
    for (const m of this.movimientos.value()) {
      const fecha = m.fecha.slice(0, 10);
      const claves = mapa.get(fecha) ?? [];
      const clave = m.sentido > 0 ? 'entrada' : 'salida';
      if (!claves.includes(clave)) claves.push(clave);
      mapa.set(fecha, claves);
    }
    return mapa;
  });

  /** Vuelve a pedir los movimientos del mes (por ejemplo tras registrar o importar entradas). */
  recargar(): void {
    this.movimientos.reload();
  }

  protected alCambiarRango(nuevo: { desde: string; hasta: string }): void {
    const actual = this.rango();
    if (actual.desde !== nuevo.desde || actual.hasta !== nuevo.hasta) this.rango.set(nuevo);
  }
}

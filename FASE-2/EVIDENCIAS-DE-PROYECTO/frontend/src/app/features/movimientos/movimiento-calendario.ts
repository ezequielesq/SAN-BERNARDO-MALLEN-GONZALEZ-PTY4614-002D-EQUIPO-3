import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import {
  DIAS_ES,
  DiaCalendario,
  MESES_ES,
  construirDiasDelMes,
  formatearFecha,
} from '../../shared/utils/calendario';
import { MovimientoService } from './movimiento.service';

@Component({
  selector: 'app-movimiento-calendario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-calendario.html',
  styleUrl: './movimiento-calendario.css',
})
export class MovimientoCalendario {
  private readonly movimientoService = inject(MovimientoService);

  readonly productoId = input<number | null>(null);
  readonly diaActivo = input<string | null>(null);
  readonly diaSeleccionado = output<string | null>();

  protected readonly DIAS_ES = DIAS_ES;

  private readonly hoy = new Date();
  protected readonly anioActual = signal(this.hoy.getFullYear());
  protected readonly mesActual = signal(this.hoy.getMonth());

  protected readonly etiquetaMes = computed(
    () => `${MESES_ES[this.mesActual()]} ${this.anioActual()}`,
  );

  protected readonly diasDelMes = computed<DiaCalendario[]>(() =>
    construirDiasDelMes(this.anioActual(), this.mesActual()),
  );

  private readonly rangoMes = computed(() => {
    const anio = this.anioActual();
    const mes = this.mesActual();
    const ultimoDia = new Date(anio, mes + 1, 0).getDate();
    return {
      productoId: this.productoId(),
      desde: formatearFecha(anio, mes, 1),
      hasta: formatearFecha(anio, mes, ultimoDia),
    };
  });

  protected readonly movimientos = this.movimientoService.listar(this.rangoMes);

  protected readonly resumenPorDia = computed(() => {
    const mapa = new Map<string, { entrada: boolean; salida: boolean }>();
    if (!this.movimientos.hasValue()) return mapa;
    for (const m of this.movimientos.value()) {
      const fecha = m.fecha.slice(0, 10);
      const actual = mapa.get(fecha) ?? { entrada: false, salida: false };
      if (m.sentido > 0) actual.entrada = true;
      else actual.salida = true;
      mapa.set(fecha, actual);
    }
    return mapa;
  });

  protected cambiarMes(delta: number): void {
    const fecha = new Date(this.anioActual(), this.mesActual() + delta, 1);
    this.anioActual.set(fecha.getFullYear());
    this.mesActual.set(fecha.getMonth());
  }

  protected seleccionarDia(fecha: string): void {
    const nueva = this.diaActivo() === fecha ? null : fecha;
    this.diaSeleccionado.emit(nueva);
  }
}

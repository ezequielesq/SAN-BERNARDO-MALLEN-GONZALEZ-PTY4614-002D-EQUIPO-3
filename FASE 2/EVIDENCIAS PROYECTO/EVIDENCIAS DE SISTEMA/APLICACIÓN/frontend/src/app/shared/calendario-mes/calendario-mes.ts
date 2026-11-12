import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
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
  rangoDelMes,
} from '../utils/calendario';

/** Tipo de marca que puede aparecer en un día; define su punto, su leyenda y su texto para lectores de pantalla. */
export interface MarcaCalendario {
  clave: string;
  etiqueta: string;
  color: string;
  descripcion: string;
}

/** Calendario mensual presentacional: el padre aporta las marcas por día y recibe el rango visible y el día elegido. */
@Component({
  selector: 'app-calendario-mes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './calendario-mes.html',
  styleUrl: './calendario-mes.css',
})
export class CalendarioMes {
  readonly marcas = input<ReadonlyMap<string, readonly string[]>>(new Map());
  readonly leyenda = input.required<readonly MarcaCalendario[]>();
  readonly diaActivo = input<string | null>(null);
  readonly cargando = input(false);

  readonly diaSeleccionado = output<string | null>();
  readonly rangoCambiado = output<{ desde: string; hasta: string }>();

  protected readonly DIAS_ES = DIAS_ES;

  private readonly hoy = new Date();
  protected readonly fechaHoy = formatearFecha(
    this.hoy.getFullYear(),
    this.hoy.getMonth(),
    this.hoy.getDate(),
  );
  protected readonly anioActual = signal(this.hoy.getFullYear());
  protected readonly mesActual = signal(this.hoy.getMonth());

  protected readonly etiquetaMes = computed(
    () => `${MESES_ES[this.mesActual()]} ${this.anioActual()}`,
  );

  protected readonly diasDelMes = computed<DiaCalendario[]>(() =>
    construirDiasDelMes(this.anioActual(), this.mesActual()),
  );

  private readonly rangoMes = computed(() => rangoDelMes(this.anioActual(), this.mesActual()));

  /** Por cada día con marcas, las del catálogo de la leyenda en el orden de la leyenda. */
  protected readonly marcasPorDia = computed(() => {
    const resultado = new Map<string, readonly MarcaCalendario[]>();
    const leyenda = this.leyenda();
    for (const [fecha, claves] of this.marcas()) {
      const presentes = leyenda.filter((m) => claves.includes(m.clave));
      if (presentes.length > 0) resultado.set(fecha, presentes);
    }
    return resultado;
  });

  constructor() {
    effect(() => this.rangoCambiado.emit(this.rangoMes()));
  }

  protected cambiarMes(delta: number): void {
    const fecha = new Date(this.anioActual(), this.mesActual() + delta, 1);
    this.anioActual.set(fecha.getFullYear());
    this.mesActual.set(fecha.getMonth());
  }

  protected seleccionarDia(fecha: string): void {
    this.diaSeleccionado.emit(this.diaActivo() === fecha ? null : fecha);
  }
}

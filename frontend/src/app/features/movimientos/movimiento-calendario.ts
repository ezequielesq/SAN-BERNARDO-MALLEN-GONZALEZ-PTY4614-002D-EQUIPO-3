import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { MovimientoService } from './movimiento.service';

interface DiaSemana {
  fecha: string;
  dia: number;
  etiqueta: string;
}

const DIAS_ES = ['Lu', 'Ma', 'Mi', 'Ju', 'Vi', 'Sa', 'Do'];

function formatearFecha(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

function inicioDeSemana(fecha: Date): Date {
  const copia = new Date(fecha);
  const dow = copia.getDay();
  const offset = dow === 0 ? -6 : 1 - dow;
  copia.setDate(copia.getDate() + offset);
  return copia;
}

@Component({
  selector: 'app-movimiento-calendario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-calendario.html',
  styleUrl: './movimiento-calendario.css',
})
export class MovimientoCalendario {
  private readonly movimientoService = inject(MovimientoService);

  readonly productoId = input<number | null>(null);
  readonly diaSeleccionado = output<string | null>();

  protected readonly inicioSemana = signal(inicioDeSemana(new Date()));
  protected readonly diaActivo = signal<string | null>(null);

  protected readonly diasSemana = computed<DiaSemana[]>(() => {
    const inicio = this.inicioSemana();
    return DIAS_ES.map((etiqueta, i) => {
      const fecha = new Date(inicio);
      fecha.setDate(fecha.getDate() + i);
      return { fecha: formatearFecha(fecha), dia: fecha.getDate(), etiqueta };
    });
  });

  protected readonly etiquetaSemana = computed(() => {
    const dias = this.diasSemana();
    return `${dias[0].dia} — ${dias[6].dia}`;
  });

  private readonly filtroSemana = computed(() => {
    const dias = this.diasSemana();
    return { productoId: this.productoId(), desde: dias[0].fecha, hasta: dias[6].fecha };
  });

  protected readonly movimientos = this.movimientoService.listar(this.filtroSemana);

  protected readonly resumenPorDia = computed(() => {
    const mapa = new Map<string, { entrada: boolean; salida: boolean }>();
    if (!this.movimientos.hasValue()) return mapa;
    for (const m of this.movimientos.value()) {
      const fecha = m.fecha.slice(0, 10);
      const actual = mapa.get(fecha) ?? { entrada: false, salida: false };
      if (m.tipo === 'ENTRADA') actual.entrada = true;
      else actual.salida = true;
      mapa.set(fecha, actual);
    }
    return mapa;
  });

  protected cambiarSemana(delta: number): void {
    const nueva = new Date(this.inicioSemana());
    nueva.setDate(nueva.getDate() + delta * 7);
    this.inicioSemana.set(nueva);
  }

  protected seleccionarDia(fecha: string): void {
    const nueva = this.diaActivo() === fecha ? null : fecha;
    this.diaActivo.set(nueva);
    this.diaSeleccionado.emit(nueva);
  }
}

import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Modal } from '../../shared/modal/modal';
import {
  DIAS_ES,
  DiaCalendario,
  MESES_ES,
  construirDiasDelMes,
  formatearFecha,
} from '../../shared/utils/calendario';
import { EventoFormulario } from './evento-formulario';
import { Evento } from './evento.modelo';
import { EventoService } from './evento.service';

@Component({
  selector: 'app-calendario-pagina',
  imports: [EstadoVista, Modal, EventoFormulario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './calendario-pagina.html',
  styleUrl: './calendario-pagina.css',
})
export class CalendarioPagina {
  private readonly eventoService = inject(EventoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly DIAS_ES = DIAS_ES;

  private readonly hoy = new Date();
  protected readonly fechaHoy = formatearFecha(
    this.hoy.getFullYear(),
    this.hoy.getMonth(),
    this.hoy.getDate(),
  );

  protected readonly anio = signal(this.hoy.getFullYear());
  protected readonly mes = signal(this.hoy.getMonth());
  /** Día (yyyy-MM-dd) que filtra la lista; `null` = todos los del mes. */
  protected readonly diaActivo = signal<string | null>(null);
  protected readonly modalEvento = signal<'nuevo' | Evento | null>(null);
  protected readonly procesandoId = signal<number | null>(null);

  protected readonly etiquetaMes = computed(() => `${MESES_ES[this.mes()]} ${this.anio()}`);

  protected readonly diasDelMes = computed<DiaCalendario[]>(() =>
    construirDiasDelMes(this.anio(), this.mes()),
  );

  private readonly rangoMes = computed(() => {
    const anio = this.anio();
    const mes = this.mes();
    return {
      desde: formatearFecha(anio, mes, 1),
      hasta: formatearFecha(anio, mes, new Date(anio, mes + 1, 0).getDate()),
    };
  });

  protected readonly eventos = this.eventoService.listar(this.rangoMes);

  protected readonly eventosDelMes = computed<Evento[]>(() =>
    this.eventos.hasValue() ? this.eventos.value() : [],
  );

  protected readonly eventosPorDia = computed(() => {
    const mapa = new Map<string, number>();
    for (const e of this.eventosDelMes()) mapa.set(e.fecha, (mapa.get(e.fecha) ?? 0) + 1);
    return mapa;
  });

  /** El servidor ya los entrega ordenados por fecha y hora. */
  protected readonly eventosVisibles = computed(() => {
    const dia = this.diaActivo();
    const todos = this.eventosDelMes();
    return dia === null ? todos : todos.filter((e) => e.fecha === dia);
  });

  protected readonly tituloLista = computed(() => {
    const dia = this.diaActivo();
    if (dia === null) return `Eventos de ${this.etiquetaMes()}`;
    const [a, m, d] = dia.split('-').map(Number);
    const largo = new Date(a, m - 1, d).toLocaleDateString('es-CL', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    });
    return `Eventos del ${largo}`;
  });

  protected readonly tituloModal = computed(() =>
    this.modalEvento() === 'nuevo' ? 'Nuevo evento' : 'Editar evento',
  );
  protected readonly eventoModal = computed<Evento | null>(() => {
    const m = this.modalEvento();
    return m === 'nuevo' || m === null ? null : m;
  });
  protected readonly fechaInicialModal = computed(() => this.diaActivo() ?? this.fechaHoy);

  protected cambiarMes(delta: number): void {
    const fecha = new Date(this.anio(), this.mes() + delta, 1);
    this.irAMes(fecha.getFullYear(), fecha.getMonth());
  }

  protected irAHoy(): void {
    this.irAMes(this.hoy.getFullYear(), this.hoy.getMonth());
  }

  protected alternarDia(fecha: string): void {
    this.diaActivo.update((actual) => (actual === fecha ? null : fecha));
  }

  protected quitarFiltro(): void {
    this.diaActivo.set(null);
  }

  protected horaCorta(e: Evento): string {
    return e.hora === null ? 'Todo el día' : e.hora.slice(0, 5);
  }

  protected fechaCorta(e: Evento): string {
    return e.fecha.split('-').reverse().join('-');
  }

  protected conteo(fecha: string): number {
    return this.eventosPorDia().get(fecha) ?? 0;
  }

  protected alGuardarEvento(): void {
    this.eventos.reload();
  }

  protected cerrarModal(): void {
    this.modalEvento.set(null);
  }

  protected eliminar(e: Evento): void {
    if (this.procesandoId() !== null) return;
    this.confirmacion
      .confirmar({
        titulo: `¿Eliminar el evento «${e.titulo}»?`,
        descripcion: 'El evento se quitará del calendario de todos los bodegueros. No se puede deshacer.',
        textoConfirmar: 'Eliminar evento',
      })
      .pipe(
        filter((confirmado) => confirmado),
        switchMap(() => {
          this.procesandoId.set(e.id);
          return this.eventoService.eliminar(e.id);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.procesandoId.set(null);
          this.notificaciones.exito(`Evento '${e.titulo}' eliminado.`);
          this.eventos.reload();
        },
        error: (err: unknown) => {
          this.procesandoId.set(null);
          this.notificaciones.error(traducirError(err).mensaje);
        },
      });
  }

  private irAMes(anio: number, mes: number): void {
    this.anio.set(anio);
    this.mes.set(mes);
    this.diaActivo.set(null);
  }
}

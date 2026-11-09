import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { CalendarioMes, MarcaCalendario } from '../../shared/calendario-mes/calendario-mes';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Modal } from '../../shared/modal/modal';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { ThOrden } from '../../shared/ordenamiento/th-orden';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { formatearFecha, rangoDelMes } from '../../shared/utils/calendario';
import { EventoFormulario } from './evento-formulario';
import { Evento } from './evento.modelo';
import { EventoService } from './evento.service';

type ColumnaOrdenEvento = 'fecha' | 'hora' | 'titulo' | 'descripcion' | 'creadoPorNombre';

const EXTRACTORES_EVENTO: Extractores<Evento, ColumnaOrdenEvento> = {
  fecha: (e) => e.fecha,
  hora: (e) => e.hora,
  titulo: (e) => e.titulo,
  descripcion: (e) => e.descripcion,
  creadoPorNombre: (e) => e.creadoPorNombre,
};

const LEYENDA: readonly MarcaCalendario[] = [
  { clave: 'evento', etiqueta: 'Evento', color: '#6a00ff', descripcion: 'con eventos' },
];

/** El servidor rechaza rangos de más de este número de días (ambos extremos incluidos). */
const MAXIMO_DIAS = 366;
const MS_POR_DIA = 24 * 60 * 60 * 1000;

function aTiempoUtc(fecha: string): number {
  const [a, m, d] = fecha.split('-').map(Number);
  // setUTCFullYear evita que Date.UTC interprete los años 0-99 como 1900-1999.
  const f = new Date(0);
  f.setUTCFullYear(a, m - 1, d);
  return f.getTime();
}

/** Minúsculas y sin tildes, para buscar sin distinguir mayúsculas ni acentos. */
function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

@Component({
  selector: 'app-calendario-pagina',
  imports: [EstadoVista, Modal, EventoFormulario, CalendarioMes, Paginacion, ThOrden],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './calendario-pagina.html',
})
export class CalendarioPagina {
  private readonly eventoService = inject(EventoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly leyenda = LEYENDA;

  private readonly hoy = new Date();
  private readonly fechaHoy = formatearFecha(
    this.hoy.getFullYear(),
    this.hoy.getMonth(),
    this.hoy.getDate(),
  );

  /** Mes que muestra el calendario; lo actualiza el propio calendario al navegar. */
  protected readonly rangoMes = signal(rangoDelMes(this.hoy.getFullYear(), this.hoy.getMonth()));

  protected readonly desdeFiltro = signal(this.rangoMes().desde);
  protected readonly hastaFiltro = signal(this.rangoMes().hasta);
  protected readonly busqueda = signal('');
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = signal(10);
  protected readonly orden = new Orden<ColumnaOrdenEvento>(null, () => this.pagina.set(1));

  protected readonly modalEvento = signal<'nuevo' | Evento | null>(null);
  protected readonly procesandoId = signal<number | null>(null);

  /** Día elegido en el calendario: existe solo cuando Desde y Hasta son el mismo día. */
  protected readonly diaActivo = computed(() => {
    const desde = this.desdeFiltro();
    return desde !== '' && desde === this.hastaFiltro() ? desde : null;
  });

  /** Mensaje si el rango escrito no se puede consultar; `null` si es válido. */
  protected readonly errorRango = computed<string | null>(() => {
    const desde = this.desdeFiltro();
    const hasta = this.hastaFiltro();
    if (desde === '' || hasta === '') return 'Indica las fechas «Desde» y «Hasta».';
    const dias = (aTiempoUtc(hasta) - aTiempoUtc(desde)) / MS_POR_DIA;
    if (dias < 0) return 'La fecha «Desde» no puede ser posterior a «Hasta».';
    if (dias + 1 > MAXIMO_DIAS) return `El rango no puede superar ${MAXIMO_DIAS} días.`;
    return null;
  });

  /** Eventos del rango Desde..Hasta; con un rango inválido no se consulta al servidor. */
  protected readonly eventos = this.eventoService.listar(() =>
    this.errorRango() === null ? { desde: this.desdeFiltro(), hasta: this.hastaFiltro() } : undefined,
  );
  protected readonly todos = computed(() => (this.eventos.hasValue() ? this.eventos.value() : []));

  /** Eventos del mes visible, solo para los puntos del calendario (independiente de la lista). */
  protected readonly eventosMes = this.eventoService.listar(this.rangoMes);

  protected readonly marcasPorDia = computed(() => {
    const mapa = new Map<string, string[]>();
    if (!this.eventosMes.hasValue()) return mapa;
    for (const e of this.eventosMes.value()) mapa.set(e.fecha, ['evento']);
    return mapa;
  });

  protected readonly filtrados = computed(() => {
    const texto = normalizar(this.busqueda().trim());
    if (texto === '') return this.todos();
    return this.todos().filter((e) => normalizar(`${e.titulo} ${e.descripcion ?? ''}`).includes(texto));
  });

  protected readonly ordenados = computed(() => ordenar(this.filtrados(), this.orden, EXTRACTORES_EVENTO));

  /** La página mostrada nunca pasa de la última: al borrar o editar eventos la lista puede encogerse. */
  protected readonly paginaActual = computed(() =>
    Math.min(this.pagina(), Math.max(1, Math.ceil(this.ordenados().length / this.tamanoPagina()))),
  );

  protected readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.ordenados().slice(inicio, inicio + this.tamanoPagina());
  });

  protected readonly tituloModal = computed(() =>
    this.modalEvento() === 'nuevo' ? 'Nuevo evento' : 'Editar evento',
  );
  protected readonly eventoModal = computed<Evento | null>(() => {
    const m = this.modalEvento();
    return m === 'nuevo' || m === null ? null : m;
  });
  protected readonly fechaInicialModal = computed(() => this.diaActivo() ?? this.fechaHoy);

  protected alCambiarRangoCalendario(nuevo: { desde: string; hasta: string }): void {
    const actual = this.rangoMes();
    if (actual.desde !== nuevo.desde || actual.hasta !== nuevo.hasta) this.rangoMes.set(nuevo);
    this.desdeFiltro.set(nuevo.desde);
    this.hastaFiltro.set(nuevo.hasta);
    this.pagina.set(1);
  }

  protected alSeleccionarDiaCalendario(fecha: string | null): void {
    const { desde, hasta } = fecha === null ? this.rangoMes() : { desde: fecha, hasta: fecha };
    this.desdeFiltro.set(desde);
    this.hastaFiltro.set(hasta);
    this.pagina.set(1);
  }

  protected cambiarDesde(valor: string): void {
    this.desdeFiltro.set(valor);
    this.pagina.set(1);
  }

  protected cambiarHasta(valor: string): void {
    this.hastaFiltro.set(valor);
    this.pagina.set(1);
  }

  protected cambiarBusqueda(valor: string): void {
    this.busqueda.set(valor);
    this.pagina.set(1);
  }

  protected limpiarFiltrosCliente(): void {
    this.busqueda.set('');
    this.pagina.set(1);
  }

  protected cambiarPagina(pagina: number): void {
    this.pagina.set(pagina);
  }

  protected cambiarTamanoPagina(tamano: number): void {
    this.tamanoPagina.set(tamano);
    this.pagina.set(1);
  }

  protected horaCorta(e: Evento): string {
    return e.hora === null ? 'Todo el día' : e.hora.slice(0, 5);
  }

  protected fechaCorta(e: Evento): string {
    return e.fecha.split('-').reverse().join('-');
  }

  protected alGuardarEvento(): void {
    this.recargar();
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
          this.recargar();
        },
        error: (err: unknown) => {
          this.procesandoId.set(null);
          this.notificaciones.error(traducirError(err).mensaje);
        },
      });
  }

  /** Vuelve a pedir la lista y los puntos del calendario. */
  private recargar(): void {
    this.eventos.reload();
    this.eventosMes.reload();
  }
}

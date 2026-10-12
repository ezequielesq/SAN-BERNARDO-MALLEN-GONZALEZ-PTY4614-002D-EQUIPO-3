import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { EstadoBadge, TipoEstado } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { OpcionOrden, SelectorOrden } from '../../shared/ordenamiento/selector-orden';
import { ThOrden } from '../../shared/ordenamiento/th-orden';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { AprobarSolicitudRequest, DetalleSolicitud, EstadoSolicitud, Solicitud } from './solicitud.modelo';
import { SolicitudService } from './solicitud.service';

type Vista = 'pendientes' | 'todas';
type ColumnaOrdenSolicitud = 'id' | 'fecha' | 'solicitanteNombre' | 'tipo' | 'estado' | 'items';

const EXTRACTORES_SOLICITUD: Extractores<Solicitud, ColumnaOrdenSolicitud> = {
  id: (s) => s.id,
  fecha: (s) => new Date(s.fecha).getTime(),
  solicitanteNombre: (s) => s.solicitanteNombre,
  tipo: (s) => s.tipo,
  estado: (s) => s.estado,
  items: (s) => s.detalles.length,
};

const OPCIONES_PENDIENTES: readonly OpcionOrden[] = [
  { valor: 'fecha', etiqueta: 'Fecha' },
  { valor: 'solicitanteNombre', etiqueta: 'Solicitante' },
  { valor: 'id', etiqueta: 'Número' },
];

@Component({
  selector: 'app-solicitud-lista',
  imports: [DatePipe, EstadoVista, EstadoBadge, Paginacion, ThOrden, SelectorOrden],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './solicitud-lista.html',
})
export class SolicitudLista {
  private readonly solicitudService = inject(SolicitudService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  protected readonly puedeGestionar = computed(() => this.auth.rol() === 'BODEGUERO');

  protected readonly vista = signal<Vista>(this.puedeGestionar() ? 'pendientes' : 'todas');
  protected readonly desdeFiltro = signal<string | null>(null);
  protected readonly hastaFiltro = signal<string | null>(null);
  protected readonly paginaActual = signal(1);
  protected readonly tamanoPagina = signal(10);
  protected readonly expandidoId = signal<number | null>(null);
  protected readonly cantidadesEditadas = signal(new Map<number, number>());
  protected readonly guardandoId = signal<number | null>(null);

  private readonly filtro = computed(() => ({ desde: this.desdeFiltro(), hasta: this.hastaFiltro() }));

  protected readonly pendientes = this.solicitudService.listarPendientes();
  protected readonly todasLista = this.solicitudService.listar(this.filtro);

  protected readonly listaPendientes = computed(() =>
    this.pendientes.hasValue() ? this.pendientes.value() : [],
  );
  protected readonly listaTodas = computed(() => (this.todasLista.hasValue() ? this.todasLista.value() : []));

  protected readonly opcionesPendientes = OPCIONES_PENDIENTES;
  protected readonly paginaPendientes = signal(1);
  protected readonly tamanoPendientes = signal(9);
  protected readonly opcionesTamanoPendientes = [6, 9, 12, 24] as const;
  protected readonly ordenPendientes = new Orden<ColumnaOrdenSolicitud>({ columna: 'fecha' }, () =>
    this.paginaPendientes.set(1),
  );
  protected readonly orden = new Orden<ColumnaOrdenSolicitud>(null, () => this.paginaActual.set(1));

  protected readonly pendientesOrdenadas = computed(() =>
    ordenar(this.listaPendientes(), this.ordenPendientes, EXTRACTORES_SOLICITUD),
  );
  /** Al aprobar o rechazar la última tarjeta de una página, la lista queda en la página anterior. */
  protected readonly paginaPendientesActual = computed(() =>
    Math.min(this.paginaPendientes(), Math.max(1, Math.ceil(this.listaPendientes().length / this.tamanoPendientes()))),
  );
  protected readonly pendientesPaginadas = computed(() => {
    const inicio = (this.paginaPendientesActual() - 1) * this.tamanoPendientes();
    return this.pendientesOrdenadas().slice(inicio, inicio + this.tamanoPendientes());
  });
  protected readonly ordenadas = computed(() => ordenar(this.listaTodas(), this.orden, EXTRACTORES_SOLICITUD));

  protected readonly paginadas = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.ordenadas().slice(inicio, inicio + this.tamanoPagina());
  });

  protected badgeTipo(s: Solicitud): TipoEstado {
    return s.tipo === 'DEVOLUCION' ? 'devolucion' : 'pedido';
  }

  protected cambiarDesde(valor: string): void {
    this.desdeFiltro.set(valor === '' ? null : valor);
    this.paginaActual.set(1);
  }

  protected cambiarHasta(valor: string): void {
    this.hastaFiltro.set(valor === '' ? null : valor);
    this.paginaActual.set(1);
  }

  protected cambiarPaginaPendientes(pagina: number): void {
    this.paginaPendientes.set(pagina);
  }

  protected cambiarTamanoPendientes(tamano: number): void {
    this.tamanoPendientes.set(tamano);
    this.paginaPendientes.set(1);
  }

  protected cambiarPagina(pagina: number): void {
    this.paginaActual.set(pagina);
  }

  protected cambiarTamanoPagina(tamano: number): void {
    this.tamanoPagina.set(tamano);
    this.paginaActual.set(1);
  }

  protected alternarExpandido(id: number): void {
    this.expandidoId.set(this.expandidoId() === id ? null : id);
  }

  protected estadoBadge(estado: EstadoSolicitud): TipoEstado {
    return estado.toLowerCase() as TipoEstado;
  }

  protected cantidadEditada(detalle: DetalleSolicitud): number {
    return this.cantidadesEditadas().get(detalle.detalleId) ?? detalle.cantidadSolicitada;
  }

  protected actualizarCantidad(detalleId: number, valor: string): void {
    const numero = Number(valor);
    if (!Number.isInteger(numero) || numero < 0) return;
    const mapa = new Map(this.cantidadesEditadas());
    mapa.set(detalleId, numero);
    this.cantidadesEditadas.set(mapa);
  }

  protected aprobar(s: Solicitud): void {
    if (this.guardandoId() !== null) return;
    this.confirmacion
      .confirmar({
        titulo: `¿Aprobar solicitud #${s.id}?`,
        descripcion:
          s.tipo === 'DEVOLUCION'
            ? 'Se repondrá en el stock lo devuelto de cada producto. Esta acción no se puede deshacer.'
            : 'Se descontará el stock entregado de cada producto. Esta acción no se puede deshacer.',
        textoConfirmar: 'Aprobar solicitud',
      })
      .pipe(
        filter((confirmado) => confirmado),
        switchMap(() => {
          this.guardandoId.set(s.id);
          const request: AprobarSolicitudRequest = {
            items: s.detalles.map((d) => ({
              detalleId: d.detalleId,
              cantidadEntregada: this.cantidadEditada(d),
            })),
          };
          return this.solicitudService.aprobar(s.id, request);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.guardandoId.set(null);
          this.notificaciones.exito(`Solicitud #${s.id} aprobada.`);
          this.pendientes.reload();
        },
        error: (err: unknown) => {
          this.guardandoId.set(null);
          this.notificaciones.error(traducirError(err).mensaje);
        },
      });
  }

  protected rechazar(s: Solicitud): void {
    if (this.guardandoId() !== null) return;
    this.confirmacion
      .confirmar({
        titulo: `¿Rechazar solicitud #${s.id}?`,
        descripcion: 'Esta acción no se puede deshacer.',
        textoConfirmar: 'Rechazar solicitud',
      })
      .pipe(
        filter((confirmado) => confirmado),
        switchMap(() => {
          this.guardandoId.set(s.id);
          return this.solicitudService.rechazar(s.id);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.guardandoId.set(null);
          this.notificaciones.exito(`Solicitud #${s.id} rechazada.`);
          this.pendientes.reload();
        },
        error: (err: unknown) => {
          this.guardandoId.set(null);
          this.notificaciones.error(traducirError(err).mensaje);
        },
      });
  }
}

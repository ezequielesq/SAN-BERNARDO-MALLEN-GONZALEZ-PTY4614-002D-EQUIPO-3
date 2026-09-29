import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { EstadoBadge, TipoEstado } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { AprobarSolicitudRequest, DetalleSolicitud, EstadoSolicitud, Solicitud } from './solicitud.modelo';
import { SolicitudService } from './solicitud.service';

type Vista = 'pendientes' | 'todas';

@Component({
  selector: 'app-solicitud-lista',
  imports: [RouterLink, DatePipe, EstadoVista, EstadoBadge, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './solicitud-lista.html',
})
export class SolicitudLista {
  private readonly solicitudService = inject(SolicitudService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  protected readonly puedeGestionar = computed(() => {
    const rol = this.auth.rol();
    return rol === 'ADMIN' || rol === 'BODEGUERO';
  });

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

  protected readonly paginadas = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.listaTodas().slice(inicio, inicio + this.tamanoPagina());
  });

  protected cambiarDesde(valor: string): void {
    this.desdeFiltro.set(valor === '' ? null : valor);
    this.paginaActual.set(1);
  }

  protected cambiarHasta(valor: string): void {
    this.hastaFiltro.set(valor === '' ? null : valor);
    this.paginaActual.set(1);
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
          'Se descontará el stock entregado de cada producto. Esta acción no se puede deshacer.',
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

import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { AnaliticaService } from './analitica.service';
import { EstadoFiltros } from './panel-estado';
import { MAXIMO_PRODUCTOS } from './panel-filtros';

/** Botones de la lista personal de productos clave (se guarda en el servidor, por usuario). */
@Component({
  selector: 'app-analitica-productos-clave',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-productos-clave.html',
})
export class AnaliticaProductosClave {
  readonly estado = input.required<EstadoFiltros>();

  private readonly service = inject(AnaliticaService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly claves = this.service.productosClave();
  protected readonly guardando = signal(false);
  protected readonly mensajeError = signal<string | null>(null);

  /** Marca en el filtro los productos guardados; si no hay ninguno, invita a guardar una lista. */
  protected usarMisProductos(): void {
    if (this.claves.error()) {
      this.mensajeError.set(traducirError(this.claves.error()).mensaje);
      return;
    }
    this.mensajeError.set(null);
    const ids = this.claves.hasValue() ? this.claves.value().map((c) => c.productoId) : [];
    if (ids.length === 0) {
      this.notificaciones.error(
        'Aún no tienes productos clave. Elige productos en el filtro y usa «Guardar selección como mis productos clave».',
      );
      return;
    }
    this.estado().productoIds.set(ids.slice(0, MAXIMO_PRODUCTOS));
  }

  protected guardarSeleccion(): void {
    const ids = this.estado().productoIds();
    if (ids.length === 0) {
      this.notificaciones.error('Elige primero los productos en el filtro para guardarlos como productos clave.');
      return;
    }
    if (this.guardando()) return;
    this.guardando.set(true);
    this.mensajeError.set(null);
    this.service
      .guardarProductosClave(ids)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.claves.reload();
          this.notificaciones.exito('Guardaste tu selección como productos clave.');
        },
        error: (err: unknown) => {
          this.guardando.set(false);
          this.mensajeError.set(traducirError(err).mensaje);
        },
      });
  }

  protected reintentar(): void {
    this.mensajeError.set(null);
    this.claves.reload();
  }
}

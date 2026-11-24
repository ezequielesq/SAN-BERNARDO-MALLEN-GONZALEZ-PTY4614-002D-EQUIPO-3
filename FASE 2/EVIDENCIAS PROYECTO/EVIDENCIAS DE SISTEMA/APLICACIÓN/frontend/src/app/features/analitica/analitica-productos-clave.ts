import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { AnaliticaService } from './analitica.service';
import { EstadoFiltros } from './panel-estado';
import { MAXIMO_PRODUCTOS, conservarDeCategoria } from './panel-filtros';

/** Botones de la lista personal de productos clave (se guarda en el servidor, por usuario). */
@Component({
  selector: 'app-analitica-productos-clave',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-productos-clave.html',
})
export class AnaliticaProductosClave {
  readonly estado = input.required<EstadoFiltros>();
  /** Productos con su categoría (nulo mientras cargan); sirven para no marcar los de otra categoría. */
  readonly productos = input.required<readonly { id: number; categoriaId: number }[] | null>();

  private readonly service = inject(AnaliticaService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly confirmacion = inject(ConfirmacionService);
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
    // Con una categoría elegida, los productos de otra no se verían en la lista pero seguirían filtrando (panel vacío).
    const categoriaId = this.estado().categoriaId();
    const productos = this.productos();
    const aplicables = productos === null ? ids : conservarDeCategoria(ids, productos, categoriaId);
    if (aplicables.length === 0) {
      this.notificaciones.error(
        'Ninguno de tus productos clave está en la categoría elegida (o ya no existe).',
      );
      return;
    }
    this.estado().productoIds.set(aplicables.slice(0, MAXIMO_PRODUCTOS));
  }

  protected guardarSeleccion(): void {
    const ids = this.estado().productoIds();
    if (this.guardando()) return;
    if (ids.length === 0) {
      // Una selección vacía es una orden de vaciar la lista: se pide confirmación antes de borrarla.
      this.confirmacion
        .confirmar({
          titulo: '¿Vaciar tus productos clave?',
          descripcion:
            'No hay productos elegidos en el filtro: se guardará una lista vacía y se quitarán los productos clave que tienes guardados.',
          textoConfirmar: 'Vaciar lista',
        })
        .pipe(
          filter((confirmado) => confirmado),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe(() => this.guardar(ids, 'Vaciaste tu lista de productos clave.'));
      return;
    }
    this.guardar(ids, 'Guardaste tu selección como productos clave.');
  }

  private guardar(ids: number[], mensajeExito: string): void {
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
          this.notificaciones.exito(mensajeExito);
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

import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { DetalleSolicitud, Solicitud } from '../solicitudes/solicitud.modelo';
import { EmpleadoService } from './empleado.service';

@Component({
  selector: 'app-empleado-devolucion',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './empleado-devolucion.html',
})
export class EmpleadoDevolucion {
  readonly solicitud = input.required<Solicitud>();
  readonly empleadoId = input.required<number>();
  /** Emite la contraseña escrita, solo para que el padre refresque la lista; no se guarda. */
  readonly devuelto = output<string>();
  readonly cancelar = output<void>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly service = inject(EmpleadoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly detalles = computed(() =>
    this.solicitud().detalles.filter((d) => (d.devolvible ?? 0) > 0),
  );
  protected readonly cantidades = signal<Record<number, number>>({});
  protected readonly password = this.fb.control('', Validators.required);
  protected readonly total = computed(() =>
    this.detalles().reduce((suma, d) => suma + Math.max(0, this.cantidadDe(d.detalleId)), 0),
  );

  protected readonly intento = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  protected cantidadDe(detalleId: number): number {
    return this.cantidades()[detalleId] ?? 0;
  }

  protected actualizar(detalleId: number, valor: string): void {
    const numero = valor === '' ? 0 : Number(valor);
    this.cantidades.update((c) => ({ ...c, [detalleId]: Number.isFinite(numero) ? numero : -1 }));
  }

  protected errorCantidad(d: DetalleSolicitud): string | null {
    const n = this.cantidadDe(d.detalleId);
    if (!Number.isInteger(n) || n < 0) return 'Ingresa un número entero de 0 o más.';
    if (n > (d.devolvible ?? 0)) return `Puedes devolver hasta ${d.devolvible ?? 0}.`;
    return null;
  }

  protected devolver(): void {
    if (this.guardando()) return;
    this.intento.set(true);
    this.errorGeneral.set(null);
    if (this.password.invalid) {
      this.password.markAsTouched();
      this.enfocar('devolver-password');
      return;
    }
    if (this.detalles().some((d) => this.errorCantidad(d) !== null) || this.total() === 0) return;

    const items = this.detalles()
      .map((d) => ({ detalleId: d.detalleId, cantidad: this.cantidadDe(d.detalleId) }))
      .filter((i) => i.cantidad > 0);
    const password = this.password.value;

    this.guardando.set(true);
    this.service
      .devolver({
        empleadoId: this.empleadoId(),
        password,
        solicitudOrigenId: this.solicitud().id,
        items,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.notificaciones.exito('Devolución enviada. El bodeguero la revisará.');
          this.devuelto.emit(password);
        },
        error: (err: unknown) => {
          this.guardando.set(false);
          this.errorGeneral.set(traducirError(err).mensaje);
          this.password.reset('');
          this.intento.set(false);
          this.enfocar('devolucion-error');
        },
      });
  }

  private enfocar(id: string): void {
    afterNextRender(() => document.getElementById(id)?.focus(), { injector: this.injector });
  }
}

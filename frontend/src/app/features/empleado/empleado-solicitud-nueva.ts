import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { EmpleadoService } from './empleado.service';

interface ItemCarrito {
  productoId: number;
  productoNombre: string;
  cantidad: number;
}

@Component({
  selector: 'app-empleado-solicitud-nueva',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './empleado-solicitud-nueva.html',
})
export class EmpleadoSolicitudNueva {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly service = inject(EmpleadoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly empleados = this.service.empleados();
  protected readonly opcionesEmpleado = computed(() =>
    this.empleados.hasValue() ? this.empleados.value() : [],
  );
  protected readonly productos = this.service.productos();
  protected readonly opcionesProducto = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

  protected readonly formIdentidad = this.fb.group({
    empleadoId: this.fb.control<number | null>(null, Validators.required),
    password: ['', Validators.required],
  });

  protected readonly formItem = this.fb.group({
    productoId: this.fb.control<number | null>(null, Validators.required),
    cantidad: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
  });

  protected readonly items = signal<ItemCarrito[]>([]);
  protected readonly intentoAgregar = signal(false);
  protected readonly errorDuplicado = signal(false);
  protected readonly intentoEnviar = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  protected errorIdentidad(campo: 'empleadoId' | 'password'): boolean {
    const control = this.formIdentidad.controls[campo];
    return (control.touched || this.intentoEnviar()) && control.invalid;
  }

  protected agregarItem(): void {
    this.intentoAgregar.set(true);
    this.errorDuplicado.set(false);
    if (this.formItem.invalid) {
      this.formItem.markAllAsTouched();
      this.enfocarPrimerErrorItem();
      return;
    }
    const v = this.formItem.getRawValue();
    if (v.productoId === null || v.cantidad === null) return;
    if (this.items().some((i) => i.productoId === v.productoId)) {
      this.errorDuplicado.set(true);
      return;
    }
    const producto = this.opcionesProducto().find((p) => p.id === v.productoId);
    if (!producto) return;
    this.items.set([
      ...this.items(),
      { productoId: producto.id, productoNombre: producto.nombre, cantidad: v.cantidad },
    ]);
    this.formItem.reset({ productoId: null, cantidad: null });
    this.intentoAgregar.set(false);
  }

  protected quitarItem(productoId: number): void {
    this.items.set(this.items().filter((i) => i.productoId !== productoId));
  }

  protected enviarSolicitud(): void {
    if (this.guardando()) return;
    this.intentoEnviar.set(true);
    this.errorGeneral.set(null);
    if (this.formIdentidad.invalid) {
      this.formIdentidad.markAllAsTouched();
      return;
    }
    if (this.items().length === 0) return;
    const identidad = this.formIdentidad.getRawValue();
    if (identidad.empleadoId === null) return;

    this.guardando.set(true);
    this.service
      .crearSolicitud({
        empleadoId: identidad.empleadoId,
        password: identidad.password,
        items: this.items().map((i) => ({ productoId: i.productoId, cantidad: i.cantidad })),
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.notificaciones.exito('Solicitud enviada.');
          this.items.set([]);
          this.intentoEnviar.set(false);
          this.formIdentidad.controls.password.reset('');
        },
        error: (err: unknown) => {
          this.guardando.set(false);
          this.errorGeneral.set(traducirError(err).mensaje);
          this.formIdentidad.controls.password.reset('');
        },
      });
  }

  private enfocarPrimerErrorItem(): void {
    afterNextRender(
      () => {
        const orden: { id: string; invalido: boolean }[] = [
          { id: 'item-producto', invalido: this.formItem.controls.productoId.invalid },
          { id: 'item-cantidad', invalido: this.formItem.controls.cantidad.invalid },
        ];
        const primero = orden.find((c) => c.invalido);
        if (primero) document.getElementById(primero.id)?.focus();
      },
      { injector: this.injector },
    );
  }
}

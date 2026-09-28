import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ProductoService } from '../productos/producto.service';
import { EntradaRequest } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

@Component({
  selector: 'app-movimiento-formulario-entrada',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-formulario-entrada.html',
})
export class MovimientoFormularioEntrada {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly opcionesProducto = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

  protected readonly form = this.fb.group({
    productoId: this.fb.control<number | null>(null, Validators.required),
    cantidad: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    fechaVencimiento: [''],
    numeroLote: ['', Validators.maxLength(50)],
    motivo: ['', Validators.maxLength(200)],
  });

  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  // form.controls.productoId.value es una lectura de propiedad plana, no de
  // señal: un computed() que la leyera directamente no se recalcularía al
  // cambiar el producto elegido (verificado manualmente: el campo de fecha
  // de vencimiento no aparecía/desaparecía al cambiar de producto tras la
  // carga inicial). Se deriva una señal real desde valueChanges para que
  // productoSeleccionado se recalcule con cada cambio.
  private readonly productoIdSeleccionado = toSignal(this.form.controls.productoId.valueChanges, {
    initialValue: this.form.controls.productoId.value,
  });

  protected readonly productoSeleccionado = computed(() => {
    const id = this.productoIdSeleccionado();
    return this.opcionesProducto().find((p) => p.id === id) ?? null;
  });

  constructor() {
    // La fecha de vencimiento solo es obligatoria cuando el producto elegido
    // es perecible. Si el usuario cambia a un producto no perecible después
    // de haber ingresado una fecha (de un producto perecible previamente
    // seleccionado), esa fecha residual se limpia para que no se envíe por
    // error.
    effect(() => {
      const requerido = this.productoSeleccionado()?.esPerecible === true;
      const control = this.form.controls.fechaVencimiento;
      if (requerido) {
        control.addValidators(Validators.required);
      } else {
        control.removeValidators(Validators.required);
        control.setValue('');
      }
      control.updateValueAndValidity({ emitEvent: false });
    });
  }

  protected guardar(): void {
    if (this.guardando()) return;
    this.enviado.set(true);
    this.errorGeneral.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.enfocarPrimerError();
      return;
    }
    const v = this.form.getRawValue();
    if (v.productoId === null || v.cantidad === null) return;
    const request: EntradaRequest = {
      productoId: v.productoId,
      cantidad: v.cantidad,
      fechaVencimiento: v.fechaVencimiento.trim() === '' ? null : v.fechaVencimiento,
      numeroLote: v.numeroLote.trim() === '' ? null : v.numeroLote.trim(),
      motivo: v.motivo.trim() === '' ? null : v.motivo.trim(),
    };
    this.guardando.set(true);
    this.movimientoService.registrarEntrada(request).subscribe({
      next: () => {
        this.guardando.set(false);
        void this.router
          .navigate(['/movimientos'])
          .then(() => this.notificaciones.exito('Entrada registrada.'));
      },
      error: () => {
        this.guardando.set(false);
        this.errorGeneral.set('No se pudo registrar la entrada. Inténtalo de nuevo.');
      },
    });
  }

  private enfocarPrimerError(): void {
    afterNextRender(
      () => {
        const orden: { id: string; invalido: boolean }[] = [
          { id: 'entrada-producto', invalido: this.form.controls.productoId.invalid },
          { id: 'entrada-cantidad', invalido: this.form.controls.cantidad.invalid },
          { id: 'entrada-vencimiento', invalido: this.form.controls.fechaVencimiento.invalid },
        ];
        const primero = orden.find((c) => c.invalido);
        if (primero) document.getElementById(primero.id)?.focus();
      },
      { injector: this.injector },
    );
  }
}

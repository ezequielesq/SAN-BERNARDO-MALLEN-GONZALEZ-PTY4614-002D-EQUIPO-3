import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { traducirError } from '../../core/errores/traducir-error';
import { ProductoService } from '../productos/producto.service';
import { SalidaRequest } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

@Component({
  selector: 'app-movimiento-formulario-salida',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-formulario-salida.html',
})
export class MovimientoFormularioSalida {
  readonly guardado = output<void>();
  readonly cancelar = output<void>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly injector = inject(Injector);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly opcionesProducto = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

  protected readonly form = this.fb.group({
    productoId: this.fb.control<number | null>(null, Validators.required),
    cantidad: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    motivo: ['', [Validators.required, Validators.maxLength(200)]],
  });

  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

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
    const request: SalidaRequest = { productoId: v.productoId, cantidad: v.cantidad, motivo: v.motivo.trim() };
    this.guardando.set(true);
    this.movimientoService.registrarSalida(request).subscribe({
      next: () => {
        this.guardando.set(false);
        this.notificaciones.exito('Salida registrada.');
        this.guardado.emit();
      },
      error: (err: unknown) => {
        this.guardando.set(false);
        this.errorGeneral.set(traducirError(err).mensaje);
      },
    });
  }

  private enfocarPrimerError(): void {
    afterNextRender(
      () => {
        const orden: { id: string; invalido: boolean }[] = [
          { id: 'salida-producto', invalido: this.form.controls.productoId.invalid },
          { id: 'salida-cantidad', invalido: this.form.controls.cantidad.invalid },
          { id: 'salida-motivo', invalido: this.form.controls.motivo.invalid },
        ];
        const primero = orden.find((c) => c.invalido);
        if (primero) document.getElementById(primero.id)?.focus();
      },
      { injector: this.injector },
    );
  }
}

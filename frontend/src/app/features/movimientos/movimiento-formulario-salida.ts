import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { traducirError } from '../../core/errores/traducir-error';
import { ProductoService } from '../productos/producto.service';
import { SalidaRequest } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

@Component({
  selector: 'app-movimiento-formulario-salida',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-formulario-salida.html',
})
export class MovimientoFormularioSalida {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly router = inject(Router);

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
      return;
    }
    const v = this.form.getRawValue();
    if (v.productoId === null || v.cantidad === null) return;
    const request: SalidaRequest = { productoId: v.productoId, cantidad: v.cantidad, motivo: v.motivo.trim() };
    this.guardando.set(true);
    this.movimientoService.registrarSalida(request).subscribe({
      next: () => {
        this.guardando.set(false);
        void this.router
          .navigate(['/movimientos'])
          .then(() => this.notificaciones.exito('Salida registrada.'));
      },
      error: (err: unknown) => {
        this.guardando.set(false);
        this.errorGeneral.set(traducirError(err).mensaje);
      },
    });
  }
}

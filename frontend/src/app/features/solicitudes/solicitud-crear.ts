import { ChangeDetectionStrategy, Component, Injector, afterNextRender, computed, inject, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ProductoService } from '../productos/producto.service';
import { SolicitudRequest } from './solicitud.modelo';
import { SolicitudService } from './solicitud.service';

interface ItemCarrito {
  productoId: number;
  productoNombre: string;
  cantidad: number;
}

@Component({
  selector: 'app-solicitud-crear',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './solicitud-crear.html',
})
export class SolicitudCrear {
  readonly guardado = output<void>();
  readonly cancelar = output<void>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly solicitudService = inject(SolicitudService);
  private readonly productoService = inject(ProductoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly injector = inject(Injector);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly opcionesProducto = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

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
    if (this.items().length === 0) return;
    const request: SolicitudRequest = {
      items: this.items().map((i) => ({ productoId: i.productoId, cantidad: i.cantidad })),
    };
    this.guardando.set(true);
    this.solicitudService.crear(request).subscribe({
      next: () => {
        this.guardando.set(false);
        this.notificaciones.exito('Solicitud enviada.');
        this.guardado.emit();
      },
      error: (err: unknown) => {
        this.guardando.set(false);
        this.errorGeneral.set(traducirError(err).mensaje);
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

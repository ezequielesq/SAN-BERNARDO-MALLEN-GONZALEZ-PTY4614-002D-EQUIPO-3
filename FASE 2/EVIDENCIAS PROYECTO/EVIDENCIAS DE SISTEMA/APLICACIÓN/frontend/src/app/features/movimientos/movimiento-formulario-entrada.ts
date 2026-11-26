import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  output,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ProveedorService } from '../../shared/proveedor/proveedor.service';
import { ProductoService } from '../productos/producto.service';
import { EntradaRequest } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';
import { proveedorObligatorio } from './proveedor-entrada';

/** El costo es opcional (salvo en compras, donde lo exige Validators.required), pero si se ingresa debe ser un entero (el mínimo de 0 lo valida Validators.min). */
function costoEntero(control: AbstractControl): ValidationErrors | null {
  const valor = control.value;
  return valor === null || valor === '' || Number.isInteger(valor) ? null : { entero: true };
}

@Component({
  selector: 'app-movimiento-formulario-entrada',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-formulario-entrada.html',
})
export class MovimientoFormularioEntrada {
  readonly guardado = output<void>();
  readonly cancelar = output<void>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  private readonly proveedorService = inject(ProveedorService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly injector = inject(Injector);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly opcionesProducto = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

  protected readonly proveedores = this.proveedorService.listar(true);
  protected readonly opcionesProveedor = computed(() =>
    this.proveedores.hasValue() ? this.proveedores.value() : [],
  );

  private readonly tipos = this.movimientoService.listarTipos(1);
  protected readonly opcionesTipo = computed(() => (this.tipos.hasValue() ? this.tipos.value() : []));

  protected readonly form = this.fb.group({
    tipo: ['', Validators.required],
    productoId: this.fb.control<number | null>(null, Validators.required),
    cantidad: this.fb.control<number | null>(null, [Validators.required, Validators.min(1)]),
    fechaVencimiento: [''],
    numeroLote: ['', Validators.maxLength(50)],
    costoUnitario: this.fb.control<number | null>(null, [Validators.min(0), costoEntero]),
    proveedorId: this.fb.control<number | null>(null),
    nota: ['', Validators.maxLength(300)],
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

  // Mismo motivo que productoIdSeleccionado: el tipo elegido debe ser una señal real.
  private readonly tipoSeleccionado = toSignal(this.form.controls.tipo.valueChanges, {
    initialValue: this.form.controls.tipo.value,
  });

  protected readonly costoObligatorio = computed(() => this.tipoSeleccionado() === 'COMPRA');

  protected readonly proveedorRequerido = computed(() => proveedorObligatorio(this.tipoSeleccionado()));

  protected readonly productoSeleccionado = computed(() => {
    const id = this.productoIdSeleccionado();
    return this.opcionesProducto().find((p) => p.id === id) ?? null;
  });

  constructor() {
    effect(() => {
      const tipos = this.opcionesTipo();
      const control = this.form.controls.tipo;
      if (tipos.length > 0 && control.value === '') {
        control.setValue((tipos.find((t) => t.codigo === 'COMPRA') ?? tipos[0]).codigo);
      }
    });

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

    // En una compra el costo es obligatorio: de ahí sale el historial de precios. En los demás tipos sigue siendo opcional.
    effect(() => {
      const control = this.form.controls.costoUnitario;
      if (this.costoObligatorio()) {
        control.addValidators(Validators.required);
      } else {
        control.removeValidators(Validators.required);
      }
      control.updateValueAndValidity({ emitEvent: false });
    });

    // En una compra hay que saber a quién se le compró; en los demás tipos el proveedor es opcional.
    effect(() => {
      const control = this.form.controls.proveedorId;
      if (this.proveedorRequerido()) {
        control.addValidators(Validators.required);
      } else {
        control.removeValidators(Validators.required);
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
      tipo: v.tipo,
      nota: v.nota.trim() === '' ? null : v.nota.trim(),
      lineas: [
        {
          productoId: v.productoId,
          cantidad: v.cantidad,
          fechaVencimiento: v.fechaVencimiento.trim() === '' ? null : v.fechaVencimiento,
          numeroLote: v.numeroLote.trim() === '' ? null : v.numeroLote.trim(),
          costoUnitario: v.costoUnitario,
          proveedorId: v.proveedorId,
        },
      ],
    };
    this.guardando.set(true);
    this.movimientoService.registrarEntrada(request).subscribe({
      next: () => {
        this.guardando.set(false);
        this.notificaciones.exito('Entrada registrada.');
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
          { id: 'entrada-tipo', invalido: this.form.controls.tipo.invalid },
          { id: 'entrada-producto', invalido: this.form.controls.productoId.invalid },
          { id: 'entrada-cantidad', invalido: this.form.controls.cantidad.invalid },
          { id: 'entrada-vencimiento', invalido: this.form.controls.fechaVencimiento.invalid },
          { id: 'entrada-costo', invalido: this.form.controls.costoUnitario.invalid },
          { id: 'entrada-proveedor', invalido: this.form.controls.proveedorId.invalid },
        ];
        const primero = orden.find((c) => c.invalido);
        if (primero) document.getElementById(primero.id)?.focus();
      },
      { injector: this.injector },
    );
  }
}

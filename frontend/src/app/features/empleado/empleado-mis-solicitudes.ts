import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { traducirError } from '../../core/errores/traducir-error';
import { EstadoBadge, TipoEstado } from '../../shared/estado-badge/estado-badge';
import { Modal } from '../../shared/modal/modal';
import { Solicitud } from '../solicitudes/solicitud.modelo';
import { EmpleadoDevolucion } from './empleado-devolucion';
import { EmpleadoService } from './empleado.service';

@Component({
  selector: 'app-empleado-mis-solicitudes',
  imports: [ReactiveFormsModule, DatePipe, EstadoBadge, Modal, EmpleadoDevolucion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './empleado-mis-solicitudes.html',
})
export class EmpleadoMisSolicitudes {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly service = inject(EmpleadoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly empleados = this.service.empleados();
  protected readonly opcionesEmpleado = computed(() =>
    this.empleados.hasValue() ? this.empleados.value() : [],
  );

  protected readonly form = this.fb.group({
    empleadoId: this.fb.control<number | null>(null, Validators.required),
    password: ['', Validators.required],
  });

  protected readonly intento = signal(false);
  protected readonly cargando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);
  protected readonly solicitudes = signal<Solicitud[] | null>(null);
  protected readonly empleadoConsultado = signal<number | null>(null);
  protected readonly devolviendo = signal<Solicitud | null>(null);

  protected errorCampo(campo: 'empleadoId' | 'password'): boolean {
    const control = this.form.controls[campo];
    return (control.touched || this.intento()) && control.invalid;
  }

  protected consultar(): void {
    this.intento.set(true);
    this.errorGeneral.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (v.empleadoId === null) return;
    this.cargar(v.empleadoId, v.password);
    this.form.controls.password.reset('');
    this.intento.set(false);
  }

  protected alDevolver(password: string): void {
    const id = this.empleadoConsultado();
    this.devolviendo.set(null);
    if (id !== null) this.cargar(id, password);
  }

  protected puedeDevolver(s: Solicitud): boolean {
    return (
      s.tipo === 'PEDIDO' && s.estado === 'APROBADA' && s.detalles.some((d) => (d.devolvible ?? 0) > 0)
    );
  }

  protected badgeTipo(s: Solicitud): TipoEstado {
    return s.tipo === 'DEVOLUCION' ? 'devolucion' : 'pedido';
  }

  protected badgeEstado(s: Solicitud): TipoEstado {
    return s.estado.toLowerCase() as TipoEstado;
  }

  private cargar(empleadoId: number, password: string): void {
    if (this.cargando()) return;
    this.cargando.set(true);
    this.service
      .misSolicitudes({ empleadoId, password })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (lista) => {
          this.cargando.set(false);
          this.solicitudes.set(lista);
          this.empleadoConsultado.set(empleadoId);
        },
        error: (err: unknown) => {
          this.cargando.set(false);
          this.solicitudes.set(null);
          this.empleadoConsultado.set(null);
          this.errorGeneral.set(traducirError(err).mensaje);
        },
      });
  }
}

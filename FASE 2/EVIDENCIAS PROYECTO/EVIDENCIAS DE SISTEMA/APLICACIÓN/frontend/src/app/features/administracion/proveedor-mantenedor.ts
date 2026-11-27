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
import { Observable } from 'rxjs';
import { ErrorTraducido, traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { ThOrden } from '../../shared/ordenamiento/th-orden';
import { Proveedor, ProveedorService } from '../../shared/proveedor/proveedor.service';
import { errorDeRut, formatearRut, normalizarRut } from '../../shared/proveedor/rut';

type ColumnaOrdenProveedor = 'rut' | 'nombre' | 'estado';

const EXTRACTORES_PROVEEDOR: Extractores<Proveedor, ColumnaOrdenProveedor> = {
  // Se ordena por el cuerpo numérico del RUT, no como texto («1000005» antes que «12345678»).
  rut: (p) => Number(p.rut.split('-')[0]),
  nombre: (p) => p.nombre,
  estado: (p) => Number(p.activo),
};

const MAX_NOMBRE = 100;

/** Mensajes de un formulario: uno por campo y uno general (errores sin campo). */
interface ErroresForm {
  rut: string | null;
  nombre: string | null;
  general: string | null;
}

const SIN_ERRORES: ErroresForm = { rut: null, nombre: null, general: null };

@Component({
  selector: 'app-proveedor-mantenedor',
  imports: [ReactiveFormsModule, EstadoVista, EstadoBadge, ThOrden],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './proveedor-mantenedor.html',
})
export class ProveedorMantenedor {
  private readonly proveedorService = inject(ProveedorService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly items = this.proveedorService.listar(false);
  protected readonly lista = computed(() => (this.items.hasValue() ? this.items.value() : []));
  protected readonly orden = new Orden<ColumnaOrdenProveedor>();
  protected readonly listaOrdenada = computed(() => ordenar(this.lista(), this.orden, EXTRACTORES_PROVEEDOR));
  protected readonly formatearRut = formatearRut;

  protected readonly nuevoForm = this.fb.group({
    rut: ['', [Validators.required, Validators.maxLength(12)]],
    nombre: ['', [Validators.required, Validators.maxLength(MAX_NOMBRE)]],
  });
  protected readonly edicionForm = this.fb.group({
    rut: ['', [Validators.required, Validators.maxLength(12)]],
    nombre: ['', [Validators.required, Validators.maxLength(MAX_NOMBRE)]],
  });

  protected readonly editandoId = signal<number | null>(null);
  protected readonly procesando = signal(false);
  protected readonly errorNuevo = signal<ErroresForm>(SIN_ERRORES);
  protected readonly errorEdicion = signal<ErroresForm>(SIN_ERRORES);

  constructor() {
    // Un mensaje de un envío anterior (p. ej. «Ya existe…») deja de valer en cuanto se escribe en el formulario.
    this.nuevoForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.errorNuevo.set(SIN_ERRORES));
    this.edicionForm.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.errorEdicion.set(SIN_ERRORES));
  }

  protected agregar(): void {
    if (this.procesando()) return;
    const { rut, nombre } = this.nuevoForm.getRawValue();
    const datos = this.validar(rut, nombre);
    if ('errores' in datos) {
      this.errorNuevo.set(datos.errores);
      this.enfocar(datos.errores.rut ? 'nuevo-rut' : 'nuevo-nombre');
      return;
    }
    this.ejecutar(
      this.proveedorService.crear(datos.rut, datos.nombre),
      (creado) => {
        this.nuevoForm.reset();
        this.errorNuevo.set(SIN_ERRORES);
        this.notificaciones.exito(`Proveedor '${creado.nombre}' agregado.`);
        this.enfocar('nuevo-rut');
      },
      (error) => {
        this.errorNuevo.set(this.erroresDe(error));
        this.enfocar(error.campo === 'nombre' ? 'nuevo-nombre' : 'nuevo-rut');
      },
    );
  }

  protected iniciarEdicion(item: Proveedor): void {
    this.errorNuevo.set(SIN_ERRORES);
    this.editandoId.set(item.id);
    this.edicionForm.reset({ rut: formatearRut(item.rut), nombre: item.nombre });
    this.errorEdicion.set(SIN_ERRORES);
    this.enfocar(`editar-rut-${item.id}`);
  }

  protected cancelarEdicion(item: Proveedor): void {
    this.editandoId.set(null);
    this.errorEdicion.set(SIN_ERRORES);
    this.enfocar(`editar-${item.id}`);
  }

  protected guardarEdicion(item: Proveedor): void {
    if (this.procesando()) return;
    const { rut, nombre } = this.edicionForm.getRawValue();
    const datos = this.validar(rut, nombre);
    if ('errores' in datos) {
      this.errorEdicion.set(datos.errores);
      this.enfocar(datos.errores.rut ? `editar-rut-${item.id}` : `editar-nombre-${item.id}`);
      return;
    }
    this.ejecutar(
      this.proveedorService.editar(item.id, datos.rut, datos.nombre),
      () => {
        this.editandoId.set(null);
        this.errorEdicion.set(SIN_ERRORES);
        this.notificaciones.exito('Proveedor actualizado.');
        this.enfocar(`editar-${item.id}`);
      },
      (error) => {
        this.errorEdicion.set(this.erroresDe(error));
        this.enfocar(error.campo === 'nombre' ? `editar-nombre-${item.id}` : `editar-rut-${item.id}`);
      },
    );
  }

  protected alternar(item: Proveedor): void {
    if (this.procesando()) return;
    this.errorNuevo.set(SIN_ERRORES);
    const operacion$ = item.activo
      ? this.proveedorService.desactivar(item.id)
      : this.proveedorService.reactivar(item.id);
    const accion = item.activo ? 'deshabilitado' : 'reactivado';
    this.ejecutar(
      operacion$,
      () => {
        this.notificaciones.exito(`Proveedor '${item.nombre}' ${accion}.`);
      },
      (error) => this.notificaciones.error(error.mensaje),
    );
  }

  /** Valida en el cliente; el RUT se envía normalizado («12345678-5»). */
  private validar(
    rutTexto: string,
    nombreTexto: string,
  ): { rut: string; nombre: string } | { errores: ErroresForm } {
    const rut = normalizarRut(rutTexto);
    const nombre = nombreTexto.trim();
    const errorRut = errorDeRut(rutTexto);
    let errorNombre: string | null = null;
    if (nombre === '') {
      errorNombre = 'Ingresa el nombre del proveedor.';
    } else if (nombre.length > MAX_NOMBRE) {
      errorNombre = `El nombre admite como máximo ${MAX_NOMBRE} caracteres.`;
    }
    if (rut === null || errorNombre !== null) {
      return { errores: { rut: errorRut, nombre: errorNombre, general: null } };
    }
    return { rut, nombre };
  }

  /** Un 422 con `field` va bajo su campo; cualquier otro error, en el aviso general. */
  private erroresDe(error: ErrorTraducido): ErroresForm {
    if (error.campo === 'rut') return { ...SIN_ERRORES, rut: error.mensaje };
    if (error.campo === 'nombre') return { ...SIN_ERRORES, nombre: error.mensaje };
    return { ...SIN_ERRORES, general: error.mensaje };
  }

  private ejecutar<T>(
    operacion$: Observable<T>,
    alTerminar: (resultado: T) => void,
    alFallar: (error: ErrorTraducido) => void,
  ): void {
    this.procesando.set(true);
    operacion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (resultado) => {
        this.procesando.set(false);
        this.items.reload();
        alTerminar(resultado);
      },
      error: (err: unknown) => {
        this.procesando.set(false);
        alFallar(traducirError(err));
      },
    });
  }

  private enfocar(id: string): void {
    afterNextRender(() => document.getElementById(id)?.focus(), { injector: this.injector });
  }
}

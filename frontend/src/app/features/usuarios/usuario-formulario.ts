import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { filter } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ConCambiosPendientes } from '../../shared/confirmar-dialogo/cambios-pendientes.guard';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { RolAdministrable, Usuario, UsuarioRequest } from './usuario.modelo';
import { UsuarioService } from './usuario.service';

type Campo = 'nombre' | 'email' | 'rol' | 'password';

const CAMPOS: readonly Campo[] = ['nombre', 'email', 'rol', 'password'];

const ETIQUETAS: Record<Campo, string> = {
  nombre: 'Nombre',
  email: 'Correo',
  rol: 'Rol',
  password: 'Contraseña',
};

const REQUERIDO: Record<Campo, string> = {
  nombre: 'Ingresa el nombre.',
  email: 'Ingresa un correo válido, por ejemplo nombre@restaurante.cl.',
  rol: 'Elige un rol.',
  password: 'Ingresa una contraseña de al menos 6 caracteres.',
};

const MAXIMO: Partial<Record<Campo, number>> = { nombre: 100, email: 150 };

function noSoloEspacios(control: AbstractControl): ValidationErrors | null {
  const valor: unknown = control.value;
  return typeof valor === 'string' && valor !== '' && valor.trim() === ''
    ? { soloEspacios: true }
    : null;
}

function esCampo(valor: string): valor is Campo {
  return (CAMPOS as readonly string[]).includes(valor);
}

@Component({
  selector: 'app-usuario-formulario',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './usuario-formulario.html',
})
export class UsuarioFormulario implements ConCambiosPendientes {
  /** `null` = alta (Nuevo usuario); un usuario = edición. */
  readonly usuario = input.required<Usuario | null>();
  readonly guardado = output<void>();
  readonly cerrarSolicitado = output<void>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly usuarioService = inject(UsuarioService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly ETIQUETAS = ETIQUETAS;

  protected readonly esEdicion = computed(() => this.usuario() !== null);

  protected readonly form = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(100), noSoloEspacios]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    rol: this.fb.control<RolAdministrable | null>(null, Validators.required),
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);
  protected readonly mostrarPassword = signal(false);

  constructor() {
    effect(() => {
      const u = this.usuario();
      untracked(() => {
        this.form.reset({
          nombre: u?.nombre ?? '',
          email: u?.email ?? '',
          rol: u === null ? null : u.rol === 'EMPLEADO' ? 'EMPLEADO' : 'BODEGUERO',
          password: '',
        });
        // Alta: contraseña obligatoria. Edición: opcional (vacía = no cambiarla).
        this.form.controls.password.setValidators(
          u === null ? [Validators.required, Validators.minLength(6)] : [Validators.minLength(6)],
        );
        this.form.controls.password.updateValueAndValidity({ emitEvent: false });
      });
    });
  }

  tieneCambiosPendientes(): boolean {
    return this.form.dirty && !this.guardando();
  }

  solicitarCierre(): void {
    if (!this.tieneCambiosPendientes()) {
      this.cerrarSolicitado.emit();
      return;
    }
    this.confirmacion
      .confirmar({
        titulo: 'Tienes cambios sin guardar',
        descripcion: 'Si sales ahora, se perderán los cambios de este formulario.',
        textoConfirmar: 'Salir sin guardar',
      })
      .pipe(
        filter((confirmado) => confirmado),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.cerrarSolicitado.emit());
  }

  protected alternarPassword(): void {
    this.mostrarPassword.update((v) => !v);
  }

  protected mensajeError(campo: Campo): string | null {
    const control = this.form.controls[campo];
    const mostrar = control.touched || this.enviado();
    if (!mostrar) return null;
    const errores = control.errors;
    if (!errores) return null;
    const servidor: unknown = errores['servidor'];
    if (typeof servidor === 'string') return servidor;
    if (errores['required'] || errores['soloEspacios']) return REQUERIDO[campo];
    if (errores['maxlength'])
      return `${ETIQUETAS[campo]} admite como máximo ${MAXIMO[campo] ?? 0} caracteres.`;
    if (errores['email']) return REQUERIDO.email;
    if (errores['minlength']) return 'La contraseña debe tener al menos 6 caracteres.';
    return `Revisa el campo ${ETIQUETAS[campo]}.`;
  }

  protected describedBy(campo: Campo): string | null {
    const ids = [
      campo === 'password' ? 'ayuda-password' : undefined,
      this.mensajeError(campo) ? `error-${campo}` : undefined,
    ].filter(Boolean);
    return ids.length > 0 ? ids.join(' ') : null;
  }

  protected camposConError(): Campo[] {
    return this.enviado() ? CAMPOS.filter((c) => this.mensajeError(c) !== null) : [];
  }

  protected irACampo(evento: Event, campo: Campo): void {
    evento.preventDefault();
    document.getElementById(`campo-${campo}`)?.focus();
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
    if (v.rol === null) return;
    const request: UsuarioRequest = {
      nombre: v.nombre.trim(),
      email: v.email.trim(),
      rol: v.rol,
      password: v.password === '' ? null : v.password,
    };
    const u = this.usuario();
    const operacion$ =
      u === null ? this.usuarioService.crear(request) : this.usuarioService.editar(u.id, request);
    this.guardando.set(true);
    operacion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (guardado) => {
        this.form.markAsPristine();
        this.guardando.set(false);
        this.notificaciones.exito(
          u === null
            ? `Usuario '${guardado.nombre}' creado.`
            : `Cambios guardados en '${guardado.nombre}'.`,
        );
        this.guardado.emit();
        this.cerrarSolicitado.emit();
      },
      error: (err: unknown) => {
        this.guardando.set(false);
        this.aplicarErrorServidor(err);
      },
    });
  }

  private aplicarErrorServidor(err: unknown): void {
    const error = traducirError(err);
    let asignado = false;
    const sinAsignar: string[] = [];
    const asignar = (campo: string, mensaje: string): void => {
      if (!esCampo(campo)) {
        sinAsignar.push(mensaje);
        return;
      }
      const control = this.form.controls[campo];
      control.setErrors({ ...(control.errors ?? {}), servidor: mensaje });
      control.markAsTouched();
      asignado = true;
    };
    if (error.campo) asignar(error.campo, error.mensaje);
    for (const [campo, mensaje] of Object.entries(error.camposValidacion)) asignar(campo, mensaje);
    if (sinAsignar.length > 0) {
      this.errorGeneral.set(sinAsignar.join(' '));
    } else if (!asignado) {
      this.errorGeneral.set(error.mensaje);
    }
    if (asignado) this.enfocarPrimerError();
  }

  private enfocarPrimerError(): void {
    afterNextRender(
      () => {
        const primero = CAMPOS.find((c) => this.mensajeError(c) !== null);
        if (primero) document.getElementById(`campo-${primero}`)?.focus();
      },
      { injector: this.injector },
    );
  }
}

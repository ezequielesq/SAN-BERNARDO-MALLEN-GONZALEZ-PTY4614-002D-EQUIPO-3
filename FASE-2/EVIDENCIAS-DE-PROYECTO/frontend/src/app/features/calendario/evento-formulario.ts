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
import { Evento, EventoRequest } from './evento.modelo';
import { EventoService } from './evento.service';

type Campo = 'titulo' | 'fecha' | 'hora' | 'descripcion';

const CAMPOS: readonly Campo[] = ['titulo', 'fecha', 'hora', 'descripcion'];

const ETIQUETAS: Record<Campo, string> = {
  titulo: 'Título',
  fecha: 'Fecha',
  hora: 'Hora',
  descripcion: 'Descripción',
};

const REQUERIDO: Record<Campo, string> = {
  titulo: 'Ingresa el título del evento.',
  fecha: 'Elige la fecha del evento.',
  hora: 'Ingresa una hora válida.',
  descripcion: 'Ingresa una descripción válida.',
};

const MAXIMO: Partial<Record<Campo, number>> = { titulo: 120, descripcion: 500 };

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
  selector: 'app-evento-formulario',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './evento-formulario.html',
})
export class EventoFormulario implements ConCambiosPendientes {
  /** `null` = alta (Nuevo evento); un evento = edición. */
  readonly evento = input.required<Evento | null>();
  /** Fecha (yyyy-MM-dd) que se precarga en el alta. */
  readonly fechaInicial = input.required<string>();
  readonly guardado = output<void>();
  readonly cerrarSolicitado = output<void>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly eventoService = inject(EventoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly ETIQUETAS = ETIQUETAS;

  protected readonly esEdicion = computed(() => this.evento() !== null);

  protected readonly form = this.fb.group({
    titulo: ['', [Validators.required, Validators.maxLength(120), noSoloEspacios]],
    fecha: ['', Validators.required],
    hora: [''],
    descripcion: ['', Validators.maxLength(500)],
  });

  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  constructor() {
    effect(() => {
      const e = this.evento();
      const fechaInicial = this.fechaInicial();
      untracked(() => {
        this.form.reset({
          titulo: e?.titulo ?? '',
          fecha: e?.fecha ?? fechaInicial,
          // El servidor puede enviar HH:mm:ss; el campo de hora usa HH:mm.
          hora: e?.hora?.slice(0, 5) ?? '',
          descripcion: e?.descripcion ?? '',
        });
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
    return `Revisa el campo ${ETIQUETAS[campo]}.`;
  }

  protected describedBy(campo: Campo): string | null {
    const ids = [
      campo === 'hora' ? 'ayuda-hora' : undefined,
      campo === 'descripcion' ? 'ayuda-descripcion' : undefined,
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
    const descripcion = v.descripcion.trim();
    const request: EventoRequest = {
      titulo: v.titulo.trim(),
      fecha: v.fecha,
      hora: v.hora === '' ? null : v.hora.slice(0, 5),
      descripcion: descripcion === '' ? null : descripcion,
    };
    const e = this.evento();
    const operacion$ =
      e === null ? this.eventoService.crear(request) : this.eventoService.actualizar(e.id, request);
    this.guardando.set(true);
    operacion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (guardado) => {
        this.form.markAsPristine();
        this.guardando.set(false);
        this.notificaciones.exito(
          e === null
            ? `Evento '${guardado.titulo}' creado.`
            : `Cambios guardados en '${guardado.titulo}'.`,
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

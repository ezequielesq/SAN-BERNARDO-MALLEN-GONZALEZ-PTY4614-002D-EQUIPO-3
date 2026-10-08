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
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { EstadoBadge } from '../estado-badge/estado-badge';
import { EstadoVista } from '../estado-vista/estado-vista';
import { CatalogoItem, CatalogoService } from './catalogo.service';

export interface ConfigCatalogo {
  titulo: string;
  singular: string;
  endpoint: string;
  /** Solo Categorías: muestra la casilla "Requiere código PTV". */
  conRequierePtv?: boolean;
}

function leerConfig(data: Record<string, unknown>): ConfigCatalogo {
  const { titulo, singular, endpoint, conRequierePtv } = data;
  if (typeof titulo !== 'string' || typeof singular !== 'string' || typeof endpoint !== 'string') {
    throw new Error('Ruta de catálogo sin data { titulo, singular, endpoint }');
  }
  return { titulo, singular, endpoint, conRequierePtv: conRequierePtv === true };
}

function capitalizar(texto: string): string {
  return texto.charAt(0).toLocaleUpperCase('es') + texto.slice(1);
}

@Component({
  selector: 'app-catalogo-mantenedor',
  imports: [ReactiveFormsModule, EstadoVista, EstadoBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './catalogo-mantenedor.html',
})
export class CatalogoMantenedor {
  private readonly catalogoService = inject(CatalogoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly config = leerConfig(inject(ActivatedRoute).snapshot.data);
  protected readonly singularMayuscula = capitalizar(this.config.singular);
  protected readonly items = this.catalogoService.listar(this.config.endpoint, false);
  protected readonly lista = computed(() => (this.items.hasValue() ? this.items.value() : []));

  protected readonly nuevoForm = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
  });
  protected readonly edicionForm = this.fb.group({
    nombre: ['', [Validators.required, Validators.maxLength(50)]],
  });

  protected readonly editandoId = signal<number | null>(null);
  protected readonly procesando = signal(false);
  protected readonly errorNuevo = signal<string | null>(null);
  protected readonly errorEdicion = signal<string | null>(null);

  protected agregar(): void {
    if (this.procesando()) return;
    const nombre = this.nuevoForm.controls.nombre.value.trim();
    if (nombre === '') {
      this.errorNuevo.set(`Ingresa el nombre de la nueva ${this.config.singular}.`);
      this.enfocar('nuevo-nombre');
      return;
    }
    if (nombre.length > 50) {
      this.errorNuevo.set('El nombre admite como máximo 50 caracteres.');
      this.enfocar('nuevo-nombre');
      return;
    }
    this.ejecutar(
      this.catalogoService.crear(this.config.endpoint, nombre),
      (creado) => {
        this.nuevoForm.reset();
        this.errorNuevo.set(null);
        this.notificaciones.exito(`${this.singularMayuscula} '${creado.nombre}' agregada.`);
        this.enfocar('nuevo-nombre');
      },
      (mensaje) => {
        this.errorNuevo.set(mensaje);
        this.enfocar('nuevo-nombre');
      },
    );
  }

  protected iniciarEdicion(item: CatalogoItem): void {
    this.editandoId.set(item.id);
    this.edicionForm.reset({ nombre: item.nombre });
    this.errorEdicion.set(null);
    this.enfocar(`editar-${item.id}`);
  }

  protected cancelarEdicion(item: CatalogoItem): void {
    this.editandoId.set(null);
    this.errorEdicion.set(null);
    this.enfocar(`renombrar-${item.id}`);
  }

  protected guardarEdicion(item: CatalogoItem): void {
    if (this.procesando()) return;
    const nombre = this.edicionForm.controls.nombre.value.trim();
    if (nombre === '' || nombre.length > 50) {
      this.errorEdicion.set(
        nombre === '' ? 'Ingresa el nuevo nombre.' : 'El nombre admite como máximo 50 caracteres.',
      );
      this.enfocar(`editar-${item.id}`);
      return;
    }
    this.ejecutar(
      this.catalogoService.renombrar(this.config.endpoint, item.id, nombre),
      (renombrado) => {
        this.editandoId.set(null);
        this.notificaciones.exito(`${this.singularMayuscula} renombrada a '${renombrado.nombre}'.`);
        this.enfocar(`renombrar-${item.id}`);
      },
      (mensaje) => {
        this.errorEdicion.set(mensaje);
        this.enfocar(`editar-${item.id}`);
      },
    );
  }

  protected alternar(item: CatalogoItem): void {
    if (this.procesando()) return;
    const operacion$ = item.activo
      ? this.catalogoService.desactivar(this.config.endpoint, item.id)
      : this.catalogoService.reactivar(this.config.endpoint, item.id);
    const accion = item.activo ? 'desactivada' : 'reactivada';
    this.ejecutar(
      operacion$,
      () => {
        this.notificaciones.exito(`${this.singularMayuscula} '${item.nombre}' ${accion}.`);
      },
      (mensaje) => this.notificaciones.error(mensaje),
    );
  }

  protected alternarRequierePtv(item: CatalogoItem, requierePtv: boolean, casilla: HTMLInputElement): void {
    if (this.procesando()) {
      casilla.checked = item.requierePtv === true;
      return;
    }
    this.ejecutar(
      this.catalogoService.cambiarRequierePtv(this.config.endpoint, item.id, requierePtv),
      () => {
        this.notificaciones.exito(
          requierePtv
            ? `${this.singularMayuscula} '${item.nombre}' ahora requiere código PTV.`
            : `${this.singularMayuscula} '${item.nombre}' ya no requiere código PTV.`,
        );
      },
      (mensaje) => {
        casilla.checked = item.requierePtv === true;
        this.notificaciones.error(mensaje);
      },
    );
  }

  private ejecutar<T>(
    operacion$: Observable<T>,
    alTerminar: (resultado: T) => void,
    alFallar: (mensaje: string) => void,
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
        alFallar(traducirError(err).mensaje);
      },
    });
  }

  private enfocar(id: string): void {
    afterNextRender(() => document.getElementById(id)?.focus(), { injector: this.injector });
  }
}

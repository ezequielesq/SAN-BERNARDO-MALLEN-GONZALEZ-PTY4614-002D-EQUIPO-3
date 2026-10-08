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
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { filter, startWith, switchMap } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import {
  CatalogoService,
  ENDPOINT_CATEGORIAS,
  ENDPOINT_UNIDADES,
} from '../../shared/catalogo/catalogo.service';
import { ConCambiosPendientes } from '../../shared/confirmar-dialogo/cambios-pendientes.guard';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Producto, ProductoRequest } from './producto.modelo';
import { ProductoService } from './producto.service';

type Campo =
  | 'codigoPtv'
  | 'codigo'
  | 'nombre'
  | 'categoriaId'
  | 'unidadMedidaId'
  | 'stockMinimo'
  | 'stockCritico'
  | 'costoUnitario';

type ReglaCodigo = 'ninguna' | 'ptv' | 'numerico';

const CAMPOS: readonly Campo[] = [
  'categoriaId',
  'codigoPtv',
  'codigo',
  'nombre',
  'unidadMedidaId',
  'stockMinimo',
  'stockCritico',
  'costoUnitario',
];

const ETIQUETAS: Record<Campo, string> = {
  codigoPtv: 'Código PTV',
  codigo: 'Código',
  nombre: 'Nombre',
  categoriaId: 'Categoría',
  unidadMedidaId: 'Unidad de medida',
  stockMinimo: 'Stock mínimo',
  stockCritico: 'Stock crítico',
  costoUnitario: 'Costo unitario',
};

const REQUERIDO: Record<Campo, string> = {
  codigoPtv: 'Ingresa el código PTV del producto, por ejemplo PTV0012.',
  codigo: 'Ingresa el código del producto, por ejemplo 71149.',
  nombre: 'Ingresa el nombre del producto.',
  categoriaId: 'Elige una categoría.',
  unidadMedidaId: 'Elige una unidad de medida.',
  stockMinimo: 'Ingresa el stock mínimo (0 o más).',
  stockCritico: 'Ingresa el stock crítico (0 o más).',
  costoUnitario: 'Ingresa el costo unitario (0 o más).',
};

const MAXIMO: Partial<Record<Campo, number>> = { codigoPtv: 20, codigo: 20, nombre: 150 };

const AYUDA: Partial<Record<Campo, string>> = {
  codigoPtv: 'ayuda-codigoPtv',
  codigo: 'ayuda-codigo',
  stockCritico: 'ayuda-umbrales',
};

function noSoloEspacios(control: AbstractControl): ValidationErrors | null {
  const valor: unknown = control.value;
  return typeof valor === 'string' && valor !== '' && valor.trim() === ''
    ? { soloEspacios: true }
    : null;
}

function criticoMenorOIgualAMinimo(grupo: AbstractControl): ValidationErrors | null {
  const controlMinimo = grupo.get('stockMinimo');
  const controlCritico = grupo.get('stockCritico');
  if (!controlMinimo?.valid || !controlCritico?.valid) return null;
  const minimo: unknown = controlMinimo.value;
  const critico: unknown = controlCritico.value;
  return typeof minimo === 'number' && typeof critico === 'number' && critico > minimo
    ? { criticoMayorQueMinimo: true }
    : null;
}

function esCampo(valor: string): valor is Campo {
  return (CAMPOS as readonly string[]).includes(valor);
}

@Component({
  selector: 'app-producto-formulario',
  imports: [ReactiveFormsModule, EstadoVista],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './producto-formulario.html',
})
export class ProductoFormulario implements ConCambiosPendientes {
  /** `null` = alta (Nuevo producto); número = edición de ese producto. */
  readonly productoId = input.required<number | null>();
  readonly guardado = output<void>();
  readonly cerrarSolicitado = output<void>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly productoService = inject(ProductoService);
  private readonly catalogoService = inject(CatalogoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly ETIQUETAS = ETIQUETAS;

  protected readonly esEdicion = computed(() => this.productoId() !== null);
  protected readonly idNumerico = computed(() => this.productoId());

  protected readonly producto = this.productoService.obtener(this.idNumerico);
  protected readonly categorias = this.catalogoService.listar(ENDPOINT_CATEGORIAS, false);
  protected readonly unidades = this.catalogoService.listar(ENDPOINT_UNIDADES, false);

  protected readonly productoActual = computed<Producto | null>(() =>
    this.producto.hasValue() ? (this.producto.value() ?? null) : null,
  );

  protected readonly titulo = computed(() => {
    if (!this.esEdicion()) return 'Nuevo producto';
    const p = this.productoActual();
    return p ? `Editar: ${p.nombre}` : 'Editar producto';
  });

  protected readonly opcionesCategoria = computed(() => {
    const lista = this.categorias.hasValue() ? this.categorias.value() : [];
    const actual = this.productoActual()?.categoriaId;
    return lista.filter((c) => c.activo || c.id === actual);
  });

  protected readonly opcionesUnidad = computed(() => {
    const lista = this.unidades.hasValue() ? this.unidades.value() : [];
    const actual = this.productoActual()?.unidadMedidaId;
    return lista.filter((u) => u.activo || u.id === actual);
  });

  protected readonly cargandoDatos = computed(
    () =>
      this.categorias.status() === 'loading' ||
      this.unidades.status() === 'loading' ||
      (this.esEdicion() && this.producto.status() === 'loading'),
  );

  protected readonly errorCarga = computed(
    () => this.producto.error() ?? this.categorias.error() ?? this.unidades.error(),
  );

  protected readonly form = this.fb.group(
    {
      codigoPtv: this.fb.control({ value: '', disabled: true }),
      codigo: this.fb.control({ value: '', disabled: true }),
      nombre: ['', [Validators.required, Validators.maxLength(150), noSoloEspacios]],
      categoriaId: this.fb.control<number | null>(null, Validators.required),
      unidadMedidaId: this.fb.control<number | null>(null, Validators.required),
      esPerecible: [false],
      stockMinimo: this.fb.control<number | null>(0, [
        Validators.required,
        Validators.min(0),
        Validators.pattern(/^\d+$/),
      ]),
      stockCritico: this.fb.control<number | null>(0, [
        Validators.required,
        Validators.min(0),
        Validators.pattern(/^\d+$/),
      ]),
      costoUnitario: this.fb.control<number | null>(null, [
        Validators.required,
        Validators.min(0),
        Validators.pattern(/^\d+$/),
      ]),
    },
    { validators: [criticoMenorOIgualAMinimo] },
  );

  private readonly categoriaElegida = toSignal(
    this.form.controls.categoriaId.valueChanges.pipe(
      startWith(this.form.controls.categoriaId.value),
    ),
    { requireSync: true },
  );

  /** Qué código se pide según la categoría elegida (la marca viene de la lista de categorías). */
  protected readonly reglaCodigo = computed<ReglaCodigo>(() => {
    const id = this.categoriaElegida();
    if (id === null) return 'ninguna';
    const categoria = this.categorias.hasValue()
      ? this.categorias.value().find((c) => c.id === id)
      : undefined;
    if (!categoria) return 'ninguna';
    return categoria.requierePtv === true ? 'ptv' : 'numerico';
  });

  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);
  private readonly enfocarTrasReactivar = signal(false);

  constructor() {
    effect(() => {
      const regla = this.reglaCodigo();
      untracked(() => this.aplicarReglaCodigo(regla));
    });

    effect(() => {
      const p = this.productoActual();
      if (!p) return;
      // No pisar ediciones en curso: solo aplicar el patch mientras el form
      // sigue intacto (evita que un reload() disparado por reactivar()/recargar()
      // borre silenciosamente cambios que el usuario ya escribió).
      if (!this.form.pristine) return;
      untracked(() => {
        this.form.reset({
          codigoPtv: p.codigoPtv ?? '',
          codigo: p.codigo ?? '',
          nombre: p.nombre,
          categoriaId: p.categoriaId,
          unidadMedidaId: p.unidadMedidaId,
          esPerecible: p.esPerecible,
          stockMinimo: p.stockMinimo,
          stockCritico: p.stockCritico,
          costoUnitario: p.costoUnitario,
        });
      });
    });

    effect(() => {
      if (this.enfocarTrasReactivar() && this.producto.status() === 'resolved') {
        this.enfocarTrasReactivar.set(false);
        afterNextRender(() => document.getElementById('titulo-estado-producto')?.focus(), {
          injector: this.injector,
        });
      }
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

  protected recargar(): void {
    this.categorias.reload();
    this.unidades.reload();
    this.producto.reload();
  }

  protected mensajeError(campo: Campo): string | null {
    const control = this.form.controls[campo];
    const mostrar = control.touched || this.enviado();
    if (!mostrar) return null;
    const errores = control.errors;
    if (errores) {
      const servidor: unknown = errores['servidor'];
      if (typeof servidor === 'string') return servidor;
      if (errores['required'] || errores['soloEspacios']) return REQUERIDO[campo];
      if (errores['maxlength'])
        return `${ETIQUETAS[campo]} admite como máximo ${MAXIMO[campo] ?? 0} caracteres.`;
      if (errores['min']) return `${ETIQUETAS[campo]} no puede ser negativo.`;
      if (errores['pattern']) {
        if (campo === 'codigoPtv') return 'El código PTV solo admite letras y números.';
        if (campo === 'codigo') return 'El código solo admite números.';
        return `${ETIQUETAS[campo]} debe ser un número entero.`;
      }
      return `Revisa el campo ${ETIQUETAS[campo]}.`;
    }
    if (campo === 'stockCritico' && this.form.hasError('criticoMayorQueMinimo')) {
      return 'El stock crítico debe ser menor o igual al stock mínimo.';
    }
    return null;
  }

  protected describedBy(campo: Campo): string | null {
    const ids = [AYUDA[campo], this.mensajeError(campo) ? `error-${campo}` : undefined].filter(
      Boolean,
    );
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
    if (
      v.categoriaId === null ||
      v.unidadMedidaId === null ||
      v.stockMinimo === null ||
      v.stockCritico === null ||
      v.costoUnitario === null
    )
      return;
    const request: ProductoRequest = {
      codigoPtv: v.codigoPtv.trim() === '' ? null : v.codigoPtv.trim(),
      codigo: v.codigo.trim() === '' ? null : v.codigo.trim(),
      nombre: v.nombre.trim(),
      categoriaId: v.categoriaId,
      unidadMedidaId: v.unidadMedidaId,
      esPerecible: v.esPerecible,
      stockMinimo: v.stockMinimo,
      stockCritico: v.stockCritico,
      costoUnitario: v.costoUnitario,
    };
    const id = this.idNumerico();
    const operacion$ =
      id === null ? this.productoService.crear(request) : this.productoService.editar(id, request);
    this.guardando.set(true);
    operacion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (guardado) => {
        this.form.markAsPristine();
        this.guardando.set(false);
        const texto =
          id === null
            ? `Producto '${guardado.nombre}' creado.`
            : `Cambios guardados en '${guardado.nombre}'.`;
        this.notificaciones.exito(texto);
        this.guardado.emit();
        this.cerrarSolicitado.emit();
      },
      error: (err: unknown) => {
        this.guardando.set(false);
        this.aplicarErrorServidor(err);
      },
    });
  }

  protected deshabilitar(p: Producto): void {
    this.confirmacion
      .confirmar({
        titulo: `¿Deshabilitar '${p.nombre}'?`,
        descripcion:
          'Dejará de aparecer para registrar movimientos y solicitudes. Su historial se conserva.',
        textoConfirmar: 'Deshabilitar producto',
      })
      .pipe(
        filter((confirmado) => confirmado),
        switchMap(() => {
          this.guardando.set(true);
          return this.productoService.deshabilitar(p.id);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.form.markAsPristine();
          this.guardando.set(false);
          this.notificaciones.exito(`Producto '${p.nombre}' deshabilitado.`);
          this.guardado.emit();
          this.cerrarSolicitado.emit();
        },
        error: (err: unknown) => {
          this.guardando.set(false);
          this.errorGeneral.set(traducirError(err).mensaje);
        },
      });
  }

  protected reactivar(p: Producto): void {
    if (this.guardando()) return;
    this.guardando.set(true);
    this.productoService
      .reactivar(p.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.notificaciones.exito(`Producto '${p.nombre}' reactivado.`);
          this.enfocarTrasReactivar.set(true);
          this.producto.reload();
          this.guardado.emit();
        },
        error: (err: unknown) => {
          this.guardando.set(false);
          this.errorGeneral.set(traducirError(err).mensaje);
        },
      });
  }

  /**
   * Activa el campo de código que aplica a la categoría (con su validación) y desactiva y vacía el
   * otro. Con la categoría aún sin resolver (p. ej. al cargar una edición) solo desactiva ambos,
   * sin tocar los valores.
   */
  private aplicarReglaCodigo(regla: ReglaCodigo): void {
    const ptv = this.form.controls.codigoPtv;
    const numerico = this.form.controls.codigo;

    if (regla === 'ptv') {
      ptv.setValidators([
        Validators.required,
        Validators.maxLength(20),
        Validators.pattern(/^[A-Za-z0-9]+$/),
        noSoloEspacios,
      ]);
      ptv.enable({ emitEvent: false });
      numerico.clearValidators();
      if (numerico.value !== '') numerico.setValue('', { emitEvent: false });
      numerico.disable({ emitEvent: false });
    } else if (regla === 'numerico') {
      numerico.setValidators([
        Validators.required,
        Validators.maxLength(20),
        Validators.pattern(/^\d+$/),
        noSoloEspacios,
      ]);
      numerico.enable({ emitEvent: false });
      ptv.clearValidators();
      if (ptv.value !== '') ptv.setValue('', { emitEvent: false });
      ptv.disable({ emitEvent: false });
    } else {
      ptv.clearValidators();
      numerico.clearValidators();
      ptv.disable({ emitEvent: false });
      numerico.disable({ emitEvent: false });
    }
    ptv.updateValueAndValidity({ emitEvent: false });
    numerico.updateValueAndValidity({ emitEvent: false });
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
    // Cualquier campo que el backend haya señalado pero que no exista en este
    // formulario debe seguir siendo visible, aunque otros campos sí se hayan
    // asignado correctamente: no lo descartamos en silencio.
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

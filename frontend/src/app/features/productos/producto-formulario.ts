import {
  ChangeDetectionStrategy, Component, DestroyRef, Injector, afterNextRender, computed, effect, inject, input, signal, untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators,
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { CatalogoService, ENDPOINT_CATEGORIAS, ENDPOINT_UNIDADES } from '../../shared/catalogo/catalogo.service';
import { ConCambiosPendientes } from '../../shared/confirmar-dialogo/cambios-pendientes.guard';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Producto, ProductoRequest } from './producto.modelo';
import { ProductoService } from './producto.service';

type Campo = 'codigoPtb' | 'nombre' | 'categoriaId' | 'unidadMedidaId' | 'stockMinimo' | 'stockCritico';

const CAMPOS: readonly Campo[] = ['codigoPtb', 'nombre', 'categoriaId', 'unidadMedidaId', 'stockMinimo', 'stockCritico'];

const ETIQUETAS: Record<Campo, string> = {
  codigoPtb: 'Código PTB',
  nombre: 'Nombre',
  categoriaId: 'Categoría',
  unidadMedidaId: 'Unidad de medida',
  stockMinimo: 'Stock mínimo',
  stockCritico: 'Stock crítico',
};

const REQUERIDO: Record<Campo, string> = {
  codigoPtb: 'Ingresa el código PTB del producto, por ejemplo PTB0012.',
  nombre: 'Ingresa el nombre del producto.',
  categoriaId: 'Elige una categoría.',
  unidadMedidaId: 'Elige una unidad de medida.',
  stockMinimo: 'Ingresa el stock mínimo (0 o más).',
  stockCritico: 'Ingresa el stock crítico (0 o más).',
};

const MAXIMO: Partial<Record<Campo, number>> = { codigoPtb: 20, nombre: 150 };

const AYUDA: Partial<Record<Campo, string>> = { codigoPtb: 'ayuda-codigoPtb', stockCritico: 'ayuda-umbrales' };

function noSoloEspacios(control: AbstractControl): ValidationErrors | null {
  const valor: unknown = control.value;
  return typeof valor === 'string' && valor !== '' && valor.trim() === '' ? { soloEspacios: true } : null;
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
  imports: [ReactiveFormsModule, RouterLink, EstadoVista],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './producto-formulario.html',
})
export class ProductoFormulario implements ConCambiosPendientes {
  /** Parámetro de ruta `:id` (withComponentInputBinding). Ausente en /productos/nuevo. */
  readonly id = input<string>();

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly productoService = inject(ProductoService);
  private readonly catalogoService = inject(CatalogoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly ETIQUETAS = ETIQUETAS;

  protected readonly esEdicion = computed(() => this.id() !== undefined);
  protected readonly idNumerico = computed(() => {
    const valor = this.id();
    if (valor === undefined) return null;
    const numero = Number(valor);
    return Number.isInteger(numero) && numero > 0 ? numero : null;
  });

  protected readonly producto = this.productoService.obtener(this.idNumerico);
  protected readonly categorias = this.catalogoService.listar(ENDPOINT_CATEGORIAS, false);
  protected readonly unidades = this.catalogoService.listar(ENDPOINT_UNIDADES, false);

  protected readonly productoActual = computed<Producto | null>(() =>
    this.producto.hasValue() ? this.producto.value() ?? null : null);

  protected readonly titulo = computed(() => {
    if (!this.esEdicion()) return 'Nuevo producto';
    const p = this.productoActual();
    return p ? `Editar: ${p.nombre}` : 'Editar producto';
  });

  protected readonly opcionesCategoria = computed(() => {
    const lista = this.categorias.hasValue() ? this.categorias.value() : [];
    const actual = this.productoActual()?.categoriaId;
    return lista.filter(c => c.activo || c.id === actual);
  });

  protected readonly opcionesUnidad = computed(() => {
    const lista = this.unidades.hasValue() ? this.unidades.value() : [];
    const actual = this.productoActual()?.unidadMedidaId;
    return lista.filter(u => u.activo || u.id === actual);
  });

  protected readonly cargandoDatos = computed(() =>
    this.categorias.status() === 'loading'
    || this.unidades.status() === 'loading'
    || (this.esEdicion() && this.producto.status() === 'loading'));

  protected readonly errorCarga = computed(() =>
    this.producto.error() ?? this.categorias.error() ?? this.unidades.error());

  protected readonly form = this.fb.group({
    codigoPtb: ['', [Validators.required, Validators.maxLength(20), noSoloEspacios]],
    nombre: ['', [Validators.required, Validators.maxLength(150), noSoloEspacios]],
    categoriaId: this.fb.control<number | null>(null, Validators.required),
    unidadMedidaId: this.fb.control<number | null>(null, Validators.required),
    esPerecible: [false],
    stockMinimo: this.fb.control<number | null>(0, [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)]),
    stockCritico: this.fb.control<number | null>(0, [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)]),
  }, { validators: [criticoMenorOIgualAMinimo] });

  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);
  private readonly enfocarTrasReactivar = signal(false);

  constructor() {
    effect(() => {
      const p = this.productoActual();
      if (!p) return;
      // No pisar ediciones en curso: solo aplicar el patch mientras el form
      // sigue intacto (evita que un reload() disparado por reactivar()/recargar()
      // borre silenciosamente cambios que el usuario ya escribió).
      if (!this.form.pristine) return;
      untracked(() => {
        this.form.reset({
          codigoPtb: p.codigoPtb,
          nombre: p.nombre,
          categoriaId: p.categoriaId,
          unidadMedidaId: p.unidadMedidaId,
          esPerecible: p.esPerecible,
          stockMinimo: p.stockMinimo,
          stockCritico: p.stockCritico,
        });
      });
    });

    effect(() => {
      if (this.enfocarTrasReactivar() && this.producto.status() === 'resolved') {
        this.enfocarTrasReactivar.set(false);
        afterNextRender(() => document.getElementById('titulo-estado-producto')?.focus(), { injector: this.injector });
      }
    });
  }

  tieneCambiosPendientes(): boolean {
    return this.form.dirty && !this.guardando();
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
      if (errores['maxlength']) return `${ETIQUETAS[campo]} admite como máximo ${MAXIMO[campo] ?? 0} caracteres.`;
      if (errores['min']) return `${ETIQUETAS[campo]} no puede ser negativo.`;
      if (errores['pattern']) return `${ETIQUETAS[campo]} debe ser un número entero.`;
      return `Revisa el campo ${ETIQUETAS[campo]}.`;
    }
    if (campo === 'stockCritico' && this.form.hasError('criticoMayorQueMinimo')) {
      return 'El stock crítico debe ser menor o igual al stock mínimo.';
    }
    return null;
  }

  protected describedBy(campo: Campo): string | null {
    const ids = [AYUDA[campo], this.mensajeError(campo) ? `error-${campo}` : undefined].filter(Boolean);
    return ids.length > 0 ? ids.join(' ') : null;
  }

  protected camposConError(): Campo[] {
    return this.enviado() ? CAMPOS.filter(c => this.mensajeError(c) !== null) : [];
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
    if (v.categoriaId === null || v.unidadMedidaId === null || v.stockMinimo === null || v.stockCritico === null) return;
    const request: ProductoRequest = {
      codigoPtb: v.codigoPtb.trim(),
      nombre: v.nombre.trim(),
      categoriaId: v.categoriaId,
      unidadMedidaId: v.unidadMedidaId,
      esPerecible: v.esPerecible,
      stockMinimo: v.stockMinimo,
      stockCritico: v.stockCritico,
    };
    const id = this.idNumerico();
    const operacion$ = id === null ? this.productoService.crear(request) : this.productoService.editar(id, request);
    this.guardando.set(true);
    operacion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: guardado => {
        this.form.markAsPristine();
        this.guardando.set(false);
        const texto = id === null ? `Producto '${guardado.nombre}' creado.` : `Cambios guardados en '${guardado.nombre}'.`;
        void this.router.navigate(['/productos']).then(() => this.notificaciones.exito(texto));
      },
      error: (err: unknown) => {
        this.guardando.set(false);
        this.aplicarErrorServidor(err);
      },
    });
  }

  protected deshabilitar(p: Producto): void {
    this.confirmacion.confirmar({
      titulo: `¿Deshabilitar '${p.nombre}'?`,
      descripcion: 'Dejará de aparecer para registrar movimientos y solicitudes. Su historial se conserva.',
      textoConfirmar: 'Deshabilitar producto',
    }).pipe(
      filter(confirmado => confirmado),
      switchMap(() => {
        this.guardando.set(true);
        return this.productoService.deshabilitar(p.id);
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        this.form.markAsPristine();
        this.guardando.set(false);
        void this.router.navigate(['/productos'])
          .then(() => this.notificaciones.exito(`Producto '${p.nombre}' deshabilitado.`));
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
    this.productoService.reactivar(p.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.guardando.set(false);
        this.notificaciones.exito(`Producto '${p.nombre}' reactivado.`);
        this.enfocarTrasReactivar.set(true);
        this.producto.reload();
      },
      error: (err: unknown) => {
        this.guardando.set(false);
        this.errorGeneral.set(traducirError(err).mensaje);
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
    afterNextRender(() => {
      const primero = CAMPOS.find(c => this.mensajeError(c) !== null);
      if (primero) document.getElementById(`campo-${primero}`)?.focus();
    }, { injector: this.injector });
  }
}

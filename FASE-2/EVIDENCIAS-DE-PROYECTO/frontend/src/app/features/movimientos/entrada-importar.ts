import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, debounceTime, filter, switchMap } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { descargarArchivo } from '../../shared/utils/descargar-archivo';
import { FilaEntrada, FilaEntradaValidada, ImportacionEntradaRequest } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

type CampoTexto = 'codigo' | 'numeroLote' | 'fechaVencimiento';
type CampoNumero = 'cantidad' | 'costoUnitario';
type Campo = CampoTexto | CampoNumero;

/**
 * Una fila de la vista previa. `lectura` guarda los problemas detectados al leer el Excel (por ejemplo una
 * fecha ilegible): el servidor no los vuelve a calcular al revalidar, así que siguen marcados hasta que el
 * usuario edita esa celda. `servidor` es el resultado de la última validación.
 */
interface FilaVista {
  datos: FilaEntrada;
  servidor: Record<string, string>;
  lectura: Record<string, string>;
}

const TIPO_POR_DEFECTO = 'COMPRA';
const MAX_NOTA = 300;

/** Rangos que acepta el servidor (enteros de Java); fuera de ellos ni se envía el valor. */
const RANGO_NUMERO: Record<CampoNumero, { min: number; max: number; mensaje: string }> = {
  cantidad: { min: 1, max: 1_000_000, mensaje: 'Debe ser un número entero entre 1 y 1.000.000.' },
  costoUnitario: { min: 0, max: 1_000_000_000, mensaje: 'Debe ser un número entero entre 0 y 1.000.000.000.' },
};

/**
 * Importación de entradas desde Excel: un archivo es una sola operación (todo o nada). Se lee el archivo,
 * se corrige en una vista previa editable que se revalida en el servidor, y recién entonces se cargan.
 */
@Component({
  selector: 'app-entrada-importar',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './entrada-importar.html',
})
export class EntradaImportar {
  private readonly movimientoService = inject(MovimientoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly maxNota = MAX_NOTA;

  protected readonly tipos = this.movimientoService.listarTipos(1);
  protected readonly opcionesTipo = computed(() => (this.tipos.hasValue() ? this.tipos.value() : []));

  protected readonly tipo = signal('');
  protected readonly nota = signal('');
  protected readonly archivo = signal<File | null>(null);

  protected readonly descargandoPlantilla = signal(false);
  protected readonly leyendo = signal(false);
  protected readonly validando = signal(false);
  protected readonly cargando = signal(false);
  protected readonly intentoLeerSinArchivo = signal(false);

  /** `null` mientras no se ha leído ningún archivo. */
  protected readonly filas = signal<FilaVista[] | null>(null);

  protected readonly total = computed(() => this.filas()?.length ?? 0);
  protected readonly conErrores = computed(() => (this.filas() ?? []).filter((f) => this.cantidadErrores(f) > 0).length);
  protected readonly validas = computed(() => this.total() - this.conErrores());
  protected readonly puedeCargar = computed(
    () =>
      this.total() > 0 &&
      this.conErrores() === 0 &&
      this.tipo() !== '' &&
      this.nota().length <= MAX_NOTA &&
      !this.validando() &&
      !this.cargando(),
  );

  private readonly revalidar$ = new Subject<void>();

  constructor() {
    // Misma preselección que el formulario de una entrada: «Compra» si existe, si no el primer tipo.
    effect(() => {
      const tipos = this.opcionesTipo();
      if (tipos.length > 0 && this.tipo() === '') {
        this.tipo.set((tipos.find((t) => t.codigo === TIPO_POR_DEFECTO) ?? tipos[0]).codigo);
      }
    });

    this.revalidar$
      .pipe(
        debounceTime(300),
        switchMap(() => {
          const datos = (this.filas() ?? []).map((f) => f.datos);
          if (datos.length === 0) {
            this.validando.set(false);
            return EMPTY;
          }
          return this.movimientoService.validarEntradas(this.solicitud(datos, true)).pipe(
            catchError((err: unknown) => {
              this.validando.set(false);
              this.notificaciones.error(traducirError(err).mensaje);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((resultado) => {
        this.aplicarValidacion(resultado);
        this.validando.set(false);
      });
  }

  protected descargarPlantilla(): void {
    if (this.descargandoPlantilla()) return;
    this.descargandoPlantilla.set(true);
    this.movimientoService
      .descargarPlantillaEntradas()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          this.descargandoPlantilla.set(false);
          descargarArchivo(blob, 'plantilla-entradas.xlsx');
        },
        error: () => {
          this.descargandoPlantilla.set(false);
          this.notificaciones.error('No se pudo descargar la plantilla. Inténtalo de nuevo.');
        },
      });
  }

  protected elegirArchivo(campo: HTMLInputElement): void {
    this.archivo.set(campo.files?.item(0) ?? null);
    this.intentoLeerSinArchivo.set(false);
  }

  protected leerArchivo(): void {
    if (this.leyendo()) return;
    const archivo = this.archivo();
    if (!archivo) {
      this.intentoLeerSinArchivo.set(true);
      return;
    }
    this.leyendo.set(true);
    this.movimientoService
      .leerEntradas(archivo)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (resultado) => {
          this.leyendo.set(false);
          this.filas.set(resultado.map((r) => ({ datos: r.datos, servidor: r.errores, lectura: { ...r.errores } })));
        },
        error: (err: unknown) => {
          this.leyendo.set(false);
          this.notificaciones.error(traducirError(err).mensaje);
        },
      });
  }

  protected errorDe(f: FilaVista, campo: Campo): string | null {
    return this.errores(f)[campo] ?? null;
  }

  protected cantidadErrores(f: FilaVista): number {
    return Object.keys(this.errores(f)).length;
  }

  protected editarTexto(fila: number, campo: CampoTexto, valor: string): void {
    this.actualizar(fila, campo, valor.trim() === '' ? null : valor.trim());
  }

  protected editarNumero(fila: number, campo: CampoNumero, valor: string): void {
    if (valor.trim() === '') {
      this.actualizar(fila, campo, null);
      return;
    }
    const numero = Number(valor);
    const { min, max, mensaje } = RANGO_NUMERO[campo];
    if (!Number.isInteger(numero) || numero < min || numero > max) {
      // No se envía al servidor: la celda queda vacía en el modelo y marcada con el motivo.
      this.actualizar(fila, campo, null, mensaje);
      return;
    }
    this.actualizar(fila, campo, numero);
  }

  protected revalidar(): void {
    this.validando.set(true);
    this.revalidar$.next();
  }

  protected cargar(): void {
    if (!this.puedeCargar()) return;
    const datos = (this.filas() ?? []).map((f) => f.datos);
    const total = datos.length;
    this.confirmacion
      .confirmar({
        titulo: `¿Cargar ${total} ${total === 1 ? 'entrada' : 'entradas'}?`,
        descripcion: 'Se registrarán todas a la vez como una sola operación y no se podrán editar después.',
        textoConfirmar: 'Cargar entradas',
      })
      .pipe(
        filter((confirmado) => confirmado),
        switchMap(() => {
          this.cargando.set(true);
          return this.movimientoService.importarEntradas(this.solicitud(datos));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (operacion) => {
          this.cargando.set(false);
          const lotes = operacion.lineas.length;
          this.notificaciones.exito(`Se cargaron ${lotes} ${lotes === 1 ? 'lote' : 'lotes'}.`);
          void this.router.navigateByUrl('/movimientos');
        },
        error: (err: unknown) => {
          this.cargando.set(false);
          this.notificaciones.error(traducirError(err).mensaje);
          this.revalidar();
        },
      });
  }

  private errores(f: FilaVista): Record<string, string> {
    // El problema de lectura (más específico) prevalece sobre el «obligatorio» que el servidor da a una celda vacía.
    return { ...f.servidor, ...f.lectura };
  }

  /**
   * El servidor exige tipo y nota válidos aun al validar (donde no los usa para nada): se envían valores
   * aceptables para no rechazar la revalidación mientras el usuario todavía no termina de llenarlos.
   */
  private solicitud(filas: FilaEntrada[], paraValidar = false): ImportacionEntradaRequest {
    const nota = this.nota().trim();
    return {
      tipo: this.tipo() || (paraValidar ? TIPO_POR_DEFECTO : ''),
      nota: nota === '' ? null : paraValidar ? nota.slice(0, MAX_NOTA) : nota,
      filas,
    };
  }

  private actualizar(fila: number, campo: Campo, valor: string | number | null, errorLectura?: string): void {
    this.filas.update((lista) =>
      (lista ?? []).map((f) => {
        if (f.datos.fila !== fila) return f;
        const { [campo]: _descartado, ...lectura } = f.lectura;
        if (errorLectura) lectura[campo] = errorLectura;
        return { ...f, datos: { ...f.datos, [campo]: valor }, lectura };
      }),
    );
    this.revalidar();
  }

  /**
   * Aplica el resultado del servidor: reemplaza los errores y el producto resuelto (que depende del código y
   * lo calcula el servidor), sin pisar lo que el usuario siguió editando mientras tanto.
   */
  private aplicarValidacion(resultado: FilaEntradaValidada[]): void {
    const porFila = new Map(resultado.map((r) => [r.datos.fila, r]));
    this.filas.update((lista) =>
      (lista ?? []).map((f) => {
        const r = porFila.get(f.datos.fila);
        if (!r) return f;
        return {
          ...f,
          servidor: r.errores,
          datos: { ...f.datos, productoId: r.datos.productoId, productoNombre: r.datos.productoNombre },
        };
      }),
    );
  }
}

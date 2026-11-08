import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, Subject, catchError, debounceTime, filter, switchMap } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { ThOrden } from '../../shared/ordenamiento/th-orden';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { FilaEntrada, FilaEntradaValidada, ImportacionEntradaRequest } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

type Columna =
  | 'fila'
  | 'estado'
  | 'codigo'
  | 'producto'
  | 'numeroLote'
  | 'fechaVencimiento'
  | 'cantidad'
  | 'costoUnitario';

type CampoTexto = 'codigo' | 'numeroLote' | 'fechaVencimiento';
type CampoNumero = 'cantidad' | 'costoUnitario';
type Campo = CampoTexto | CampoNumero;
type Vista = 'todas' | 'errores';

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
 * Vista previa editable de la importación de entradas: un archivo es una sola operación (todo o nada). Valida
 * en el servidor cada vez que se edita una fila; el orden y el filtro se recalculan solo al pedirlo
 * (encabezado, filtro o «Actualizar orden») para que una fila no salte de lugar mientras se corrige.
 */
@Component({
  selector: 'app-entrada-importar',
  imports: [ThOrden, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './entrada-importar.html',
})
export class EntradaImportar implements OnInit {
  readonly filasIniciales = input.required<FilaEntradaValidada[]>();
  readonly importado = output<number>();
  readonly cerrarSolicitado = output<void>();

  private readonly movimientoService = inject(MovimientoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly maxNota = MAX_NOTA;
  protected readonly opcionesTamano = [10, 25, 50, 100] as const;

  protected readonly tipos = this.movimientoService.listarTipos(1);
  protected readonly opcionesTipo = computed(() => (this.tipos.hasValue() ? this.tipos.value() : []));

  protected readonly tipo = signal('');
  protected readonly nota = signal('');

  protected readonly filas = signal<FilaVista[]>([]);
  protected readonly validando = signal(false);
  protected readonly cargando = signal(false);

  protected readonly vista = signal<Vista>('todas');
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = signal(25);
  protected readonly orden = new Orden<Columna>({ columna: 'estado', direccion: 'desc' }, () => this.reordenar());

  /** Las filas en el orden en que se muestran, fijado al pedir un reordenamiento. */
  private readonly idsMostrados = signal<number[]>([]);

  protected readonly conErrores = computed(() => this.filas().filter((f) => this.cantidadErrores(f) > 0).length);
  protected readonly validas = computed(() => this.filas().length - this.conErrores());
  protected readonly puedeCargar = computed(
    () =>
      this.filas().length > 0 &&
      this.conErrores() === 0 &&
      this.tipo() !== '' &&
      this.nota().length <= MAX_NOTA &&
      !this.validando() &&
      !this.cargando(),
  );

  /** El orden que resultaría ahora mismo; si difiere del mostrado, se ofrece actualizarlo. */
  private readonly idsIdeales = computed(() => {
    const lista = this.vista() === 'errores' ? this.filas().filter((f) => this.cantidadErrores(f) > 0) : this.filas();
    return ordenar(lista, this.orden, this.extractores).map((f) => f.datos.fila ?? 0);
  });
  protected readonly ordenDesactualizado = computed(
    () => this.idsIdeales().join(',') !== this.idsMostrados().join(','),
  );

  protected readonly mostradas = computed(() => {
    const porId = new Map(this.filas().map((f) => [f.datos.fila ?? 0, f]));
    return this.idsMostrados()
      .map((id) => porId.get(id))
      .filter((f): f is FilaVista => f !== undefined);
  });
  protected readonly paginaActual = computed(() =>
    Math.min(this.pagina(), Math.max(1, Math.ceil(this.mostradas().length / this.tamanoPagina()))),
  );
  protected readonly paginadas = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.mostradas().slice(inicio, inicio + this.tamanoPagina());
  });

  private readonly revalidar$ = new Subject<void>();

  private readonly extractores: Extractores<FilaVista, Columna> = {
    fila: (f) => f.datos.fila,
    estado: (f) => this.cantidadErrores(f),
    codigo: (f) => f.datos.codigo,
    producto: (f) => f.datos.productoNombre,
    numeroLote: (f) => f.datos.numeroLote,
    fechaVencimiento: (f) => f.datos.fechaVencimiento,
    cantidad: (f) => f.datos.cantidad,
    costoUnitario: (f) => f.datos.costoUnitario,
  };

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
          const datos = this.filas().map((f) => f.datos);
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

  ngOnInit(): void {
    this.filas.set(
      this.filasIniciales().map((r) => ({ datos: r.datos, servidor: r.errores, lectura: { ...r.errores } })),
    );
    this.reordenar();
  }

  protected errorDe(f: FilaVista, campo: Campo): string | null {
    return this.errores(f)[campo] ?? null;
  }

  protected cantidadErrores(f: FilaVista): number {
    return Object.keys(this.errores(f)).length;
  }

  protected claseErrorFila(f: FilaVista): string {
    return this.cantidadErrores(f) > 0 ? 'fila-con-error' : '';
  }

  protected reordenar(): void {
    untracked(() => {
      this.idsMostrados.set(this.idsIdeales());
      this.pagina.set(1);
    });
  }

  protected cambiarVista(valor: string): void {
    this.vista.set(valor === 'errores' ? 'errores' : 'todas');
    this.reordenar();
  }

  protected cambiarPagina(pagina: number): void {
    this.pagina.set(pagina);
  }

  protected cambiarTamanoPagina(tamano: number): void {
    this.tamanoPagina.set(tamano);
    this.pagina.set(1);
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

  protected quitar(fila: number): void {
    this.filas.update((lista) => lista.filter((f) => f.datos.fila !== fila));
    this.revalidar();
  }

  solicitarCierre(): void {
    if (this.cargando()) return;
    if (this.filas().length === 0) {
      this.cerrarSolicitado.emit();
      return;
    }
    this.confirmacion
      .confirmar({
        titulo: '¿Descartar la importación?',
        descripcion: 'No se cargará ninguna entrada y se perderán las correcciones hechas.',
        textoConfirmar: 'Descartar',
      })
      .pipe(
        filter((confirmado) => confirmado),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.cerrarSolicitado.emit());
  }

  protected cargar(): void {
    if (!this.puedeCargar()) return;
    const datos = this.filas().map((f) => f.datos);
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
          this.importado.emit(operacion.lineas.length);
        },
        error: (err: unknown) => {
          this.cargando.set(false);
          this.notificaciones.error(traducirError(err).mensaje);
          this.revalidar();
        },
      });
  }

  private revalidar(): void {
    this.validando.set(true);
    this.revalidar$.next();
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
      lista.map((f) => {
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
      lista.map((f) => {
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

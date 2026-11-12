import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
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
import {
  CatalogoService,
  ENDPOINT_CATEGORIAS,
  ENDPOINT_UNIDADES,
} from '../../shared/catalogo/catalogo.service';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { ThOrden } from '../../shared/ordenamiento/th-orden';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { FilaImportacion, FilaValidada } from './producto.modelo';
import { ProductoService } from './producto.service';

type Columna =
  | 'fila'
  | 'estado'
  | 'codigoPtv'
  | 'codigo'
  | 'nombre'
  | 'categoria'
  | 'unidad'
  | 'perecible'
  | 'stockMinimo'
  | 'stockCritico'
  | 'costoUnitario';

type CampoTexto = 'codigoPtv' | 'codigo' | 'nombre';
type CampoNumero = 'stockMinimo' | 'stockCritico' | 'costoUnitario';
type CampoId = 'categoriaId' | 'unidadMedidaId';
type Vista = 'todas' | 'errores';

/**
 * Vista previa editable de una importación masiva. Valida en el servidor cada vez que se edita una fila;
 * el orden y el filtro se recalculan solo al pedirlo (encabezado, filtro o «Actualizar orden») para que una
 * fila no salte de lugar mientras se está corrigiendo.
 */
@Component({
  selector: 'app-producto-importar',
  imports: [ThOrden, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './producto-importar.html',
})
export class ProductoImportar implements OnInit {
  readonly filasIniciales = input.required<FilaValidada[]>();
  readonly importado = output<number>();
  readonly cerrarSolicitado = output<void>();

  private readonly productoService = inject(ProductoService);
  private readonly catalogoService = inject(CatalogoService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly opcionesTamano = [10, 25, 50, 100] as const;

  private readonly categoriasRecurso = this.catalogoService.listar(ENDPOINT_CATEGORIAS, true);
  private readonly unidadesRecurso = this.catalogoService.listar(ENDPOINT_UNIDADES, true);
  protected readonly categorias = computed(() =>
    this.categoriasRecurso.hasValue() ? this.categoriasRecurso.value() : [],
  );
  protected readonly unidades = computed(() =>
    this.unidadesRecurso.hasValue() ? this.unidadesRecurso.value() : [],
  );

  protected readonly filas = signal<FilaValidada[]>([]);
  protected readonly validando = signal(false);
  protected readonly creando = signal(false);

  protected readonly vista = signal<Vista>('todas');
  protected readonly pagina = signal(1);
  protected readonly tamanoPagina = signal(25);
  protected readonly orden = new Orden<Columna>({ columna: 'estado', direccion: 'desc' }, () => this.reordenar());

  /** Las filas en el orden en que se muestran, fijado al pedir un reordenamiento. */
  private readonly idsMostrados = signal<number[]>([]);

  protected readonly validas = computed(() => this.filas().filter((f) => !this.tieneErrores(f)).length);
  protected readonly conErrores = computed(() => this.filas().length - this.validas());

  /** El orden que resultaría ahora mismo; si difiere del mostrado, se ofrece actualizarlo. */
  private readonly idsIdeales = computed(() => {
    const lista = this.vista() === 'errores' ? this.filas().filter((f) => this.tieneErrores(f)) : this.filas();
    return ordenar(lista, this.orden, this.extractores).map((f) => f.datos.fila ?? 0);
  });
  protected readonly ordenDesactualizado = computed(
    () => this.idsIdeales().join(',') !== this.idsMostrados().join(','),
  );

  protected readonly mostradas = computed(() => {
    const porId = new Map(this.filas().map((f) => [f.datos.fila ?? 0, f]));
    return this.idsMostrados()
      .map((id) => porId.get(id))
      .filter((f): f is FilaValidada => f !== undefined);
  });
  protected readonly paginaActual = computed(() =>
    Math.min(this.pagina(), Math.max(1, Math.ceil(this.mostradas().length / this.tamanoPagina()))),
  );
  protected readonly paginadas = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.mostradas().slice(inicio, inicio + this.tamanoPagina());
  });

  private readonly revalidar$ = new Subject<void>();

  private readonly extractores: Extractores<FilaValidada, Columna> = {
    fila: (f) => f.datos.fila,
    estado: (f) => Object.keys(f.errores).length,
    codigoPtv: (f) => f.datos.codigoPtv,
    codigo: (f) => f.datos.codigo,
    nombre: (f) => f.datos.nombre,
    categoria: (f) => this.nombreDe(this.categorias(), f.datos.categoriaId) ?? f.datos.categoriaTexto,
    unidad: (f) => this.nombreDe(this.unidades(), f.datos.unidadMedidaId) ?? f.datos.unidadTexto,
    perecible: (f) => Number(f.datos.esPerecible),
    stockMinimo: (f) => f.datos.stockMinimo,
    stockCritico: (f) => f.datos.stockCritico,
    costoUnitario: (f) => f.datos.costoUnitario,
  };

  constructor() {
    this.revalidar$
      .pipe(
        debounceTime(300),
        switchMap(() => {
          const datos = this.filas().map((f) => f.datos);
          if (datos.length === 0) {
            this.validando.set(false);
            return EMPTY;
          }
          return this.productoService.validarImportacion(datos).pipe(
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
        const erroresPorFila = new Map(resultado.map((r) => [r.datos.fila, r.errores]));
        // Solo se actualizan los errores: si el usuario siguió editando, sus datos no se pisan.
        this.filas.update((lista) => lista.map((f) => ({ ...f, errores: erroresPorFila.get(f.datos.fila) ?? f.errores })));
        this.validando.set(false);
      });
  }

  ngOnInit(): void {
    this.filas.set(this.filasIniciales());
    this.reordenar();
  }

  protected tieneErrores(f: FilaValidada): boolean {
    return Object.keys(f.errores).length > 0;
  }

  protected cantidadErrores(f: FilaValidada): number {
    return Object.keys(f.errores).length;
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
    this.actualizar(fila, { [campo]: valor.trim() === '' ? null : valor });
  }

  protected editarNumero(fila: number, campo: CampoNumero, valor: string): void {
    const numero = valor.trim() === '' ? null : Math.round(Number(valor));
    this.actualizar(fila, { [campo]: numero === null || Number.isNaN(numero) ? null : numero });
  }

  protected editarId(fila: number, campo: CampoId, valor: string): void {
    this.actualizar(fila, { [campo]: valor === '' ? null : Number(valor) });
  }

  protected editarPerecible(fila: number, marcado: boolean): void {
    this.actualizar(fila, { esPerecible: marcado });
  }

  protected quitar(fila: number): void {
    this.filas.update((lista) => lista.filter((f) => f.datos.fila !== fila));
    this.programarRevalidacion();
  }

  solicitarCierre(): void {
    if (this.creando()) return;
    if (this.filas().length === 0) {
      this.cerrarSolicitado.emit();
      return;
    }
    this.confirmacion
      .confirmar({
        titulo: '¿Descartar la importación?',
        descripcion: 'No se creará ningún producto y se perderán las correcciones hechas.',
        textoConfirmar: 'Descartar',
      })
      .pipe(
        filter((confirmado) => confirmado),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.cerrarSolicitado.emit());
  }

  protected crear(): void {
    if (this.creando() || this.validando() || this.conErrores() > 0 || this.filas().length === 0) return;
    const total = this.filas().length;
    this.confirmacion
      .confirmar({
        titulo: `¿Crear ${total} ${total === 1 ? 'producto' : 'productos'}?`,
        descripcion: 'Se crearán todos a la vez. Podrás editarlos después desde la lista de productos.',
        textoConfirmar: 'Crear productos',
      })
      .pipe(
        filter((confirmado) => confirmado),
        switchMap(() => {
          this.creando.set(true);
          return this.productoService.importar(this.filas().map((f) => f.datos));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (creados) => {
          this.creando.set(false);
          this.importado.emit(creados.length);
        },
        error: (err: unknown) => {
          this.creando.set(false);
          this.notificaciones.error(traducirError(err).mensaje);
          this.programarRevalidacion();
        },
      });
  }

  protected claseErrorFila(f: FilaValidada): string {
    return this.tieneErrores(f) ? 'fila-con-error' : '';
  }

  private actualizar(fila: number, cambios: Partial<FilaImportacion>): void {
    this.filas.update((lista) =>
      lista.map((f) => (f.datos.fila === fila ? { ...f, datos: { ...f.datos, ...cambios } } : f)),
    );
    this.programarRevalidacion();
  }

  private programarRevalidacion(): void {
    this.validando.set(true);
    this.revalidar$.next();
  }

  private nombreDe(lista: { id: number; nombre: string }[], id: number | null): string | null {
    return id === null ? null : (lista.find((i) => i.id === id)?.nombre ?? null);
  }
}

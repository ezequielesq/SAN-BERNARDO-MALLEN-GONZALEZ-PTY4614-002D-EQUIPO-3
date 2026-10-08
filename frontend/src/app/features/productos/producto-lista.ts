import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/auth/auth.service';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Modal } from '../../shared/modal/modal';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { descargarArchivo } from '../../shared/utils/descargar-archivo';
import { ProductoFormulario } from './producto-formulario';
import { Producto } from './producto.modelo';
import { ProductoService } from './producto.service';

type ColumnaOrdenProducto = 'codigo' | 'nombre' | 'categoriaNombre' | 'estado';

interface OpcionCategoria {
  id: number;
  nombre: string;
}

function normalizar(texto: string): string {
  return texto
    .toLocaleLowerCase('es')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .trim();
}

@Component({
  selector: 'app-producto-lista',
  imports: [EstadoVista, EstadoBadge, Paginacion, Modal, ProductoFormulario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './producto-lista.html',
})
export class ProductoLista {
  private readonly productoService = inject(ProductoService);
  private readonly auth = inject(AuthService);
  private readonly injector = inject(Injector);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly puedeGestionar = computed(() => {
    const rol = this.auth.rol();
    return rol === 'ADMIN' || rol === 'BODEGUERO';
  });
  protected readonly soloActivos = signal(true);
  protected readonly busqueda = signal('');
  protected readonly categoriaId = signal<number | null>(null);
  protected readonly paginaActual = signal(1);
  protected readonly tamanoPagina = signal(10);
  protected readonly exportando = signal(false);
  protected readonly columnaOrden = signal<ColumnaOrdenProducto | null>(null);
  protected readonly direccionOrden = signal<'asc' | 'desc'>('asc');

  protected readonly modalProducto = signal<'nuevo' | number | null>(null);

  protected readonly tituloModal = computed(() => {
    const m = this.modalProducto();
    return m === 'nuevo' ? 'Nuevo producto' : 'Editar producto';
  });

  protected readonly productoIdModal = computed<number | null>(() => {
    const m = this.modalProducto();
    return m === 'nuevo' ? null : m;
  });

  protected readonly productos = this.productoService.listar(this.soloActivos);
  protected readonly todos = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

  protected readonly categorias = computed<OpcionCategoria[]>(() => {
    const mapa = new Map<number, string>();
    for (const p of this.todos()) mapa.set(p.categoriaId, p.categoriaNombre);
    return [...mapa]
      .map(([id, nombre]) => ({ id, nombre }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  protected readonly filtrados = computed(() => {
    const texto = normalizar(this.busqueda());
    const categoria = this.categoriaId();
    return this.todos().filter(
      (p) =>
        (categoria === null || p.categoriaId === categoria) &&
        (texto === '' ||
          normalizar(p.nombre).includes(texto) ||
          normalizar(this.codigoDe(p)).includes(texto)),
    );
  });

  protected readonly ordenados = computed(() => {
    const columna = this.columnaOrden();
    const lista = [...this.filtrados()];
    if (columna === null) return lista;
    const dir = this.direccionOrden() === 'asc' ? 1 : -1;
    lista.sort((a, b) => {
      switch (columna) {
        case 'codigo':
          return (
            this.codigoDe(a).localeCompare(this.codigoDe(b), 'es', { numeric: true }) * dir
          );
        case 'nombre':
          return a.nombre.localeCompare(b.nombre, 'es') * dir;
        case 'categoriaNombre':
          return a.categoriaNombre.localeCompare(b.categoriaNombre, 'es') * dir;
        case 'estado':
          return (Number(a.activo) - Number(b.activo)) * dir;
      }
    });
    return lista;
  });

  protected readonly mensajeVacio = computed(() =>
    this.puedeGestionar()
      ? "Aún no hay productos. Crea el primero con 'Nuevo producto'."
      : 'Aún no hay productos registrados.',
  );

  protected readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.ordenados().slice(inicio, inicio + this.tamanoPagina());
  });

  constructor() {
    effect(() => {
      this.filtrados();
      untracked(() => this.paginaActual.set(1));
    });
  }

  protected cambiarCategoria(valor: string): void {
    this.categoriaId.set(valor === '' ? null : Number(valor));
  }

  /** El código con el que se identifica el producto: PTV (vinos y licores) o el numérico. */
  protected codigoDe(p: Producto): string {
    return p.codigoPtv ?? p.codigo ?? '';
  }

  protected limpiarFiltros(): void {
    this.busqueda.set('');
    this.categoriaId.set(null);
    afterNextRender(() => document.getElementById('buscar-producto')?.focus(), {
      injector: this.injector,
    });
  }

  protected alGuardarProducto(): void {
    this.productos.reload();
  }

  protected cerrarModalProducto(): void {
    this.modalProducto.set(null);
  }

  protected ordenarPor(columna: ColumnaOrdenProducto): void {
    if (this.columnaOrden() === columna) {
      this.direccionOrden.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.columnaOrden.set(columna);
      this.direccionOrden.set('asc');
    }
  }

  protected iconoOrden(columna: ColumnaOrdenProducto): string {
    if (this.columnaOrden() !== columna) return 'fa-sort';
    return this.direccionOrden() === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  protected ariaSort(columna: ColumnaOrdenProducto): 'ascending' | 'descending' | null {
    if (this.columnaOrden() !== columna) return null;
    return this.direccionOrden() === 'asc' ? 'ascending' : 'descending';
  }

  protected cambiarPagina(pagina: number): void {
    this.paginaActual.set(pagina);
  }

  protected cambiarTamanoPagina(tamano: number): void {
    this.tamanoPagina.set(tamano);
    this.paginaActual.set(1);
  }

  protected exportar(formato: 'xlsx' | 'pdf'): void {
    if (this.exportando()) return;
    this.exportando.set(true);
    const operacion$ =
      formato === 'xlsx'
        ? this.productoService.exportarXlsx(this.soloActivos())
        : this.productoService.exportarPdf(this.soloActivos());
    operacion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (blob) => {
        this.exportando.set(false);
        descargarArchivo(blob, `productos.${formato}`);
      },
      error: () => {
        this.exportando.set(false);
        this.notificaciones.error(`No se pudo generar el archivo ${formato.toUpperCase()}. Inténtalo de nuevo.`);
      },
    });
  }
}

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
import { Orden, ordenar } from '../../shared/ordenamiento/orden';
import { ThOrden } from '../../shared/ordenamiento/th-orden';
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
  imports: [EstadoVista, EstadoBadge, Paginacion, ThOrden, Modal, ProductoFormulario],
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
  protected readonly orden = new Orden<ColumnaOrdenProducto>(null, () => this.paginaActual.set(1));

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
          normalizar(p.codigoPtv ?? '').includes(texto) ||
          normalizar(p.codigo ?? '').includes(texto)),
    );
  });

  protected readonly ordenados = computed(() =>
    ordenar(this.filtrados(), this.orden, {
      codigo: (p) => this.codigoDe(p),
      nombre: (p) => p.nombre,
      categoriaNombre: (p) => p.categoriaNombre,
      estado: (p) => Number(p.activo),
    }),
  );

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

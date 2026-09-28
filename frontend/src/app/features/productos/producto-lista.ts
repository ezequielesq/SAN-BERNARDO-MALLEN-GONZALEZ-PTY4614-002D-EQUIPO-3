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
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { descargarArchivo } from '../../shared/utils/descargar-archivo';
import { ProductoService } from './producto.service';

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
  imports: [RouterLink, EstadoVista, EstadoBadge, Paginacion],
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
          normalizar(p.codigoPtv).includes(texto)),
    );
  });

  protected readonly mensajeVacio = computed(() =>
    this.puedeGestionar()
      ? "Aún no hay productos. Crea el primero con 'Nuevo producto'."
      : 'Aún no hay productos registrados.',
  );

  protected readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.filtrados().slice(inicio, inicio + this.tamanoPagina());
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

  protected limpiarFiltros(): void {
    this.busqueda.set('');
    this.categoriaId.set(null);
    afterNextRender(() => document.getElementById('buscar-producto')?.focus(), {
      injector: this.injector,
    });
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

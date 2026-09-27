import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { ProductoService } from './producto.service';

interface OpcionCategoria {
  id: number;
  nombre: string;
}

function normalizar(texto: string): string {
  return texto.toLocaleLowerCase('es').normalize('NFD').replace(/\p{Diacritic}/gu, '').trim();
}

@Component({
  selector: 'app-producto-lista',
  imports: [RouterLink, EstadoVista, EstadoBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './producto-lista.html',
})
export class ProductoLista {
  private readonly productoService = inject(ProductoService);
  private readonly auth = inject(AuthService);

  protected readonly puedeGestionar = computed(() => {
    const rol = this.auth.rol();
    return rol === 'ADMIN' || rol === 'BODEGUERO';
  });
  protected readonly soloActivos = signal(true);
  protected readonly busqueda = signal('');
  protected readonly categoriaId = signal<number | null>(null);

  protected readonly productos = this.productoService.listar(this.soloActivos);
  protected readonly todos = computed(() => (this.productos.hasValue() ? this.productos.value() : []));

  protected readonly categorias = computed<OpcionCategoria[]>(() => {
    const mapa = new Map<number, string>();
    for (const p of this.todos()) mapa.set(p.categoriaId, p.categoriaNombre);
    return [...mapa].map(([id, nombre]) => ({ id, nombre })).sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  protected readonly filtrados = computed(() => {
    const texto = normalizar(this.busqueda());
    const categoria = this.categoriaId();
    return this.todos().filter(p =>
      (categoria === null || p.categoriaId === categoria)
      && (texto === '' || normalizar(p.nombre).includes(texto) || normalizar(p.codigoPtb).includes(texto)));
  });

  protected readonly mensajeVacio = computed(() =>
    this.puedeGestionar()
      ? "Aún no hay productos. Crea el primero con 'Nuevo producto'."
      : 'Aún no hay productos registrados.');

  protected cambiarCategoria(valor: string): void {
    this.categoriaId.set(valor === '' ? null : Number(valor));
  }

  protected limpiarFiltros(): void {
    this.busqueda.set('');
    this.categoriaId.set(null);
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ProductoService } from '../productos/producto.service';
import { AnaliticaService } from './analitica.service';

interface OpcionCategoria {
  id: number;
  nombre: string;
}

function formatearFecha(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-analitica-mas-usados',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-mas-usados.html',
})
export class AnaliticaMasUsados {
  private readonly analiticaService = inject(AnaliticaService);
  private readonly productoService = inject(ProductoService);

  private readonly hoy = new Date();
  protected readonly desdeFiltro = signal(formatearFecha(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), 1)));
  protected readonly hastaFiltro = signal(formatearFecha(this.hoy));
  protected readonly categoriaId = signal<number | null>(null);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly categorias = computed<OpcionCategoria[]>(() => {
    if (!this.productos.hasValue()) return [];
    const mapa = new Map<number, string>();
    for (const p of this.productos.value()) mapa.set(p.categoriaId, p.categoriaNombre);
    return [...mapa]
      .map(([id, nombre]) => ({ id, nombre }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  private readonly filtro = computed(() => ({
    desde: this.desdeFiltro(),
    hasta: this.hastaFiltro(),
    categoriaId: this.categoriaId(),
  }));

  protected readonly masUsados = this.analiticaService.masUsados(this.filtro);
  protected readonly filas = computed(() => (this.masUsados.hasValue() ? this.masUsados.value() : []));

  protected cambiarDesde(valor: string): void {
    if (valor !== '') this.desdeFiltro.set(valor);
  }

  protected cambiarHasta(valor: string): void {
    if (valor !== '') this.hastaFiltro.set(valor);
  }

  protected cambiarCategoria(valor: string): void {
    this.categoriaId.set(valor === '' ? null : Number(valor));
  }
}

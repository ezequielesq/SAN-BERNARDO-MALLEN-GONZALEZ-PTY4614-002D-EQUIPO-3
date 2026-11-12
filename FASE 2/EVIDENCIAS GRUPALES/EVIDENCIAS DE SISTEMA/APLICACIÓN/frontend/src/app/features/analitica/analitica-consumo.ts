import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MovimientoCalendario } from '../movimientos/movimiento-calendario';
import { ProductoService } from '../productos/producto.service';
import { AgruparPor, ConsumoDto } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';

type VerPor = 'SEMANA' | 'MES';

interface OpcionCategoria {
  id: number;
  nombre: string;
}

function inicioDeSemana(fecha: Date): Date {
  const dia = fecha.getDay();
  const desplazamiento = dia === 0 ? -6 : 1 - dia;
  const inicio = new Date(fecha);
  inicio.setDate(fecha.getDate() + desplazamiento);
  return inicio;
}

function formatearFecha(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-analitica-consumo',
  imports: [MovimientoCalendario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-consumo.html',
})
export class AnaliticaConsumo {
  private readonly analiticaService = inject(AnaliticaService);
  private readonly productoService = inject(ProductoService);

  protected readonly verPor = signal<VerPor>('MES');
  protected readonly agruparPor = signal<AgruparPor>('PRODUCTO');
  protected readonly categoriaId = signal<number | null>(null);

  private readonly hoy = new Date();
  protected readonly desdeFiltro = signal(formatearFecha(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), 1)));
  protected readonly hastaFiltro = signal(
    formatearFecha(new Date(this.hoy.getFullYear(), this.hoy.getMonth() + 1, 0)),
  );

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
    agruparPor: this.agruparPor(),
    categoriaId: this.categoriaId(),
  }));

  protected readonly consumo = this.analiticaService.consumo(this.filtro);
  protected readonly filas = computed<ConsumoDto[]>(() => (this.consumo.hasValue() ? this.consumo.value() : []));

  protected readonly ordenadas = computed(() =>
    [...this.filas()].sort((a, b) => b.salidaCantidad - a.salidaCantidad),
  );

  protected etiquetaColumna(): string {
    return this.agruparPor() === 'CATEGORIA' ? 'Categoría' : 'Producto';
  }

  protected cambiarVerPor(valor: VerPor): void {
    this.verPor.set(valor);
  }

  protected cambiarAgruparPor(valor: AgruparPor): void {
    this.agruparPor.set(valor);
  }

  protected cambiarCategoria(valor: string): void {
    this.categoriaId.set(valor === '' ? null : Number(valor));
  }

  protected cambiarDesde(valor: string): void {
    if (valor !== '') this.desdeFiltro.set(valor);
  }

  protected cambiarHasta(valor: string): void {
    if (valor !== '') this.hastaFiltro.set(valor);
  }

  protected alSeleccionarDiaCalendario(fecha: string | null): void {
    if (fecha === null) return;
    const dia = new Date(`${fecha}T00:00:00`);
    if (this.verPor() === 'SEMANA') {
      const inicio = inicioDeSemana(dia);
      const fin = new Date(inicio);
      fin.setDate(inicio.getDate() + 6);
      this.desdeFiltro.set(formatearFecha(inicio));
      this.hastaFiltro.set(formatearFecha(fin));
    } else {
      const inicio = new Date(dia.getFullYear(), dia.getMonth(), 1);
      const fin = new Date(dia.getFullYear(), dia.getMonth() + 1, 0);
      this.desdeFiltro.set(formatearFecha(inicio));
      this.hastaFiltro.set(formatearFecha(fin));
    }
  }
}

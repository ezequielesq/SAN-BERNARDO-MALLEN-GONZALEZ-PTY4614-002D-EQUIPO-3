import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { ProductoService } from '../productos/producto.service';
import { AnaliticaService } from './analitica.service';
import { VencimientoCalendario } from './vencimiento-calendario';

interface OpcionCategoria {
  id: number;
  nombre: string;
}

function formatearFecha(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

@Component({
  selector: 'app-analitica-vencimientos',
  imports: [DatePipe, EstadoBadge, VencimientoCalendario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-vencimientos.html',
})
export class AnaliticaVencimientos {
  private readonly analiticaService = inject(AnaliticaService);
  private readonly productoService = inject(ProductoService);

  protected readonly diasUmbral = signal(30);
  protected readonly categoriaUmbral = signal<number | null>(null);

  private readonly hoy = new Date();
  protected readonly desdeFiltro = signal(formatearFecha(this.hoy));
  protected readonly hastaFiltro = signal(formatearFecha(new Date(this.hoy.getFullYear(), this.hoy.getMonth() + 1, 0)));
  protected readonly categoriaRango = signal<number | null>(null);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly categorias = computed<OpcionCategoria[]>(() => {
    if (!this.productos.hasValue()) return [];
    const mapa = new Map<number, string>();
    for (const p of this.productos.value()) mapa.set(p.categoriaId, p.categoriaNombre);
    return [...mapa]
      .map(([id, nombre]) => ({ id, nombre }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  private readonly filtroUmbral = computed(() => ({
    dias: this.diasUmbral(),
    categoriaId: this.categoriaUmbral(),
  }));
  protected readonly tarjetas = this.analiticaService.vencimientos(this.filtroUmbral);
  protected readonly filasTarjetas = computed(() => (this.tarjetas.hasValue() ? this.tarjetas.value() : []));

  private readonly filtroRango = computed(() => ({
    desde: this.desdeFiltro(),
    hasta: this.hastaFiltro(),
    categoriaId: this.categoriaRango(),
  }));
  protected readonly detalle = this.analiticaService.vencimientosRango(this.filtroRango);
  protected readonly filasDetalle = computed(() => (this.detalle.hasValue() ? this.detalle.value() : []));

  protected cambiarUmbral(valor: string): void {
    const numero = Number(valor);
    if (Number.isInteger(numero) && numero > 0) this.diasUmbral.set(numero);
  }

  protected cambiarCategoriaUmbral(valor: string): void {
    this.categoriaUmbral.set(valor === '' ? null : Number(valor));
  }

  protected cambiarCategoriaRango(valor: string): void {
    this.categoriaRango.set(valor === '' ? null : Number(valor));
  }

  protected cambiarDesde(valor: string): void {
    if (valor !== '') this.desdeFiltro.set(valor);
  }

  protected cambiarHasta(valor: string): void {
    if (valor !== '') this.hastaFiltro.set(valor);
  }

  protected alSeleccionarDiaCalendario(fecha: string | null): void {
    if (fecha === null) return;
    this.desdeFiltro.set(fecha);
    this.hastaFiltro.set(fecha);
  }

  protected estadoBadge(vencido: boolean): 'vencido' | 'proximo' {
    return vencido ? 'vencido' : 'proximo';
  }
}

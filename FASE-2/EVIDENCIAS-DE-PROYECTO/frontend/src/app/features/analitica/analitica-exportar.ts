import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { descargarArchivo } from '../../shared/utils/descargar-archivo';
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
  selector: 'app-analitica-exportar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-exportar.html',
})
export class AnaliticaExportar {
  private readonly analiticaService = inject(AnaliticaService);
  private readonly productoService = inject(ProductoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly hoy = new Date();
  protected readonly desdeFiltro = signal(formatearFecha(new Date(this.hoy.getFullYear(), this.hoy.getMonth(), 1)));
  protected readonly hastaFiltro = signal(formatearFecha(this.hoy));
  protected readonly categoriaId = signal<number | null>(null);
  protected readonly exportando = signal(false);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly categorias = computed<OpcionCategoria[]>(() => {
    if (!this.productos.hasValue()) return [];
    const mapa = new Map<number, string>();
    for (const p of this.productos.value()) mapa.set(p.categoriaId, p.categoriaNombre);
    return [...mapa]
      .map(([id, nombre]) => ({ id, nombre }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  protected cambiarDesde(valor: string): void {
    if (valor !== '') this.desdeFiltro.set(valor);
  }

  protected cambiarHasta(valor: string): void {
    if (valor !== '') this.hastaFiltro.set(valor);
  }

  protected cambiarCategoria(valor: string): void {
    this.categoriaId.set(valor === '' ? null : Number(valor));
  }

  protected exportar(): void {
    if (this.exportando()) return;
    this.exportando.set(true);
    this.analiticaService
      .exportarMovimientos(this.desdeFiltro(), this.hastaFiltro(), this.categoriaId())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (blob) => {
          this.exportando.set(false);
          descargarArchivo(blob, 'movimientos.xlsx');
        },
        error: () => {
          this.exportando.set(false);
          this.notificaciones.error('No se pudo generar el archivo Excel. Inténtalo de nuevo.');
        },
      });
  }
}

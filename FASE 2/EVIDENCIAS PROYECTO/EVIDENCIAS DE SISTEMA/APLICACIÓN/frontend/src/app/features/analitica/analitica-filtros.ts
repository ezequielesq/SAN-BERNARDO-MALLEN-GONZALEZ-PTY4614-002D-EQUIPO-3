import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { descargarArchivo } from '../../shared/utils/descargar-archivo';
import { ProductoService } from '../productos/producto.service';
import { Agrupacion, Medida } from './analitica.modelo';
import { AnaliticaProductosClave } from './analitica-productos-clave';
import { AnaliticaService } from './analitica.service';
import { EstadoFiltros } from './panel-estado';
import { Atajo } from './panel-filtros';

interface OpcionCategoria {
  id: number;
  nombre: string;
}

/** Barra de filtros compartidos: fechas, agrupación, categoría, productos, persona, medida y exportación. */
@Component({
  selector: 'app-analitica-filtros',
  imports: [AnaliticaProductosClave],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-filtros.html',
})
export class AnaliticaFiltros {
  readonly estado = input.required<EstadoFiltros>();

  private readonly service = inject(AnaliticaService);
  private readonly productoService = inject(ProductoService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly atajos: readonly { atajo: Atajo; etiqueta: string }[] = [
    { atajo: '7d', etiqueta: 'Últimos 7 días' },
    { atajo: '30d', etiqueta: 'Últimos 30 días' },
    { atajo: '90d', etiqueta: 'Últimos 90 días' },
    { atajo: 'mes', etiqueta: 'Este mes' },
  ];
  protected readonly agrupaciones: readonly { valor: Agrupacion; etiqueta: string }[] = [
    { valor: 'DIA', etiqueta: 'Día' },
    { valor: 'SEMANA', etiqueta: 'Semana' },
    { valor: 'MES', etiqueta: 'Mes' },
  ];

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly solicitantes = this.service.solicitantes();
  protected readonly busquedaProducto = signal('');
  protected readonly exportando = signal(false);

  protected readonly categorias = computed<OpcionCategoria[]>(() => {
    if (!this.productos.hasValue()) return [];
    const mapa = new Map<number, string>();
    for (const p of this.productos.value()) mapa.set(p.categoriaId, p.categoriaNombre);
    return [...mapa]
      .map(([id, nombre]) => ({ id, nombre }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  /** Productos que se pueden elegir: de la categoría elegida y que coinciden con la búsqueda. */
  protected readonly productosVisibles = computed(() => {
    if (!this.productos.hasValue()) return [];
    const categoria = this.estado().categoriaId();
    const texto = this.busquedaProducto().trim().toLocaleLowerCase('es');
    return this.productos
      .value()
      .filter((p) => (categoria === null || p.categoriaId === categoria) && p.nombre.toLocaleLowerCase('es').includes(texto))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  protected cambiarFecha(campo: 'desde' | 'hasta', valor: string): void {
    this.estado()[campo].set(valor);
  }

  protected cambiarAgrupacion(valor: string): void {
    this.estado().agrupar.set(valor as Agrupacion);
  }

  protected cambiarCategoria(valor: string): void {
    const estado = this.estado();
    estado.categoriaId.set(valor === '' ? null : Number(valor));
  }

  protected cambiarPersona(valor: string): void {
    this.estado().solicitanteId.set(valor === '' ? null : Number(valor));
  }

  protected cambiarMedida(valor: Medida): void {
    this.estado().medida.set(valor);
  }

  protected alternarProducto(id: number, marcado: boolean): void {
    const actuales = this.estado().productoIds();
    this.estado().productoIds.set(marcado ? [...actuales, id] : actuales.filter((x) => x !== id));
  }

  protected limpiarProductos(): void {
    this.estado().productoIds.set([]);
  }

  protected estaMarcado(id: number): boolean {
    return this.estado().productoIds().includes(id);
  }

  protected exportar(): void {
    const filtro = this.estado().filtro();
    if (filtro === null || this.exportando()) return;
    this.exportando.set(true);
    this.service
      .exportarMovimientos(filtro)
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

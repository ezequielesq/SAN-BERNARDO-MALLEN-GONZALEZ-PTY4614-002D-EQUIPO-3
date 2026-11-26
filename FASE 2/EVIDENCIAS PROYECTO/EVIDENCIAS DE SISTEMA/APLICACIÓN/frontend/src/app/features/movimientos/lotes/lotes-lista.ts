import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CatalogoService, ENDPOINT_CATEGORIAS } from '../../../shared/catalogo/catalogo.service';
import { EstadoBadge } from '../../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../../shared/estado-vista/estado-vista';
import { Extractores, Orden, ordenar } from '../../../shared/ordenamiento/orden';
import { ThOrden } from '../../../shared/ordenamiento/th-orden';
import { Paginacion } from '../../../shared/paginacion/paginacion';
import { ProveedorService } from '../../../shared/proveedor/proveedor.service';
import { ProductoService } from '../../productos/producto.service';
import { EstadoLote, FiltroLotes, Lote } from '../lote.modelo';
import { MovimientoService } from '../movimiento.service';
import {
  estadoLoteValido,
  etiquetaDias,
  etiquetaEstado,
  normalizarDias,
  rangoEstado,
  tipoBadge,
} from './lote-estado';

type ColumnaOrdenLote = 'productoNombre' | 'fechaVencimiento' | 'saldo' | 'estado';

const EXTRACTORES_LOTE: Extractores<Lote, ColumnaOrdenLote> = {
  productoNombre: (l) => l.productoNombre,
  fechaVencimiento: (l) => (l.fechaVencimiento ? Date.parse(l.fechaVencimiento) : null),
  saldo: (l) => l.saldo,
  estado: (l) => rangoEstado(l.estado),
};

const UMBRAL_INICIAL = 7;
const ESTADOS: readonly EstadoLote[] = ['VIGENTE', 'POR_VENCER', 'VENCIDO', 'AGOTADO'];

/** Id numérico de un parámetro de URL; null si falta o no es un entero positivo. */
function idDeParametro(valor: string | null): number | null {
  const n = valor === null ? NaN : Number(valor);
  return Number.isInteger(n) && n > 0 ? n : null;
}

@Component({
  selector: 'app-lotes-lista',
  imports: [DatePipe, RouterLink, EstadoVista, EstadoBadge, ThOrden, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lotes-lista.html',
})
export class LotesLista {
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  private readonly proveedorService = inject(ProveedorService);
  private readonly catalogoService = inject(CatalogoService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly productoId = signal<number | null>(null);
  protected readonly categoriaId = signal<number | null>(null);
  protected readonly proveedorId = signal<number | null>(null);
  protected readonly estado = signal<EstadoLote | null>(null);
  protected readonly texto = signal('');
  protected readonly diasPorVencer = signal(UMBRAL_INICIAL);
  protected readonly diasInvalido = signal(false);
  protected readonly incluirAgotados = signal(false);

  protected readonly paginaActual = signal(1);
  protected readonly tamanoPagina = signal(15);
  protected readonly opcionesTamano = [10, 15, 20, 50] as const;
  /** Sin columna activa conserva el orden del servidor (primero lo que vence antes). */
  protected readonly orden = new Orden<ColumnaOrdenLote>(null, () => this.paginaActual.set(1));

  protected readonly estados = ESTADOS;
  protected readonly etiquetaEstado = etiquetaEstado;
  protected readonly etiquetaDias = etiquetaDias;
  protected readonly tipoBadge = tipoBadge;

  protected readonly productos = this.productoService.listar(signal(false));
  protected readonly categorias = this.catalogoService.listar(ENDPOINT_CATEGORIAS, false);
  protected readonly proveedores = this.proveedorService.listar(false);
  protected readonly opcionesProducto = computed(() => (this.productos.hasValue() ? this.productos.value() : []));
  protected readonly opcionesCategoria = computed(() => (this.categorias.hasValue() ? this.categorias.value() : []));
  protected readonly opcionesProveedor = computed(() => (this.proveedores.hasValue() ? this.proveedores.value() : []));

  protected readonly filtro = computed<FiltroLotes>(() => ({
    productoId: this.productoId(),
    categoriaId: this.categoriaId(),
    proveedorId: this.proveedorId(),
    estado: this.estado(),
    texto: this.texto(),
    diasPorVencer: this.diasPorVencer(),
    incluirAgotados: this.incluirAgotados(),
  }));

  protected readonly lotes = this.movimientoService.listarLotes(this.filtro);
  protected readonly todos = computed(() => (this.lotes.hasValue() ? this.lotes.value() : []));
  protected readonly ordenados = computed(() => ordenar(this.todos(), this.orden, EXTRACTORES_LOTE));
  protected readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.ordenados().slice(inicio, inicio + this.tamanoPagina());
  });

  constructor() {
    // Los enlaces desde Stock, Alertas y el detalle traen sus filtros iniciales en la URL.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.productoId.set(idDeParametro(params.get('productoId')));
      this.proveedorId.set(idDeParametro(params.get('proveedorId')));
      this.estado.set(estadoLoteValido(params.get('estado')));
      this.diasPorVencer.set(normalizarDias(params.get('diasPorVencer') ?? '') ?? UMBRAL_INICIAL);
      this.diasInvalido.set(false);
      this.paginaActual.set(1);
    });
  }

  protected cambiarProducto(valor: string): void {
    this.productoId.set(idDeParametro(valor));
    this.paginaActual.set(1);
  }

  protected cambiarCategoria(valor: string): void {
    this.categoriaId.set(idDeParametro(valor));
    this.paginaActual.set(1);
  }

  protected cambiarProveedor(valor: string): void {
    this.proveedorId.set(idDeParametro(valor));
    this.paginaActual.set(1);
  }

  protected cambiarEstado(valor: string): void {
    this.estado.set(estadoLoteValido(valor));
    this.paginaActual.set(1);
  }

  protected cambiarTexto(valor: string): void {
    this.texto.set(valor);
    this.paginaActual.set(1);
  }

  /** Solo aplica un umbral válido; si no lo es, conserva el anterior y avisa. */
  protected cambiarUmbral(valor: string): void {
    const dias = normalizarDias(valor);
    this.diasInvalido.set(dias === null);
    if (dias !== null) {
      this.diasPorVencer.set(dias);
      this.paginaActual.set(1);
    }
  }

  protected cambiarIncluirAgotados(marcado: boolean): void {
    this.incluirAgotados.set(marcado);
    this.paginaActual.set(1);
  }

  protected cambiarPagina(pagina: number): void {
    this.paginaActual.set(pagina);
  }

  protected cambiarTamanoPagina(tamano: number): void {
    this.tamanoPagina.set(tamano);
    this.paginaActual.set(1);
  }
}

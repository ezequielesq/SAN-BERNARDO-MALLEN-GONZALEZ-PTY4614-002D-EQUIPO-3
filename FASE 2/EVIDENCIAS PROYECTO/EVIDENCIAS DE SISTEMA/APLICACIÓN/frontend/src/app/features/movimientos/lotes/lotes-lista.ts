import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime } from 'rxjs';
import { CatalogoService, ENDPOINT_CATEGORIAS } from '../../../shared/catalogo/catalogo.service';
import { EstadoBadge } from '../../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../../shared/estado-vista/estado-vista';
import { Extractores, Orden, ordenar } from '../../../shared/ordenamiento/orden';
import { ThOrden } from '../../../shared/ordenamiento/th-orden';
import { Paginacion } from '../../../shared/paginacion/paginacion';
import { paginaAjustada } from '../../../shared/paginacion/paginar';
import { ProveedorService } from '../../../shared/proveedor/proveedor.service';
import { ProductoService } from '../../productos/producto.service';
import { EstadoLote, FiltroLotes, Lote } from '../lote.modelo';
import { MovimientoService } from '../movimiento.service';
import {
  ColumnaOrdenLote,
  ESTADOS_LOTE,
  TAMANOS_PAGINA_LOTE,
  UMBRAL_POR_DEFECTO,
  VISTA_POR_DEFECTO,
  VistaLotes,
  estadoLoteValido,
  etiquetaDias,
  etiquetaEstado,
  filtroDesdeParams,
  idDeParametro,
  normalizarDias,
  paramsDesdeFiltro,
  paramsDesdeVista,
  rangoEstado,
  tipoBadge,
  vistaDesdeParams,
} from './lote-estado';

const EXTRACTORES_LOTE: Extractores<Lote, ColumnaOrdenLote> = {
  productoNombre: (l) => l.productoNombre,
  fechaVencimiento: (l) => (l.fechaVencimiento ? Date.parse(l.fechaVencimiento) : null),
  saldo: (l) => l.saldo,
  estado: (l) => rangoEstado(l.estado),
};

/** Espera tras la última tecla antes de filtrar por N.º de lote. */
const ESPERA_TEXTO_MS = 300;

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
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly productoId = signal<number | null>(null);
  protected readonly categoriaId = signal<number | null>(null);
  protected readonly proveedorId = signal<number | null>(null);
  protected readonly estado = signal<EstadoLote | null>(null);
  protected readonly texto = signal('');
  protected readonly diasPorVencer = signal(UMBRAL_POR_DEFECTO);
  protected readonly diasInvalido = signal(false);
  protected readonly incluirAgotados = signal(false);

  /** Página pedida (de la URL); `paginaActual` la lleva al rango válido cuando los datos son menos. */
  protected readonly pagina = signal(VISTA_POR_DEFECTO.pagina);
  protected readonly tamanoPagina = signal(VISTA_POR_DEFECTO.tamano);
  protected readonly opcionesTamano = TAMANOS_PAGINA_LOTE;
  /** Sin columna activa conserva el orden del servidor (primero lo que vence antes). */
  protected readonly orden = new Orden<ColumnaOrdenLote>(null, () => this.aplicar({}));

  protected readonly estados = ESTADOS_LOTE;
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
  protected readonly paginaActual = computed(() =>
    paginaAjustada(this.pagina(), this.ordenados().length, this.tamanoPagina()),
  );
  protected readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.ordenados().slice(inicio, inicio + this.tamanoPagina());
  });

  /** Lo que se va escribiendo en «N.º de lote»; se aplica tras una pausa. */
  private readonly textoEscrito = new Subject<string>();

  constructor() {
    // La URL es la fuente de todos los filtros: los enlaces (Stock, Alertas, detalle) los traen en ella y la lista
    // los escribe cuando el usuario los cambia. Lo que no venga vuelve a su valor por defecto.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const f = filtroDesdeParams(params);
      this.productoId.set(f.productoId);
      this.categoriaId.set(f.categoriaId);
      this.proveedorId.set(f.proveedorId);
      this.estado.set(f.estado);
      this.texto.set(f.texto);
      if (f.diasPorVencer !== this.diasPorVencer()) this.diasInvalido.set(false);
      this.diasPorVencer.set(f.diasPorVencer);
      this.incluirAgotados.set(f.incluirAgotados);
      // El orden y la página también vienen de la URL, así que «Volver» y el botón Atrás los restituyen.
      const v = vistaDesdeParams(params);
      this.orden.columna.set(v.columna);
      this.orden.direccion.set(v.direccion);
      this.pagina.set(v.pagina);
      this.tamanoPagina.set(v.tamano);
    });
    this.textoEscrito
      .pipe(debounceTime(ESPERA_TEXTO_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe((valor) => this.cambiarTexto(valor));
  }

  /**
   * Escribe filtros, orden, página y tamaño en la URL (sin sumar una entrada al historial); la suscripción de arriba
   * los aplica. Cualquier cambio vuelve a la página 1 salvo que se pida otra (`cambiarPagina`).
   */
  private aplicar(filtro: Partial<FiltroLotes>, vista: Partial<VistaLotes> = {}): void {
    const actual: VistaLotes = {
      columna: this.orden.columna(),
      direccion: this.orden.direccion(),
      pagina: 1,
      tamano: this.tamanoPagina(),
    };
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        ...paramsDesdeFiltro({ ...this.filtro(), ...filtro }),
        ...paramsDesdeVista({ ...actual, ...vista }),
      },
      replaceUrl: true,
    });
  }

  protected cambiarProducto(valor: string): void {
    this.aplicar({ productoId: idDeParametro(valor) });
  }

  protected cambiarCategoria(valor: string): void {
    this.aplicar({ categoriaId: idDeParametro(valor) });
  }

  protected cambiarProveedor(valor: string): void {
    this.aplicar({ proveedorId: idDeParametro(valor) });
  }

  protected cambiarEstado(valor: string): void {
    this.aplicar({ estado: estadoLoteValido(valor) });
  }

  /** Aplica al escribir, con una pausa de ~300 ms. */
  protected escribirTexto(valor: string): void {
    this.textoEscrito.next(valor);
  }

  protected cambiarTexto(valor: string): void {
    this.aplicar({ texto: valor });
  }

  /** Solo aplica un umbral válido; si no lo es, conserva el anterior y avisa. */
  protected cambiarUmbral(valor: string): void {
    const dias = normalizarDias(valor);
    this.diasInvalido.set(dias === null);
    if (dias !== null) this.aplicar({ diasPorVencer: dias });
  }

  protected cambiarIncluirAgotados(marcado: boolean): void {
    this.aplicar({ incluirAgotados: marcado });
  }

  protected cambiarPagina(pagina: number): void {
    this.aplicar({}, { pagina });
  }

  protected cambiarTamanoPagina(tamano: number): void {
    this.aplicar({}, { tamano });
  }
}

import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CompraProveedor,
  FiltroPanel,
  HistorialPrecios,
  Opcion,
  PedidosPorPersona,
  ProductoClave,
  ProductoTop,
  ResumenPanel,
  SerieConsumo,
  VencimientoDto,
} from './analitica.modelo';
import { Parametros, parametrosPanel, parametrosPrecios } from './panel-filtros';

export interface FiltroVencimientosUmbral {
  dias: number;
  categoriaId: number | null;
  proveedorId: number | null;
  productoIds?: number[];
}

export interface FiltroVencimientosRango {
  desde: string;
  hasta: string;
  categoriaId: number | null;
  proveedorId: number | null;
  productoIds?: number[];
}

@Injectable({ providedIn: 'root' })
export class AnaliticaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/analitica`;
  private readonly panel = `${this.url}/panel`;

  /** Los métodos que devuelven un recurso se llaman desde un inicializador de campo (contexto de inyección). */
  resumen(filtro: Signal<FiltroPanel | null>): HttpResourceRef<ResumenPanel | undefined> {
    return httpResource<ResumenPanel>(() => this.pedido('resumen', filtro()));
  }

  consumoPeriodo(filtro: Signal<FiltroPanel | null>): HttpResourceRef<SerieConsumo | undefined> {
    return httpResource<SerieConsumo>(() => this.pedido('consumo-periodo', filtro()));
  }

  productosTop(filtro: Signal<FiltroPanel | null>, limite = 7): HttpResourceRef<ProductoTop[] | undefined> {
    return httpResource<ProductoTop[]>(() => this.pedido('productos-top', filtro(), { limite }));
  }

  comprasPorProveedor(filtro: Signal<FiltroPanel | null>): HttpResourceRef<CompraProveedor[] | undefined> {
    return httpResource<CompraProveedor[]>(() => this.pedido('compras-por-proveedor', filtro()));
  }

  pedidosPorPersona(filtro: Signal<FiltroPanel | null>): HttpResourceRef<PedidosPorPersona | undefined> {
    return httpResource<PedidosPorPersona>(() => this.pedido('pedidos-por-persona', filtro()));
  }

  precios(
    filtro: Signal<FiltroPanel | null>,
    productoId: Signal<number | null>,
  ): HttpResourceRef<HistorialPrecios | undefined> {
    return httpResource<HistorialPrecios>(() => {
      const f = filtro();
      const id = productoId();
      if (f === null || id === null) return undefined;
      return { url: `${this.panel}/precios/${id}`, params: parametrosPrecios(f) };
    });
  }

  solicitantes(): HttpResourceRef<Opcion[]> {
    return httpResource<Opcion[]>(() => `${this.panel}/solicitantes`, { defaultValue: [] });
  }

  productosClave(): HttpResourceRef<ProductoClave[]> {
    return httpResource<ProductoClave[]>(() => `${this.panel}/productos-clave`, { defaultValue: [] });
  }

  guardarProductosClave(productoIds: number[]): Observable<ProductoClave[]> {
    return this.http.put<ProductoClave[]>(`${this.panel}/productos-clave`, { productoIds });
  }

  vencimientos(filtro: Signal<FiltroVencimientosUmbral>): HttpResourceRef<VencimientoDto[]> {
    return httpResource<VencimientoDto[]>(
      () => {
        const f = filtro();
        const params: Parametros = { dias: f.dias };
        if (f.categoriaId !== null) params['categoriaId'] = f.categoriaId;
        if (f.proveedorId !== null) params['proveedorId'] = f.proveedorId;
        if (f.productoIds && f.productoIds.length > 0) params['productoIds'] = f.productoIds.join(',');
        return { url: `${this.url}/vencimientos`, params };
      },
      { defaultValue: [] },
    );
  }

  vencimientosRango(filtro: Signal<FiltroVencimientosRango>): HttpResourceRef<VencimientoDto[]> {
    return httpResource<VencimientoDto[]>(
      () => {
        const f = filtro();
        const params: Parametros = { desde: f.desde, hasta: f.hasta };
        if (f.categoriaId !== null) params['categoriaId'] = f.categoriaId;
        if (f.proveedorId !== null) params['proveedorId'] = f.proveedorId;
        if (f.productoIds && f.productoIds.length > 0) params['productoIds'] = f.productoIds.join(',');
        return { url: `${this.url}/vencimientos/rango`, params };
      },
      { defaultValue: [] },
    );
  }

  exportarMovimientos(filtro: FiltroPanel): Observable<Blob> {
    const params: Parametros = { desde: filtro.desde, hasta: filtro.hasta };
    if (filtro.categoriaId !== null) params['categoriaId'] = filtro.categoriaId;
    if (filtro.productoIds.length > 0) params['productoIds'] = filtro.productoIds.join(',');
    if (filtro.solicitanteId !== null) params['solicitanteId'] = filtro.solicitanteId;
    if (filtro.proveedorId !== null) params['proveedorId'] = filtro.proveedorId;
    return this.http.get(`${this.url}/exportar-movimientos`, { params, responseType: 'blob' });
  }

  /** Petición de un bloque del panel; sin filtro válido el recurso queda inactivo (no consulta). */
  private pedido(ruta: string, filtro: FiltroPanel | null, extra: Parametros = {}) {
    if (filtro === null) return undefined;
    return { url: `${this.panel}/${ruta}`, params: parametrosPanel(filtro, extra) };
  }
}

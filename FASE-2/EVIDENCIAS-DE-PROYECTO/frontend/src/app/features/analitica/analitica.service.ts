import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AgruparPor, ConsumoDto, ProductoMasUsadoDto, VencimientoDto } from './analitica.modelo';

export interface FiltroConsumo {
  desde: string;
  hasta: string;
  agruparPor: AgruparPor;
  categoriaId: number | null;
}

export interface FiltroMasUsados {
  desde: string;
  hasta: string;
  categoriaId: number | null;
}

export interface FiltroVencimientosUmbral {
  dias: number;
  categoriaId: number | null;
}

export interface FiltroVencimientosRango {
  desde: string;
  hasta: string;
  categoriaId: number | null;
}

@Injectable({ providedIn: 'root' })
export class AnaliticaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/analitica`;

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  consumo(filtro: Signal<FiltroConsumo>): HttpResourceRef<ConsumoDto[]> {
    return httpResource<ConsumoDto[]>(
      () => {
        const f = filtro();
        const params: Record<string, string | number> = {
          desde: f.desde,
          hasta: f.hasta,
          agruparPor: f.agruparPor,
        };
        if (f.categoriaId !== null) params['categoriaId'] = f.categoriaId;
        return { url: `${this.url}/consumo`, params };
      },
      { defaultValue: [] },
    );
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  masUsados(filtro: Signal<FiltroMasUsados>): HttpResourceRef<ProductoMasUsadoDto[]> {
    return httpResource<ProductoMasUsadoDto[]>(
      () => {
        const f = filtro();
        const params: Record<string, string | number> = { desde: f.desde, hasta: f.hasta };
        if (f.categoriaId !== null) params['categoriaId'] = f.categoriaId;
        return { url: `${this.url}/mas-usados`, params };
      },
      { defaultValue: [] },
    );
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  vencimientos(filtro: Signal<FiltroVencimientosUmbral>): HttpResourceRef<VencimientoDto[]> {
    return httpResource<VencimientoDto[]>(
      () => {
        const f = filtro();
        const params: Record<string, string | number> = { dias: f.dias };
        if (f.categoriaId !== null) params['categoriaId'] = f.categoriaId;
        return { url: `${this.url}/vencimientos`, params };
      },
      { defaultValue: [] },
    );
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  vencimientosRango(filtro: Signal<FiltroVencimientosRango>): HttpResourceRef<VencimientoDto[]> {
    return httpResource<VencimientoDto[]>(
      () => {
        const f = filtro();
        const params: Record<string, string | number> = { desde: f.desde, hasta: f.hasta };
        if (f.categoriaId !== null) params['categoriaId'] = f.categoriaId;
        return { url: `${this.url}/vencimientos/rango`, params };
      },
      { defaultValue: [] },
    );
  }

  exportarMovimientos(desde: string, hasta: string, categoriaId: number | null): Observable<Blob> {
    const params: Record<string, string | number> = { desde, hasta };
    if (categoriaId !== null) params['categoriaId'] = categoriaId;
    return this.http.get(`${this.url}/exportar-movimientos`, { params, responseType: 'blob' });
  }
}

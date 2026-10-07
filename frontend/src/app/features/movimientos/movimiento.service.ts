import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AlertaVencimiento,
  EntradaRequest,
  Movimiento,
  SalidaRequest,
  StockStatus,
} from './movimiento.modelo';

export interface FiltroMovimientos {
  productoId: number | null;
  desde: string | null;
  hasta: string | null;
}

@Injectable({ providedIn: 'root' })
export class MovimientoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/movimientos`;

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listar(filtro: Signal<FiltroMovimientos>): HttpResourceRef<Movimiento[]> {
    return httpResource<Movimiento[]>(
      () => {
        const f = filtro();
        const params: Record<string, string | number> = {};
        if (f.productoId !== null) params['productoId'] = f.productoId;
        if (f.desde !== null) params['desde'] = f.desde;
        if (f.hasta !== null) params['hasta'] = f.hasta;
        return { url: this.url, params };
      },
      { defaultValue: [] },
    );
  }

  registrarEntrada(request: EntradaRequest): Observable<Movimiento> {
    return this.http.post<Movimiento>(`${this.url}/entradas`, request);
  }

  registrarSalida(request: SalidaRequest): Observable<Movimiento> {
    return this.http.post<Movimiento>(`${this.url}/salidas`, request);
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listarStock(): HttpResourceRef<StockStatus[]> {
    return httpResource<StockStatus[]>(() => `${this.url}/stock`, { defaultValue: [] });
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  alertasVencimiento(dias: Signal<number>): HttpResourceRef<AlertaVencimiento[]> {
    return httpResource<AlertaVencimiento[]>(
      () => ({ url: `${this.url}/alertas/vencimiento`, params: { dias: dias() } }),
      { defaultValue: [] },
    );
  }
}

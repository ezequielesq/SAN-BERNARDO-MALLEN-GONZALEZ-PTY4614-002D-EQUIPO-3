import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AlertaVencimiento,
  EntradaRequest,
  FilaEntrada,
  FilaEntradaValidada,
  ImportacionEntradaRequest,
  Movimiento,
  OperacionResponse,
  SalidaRequest,
  StockStatus,
  TipoOperacion,
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

  /**
   * Tipos de operación que se pueden registrar a mano. Sin `sentido` devuelve todos (para reconocer
   * qué movimientos son corregibles). Llamar desde un inicializador de campo (requiere contexto de inyección).
   */
  listarTipos(sentido?: 1 | -1): HttpResourceRef<TipoOperacion[]> {
    return httpResource<TipoOperacion[]>(
      () => {
        const params: Record<string, number> = {};
        if (sentido !== undefined) params['sentido'] = sentido;
        return { url: `${this.url}/tipos`, params };
      },
      { defaultValue: [] },
    );
  }

  registrarEntrada(request: EntradaRequest): Observable<OperacionResponse> {
    return this.http.post<OperacionResponse>(`${this.url}/entradas`, request);
  }

  descargarPlantillaEntradas(): Observable<Blob> {
    return this.http.get(`${this.url}/entradas/plantilla/xlsx`, { responseType: 'blob' });
  }

  /** Lee el Excel y devuelve las filas validadas; no guarda nada. */
  leerEntradas(archivo: File): Observable<FilaEntradaValidada[]> {
    const cuerpo = new FormData();
    cuerpo.append('archivo', archivo);
    return this.http.post<FilaEntradaValidada[]>(`${this.url}/entradas/importar/leer`, cuerpo);
  }

  /** Revalida filas ya editadas; no guarda nada. */
  validarEntradas(request: ImportacionEntradaRequest): Observable<FilaEntradaValidada[]> {
    return this.http.post<FilaEntradaValidada[]>(`${this.url}/entradas/importar/validar`, request);
  }

  /** Registra todas las filas como una sola operación de entrada (todo o nada). */
  importarEntradas(request: ImportacionEntradaRequest): Observable<OperacionResponse> {
    return this.http.post<OperacionResponse>(`${this.url}/entradas/importar`, request);
  }

  registrarSalida(request: SalidaRequest): Observable<OperacionResponse> {
    return this.http.post<OperacionResponse>(`${this.url}/salidas`, request);
  }

  /** Contrarresta una operación manual con otra de sentido contrario (el libro nunca se edita). */
  corregir(operacionId: number, nota: string | null): Observable<OperacionResponse> {
    return this.http.post<OperacionResponse>(`${this.url}/operaciones/${operacionId}/correccion`, { nota });
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

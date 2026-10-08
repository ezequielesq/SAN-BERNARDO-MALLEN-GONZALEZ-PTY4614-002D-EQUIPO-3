import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AprobarSolicitudRequest, Solicitud } from './solicitud.modelo';

export interface FiltroSolicitudes {
  desde: string | null;
  hasta: string | null;
}

@Injectable({ providedIn: 'root' })
export class SolicitudService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/solicitudes`;

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listar(filtro: Signal<FiltroSolicitudes>): HttpResourceRef<Solicitud[]> {
    return httpResource<Solicitud[]>(
      () => {
        const f = filtro();
        const params: Record<string, string> = {};
        if (f.desde !== null) params['desde'] = f.desde;
        if (f.hasta !== null) params['hasta'] = f.hasta;
        return { url: this.url, params };
      },
      { defaultValue: [] },
    );
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listarPendientes(): HttpResourceRef<Solicitud[]> {
    return httpResource<Solicitud[]>(() => `${this.url}/pendientes`, { defaultValue: [] });
  }

  aprobar(id: number, request: AprobarSolicitudRequest): Observable<Solicitud> {
    return this.http.put<Solicitud>(`${this.url}/${id}/aprobar`, request);
  }

  rechazar(id: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/rechazar`, {});
  }
}

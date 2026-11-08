import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Evento, EventoRequest } from './evento.modelo';

@Injectable({ providedIn: 'root' })
export class EventoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/eventos`;

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listar(rango: () => { desde: string; hasta: string }): HttpResourceRef<Evento[]> {
    return httpResource<Evento[]>(
      () => {
        const { desde, hasta } = rango();
        return { url: this.url, params: { desde, hasta } };
      },
      { defaultValue: [] },
    );
  }

  crear(request: EventoRequest): Observable<Evento> {
    return this.http.post<Evento>(this.url, request);
  }

  actualizar(id: number, request: EventoRequest): Observable<Evento> {
    return this.http.put<Evento>(`${this.url}/${id}`, request);
  }

  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }
}

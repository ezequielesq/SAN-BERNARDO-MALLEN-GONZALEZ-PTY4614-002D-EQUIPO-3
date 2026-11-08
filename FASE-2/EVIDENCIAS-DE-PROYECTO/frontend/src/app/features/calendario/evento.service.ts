import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Evento, EventoRequest } from './evento.modelo';

@Injectable({ providedIn: 'root' })
export class EventoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/eventos`;

  /**
   * Llamar desde un inicializador de campo del componente (requiere contexto de inyección).
   * Si el rango devuelve `undefined` el recurso queda inactivo y no consulta al servidor.
   */
  listar(rango: () => { desde: string; hasta: string } | undefined): HttpResourceRef<Evento[]> {
    return httpResource<Evento[]>(
      () => {
        const r = rango();
        return r === undefined ? undefined : { url: this.url, params: { desde: r.desde, hasta: r.hasta } };
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

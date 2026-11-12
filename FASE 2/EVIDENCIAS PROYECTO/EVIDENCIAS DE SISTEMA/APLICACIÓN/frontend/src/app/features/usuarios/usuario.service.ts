import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Usuario, UsuarioRequest } from './usuario.modelo';

@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/usuarios`;

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listar(mostrarDesactivados: Signal<boolean>): HttpResourceRef<Usuario[]> {
    return httpResource<Usuario[]>(
      () => ({ url: this.url, params: { soloActivos: !mostrarDesactivados() } }),
      { defaultValue: [] },
    );
  }

  crear(request: UsuarioRequest): Observable<Usuario> {
    return this.http.post<Usuario>(this.url, request);
  }

  editar(id: number, request: UsuarioRequest): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.url}/${id}`, request);
  }

  desactivar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  reactivar(id: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/reactivar`, {});
  }
}

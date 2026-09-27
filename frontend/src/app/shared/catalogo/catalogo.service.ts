import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface CatalogoItem {
  id: number;
  nombre: string;
  activo: boolean;
}

export const ENDPOINT_CATEGORIAS = '/categorias';
export const ENDPOINT_UNIDADES = '/unidades-medida';

@Injectable({ providedIn: 'root' })
export class CatalogoService {
  private readonly http = inject(HttpClient);

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listar(endpoint: string, soloActivas: boolean): HttpResourceRef<CatalogoItem[]> {
    return httpResource<CatalogoItem[]>(
      () => ({ url: `${environment.apiUrl}${endpoint}`, params: { soloActivas } }),
      { defaultValue: [] },
    );
  }

  crear(endpoint: string, nombre: string): Observable<CatalogoItem> {
    return this.http.post<CatalogoItem>(`${environment.apiUrl}${endpoint}`, { nombre });
  }

  renombrar(endpoint: string, id: number, nombre: string): Observable<CatalogoItem> {
    return this.http.put<CatalogoItem>(`${environment.apiUrl}${endpoint}/${id}`, { nombre });
  }

  desactivar(endpoint: string, id: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}${endpoint}/${id}`);
  }

  reactivar(endpoint: string, id: number): Observable<void> {
    return this.http.put<void>(`${environment.apiUrl}${endpoint}/${id}/reactivar`, {});
  }
}

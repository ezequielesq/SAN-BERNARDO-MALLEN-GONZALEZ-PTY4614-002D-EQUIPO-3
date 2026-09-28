import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, Signal, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Producto, ProductoRequest } from './producto.modelo';

@Injectable({ providedIn: 'root' })
export class ProductoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/productos`;

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listar(soloActivos: Signal<boolean>): HttpResourceRef<Producto[]> {
    return httpResource<Producto[]>(
      () => ({ url: this.url, params: { soloActivos: soloActivos() } }),
      { defaultValue: [] },
    );
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  obtener(id: Signal<number | null>): HttpResourceRef<Producto | undefined> {
    return httpResource<Producto>(() => {
      const valor = id();
      return valor === null ? undefined : `${this.url}/${valor}`;
    });
  }

  crear(request: ProductoRequest): Observable<Producto> {
    return this.http.post<Producto>(this.url, request);
  }

  editar(id: number, request: ProductoRequest): Observable<Producto> {
    return this.http.put<Producto>(`${this.url}/${id}`, request);
  }

  deshabilitar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  reactivar(id: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/reactivar`, {});
  }

  exportarXlsx(soloActivos: boolean): Observable<Blob> {
    return this.http.get(`${this.url}/exportar/xlsx`, { params: { soloActivos }, responseType: 'blob' });
  }

  exportarPdf(soloActivos: boolean): Observable<Blob> {
    return this.http.get(`${this.url}/exportar/pdf`, { params: { soloActivos }, responseType: 'blob' });
  }
}

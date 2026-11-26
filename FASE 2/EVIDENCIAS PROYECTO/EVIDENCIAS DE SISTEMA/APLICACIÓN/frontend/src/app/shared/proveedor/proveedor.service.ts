import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Proveedor {
  id: number;
  rut: string;
  nombre: string;
  activo: boolean;
}

@Injectable({ providedIn: 'root' })
export class ProveedorService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/proveedores`;

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  listar(soloActivos: boolean): HttpResourceRef<Proveedor[]> {
    return httpResource<Proveedor[]>(() => ({ url: this.url, params: { soloActivos } }), { defaultValue: [] });
  }

  crear(rut: string, nombre: string): Observable<Proveedor> {
    return this.http.post<Proveedor>(this.url, { rut, nombre });
  }

  editar(id: number, rut: string, nombre: string): Observable<Proveedor> {
    return this.http.put<Proveedor>(`${this.url}/${id}`, { rut, nombre });
  }

  desactivar(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  reactivar(id: number): Observable<void> {
    return this.http.put<void>(`${this.url}/${id}/reactivar`, {});
  }
}

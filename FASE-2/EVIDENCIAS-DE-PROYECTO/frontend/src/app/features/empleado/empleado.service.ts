import { HttpClient, HttpResourceRef, httpResource } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Solicitud } from '../solicitudes/solicitud.modelo';
import {
  CredencialesEmpleado,
  DevolucionRequest,
  EmpleadoOpcion,
  ProductoOpcion,
  SolicitudPublicaRequest,
} from './empleado.modelo';

@Injectable({ providedIn: 'root' })
export class EmpleadoService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/publico`;

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  empleados(): HttpResourceRef<EmpleadoOpcion[]> {
    return httpResource<EmpleadoOpcion[]>(() => `${this.url}/empleados`, { defaultValue: [] });
  }

  /** Llamar desde un inicializador de campo del componente (requiere contexto de inyección). */
  productos(): HttpResourceRef<ProductoOpcion[]> {
    return httpResource<ProductoOpcion[]>(() => `${this.url}/productos`, { defaultValue: [] });
  }

  crearSolicitud(request: SolicitudPublicaRequest): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${this.url}/solicitudes`, request);
  }

  misSolicitudes(credenciales: CredencialesEmpleado): Observable<Solicitud[]> {
    return this.http.post<Solicitud[]>(`${this.url}/mis-solicitudes`, credenciales);
  }

  devolver(request: DevolucionRequest): Observable<Solicitud> {
    return this.http.post<Solicitud>(`${this.url}/devoluciones`, request);
  }
}

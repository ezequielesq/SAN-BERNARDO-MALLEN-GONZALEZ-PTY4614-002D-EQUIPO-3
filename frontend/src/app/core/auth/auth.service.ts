import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Credenciales, Sesion, esSesion } from './sesion';

const CLAVE_SESION = 'smartrdp.sesion';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly _sesion = signal<Sesion | null>(leerSesion());

  readonly sesion = this._sesion.asReadonly();
  readonly estaAutenticado = computed(() => this._sesion() !== null);
  readonly rol = computed(() => this._sesion()?.rol ?? null);

  login(credenciales: Credenciales): Observable<Sesion> {
    return this.http.post<Sesion>(`${environment.apiUrl}/auth/login`, credenciales).pipe(
      tap(sesion => {
        this._sesion.set(sesion);
        guardarSesion(sesion);
      }),
    );
  }

  logout(opciones: { expirada?: boolean; returnUrl?: string } = {}): void {
    this._sesion.set(null);
    borrarSesion();
    const queryParams: Record<string, string> = {};
    if (opciones.expirada) queryParams['expirada'] = '1';
    if (opciones.returnUrl) queryParams['returnUrl'] = opciones.returnUrl;
    void this.router.navigate(['/login'], { queryParams });
  }
}

function leerSesion(): Sesion | null {
  try {
    const crudo = sessionStorage.getItem(CLAVE_SESION);
    if (crudo === null) return null;
    const valor: unknown = JSON.parse(crudo);
    return esSesion(valor) ? valor : null;
  } catch {
    return null;
  }
}

function guardarSesion(sesion: Sesion): void {
  try {
    sessionStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
  } catch {
    // Almacenamiento bloqueado: la sesión dura solo mientras la pestaña siga abierta.
  }
}

function borrarSesion(): void {
  try {
    sessionStorage.removeItem(CLAVE_SESION);
  } catch {
    // Almacenamiento bloqueado: no hay nada que borrar.
  }
}

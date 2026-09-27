import { LiveAnnouncer } from '@angular/cdk/a11y';
import { Injectable, inject, signal } from '@angular/core';

export interface Notificacion {
  tipo: 'exito' | 'error';
  texto: string;
}

@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly announcer = inject(LiveAnnouncer);
  private readonly _actual = signal<Notificacion | null>(null);
  readonly actual = this._actual.asReadonly();

  exito(texto: string): void {
    this.mostrar({ tipo: 'exito', texto });
  }

  error(texto: string): void {
    this.mostrar({ tipo: 'error', texto });
  }

  limpiar(): void {
    this._actual.set(null);
  }

  private mostrar(notificacion: Notificacion): void {
    this._actual.set(notificacion);
    void this.announcer.announce(notificacion.texto, notificacion.tipo === 'error' ? 'assertive' : 'polite');
  }
}

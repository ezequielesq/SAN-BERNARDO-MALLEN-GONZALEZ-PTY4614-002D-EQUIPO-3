import { LiveAnnouncer } from '@angular/cdk/a11y';
import { Injectable, inject } from '@angular/core';
import alertify from 'alertifyjs';

@Injectable({ providedIn: 'root' })
export class NotificacionService {
  private readonly announcer = inject(LiveAnnouncer);

  exito(texto: string): void {
    alertify.success(texto);
    void this.announcer.announce(texto, 'polite');
  }

  error(texto: string): void {
    alertify.error(texto);
    void this.announcer.announce(texto, 'assertive');
  }

  limpiar(): void {
    alertify.dismissAll();
  }
}

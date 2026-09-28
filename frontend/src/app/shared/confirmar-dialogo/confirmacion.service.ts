import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import alertify from 'alertifyjs';

export interface DatosConfirmacion {
  titulo: string;
  descripcion: string;
  textoConfirmar: string;
}

@Injectable({ providedIn: 'root' })
export class ConfirmacionService {
  confirmar(datos: DatosConfirmacion): Observable<boolean> {
    return new Observable<boolean>((subscriber) => {
      const elementoActivo = document.activeElement as HTMLElement | null;

      const dialogo = alertify
        .confirm(
          datos.titulo,
          datos.descripcion,
          () => {
            subscriber.next(true);
            subscriber.complete();
            elementoActivo?.focus();
          },
          () => {
            subscriber.next(false);
            subscriber.complete();
            elementoActivo?.focus();
          },
        )
        .set('labels', { ok: datos.textoConfirmar, cancel: 'Cancelar' });

      // alertify no agrega ningún atributo ARIA — se suple a mano para no
      // perder lo que ya daba el Dialog de CDK (rol de diálogo + foco inicial).
      queueMicrotask(() => {
        const nodo = document.querySelector<HTMLElement>('.ajs-dialog');
        if (!nodo) return;
        nodo.setAttribute('role', 'alertdialog');
        nodo.setAttribute('aria-modal', 'true');
        const titulo = nodo.querySelector<HTMLElement>('.ajs-header');
        const cuerpo = nodo.querySelector<HTMLElement>('.ajs-body');
        if (titulo) {
          titulo.id = 'ajs-titulo-confirmacion';
          nodo.setAttribute('aria-labelledby', titulo.id);
        }
        if (cuerpo) {
          cuerpo.id = 'ajs-cuerpo-confirmacion';
          nodo.setAttribute('aria-describedby', cuerpo.id);
        }
        nodo.querySelector<HTMLElement>('.ajs-cancel, .ajs-ok')?.focus();
      });

      void dialogo;
    });
  }
}

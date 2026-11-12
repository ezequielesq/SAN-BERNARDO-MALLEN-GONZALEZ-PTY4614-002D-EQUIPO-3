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
        .set('labels', { ok: datos.textoConfirmar, cancel: 'Cancelar' })
        // alertify vuelve a fijar el foco por su cuenta ~1s después de abrir
        // (al terminar su transición CSS, o por su temporizador de respaldo si
        // esta no dispara) usando este ajuste — sin él, ese segundo enfoque
        // interno pisaría nuestro foco manual de abajo y volvería a dejar el
        // foco en OK, el botón destructivo.
        .set('defaultFocus', 'cancel')
        .set('closable', false);

      // alertify cuelga su raíz de <body>. Un <dialog> abierto con showModal()
      // (app-modal) vive en la capa superior del navegador y deja inerte todo
      // lo que no esté dentro de él: la confirmación quedaría tapada por el
      // backdrop y sin recibir clics. Se monta dentro del modal abierto y se
      // devuelve a <body> al terminar (el diálogo de alertify es un singleton
      // y se destruiría junto con el modal de Angular).
      // `elements` es API pública de alertify pero no figura en sus tipos.
      const raiz = (dialogo as unknown as { elements: { root: HTMLElement } }).elements.root;
      const modalAbierto = document.querySelector('dialog:modal');
      if (modalAbierto) modalAbierto.appendChild(raiz);

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
        // Preferir el botón Cancelar: querySelector con una lista de selectores
        // devuelve el primero en orden de DOM (no de la lista), y alertify enfoca
        // el OK (índice 0) por defecto — eso dejaría un Enter reflejo confirmando
        // una acción destructiva.
        const cancelar = nodo.querySelector<HTMLElement>('.ajs-cancel');
        const ok = nodo.querySelector<HTMLElement>('.ajs-ok');
        (cancelar ?? ok)?.focus();
      });

      // Cierra el diálogo si el suscriptor se da de baja antes de que el
      // usuario responda (p. ej. cambios-pendientes.guard.ts cancela la
      // suscripción pendiente ante una navegación que la reemplaza). alertify
      // ignora close() si el diálogo ya está cerrado, así que esto es un
      // no-op tras una respuesta normal y nunca vuelve a disparar onok/oncancel.
      return () => {
        dialogo.close();
        if (raiz.parentNode !== document.body) document.body.appendChild(raiz);
      };
    });
  }
}

import { Dialog } from '@angular/cdk/dialog';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ConfirmarDialogo, DatosConfirmacion } from './confirmar-dialogo';

@Injectable({ providedIn: 'root' })
export class ConfirmacionService {
  private readonly dialog = inject(Dialog);

  confirmar(datos: DatosConfirmacion): Observable<boolean> {
    const ref = this.dialog.open<boolean, DatosConfirmacion>(ConfirmarDialogo, {
      data: datos,
      ariaLabelledBy: 'confirmar-dialogo-titulo',
      ariaDescribedBy: 'confirmar-dialogo-descripcion',
      autoFocus: '#confirmar-dialogo-cancelar',
      restoreFocus: true,
      width: '28rem',
      maxWidth: 'calc(100vw - 32px)',
    });
    return ref.closed.pipe(map(resultado => resultado === true));
  }
}

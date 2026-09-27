import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

export interface DatosConfirmacion {
  titulo: string;
  descripcion: string;
  textoConfirmar: string;
}

@Component({
  selector: 'app-confirmar-dialogo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './confirmar-dialogo.html',
})
export class ConfirmarDialogo {
  protected readonly datos = inject<DatosConfirmacion>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<boolean>>(DialogRef);

  protected cerrar(confirmado: boolean): void {
    this.ref.close(confirmado);
  }
}

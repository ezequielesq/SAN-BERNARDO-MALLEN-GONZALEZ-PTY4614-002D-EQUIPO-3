import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';
import { ConfirmacionService } from './confirmacion.service';

export interface ConCambiosPendientes {
  tieneCambiosPendientes(): boolean;
}

export const cambiosPendientesGuard: CanDeactivateFn<ConCambiosPendientes> = componente =>
  componente.tieneCambiosPendientes()
    ? inject(ConfirmacionService).confirmar({
        titulo: 'Tienes cambios sin guardar',
        descripcion: 'Si sales ahora, se perderán los cambios de este formulario.',
        textoConfirmar: 'Salir sin guardar',
      })
    : true;

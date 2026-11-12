import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { OrdenVista } from './orden';

/** Encabezado de columna ordenable: `<th appThOrden [orden]="orden" columna="nombre">Nombre</th>`. */
@Component({
  selector: 'th[appThOrden]',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { scope: 'col', '[attr.aria-sort]': 'orden().ariaSort(columna())' },
  template: `
    <button type="button" class="boton-orden" (click)="orden().alternar(columna())">
      <ng-content />
      <i class="fa {{ orden().icono(columna()) }}" aria-hidden="true"></i>
    </button>
  `,
})
export class ThOrden {
  readonly orden = input.required<OrdenVista>();
  readonly columna = input.required<string>();
}

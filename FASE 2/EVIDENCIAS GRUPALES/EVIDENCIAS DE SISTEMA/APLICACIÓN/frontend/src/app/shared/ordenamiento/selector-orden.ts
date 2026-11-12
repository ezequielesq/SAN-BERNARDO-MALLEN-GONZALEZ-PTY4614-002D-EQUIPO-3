import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { OrdenVista } from './orden';

export interface OpcionOrden {
  valor: string;
  etiqueta: string;
}

/** Control para ordenar listas de tarjetas, que no tienen encabezados de columna. */
@Component({
  selector: 'app-selector-orden',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="selector-orden">
      <label [for]="idCampo()"><b>Ordenar por</b></label>
      <select
        #campo
        [id]="idCampo()"
        class="w3-select"
        style="width: auto; display: inline-block"
        (change)="orden().fijarColumna(campo.value)"
      >
        @for (o of opciones(); track o.valor) {
          <option [value]="o.valor" [selected]="orden().esColumna(o.valor)">{{ o.etiqueta }}</option>
        }
      </select>
      <button type="button" class="w3-button w3-border w3-round" (click)="orden().invertir()">
        <i class="fa {{ orden().direccion() === 'asc' ? 'fa-arrow-up' : 'fa-arrow-down' }}" aria-hidden="true"></i>
        {{ orden().direccion() === 'asc' ? 'Ascendente' : 'Descendente' }}
      </button>
    </div>
  `,
})
export class SelectorOrden {
  readonly orden = input.required<OrdenVista>();
  readonly opciones = input.required<readonly OpcionOrden[]>();
  readonly idCampo = input.required<string>();
}

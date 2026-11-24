import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

export interface ItemBarra {
  etiqueta: string;
  valor: number;
  /** Texto ya formateado del valor (pesos o cantidad con unidad). */
  texto: string;
}

const ANCHO = 640;
const FILA = 34;
const IZQ = 200;
const DER = 110;

function recortar(texto: string, maximo: number): string {
  return texto.length <= maximo ? texto : `${texto.slice(0, maximo - 1)}…`;
}

@Component({
  selector: 'app-grafico-barras-horizontales',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './grafico-barras-horizontales.html',
})
export class GraficoBarrasHorizontales {
  readonly items = input.required<readonly ItemBarra[]>();
  readonly descripcion = input.required<string>();

  protected readonly verTabla = signal(false);
  protected readonly ancho = ANCHO;
  protected readonly xBarra = IZQ;

  protected readonly alto = computed(() => 16 + this.items().length * FILA);

  protected readonly barras = computed(() => {
    const maximo = Math.max(1, ...this.items().map((i) => i.valor));
    return this.items().map((item, i) => {
      const largo = ((ANCHO - IZQ - DER) * Math.max(0, item.valor)) / maximo;
      return {
        y: 8 + i * FILA,
        largo,
        etiqueta: recortar(item.etiqueta, 28),
        completa: item.etiqueta,
        texto: item.texto,
        xTexto: IZQ + largo + 6,
      };
    });
  });
}

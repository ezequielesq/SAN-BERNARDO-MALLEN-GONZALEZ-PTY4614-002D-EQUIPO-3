import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { Paginacion } from '../../../shared/paginacion/paginacion';
import { paginaAjustada } from '../../../shared/paginacion/paginar';
import { maximoBarras } from './escala';
import { idUnico } from './id-unico';
import { TAMANO_TABLA_INICIAL, filasVisibles, hayPaginasTabla } from './tabla-grafico';

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
  imports: [Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './grafico-barras-horizontales.html',
})
export class GraficoBarrasHorizontales {
  readonly items = input.required<readonly ItemBarra[]>();
  readonly descripcion = input.required<string>();

  protected readonly verTabla = signal(false);
  protected readonly idTabla = idUnico('grafico-tabla');
  protected readonly idTamanoTabla = `${this.idTabla}-tamano`;

  /** Paginación solo de la tabla alternativa: el gráfico dibuja todos los puntos. Vuelve a 1 si cambian los datos o el tamaño. */
  protected readonly paginaTabla = linkedSignal({ source: this.items, computation: () => 1 });
  protected readonly tamanoTabla = signal(TAMANO_TABLA_INICIAL);
  protected readonly opcionesTamanoTabla = [5, 10, 20, 50] as const;
  protected readonly paginaTablaActual = computed(() =>
    paginaAjustada(this.paginaTabla(), this.items().length, this.tamanoTabla()),
  );
  protected readonly filasTabla = computed(() =>
    filasVisibles(this.items(), this.paginaTablaActual(), this.tamanoTabla()),
  );
  /** Hay paginador solo con más filas que el tamaño inicial (umbral fijo, no el tamaño elegido); con menos se ven todas. */
  protected readonly conPaginador = computed(() => hayPaginasTabla(this.items().length));

  protected cambiarTamanoTabla(tamano: number): void {
    this.tamanoTabla.set(tamano);
    this.paginaTabla.set(1);
  }
  protected readonly ancho = ANCHO;
  protected readonly xBarra = IZQ;

  protected readonly alto = computed(() => 16 + this.items().length * FILA);

  protected readonly barras = computed(() => {
    const maximo = maximoBarras(this.items().map((i) => i.valor));
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

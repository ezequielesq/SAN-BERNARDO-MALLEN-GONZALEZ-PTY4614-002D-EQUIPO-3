import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { marcasEnteras, margenIzquierdo } from './escala';
import { idUnico } from './id-unico';

export interface PuntoLinea {
  etiqueta: string;
  valor: number;
}

const ANCHO = 640;
const ALTO = 240;
const MARGEN = { der: 24, arr: 16, abajo: 40 };

/** Línea con un punto por observación; el eje vertical se ajusta al rango de los datos (con holgura). */
@Component({
  selector: 'app-grafico-linea',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './grafico-linea.html',
})
export class GraficoLinea {
  readonly puntos = input.required<readonly PuntoLinea[]>();
  readonly descripcion = input.required<string>();
  readonly formatear = input.required<(n: number) => string>();

  protected readonly verTabla = signal(false);
  protected readonly idTabla = idUnico('grafico-tabla');
  protected readonly ancho = ANCHO;
  protected readonly alto = ALTO;
  protected readonly xEjeDer = ANCHO - MARGEN.der;
  protected readonly yEtiqueta = ALTO - MARGEN.abajo + 16;

  private readonly areaAlto = ALTO - MARGEN.arr - MARGEN.abajo;

  private readonly dominio = computed(() => {
    const valores = this.puntos().map((p) => p.valor);
    if (valores.length === 0) return { bajo: 0, alto: 1 };
    const minimo = Math.min(...valores);
    const maximo = Math.max(...valores);
    const holgura = (maximo - minimo || maximo || 1) * 0.2;
    return { bajo: Math.max(0, minimo - holgura), alto: maximo + holgura };
  });

  private y(valor: number): number {
    const { bajo, alto } = this.dominio();
    return MARGEN.arr + this.areaAlto * (1 - (valor - bajo) / (alto - bajo));
  }

  /** Marcas enteras y sin repetir: la etiqueta y la posición usan el mismo valor redondeado. */
  protected readonly marcasY = computed(() => {
    const { bajo, alto } = this.dominio();
    return marcasEnteras(bajo, alto, 4).map((valor) => ({ valor, y: this.y(valor) }));
  });

  /** Crece si alguna etiqueta del eje (p. ej. $12.345.678) no cabe en el margen mínimo. */
  protected readonly xEjeIzq = computed(() =>
    margenIzquierdo(this.marcasY().map((m) => this.formatear()(m.valor))),
  );

  protected readonly marcas = computed(() => {
    const puntos = this.puntos();
    const paso = Math.max(1, Math.ceil(puntos.length / 8));
    const izq = this.xEjeIzq();
    const areaAncho = ANCHO - izq - MARGEN.der;
    return puntos.map((p, i) => ({
      etiqueta: p.etiqueta,
      mostrarEtiqueta: i % paso === 0,
      x: puntos.length === 1 ? izq + areaAncho / 2 : izq + (areaAncho * i) / (puntos.length - 1),
      y: this.y(p.valor),
      valor: p.valor,
    }));
  });

  protected readonly linea = computed(() => this.marcas().map((m) => `${m.x},${m.y}`).join(' '));
}

import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

export interface PuntoLinea {
  etiqueta: string;
  valor: number;
}

const ANCHO = 640;
const ALTO = 240;
const MARGEN = { izq: 76, der: 24, arr: 16, abajo: 40 };

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
  protected readonly ancho = ANCHO;
  protected readonly alto = ALTO;
  protected readonly xEjeIzq = MARGEN.izq;
  protected readonly xEjeDer = ANCHO - MARGEN.der;
  protected readonly yEtiqueta = ALTO - MARGEN.abajo + 16;

  private readonly areaAncho = ANCHO - MARGEN.izq - MARGEN.der;
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

  protected readonly marcasY = computed(() => {
    const { bajo, alto } = this.dominio();
    return [0, 1, 2, 3, 4].map((i) => {
      const valor = bajo + ((alto - bajo) * i) / 4;
      return { valor: Math.round(valor), y: this.y(valor) };
    });
  });

  protected readonly marcas = computed(() => {
    const puntos = this.puntos();
    const paso = Math.max(1, Math.ceil(puntos.length / 8));
    return puntos.map((p, i) => ({
      etiqueta: p.etiqueta,
      mostrarEtiqueta: i % paso === 0,
      x: puntos.length === 1 ? MARGEN.izq + this.areaAncho / 2 : MARGEN.izq + (this.areaAncho * i) / (puntos.length - 1),
      y: this.y(p.valor),
      valor: p.valor,
    }));
  });

  protected readonly linea = computed(() => this.marcas().map((m) => `${m.x},${m.y}`).join(' '));
}

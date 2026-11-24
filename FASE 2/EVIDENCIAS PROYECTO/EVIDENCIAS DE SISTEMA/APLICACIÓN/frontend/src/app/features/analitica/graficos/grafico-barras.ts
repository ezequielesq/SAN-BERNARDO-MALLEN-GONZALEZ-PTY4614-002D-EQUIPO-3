import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { marcas, margenIzquierdo, maximoRedondo } from './escala';
import { idUnico } from './id-unico';

export interface PuntoBarra {
  etiqueta: string;
  valor: number;
  secundario: number;
}

const ANCHO = 640;
const ALTO = 260;
const MARGEN = { der: 12, arr: 12, abajo: 40 };

/** Barras verticales con una segunda serie (rayada) al lado; los valores negativos se dibujan como 0. */
@Component({
  selector: 'app-grafico-barras',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './grafico-barras.html',
})
export class GraficoBarras {
  readonly puntos = input.required<readonly PuntoBarra[]>();
  readonly descripcion = input.required<string>();
  readonly nombrePrincipal = input('Consumo');
  readonly nombreSecundario = input('Pérdidas');
  readonly formatear = input.required<(n: number) => string>();
  /** Falso cuando la segunda serie no aplica (p. ej. pérdidas en modo cantidad): se oculta de barras, leyenda y tabla. */
  readonly mostrarSecundario = input(true);

  protected readonly verTabla = signal(false);
  protected readonly idTabla = idUnico('grafico-tabla');
  protected readonly ancho = ANCHO;
  protected readonly alto = ALTO;
  protected readonly xEjeDer = ANCHO - MARGEN.der;
  protected readonly yEtiqueta = ALTO - MARGEN.abajo + 16;

  private readonly areaAlto = ALTO - MARGEN.arr - MARGEN.abajo;

  protected readonly maximo = computed(() =>
    maximoRedondo(Math.max(0, ...this.puntos().flatMap((p) => [p.valor, p.secundario]))),
  );

  protected readonly marcasY = computed(() =>
    marcas(this.maximo(), 4).map((valor) => ({
      valor,
      y: MARGEN.arr + this.areaAlto * (1 - valor / this.maximo()),
    })),
  );

  /** Crece si alguna etiqueta del eje (p. ej. $12.345.678) no cabe en el margen mínimo. */
  protected readonly xEjeIzq = computed(() =>
    margenIzquierdo(this.marcasY().map((m) => this.formatear()(m.valor))),
  );

  protected readonly barras = computed(() => {
    const puntos = this.puntos();
    const izq = this.xEjeIzq();
    const grupo = (ANCHO - izq - MARGEN.der) / Math.max(puntos.length, 1);
    const series = this.mostrarSecundario() ? 2 : 1;
    const anchoBarra = Math.max(2, Math.min(20, (grupo * 0.8) / series));
    const paso = Math.max(1, Math.ceil(puntos.length / 10));
    const base = MARGEN.arr + this.areaAlto;
    return puntos.map((p, i) => {
      const centro = izq + grupo * (i + 0.5);
      const altoPrincipal = (this.areaAlto * Math.max(0, p.valor)) / this.maximo();
      const altoSecundario = (this.areaAlto * Math.max(0, p.secundario)) / this.maximo();
      return {
        etiqueta: p.etiqueta,
        mostrarEtiqueta: i % paso === 0,
        centro,
        x: this.mostrarSecundario() ? centro - anchoBarra : centro - anchoBarra / 2,
        anchoBarra,
        yPrincipal: base - altoPrincipal,
        altoPrincipal,
        ySecundario: base - altoSecundario,
        altoSecundario,
      };
    });
  });
}

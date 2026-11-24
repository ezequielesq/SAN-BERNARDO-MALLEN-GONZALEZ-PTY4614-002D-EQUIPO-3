import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { FiltroPanel } from './analitica.modelo';
import { AnaliticaService } from './analitica.service';
import { formatearFechaCorta, formatearPesos, formatearVariacion } from './formato';
import { GraficoLinea, PuntoLinea } from './graficos/grafico-linea';

@Component({
  selector: 'app-analitica-precios',
  imports: [EstadoVista, GraficoLinea],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-precios.html',
})
export class AnaliticaPrecios {
  readonly filtro = input.required<FiltroPanel | null>();

  private readonly service = inject(AnaliticaService);

  /** El historial es de un solo producto: solo se consulta cuando el filtro tiene exactamente uno. */
  protected readonly productoId = computed(() => {
    const ids = this.filtro()?.productoIds ?? [];
    return ids.length === 1 ? ids[0] : null;
  });
  protected readonly historial = this.service.precios(this.filtro, this.productoId);
  protected readonly h = computed(() => (this.historial.hasValue() ? this.historial.value() : null));

  protected readonly puntos = computed<PuntoLinea[]>(
    () => this.h()?.compras.map((c) => ({ etiqueta: formatearFechaCorta(c.fecha), valor: c.costo })) ?? [],
  );
  protected readonly descripcion = computed(
    () => `Gráfico de línea con el costo unitario de las compras de ${this.h()?.nombre ?? 'el producto'}.`,
  );
  protected readonly formatear = formatearPesos;

  /** Estadísticos listos para mostrar; nulos cuando hay menos de dos compras (el servidor no los calcula). */
  protected readonly estadisticos = computed(() => {
    const h = this.h();
    if (h === null || h.ultimo === null) return null;
    const pesos = (n: number | null) => (n === null ? '—' : formatearPesos(n));
    return {
      ultimo: formatearPesos(h.ultimo),
      anterior: pesos(h.anterior),
      variacion: formatearVariacion(h.variacionPct),
      rango: `${pesos(h.minimo)} / ${pesos(h.maximo)}`,
      promedio: pesos(h.promedio),
      ponderado: pesos(h.promedioPonderado),
    };
  });
}

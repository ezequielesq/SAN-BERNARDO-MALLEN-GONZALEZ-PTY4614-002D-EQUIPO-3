import { DatePipe } from '@angular/common';
import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { EstadoBadge } from '../../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../../shared/estado-vista/estado-vista';
import { MovimientoService } from '../movimiento.service';
import { UMBRAL_POR_DEFECTO, esVueltaALotes, etiquetaDias, normalizarDias, tipoBadge } from './lote-estado';

@Component({
  selector: 'app-lote-detalle',
  imports: [DatePipe, RouterLink, EstadoVista, EstadoBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lote-detalle.html',
})
export class LoteDetalle {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly movimientoService = inject(MovimientoService);

  /** El umbral llega desde la lista para que ambas pantallas coincidan en el estado del lote. */
  protected readonly diasPorVencer = toSignal(
    this.route.queryParamMap.pipe(map((p) => normalizarDias(p.get('diasPorVencer') ?? '') ?? UMBRAL_POR_DEFECTO)),
    { initialValue: UMBRAL_POR_DEFECTO },
  );
  private readonly id = toSignal(this.route.paramMap.pipe(map((p) => Number(p.get('id')))), {
    initialValue: NaN,
  });

  protected readonly detalle = this.movimientoService.detalleLote(this.id, this.diasPorVencer);
  protected readonly datos = computed(() => (this.detalle.hasValue() ? this.detalle.value() : undefined));

  /**
   * Se llegó desde la lista de lotes dentro de la app (y no por un enlace directo ni desde otra pantalla):
   * «Volver» retrocede en el historial y la lista reaparece con sus filtros, que viven en su URL.
   */
  private readonly vieneDeLotes = (() => {
    const previa = this.router.currentNavigation()?.previousNavigation?.finalUrl;
    return esVueltaALotes(previa ? this.router.serializeUrl(previa) : null);
  })();

  protected readonly etiquetaDias = etiquetaDias;
  protected readonly tipoBadge = tipoBadge;

  /** Con historial de la lista retrocede; si no, el enlace lleva a la lista con el umbral (comportamiento por defecto). */
  protected volver(evento: Event): void {
    if (!this.vieneDeLotes) return;
    evento.preventDefault();
    this.location.back();
  }
}

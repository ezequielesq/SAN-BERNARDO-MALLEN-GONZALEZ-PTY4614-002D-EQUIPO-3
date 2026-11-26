import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { EstadoBadge } from '../../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../../shared/estado-vista/estado-vista';
import { MovimientoService } from '../movimiento.service';
import { etiquetaDias, normalizarDias, tipoBadge } from './lote-estado';

const UMBRAL_INICIAL = 7;

@Component({
  selector: 'app-lote-detalle',
  imports: [DatePipe, RouterLink, EstadoVista, EstadoBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lote-detalle.html',
})
export class LoteDetalle {
  private readonly route = inject(ActivatedRoute);
  private readonly movimientoService = inject(MovimientoService);

  /** El umbral llega desde la lista para que ambas pantallas coincidan en el estado del lote. */
  protected readonly diasPorVencer = toSignal(
    this.route.queryParamMap.pipe(map((p) => normalizarDias(p.get('diasPorVencer') ?? '') ?? UMBRAL_INICIAL)),
    { initialValue: UMBRAL_INICIAL },
  );
  private readonly id = toSignal(this.route.paramMap.pipe(map((p) => Number(p.get('id')))), {
    initialValue: NaN,
  });

  protected readonly detalle = this.movimientoService.detalleLote(this.id, this.diasPorVencer);
  protected readonly datos = computed(() => (this.detalle.hasValue() ? this.detalle.value() : undefined));

  protected readonly etiquetaDias = etiquetaDias;
  protected readonly tipoBadge = tipoBadge;
}

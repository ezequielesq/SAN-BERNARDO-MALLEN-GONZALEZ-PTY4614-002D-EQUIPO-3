import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { MovimientoService } from './movimiento.service';

@Component({
  selector: 'app-alertas-lista',
  imports: [DatePipe, EstadoVista, EstadoBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './alertas-lista.html',
})
export class AlertasLista {
  private readonly movimientoService = inject(MovimientoService);

  protected readonly diasUmbral = signal(7);
  protected readonly alertas = this.movimientoService.alertasVencimiento(this.diasUmbral);
  protected readonly todos = computed(() => (this.alertas.hasValue() ? this.alertas.value() : []));

  protected cambiarUmbral(valor: string): void {
    const numero = Number(valor);
    if (Number.isInteger(numero) && numero > 0) this.diasUmbral.set(numero);
  }
}

import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type TipoEstado =
  | 'normal'
  | 'bajo'
  | 'critico'
  | 'agotado'
  | 'pendiente'
  | 'aprobada'
  | 'rechazada'
  | 'deshabilitado'
  | 'inactiva'
  | 'entrada'
  | 'salida';

interface ConfigBadge {
  clase: string;
  icono: string;
  texto: string;
}

const BADGES: Record<TipoEstado, ConfigBadge> = {
  normal: { clase: 'w3-emerald', icono: 'fa-check-circle', texto: 'Normal' },
  bajo: { clase: 'w3-amber', icono: 'fa-exclamation-triangle', texto: 'Bajo' },
  critico: { clase: 'w3-orange', icono: 'fa-exclamation-triangle', texto: 'Crítico' },
  agotado: { clase: 'w3-crimson', icono: 'fa-times-circle', texto: 'Agotado' },
  pendiente: { clase: 'w3-amber', icono: 'fa-clock-o', texto: 'Pendiente' },
  aprobada: { clase: 'w3-emerald', icono: 'fa-check-circle', texto: 'Aprobada' },
  rechazada: { clase: 'w3-crimson', icono: 'fa-times-circle', texto: 'Rechazada' },
  deshabilitado: { clase: 'w3-light-grey', icono: 'fa-ban', texto: 'Deshabilitado' },
  inactiva: { clase: 'w3-light-grey', icono: 'fa-ban', texto: 'Inactiva' },
  entrada: { clase: 'w3-emerald', icono: 'fa-arrow-down', texto: 'Entrada' },
  salida: { clase: 'w3-crimson', icono: 'fa-arrow-up', texto: 'Salida' },
};

@Component({
  selector: 'app-estado-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="'w3-tag w3-round ' + config().clase"
    ><i [class]="'fa ' + config().icono" aria-hidden="true"></i> {{ config().texto }}</span
  >`,
})
export class EstadoBadge {
  readonly estado = input.required<TipoEstado>();
  protected readonly config = computed(() => BADGES[this.estado()]);
}

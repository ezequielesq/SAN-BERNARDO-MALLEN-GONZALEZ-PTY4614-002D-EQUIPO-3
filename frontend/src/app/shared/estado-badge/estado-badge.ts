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
  normal: { clase: 'w3-emerald', icono: '●', texto: 'Normal' },
  bajo: { clase: 'w3-amber', icono: '▲', texto: 'Bajo' },
  critico: { clase: 'w3-crimson', icono: '⚠', texto: 'Crítico' },
  agotado: { clase: 'w3-crimson', icono: '✕', texto: 'Agotado' },
  pendiente: { clase: 'w3-amber', icono: '◷', texto: 'Pendiente' },
  aprobada: { clase: 'w3-emerald', icono: '✓', texto: 'Aprobada' },
  rechazada: { clase: 'w3-crimson', icono: '✕', texto: 'Rechazada' },
  deshabilitado: { clase: 'w3-light-grey', icono: '○', texto: 'Deshabilitado' },
  inactiva: { clase: 'w3-light-grey', icono: '○', texto: 'Inactiva' },
  entrada: { clase: 'w3-emerald', icono: '↓', texto: 'Entrada' },
  salida: { clase: 'w3-crimson', icono: '↑', texto: 'Salida' },
};

@Component({
  selector: 'app-estado-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="'w3-tag w3-round ' + config().clase"
    ><span aria-hidden="true">{{ config().icono }} </span>{{ config().texto }}</span
  >`,
})
export class EstadoBadge {
  readonly estado = input.required<TipoEstado>();
  protected readonly config = computed(() => BADGES[this.estado()]);
}

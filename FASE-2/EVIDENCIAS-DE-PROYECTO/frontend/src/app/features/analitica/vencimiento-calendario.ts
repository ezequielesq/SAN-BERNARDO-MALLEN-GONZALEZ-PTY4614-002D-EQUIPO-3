import { ChangeDetectionStrategy, Component, computed, inject, output, signal } from '@angular/core';
import { CalendarioMes, MarcaCalendario } from '../../shared/calendario-mes/calendario-mes';
import { rangoDelMes } from '../../shared/utils/calendario';
import { AnaliticaService } from './analitica.service';

const LEYENDA: readonly MarcaCalendario[] = [
  {
    clave: 'proximo',
    etiqueta: 'Próximo a vencer',
    color: '#ff9800',
    descripcion: 'con productos próximos a vencer',
  },
  { clave: 'vencido', etiqueta: 'Vencido', color: '#f44336', descripcion: 'con productos vencidos' },
];

@Component({
  selector: 'app-vencimiento-calendario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CalendarioMes],
  template: `
    <app-calendario-mes
      [marcas]="marcasPorDia()"
      [leyenda]="leyenda"
      [diaActivo]="diaActivo()"
      [cargando]="vencimientos.status() === 'loading'"
      (diaSeleccionado)="seleccionarDia($event)"
      (rangoCambiado)="alCambiarRango($event)"
    />
  `,
})
export class VencimientoCalendario {
  private readonly analiticaService = inject(AnaliticaService);

  readonly diaActivo = signal<string | null>(null);
  readonly diaSeleccionado = output<string | null>();

  protected readonly leyenda = LEYENDA;

  private readonly hoy = new Date();
  private readonly rango = signal(rangoDelMes(this.hoy.getFullYear(), this.hoy.getMonth()));

  private readonly filtro = computed(() => ({ ...this.rango(), categoriaId: null }));

  protected readonly vencimientos = this.analiticaService.vencimientosRango(this.filtro);

  protected readonly marcasPorDia = computed(() => {
    const mapa = new Map<string, string[]>();
    if (!this.vencimientos.hasValue()) return mapa;
    for (const v of this.vencimientos.value()) {
      const fecha = v.fechaVencimiento.slice(0, 10);
      const claves = mapa.get(fecha) ?? [];
      const clave = v.vencido ? 'vencido' : 'proximo';
      if (!claves.includes(clave)) claves.push(clave);
      mapa.set(fecha, claves);
    }
    return mapa;
  });

  protected alCambiarRango(nuevo: { desde: string; hasta: string }): void {
    const actual = this.rango();
    if (actual.desde !== nuevo.desde || actual.hasta !== nuevo.hasta) this.rango.set(nuevo);
  }

  protected seleccionarDia(fecha: string | null): void {
    this.diaActivo.set(fecha);
    this.diaSeleccionado.emit(fecha);
  }
}

import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { OpcionOrden, SelectorOrden } from '../../shared/ordenamiento/selector-orden';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { AlertaVencimiento } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

type ColumnaOrdenAlerta = 'fechaVencimiento' | 'productoNombre' | 'cantidadDisponible';

const EXTRACTORES_ALERTA: Extractores<AlertaVencimiento, ColumnaOrdenAlerta> = {
  fechaVencimiento: (a) => Date.parse(a.fechaVencimiento),
  productoNombre: (a) => a.productoNombre,
  cantidadDisponible: (a) => a.cantidadDisponible,
};

const OPCIONES_ALERTA: readonly OpcionOrden[] = [
  { valor: 'fechaVencimiento', etiqueta: 'Fecha de vencimiento' },
  { valor: 'productoNombre', etiqueta: 'Producto' },
  { valor: 'cantidadDisponible', etiqueta: 'Cantidad' },
];

@Component({
  selector: 'app-alertas-lista',
  imports: [DatePipe, EstadoVista, EstadoBadge, SelectorOrden, Paginacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './alertas-lista.html',
})
export class AlertasLista {
  private readonly movimientoService = inject(MovimientoService);

  protected readonly diasUmbral = signal(7);
  protected readonly alertas = this.movimientoService.alertasVencimiento(this.diasUmbral);
  protected readonly paginaActual = signal(1);
  protected readonly tamanoPagina = signal(15);
  protected readonly opcionesTamano = [10, 15, 20, 50] as const;
  protected readonly orden = new Orden<ColumnaOrdenAlerta>({ columna: 'fechaVencimiento' }, () =>
    this.paginaActual.set(1),
  );
  protected readonly opciones = OPCIONES_ALERTA;
  protected readonly todos = computed(() => (this.alertas.hasValue() ? this.alertas.value() : []));
  protected readonly ordenadas = computed(() => ordenar(this.todos(), this.orden, EXTRACTORES_ALERTA));
  protected readonly paginadas = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.ordenadas().slice(inicio, inicio + this.tamanoPagina());
  });

  protected cambiarUmbral(valor: string): void {
    const numero = Number(valor);
    if (Number.isInteger(numero) && numero > 0) {
      this.diasUmbral.set(numero);
      this.paginaActual.set(1);
    }
  }

  protected cambiarPagina(pagina: number): void {
    this.paginaActual.set(pagina);
  }

  protected cambiarTamanoPagina(tamano: number): void {
    this.tamanoPagina.set(tamano);
    this.paginaActual.set(1);
  }
}

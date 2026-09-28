import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { EstadoBadge } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { ProductoService } from '../productos/producto.service';
import { MovimientoCalendario } from './movimiento-calendario';
import { MovimientoService } from './movimiento.service';

type Vista = 'historial' | 'calendario';

@Component({
  selector: 'app-movimiento-lista',
  imports: [RouterLink, DatePipe, EstadoVista, EstadoBadge, Paginacion, MovimientoCalendario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-lista.html',
})
export class MovimientoLista {
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  private readonly injector = inject(Injector);
  protected readonly auth = inject(AuthService);

  protected readonly puedeGestionar = computed(() => {
    const rol = this.auth.rol();
    return rol === 'ADMIN' || rol === 'BODEGUERO';
  });

  protected readonly vista = signal<Vista>('historial');
  protected readonly productoIdFiltro = signal<number | null>(null);
  protected readonly desdeFiltro = signal<string | null>(null);
  protected readonly hastaFiltro = signal<string | null>(null);
  protected readonly paginaActual = signal(1);
  protected readonly tamanoPagina = signal(10);

  protected readonly productos = this.productoService.listar(signal(true));
  protected readonly opcionesProducto = computed(() =>
    this.productos.hasValue() ? this.productos.value() : [],
  );

  protected readonly filtro = computed(() => ({
    productoId: this.productoIdFiltro(),
    desde: this.desdeFiltro(),
    hasta: this.hastaFiltro(),
  }));

  protected readonly movimientos = this.movimientoService.listar(this.filtro);
  protected readonly todos = computed(() =>
    this.movimientos.hasValue() ? this.movimientos.value() : [],
  );

  protected readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.todos().slice(inicio, inicio + this.tamanoPagina());
  });

  protected cambiarProductoFiltro(valor: string): void {
    this.productoIdFiltro.set(valor === '' ? null : Number(valor));
    this.paginaActual.set(1);
  }

  protected cambiarDesde(valor: string): void {
    this.desdeFiltro.set(valor === '' ? null : valor);
    this.paginaActual.set(1);
  }

  protected cambiarHasta(valor: string): void {
    this.hastaFiltro.set(valor === '' ? null : valor);
    this.paginaActual.set(1);
  }

  protected cambiarPagina(pagina: number): void {
    this.paginaActual.set(pagina);
  }

  protected cambiarTamanoPagina(tamano: number): void {
    this.tamanoPagina.set(tamano);
    this.paginaActual.set(1);
  }

  protected alSeleccionarDiaCalendario(fecha: string | null): void {
    this.desdeFiltro.set(fecha);
    this.hastaFiltro.set(fecha);
    this.paginaActual.set(1);
    this.vista.set('historial');
    // Al volver a 'historial' el botón del calendario que originó el click
    // desaparece del DOM y el foco cae a <body>: lo movemos al título de la
    // página, siguiendo el mismo patrón de shell.ts (enfocarTitulo) y
    // producto-lista.ts (limpiarFiltros).
    afterNextRender(() => document.getElementById('titulo-movimientos')?.focus(), {
      injector: this.injector,
    });
  }
}

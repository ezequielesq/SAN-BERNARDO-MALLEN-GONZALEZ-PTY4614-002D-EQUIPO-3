import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { Modal } from '../../shared/modal/modal';
import { EstadoBadge, TipoEstado } from '../../shared/estado-badge/estado-badge';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { ProductoService } from '../productos/producto.service';
import { MovimientoCalendario } from './movimiento-calendario';
import { MovimientoFormularioEntrada } from './movimiento-formulario-entrada';
import { MovimientoFormularioSalida } from './movimiento-formulario-salida';
import { Movimiento, TipoMovimiento } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

type ColumnaOrdenMovimiento = 'fecha' | 'productoNombre' | 'tipo' | 'cantidad' | 'usuarioEmail';

@Component({
  selector: 'app-movimiento-lista',
  imports: [DatePipe, EstadoVista, EstadoBadge, Paginacion, MovimientoCalendario, Modal, MovimientoFormularioEntrada, MovimientoFormularioSalida],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-lista.html',
})
export class MovimientoLista {
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  protected readonly auth = inject(AuthService);

  protected readonly mostrarEntrada = signal(false);
  protected readonly mostrarSalida = signal(false);

  protected alGuardarMovimiento(): void {
    this.movimientos.reload();
  }

  protected readonly puedeGestionar = computed(() => {
    const rol = this.auth.rol();
    return rol === 'ADMIN' || rol === 'BODEGUERO';
  });

  protected readonly productoIdFiltro = signal<number | null>(null);
  protected readonly desdeFiltro = signal<string | null>(null);
  protected readonly hastaFiltro = signal<string | null>(null);
  protected readonly paginaActual = signal(1);
  protected readonly tamanoPagina = signal(10);
  protected readonly tipoFiltro = signal<TipoMovimiento | null>(null);
  protected readonly usuarioFiltro = signal('');
  protected readonly columnaOrden = signal<ColumnaOrdenMovimiento | null>(null);
  protected readonly direccionOrden = signal<'asc' | 'desc'>('asc');

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

  protected readonly filtrados = computed(() => {
    const tipo = this.tipoFiltro();
    const texto = this.usuarioFiltro().trim().toLowerCase();
    return this.todos().filter(
      (m) =>
        (tipo === null || m.tipo === tipo) &&
        (texto === '' || (m.usuarioEmail ?? '').toLowerCase().includes(texto)),
    );
  });

  protected readonly ordenados = computed(() => {
    const columna = this.columnaOrden();
    const lista = [...this.filtrados()];
    if (columna === null) return lista;
    const dir = this.direccionOrden() === 'asc' ? 1 : -1;
    lista.sort((a, b) => {
      switch (columna) {
        case 'fecha':
          return (new Date(a.fecha).getTime() - new Date(b.fecha).getTime()) * dir;
        case 'productoNombre':
          return a.productoNombre.localeCompare(b.productoNombre, 'es') * dir;
        case 'tipo':
          return a.tipo.localeCompare(b.tipo, 'es') * dir;
        case 'cantidad':
          return (a.cantidad - b.cantidad) * dir;
        case 'usuarioEmail':
          return (a.usuarioEmail ?? '').localeCompare(b.usuarioEmail ?? '', 'es') * dir;
      }
    });
    return lista;
  });

  protected readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.ordenados().slice(inicio, inicio + this.tamanoPagina());
  });

  protected estadoTipo(tipo: TipoMovimiento): TipoEstado {
    return tipo.toLowerCase() as TipoEstado;
  }

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
  }

  protected cambiarTipoFiltro(valor: string): void {
    this.tipoFiltro.set(valor === '' ? null : (valor as TipoMovimiento));
    this.paginaActual.set(1);
  }

  protected cambiarUsuarioFiltro(valor: string): void {
    this.usuarioFiltro.set(valor);
    this.paginaActual.set(1);
  }

  protected limpiarFiltrosCliente(): void {
    this.tipoFiltro.set(null);
    this.usuarioFiltro.set('');
    this.paginaActual.set(1);
  }

  protected ordenarPor(columna: ColumnaOrdenMovimiento): void {
    if (this.columnaOrden() === columna) {
      this.direccionOrden.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.columnaOrden.set(columna);
      this.direccionOrden.set('asc');
    }
  }

  protected iconoOrden(columna: ColumnaOrdenMovimiento): string {
    if (this.columnaOrden() !== columna) return 'fa-sort';
    return this.direccionOrden() === 'asc' ? 'fa-sort-up' : 'fa-sort-down';
  }

  protected ariaSort(columna: ColumnaOrdenMovimiento): 'ascending' | 'descending' | null {
    if (this.columnaOrden() !== columna) return null;
    return this.direccionOrden() === 'asc' ? 'ascending' : 'descending';
  }

  protected costoTotal(m: Movimiento): number | null {
    return m.costoUnitario !== null ? m.costoUnitario * m.cantidad : null;
  }
}

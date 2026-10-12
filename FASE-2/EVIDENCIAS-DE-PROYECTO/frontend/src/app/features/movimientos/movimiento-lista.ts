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
import { Extractores, Orden, ordenar } from '../../shared/ordenamiento/orden';
import { ThOrden } from '../../shared/ordenamiento/th-orden';
import { Paginacion } from '../../shared/paginacion/paginacion';
import { ProductoService } from '../productos/producto.service';
import { MovimientoCalendario } from './movimiento-calendario';
import { MovimientoFormularioEntrada } from './movimiento-formulario-entrada';
import { MovimientoFormularioSalida } from './movimiento-formulario-salida';
import { Movimiento, TipoMovimiento } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

type ColumnaOrdenMovimiento = 'fecha' | 'productoNombre' | 'tipo' | 'cantidad' | 'usuarioEmail';

const EXTRACTORES_MOVIMIENTO: Extractores<Movimiento, ColumnaOrdenMovimiento> = {
  fecha: (m) => new Date(m.fecha).getTime(),
  productoNombre: (m) => m.productoNombre,
  tipo: (m) => m.tipo,
  cantidad: (m) => m.cantidad,
  usuarioEmail: (m) => m.usuarioEmail,
};

@Component({
  selector: 'app-movimiento-lista',
  imports: [DatePipe, EstadoVista, EstadoBadge, Paginacion, ThOrden, MovimientoCalendario, Modal, MovimientoFormularioEntrada, MovimientoFormularioSalida],
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
  protected readonly orden = new Orden<ColumnaOrdenMovimiento>(null, () => this.paginaActual.set(1));

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

  protected readonly ordenados = computed(() => ordenar(this.filtrados(), this.orden, EXTRACTORES_MOVIMIENTO));

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

  protected costoTotal(m: Movimiento): number | null {
    return m.costoUnitario !== null ? m.costoUnitario * m.cantidad : null;
  }
}

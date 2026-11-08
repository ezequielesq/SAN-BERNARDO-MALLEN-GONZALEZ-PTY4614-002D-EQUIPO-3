import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { filter, switchMap } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { traducirError } from '../../core/errores/traducir-error';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
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
import { Movimiento } from './movimiento.modelo';
import { MovimientoService } from './movimiento.service';

type ColumnaOrdenMovimiento = 'fecha' | 'productoNombre' | 'tipo' | 'cantidad' | 'usuarioEmail';

const EXTRACTORES_MOVIMIENTO: Extractores<Movimiento, ColumnaOrdenMovimiento> = {
  fecha: (m) => new Date(m.fecha).getTime(),
  productoNombre: (m) => m.productoNombre,
  tipo: (m) => m.tipoNombre,
  cantidad: (m) => m.cantidad,
  usuarioEmail: (m) => m.usuarioEmail,
};

@Component({
  selector: 'app-movimiento-lista',
  imports: [DatePipe, RouterLink, EstadoVista, EstadoBadge, Paginacion, ThOrden, MovimientoCalendario, Modal, MovimientoFormularioEntrada, MovimientoFormularioSalida],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './movimiento-lista.html',
})
export class MovimientoLista {
  private readonly movimientoService = inject(MovimientoService);
  private readonly productoService = inject(ProductoService);
  protected readonly auth = inject(AuthService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

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
  protected readonly sentidoFiltro = signal<number | null>(null);
  protected readonly tipoFiltro = signal<string | null>(null);
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

  private readonly tiposManuales = this.movimientoService.listarTipos();
  /** Códigos de las operaciones registradas a mano: solo esas se pueden corregir. */
  private readonly codigosManuales = computed(
    () => new Set((this.tiposManuales.hasValue() ? this.tiposManuales.value() : []).map((t) => t.codigo)),
  );

  protected readonly puedeCorregir = computed(() => this.auth.rol() === 'BODEGUERO');
  protected readonly corrigiendoId = signal<number | null>(null);

  /** Tipos presentes en los datos cargados (código + nombre), para el filtro. */
  protected readonly opcionesTipo = computed(() => {
    const mapa = new Map<string, string>();
    for (const m of this.todos()) mapa.set(m.tipo, m.tipoNombre);
    return [...mapa.entries()]
      .map(([codigo, nombre]) => ({ codigo, nombre }))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  protected readonly filtrados = computed(() => {
    const sentido = this.sentidoFiltro();
    const tipo = this.tipoFiltro();
    const texto = this.usuarioFiltro().trim().toLowerCase();
    return this.todos().filter(
      (m) =>
        (sentido === null || m.sentido === sentido) &&
        (tipo === null || m.tipo === tipo) &&
        (texto === '' || (m.usuarioEmail ?? '').toLowerCase().includes(texto)),
    );
  });

  protected readonly ordenados = computed(() => ordenar(this.filtrados(), this.orden, EXTRACTORES_MOVIMIENTO));

  protected readonly paginados = computed(() => {
    const inicio = (this.paginaActual() - 1) * this.tamanoPagina();
    return this.ordenados().slice(inicio, inicio + this.tamanoPagina());
  });

  protected estadoSentido(m: Movimiento): TipoEstado {
    return m.sentido > 0 ? 'entrada' : 'salida';
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

  protected cambiarSentidoFiltro(valor: string): void {
    this.sentidoFiltro.set(valor === '' ? null : Number(valor));
    this.paginaActual.set(1);
  }

  protected cambiarTipoFiltro(valor: string): void {
    this.tipoFiltro.set(valor === '' ? null : valor);
    this.paginaActual.set(1);
  }

  protected cambiarUsuarioFiltro(valor: string): void {
    this.usuarioFiltro.set(valor);
    this.paginaActual.set(1);
  }

  protected limpiarFiltrosCliente(): void {
    this.sentidoFiltro.set(null);
    this.tipoFiltro.set(null);
    this.usuarioFiltro.set('');
    this.paginaActual.set(1);
  }

  protected costoTotal(m: Movimiento): number | null {
    return m.costoUnitario !== null ? m.costoUnitario * m.cantidad : null;
  }

  protected esCorregible(m: Movimiento): boolean {
    return this.puedeCorregir() && !m.corregida && !m.esCorreccion && this.codigosManuales().has(m.tipo);
  }

  protected corregir(m: Movimiento): void {
    if (this.corrigiendoId() !== null) return;
    this.confirmacion
      .confirmar({
        titulo: `¿Corregir la operación #${m.operacionId}?`,
        descripcion:
          'Se registrará una operación contraria con las mismas cantidades y lotes. La original no se modifica ni se borra.',
        textoConfirmar: 'Corregir operación',
      })
      .pipe(
        filter((confirmado) => confirmado),
        switchMap(() => {
          this.corrigiendoId.set(m.operacionId);
          return this.movimientoService.corregir(m.operacionId, null);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.corrigiendoId.set(null);
          this.notificaciones.exito(`Operación #${m.operacionId} corregida.`);
          this.movimientos.reload();
        },
        error: (err: unknown) => {
          this.corrigiendoId.set(null);
          this.notificaciones.error(traducirError(err).mensaje);
        },
      });
  }
}

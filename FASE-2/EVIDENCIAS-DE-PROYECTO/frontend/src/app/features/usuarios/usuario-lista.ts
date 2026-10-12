import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, filter, switchMap } from 'rxjs';
import { traducirError } from '../../core/errores/traducir-error';
import { ETIQUETA_ROL } from '../../core/layout/navegacion';
import { NotificacionService } from '../../core/notificaciones/notificacion.service';
import { ConfirmacionService } from '../../shared/confirmar-dialogo/confirmacion.service';
import { EstadoVista } from '../../shared/estado-vista/estado-vista';
import { Modal } from '../../shared/modal/modal';
import { UsuarioFormulario } from './usuario-formulario';
import { Usuario } from './usuario.modelo';
import { UsuarioService } from './usuario.service';

function normalizar(texto: string): string {
  return texto
    .toLocaleLowerCase('es')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .trim();
}

@Component({
  selector: 'app-usuario-lista',
  imports: [EstadoVista, Modal, UsuarioFormulario],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './usuario-lista.html',
})
export class UsuarioLista {
  private readonly usuarioService = inject(UsuarioService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly notificaciones = inject(NotificacionService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly mostrarDesactivados = signal(false);
  protected readonly busqueda = signal('');
  protected readonly modalUsuario = signal<'nuevo' | Usuario | null>(null);
  protected readonly procesandoId = signal<number | null>(null);

  protected readonly usuarios = this.usuarioService.listar(this.mostrarDesactivados);
  protected readonly todos = computed(() => (this.usuarios.hasValue() ? this.usuarios.value() : []));
  protected readonly filtrados = computed(() => {
    const texto = normalizar(this.busqueda());
    return this.todos().filter(
      (u) =>
        texto === '' || normalizar(u.nombre).includes(texto) || normalizar(u.email).includes(texto),
    );
  });

  protected readonly tituloModal = computed(() =>
    this.modalUsuario() === 'nuevo' ? 'Nuevo usuario' : 'Editar usuario',
  );
  protected readonly usuarioModal = computed<Usuario | null>(() => {
    const m = this.modalUsuario();
    return m === 'nuevo' || m === null ? null : m;
  });

  protected etiquetaRol(u: Usuario): string {
    return ETIQUETA_ROL[u.rol];
  }

  protected alGuardarUsuario(): void {
    this.usuarios.reload();
  }

  protected cerrarModal(): void {
    this.modalUsuario.set(null);
  }

  protected alternar(u: Usuario): void {
    if (this.procesandoId() !== null) return;
    if (!u.activo) {
      this.ejecutar(u, this.usuarioService.reactivar(u.id), `Usuario '${u.nombre}' reactivado.`);
      return;
    }
    this.confirmacion
      .confirmar({
        titulo: `¿Desactivar a '${u.nombre}'?`,
        descripcion:
          'No podrá iniciar sesión ni aparecerá en la vista pública de empleados. Su historial se conserva y puedes reactivarlo después.',
        textoConfirmar: 'Desactivar usuario',
      })
      .pipe(
        filter((confirmado) => confirmado),
        switchMap(() => {
          this.procesandoId.set(u.id);
          return this.usuarioService.desactivar(u.id);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => this.alTerminar(`Usuario '${u.nombre}' desactivado.`),
        error: (err: unknown) => this.alFallar(err),
      });
  }

  private ejecutar(u: Usuario, operacion$: Observable<void>, mensaje: string): void {
    this.procesandoId.set(u.id);
    operacion$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.alTerminar(mensaje),
      error: (err: unknown) => this.alFallar(err),
    });
  }

  private alTerminar(mensaje: string): void {
    this.procesandoId.set(null);
    this.notificaciones.exito(mensaje);
    this.usuarios.reload();
  }

  private alFallar(err: unknown): void {
    this.procesandoId.set(null);
    this.notificaciones.error(traducirError(err).mensaje);
  }
}

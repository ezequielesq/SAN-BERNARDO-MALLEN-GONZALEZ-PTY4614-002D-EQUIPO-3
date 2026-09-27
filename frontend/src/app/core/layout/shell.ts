import { CdkTrapFocus } from '@angular/cdk/a11y';
import {
  ChangeDetectionStrategy, Component, ElementRef, Injector, afterNextRender, computed, inject, signal, viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, NavigationStart, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { NotificacionService } from '../notificaciones/notificacion.service';
import { ETIQUETA_ROL, agruparNavegacion } from './navegacion';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CdkTrapFocus],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.html',
  host: { '(document:keydown.escape)': 'cerrarMenu()' },
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly notificaciones = inject(NotificacionService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);

  private readonly main = viewChild.required<ElementRef<HTMLElement>>('main');
  private readonly botonMenu = viewChild<ElementRef<HTMLButtonElement>>('botonMenu');

  protected readonly menuAbierto = signal(false);
  protected readonly grupos = computed(() => agruparNavegacion(this.auth.rol()));
  protected readonly etiquetaRol = computed(() => {
    const rol = this.auth.rol();
    return rol === null ? '' : ETIQUETA_ROL[rol];
  });

  constructor() {
    this.router.events.pipe(takeUntilDestroyed()).subscribe(evento => {
      if (evento instanceof NavigationStart) {
        this.notificaciones.limpiar();
      }
      if (evento instanceof NavigationEnd) {
        this.menuAbierto.set(false);
        this.enfocarTitulo();
      }
    });
  }

  protected abrirMenu(): void {
    this.menuAbierto.set(true);
  }

  protected cerrarMenu(): void {
    if (!this.menuAbierto()) return;
    this.menuAbierto.set(false);
    this.botonMenu()?.nativeElement.focus();
  }

  protected saltarAlContenido(evento: Event): void {
    evento.preventDefault();
    this.main().nativeElement.focus();
  }

  protected cerrarSesion(): void {
    this.auth.logout();
  }

  private enfocarTitulo(): void {
    afterNextRender(() => {
      this.main().nativeElement.querySelector<HTMLElement>('h1')?.focus();
    }, { injector: this.injector });
  }
}

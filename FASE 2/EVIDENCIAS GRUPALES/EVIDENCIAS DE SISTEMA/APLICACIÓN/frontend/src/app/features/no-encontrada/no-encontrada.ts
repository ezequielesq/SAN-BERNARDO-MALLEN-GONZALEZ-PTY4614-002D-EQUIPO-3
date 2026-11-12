import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { paginaInicialPara } from '../../core/layout/navegacion';

/** Ruta inexistente: explica qué pasó y lleva a la página de inicio del usuario. */
@Component({
  selector: 'app-no-encontrada',
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main id="contenido" class="w3-container w3-padding-32 w3-asphalt">
      <div class="w3-display-middle w3-white" style="width: 100%; max-width: 420px; padding: 16px">
        <h1 class="w3-xxlarge">Página no encontrada</h1>
        <p>La dirección que abriste no existe o fue movida.</p>
        <a class="w3-button w3-block w3-section" [routerLink]="inicio">Volver al inicio</a>
      </div>
    </main>
  `,
})
export class NoEncontrada {
  protected readonly inicio = paginaInicialPara(inject(AuthService).rol());
}

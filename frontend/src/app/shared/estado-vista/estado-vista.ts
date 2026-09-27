import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { traducirError } from '../../core/errores/traducir-error';

@Component({
  selector: 'app-estado-vista',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './estado-vista.html',
})
export class EstadoVista {
  readonly cargando = input.required<boolean>();
  readonly error = input<unknown>(undefined);
  readonly vacio = input(false);
  readonly mensajeCarga = input('Cargando…');
  readonly mensajeVacio = input('No hay registros.');
  readonly reintentar = output<void>();

  protected readonly mensajeError = computed(() => {
    const err = this.error();
    return err ? traducirError(err).mensaje : null;
  });
}

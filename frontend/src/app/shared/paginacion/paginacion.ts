import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-paginacion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="w3-bar w3-margin-top" aria-label="Paginación">
      <button
        type="button"
        class="w3-button w3-border w3-border-black w3-round"
        [attr.aria-disabled]="pagina() <= 1"
        [disabled]="pagina() <= 1"
        (click)="paginaCambiada.emit(pagina() - 1)"
      >
        <i class="fa fa-chevron-left" aria-hidden="true"></i> Anterior
      </button>
      <span class="w3-bar-item w3-small" aria-live="polite">
        Página {{ pagina() }} de {{ totalPaginas() }} · {{ totalItems() }} resultados
      </span>
      <button
        type="button"
        class="w3-button w3-border w3-border-black w3-round w3-right"
        [attr.aria-disabled]="pagina() >= totalPaginas()"
        [disabled]="pagina() >= totalPaginas()"
        (click)="paginaCambiada.emit(pagina() + 1)"
      >
        Siguiente <i class="fa fa-chevron-right" aria-hidden="true"></i>
      </button>
      <div class="w3-bar-item w3-right" style="display:flex; align-items:center; gap:4px">
        <label for="paginacion-tamano" class="w3-small">Filas por página:</label>
        <select
          id="paginacion-tamano"
          class="w3-select w3-border"
          style="width:auto"
          (change)="tamanoPaginaCambiado.emit(Number($any($event.target).value))"
        >
          @for (opcion of OPCIONES_TAMANO; track opcion) {
            <option [value]="opcion" [selected]="opcion === tamanoPagina()">{{ opcion }}</option>
          }
        </select>
      </div>
    </nav>
  `,
})
export class Paginacion {
  protected readonly OPCIONES_TAMANO = [5, 10, 20, 50] as const;
  protected readonly Number = Number;

  readonly pagina = input.required<number>();
  readonly totalItems = input.required<number>();
  readonly tamanoPagina = input.required<number>();
  readonly totalPaginas = computed(() => Math.max(1, Math.ceil(this.totalItems() / this.tamanoPagina())));

  readonly paginaCambiada = output<number>();
  readonly tamanoPaginaCambiado = output<number>();
}

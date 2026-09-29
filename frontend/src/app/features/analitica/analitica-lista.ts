import { ChangeDetectionStrategy, Component, signal } from '@angular/core';

type Pestana = 'consumo' | 'mas-usados' | 'vencimientos' | 'exportar';

@Component({
  selector: 'app-analitica-lista',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-lista.html',
})
export class AnaliticaLista {
  protected readonly pestana = signal<Pestana>('consumo');
}

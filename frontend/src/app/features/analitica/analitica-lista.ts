import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { AnaliticaConsumo } from './analitica-consumo';

type Pestana = 'consumo' | 'mas-usados' | 'vencimientos' | 'exportar';

@Component({
  selector: 'app-analitica-lista',
  imports: [AnaliticaConsumo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-lista.html',
})
export class AnaliticaLista {
  protected readonly pestana = signal<Pestana>('consumo');
}

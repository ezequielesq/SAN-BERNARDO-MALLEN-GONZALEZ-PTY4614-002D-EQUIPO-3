import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { AnaliticaConsumo } from './analitica-consumo';
import { AnaliticaMasUsados } from './analitica-mas-usados';
import { AnaliticaVencimientos } from './analitica-vencimientos';
import { AnaliticaExportar } from './analitica-exportar';

type Pestana = 'consumo' | 'mas-usados' | 'vencimientos' | 'exportar';

@Component({
  selector: 'app-analitica-lista',
  imports: [AnaliticaConsumo, AnaliticaMasUsados, AnaliticaVencimientos, AnaliticaExportar],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analitica-lista.html',
})
export class AnaliticaLista {
  protected readonly pestana = signal<Pestana>('consumo');
}

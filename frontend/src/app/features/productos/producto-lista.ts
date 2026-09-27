import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-producto-lista',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<h1 tabindex="-1">Productos</h1>',
})
export class ProductoLista {}

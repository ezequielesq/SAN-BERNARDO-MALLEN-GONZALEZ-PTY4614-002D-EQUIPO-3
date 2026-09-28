import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <footer class="w3-container w3-asphalt w3-padding">
      <span class="w3-left w3-text-white w3-small">Smart RDP · Restaurante Doña Paula</span>
      <span class="w3-right w3-text-white w3-small">v0.1.0</span>
    </footer>
  `,
})
export class Footer {}

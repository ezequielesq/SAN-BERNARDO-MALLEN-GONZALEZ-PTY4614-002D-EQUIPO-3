import { Injectable, inject } from '@angular/core';
import { NgxSpinnerService } from 'ngx-spinner';

const OPCIONES = {
  bdColor: 'rgba(0, 0, 0, 0.8)',
  size: 'medium' as const,
  color: '#fff',
  type: 'timer',
  fullScreen: true,
};

@Injectable({ providedIn: 'root' })
export class SpinnerService {
  private readonly spinner = inject(NgxSpinnerService);

  show(): void {
    this.spinner.show(undefined, OPCIONES);
  }

  hide(): void {
    this.spinner.hide();
  }
}

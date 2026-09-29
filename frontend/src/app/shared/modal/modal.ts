import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  input,
  output,
  viewChild,
} from '@angular/core';

@Component({
  selector: 'app-modal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './modal.html',
  styleUrl: './modal.css',
})
export class Modal implements AfterViewInit, OnDestroy {
  readonly titulo = input.required<string>();
  readonly cerrar = output<void>();

  private readonly dialogRef = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  private elementoActivo: HTMLElement | null = null;

  ngAfterViewInit(): void {
    this.elementoActivo = document.activeElement as HTMLElement | null;
    this.dialogRef().nativeElement.showModal();
  }

  ngOnDestroy(): void {
    this.elementoActivo?.focus();
  }

  protected alCancelar(): void {
    this.cerrar.emit();
  }
}

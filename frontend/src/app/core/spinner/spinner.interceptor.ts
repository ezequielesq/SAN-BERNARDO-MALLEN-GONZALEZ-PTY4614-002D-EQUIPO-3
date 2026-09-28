import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { SpinnerService } from './spinner.service';

/** Cabecera para peticiones que no deben mostrar el spinner global (ej. polling). */
export const SKIP_SPINNER_HEADER = 'X-Skip-Spinner';

export const spinnerInterceptor: HttpInterceptorFn = (req, next) => {
  const spinner = inject(SpinnerService);
  const omitir = req.headers.has(SKIP_SPINNER_HEADER);

  if (!omitir) spinner.show();

  const peticion = omitir ? req.clone({ headers: req.headers.delete(SKIP_SPINNER_HEADER) }) : req;

  return next(peticion).pipe(finalize(() => { if (!omitir) spinner.hide(); }));
};

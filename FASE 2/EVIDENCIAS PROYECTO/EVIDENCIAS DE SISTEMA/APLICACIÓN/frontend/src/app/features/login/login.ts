import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { traducirError } from '../../core/errores/traducir-error';
import { paginaInicialPara } from '../../core/layout/navegacion';

type CampoLogin = 'email' | 'password';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
})
export class Login {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly injector = inject(Injector);

  protected readonly form = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly mostrarPassword = signal(false);
  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);
  protected readonly sesionExpirada = this.route.snapshot.queryParamMap.get('expirada') === '1';

  protected mensajeError(campo: CampoLogin): string | null {
    const control = this.form.controls[campo];
    if (!(control.touched || this.enviado()) || !control.errors) return null;
    if (campo === 'email') {
      return control.hasError('required')
        ? 'Ingresa tu correo.'
        : 'Ingresa un correo válido, por ejemplo nombre@restaurante.cl.';
    }
    return 'Ingresa tu contraseña.';
  }

  protected alternarPassword(): void {
    this.mostrarPassword.update((v) => !v);
  }

  protected ingresar(): void {
    if (this.enviando()) return;
    this.enviado.set(true);
    this.errorGeneral.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      const primero: CampoLogin = this.form.controls.email.invalid ? 'email' : 'password';
      this.enfocar(`login-${primero}`);
      return;
    }
    this.enviando.set(true);
    this.auth
      .login(this.form.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (sesion) => {
          this.enviando.set(false);
          void this.router.navigateByUrl(this.destino(paginaInicialPara(sesion.rol)));
        },
        error: (err: unknown) => {
          this.enviando.set(false);
          const mensaje =
            err instanceof HttpErrorResponse && err.status === 401
              ? 'Correo o contraseña incorrectos.'
              : traducirError(err).mensaje;
          this.errorGeneral.set(mensaje);
          this.enfocar('login-error');
        },
      });
  }

  private destino(porDefecto: string): string {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
    const esInterna =
      returnUrl !== null &&
      returnUrl.startsWith('/') &&
      !returnUrl.startsWith('//') &&
      !returnUrl.startsWith('/login');
    return esInterna ? returnUrl : porDefecto;
  }

  private enfocar(id: string): void {
    afterNextRender(() => document.getElementById(id)?.focus(), { injector: this.injector });
  }
}

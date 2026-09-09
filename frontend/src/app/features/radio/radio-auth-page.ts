import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, finalize } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { RadioAccountApiService } from './radio-account-api.service';
import { RadioContext } from './radio-context';

type Mode = 'register' | 'login' | 'forgot' | 'reset';

@Component({
  selector: 'app-radio-auth-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './radio-auth-page.html',
  styleUrl: './radio-account.scss',
})
export class RadioAuthPage {
  readonly context = inject(RadioContext);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(RadioAccountApiService);
  private readonly auth = inject(AuthService);
  readonly mode: Mode = this.route.snapshot.data['mode'];
  readonly title = { register: 'Crear cuenta', login: 'Iniciar sesión', forgot: 'Recuperar contraseña', reset: 'Nueva contraseña' }[this.mode];
  readonly busy = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  private token = '';
  readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true }),
    lastName: new FormControl('', { nonNullable: true }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(40)] }),
    email: new FormControl('', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true }),
    confirmation: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    if (this.mode === 'register') {
      for (const name of ['firstName', 'lastName'] as const) this.form.controls[name].setValidators([Validators.required, Validators.maxLength(70)]);
    }
    if (this.mode !== 'reset') this.form.controls.email.setValidators([Validators.required, Validators.email, Validators.maxLength(254)]);
    if (this.mode !== 'forgot') this.form.controls.password.setValidators(this.mode === 'login'
      ? [Validators.required] : [Validators.required, Validators.minLength(12), Validators.maxLength(72)]);
    if (this.mode === 'reset' || this.mode === 'register') this.form.controls.confirmation.setValidators([Validators.required]);
    if (this.mode === 'reset') {
      this.token = new URLSearchParams(this.route.snapshot.fragment ?? '').get('token') ?? '';
      if (!/^[A-Za-z0-9_-]{43}$/.test(this.token)) this.error.set('El enlace no es válido. Solicitá uno nuevo.');
      // Keep the bearer secret out of browser history and subsequent referrers.
      void this.router.navigate([], { relativeTo: this.route, fragment: undefined, replaceUrl: true });
    }
  }

  submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy() || !this.context.slug()) return;
    const value = this.form.getRawValue();
    if ((this.mode === 'register' || this.mode === 'reset') && value.password !== value.confirmation) {
      this.error.set('Las contraseñas no coinciden.'); return;
    }
    if (this.mode === 'reset' && !/^[A-Za-z0-9_-]{43}$/.test(this.token)) return;
    const slug = this.context.slug()!;
    let request: Observable<unknown>;
    switch (this.mode) {
      case 'register': request = this.api.register(slug, {
        firstName: value.firstName.trim(), lastName: value.lastName.trim(), phone: value.phone.trim() || null,
        email: value.email.trim(), password: value.password,
      }); break;
      case 'login': request = this.auth.login({ email: value.email.trim(), password: value.password }); break;
      case 'forgot': request = this.api.forgot(slug, value.email.trim()); break;
      case 'reset': request = this.api.reset(slug, this.token, value.password); break;
    }
    this.busy.set(true); this.error.set(''); this.message.set('');
    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => {
        this.form.controls.password.reset(); this.form.controls.confirmation.reset();
        if (this.mode === 'login') void this.router.navigate(this.context.link('mi-cuenta'));
        else if (this.mode === 'reset') {
          this.token = ''; this.auth.markAnonymous();
          this.message.set('Contraseña actualizada. Iniciá sesión con tu nueva contraseña.');
        } else this.message.set(this.mode === 'forgot'
          ? 'Si existe una cuenta con ese correo, te enviamos instrucciones.'
          : 'Solicitud recibida. Iniciá sesión o recuperá tu contraseña si ya tenías cuenta.');
      },
      error: (error: HttpErrorResponse) => this.error.set(error.status === 429
        ? 'Demasiados intentos. Esperá unos minutos.' : error.status === 401
          ? 'El correo o la contraseña no son válidos.' : 'No pudimos completar la solicitud. Revisá los datos o solicitá un nuevo enlace.'),
    });
  }
}

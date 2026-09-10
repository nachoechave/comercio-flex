import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { RadioAccountApiService, RadioProfile } from './radio-account-api.service';
import { RadioContext } from './radio-context';

@Component({
  selector: 'app-radio-private-page',
  imports: [ReactiveFormsModule],
  styleUrl: './radio-account.scss',
  template: `
    <section class="account-card">
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      @if (loading()) { <p role="status">Cargando tu cuenta…</p> }
      @if (profile(); as user) {
        @if (!editing) {
          <h1>Hola, {{ user.firstName }}</h1><p>Tu cuenta está activa.</p>
        } @else {
          <h1>Mi perfil</h1>
          <p>Estos datos pertenecen a tu cuenta global de Comercio Flex.</p>
          <label>Email<input type="email" [value]="user.email" readonly /></label>
          <p class="hint">El cambio de email no está disponible todavía.</p>
          <form [formGroup]="form" (ngSubmit)="save()">
            <div class="fields">
              <label>Nombre<input formControlName="firstName" autocomplete="given-name" maxlength="70" required /></label>
              <label>Apellido<input formControlName="lastName" autocomplete="family-name" maxlength="70" required /></label>
            </div>
            <label>Teléfono (opcional)<input formControlName="phone" type="tel" autocomplete="tel" maxlength="40" /></label>
            @if (form.touched && form.invalid) { <p role="alert">Completá nombre y apellido.</p> }
            <button type="submit" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar cambios' }}</button>
          </form>
          @if (saved()) { <p role="status">Perfil actualizado.</p> }
        }
      }
    </section>`,
})
export class RadioPrivatePage {
  private readonly context = inject(RadioContext);
  private readonly api = inject(RadioAccountApiService);
  readonly editing = inject(ActivatedRoute).snapshot.data['profile'] === true;
  readonly profile = signal<RadioProfile | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly error = signal('');
  readonly form = new FormGroup({
    firstName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(70)] }),
    lastName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(70)] }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(40)] }),
  });
  constructor() {
    this.api.profile(this.context.slug()!).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: profile => { this.profile.set(profile); this.form.setValue({ firstName: profile.firstName, lastName: profile.lastName, phone: profile.phone ?? '' }); },
      error: () => this.error.set('No pudimos cargar tu perfil. Volvé a iniciar sesión e intentá nuevamente.'),
    });
  }
  save() {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const value = this.form.getRawValue();
    this.saving.set(true); this.saved.set(false); this.error.set('');
    this.api.update(this.context.slug()!, { firstName: value.firstName.trim(), lastName: value.lastName.trim(), phone: value.phone.trim() || null })
      .pipe(finalize(() => this.saving.set(false))).subscribe({
        next: profile => { this.profile.set(profile); this.saved.set(true); },
        error: () => this.error.set('No pudimos guardar los cambios. Intentá nuevamente.'),
      });
  }
}

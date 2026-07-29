import {
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  signal,
  ViewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, Subscription } from 'rxjs';

import { routeParam } from '../../../../core/auth/auth.guards';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { CategoryApiService } from '../category-api.service';
import { categoryErrorMessage, fieldProblem } from '../category-errors';
import { Category } from '../category.models';

function trimmedNameLength(control: AbstractControl<string>): ValidationErrors | null {
  const length = control.value.trim().length;
  if (length > 0 && length < 2) {
    return { minlength: { requiredLength: 2, actualLength: length } };
  }
  if (length > 120) {
    return { maxlength: { requiredLength: 120, actualLength: length } };
  }
  return null;
}

@Component({
  selector: 'app-category-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './category-form.html',
  styleUrl: './category-form.scss',
})
export class CategoryForm {
  private readonly api = inject(CategoryApiService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private saveSubscription?: Subscription;

  @ViewChild('nameInput') private nameInput?: ElementRef<HTMLInputElement>;

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly categoryId = toSignal(inheritedRouteParam(this.route, 'categoryId'), {
    initialValue: routeParam(this.route.snapshot, 'categoryId'),
  });
  readonly editing = computed(() => this.categoryId() !== null);
  readonly category = signal<Category | null>(null);
  readonly loading = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly serverNameError = signal<string | null>(null);
  readonly form = this.formBuilder.nonNullable.group({
    name: [
      '',
      [
        Validators.required,
        Validators.pattern(/.*\S.*/),
        trimmedNameLength,
      ],
    ],
  });

  constructor() {
    effect((onCleanup) => {
      const storeSlug = this.storeSlug();
      const categoryId = this.categoryId();
      this.resetForRouteChange();

      if (!storeSlug) {
        this.loadError.set('No pudimos identificar el comercio solicitado.');
        return;
      }
      if (!categoryId) {
        return;
      }

      this.loading.set(true);
      const subscription = this.api
        .get(storeSlug, categoryId)
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: (category) => {
            this.category.set(category);
            this.form.controls.name.setValue(category.name);
          },
          error: (error: unknown) =>
            this.loadError.set(
              categoryErrorMessage(
                error,
                'No pudimos cargar la categoría. Revisá tu conexión e intentá nuevamente.',
              ),
            ),
        });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  submit(): void {
    if (this.saving()) {
      return;
    }
    this.submitError.set(null);
    this.serverNameError.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      queueMicrotask(() => this.nameInput?.nativeElement.focus());
      return;
    }

    const storeSlug = this.storeSlug();
    const categoryId = this.categoryId();
    if (!storeSlug) {
      this.submitError.set('No pudimos identificar el comercio solicitado.');
      return;
    }
    const editing = categoryId !== null;
    const value = { name: this.form.controls.name.value.trim() };
    this.saving.set(true);
    const request =
      editing && categoryId
        ? this.api.update(storeSlug, categoryId, value)
        : this.api.create(storeSlug, value);

    this.saveSubscription = request
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () =>
          void this.router.navigate(
            ['/tiendas', storeSlug, 'admin', 'categorias'],
            { queryParams: { saved: editing ? 'updated' : 'created' } },
          ),
        error: (error: unknown) => {
          this.serverNameError.set(fieldProblem(error, 'name'));
          this.submitError.set(
            categoryErrorMessage(
              error,
              `No pudimos ${editing ? 'guardar los cambios' : 'crear la categoría'}.`,
            ),
          );
          if (this.serverNameError()) {
            queueMicrotask(() => this.nameInput?.nativeElement.focus());
          }
        },
      });
  }

  private resetForRouteChange(): void {
    this.saveSubscription?.unsubscribe();
    this.saveSubscription = undefined;
    this.form.reset({ name: '' });
    this.category.set(null);
    this.loading.set(false);
    this.loadError.set(null);
    this.saving.set(false);
    this.submitError.set(null);
    this.serverNameError.set(null);
  }
}

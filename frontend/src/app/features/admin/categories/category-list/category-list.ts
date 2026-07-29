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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize, Subscription } from 'rxjs';

import { AuthService } from '../../../../core/auth/auth.service';
import { routeParam } from '../../../../core/auth/auth.guards';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { CategoryApiService } from '../category-api.service';
import { categoryErrorMessage } from '../category-errors';
import { Category } from '../category.models';

@Component({
  selector: 'app-category-list',
  imports: [RouterLink],
  templateUrl: './category-list.html',
  styleUrl: './category-list.scss',
})
export class CategoryList {
  private readonly api = inject(CategoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly auth = inject(AuthService);
  private readonly reloadVersion = signal(0);
  private statusTrigger?: HTMLButtonElement;
  private statusSubscription?: Subscription;

  @ViewChild('confirmButton') private confirmButton?: ElementRef<HTMLButtonElement>;

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly statusMessage = signal<string | null>(null);
  readonly pendingCategoryId = signal<string | null>(null);
  readonly categoryToDeactivate = signal<Category | null>(null);
  readonly filter = signal('');
  readonly canManage = computed(() => {
    const role = this.auth.membershipFor(this.storeSlug() ?? '')?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });
  readonly filteredCategories = computed(() => {
    const term = this.filter().trim().toLocaleLowerCase('es');
    if (!term) {
      return this.categories();
    }
    return this.categories().filter(
      (category) =>
        category.name.toLocaleLowerCase('es').includes(term) ||
        category.slug.toLocaleLowerCase('es').includes(term),
    );
  });

  constructor() {
    effect((onCleanup) => {
      const storeSlug = this.storeSlug();
      this.reloadVersion();
      this.resetForStoreChange();
      const saved = this.route.snapshot.queryParamMap.get('saved');
      if (saved === 'created') {
        this.statusMessage.set('La categoría fue creada.');
      } else if (saved === 'updated') {
        this.statusMessage.set('Los cambios de la categoría fueron guardados.');
      }
      if (!storeSlug) {
        this.loading.set(false);
        this.loadError.set('No pudimos identificar el comercio solicitado.');
        return;
      }

      const subscription = this.api
        .list(storeSlug)
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: (categories) =>
            this.categories.set(
              [...categories].sort((left, right) =>
                left.name.localeCompare(right.name, 'es', { sensitivity: 'base' }),
              ),
            ),
          error: (error: unknown) =>
            this.loadError.set(
              categoryErrorMessage(
                error,
                'No pudimos cargar las categorías. Revisá tu conexión e intentá nuevamente.',
              ),
            ),
        });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  load(): void {
    this.reloadVersion.update((version) => version + 1);
  }

  updateFilter(event: Event): void {
    this.filter.set((event.target as HTMLInputElement).value);
  }

  requestStatusChange(category: Category, event: Event): void {
    if (!this.canManage()) {
      return;
    }
    this.statusTrigger = event.currentTarget as HTMLButtonElement;
    if (category.active) {
      this.categoryToDeactivate.set(category);
      queueMicrotask(() => this.confirmButton?.nativeElement.focus());
      return;
    }
    this.changeStatus(category, true);
  }

  cancelDeactivation(): void {
    this.categoryToDeactivate.set(null);
    queueMicrotask(() => this.statusTrigger?.focus());
  }

  confirmDeactivation(): void {
    const category = this.categoryToDeactivate();
    this.categoryToDeactivate.set(null);
    queueMicrotask(() => this.statusTrigger?.focus());
    if (category) {
      this.changeStatus(category, false);
    }
  }

  private changeStatus(category: Category, active: boolean): void {
    const storeSlug = this.storeSlug();
    if (!storeSlug) {
      this.actionError.set('No pudimos identificar el comercio solicitado.');
      return;
    }
    this.actionError.set(null);
    this.statusMessage.set(null);
    this.pendingCategoryId.set(category.id);
    this.statusSubscription?.unsubscribe();
    this.statusSubscription = this.api
      .setActive(storeSlug, category.id, active)
      .pipe(finalize(() => this.pendingCategoryId.set(null)))
      .subscribe({
        next: (updated) => {
          this.categories.update((categories) =>
            categories.map((item) => (item.id === updated.id ? updated : item)),
          );
          this.statusMessage.set(
            active
              ? `La categoría ${updated.name} fue activada.`
              : `La categoría ${updated.name} fue desactivada.`,
          );
        },
        error: (error: unknown) =>
          this.actionError.set(
            categoryErrorMessage(
              error,
              `No pudimos ${active ? 'activar' : 'desactivar'} la categoría.`,
            ),
          ),
      });
  }

  private resetForStoreChange(): void {
    this.statusSubscription?.unsubscribe();
    this.statusSubscription = undefined;
    this.categories.set([]);
    this.loading.set(true);
    this.loadError.set(null);
    this.actionError.set(null);
    this.statusMessage.set(null);
    this.pendingCategoryId.set(null);
    this.categoryToDeactivate.set(null);
    this.filter.set('');
  }
}

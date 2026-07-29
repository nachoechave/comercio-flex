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

import { routeParam } from '../../../../core/auth/auth.guards';
import { AuthService } from '../../../../core/auth/auth.service';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { ProductApiService } from '../product-api.service';
import { productErrorMessage } from '../product-errors';
import { ProductDetail as ProductDetailModel, ProductStatus } from '../product.models';

@Component({
  selector: 'app-product-detail',
  imports: [RouterLink],
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetail {
  private readonly api = inject(ProductApiService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private mutation?: Subscription;
  private statusTrigger?: HTMLButtonElement;

  @ViewChild('statusConfirm') private statusConfirm?: ElementRef<HTMLButtonElement>;

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly productId = toSignal(inheritedRouteParam(this.route, 'productId'), {
    initialValue: routeParam(this.route.snapshot, 'productId'),
  });
  readonly product = signal<ProductDetailModel | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly changingStatus = signal(false);
  readonly requestedStatus = signal<ProductStatus | null>(null);
  readonly canManage = computed(() => {
    const role = this.auth.membershipFor(this.storeSlug() ?? '')?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      const id = this.productId();
      this.mutation?.unsubscribe();
      this.product.set(null);
      this.loading.set(true);
      this.errorMessage.set(null);
      this.successMessage.set(null);
      this.requestedStatus.set(null);
      if (!slug || !id) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar el producto solicitado.');
        return;
      }
      const subscription = this.api.get(slug, id).subscribe({
        next: (product) => {
          this.product.set(product);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(productErrorMessage(error, 'No pudimos cargar el producto.'));
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  requestStatus(status: ProductStatus, event: Event): void {
    if (this.canManage()) {
      this.statusTrigger = event.currentTarget as HTMLButtonElement;
      this.requestedStatus.set(status);
      queueMicrotask(() => this.statusConfirm?.nativeElement.focus());
    }
  }

  cancelStatus(): void {
    this.requestedStatus.set(null);
    queueMicrotask(() => this.statusTrigger?.focus());
  }

  confirmStatus(): void {
    const product = this.product();
    const status = this.requestedStatus();
    const slug = this.storeSlug();
    if (!product || !status || !slug || this.changingStatus()) return;
    this.requestedStatus.set(null);
    queueMicrotask(() => this.statusTrigger?.focus());
    this.changingStatus.set(true);
    this.errorMessage.set(null);
    this.mutation = this.api
      .setStatus(slug, product.id, status, product.version)
      .pipe(finalize(() => this.changingStatus.set(false)))
      .subscribe({
        next: (updated) => {
          this.product.set(updated);
          this.successMessage.set(`El producto ahora está ${this.statusLabel(updated.status).toLowerCase()}.`);
        },
        error: (error: unknown) =>
          this.errorMessage.set(productErrorMessage(error, 'No pudimos cambiar el estado.')),
      });
  }

  statusLabel(status: ProductStatus): string {
    return { DRAFT: 'Borrador', PUBLISHED: 'Publicado', ARCHIVED: 'Archivado' }[status];
  }
}

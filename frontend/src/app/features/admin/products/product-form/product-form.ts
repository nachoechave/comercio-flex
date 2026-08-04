import {
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  OnDestroy,
  signal,
  ViewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormArray,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize, forkJoin, map, Observable, Subscription, switchMap } from 'rxjs';

import { routeParam } from '../../../../core/auth/auth.guards';
import { CsrfService } from '../../../../core/auth/csrf.service';
import { inheritedRouteParam } from '../../../../core/routing/inherited-route-param';
import { ProductApiService } from '../product-api.service';
import { productErrorMessage } from '../product-errors';
import { ProductCategory, ProductDetail, ProductVariant, SaveVariant } from '../product.models';

function positiveDecimal(control: AbstractControl<string>): ValidationErrors | null {
  const value = control.value.trim();
  const decimalPattern = /^(?:0|[1-9][0-9]{0,12})(?:\.[0-9]{1,2})?$/;
  const zeroPattern = /^0(?:\.0{1,2})?$/;
  if (!decimalPattern.test(value) || zeroPattern.test(value)) {
    return { price: true };
  }
  return null;
}

function normalizeProductName(value: string): string {
  return value.trim().replace(/\s+/g, ' ');
}

function normalizedProductNameLength(
  control: AbstractControl<string>,
): ValidationErrors | null {
  const length = normalizeProductName(control.value).length;
  if (length < 2) {
    return { minlength: { requiredLength: 2, actualLength: length } };
  }
  if (length > 160) {
    return { maxlength: { requiredLength: 160, actualLength: length } };
  }
  return null;
}

@Component({
  selector: 'app-product-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './product-form.html',
  styleUrl: './product-form.scss',
})
export class ProductForm implements OnDestroy {
  private readonly api = inject(ProductApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);
  private readonly csrf = inject(CsrfService);
  private readonly mutations: Subscription[] = [];
  private previewObjectUrl: string | null = null;
  private imageRemovalTrigger?: HTMLButtonElement;

  @ViewChild('imageInput') private imageInput?: ElementRef<HTMLInputElement>;
  @ViewChild('imageRemovalConfirm')
  private imageRemovalConfirm?: ElementRef<HTMLButtonElement>;

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), {
    initialValue: routeParam(this.route.snapshot, 'storeSlug') ?? '',
  });
  readonly productId = toSignal(inheritedRouteParam(this.route, 'productId'), {
    initialValue: routeParam(this.route.snapshot, 'productId'),
  });
  readonly editing = computed(() => this.productId() !== null);
  readonly product = signal<ProductDetail | null>(null);
  readonly categories = signal<ProductCategory[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);
  readonly pendingVariantId = signal<string | null>(null);
  readonly selectedImageFile = signal<File | null>(null);
  readonly imagePreviewUrl = signal<string | null>(null);
  readonly imageError = signal<string | null>(null);
  readonly imageBusy = signal(false);
  readonly confirmingImageRemoval = signal(false);
  readonly archived = computed(() => this.product()?.status === 'ARCHIVED');

  readonly form = this.formBuilder.nonNullable.group({
    name: ['', [normalizedProductNameLength]],
    description: ['', [Validators.maxLength(2000)]],
    categoryId: ['', [Validators.required]],
  });
  readonly imageAltText = this.formBuilder.nonNullable.control('', [
    Validators.required,
    Validators.minLength(1),
    Validators.maxLength(180),
  ]);
  readonly variants = new FormArray([this.newVariantForm()]);

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      const id = this.productId();
      this.resetForRoute();
      if (!slug) {
        this.loading.set(false);
        this.loadError.set('No pudimos identificar el comercio solicitado.');
        return;
      }

      const request: Observable<{
        categories: ProductCategory[];
        product?: ProductDetail;
      }> = id
        ? forkJoin({ categories: this.api.listCategories(slug), product: this.api.get(slug, id) })
        : this.api.listCategories(slug).pipe(map((categories) => ({ categories })));
      const subscription = request.subscribe({
        next: (result) => {
          this.categories.set(result.categories);
          if (result.product) this.populate(result.product);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.loadError.set(
            productErrorMessage(error, 'No pudimos cargar el formulario de producto.'),
          );
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  addVariant(): void {
    if (!this.archived()) this.variants.push(this.newVariantForm());
  }

  removeUnsavedVariant(index: number): void {
    const row = this.variants.at(index);
    if (!row.controls.id.value && this.variants.length > 1) this.variants.removeAt(index);
  }

  submitProduct(): void {
    if (this.saving() || this.archived()) return;
    this.formError.set(null);
    this.successMessage.set(null);
    this.form.markAllAsTouched();
    if (!this.editing()) this.variants.markAllAsTouched();
    if (this.form.invalid || (!this.editing() && this.variants.invalid)) {
      this.formError.set('Revisá los campos marcados antes de guardar.');
      return;
    }
    if (!this.editing() && !this.newVariantsAreUnique()) {
      return;
    }

    const slug = this.storeSlug();
    if (!slug) return;
    const metadata = this.form.getRawValue();
    this.saving.set(true);
    const description = metadata.description.trim() || undefined;
    const current = this.product();
    const request =
      this.editing() && current
        ? this.api.update(slug, current.id, {
            name: normalizeProductName(metadata.name),
            description,
            categoryId: metadata.categoryId,
            version: current.version,
          })
        : this.api.create(slug, {
            name: normalizeProductName(metadata.name),
            description,
            categoryId: metadata.categoryId,
            variants: this.variants.controls.map((row) => this.variantBody(row)),
          });

    const subscription = request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (product) => {
        if (!this.editing()) {
          void this.router.navigate(
            ['/tiendas', slug, 'admin', 'productos', product.id],
            { queryParams: { created: 'true' } },
          );
          return;
        }
        this.product.set(product);
        this.form.markAsPristine();
        this.successMessage.set('Los datos del producto fueron guardados.');
      },
      error: (error: unknown) =>
        this.formError.set(productErrorMessage(error, 'No pudimos guardar el producto.')),
    });
    this.mutations.push(subscription);
  }

  saveVariant(index: number): void {
    if (this.archived() || this.pendingVariantId()) return;
    const row = this.variants.at(index);
    row.markAllAsTouched();
    if (row.invalid) return;
    const slug = this.storeSlug();
    const productId = this.productId();
    if (!slug || !productId) return;

    this.formError.set(null);
    const variantId = row.controls.id.value;
    this.pendingVariantId.set(variantId ?? `new-${index}`);
    const body = this.variantBody(row);
    const request = variantId
      ? this.api.updateVariant(slug, productId, variantId, {
          ...body,
          version: row.controls.version.value ?? 0,
        })
      : this.api.createVariant(slug, productId, body);
    const subscription = request
      .pipe(finalize(() => this.pendingVariantId.set(null)))
      .subscribe({
        next: (variant) => {
          this.setVariantRow(row, variant);
          this.successMessage.set(`La variante ${variant.sku} fue guardada.`);
        },
        error: (error: unknown) =>
          this.formError.set(productErrorMessage(error, 'No pudimos guardar la variante.')),
      });
    this.mutations.push(subscription);
  }

  toggleVariant(index: number): void {
    if (this.archived() || this.pendingVariantId()) return;
    const row = this.variants.at(index);
    const slug = this.storeSlug();
    const productId = this.productId();
    const variantId = row.controls.id.value;
    const version = row.controls.version.value;
    if (!slug || !productId || !variantId || version === null) return;

    this.pendingVariantId.set(variantId);
    const subscription = this.api
      .setVariantActive(slug, productId, variantId, !row.controls.active.value, version)
      .pipe(finalize(() => this.pendingVariantId.set(null)))
      .subscribe({
        next: (variant) => this.setVariantRow(row, variant),
        error: (error: unknown) =>
          this.formError.set(productErrorMessage(error, 'No pudimos cambiar la variante.')),
      });
    this.mutations.push(subscription);
  }

  selectImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0) ?? null;
    this.clearPreviewObjectUrl();
    this.selectedImageFile.set(null);
    this.imagePreviewUrl.set(this.product()?.image?.url ?? null);
    this.imageError.set(null);
    this.successMessage.set(null);
    if (!file) return;
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      this.imageError.set('Elegí una imagen JPEG o PNG.');
      input.value = '';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      this.imageError.set('La imagen no puede superar los 5 MiB.');
      input.value = '';
      return;
    }
    this.selectedImageFile.set(file);
    this.previewObjectUrl = URL.createObjectURL(file);
    this.imagePreviewUrl.set(this.previewObjectUrl);
  }

  uploadImage(): void {
    const slug = this.storeSlug();
    const product = this.product();
    const file = this.selectedImageFile();
    const altText = this.imageAltText.value.trim();
    this.imageAltText.markAsTouched();
    this.imageError.set(null);
    this.successMessage.set(null);
    if (!slug || !product || !file || this.imageBusy()) return;
    if (!altText || altText.length > 180) {
      this.imageError.set('Ingresá un texto alternativo de entre 1 y 180 caracteres.');
      return;
    }

    this.imageBusy.set(true);
    const subscription = this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.api.uploadImage(slug, product.id, file, altText)),
        finalize(() => this.imageBusy.set(false)),
      )
      .subscribe({
        next: (image) => {
          this.product.update((current) => (current ? { ...current, image } : current));
          this.clearPreviewObjectUrl();
          this.selectedImageFile.set(null);
          this.imagePreviewUrl.set(image.url);
          if (this.imageInput) this.imageInput.nativeElement.value = '';
          this.successMessage.set('La imagen del producto fue guardada.');
          queueMicrotask(() => this.imageInput?.nativeElement.focus());
        },
        error: (error: unknown) =>
          this.imageError.set(productErrorMessage(error, 'No pudimos guardar la imagen.')),
      });
    this.mutations.push(subscription);
  }

  requestImageRemoval(event: Event): void {
    if (!this.archived() && this.product()?.image) {
      this.imageRemovalTrigger = event.currentTarget as HTMLButtonElement;
      this.confirmingImageRemoval.set(true);
      queueMicrotask(() => this.imageRemovalConfirm?.nativeElement.focus());
    }
  }

  cancelImageRemoval(event?: Event): void {
    event?.preventDefault();
    this.confirmingImageRemoval.set(false);
    queueMicrotask(() => this.imageRemovalTrigger?.focus());
  }

  deleteImage(): void {
    const slug = this.storeSlug();
    const product = this.product();
    if (!slug || !product?.image || this.imageBusy()) return;
    this.confirmingImageRemoval.set(false);
    this.imageError.set(null);
    this.successMessage.set(null);
    this.imageBusy.set(true);
    const subscription = this.csrf
      .ensureToken()
      .pipe(
        switchMap(() => this.api.deleteImage(slug, product.id)),
        finalize(() => this.imageBusy.set(false)),
      )
      .subscribe({
        next: () => {
          this.product.update((current) => (current ? { ...current, image: null } : current));
          this.clearPreviewObjectUrl();
          this.selectedImageFile.set(null);
          this.imagePreviewUrl.set(null);
          this.imageAltText.setValue('');
          if (this.imageInput) this.imageInput.nativeElement.value = '';
          this.successMessage.set('La imagen del producto fue eliminada.');
          queueMicrotask(() => this.imageInput?.nativeElement.focus());
        },
        error: (error: unknown) => {
          this.imageError.set(productErrorMessage(error, 'No pudimos eliminar la imagen.'));
          queueMicrotask(() => this.imageRemovalTrigger?.focus());
        },
      });
    this.mutations.push(subscription);
  }

  private newVariantForm(variant?: ProductVariant) {
    return this.formBuilder.group({
      id: this.formBuilder.control<string | null>(variant?.id ?? null),
      sku: this.formBuilder.nonNullable.control(variant?.sku ?? '', [
        Validators.required,
        Validators.maxLength(64),
        Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/),
      ]),
      price: this.formBuilder.nonNullable.control(variant?.price ?? '', [positiveDecimal]),
      size: this.formBuilder.nonNullable.control(variant?.size ?? '', [Validators.maxLength(60)]),
      color: this.formBuilder.nonNullable.control(variant?.color ?? '', [Validators.maxLength(60)]),
      active: this.formBuilder.nonNullable.control(variant?.active ?? true),
      version: this.formBuilder.control<number | null>(variant?.version ?? null),
    });
  }

  private variantBody(row: ReturnType<ProductForm['newVariantForm']>): SaveVariant {
    const value = row.getRawValue();
    return {
      sku: value.sku.trim(),
      price: value.price.trim(),
      ...(value.size.trim() ? { size: value.size.trim() } : {}),
      ...(value.color.trim() ? { color: value.color.trim() } : {}),
    };
  }

  private populate(product: ProductDetail): void {
    this.product.set(product);
    this.form.setValue({
      name: product.name,
      description: product.description ?? '',
      categoryId: product.category.id,
    });
    this.imageAltText.setValue(product.image?.altText ?? '');
    this.imagePreviewUrl.set(product.image?.url ?? null);
    this.variants.clear();
    for (const variant of product.variants) this.variants.push(this.newVariantForm(variant));
    if (product.status === 'ARCHIVED') {
      this.form.disable();
      this.variants.disable();
    }
  }

  private newVariantsAreUnique(): boolean {
    const skus = new Set<string>();
    const combinations = new Set<string>();
    for (const row of this.variants.controls) {
      const value = row.getRawValue();
      const sku = value.sku.trim().toUpperCase();
      const combination = `${value.size.trim().toLocaleLowerCase('es')}\u0000${value.color
        .trim()
        .toLocaleLowerCase('es')}`;
      if (skus.has(sku)) {
        this.formError.set(`El SKU ${value.sku.trim()} está repetido.`);
        return false;
      }
      if (combinations.has(combination)) {
        this.formError.set('Hay dos variantes con la misma combinación de talle y color.');
        return false;
      }
      skus.add(sku);
      combinations.add(combination);
    }
    return true;
  }

  private setVariantRow(
    row: ReturnType<ProductForm['newVariantForm']>,
    variant: ProductVariant,
  ): void {
    row.setValue({
      id: variant.id,
      sku: variant.sku,
      price: variant.price,
      size: variant.size ?? '',
      color: variant.color ?? '',
      active: variant.active,
      version: variant.version,
    });
    row.markAsPristine();
  }

  private resetForRoute(): void {
    for (const mutation of this.mutations.splice(0)) mutation.unsubscribe();
    this.form.enable();
    this.form.reset({ name: '', description: '', categoryId: '' });
    this.variants.clear();
    this.variants.push(this.newVariantForm());
    this.product.set(null);
    this.categories.set([]);
    this.loading.set(true);
    this.saving.set(false);
    this.loadError.set(null);
    this.formError.set(null);
    this.successMessage.set(null);
    this.pendingVariantId.set(null);
    this.clearPreviewObjectUrl();
    this.selectedImageFile.set(null);
    this.imagePreviewUrl.set(null);
    this.imageAltText.reset('');
    this.imageError.set(null);
    this.imageBusy.set(false);
    this.confirmingImageRemoval.set(false);
  }

  private clearPreviewObjectUrl(): void {
    if (this.previewObjectUrl) URL.revokeObjectURL(this.previewObjectUrl);
    this.previewObjectUrl = null;
  }

  ngOnDestroy(): void {
    for (const mutation of this.mutations.splice(0)) mutation.unsubscribe();
    this.clearPreviewObjectUrl();
  }
}

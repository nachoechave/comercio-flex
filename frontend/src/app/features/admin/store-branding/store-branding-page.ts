import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { BrandFont, StorefrontTemplate, TenantBranding } from '../../storefront/storefront.models';
import { STOREFRONT_TEMPLATES } from '../../storefront/storefront-template';
import { StoreSettingsApiService } from '../store-settings/store-settings-api.service';

type BrandAssetType = 'logo' | 'favicon' | 'hero';

@Component({
  selector: 'app-store-branding-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './store-branding-page.html',
  styleUrl: './store-branding-page.scss',
})
export class StoreBrandingPage {
  private readonly api = inject(StoreSettingsApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);

  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), { initialValue: '' });
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly assetBusy = signal<BrandAssetType | null>(null);
  readonly branding = signal<TenantBranding | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);
  readonly templates = STOREFRONT_TEMPLATES;
  readonly fonts: { value: BrandFont; label: string }[] = [
    { value: 'SYSTEM', label: 'Sistema' },
    { value: 'SANS', label: 'Sans moderna' },
    { value: 'SERIF', label: 'Serif editorial' },
  ];
  readonly form = this.formBuilder.nonNullable.group({
    primaryColor: ['#315A46', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    secondaryColor: ['#17352A', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    backgroundColor: ['#F7F5EF', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    textColor: ['#20241F', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    font: ['SYSTEM' as BrandFont, Validators.required],
    heroTitle: ['', Validators.maxLength(160)],
    heroSubtitle: ['', Validators.maxLength(300)],
    template: ['CATALOG' as StorefrontTemplate, Validators.required],
  });

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      if (!slug) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar el comercio.');
        return;
      }
      const subscription = this.api.getBranding(slug).subscribe({
        next: (branding) => {
          this.applyBranding(branding);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No pudimos cargar la apariencia de la tienda.');
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  selectTemplate(template: StorefrontTemplate): void {
    this.form.controls.template.setValue(template);
    this.form.controls.template.markAsDirty();
  }

  save(): void {
    this.form.markAllAsTouched();
    const slug = this.storeSlug();
    if (!slug || this.form.invalid || this.saving()) return;
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.api
      .updateBranding(slug, {
        ...value,
        heroTitle: value.heroTitle.trim() || null,
        heroSubtitle: value.heroSubtitle.trim() || null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (branding) => {
          this.applyBranding(branding);
          this.noticeMessage.set('La apariencia quedó actualizada.');
        },
        error: () => this.errorMessage.set('No pudimos guardar la apariencia.'),
      });
  }

  upload(type: BrandAssetType, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const slug = this.storeSlug();
    if (!file || !slug || this.assetBusy()) return;
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      this.errorMessage.set('Usá una imagen JPEG o PNG de hasta 5 MB.');
      return;
    }
    this.assetBusy.set(type);
    this.errorMessage.set(null);
    this.api
      .uploadBrandingAsset(slug, type, file)
      .pipe(finalize(() => this.assetBusy.set(null)))
      .subscribe({
        next: (branding) => {
          this.applyBranding(branding, false);
          this.noticeMessage.set('La imagen quedó actualizada.');
        },
        error: () => this.errorMessage.set('No pudimos procesar la imagen.'),
      });
  }

  remove(type: BrandAssetType): void {
    const slug = this.storeSlug();
    if (!slug || this.assetBusy()) return;
    this.assetBusy.set(type);
    this.api
      .deleteBrandingAsset(slug, type)
      .pipe(finalize(() => this.assetBusy.set(null)))
      .subscribe({
        next: (branding) => {
          this.applyBranding(branding, false);
          this.noticeMessage.set('La imagen fue eliminada.');
        },
        error: () => this.errorMessage.set('No pudimos eliminar la imagen.'),
      });
  }

  private applyBranding(branding: TenantBranding, updateForm = true): void {
    this.branding.set(branding);
    if (!updateForm) return;
    this.form.setValue({
      primaryColor: branding.primaryColor,
      secondaryColor: branding.secondaryColor,
      backgroundColor: branding.backgroundColor,
      textColor: branding.textColor,
      font: branding.font,
      heroTitle: branding.heroTitle ?? '',
      heroSubtitle: branding.heroSubtitle ?? '',
      template: branding.template,
    });
  }
}

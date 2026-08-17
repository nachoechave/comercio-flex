import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { SuperAdminApiService } from '../super-admin-api.service';
import {
  BrandAssetType,
  BrandFont,
  CompanyBranding,
  StorefrontTemplate,
} from '../super-admin.models';

@Component({
  selector: 'app-company-branding',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './company-branding.html',
  styleUrl: './company-branding.scss',
})
export class CompanyBrandingPage {
  private readonly api = inject(SuperAdminApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly formBuilder = inject(FormBuilder);
  readonly companyId = toSignal(inheritedRouteParam(this.route, 'companyId'), {
    initialValue: '',
  });

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly assetBusy = signal<BrandAssetType | null>(null);
  readonly branding = signal<CompanyBranding | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly noticeMessage = signal<string | null>(null);
  readonly fonts: { value: BrandFont; label: string }[] = [
    { value: 'SYSTEM', label: 'Sistema' },
    { value: 'SANS', label: 'Sans moderna' },
    { value: 'SERIF', label: 'Serif editorial' },
  ];
  readonly templates: { value: StorefrontTemplate; label: string }[] = [
    { value: 'CLASSIC', label: 'Classic' },
    { value: 'MODERN', label: 'Modern' },
    { value: 'MINIMAL', label: 'Minimal' },
  ];
  readonly form = this.formBuilder.nonNullable.group({
    primaryColor: ['#6D3CE7', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    secondaryColor: ['#2A1B4D', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    backgroundColor: ['#F7F5FB', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    textColor: ['#211A2D', [Validators.required, Validators.pattern(/^#[0-9A-Fa-f]{6}$/)]],
    font: ['SYSTEM' as BrandFont, Validators.required],
    heroTitle: ['', Validators.maxLength(160)],
    heroSubtitle: ['', Validators.maxLength(300)],
    template: ['CLASSIC' as StorefrontTemplate, Validators.required],
  });

  constructor() {
    effect((onCleanup) => {
      const id = this.companyId();
      if (!id) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar la empresa.');
        return;
      }
      const subscription = this.api.branding(id).subscribe({
        next: (branding) => {
          this.branding.set(branding);
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
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
          this.errorMessage.set('No pudimos cargar la apariencia del tenant.');
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }

  save(): void {
    this.form.markAllAsTouched();
    const id = this.companyId();
    if (!id || this.form.invalid || this.saving()) return;
    this.saving.set(true);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    const value = this.form.getRawValue();
    this.api
      .updateBranding(id, {
        ...value,
        heroTitle: value.heroTitle.trim() || null,
        heroSubtitle: value.heroSubtitle.trim() || null,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (branding) => {
          this.branding.set(branding);
          this.noticeMessage.set('La apariencia quedó actualizada.');
        },
        error: () => this.errorMessage.set('No pudimos guardar la apariencia.'),
      });
  }

  upload(type: BrandAssetType, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const id = this.companyId();
    if (!file || !id || this.assetBusy()) return;
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      this.errorMessage.set('Usá una imagen JPEG o PNG de hasta 5 MB.');
      return;
    }
    this.assetBusy.set(type);
    this.errorMessage.set(null);
    this.noticeMessage.set(null);
    this.api
      .uploadBrandingAsset(id, type, file)
      .pipe(finalize(() => this.assetBusy.set(null)))
      .subscribe({
        next: (branding) => {
          this.branding.set(branding);
          this.noticeMessage.set('La imagen quedó actualizada.');
        },
        error: () => this.errorMessage.set('No pudimos procesar la imagen.'),
      });
  }

  remove(type: BrandAssetType): void {
    const id = this.companyId();
    if (!id || this.assetBusy()) return;
    this.assetBusy.set(type);
    this.api
      .deleteBrandingAsset(id, type)
      .pipe(finalize(() => this.assetBusy.set(null)))
      .subscribe({
        next: (branding) => {
          this.branding.set(branding);
          this.noticeMessage.set('La imagen fue eliminada.');
        },
        error: () => this.errorMessage.set('No pudimos eliminar la imagen.'),
      });
  }

  fontStack(font: BrandFont): string {
    if (font === 'SERIF') return "Georgia, 'Times New Roman', serif";
    if (font === 'SANS') return "Inter, 'Segoe UI', Arial, sans-serif";
    return "system-ui, -apple-system, 'Segoe UI', sans-serif";
  }
}

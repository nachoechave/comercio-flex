import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { StoreSettingsApiService } from '../store-settings/store-settings-api.service';
import { StoreBrandingPage } from './store-branding-page';

describe('StoreBrandingPage', () => {
  it('shows the template gallery, previews without persisting and saves only the selected composition', () => {
    const branding = {
      primaryColor: '#315A46', secondaryColor: '#17352A', backgroundColor: '#F7F5EF',
      textColor: '#20241F', font: 'SYSTEM' as const, heroEyebrow: null,
      heroTitle: 'La feria en tu casa', heroSubtitle: null, template: 'CATALOG' as const,
      logoUrl: null, faviconUrl: null, heroImageUrl: null,
    };
    const updateBranding = vi.fn().mockImplementation((_slug, value) => of({ ...branding, ...value }));
    const api = {
      getBranding: vi.fn().mockReturnValue(of(branding)),
      updateBranding,
      uploadBrandingAsset: vi.fn(),
      deleteBrandingAsset: vi.fn(),
    };
    const params = of(convertToParamMap({ storeSlug: 'mercado-sur' }));

    TestBed.configureTestingModule({
      imports: [StoreBrandingPage],
      providers: [
        provideRouter([]),
        { provide: StoreSettingsApiService, useValue: api },
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: params }],
            snapshot: { paramMap: convertToParamMap({ storeSlug: 'mercado-sur' }), parent: null },
          },
        },
      ],
    });

    const fixture = TestBed.createComponent(StoreBrandingPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;

    expect(fixture.nativeElement.querySelectorAll('.template-card').length).toBe(11);
    expect(fixture.nativeElement.textContent).toContain('Fashion actual');
    expect(fixture.nativeElement.textContent).toContain('Fresh');
    expect(fixture.nativeElement.textContent).toContain('Catalog');
    expect(fixture.nativeElement.textContent).toContain('Coast');
    expect(fixture.nativeElement.textContent).toContain('Luxe');
    expect(fixture.nativeElement.textContent).toContain('Bold');

    page.openPreview('COAST');
    expect(page.previewTemplate()).toBe('COAST');
    expect(updateBranding).not.toHaveBeenCalled();

    page.usePreviewTemplate();
    expect(page.previewTemplate()).toBeNull();
    expect(page.form.controls.template.value).toBe('COAST');
    expect(updateBranding).not.toHaveBeenCalled();

    page.save();

    expect(updateBranding).toHaveBeenCalledTimes(1);
    expect(updateBranding).toHaveBeenCalledWith(
      'mercado-sur',
      expect.objectContaining({ template: 'COAST', heroTitle: 'La feria en tu casa' }),
    );
  });
});

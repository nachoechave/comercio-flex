import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { StoreSettingsApiService } from '../store-settings/store-settings-api.service';
import { StoreBrandingPage } from './store-branding-page';

describe('StoreBrandingPage', () => {
  it('shows the three templates and persists the selected composition', () => {
    const branding = {
      primaryColor: '#315A46', secondaryColor: '#17352A', backgroundColor: '#F7F5EF',
      textColor: '#20241F', font: 'SYSTEM' as const, heroTitle: 'La feria en tu casa',
      heroSubtitle: null, template: 'CATALOG' as const, logoUrl: null,
      faviconUrl: null, heroImageUrl: null,
    };
    const updateBranding = vi.fn().mockReturnValue(of({ ...branding, template: 'FRESH' }));
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

    expect(fixture.nativeElement.querySelectorAll('.template-card').length).toBe(3);
    expect(fixture.nativeElement.textContent).toContain('Editorial Moda');
    expect(fixture.nativeElement.textContent).toContain('Mercado Fresco');
    expect(fixture.nativeElement.textContent).toContain('Catálogo Versátil');

    page.selectTemplate('FRESH');
    page.save();

    expect(updateBranding).toHaveBeenCalledWith(
      'mercado-sur',
      expect.objectContaining({ template: 'FRESH', heroTitle: 'La feria en tu casa' }),
    );
  });
});

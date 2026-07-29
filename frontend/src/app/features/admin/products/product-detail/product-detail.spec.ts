import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { ProductDetail } from './product-detail';

const PUBLISHED_PRODUCT = {
  id: 'product-1',
  name: 'Remera',
  slug: 'remera',
  description: null,
  status: 'PUBLISHED' as const,
  category: { id: 'category-1', name: 'Remeras', active: true },
  variants: [],
  version: 7,
  createdAt: '',
  updatedAt: '',
};

describe('ProductDetail', () => {
  let fixture: ComponentFixture<ProductDetail>;
  let http: HttpTestingController;
  let role: 'OWNER' | 'STAFF';

  beforeEach(async () => {
    role = 'OWNER';
    await TestBed.configureTestingModule({
      imports: [ProductDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ productId: 'product-1' }),
              parent: {
                paramMap: convertToParamMap({ storeSlug: 'tienda-a' }),
                parent: null,
              },
            },
          },
        },
        {
          provide: AuthService,
          useValue: { membershipFor: () => ({ role }) },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function load(): void {
    fixture = TestBed.createComponent(ProductDetail);
    fixture.detectChanges();
    http
      .expectOne('/api/v1/stores/tienda-a/admin/products/product-1')
      .flush(PUBLISHED_PRODUCT);
    fixture.detectChanges();
  }

  it('lets an owner archive a published product using its version', () => {
    load();
    const archive = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Archivar',
    ) as HTMLButtonElement;
    archive.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeTruthy();

    const confirm = fixture.nativeElement.querySelector(
      '.confirmation .primary',
    ) as HTMLButtonElement;
    confirm.click();
    const request = http.expectOne(
      '/api/v1/stores/tienda-a/admin/products/product-1/status',
    );
    expect(request.request.body).toEqual({ status: 'ARCHIVED', version: 7 });
    request.flush({ ...PUBLISHED_PRODUCT, status: 'ARCHIVED', version: 8 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Archivado');
  });

  it('gives staff a read-only detail without publication actions', () => {
    role = 'STAFF';
    load();
    expect(fixture.nativeElement.textContent).toContain('Vista de lectura');
    expect(fixture.nativeElement.textContent).not.toContain('Despublicar');
    expect(fixture.nativeElement.textContent).not.toContain('Archivar');
  });
});

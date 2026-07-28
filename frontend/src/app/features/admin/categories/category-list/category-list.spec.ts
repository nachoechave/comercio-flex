import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  ParamMap,
  provideRouter,
} from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { AuthService } from '../../../../core/auth/auth.service';
import { Category } from '../category.models';
import { CategoryList } from './category-list';

const CATEGORY: Category = {
  id: 'category-1',
  name: 'Remeras',
  slug: 'remeras',
  active: true,
  createdAt: '2026-07-28T12:00:00Z',
  updatedAt: '2026-07-28T12:00:00Z',
};

describe('CategoryList', () => {
  let fixture: ComponentFixture<CategoryList>;
  let http: HttpTestingController;
  let role: 'OWNER' | 'ADMIN' | 'STAFF';
  let storeParams: BehaviorSubject<ParamMap>;

  function createComponent(): void {
    fixture = TestBed.createComponent(CategoryList);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    role = 'OWNER';
    storeParams = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
    const parent = {
      paramMap: convertToParamMap({ storeSlug: 'tienda-a' }),
      parent: null,
    };
    await TestBed.configureTestingModule({
      imports: [CategoryList],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: storeParams.asObservable() }],
            snapshot: {
              paramMap: convertToParamMap({}),
              queryParamMap: convertToParamMap({}),
              parent,
            },
          },
        },
        {
          provide: AuthService,
          useValue: {
            membershipFor: () => ({
              storeSlug: 'tienda-a',
              storeName: 'Tienda A',
              role,
            }),
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('renders categories and management actions for an owner', () => {
    createComponent();
    http.expectOne('/api/v1/stores/tienda-a/admin/categories').flush([CATEGORY]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Remeras');
    expect(fixture.nativeElement.querySelector('[aria-label="Editar Remeras"]')).toBeTruthy();
    expect(
      fixture.nativeElement.querySelector('[aria-label="Desactivar Remeras"]'),
    ).toBeTruthy();
  });

  it('gives staff read-only access without management actions', () => {
    role = 'STAFF';
    createComponent();
    http.expectOne('/api/v1/stores/tienda-a/admin/categories').flush([CATEGORY]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Tenés acceso de lectura');
    expect(fixture.nativeElement.querySelector('[aria-label="Editar Remeras"]')).toBeNull();
    expect(
      fixture.nativeElement.querySelector('[aria-label="Desactivar Remeras"]'),
    ).toBeNull();
  });

  it('confirms deactivation and updates only after the server responds', () => {
    createComponent();
    http.expectOne('/api/v1/stores/tienda-a/admin/categories').flush([CATEGORY]);
    fixture.detectChanges();

    (
      fixture.nativeElement.querySelector(
        '[aria-label="Desactivar Remeras"]',
      ) as HTMLButtonElement
    ).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alertdialog"]')).toBeTruthy();

    (
      fixture.nativeElement.querySelector('.confirmation .danger') as HTMLButtonElement
    ).click();
    const request = http.expectOne(
      '/api/v1/stores/tienda-a/admin/categories/category-1/status',
    );
    expect(request.request.body).toEqual({ active: false });
    request.flush({ ...CATEGORY, active: false });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'La categoría Remeras fue desactivada.',
    );
    expect(fixture.nativeElement.textContent).toContain('Inactiva');
  });

  it('cancels tenant state and requests the new store when Angular reuses the component', () => {
    createComponent();
    http.expectOne('/api/v1/stores/tienda-a/admin/categories').flush([CATEGORY]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Remeras');

    storeParams.next(convertToParamMap({ storeSlug: 'tienda-b' }));
    fixture.detectChanges();

    const storeB = http.expectOne('/api/v1/stores/tienda-b/admin/categories');
    expect(fixture.nativeElement.textContent).not.toContain('Remeras');
    storeB.flush([{ ...CATEGORY, id: 'category-b', name: 'Abrigos', slug: 'abrigos' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Abrigos');
    expect(fixture.componentInstance.storeSlug()).toBe('tienda-b');
  });
});

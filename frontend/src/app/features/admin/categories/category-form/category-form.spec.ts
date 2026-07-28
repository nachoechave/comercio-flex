import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { CategoryForm } from './category-form';

describe('CategoryForm', () => {
  let fixture: ComponentFixture<CategoryForm>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    const parent = {
      paramMap: convertToParamMap({ storeSlug: 'tienda-a' }),
      parent: null,
    };
    await TestBed.configureTestingModule({
      imports: [CategoryForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({}),
              parent,
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(CategoryForm);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows validation and does not submit a blank name', () => {
    fixture.componentInstance.form.controls.name.setValue('   ');
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ingresá un nombre.');
    expect(
      (fixture.nativeElement.querySelector('#category-name') as HTMLInputElement).getAttribute(
        'aria-invalid',
      ),
    ).toBe('true');
    http.expectNone('/api/v1/stores/tienda-a/admin/categories');
  });

  it('trims and creates a category before returning to the list', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.form.controls.name.setValue('  Remeras  ');

    fixture.componentInstance.submit();
    const request = http.expectOne('/api/v1/stores/tienda-a/admin/categories');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Remeras' });
    request.flush({
      id: 'category-1',
      name: 'Remeras',
      slug: 'remeras',
      active: true,
      createdAt: '2026-07-28T12:00:00Z',
      updatedAt: '2026-07-28T12:00:00Z',
    });

    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(
      ['/tiendas', 'tienda-a', 'admin', 'categorias'],
      { queryParams: { saved: 'created' } },
    );
  });

  it('keeps the form and shows a duplicate-name problem', () => {
    fixture.componentInstance.form.controls.name.setValue('Remeras');
    fixture.componentInstance.submit();
    http.expectOne('/api/v1/stores/tienda-a/admin/categories').flush(
      {
        title: 'Categoría duplicada',
        detail: 'Ya existe una categoría con ese nombre.',
        status: 409,
      },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain(
      'Ya existe una categoría con ese nombre.',
    );
    expect(fixture.componentInstance.form.controls.name.value).toBe('Remeras');
  });

  it('does not send the form twice while the first request is pending', () => {
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.form.controls.name.setValue('Remeras');

    fixture.componentInstance.submit();
    fixture.componentInstance.submit();

    const requests = http.match('/api/v1/stores/tienda-a/admin/categories');
    expect(requests).toHaveLength(1);
    requests[0].flush({
      id: 'category-1',
      name: 'Remeras',
      slug: 'remeras',
      active: true,
      createdAt: '2026-07-28T12:00:00Z',
      updatedAt: '2026-07-28T12:00:00Z',
    });
  });
});

describe('CategoryForm in edit mode', () => {
  let fixture: ComponentFixture<CategoryForm>;
  let http: HttpTestingController;
  let router: Router;
  let routeParams: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(async () => {
    const storeRoute = {
      paramMap: convertToParamMap({ storeSlug: 'tienda-a' }),
      parent: null,
    };
    routeParams = new BehaviorSubject(
      convertToParamMap({ storeSlug: 'tienda-a', categoryId: 'category-1' }),
    );
    await TestBed.configureTestingModule({
      imports: [CategoryForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            pathFromRoot: [{ paramMap: routeParams.asObservable() }],
            snapshot: {
              paramMap: convertToParamMap({ categoryId: 'category-1' }),
              parent: storeRoute,
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(CategoryForm);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads the category, keeps its slug read-only and renames it', async () => {
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const load = http.expectOne(
      '/api/v1/stores/tienda-a/admin/categories/category-1',
    );
    expect(load.request.method).toBe('GET');
    load.flush({
      id: 'category-1',
      name: 'Remeras',
      slug: 'remeras',
      active: true,
      createdAt: '2026-07-28T12:00:00Z',
      updatedAt: '2026-07-28T12:00:00Z',
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('remeras');
    expect(fixture.nativeElement.querySelector('input[formControlName="slug"]')).toBeNull();

    fixture.componentInstance.form.controls.name.setValue('Remeras y tops');
    fixture.componentInstance.submit();
    const update = http.expectOne(
      '/api/v1/stores/tienda-a/admin/categories/category-1',
    );
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ name: 'Remeras y tops' });
    update.flush({
      id: 'category-1',
      name: 'Remeras y tops',
      slug: 'remeras',
      active: true,
      createdAt: '2026-07-28T12:00:00Z',
      updatedAt: '2026-07-28T12:05:00Z',
    });

    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(
      ['/tiendas', 'tienda-a', 'admin', 'categorias'],
      { queryParams: { saved: 'updated' } },
    );
  });

  it('cancels the old load and reloads against the new store when reused', () => {
    const storeA = http.expectOne(
      '/api/v1/stores/tienda-a/admin/categories/category-1',
    );

    routeParams.next(
      convertToParamMap({ storeSlug: 'tienda-b', categoryId: 'category-1' }),
    );
    fixture.detectChanges();

    expect(storeA.cancelled).toBe(true);
    const storeB = http.expectOne(
      '/api/v1/stores/tienda-b/admin/categories/category-1',
    );
    storeB.flush({
      id: 'category-1',
      name: 'Abrigos',
      slug: 'abrigos',
      active: true,
      createdAt: '2026-07-28T12:00:00Z',
      updatedAt: '2026-07-28T12:00:00Z',
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.storeSlug()).toBe('tienda-b');
    expect(fixture.componentInstance.form.controls.name.value).toBe('Abrigos');
  });
});

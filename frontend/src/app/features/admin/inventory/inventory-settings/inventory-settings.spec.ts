import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { InventorySettings } from './inventory-settings';

describe('InventorySettings', () => {
  let fixture: ComponentFixture<InventorySettings>;
  let http: HttpTestingController;

  beforeEach(async () => {
    const params = new BehaviorSubject(convertToParamMap({ storeSlug: 'tienda-a' }));
    await TestBed.configureTestingModule({
      imports: [InventorySettings],
      providers: [
        provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { pathFromRoot: [{ paramMap: params.asObservable() }], snapshot: { paramMap: convertToParamMap({}) } } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InventorySettings);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('loads and updates the real low-stock threshold endpoint', () => {
    http.expectOne('/api/v1/stores/tienda-a/admin/dashboard').flush({ lowStockThreshold: '5.000' });
    fixture.detectChanges();
    expect(fixture.componentInstance.form.controls.threshold.value).toBe('5.000');

    fixture.componentInstance.form.controls.threshold.setValue('2.500');
    fixture.componentInstance.save();
    http.expectOne('/api/v1/auth/csrf').flush({ token: 'csrf' });
    const update = http.expectOne('/api/v1/stores/tienda-a/admin/dashboard/settings');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toEqual({ lowStockThreshold: '2.500' });
    update.flush({ lowStockThreshold: '2.500' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Actualizamos el umbral');
  });
});

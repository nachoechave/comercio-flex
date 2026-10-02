import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { StorefrontPromotionsService } from './storefront-promotions.service';

describe('Combo promotions', () => {
  let service: StorefrontPromotionsService;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(StorefrontPromotionsService);
    service.ensure('limits');
    TestBed.inject(HttpTestingController).expectOne('/api/v1/stores/limits/catalog/promotions').flush([{
      id: 'combo', scopeType: 'COMBO', productIds: ['remera'], secondProductIds: ['jean', 'mom'],
      bundleQuantity: 2, bundlePrice: 40000,
    }]);
  });
  const line = (productId: string, quantity = 1) => ({ productId, quantity, unitPrice: productId === 'remera' ? '15000' : '35000' });
  it('requires one unit from each group', () => {
    expect(service.calculateDiscount('limits', [line('jean', 2)])).toBe(0);
    expect(service.calculateDiscount('limits', [line('jean'), line('mom')])).toBe(0);
    expect(service.calculateDiscount('limits', [line('remera', 2)])).toBe(0);
    expect(service.calculateDiscount('limits', [line('remera'), line('jean')])).toBe(10000);
  });
  it('leaves extra units at regular price and repeats complete pairs', () => {
    expect(service.calculateDiscount('limits', [line('remera'), line('jean', 2)])).toBe(10000);
    expect(service.calculateDiscount('limits', [line('remera', 2), line('jean'), line('mom')])).toBe(20000);
  });
  it('shows the promotion on products from both groups', () => {
    expect(service.promotion('limits', 'remera')?.id).toBe('combo');
    expect(service.promotion('limits', 'mom')?.id).toBe('combo');
    expect(service.promotion('limits', 'other')).toBeNull();
  });
});

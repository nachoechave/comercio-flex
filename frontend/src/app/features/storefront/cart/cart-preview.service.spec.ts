import { TestBed } from '@angular/core/testing';

import { CartPreviewService } from './cart-preview.service';

describe('CartPreviewService', () => {
  let service: CartPreviewService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CartPreviewService);
  });

  it('opens and closes the preview for the normalized store slug', () => {
    service.open(' Tienda-A ');
    expect(service.storeSlug()).toBe('tienda-a');

    service.close();
    expect(service.storeSlug()).toBeNull();
  });
});

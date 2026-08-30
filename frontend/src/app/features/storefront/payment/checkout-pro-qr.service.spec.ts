import { TestBed } from '@angular/core/testing';

import { CheckoutProNavigationService } from './checkout-pro-navigation.service';
import { CheckoutProQrService } from './checkout-pro-qr.service';

describe('CheckoutProQrService', () => {
  const checkoutUrl = 'https://www.mercadopago.com.ar/checkout/v1/redirect?id=preference';
  const navigation = { trustedUrl: vi.fn() };

  beforeEach(() => {
    navigation.trustedUrl.mockReset();
    navigation.trustedUrl.mockReturnValue(checkoutUrl);
    TestBed.configureTestingModule({
      providers: [
        CheckoutProQrService,
        { provide: CheckoutProNavigationService, useValue: navigation },
      ],
    });
  });

  it('generates the QR locally from the validated checkout URL', async () => {
    const service = TestBed.inject(CheckoutProQrService);

    const result = await service.create(checkoutUrl);

    expect(navigation.trustedUrl).toHaveBeenCalledWith(checkoutUrl);
    expect(result).toMatch(/^data:image\/png;base64,/);
  });

  it('does not generate a QR when the destination is rejected', async () => {
    navigation.trustedUrl.mockImplementation(() => {
      throw new Error('Unexpected Checkout Pro destination.');
    });
    const service = TestBed.inject(CheckoutProQrService);

    await expect(service.create('https://example.test/checkout')).rejects.toThrow(
      'Unexpected Checkout Pro destination.',
    );
  });
});

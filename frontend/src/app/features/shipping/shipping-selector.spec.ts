import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ShippingSelector } from './shipping-selector';
import { ShippingQuote } from './shipping.models';

describe('ShippingSelector', () => {
  let fixture: ComponentFixture<ShippingSelector>, http: HttpTestingController;
  const pickup: ShippingQuote = {
    methodId: 'pickup',
    name: 'Retiro',
    description: null,
    type: 'PICKUP',
    shippingAmount: '0.00',
    freeShipping: false,
    listSubtotal: '5000.00',
    discountAmount: '0.00',
    subtotal: '5000.00',
    total: '5000.00',
    pickupAddress: 'Calle 123',
    instructions: 'De 9 a 18',
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ShippingSelector],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(ShippingSelector);
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('storeSlug', 'tienda-a');
    fixture.componentRef.setInput('items', [{ variantId: 'variant-1', quantity: '2' }]);
    fixture.componentRef.setInput('paymentMethod', 'MERCADO_PAGO');
    fixture.detectChanges();
  });
  afterEach(() => http.verify());
  function respond(q: ShippingQuote = pickup) {
    fixture.componentInstance.quote();
    const request = http.expectOne('/api/v1/stores/tienda-a/shipping/quote');
    expect(request.request.body.items).toEqual([{ variantId: 'variant-1', quantity: '2' }]);
    expect(request.request.body.shippingAmount).toBeUndefined();
    request.flush([q]);
    fixture.detectChanges();
    fixture.componentInstance.select(q);
    fixture.detectChanges();
  }
  it('allows pickup without an address and shows backend total', () => {
    const emitted = vi.fn();
    fixture.componentInstance.selection.subscribe(emitted);
    respond();
    expect(emitted).toHaveBeenLastCalledWith({ methodId: 'pickup', expectedTotal: '5000.00' });
    expect(fixture.nativeElement.textContent).toContain('Calle 123');
    expect(fixture.nativeElement.textContent).toContain('5.000,00');
  });
  it('requires an address for delivery', () => {
    fixture.componentInstance.setMode('SHIPPING');
    fixture.componentInstance.quote();
    fixture.detectChanges();
    http.expectNone('/api/v1/stores/tienda-a/shipping/quote');
    expect(fixture.nativeElement.textContent).toContain('Completá la dirección');
  });
  it('quotes locality and postal code and displays free shipping', () => {
    fixture.componentInstance.setMode('SHIPPING');
    fixture.componentInstance.address.setValue({
      street: 'Calle',
      number: '1',
      apartment: '',
      city: 'La Plata',
      province: 'Buenos Aires',
      postalCode: '1900',
    });
    fixture.componentInstance.quote();
    const req = http.expectOne('/api/v1/stores/tienda-a/shipping/quote');
    expect(req.request.body.city).toBe('La Plata');
    expect(req.request.body.postalCode).toBe('1900');
    req.flush([{ ...pickup, type: 'LOCATION_RATE', freeShipping: true }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Envío gratis');
  });
  it('invalidates the quote when locality or postal code changes', () => {
    respond();
    fixture.componentInstance.setMode('SHIPPING');
    fixture.detectChanges();
    for (const name of ['city', 'postalCode']) {
      const input = fixture.nativeElement.querySelector(
        '[data-address-field="' + name + '"]',
      ) as HTMLInputElement;
      input.value = 'nuevo';
      input.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(fixture.componentInstance.current()).toBeNull();
      expect(fixture.componentInstance.selected()).toBeNull();
    }
  });
  it('discards a response for an obsolete destination', () => {
    fixture.componentInstance.quote();
    const req = http.expectOne('/api/v1/stores/tienda-a/shipping/quote');
    fixture.componentInstance.invalidate();
    req.flush([pickup]);
    expect(fixture.componentInstance.options()).toEqual([]);
  });
  it('shows quote errors and supports retry', () => {
    fixture.componentInstance.quote();
    http
      .expectOne('/api/v1/stores/tienda-a/shipping/quote')
      .flush({}, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Reintentá');
    respond();
    expect(fixture.componentInstance.error()).toBe('');
  });
  it('invalidates a selection when the payment method changes', () => {
    respond();
    fixture.componentRef.setInput('paymentMethod', 'BANK_TRANSFER');
    fixture.detectChanges();
    expect(fixture.componentInstance.current()).toBeNull();
  });
  it.skipIf(navigator.userAgent.includes('jsdom'))(
    'fits a mobile viewport with delivery address and rates',
    async () => {
      const { page } = await import('vitest/browser');
      await page.viewport(390, 844);
      try {
        fixture.componentInstance.setMode('SHIPPING');
        fixture.componentInstance.address.setValue({
          street: 'Calle 123',
          number: '456',
          apartment: '',
          city: 'La Plata',
          province: 'Buenos Aires',
          postalCode: '1900',
        });
        respond({
          ...pickup,
          type: 'FIXED_RATE',
          name: 'Envío estándar',
          shippingAmount: '3000.00',
          total: '8000.00',
        });
        const panel = fixture.nativeElement.querySelector('fieldset') as HTMLElement;
        expect(panel.scrollWidth).toBeLessThanOrEqual(panel.clientWidth + 1);
        expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(window.innerWidth + 1);
      } finally {
        await page.viewport(1024, 768);
      }
    },
  );
});

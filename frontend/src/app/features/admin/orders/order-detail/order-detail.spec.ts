import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { OrderDetail } from './order-detail';

function pendingOrder() {
  return {
    id: 'order-1',
    number: 'ORD-000001',
    status: 'PENDING_CONFIRMATION',
    fulfillmentType: 'PICKUP',
    customerName: 'Cliente Demo',
    customerPhone: '1100000000',
    customerEmail: 'cliente@example.test',
    notes: 'Cortar fino',
    currencyCode: 'ARS',
    subtotal: '2500.00',
    reservationExpiresAt: '2026-07-30T19:00:00Z',
    createdAt: '2026-07-30T18:30:00Z',
    version: 0,
    items: [],
    history: [],
  };
}

describe('OrderDetail', () => {
  let fixture: ComponentFixture<OrderDetail>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OrderDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: convertToParamMap({ orderId: 'order-1' }),
              parent: {
                paramMap: convertToParamMap({ storeSlug: 'tienda-a' }),
                parent: null,
              },
            },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrderDetail);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows contact, items, history and allowed actions', () => {
    http.expectOne('/api/v1/stores/tienda-a/admin/orders/order-1').flush({
      id: 'order-1',
      number: 'ORD-000001',
      status: 'PENDING_CONFIRMATION',
      fulfillmentType: 'PICKUP',
      customerName: 'Cliente Demo',
      customerPhone: '1100000000',
      customerEmail: 'cliente@example.test',
      notes: 'Cortar fino',
      currencyCode: 'ARS',
      subtotal: '2500.00',
      reservationExpiresAt: '2026-07-30T19:00:00Z',
      createdAt: '2026-07-30T18:30:00Z',
      version: 0,
      items: [
        {
          productId: 'product-1',
          variantId: 'variant-1',
          productName: 'Asado',
          size: null,
          color: null,
          unitCode: 'UNIT',
          unitPrice: '2500.00',
          quantity: '1.000',
          lineTotal: '2500.00',
        },
      ],
      history: [
        {
          id: 'history-1',
          previousStatus: null,
          newStatus: 'PENDING_CONFIRMATION',
          note: null,
          actorId: null,
          actorDisplayName: 'Sistema',
          createdAt: '2026-07-30T18:30:00Z',
        },
      ],
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('ORD-000001');
    expect(fixture.nativeElement.textContent).toContain('cliente@example.test');
    expect(fixture.nativeElement.textContent).toContain('Asado');
    expect(fixture.nativeElement.textContent).toContain('Confirmar');
    expect(fixture.nativeElement.textContent).toContain('Rechazar');
  });

  it('reuses the idempotency key after an uncertain failure and updates on success', () => {
    http.expectOne('/api/v1/stores/tienda-a/admin/orders/order-1').flush(pendingOrder());
    fixture.detectChanges();
    fixture.componentInstance.form.controls.note.setValue('Stock revisado');

    fixture.componentInstance.transition('CONFIRMED');
    const first = http.expectOne('/api/v1/stores/tienda-a/admin/orders/order-1/transitions');
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    expect(first.request.body).toEqual({
      targetStatus: 'CONFIRMED',
      note: 'Stock revisado',
    });
    first.flush({}, { status: 500, statusText: 'Server error' });

    fixture.componentInstance.transition('CONFIRMED');
    const retry = http.expectOne('/api/v1/stores/tienda-a/admin/orders/order-1/transitions');
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush({
      ...pendingOrder(),
      status: 'CONFIRMED',
      version: 1,
    });
    fixture.detectChanges();

    expect(fixture.componentInstance.order()?.status).toBe('CONFIRMED');
    expect(fixture.componentInstance.form.controls.note.value).toBe('');
  });

  it('limits the transition note to 500 characters in the UI', () => {
    http.expectOne('/api/v1/stores/tienda-a/admin/orders/order-1').flush(pendingOrder());
    fixture.detectChanges();

    const textarea: HTMLTextAreaElement = fixture.nativeElement.querySelector('textarea');
    expect(textarea.maxLength).toBe(500);
  });
});

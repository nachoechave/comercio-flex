import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { RadioContext } from './radio-context';
import { MembershipCheckoutNavigation, MembershipPaymentApi } from './membership-payment-api.service';
import { MembershipPaymentPanel } from './membership-payment-panel';
import { MembershipPaymentHistory } from './membership-payment-history';

describe('RADIO membership payments', () => {
  const context = { slug: () => 'radio-a', link: () => ['/tiendas', 'radio-a'] };
  const pending = { available: true, status: 'NOT_STARTED', checkoutUrl: null, message: '' };

  function setup(api: Record<string, ReturnType<typeof vi.fn>>) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: RadioContext, useValue: context },
        { provide: MembershipPaymentApi, useValue: api },
        { provide: MembershipCheckoutNavigation, useValue: { navigate: vi.fn() } },
      ],
    });
  }

  it('shows the payment button only for an available pending period', () => {
    const api = { state: vi.fn(() => of(pending)), checkout: vi.fn() };
    setup(api);
    const fixture = TestBed.createComponent(MembershipPaymentPanel);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Pagar con Mercado Pago');
  });

  it('does not expose a payment button after accreditation', () => {
    const api = { state: vi.fn(() => of({ ...pending, status: 'APPROVED' })), checkout: vi.fn() };
    setup(api);
    const fixture = TestBed.createComponent(MembershipPaymentPanel);
    fixture.componentRef.setInput('paid', true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('PAGADO');
    expect(fixture.nativeElement.textContent).not.toContain('Pagar con Mercado Pago');
  });

  it('keeps the button busy and reports provider failures', () => {
    const api = { state: vi.fn(() => of(pending)), checkout: vi.fn(() => throwError(() => ({ error: { detail: 'Mercado Pago no disponible' } }))) };
    setup(api);
    const fixture = TestBed.createComponent(MembershipPaymentPanel);
    fixture.detectChanges();
    fixture.componentInstance.pay();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Mercado Pago no disponible');
  });

  it('renders provider status and diagnostics only in the admin history', () => {
    const api = {
      history: vi.fn(() => of([])),
      adminHistory: vi.fn(() => of({ payments: [{ publicId: 'p', periodPublicId: 'period', periodYear: 2026, periodMonth: 9, planName: 'PLUS', amount: 6000, currency: 'ARS', periodStatus: 'PENDING', providerStatus: 'REJECTED', paidAt: null, appliedAt: null, reviewReason: 'AMOUNT_MISMATCH', providerPaymentId: '123' }], attempts: [{ publicId: 'a', periodPublicId: 'period', status: 'FAILED', externalReference: 'ref', preferenceId: null, lastErrorCode: 'PREFERENCE_LOOKUP_FAILED' }] })),
    };
    setup(api);
    const fixture = TestBed.createComponent(MembershipPaymentHistory);
    fixture.componentRef.setInput('slug', 'radio-a');
    fixture.componentRef.setInput('membershipId', 'member-1');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('AMOUNT_MISMATCH');
    expect(fixture.nativeElement.textContent).toContain('PREFERENCE_LOOKUP_FAILED');
  });
});

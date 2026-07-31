import { PaymentAuthorizationNavigationService } from './payment-authorization-navigation.service';

describe('PaymentAuthorizationNavigationService', () => {
  const service = new PaymentAuthorizationNavigationService();

  it('rejects insecure authorization destinations', () => {
    expect(() => service.navigate('http://auth.mercadopago.com.ar/authorization')).toThrow();
  });

  it('rejects HTTPS destinations outside Mercado Pago authentication', () => {
    expect(() => service.navigate('https://example.test/authorization')).toThrow();
  });
});

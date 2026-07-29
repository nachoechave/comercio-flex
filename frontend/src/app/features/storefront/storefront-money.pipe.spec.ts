import { StorefrontMoneyPipe } from './storefront-money.pipe';

describe('StorefrontMoneyPipe', () => {
  const pipe = new StorefrontMoneyPipe();

  it('formats decimal strings without converting them to floating point', () => {
    expect(pipe.transform('1234567890123.40', 'ARS')).toBe('$\u00a01.234.567.890.123,40');
  });

  it('uses the ISO code when no local symbol is defined', () => {
    expect(pipe.transform('25.5', 'USD')).toBe('USD\u00a025,50');
  });
});

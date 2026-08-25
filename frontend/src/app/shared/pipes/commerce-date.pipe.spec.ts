import { CommerceDatePipe, formatCommerceDate } from './commerce-date.pipe';

describe('CommerceDatePipe', () => {
  it('formats dates as es-AR with day/month/year and 24-hour time', () => {
    const formatted = new CommerceDatePipe().transform(
      '2026-08-25T15:05:00-03:00',
    );

    expect(formatted).toBe('25/08/2026, 15:05');
    expect(formatted).not.toMatch(/AM|PM/i);
  });

  it('uses Argentina time independently of the environment timezone', () => {
    const fromUtc = formatCommerceDate('2026-08-25T18:05:00Z');
    const fromArgentinaOffset = formatCommerceDate('2026-08-25T15:05:00-03:00');

    expect(fromUtc).toBe('25/08/2026, 15:05');
    expect(fromArgentinaOffset).toBe(fromUtc);
  });

  it('supports date-only presentation and invalid values', () => {
    expect(formatCommerceDate('2026-08-25T15:05:00-03:00', 'date')).toBe('25/08/2026');
    expect(formatCommerceDate('not-a-date')).toBe('');
  });
});

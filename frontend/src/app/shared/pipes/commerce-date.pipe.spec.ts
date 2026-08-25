import { CommerceDatePipe, formatCommerceDate } from './commerce-date.pipe';

describe('CommerceDatePipe', () => {
  it('formats dates as es-AR with day/month/year and 24-hour time', () => {
    const formatted = new CommerceDatePipe().transform(
      '2026-08-25T15:05:00-03:00',
    );

    expect(formatted).toBe('25/08/2026, 15:05');
    expect(formatted).not.toMatch(/AM|PM/i);
  });

  it('supports date-only presentation and invalid values', () => {
    expect(formatCommerceDate('2026-08-25T15:05:00-03:00', 'date')).toBe('25/08/2026');
    expect(formatCommerceDate('not-a-date')).toBe('');
  });
});

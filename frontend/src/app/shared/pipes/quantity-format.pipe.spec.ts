import { QuantityFormatPipe } from './quantity-format.pipe';

describe('QuantityFormatPipe', () => {
  const pipe = new QuantityFormatPipe();

  it.each([
    [10, '10'],
    ['10', '10'],
    ['10.000', '10'],
    ['10.500', '10,5'],
    ['1.250', '1,25'],
    ['2.125', '2,125'],
    ['0.000', '0'],
    ['0.750', '0,75'],
    ['-3.000', '-3'],
  ])('formats %s as %s', (value, expected) => {
    expect(pipe.transform(value)).toBe(expected);
  });

  it('expands scientific notation emitted by number values', () => {
    expect(pipe.transform(1e-7)).toBe('0,0000001');
    expect(pipe.transform(1e21)).toBe('1000000000000000000000');
  });

  it('handles nullish and non-numeric values without displaying invalid output', () => {
    expect(pipe.transform(null)).toBe('');
    expect(pipe.transform(undefined)).toBe('');
    expect(pipe.transform('not-a-quantity')).toBe('');
    expect(pipe.transform(Number.NaN)).toBe('');
  });
});

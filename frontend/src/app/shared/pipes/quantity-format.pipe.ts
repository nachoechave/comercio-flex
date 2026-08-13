import { Pipe, PipeTransform } from '@angular/core';

const DECIMAL_VALUE = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))(?:[eE]([+-]?\d+))?$/;

export function formatQuantity(value: number | string | null | undefined): string {
  if (value === null || value === undefined) return '';
  if (typeof value === 'number' && !Number.isFinite(value)) return '';

  const rawValue = String(value).trim();
  const match = DECIMAL_VALUE.exec(rawValue);
  if (!match) return '';

  const sign = match[1];
  const integer = match[2] ?? '0';
  const fraction = match[3] ?? match[4] ?? '';
  const exponent = Number(match[5] ?? 0);
  if (!Number.isSafeInteger(exponent) || Math.abs(exponent) > 10_000) return '';

  const digits = integer + fraction;
  const decimalIndex = integer.length + exponent;
  let whole: string;
  let decimals: string;

  if (decimalIndex <= 0) {
    whole = '0';
    decimals = '0'.repeat(-decimalIndex) + digits;
  } else if (decimalIndex >= digits.length) {
    whole = digits + '0'.repeat(decimalIndex - digits.length);
    decimals = '';
  } else {
    whole = digits.slice(0, decimalIndex);
    decimals = digits.slice(decimalIndex);
  }

  whole = whole.replace(/^0+(?=\d)/, '');
  decimals = decimals.replace(/0+$/, '');
  const isZero = whole === '0' && decimals.length === 0;
  const normalizedSign = isZero ? '' : sign;

  return `${normalizedSign}${whole}${decimals ? `.${decimals}` : ''}`;
}

@Pipe({
  name: 'quantityFormat',
})
export class QuantityFormatPipe implements PipeTransform {
  transform(value: number | string | null | undefined): string {
    return formatQuantity(value);
  }
}

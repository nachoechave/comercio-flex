import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'storefrontMoney',
})
export class StorefrontMoneyPipe implements PipeTransform {
  transform(value: string | null, currencyCode: string): string {
    if (value === null || !/^\d+(?:\.\d{1,2})?$/.test(value)) return 'Precio no disponible';

    const [rawInteger, rawFraction = ''] = value.split('.');
    const integer = rawInteger.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    const fraction = rawFraction.padEnd(2, '0');
    const amount = `${integer},${fraction}`;
    const prefix = currencyCode === 'ARS' ? '$' : currencyCode;
    return `${prefix}\u00a0${amount}`;
  }
}

import { Pipe, PipeTransform } from '@angular/core';

export type CommerceDateStyle = 'date' | 'dateTime';

const COMMERCE_TIME_ZONE = 'America/Argentina/Buenos_Aires';

const DATE_OPTIONS: Intl.DateTimeFormatOptions = {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
};

const DATE_TIME_OPTIONS: Intl.DateTimeFormatOptions = {
  ...DATE_OPTIONS,
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
};

export function formatCommerceDate(
  value: string | number | Date | null | undefined,
  style: CommerceDateStyle = 'dateTime',
  timeZone: string = COMMERCE_TIME_ZONE,
): string {
  if (value === null || value === undefined || value === '') return '';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return new Intl.DateTimeFormat('es-AR', {
    ...(style === 'date' ? DATE_OPTIONS : DATE_TIME_OPTIONS),
    timeZone,
  }).format(date);
}

@Pipe({ name: 'commerceDate' })
export class CommerceDatePipe implements PipeTransform {
  transform(
    value: string | number | Date | null | undefined,
    style: CommerceDateStyle = 'dateTime',
  ): string {
    return formatCommerceDate(value, style);
  }
}

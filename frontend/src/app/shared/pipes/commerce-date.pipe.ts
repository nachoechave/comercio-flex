import { Pipe, PipeTransform } from '@angular/core';

export type CommerceDateStyle = 'date' | 'dateTime';

const DATE_OPTIONS: Intl.DateTimeFormatOptions = {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
};

const DATE_TIME_OPTIONS: Intl.DateTimeFormatOptions = {
  ...DATE_OPTIONS,
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
};

export function formatCommerceDate(
  value: string | number | Date | null | undefined,
  style: CommerceDateStyle = 'dateTime',
  timeZone?: string,
): string {
  if (value === null || value === undefined || value === '') return '';
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return new Intl.DateTimeFormat('es-AR', {
    ...(style === 'date' ? DATE_OPTIONS : DATE_TIME_OPTIONS),
    ...(timeZone ? { timeZone } : {}),
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

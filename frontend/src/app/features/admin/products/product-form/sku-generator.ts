import { VariantOptionValue } from '../../../../shared/variant-options';

const COLOR_CODES: Record<string, string> = {
  negro: 'NEG',
  negra: 'NEG',
  blanco: 'BLA',
  blanca: 'BLA',
  azul: 'AZU',
  'azul marino': 'AZM',
  celeste: 'CEL',
  rojo: 'ROJ',
  roja: 'ROJ',
  verde: 'VER',
  gris: 'GRI',
  beige: 'BEI',
  bordo: 'BOR',
  bordó: 'BOR',
  marron: 'MAR',
  marrón: 'MAR',
  camel: 'CAM',
  rosa: 'ROS',
  fucsia: 'FUC',
  violeta: 'VIO',
  lila: 'LIL',
  naranja: 'NAR',
  amarillo: 'AMA',
  amarilla: 'AMA',
};

function ascii(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^A-Za-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ');
}

function tokens(value: string): string[] {
  return ascii(value)
    .split(' ')
    .filter(Boolean)
    .map((token) => token.toUpperCase());
}

function shortCode(value: string, max = 3): string {
  const parts = tokens(value);
  if (!parts.length) return '';
  if (parts.length === 1) return parts[0].slice(0, max);
  return parts
    .slice(0, max)
    .map((part) => part[0])
    .join('');
}

export function productSkuCode(productName: string, imageAltText = ''): string {
  const parts = tokens(productName || imageAltText);
  if (!parts.length) return 'PRD';
  if (parts.length === 1) return parts[0].slice(0, 3);
  if (parts.length === 2) return (parts[0].slice(0, 2) + parts[1][0]).slice(0, 3);
  return parts.slice(0, 3).map((part) => part[0]).join('');
}

export function optionSkuCode(option: VariantOptionValue): string {
  const name = ascii(option.name).toLocaleLowerCase('es');
  const value = ascii(option.value);
  if (!value) return '';

  if (name === 'talle' || name === 'size') {
    return value.replace(/\s+/g, '').toUpperCase().slice(0, 8);
  }

  if (name === 'color') {
    const normalized = option.value
      .trim()
      .replace(/\s+/g, ' ')
      .toLocaleLowerCase('es');
    return COLOR_CODES[normalized] ?? shortCode(option.value, 3);
  }

  const parts = tokens(option.value);
  if (parts.length === 1 && parts[0].length <= 4) return parts[0];
  return shortCode(option.value, 3);
}

export function generateVariantSku(
  productName: string,
  options: readonly VariantOptionValue[],
  imageAltText = '',
): string {
  const parts = [
    productSkuCode(productName, imageAltText),
    ...options.map(optionSkuCode).filter(Boolean),
  ];
  return parts.join('-').slice(0, 64);
}

export function uniqueSku(candidate: string, used: Set<string>): string {
  let result = candidate.slice(0, 64);
  let suffix = 2;
  while (used.has(result.toUpperCase())) {
    const ending = `-${suffix++}`;
    result = candidate.slice(0, 64 - ending.length) + ending;
  }
  used.add(result.toUpperCase());
  return result;
}

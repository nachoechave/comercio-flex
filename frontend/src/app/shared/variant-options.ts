export interface VariantOptionValue {
  name: string;
  value: string;
}

export function variantOptionsLabel(
  options: readonly VariantOptionValue[] | null | undefined,
  size?: string | null,
  color?: string | null,
): string {
  const values = options?.length
    ? options
    : [
        ...(size ? [{ name: 'Talle', value: size }] : []),
        ...(color ? [{ name: 'Color', value: color }] : []),
      ];
  return values.map((option) => `${option.name}: ${option.value}`).join(' · ');
}

export function canonicalVariantOptions(
  options: readonly VariantOptionValue[] | null | undefined,
  size?: string | null,
  color?: string | null,
): string {
  const values = options?.length
    ? options
    : [
        ...(size ? [{ name: 'Talle', value: size }] : []),
        ...(color ? [{ name: 'Color', value: color }] : []),
      ];
  return values
    .map((option) => `${normalize(option.name)}=${normalize(option.value)}`)
    .sort()
    .join('\u001f');
}

function normalize(value: string): string {
  return value.trim().replace(/\s+/g, ' ').toLocaleLowerCase('es');
}

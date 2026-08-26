import { canonicalVariantOptions, VariantOptionValue } from '../../../../shared/variant-options';

export const MAX_PRODUCT_OPTIONS = 5;
export const MAX_PRODUCT_VARIANTS = 100;

export interface ProductOptionDefinition {
  name: string;
  values: readonly string[];
}

export interface VariantCombination {
  key: string;
  label: string;
  options: VariantOptionValue[];
}

export interface ProductOptionValidation {
  valid: boolean;
  error: string | null;
  variantCount: number;
}

export interface ReconstructedProductOptions {
  definitions: ProductOptionDefinition[];
  compatible: boolean;
}

export function validateProductOptions(
  definitions: readonly ProductOptionDefinition[],
): ProductOptionValidation {
  if (definitions.length > MAX_PRODUCT_OPTIONS) {
    return invalid(`Podés definir hasta ${MAX_PRODUCT_OPTIONS} tipos de opción.`);
  }
  if (!definitions.length) {
    return { valid: true, error: null, variantCount: 1 };
  }

  const names = new Set<string>();
  let variantCount = 1;
  for (const definition of definitions) {
    const name = cleanOptionText(definition.name);
    if (!name) return invalid('Cada opción necesita un nombre.');
    if (name.length > 40) return invalid('El nombre de una opción admite hasta 40 caracteres.');
    const normalizedName = normalizeOptionText(name);
    if (names.has(normalizedName)) {
      return invalid('No puede haber dos opciones con el mismo nombre.');
    }
    names.add(normalizedName);
    if (!definition.values.length) {
      return invalid(`Agregá al menos un valor para ${name}.`);
    }

    const values = new Set<string>();
    for (const rawValue of definition.values) {
      const value = cleanOptionText(rawValue);
      if (!value) return invalid(`Todos los valores de ${name} deben estar completos.`);
      if (value.length > 60) {
        return invalid(`Los valores de ${name} admiten hasta 60 caracteres.`);
      }
      const normalizedValue = normalizeOptionText(value);
      if (values.has(normalizedValue)) {
        return invalid(`${name} tiene valores repetidos.`);
      }
      values.add(normalizedValue);
    }

    variantCount *= definition.values.length;
    if (variantCount > MAX_PRODUCT_VARIANTS) {
      return invalid(`Las opciones generan más de ${MAX_PRODUCT_VARIANTS} variantes.`);
    }
  }
  return { valid: true, error: null, variantCount };
}

export function generateVariantCombinations(
  definitions: readonly ProductOptionDefinition[],
): VariantCombination[] {
  if (!validateProductOptions(definitions).valid) return [];
  if (!definitions.length) {
    return [{ key: '', label: 'Opción estándar', options: [] }];
  }

  const cleaned = definitions.map((definition) => ({
    name: cleanOptionText(definition.name),
    values: definition.values.map(cleanOptionText),
  }));
  const combinations: VariantCombination[] = [];
  const selected = new Array<string>(cleaned.length);

  const visit = (definitionIndex: number): void => {
    if (definitionIndex < 0) {
      const options = cleaned.map((definition, index) => ({
        name: definition.name,
        value: selected[index],
      }));
      combinations.push({
        key: variantCombinationKey(options),
        label: options.map((option) => option.value).join(' / '),
        options,
      });
      return;
    }
    for (const value of cleaned[definitionIndex].values) {
      selected[definitionIndex] = value;
      visit(definitionIndex - 1);
    }
  };

  visit(cleaned.length - 1);
  return combinations;
}

export function reconstructProductOptions(
  variants: readonly (readonly VariantOptionValue[])[],
): ReconstructedProductOptions {
  if (!variants.length || variants.every((options) => !options.length)) {
    return { definitions: [], compatible: true };
  }
  if (variants.some((options) => !options.length)) {
    return { definitions: [], compatible: false };
  }

  const firstNames = variants[0].map((option) => normalizeOptionText(option.name));
  const expectedNames = new Set(firstNames);
  if (expectedNames.size !== firstNames.length) {
    return { definitions: [], compatible: false };
  }
  for (const options of variants.slice(1)) {
    const names = new Set(options.map((option) => normalizeOptionText(option.name)));
    if (names.size !== expectedNames.size || [...expectedNames].some((name) => !names.has(name))) {
      return { definitions: [], compatible: false };
    }
  }

  const definitions = variants[0].map((firstOption) => {
    const normalizedName = normalizeOptionText(firstOption.name);
    const values = new Map<string, string>();
    for (const options of variants) {
      const option = options.find(
        (candidate) => normalizeOptionText(candidate.name) === normalizedName,
      );
      if (option) values.set(normalizeOptionText(option.value), cleanOptionText(option.value));
    }
    return {
      name: cleanOptionText(firstOption.name),
      values: [...values.values()],
    };
  });
  return { definitions, compatible: validateProductOptions(definitions).valid };
}

export function variantCombinationKey(options: readonly VariantOptionValue[]): string {
  return canonicalVariantOptions(
    options.map((option) => ({
      name: cleanOptionText(option.name),
      value: cleanOptionText(option.value),
    })),
  );
}

export function cleanOptionText(value: string): string {
  return value.trim().replace(/\s+/g, ' ');
}

function normalizeOptionText(value: string): string {
  return cleanOptionText(value).toLocaleLowerCase('es');
}

function invalid(error: string): ProductOptionValidation {
  return { valid: false, error, variantCount: 0 };
}

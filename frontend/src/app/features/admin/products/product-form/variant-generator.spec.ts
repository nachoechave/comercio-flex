import {
  generateVariantCombinations,
  reconstructProductOptions,
  validateProductOptions,
  variantCombinationKey,
} from './variant-generator';

describe('variant generator', () => {
  it('generates one independent variant for each size', () => {
    const combinations = generateVariantCombinations([{ name: 'Talle', values: ['S', 'M', 'L'] }]);

    expect(combinations.map((combination) => combination.label)).toEqual(['S', 'M', 'L']);
    expect(combinations.map((combination) => combination.options)).toEqual([
      [{ name: 'Talle', value: 'S' }],
      [{ name: 'Talle', value: 'M' }],
      [{ name: 'Talle', value: 'L' }],
    ]);
  });

  it('generates the product of sizes and colors without repeated option names', () => {
    const combinations = generateVariantCombinations([
      { name: 'Talle', values: ['S', 'M'] },
      { name: 'Color', values: ['Negro', 'Blanco'] },
    ]);

    expect(combinations.map((combination) => combination.label)).toEqual([
      'S / Negro',
      'M / Negro',
      'S / Blanco',
      'M / Blanco',
    ]);
    expect(combinations).toHaveLength(4);
    for (const combination of combinations) {
      expect(combination.options.map((option) => option.name)).toEqual(['Talle', 'Color']);
      expect(new Set(combination.options.map((option) => option.name)).size).toBe(2);
    }
  });

  it('uses a stable normalized key independent from option order and casing', () => {
    expect(
      variantCombinationKey([
        { name: 'Talle', value: ' M ' },
        { name: 'Color', value: 'Negro' },
      ]),
    ).toBe(
      variantCombinationKey([
        { name: 'color', value: ' negro ' },
        { name: 'TALLE', value: 'm' },
      ]),
    );
  });

  it('rejects duplicate names, duplicate values, blanks and more than 100 combinations', () => {
    expect(
      validateProductOptions([
        { name: 'Talle', values: ['S'] },
        { name: ' talle ', values: ['M'] },
      ]).error,
    ).toContain('mismo nombre');
    expect(validateProductOptions([{ name: 'Talle', values: ['S', ' s '] }]).error).toContain(
      'repetidos',
    );
    expect(validateProductOptions([{ name: '', values: ['S'] }]).valid).toBe(false);
    expect(validateProductOptions([{ name: 'Talle', values: [''] }]).valid).toBe(false);
    expect(
      validateProductOptions([
        { name: 'A', values: Array.from({ length: 11 }, (_, index) => String(index)) },
        { name: 'B', values: Array.from({ length: 10 }, (_, index) => String(index)) },
      ]).error,
    ).toContain('100');
  });

  it('keeps a simple product valid without options', () => {
    expect(generateVariantCombinations([])).toEqual([
      { key: '', label: 'Opción estándar', options: [] },
    ]);
  });

  it('reconstructs generic option definitions from existing variants', () => {
    const result = reconstructProductOptions([
      [
        { name: 'Talle', value: 'S' },
        { name: 'Color', value: 'Negro' },
      ],
      [
        { name: 'Talle', value: 'M' },
        { name: 'Color', value: 'Negro' },
      ],
    ]);

    expect(result).toEqual({
      compatible: true,
      definitions: [
        { name: 'Talle', values: ['S', 'M'] },
        { name: 'Color', values: ['Negro'] },
      ],
    });
  });

  it('detects heterogeneous legacy structures instead of crossing their data', () => {
    expect(
      reconstructProductOptions([
        [{ name: 'Talle', value: 'S' }],
        [{ name: 'Presentación', value: '100ml' }],
      ]).compatible,
    ).toBe(false);
  });
});

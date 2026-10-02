import { generateVariantSku, productSkuCode, uniqueSku } from './sku-generator';

describe('SKU generator', () => {
  it('builds a readable code from product name, size and color', () => {
    expect(
      generateVariantSku('Chomba Wafle', [
        { name: 'Talle', value: 'L' },
        { name: 'Color', value: 'Negro' },
      ]),
    ).toBe('CHW-L-NEG');
  });

  it('uses the image alt text only as fallback when the product name is empty', () => {
    expect(productSkuCode('', 'Chomba Wafle')).toBe('CHW');
    expect(productSkuCode('Jean Relaxed Fit', 'otra cosa')).toBe('JRF');
  });

  it('supports numeric sizes and multiword colors', () => {
    expect(
      generateVariantSku('Jean Relaxed Fit', [
        { name: 'Talle', value: '42' },
        { name: 'Color', value: 'Azul Marino' },
      ]),
    ).toBe('JRF-42-AZM');
  });

  it('adds a readable suffix when two generated SKUs collide', () => {
    const used = new Set<string>();
    expect(uniqueSku('CHW-L-NEG', used)).toBe('CHW-L-NEG');
    expect(uniqueSku('CHW-L-NEG', used)).toBe('CHW-L-NEG-2');
  });
});

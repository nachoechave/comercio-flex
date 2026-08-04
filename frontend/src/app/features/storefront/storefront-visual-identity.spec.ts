import { storefrontVisualIdentityFor } from './storefront-visual-identity';

describe('storefrontVisualIdentityFor', () => {
  it('assigns the clothing demo only to tienda-a', () => {
    const identity = storefrontVisualIdentityFor('tienda-a');

    expect(identity.key).toBe('apparel-editorial');
    expect(identity.collections).toHaveLength(3);
    expect(identity.heroImageUrl).toContain('/assets/demo/indumentaria/');
  });

  it('keeps a neutral fallback for every other tenant', () => {
    const identity = storefrontVisualIdentityFor('otra-tienda');

    expect(identity.key).toBe('default');
    expect(identity.collections).toEqual([]);
    expect(identity.heroImageUrl).toBeUndefined();
  });
});

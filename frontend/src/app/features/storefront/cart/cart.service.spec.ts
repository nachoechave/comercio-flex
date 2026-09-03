import { TestBed } from '@angular/core/testing';

import { PublicProductDetail, PublicProductVariant } from '../storefront.models';
import { CartService } from './cart.service';

describe('CartService', () => {
  let service: CartService;
  const product: PublicProductDetail = {
    image: {
      id: 'image-1',
      url: '/media/image-1/original',
      thumbnailUrl: '/media/image-1/thumbnail',
      altText: 'Remera azul de frente',
    },
    id: 'product-1',
    slug: 'remera',
    name: 'Remera',
    description: null,
    category: { id: 'category-1', name: 'Remeras', slug: 'remeras' },
    variants: [],
  };
  const variant: PublicProductVariant = {
    id: 'variant-1',
    price: '2500.00',
    size: 'M',
    color: 'Azul',
    options: [
      { name: 'Talle', value: 'M' },
      { name: 'Color', value: 'Azul' },
    ],
    available: true,
    availableQuantity: '10',
  };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(CartService);
  });

  afterEach(() => localStorage.clear());

  it('persists and accumulates an available variant without exceeding available stock', () => {
    expect(service.add('tienda-a', { product, variant, quantity: 2 })).toEqual({
      quantity: 2,
      reachedLimit: false,
      maxQuantity: 10,
    });

    expect(service.add('tienda-a', { product, variant, quantity: 98 })).toEqual({
      quantity: 10,
      reachedLimit: true,
      maxQuantity: 10,
    });

    expect(service.totalUnits('tienda-a')).toBe(10);
    expect(JSON.parse(localStorage.getItem('comercio-flex:cart:v1:tienda-a')!)).toEqual({
      version: 1,
      items: [
        expect.objectContaining({
          productSlug: 'remera',
          variantId: 'variant-1',
          quantity: 10,
          imageThumbnailUrl: '/media/image-1/thumbnail',
          imageAltText: 'Remera azul de frente',
          options: [
            { name: 'Talle', value: 'M' },
            { name: 'Color', value: 'Azul' },
          ],
        }),
      ],
    });
  });

  it('keeps carts isolated by store slug', () => {
    service.add('tienda-a', { product, variant, quantity: 2 });
    service.add('tienda-b', {
      product: { ...product, id: 'product-b' },
      variant: { ...variant, id: 'variant-b' },
      quantity: 1,
    });

    expect(service.totalUnits('tienda-a')).toBe(2);
    expect(service.totalUnits('tienda-b')).toBe(1);
  });

  it('rejects unavailable variants and invalid quantities', () => {
    expect(() =>
      service.add('tienda-a', {
        product,
        variant: { ...variant, available: false },
        quantity: 1,
      }),
    ).toThrowError('La variante no está disponible.');
    expect(() => service.add('tienda-a', { product, variant, quantity: 0 })).toThrowError(
      'La cantidad debe estar entre 1 y 99.',
    );
  });

  it('discards manipulated storage safely', () => {
    localStorage.setItem(
      'comercio-flex:cart:v1:tienda-a',
      JSON.stringify({
        version: 1,
        items: [{ productId: 'x', quantity: 500, unexpected: 'value' }],
      }),
    );

    service.activate('tienda-a');

    expect(service.items('tienda-a')).toEqual([]);
    expect(localStorage.getItem('comercio-flex:cart:v1:tienda-a')).toBeNull();
  });

  it('loads carts stored before product thumbnails were introduced', () => {
    localStorage.setItem(
      'comercio-flex:cart:v1:tienda-a',
      JSON.stringify({
        version: 1,
        items: [
          {
            productId: 'product-1',
            productSlug: 'remera',
            productName: 'Remera',
            variantId: 'variant-1',
            size: 'M',
            color: 'Azul',
            unitPrice: '2500.00',
            quantity: 1,
          },
        ],
      }),
    );

    service.activate('tienda-a');

    expect(service.items('tienda-a')[0]).toEqual(
      expect.objectContaining({
        imageThumbnailUrl: null,
        imageAltText: null,
        status: 'UNKNOWN',
      }),
    );
  });

  it('revalidates price and availability and excludes invalid lines from subtotal', () => {
    service.add('tienda-a', { product, variant, quantity: 2 });
    service.reconcileProduct('tienda-a', {
      ...product,
      name: 'Remera actualizada',
      variants: [{ ...variant, price: '2750.50', available: true }],
    });

    expect(service.items('tienda-a')[0]).toEqual(
      expect.objectContaining({
        productName: 'Remera actualizada',
        imageThumbnailUrl: '/media/image-1/thumbnail',
        imageAltText: 'Remera azul de frente',
        unitPrice: '2750.50',
        notice: 'Actualizamos los datos de este producto.',
      }),
    );
    expect(service.availableSubtotal('tienda-a')).toBe('5501.00');

    service.markProductUnavailable('tienda-a', 'remera');
    expect(service.availableSubtotal('tienda-a')).toBe('0.00');
  });

  it('updates quantities, removes lines and clears persisted state', () => {
    service.add('tienda-a', { product, variant, quantity: 1 });
    expect(service.setQuantity('tienda-a', 'variant-1', 4)).toBe(true);
    expect(service.setQuantity('tienda-a', 'variant-1', 100)).toBe(false);
    expect(service.totalUnits('tienda-a')).toBe(4);

    service.remove('tienda-a', 'variant-1');
    expect(service.items('tienda-a')).toEqual([]);

    service.add('tienda-a', { product, variant, quantity: 1 });
    service.clear('tienda-a');
    expect(localStorage.getItem('comercio-flex:cart:v1:tienda-a')).toBeNull();
  });
});

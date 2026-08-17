import { Injectable, signal } from '@angular/core';

import { PublicProductDetail } from '../storefront.models';
import { AddCartItem, AddCartResult, CartLine, StoredCart, StoredCartLine } from './cart.models';

const STORAGE_PREFIX = 'comercio-flex:cart:v1:';
const MAX_QUANTITY = 99;
const MONEY_PATTERN = /^\d{1,13}(?:\.\d{1,2})?$/;
const STORE_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly carts = signal<Record<string, CartLine[]>>({});
  private readonly loadedStores = new Set<string>();

  activate(storeSlug: string): void {
    const slug = normalizeStoreSlug(storeSlug);
    if (!slug || this.loadedStores.has(slug)) return;

    this.loadedStores.add(slug);
    this.carts.update((carts) => ({ ...carts, [slug]: this.read(slug) }));
  }

  items(storeSlug: string): readonly CartLine[] {
    return this.carts()[normalizeStoreSlug(storeSlug)] ?? [];
  }

  totalUnits(storeSlug: string): number {
    return this.items(storeSlug).reduce((total, line) => total + line.quantity, 0);
  }

  availableSubtotal(storeSlug: string): string {
    const totalCents = this.items(storeSlug)
      .filter((line) => line.status === 'AVAILABLE')
      .reduce((total, line) => total + parseCents(line.unitPrice) * BigInt(line.quantity), 0n);
    return formatCents(totalCents);
  }

  add(storeSlug: string, item: AddCartItem): AddCartResult {
    const slug = requireStore(storeSlug);
    this.activate(slug);
    if (!item.variant.available) throw new Error('La variante no está disponible.');
    if (!isQuantity(item.quantity)) throw new Error('La cantidad debe estar entre 1 y 99.');

    const current = this.items(slug);
    const existing = current.find((line) => line.variantId === item.variant.id);
    const requestedQuantity = (existing?.quantity ?? 0) + item.quantity;
    const quantity = Math.min(requestedQuantity, MAX_QUANTITY);
    const nextLine = toCartLine(item, quantity);
    const next = existing
      ? current.map((line) => (line.variantId === item.variant.id ? nextLine : line))
      : [...current, nextLine];
    this.replace(slug, next);
    return { quantity, reachedLimit: requestedQuantity > MAX_QUANTITY };
  }

  setQuantity(storeSlug: string, variantId: string, quantity: number): boolean {
    const slug = requireStore(storeSlug);
    this.activate(slug);
    if (!isQuantity(quantity)) return false;

    let found = false;
    const next = this.items(slug).map((line) => {
      if (line.variantId !== variantId) return line;
      found = true;
      return { ...line, quantity };
    });
    if (found) this.replace(slug, next);
    return found;
  }

  remove(storeSlug: string, variantId: string): void {
    const slug = requireStore(storeSlug);
    this.activate(slug);
    this.replace(
      slug,
      this.items(slug).filter((line) => line.variantId !== variantId),
    );
  }

  clear(storeSlug: string): void {
    const slug = requireStore(storeSlug);
    this.activate(slug);
    this.replace(slug, []);
  }

  reconcileProduct(storeSlug: string, product: PublicProductDetail): void {
    const slug = requireStore(storeSlug);
    this.activate(slug);
    const next = this.items(slug).map((line) => {
      if (line.productSlug !== product.slug) return line;
      const variant = product.variants.find((candidate) => candidate.id === line.variantId);
      if (!variant) {
        return {
          ...line,
          status: 'UNAVAILABLE' as const,
          notice: 'Esta opción ya no está publicada.',
        };
      }

      const changed =
        line.productName !== product.name ||
        line.imageThumbnailUrl !== (product.image?.thumbnailUrl ?? null) ||
        line.imageAltText !== (product.image?.altText ?? null) ||
        line.unitPrice !== normalizeMoney(variant.price) ||
        line.size !== variant.size ||
        line.color !== variant.color;
      return {
        ...line,
        productId: product.id,
        productName: product.name,
        imageThumbnailUrl: product.image?.thumbnailUrl ?? null,
        imageAltText: product.image?.altText ?? null,
        size: variant.size,
        color: variant.color,
        unitPrice: normalizeMoney(variant.price),
        status: variant.available ? ('AVAILABLE' as const) : ('UNAVAILABLE' as const),
        notice: !variant.available
          ? 'Esta opción está momentáneamente sin stock.'
          : changed
            ? 'Actualizamos los datos de este producto.'
            : null,
      };
    });
    this.replace(slug, next);
  }

  markProductUnavailable(storeSlug: string, productSlug: string): void {
    this.updateProductStatus(
      storeSlug,
      productSlug,
      'UNAVAILABLE',
      'Este producto ya no está publicado.',
    );
  }

  markProductUnknown(storeSlug: string, productSlug: string): void {
    this.updateProductStatus(
      storeSlug,
      productSlug,
      'UNKNOWN',
      'No pudimos confirmar precio y disponibilidad.',
    );
  }

  private updateProductStatus(
    storeSlug: string,
    productSlug: string,
    status: CartLine['status'],
    notice: string,
  ): void {
    const slug = requireStore(storeSlug);
    this.activate(slug);
    this.replace(
      slug,
      this.items(slug).map((line) =>
        line.productSlug === productSlug ? { ...line, status, notice } : line,
      ),
    );
  }

  private replace(storeSlug: string, items: readonly CartLine[]): void {
    const next = items.map((item) => ({ ...item }));
    this.carts.update((carts) => ({ ...carts, [storeSlug]: next }));
    this.write(storeSlug, next);
  }

  private read(storeSlug: string): CartLine[] {
    const storage = browserStorage();
    if (!storage) return [];

    try {
      const raw = storage.getItem(storageKey(storeSlug));
      if (!raw) return [];
      const parsed: unknown = JSON.parse(raw);
      if (!isStoredCart(parsed)) {
        storage.removeItem(storageKey(storeSlug));
        return [];
      }
      return parsed.items.map((item) => ({
        ...item,
        imageThumbnailUrl: item.imageThumbnailUrl ?? null,
        imageAltText: item.imageAltText ?? null,
        unitPrice: normalizeMoney(item.unitPrice),
        status: 'UNKNOWN',
        notice: 'Confirmando precio y disponibilidad.',
      }));
    } catch {
      try {
        storage.removeItem(storageKey(storeSlug));
      } catch {
        // Storage may be disabled; the in-memory cart remains usable.
      }
      return [];
    }
  }

  private write(storeSlug: string, items: readonly CartLine[]): void {
    const storage = browserStorage();
    if (!storage) return;

    const stored: StoredCart = {
      version: 1,
      items: items.map(({ status: _status, notice: _notice, ...item }) => item),
    };
    try {
      if (stored.items.length === 0) storage.removeItem(storageKey(storeSlug));
      else storage.setItem(storageKey(storeSlug), JSON.stringify(stored));
    } catch {
      // Quota or privacy mode must not make the in-memory cart unusable.
    }
  }
}

function toCartLine(item: AddCartItem, quantity: number): CartLine {
  return {
    productId: item.product.id,
    productSlug: item.product.slug,
    productName: item.product.name,
    imageThumbnailUrl: item.product.image?.thumbnailUrl ?? null,
    imageAltText: item.product.image?.altText ?? null,
    variantId: item.variant.id,
    size: item.variant.size,
    color: item.variant.color,
    unitPrice: normalizeMoney(item.variant.price),
    quantity,
    status: 'AVAILABLE',
    notice: null,
  };
}

function isStoredCart(value: unknown): value is StoredCart {
  if (!isRecord(value) || value['version'] !== 1 || !Array.isArray(value['items'])) return false;
  return value['items'].every(isStoredLine);
}

function isStoredLine(value: unknown): value is StoredCartLine {
  if (!isRecord(value)) return false;
  return (
    isText(value['productId'], 100) &&
    isText(value['productSlug'], 180) &&
    isText(value['productName'], 160) &&
    (value['imageThumbnailUrl'] === undefined ||
      isNullableText(value['imageThumbnailUrl'], 2048)) &&
    (value['imageAltText'] === undefined || isNullableText(value['imageAltText'], 300)) &&
    isText(value['variantId'], 100) &&
    isNullableText(value['size'], 60) &&
    isNullableText(value['color'], 60) &&
    typeof value['unitPrice'] === 'string' &&
    MONEY_PATTERN.test(value['unitPrice']) &&
    parseCents(value['unitPrice']) > 0n &&
    isQuantity(value['quantity']) &&
    Object.keys(value).every((key) =>
      [
        'productId',
        'productSlug',
        'productName',
        'imageThumbnailUrl',
        'imageAltText',
        'variantId',
        'size',
        'color',
        'unitPrice',
        'quantity',
      ].includes(key),
    )
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isText(value: unknown, maxLength: number): value is string {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= maxLength;
}

function isNullableText(value: unknown, maxLength: number): value is string | null {
  return value === null || (typeof value === 'string' && value.length <= maxLength);
}

function isQuantity(value: unknown): value is number {
  return Number.isInteger(value) && Number(value) >= 1 && Number(value) <= MAX_QUANTITY;
}

function normalizeStoreSlug(value: string): string {
  const normalized = value.trim().toLowerCase();
  return STORE_SLUG_PATTERN.test(normalized) ? normalized : '';
}

function requireStore(value: string): string {
  const slug = normalizeStoreSlug(value);
  if (!slug) throw new Error('No se pudo identificar el comercio.');
  return slug;
}

function normalizeMoney(value: string): string {
  if (!MONEY_PATTERN.test(value) || parseCents(value) <= 0n) {
    throw new Error('El precio público no es válido.');
  }
  return formatCents(parseCents(value));
}

function parseCents(value: string): bigint {
  const [integer, fraction = ''] = value.split('.');
  return BigInt(integer) * 100n + BigInt(fraction.padEnd(2, '0'));
}

function formatCents(value: bigint): string {
  return `${value / 100n}.${(value % 100n).toString().padStart(2, '0')}`;
}

function storageKey(storeSlug: string): string {
  return `${STORAGE_PREFIX}${storeSlug}`;
}

function browserStorage(): Storage | null {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

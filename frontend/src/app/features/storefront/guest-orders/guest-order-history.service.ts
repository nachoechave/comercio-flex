import { Injectable } from '@angular/core';

import { GuestOrder, GuestOrderStatus } from '../storefront.models';

const STORAGE_KEY = 'comercioflex:guest-orders:v1';
const MAX_ORDERS = 20;
const STORE_SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const ORDER_ID_PATTERN = /^[A-Za-z0-9-]{1,100}$/;
const LOOKUP_TOKEN_PATTERN = /^[A-Za-z0-9_-]{10,200}$/;

export interface GuestOrderHistoryEntry {
  storeSlug: string;
  orderId: string;
  orderNumber: string;
  lookupToken: string;
  createdAt: string;
  lastKnownStatus: GuestOrderStatus;
  total: string;
  currencyCode: string;
}

@Injectable({ providedIn: 'root' })
export class GuestOrderHistoryService {
  remember(storeSlug: string, order: GuestOrder, lookupToken: string): GuestOrderHistoryEntry {
    const entry: GuestOrderHistoryEntry = {
      storeSlug: storeSlug.trim().toLowerCase(),
      orderId: order.id,
      orderNumber: order.number,
      lookupToken,
      createdAt: order.createdAt,
      lastKnownStatus: order.status,
      total: order.subtotal,
      currencyCode: order.currencyCode,
    };
    if (!isEntry(entry)) throw new Error('Los datos de recuperación del pedido no son válidos.');
    const next = [entry, ...this.read().filter((item) =>
      item.storeSlug !== entry.storeSlug || item.orderId !== entry.orderId,
    )].slice(0, MAX_ORDERS);
    this.write(next);
    return entry;
  }

  list(storeSlug: string): GuestOrderHistoryEntry[] {
    const normalized = storeSlug.trim().toLowerCase();
    return this.read().filter((entry) => entry.storeSlug === normalized);
  }

  find(storeSlug: string, orderId: string): GuestOrderHistoryEntry | null {
    const normalized = storeSlug.trim().toLowerCase();
    return this.read().find((entry) =>
      entry.storeSlug === normalized && entry.orderId === orderId,
    ) ?? null;
  }

  update(storeSlug: string, order: GuestOrder): void {
    const current = this.find(storeSlug, order.id);
    if (!current) return;
    this.remember(storeSlug, order, current.lookupToken);
  }

  remove(storeSlug: string, orderId: string): void {
    const normalized = storeSlug.trim().toLowerCase();
    this.write(this.read().filter((entry) =>
      entry.storeSlug !== normalized || entry.orderId !== orderId,
    ));
  }

  private read(): GuestOrderHistoryEntry[] {
    const storage = browserStorage();
    if (!storage) return [];
    try {
      const raw = storage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const parsed: unknown = JSON.parse(raw);
      if (!Array.isArray(parsed)) throw new Error('invalid');
      const valid = parsed.filter(isEntry).slice(0, MAX_ORDERS);
      if (valid.length !== parsed.length) this.write(valid);
      return valid;
    } catch {
      try { storage.removeItem(STORAGE_KEY); } catch { /* Storage is optional. */ }
      return [];
    }
  }

  private write(entries: readonly GuestOrderHistoryEntry[]): void {
    const storage = browserStorage();
    if (!storage) return;
    try {
      if (entries.length === 0) storage.removeItem(STORAGE_KEY);
      else storage.setItem(STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_ORDERS)));
    } catch {
      // Privacy mode or quota errors must not block checkout.
    }
  }
}

function isEntry(value: unknown): value is GuestOrderHistoryEntry {
  if (!isRecord(value)) return false;
  return (
    typeof value['storeSlug'] === 'string' && STORE_SLUG_PATTERN.test(value['storeSlug']) &&
    typeof value['orderId'] === 'string' && ORDER_ID_PATTERN.test(value['orderId']) &&
    typeof value['orderNumber'] === 'string' && value['orderNumber'].length <= 40 &&
    typeof value['lookupToken'] === 'string' && LOOKUP_TOKEN_PATTERN.test(value['lookupToken']) &&
    typeof value['createdAt'] === 'string' && !Number.isNaN(Date.parse(value['createdAt'])) &&
    typeof value['lastKnownStatus'] === 'string' && isOrderStatus(value['lastKnownStatus']) &&
    typeof value['total'] === 'string' && /^\d{1,13}(?:\.\d{1,2})?$/.test(value['total']) &&
    typeof value['currencyCode'] === 'string' && /^[A-Z]{3}$/.test(value['currencyCode']) &&
    Object.keys(value).every((key) => [
      'storeSlug', 'orderId', 'orderNumber', 'lookupToken', 'createdAt',
      'lastKnownStatus', 'total', 'currencyCode',
    ].includes(key))
  );
}

function isOrderStatus(value: string): value is GuestOrderStatus {
  return [
    'PENDING_CONFIRMATION', 'CONFIRMED', 'READY_FOR_PICKUP', 'COMPLETED',
    'REJECTED', 'CANCELLED', 'EXPIRED',
  ].includes(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function browserStorage(): Storage | null {
  try { return globalThis.localStorage ?? null; } catch { return null; }
}

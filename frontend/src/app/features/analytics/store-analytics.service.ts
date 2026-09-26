import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Subscription, filter, throwError } from 'rxjs';

export type StoreAnalyticsEventType =
  | 'PAGE_VIEW'
  | 'PRODUCT_VIEW'
  | 'ADD_TO_CART'
  | 'BEGIN_CHECKOUT';

export interface AnalyticsProductSummary {
  productId: string;
  productName: string;
  views: number;
  addToCarts: number;
}

export interface AnalyticsTrafficSource {
  source: string;
  visits: number;
}

export interface StoreAnalyticsSummary {
  days: number;
  timezone: string;
  from: string;
  to: string;
  visits: number;
  visitors: number;
  pageViews: number;
  productViews: number;
  addToCarts: number;
  checkouts: number;
  purchases: number;
  conversionRate: number;
  topProducts: AnalyticsProductSummary[];
  trafficSources: AnalyticsTrafficSource[];
  generatedAt: string;
}

interface SessionState {
  id: string;
  lastSeen: number;
  source: string;
  medium: string | null;
}

const VISITOR_PREFIX = 'comercio-flex:analytics:visitor:v1:';
const SESSION_PREFIX = 'comercio-flex:analytics:session:v1:';
const SESSION_TIMEOUT_MS = 30 * 60 * 1000;
const memoryVisitors = new Map<string, string>();
const memorySessions = new Map<string, SessionState>();

@Injectable({ providedIn: 'root' })
export class StoreAnalyticsService {
  private readonly http = inject(HttpClient, { optional: true });
  private readonly router = inject(Router);
  private activeSlug = '';
  private navigationSubscription: Subscription | null = null;
  private lastPageViewPath = '';
  private lastPageViewAt = 0;

  activate(storeSlug: string): void {
    const slug = normalizeSlug(storeSlug);
    if (!slug || slug === this.activeSlug) return;

    this.deactivate();
    this.activeSlug = slug;
    this.trackPageView();
    this.navigationSubscription = this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe(() => this.trackPageView());
  }

  deactivate(storeSlug?: string): void {
    if (storeSlug && normalizeSlug(storeSlug) !== this.activeSlug) return;
    this.navigationSubscription?.unsubscribe();
    this.navigationSubscription = null;
    this.activeSlug = '';
    this.lastPageViewPath = '';
    this.lastPageViewAt = 0;
  }

  trackProductView(productId: string): void {
    this.track('PRODUCT_VIEW', productId);
  }

  trackAddToCart(productId: string): void {
    this.track('ADD_TO_CART', productId);
  }

  trackBeginCheckout(): void {
    this.track('BEGIN_CHECKOUT');
  }

  summary(storeSlug: string, days: 1 | 7 | 30) {
    if (!this.http) {
      return throwError(() => new Error('El transporte HTTP no está disponible.'));
    }
    return this.http.get<StoreAnalyticsSummary>(
      `/api/v1/stores/${encodeURIComponent(storeSlug)}/admin/analytics`,
      { params: { days } },
    );
  }

  private trackPageView(): void {
    const path = currentPath();
    const now = Date.now();
    if (path === this.lastPageViewPath && now - this.lastPageViewAt < 1000) return;
    this.lastPageViewPath = path;
    this.lastPageViewAt = now;
    this.track('PAGE_VIEW');
  }

  private track(eventType: StoreAnalyticsEventType, productId?: string): void {
    const slug = this.activeSlug;
    if (!slug || !this.http || trackingDisabled()) return;

    const anonymousVisitorId = visitorId(slug);
    const session = sessionState(slug);
    if (!anonymousVisitorId || !session) return;

    this.http
      .post<void>(`/api/v1/stores/${encodeURIComponent(slug)}/analytics/events`, {
        eventType,
        visitorId: anonymousVisitorId,
        sessionId: session.id,
        path: currentPath(),
        source: session.source,
        medium: session.medium,
        ...(productId ? { productId } : {}),
      })
      .subscribe({ error: () => undefined });
  }
}

function trackingDisabled(): boolean {
  if (typeof window === 'undefined') return true;
  const url = new URL(window.location.href);
  if (url.searchParams.get('preview') === '1') return true;
  return typeof navigator !== 'undefined' && navigator.doNotTrack === '1';
}

function currentPath(): string {
  if (typeof window === 'undefined') return '/';
  return window.location.pathname || '/';
}

function visitorId(storeSlug: string): string | null {
  const storage = safeStorage('local');
  const key = VISITOR_PREFIX + storeSlug;
  if (!storage) {
    const existing = memoryVisitors.get(storeSlug);
    if (existing) return existing;
    const created = ephemeralId();
    memoryVisitors.set(storeSlug, created);
    return created;
  }
  try {
    const existing = storage.getItem(key);
    if (existing && UUID_PATTERN.test(existing)) return existing;
    const created = ephemeralId();
    storage.setItem(key, created);
    return created;
  } catch {
    const existing = memoryVisitors.get(storeSlug);
    if (existing) return existing;
    const created = ephemeralId();
    memoryVisitors.set(storeSlug, created);
    return created;
  }
}

function sessionState(storeSlug: string): SessionState | null {
  const storage = safeStorage('session');
  const now = Date.now();
  if (!storage) return memorySession(storeSlug, now);

  const key = SESSION_PREFIX + storeSlug;
  try {
    const raw = storage.getItem(key);
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<SessionState>;
      if (
        typeof parsed.id === 'string' &&
        UUID_PATTERN.test(parsed.id) &&
        typeof parsed.lastSeen === 'number' &&
        now - parsed.lastSeen <= SESSION_TIMEOUT_MS &&
        typeof parsed.source === 'string'
      ) {
        const refreshed: SessionState = {
          id: parsed.id,
          lastSeen: now,
          source: cleanText(parsed.source, 120) || 'Directo',
          medium: typeof parsed.medium === 'string' ? cleanText(parsed.medium, 80) : null,
        };
        storage.setItem(key, JSON.stringify(refreshed));
        return refreshed;
      }
    }

    const created: SessionState = {
      id: ephemeralId(),
      lastSeen: now,
      ...captureAttribution(),
    };
    storage.setItem(key, JSON.stringify(created));
    return created;
  } catch {
    return memorySession(storeSlug, now);
  }
}

function memorySession(storeSlug: string, now: number): SessionState {
  const current = memorySessions.get(storeSlug);
  if (current && now - current.lastSeen <= SESSION_TIMEOUT_MS) {
    const refreshed = { ...current, lastSeen: now };
    memorySessions.set(storeSlug, refreshed);
    return refreshed;
  }
  const created: SessionState = {
    id: ephemeralId(),
    lastSeen: now,
    ...captureAttribution(),
  };
  memorySessions.set(storeSlug, created);
  return created;
}

function captureAttribution(): Pick<SessionState, 'source' | 'medium'> {
  if (typeof window === 'undefined') return { source: 'Directo', medium: null };

  const current = new URL(window.location.href);
  const campaignSource = cleanText(current.searchParams.get('utm_source'), 120);
  const campaignMedium = cleanText(current.searchParams.get('utm_medium'), 80);
  if (campaignSource) {
    return { source: campaignSource, medium: campaignMedium || 'campaign' };
  }

  if (!document.referrer) return { source: 'Directo', medium: null };
  try {
    const referrer = new URL(document.referrer);
    if (referrer.hostname === current.hostname) return { source: 'Directo', medium: null };
    return { source: classifyHost(referrer.hostname), medium: 'referral' };
  } catch {
    return { source: 'Directo', medium: null };
  }
}

function classifyHost(hostname: string): string {
  const host = hostname.toLowerCase().replace(/^www\./, '');
  if (host.includes('google.')) return 'Google';
  if (host.includes('instagram.com')) return 'Instagram';
  if (host.includes('facebook.com') || host.includes('fb.com')) return 'Facebook';
  if (host.includes('tiktok.com')) return 'TikTok';
  if (host.includes('twitter.com') || host === 'x.com') return 'X';
  if (host.includes('youtube.com') || host === 'youtu.be') return 'YouTube';
  if (host.includes('bing.com')) return 'Bing';
  if (host.includes('whatsapp.com') || host === 'wa.me') return 'WhatsApp';
  return cleanText(host, 120) || 'Referencia';
}

function cleanText(value: string | null | undefined, maxLength: number): string | null {
  if (!value) return null;
  const cleaned = value.replace(/[\u0000-\u001f\u007f]/g, '').trim();
  if (!cleaned) return null;
  return cleaned.slice(0, maxLength);
}

function safeStorage(kind: 'local' | 'session'): Storage | null {
  if (typeof window === 'undefined') return null;
  try {
    return kind === 'local' ? window.localStorage : window.sessionStorage;
  } catch {
    return null;
  }
}

function ephemeralId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') return globalThis.crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const random = Math.floor(Math.random() * 16);
    const value = character === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

function normalizeSlug(value: string): string {
  return value.trim().toLowerCase();
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

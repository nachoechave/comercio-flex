import { TestBed } from '@angular/core/testing';

import {
  FaviconService,
  PLATFORM_FAVICON_URL,
  TENANT_FAVICON_FALLBACK_URL,
} from './favicon.service';

describe('FaviconService', () => {
  let service: FaviconService;

  beforeEach(() => {
    document.head.querySelectorAll('link[rel="icon"]').forEach((link) => link.remove());
    TestBed.configureTestingModule({});
    service = TestBed.inject(FaviconService);
  });

  afterEach(() => {
    document.head.querySelectorAll('link[rel="icon"]').forEach((link) => link.remove());
  });

  it('uses the Comercio Flex asset for platform surfaces', () => {
    service.usePlatform();

    const link = document.head.querySelector<HTMLLinkElement>('link[rel="icon"]');
    expect(link?.getAttribute('href')).toBe(PLATFORM_FAVICON_URL);
    expect(link?.type).toBe('image/png');
    expect(link?.dataset['faviconOwner']).toBe('platform');
  });

  it('keeps tenant favicon branding isolated from Comercio Flex', () => {
    service.useTenant('/api/v1/stores/tienda-a/branding/favicon');

    const link = document.head.querySelector<HTMLLinkElement>('link[rel="icon"]');
    expect(link?.getAttribute('href')).toBe('/api/v1/stores/tienda-a/branding/favicon');
    expect(link?.dataset['faviconOwner']).toBe('tenant');
    expect(link?.getAttribute('href')).not.toBe(PLATFORM_FAVICON_URL);
  });

  it('uses the tenant logo before falling back to a neutral storefront icon', () => {
    service.useTenant(null, '/api/v1/stores/tienda-a/branding/logo');
    let link = document.head.querySelector<HTMLLinkElement>('link[rel="icon"]');
    expect(link?.getAttribute('href')).toBe('/api/v1/stores/tienda-a/branding/logo');

    service.useTenant(null, null);
    link = document.head.querySelector<HTMLLinkElement>('link[rel="icon"]');
    expect(link?.getAttribute('href')).toBe(TENANT_FAVICON_FALLBACK_URL);
    expect(link?.type).toBe('image/svg+xml');
    expect(link?.getAttribute('href')).not.toBe(PLATFORM_FAVICON_URL);
  });
});

import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';

export const PLATFORM_FAVICON_URL = '/assets/comercio-flex/favicon-32x32.png';
export const TENANT_FAVICON_FALLBACK_URL = '/assets/storefront/tenant-favicon-default.svg';

@Injectable({ providedIn: 'root' })
export class FaviconService {
  private readonly document = inject(DOCUMENT);

  usePlatform(): void {
    this.apply(PLATFORM_FAVICON_URL, 'image/png', 'platform');
  }

  useTenant(faviconUrl?: string | null, logoUrl?: string | null): void {
    const url = faviconUrl || logoUrl || TENANT_FAVICON_FALLBACK_URL;
    const type = url === TENANT_FAVICON_FALLBACK_URL ? 'image/svg+xml' : null;
    this.apply(url, type, 'tenant');
  }

  private apply(url: string, type: string | null, owner: 'platform' | 'tenant'): void {
    const faviconLinks = Array.from(
      this.document.head.querySelectorAll<HTMLLinkElement>('link[rel="icon"]'),
    );

    let link = faviconLinks[0];
    if (!link) {
      link = this.document.createElement('link');
      link.rel = 'icon';
      this.document.head.appendChild(link);
    }

    faviconLinks.slice(1).forEach((extraLink) => extraLink.remove());

    link.href = url;
    if (type) {
      link.type = type;
    } else {
      link.removeAttribute('type');
    }
    link.dataset['faviconOwner'] = owner;
  }
}

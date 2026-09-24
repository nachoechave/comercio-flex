import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';

interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

export function isAdminPwaPath(pathname: string): boolean {
  return (
    /^\/admin(?:\/|$)/.test(pathname) ||
    /^\/tiendas\/[^/]+\/admin(?:\/|$)/.test(pathname) ||
    /^\/superadmin(?:\/|$)/.test(pathname)
  );
}

@Injectable({ providedIn: 'root' })
export class AdminPwaService {
  private readonly document = inject(DOCUMENT);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private initialized = false;
  private deferredPrompt: BeforeInstallPromptEvent | null = null;
  private registration: ServiceWorkerRegistration | null = null;

  readonly canInstall = signal(false);
  readonly standalone = signal(false);

  initialize(): void {
    if (this.initialized || typeof window === 'undefined') return;
    this.initialized = true;

    this.syncStandaloneState();
    this.syncRoute(this.currentPath());

    window.addEventListener('beforeinstallprompt', (rawEvent) => {
      if (!isAdminPwaPath(window.location.pathname) || this.standalone()) return;
      const event = rawEvent as BeforeInstallPromptEvent;
      event.preventDefault();
      this.deferredPrompt = event;
      this.canInstall.set(true);
    });

    window.addEventListener('appinstalled', () => {
      this.deferredPrompt = null;
      this.canInstall.set(false);
      this.standalone.set(true);
    });

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((event) => this.syncRoute(this.pathFromUrl(event.urlAfterRedirects)));
  }

  async install(): Promise<boolean> {
    if (this.standalone()) return false;

    const prompt = this.deferredPrompt;
    if (prompt) {
      await prompt.prompt();
      const choice = await prompt.userChoice;
      this.deferredPrompt = null;
      this.canInstall.set(!this.standalone() && isAdminPwaPath(window.location.pathname));
      return choice.outcome === 'accepted';
    }

    this.showManualInstallInstructions();
    return false;
  }

  private syncRoute(pathname: string): void {
    const admin = isAdminPwaPath(pathname);
    if (admin) {
      this.ensureManifest();
      void this.registerServiceWorker();
      // Keep the install action discoverable even when Chromium does not expose
      // beforeinstallprompt. In that case install() explains the browser-native path.
      this.canInstall.set(!this.standalone());
    } else {
      this.removeManifest();
      this.canInstall.set(false);
    }
  }

  private ensureManifest(): void {
    let link = this.document.head.querySelector<HTMLLinkElement>('link[data-admin-pwa-manifest]');
    if (!link) {
      link = this.document.createElement('link');
      link.rel = 'manifest';
      link.href = '/admin.webmanifest';
      link.dataset['adminPwaManifest'] = 'true';
      this.document.head.appendChild(link);
    }

    let theme = this.document.head.querySelector<HTMLMetaElement>('meta[data-admin-pwa-theme]');
    if (!theme) {
      theme = this.document.createElement('meta');
      theme.name = 'theme-color';
      theme.content = '#0b4ddb';
      theme.dataset['adminPwaTheme'] = 'true';
      this.document.head.appendChild(theme);
    }
  }

  private removeManifest(): void {
    this.document.head.querySelector('link[data-admin-pwa-manifest]')?.remove();
    this.document.head.querySelector('meta[data-admin-pwa-theme]')?.remove();
  }

  private async registerServiceWorker(): Promise<void> {
    if (this.registration || !('serviceWorker' in navigator) || !window.isSecureContext) return;
    try {
      this.registration = await navigator.serviceWorker.register('/admin-sw.js', {
        scope: '/',
        updateViaCache: 'none',
      });
      void this.registration.update();
    } catch {
      // La PWA es una mejora progresiva: un fallo de registro no debe romper el Admin.
    }
  }

  private showManualInstallInstructions(): void {
    const userAgent = navigator.userAgent.toLowerCase();
    const ios = /iphone|ipad|ipod/.test(userAgent);
    const android = /android/.test(userAgent);

    if (ios) {
      window.alert(
        'Para instalar Comercio Flex Admin: tocá Compartir y elegí “Agregar a pantalla de inicio”.',
      );
      return;
    }

    if (android) {
      window.alert(
        'Para instalar Comercio Flex Admin: abrí el menú ⋮ del navegador y elegí “Instalar aplicación” o “Agregar a pantalla principal”.',
      );
      return;
    }

    window.alert(
      'Para instalar Comercio Flex Admin, usá la opción “Instalar aplicación” del menú de tu navegador.',
    );
  }

  private syncStandaloneState(): void {
    const navigatorWithStandalone = navigator as Navigator & { standalone?: boolean };
    const displayModeStandalone =
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(display-mode: standalone)').matches;
    this.standalone.set(displayModeStandalone || navigatorWithStandalone.standalone === true);
  }

  private currentPath(): string {
    return this.pathFromUrl(this.router.url || window.location.pathname);
  }

  private pathFromUrl(url: string): string {
    try {
      return new URL(url, window.location.origin).pathname;
    } catch {
      return window.location.pathname;
    }
  }
}

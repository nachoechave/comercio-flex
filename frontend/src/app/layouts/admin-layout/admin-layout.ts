import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, finalize, map } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { AdminIcon } from '../../shared/ui/admin-icon/admin-icon';

@Component({
  selector: 'app-admin-layout',
  imports: [AdminIcon, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-layout.html',
  styleUrl: './admin-layout.scss',
  host: { class: 'admin-shell' },
})
export class AdminLayout {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly storeSlug = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('storeSlug') ?? '')),
    { initialValue: this.route.snapshot.paramMap.get('storeSlug') ?? '' },
  );

  readonly membership = computed(() => this.auth.membershipFor(this.storeSlug()));
  readonly user = this.auth.user;
  readonly hasMultipleStores = computed(() => this.auth.memberships().length > 1);
  readonly loggingOut = signal(false);
  readonly logoutError = signal<string | null>(null);
  readonly mobileNavigationOpen = signal(false);
  readonly compactNavigation = signal(false);
  readonly currentUrl = signal(this.router.url);
  readonly ordersNavigationActive = computed(
    () => this.currentUrl().includes('/admin/pedidos') && !this.currentUrl().includes('/transferencias'),
  );
  readonly transfersNavigationActive = computed(() => this.currentUrl().includes('/transferencias'));

  constructor() {
    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((event) => {
        this.currentUrl.set(event.urlAfterRedirects);
        this.mobileNavigationOpen.set(false);
      });
  }

  toggleMobileNavigation(): void {
    this.mobileNavigationOpen.update((open) => !open);
  }

  toggleCompactNavigation(): void {
    this.compactNavigation.update((compact) => !compact);
  }

  closeMobileNavigation(): void {
    this.mobileNavigationOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  closeNavigationWithEscape(): void {
    this.closeMobileNavigation();
  }

  logout(): void {
    this.logoutError.set(null);
    this.loggingOut.set(true);
    this.auth
      .logout()
      .pipe(finalize(() => this.loggingOut.set(false)))
      .subscribe({
        next: () => void this.router.navigate(['/admin/login']),
        error: () =>
          this.logoutError.set(
            'No pudimos cerrar la sesión. Revisá tu conexión e intentá nuevamente.',
          ),
      });
  }
}

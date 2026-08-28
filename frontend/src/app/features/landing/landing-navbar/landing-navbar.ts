import { Component, ElementRef, HostListener, signal, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';

import { LANDING_CONFIG, LANDING_NAVIGATION } from '../landing.config';
import { LandingLogo } from '../landing-logo/landing-logo';

@Component({
  selector: 'app-landing-navbar',
  imports: [RouterLink, LandingLogo],
  templateUrl: './landing-navbar.html',
  styleUrl: './landing-navbar.scss',
})
export class LandingNavbar {
  protected readonly config = LANDING_CONFIG;
  protected readonly navigation = LANDING_NAVIGATION;
  protected readonly menuOpen = signal(false);

  @ViewChild('menuButton') private menuButton?: ElementRef<HTMLButtonElement>;
  @ViewChild('firstMobileLink') private firstMobileLink?: ElementRef<HTMLAnchorElement>;

  protected toggleMenu(): void {
    const shouldOpen = !this.menuOpen();
    this.menuOpen.set(shouldOpen);

    if (shouldOpen) {
      queueMicrotask(() => this.firstMobileLink?.nativeElement.focus());
    }
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  protected closeMenuWithKeyboard(): void {
    if (!this.menuOpen()) {
      return;
    }

    this.closeMenu();
    this.menuButton?.nativeElement.focus();
  }
}

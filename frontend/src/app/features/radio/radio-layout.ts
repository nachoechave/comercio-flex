import { Component, computed, effect, inject, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { NgIf } from '@angular/common';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { StorefrontContextService } from '../storefront/storefront-context.service';
import { RadioSite, RadioSiteApiService } from './radio-site-api.service';
import { RadioContext } from './radio-context';

@Component({
  selector: 'app-radio-layout',
  imports: [RouterLink, RouterOutlet, NgIf],
  providers: [RadioContext, StorefrontContextService],
  styleUrl: './radio-account.scss',
  template: `
    <div class="radio-shell" [style.font-family]="font()" [style.--radio-primary]="primary()" [style.--radio-secondary]="secondary()" [style.--radio-bg]="background()" [style.--radio-text]="text()">
      <header class="radio-header">
        <a class="radio-brand" [routerLink]="context.link()" [attr.aria-label]="settings()?.storeName || 'Radio'"><img *ngIf="settings()?.branding?.logoUrl as logo" [src]="logo" [alt]="settings()?.storeName || 'Logo'" /><span *ngIf="!settings()?.branding?.logoUrl">{{ settings()?.storeName?.slice(0, 1) || 'R' }}</span><strong>{{ settings()?.storeName || 'Radio' }}</strong></a>
        <button class="menu-toggle" type="button" (click)="menuOpen.update(v => !v)" [attr.aria-expanded]="menuOpen()">Menú</button>
        <nav [class.nav-open]="menuOpen()" aria-label="Navegación de la radio">
          <a [routerLink]="context.link()" (click)="menuOpen.set(false)">Inicio</a>
          <a [routerLink]="context.link('programas')" (click)="menuOpen.set(false)">Programas</a>
          <a [routerLink]="context.link('nosotros')" (click)="menuOpen.set(false)">Nosotros</a>
          <a [routerLink]="context.link('socios')" (click)="menuOpen.set(false)">Socios</a>
          <a *ngIf="site()?.settings?.youtubeUrl as youtube" class="youtube-link" [href]="youtube" target="_blank" rel="noopener">Ver en YouTube</a>
        @if (auth.isAuthenticated()) {
          <a [routerLink]="context.link('mi-cuenta')" (click)="menuOpen.set(false)">Mi cuenta</a>
          <button type="button" (click)="logout()" [disabled]="busy()">Cerrar sesión</button>
        } @else {
          <a [routerLink]="context.link('ingresar')" (click)="menuOpen.set(false)">Iniciar sesión</a>
        }
        </nav>
      </header>
      @if (error()) { <p role="alert">{{ error() }}</p> }
      <main><router-outlet /></main>
      <footer class="radio-footer"><div><strong>{{ settings()?.storeName || 'Radio' }}</strong><p>{{ site()?.settings?.description || 'Una comunidad que comparte pasión, historias y voces.' }}</p></div><div class="socials"><a *ngIf="site()?.settings?.youtubeUrl as youtube" [href]="youtube" target="_blank" rel="noopener">YouTube</a><a *ngIf="site()?.settings?.instagramUrl as instagram" [href]="instagram" target="_blank" rel="noopener">Instagram</a><a *ngIf="site()?.settings?.xUrl as x" [href]="x" target="_blank" rel="noopener">X</a><a *ngIf="site()?.settings?.whatsappUrl as whatsapp" [href]="whatsapp" target="_blank" rel="noopener">WhatsApp</a></div><small>© {{ year }} {{ settings()?.storeName || 'Radio' }}</small></footer>
    </div>`,
})
export class RadioLayout {
  private readonly document = inject(DOCUMENT);
  private readonly brandingEffect = effect(() => {
    const branding = this.settings()?.branding;
    let icon = this.document.head.querySelector<HTMLLinkElement>('link[rel="icon"]');
    if (!icon) { icon = this.document.createElement('link'); icon.rel = 'icon'; this.document.head.appendChild(icon); }
    icon.href = branding?.faviconUrl || '/favicon.ico';
    icon.removeAttribute('type');
  });
  readonly context = inject(RadioContext);
  readonly auth = inject(AuthService);
  private readonly siteApi = inject(RadioSiteApiService);
  private readonly storeContext = inject(StorefrontContextService, { optional: true });
  private readonly router = inject(Router);
  readonly site = signal<RadioSite | null>(null);
  readonly menuOpen = signal(false);
  readonly year = new Date().getFullYear();
  readonly settings = this.storeContext?.settings ?? signal(null);
  readonly font = computed(() => this.settings()?.branding?.font === 'SERIF' ? "Georgia, 'Times New Roman', serif" : this.settings()?.branding?.font === 'SANS' ? "Inter, 'Segoe UI', Arial, sans-serif" : "system-ui, sans-serif");
  readonly primary = computed(() => this.settings()?.branding?.primaryColor || '#173B67');
  readonly secondary = computed(() => this.settings()?.branding?.secondaryColor || '#071A2D');
  readonly background = computed(() => this.settings()?.branding?.backgroundColor || '#F5F7FA');
  readonly text = computed(() => this.settings()?.branding?.textColor || '#102033');
  readonly busy = signal(false);
  readonly error = signal('');
  constructor() { const slug = this.context.slug(); if (slug) { this.storeContext?.load(slug); this.siteApi.get(slug).subscribe({ next: value => this.site.set(value), error: () => this.error.set('No pudimos cargar la configuración de la radio.') }); } this.auth.loadSession().subscribe(); }
  logout() {
    this.busy.set(true); this.error.set('');
    this.auth.logout().pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => void this.router.navigate(this.context.link()),
      error: () => this.error.set('No pudimos cerrar la sesión. Intentá nuevamente.'),
    });
  }
}

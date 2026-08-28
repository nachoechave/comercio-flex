import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { LANDING_CONFIG } from '../landing.config';
import { LandingPage } from './landing-page';

describe('LandingPage', () => {
  let fixture: ComponentFixture<LandingPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LandingPage],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(LandingPage);
    fixture.detectChanges();
  });

  afterEach(() => {
    if (!fixture.componentRef.hostView.destroyed) {
      fixture.destroy();
    }
  });

  it('renders the commercial landing with its primary navigation and internal links', () => {
    const element = fixture.nativeElement as HTMLElement;
    const navLabels = Array.from(element.querySelectorAll('.desktop-nav a')).map((link) =>
      link.textContent?.trim(),
    );

    expect(element.querySelector('h1')?.textContent).toContain('más simple');
    expect(navLabels).toEqual([
      'Inicio',
      'Solución',
      'Beneficios',
      'Cómo funciona',
      'Rubros',
      'Demo',
    ]);
    expect(
      Array.from(element.querySelectorAll('.desktop-nav a')).every((link) =>
        link.getAttribute('href')?.startsWith('#'),
      ),
    ).toBe(true);
  });

  it('uses the real login and published demo storefront routes', () => {
    const element = fixture.nativeElement as HTMLElement;
    const links = Array.from(element.querySelectorAll('a'));
    const login = links.find((link) => link.textContent?.trim() === 'Iniciar sesión');
    const demo = links.find((link) => link.textContent?.trim() === 'Ver tienda demo');

    expect(login?.getAttribute('href')).toBe('/admin/login');
    expect(LANDING_CONFIG.demoStorePath).toBe('/tiendas/tiendademo');
    expect(demo?.getAttribute('href')).toBe('/tiendas/tiendademo');
  });

  it('does not expose a demo-request CTA until a verified contact channel is configured', () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(LANDING_CONFIG.contactHref).toBeNull();
    expect(element.querySelector('a[href^="mailto:"]')).toBeNull();
    expect(element.querySelector('a[href*="wa.me"]')).toBeNull();
    expect(element.textContent).not.toContain('Solicitar demo');
  });

  it('keeps pricing and testimonials hidden and renders no unsupported claims', () => {
    const element = fixture.nativeElement as HTMLElement;
    const text = element.textContent ?? '';

    expect(element.querySelector('[data-section="pricing"]')).toBeNull();
    expect(element.querySelector('[data-section="testimonials"]')).toBeNull();
    expect(LANDING_CONFIG.showPricing).toBe(false);
    expect(LANDING_CONFIG.showTestimonials).toBe(false);
    expect(element.querySelector('.desktop-nav')?.textContent).not.toMatch(/precios|testimonios/i);
    expect(text).not.toMatch(
      /sin tarjeta|clientes satisfechos|conversion rate|aumentá ventas \d+%/i,
    );
    expect(text).not.toMatch(/19\.990|39\.990|79\.990/);
  });

  it('points every navbar anchor to a rendered section', () => {
    const element = fixture.nativeElement as HTMLElement;
    const anchors = Array.from(element.querySelectorAll<HTMLAnchorElement>('.desktop-nav a'));

    expect(anchors.length).toBeGreaterThan(0);
    for (const anchor of anchors) {
      expect(element.querySelector(anchor.hash)).not.toBeNull();
    }
  });

  it('renders accessible native FAQ accordions', () => {
    const element = fixture.nativeElement as HTMLElement;
    const questions = element.querySelectorAll('details');

    expect(questions).toHaveLength(7);
    expect(questions[0].querySelector('summary')?.textContent).toContain(
      '¿Necesito conocimientos técnicos?',
    );
  });

  it('opens the mobile navigation, closes it after navigation and restores focus on Escape', () => {
    const element = fixture.nativeElement as HTMLElement;
    const menuButton = element.querySelector('.menu-button') as HTMLButtonElement;

    menuButton.click();
    fixture.detectChanges();
    expect(menuButton.getAttribute('aria-expanded')).toBe('true');
    expect(element.querySelector('#landing-mobile-menu')).not.toBeNull();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(element.querySelector('#landing-mobile-menu')).toBeNull();
    expect(document.activeElement).toBe(menuButton);

    menuButton.click();
    fixture.detectChanges();
    (element.querySelector('#landing-mobile-menu a') as HTMLAnchorElement).click();
    fixture.detectChanges();
    expect(element.querySelector('#landing-mobile-menu')).toBeNull();
  });

  it('sets the landing SEO title, description, OpenGraph and canonical URL', () => {
    const title = TestBed.inject(Title);
    const meta = TestBed.inject(Meta);

    expect(title.getTitle()).toBe('Comercio Flex | Ecommerce y gestión para comercios');
    expect(meta.getTag("name='description'")?.content).toContain('productos, inventario, pedidos');
    expect(meta.getTag("property='og:type'")?.content).toBe('website');
    expect(meta.getTag("property='og:url'")?.content).toBe('https://comercioflex.com.ar/');
    expect(document.head.querySelector<HTMLLinkElement>("link[rel='canonical']")?.href).toBe(
      'https://comercioflex.com.ar/',
    );
  });

  it('removes landing-only OpenGraph and canonical metadata when leaving the route', () => {
    const meta = TestBed.inject(Meta);

    fixture.destroy();

    expect(meta.getTag("property='og:title'")).toBeNull();
    expect(meta.getTag("property='og:description'")).toBeNull();
    expect(meta.getTag("property='og:type'")).toBeNull();
    expect(meta.getTag("property='og:url'")).toBeNull();
    expect(document.head.querySelector("link[rel='canonical']")).toBeNull();
  });
});

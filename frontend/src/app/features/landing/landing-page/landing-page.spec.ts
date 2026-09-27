import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';

import { LandingPage } from './landing-page';

describe('LandingPage', () => {
  let fixture: ComponentFixture<LandingPage>;
  let httpTesting: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LandingPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(LandingPage);
    httpTesting = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTesting.verify();
    if (!fixture.componentRef.hostView.destroyed) {
      fixture.destroy();
    }
  });

  it('renders the redesigned commercial landing and internal navigation', () => {
    const element = fixture.nativeElement as HTMLElement;
    const navLabels = Array.from(element.querySelectorAll('.desktop-nav a')).map((link) =>
      link.textContent?.trim(),
    );

    expect(element.querySelector('h1')?.textContent).toContain('en un solo lugar');
    expect(navLabels).toEqual([
      'Inicio',
      'Funciones',
      'Plantillas',
      'Cómo funciona',
      'Contacto',
    ]);

    for (const anchor of Array.from(element.querySelectorAll<HTMLAnchorElement>('.desktop-nav a'))) {
      expect(element.querySelector(anchor.hash)).not.toBeNull();
    }
  });

  it('publishes the verified direct contact channels', () => {
    const element = fixture.nativeElement as HTMLElement;
    const email = element.querySelector<HTMLAnchorElement>('a[href^="mailto:"]');
    const whatsapp = element.querySelector<HTMLAnchorElement>('a[href*="wa.me"]');

    expect(email?.getAttribute('href')).toBe('mailto:nacho9847@gmail.com');
    expect(whatsapp?.getAttribute('href')).toContain('5492213533259');
    expect(element.textContent).toContain('221 353-3259');
    expect(element.textContent).toContain('Solicitar demo');
  });

  it('keeps pricing and testimonials out of the landing', () => {
    const element = fixture.nativeElement as HTMLElement;
    const text = element.textContent ?? '';

    expect(element.querySelector('[data-section="pricing"]')).toBeNull();
    expect(element.querySelector('[data-section="testimonials"]')).toBeNull();
    expect(text).not.toMatch(/clientes satisfechos|conversion rate|aumentá ventas \d+%/i);
  });

  it('opens and closes the mobile navigation', () => {
    const element = fixture.nativeElement as HTMLElement;
    const menuButton = element.querySelector('.menu-button') as HTMLButtonElement;

    menuButton.click();
    fixture.detectChanges();
    expect(menuButton.getAttribute('aria-expanded')).toBe('true');
    expect(element.querySelector('#landing-mobile-menu')).not.toBeNull();

    (element.querySelector('#landing-mobile-menu a') as HTMLAnchorElement).click();
    fixture.detectChanges();
    expect(element.querySelector('#landing-mobile-menu')).toBeNull();
  });

  it('submits the contact form to the public endpoint', () => {
    const element = fixture.nativeElement as HTMLElement;
    setInput(element, 'name', 'Ignacio');
    setInput(element, 'business', 'Comercio de prueba');
    setInput(element, 'email', 'cliente@example.com');
    setInput(element, 'whatsapp', '2215555555');
    setTextarea(element, 'message', 'Quiero conocer Comercio Flex.');
    fixture.detectChanges();

    (element.querySelector('.contact-form') as HTMLFormElement).dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    );

    const request = httpTesting.expectOne('/api/v1/public/contact');
    expect(request.request.method).toBe('POST');
    const body = request.request.body as Record<string, unknown>;
    expect(body['name']).toBe('Ignacio');
    expect(body['business']).toBe('Comercio de prueba');
    expect(body['email']).toBe('cliente@example.com');
    expect(body['whatsapp']).toBe('2215555555');
    expect(body['message']).toBe('Quiero conocer Comercio Flex.');
    expect(body['website']).toBe('');
    expect(body['startedAt'] as number).toBeGreaterThan(0);
    request.flush(null);
    fixture.detectChanges();

    expect(element.textContent).toContain('Consulta enviada');
  });

  it('sets complete landing SEO, social and canonical metadata', () => {
    const title = TestBed.inject(Title);
    const meta = TestBed.inject(Meta);

    expect(title.getTitle()).toBe('Comercio Flex | Tienda online y gestión para comercios');
    expect(meta.getTag("name='description'")?.content).toContain(
      'productos, stock, pedidos, pagos y envíos',
    );
    expect(meta.getTag("name='robots'")?.content).toContain('index, follow');
    expect(meta.getTag("property='og:site_name'")?.content).toBe('Comercio Flex');
    expect(meta.getTag("property='og:locale'")?.content).toBe('es_AR');
    expect(meta.getTag("property='og:type'")?.content).toBe('website');
    expect(meta.getTag("property='og:url'")?.content).toBe('https://comercioflex.com.ar/');
    expect(meta.getTag("property='og:image'")?.content).toBe(
      'https://comercioflex.com.ar/assets/comercio-flex/icon-512x512.png',
    );
    expect(document.head.querySelector<HTMLLinkElement>("link[rel='canonical']")?.href).toBe(
      'https://comercioflex.com.ar/',
    );
  });

  it('publishes Organization contact data in structured data', () => {
    const script = document.head.querySelector<HTMLScriptElement>(
      "script[type='application/ld+json'][data-landing-seo='true']",
    );
    expect(script).not.toBeNull();

    const schema = JSON.parse(script?.textContent ?? '{}') as {
      '@graph': Array<Record<string, unknown>>;
    };
    expect(schema['@graph']).toHaveLength(2);
    expect(schema['@graph'][0]['email']).toBe('nacho9847@gmail.com');
    expect(schema['@graph'][1]['@type']).toBe('SoftwareApplication');
  });

  it('removes landing-only metadata when leaving the route', () => {
    const meta = TestBed.inject(Meta);

    fixture.destroy();

    expect(meta.getTag("name='robots'")).toBeNull();
    expect(meta.getTag("property='og:site_name'")).toBeNull();
    expect(meta.getTag("property='og:title'")).toBeNull();
    expect(document.head.querySelector("script[data-landing-seo='true']")).toBeNull();
    expect(document.head.querySelector("link[rel='canonical']")).toBeNull();
  });
});

function setInput(element: HTMLElement, name: string, value: string): void {
  const input = element.querySelector<HTMLInputElement>(`input[name="${name}"]`);
  if (!input) throw new Error(`Missing input ${name}`);
  input.value = value;
  input.dispatchEvent(new Event('input', { bubbles: true }));
}

function setTextarea(element: HTMLElement, name: string, value: string): void {
  const textarea = element.querySelector<HTMLTextAreaElement>(`textarea[name="${name}"]`);
  if (!textarea) throw new Error(`Missing textarea ${name}`);
  textarea.value = value;
  textarea.dispatchEvent(new Event('input', { bubbles: true }));
}

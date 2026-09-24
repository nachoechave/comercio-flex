import { DOCUMENT } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

import { LandingBeforeAfter } from '../landing-before-after/landing-before-after';
import { LandingCta } from '../landing-cta/landing-cta';
import { LandingFaq } from '../landing-faq/landing-faq';
import { LandingFeatures } from '../landing-features/landing-features';
import { LandingFooter } from '../landing-footer/landing-footer';
import { LandingHero } from '../landing-hero/landing-hero';
import { LandingHowItWorks } from '../landing-how-it-works/landing-how-it-works';
import { LandingIndustries } from '../landing-industries/landing-industries';
import { LandingNavbar } from '../landing-navbar/landing-navbar';

const LANDING_TITLE = 'Comercio Flex | Tienda online y gestión para comercios';
const LANDING_DESCRIPTION =
  'Creá tu tienda online y gestioná productos, stock, pedidos, pagos y envíos desde un solo lugar. Comercio Flex simplifica la venta online de tu negocio.';
const LANDING_CANONICAL = 'https://comercioflex.com.ar/';
const LANDING_SOCIAL_IMAGE =
  'https://comercioflex.com.ar/assets/comercio-flex/icon-512x512.png';
const LANDING_ROBOTS =
  'index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1';

@Component({
  selector: 'app-landing-page',
  imports: [
    LandingNavbar,
    LandingHero,
    LandingFeatures,
    LandingBeforeAfter,
    LandingHowItWorks,
    LandingIndustries,
    LandingFaq,
    LandingCta,
    LandingFooter,
  ],
  templateUrl: './landing-page.html',
  styleUrl: './landing-page.scss',
})
export class LandingPage implements OnInit, OnDestroy {
  private readonly document = inject(DOCUMENT);
  private readonly meta = inject(Meta);
  private readonly title = inject(Title);
  private canonicalElement?: HTMLLinkElement;
  private canonicalCreated = false;
  private previousCanonical: string | null = null;
  private structuredDataElement?: HTMLScriptElement;

  ngOnInit(): void {
    this.title.setTitle(LANDING_TITLE);
    this.meta.updateTag({ name: 'description', content: LANDING_DESCRIPTION });
    this.meta.updateTag({ name: 'robots', content: LANDING_ROBOTS });

    this.meta.updateTag({ property: 'og:site_name', content: 'Comercio Flex' });
    this.meta.updateTag({ property: 'og:locale', content: 'es_AR' });
    this.meta.updateTag({ property: 'og:title', content: LANDING_TITLE });
    this.meta.updateTag({ property: 'og:description', content: LANDING_DESCRIPTION });
    this.meta.updateTag({ property: 'og:type', content: 'website' });
    this.meta.updateTag({ property: 'og:url', content: LANDING_CANONICAL });
    this.meta.updateTag({ property: 'og:image', content: LANDING_SOCIAL_IMAGE });
    this.meta.updateTag({ property: 'og:image:type', content: 'image/png' });
    this.meta.updateTag({ property: 'og:image:width', content: '512' });
    this.meta.updateTag({ property: 'og:image:height', content: '512' });
    this.meta.updateTag({ property: 'og:image:alt', content: 'Logo de Comercio Flex' });

    this.meta.updateTag({ name: 'twitter:card', content: 'summary' });
    this.meta.updateTag({ name: 'twitter:title', content: LANDING_TITLE });
    this.meta.updateTag({ name: 'twitter:description', content: LANDING_DESCRIPTION });
    this.meta.updateTag({ name: 'twitter:image', content: LANDING_SOCIAL_IMAGE });
    this.meta.updateTag({ name: 'twitter:image:alt', content: 'Logo de Comercio Flex' });

    this.setCanonicalUrl();
    this.setStructuredData();
  }

  ngOnDestroy(): void {
    this.meta.removeTag("name='robots'");
    this.meta.removeTag("property='og:site_name'");
    this.meta.removeTag("property='og:locale'");
    this.meta.removeTag("property='og:title'");
    this.meta.removeTag("property='og:description'");
    this.meta.removeTag("property='og:type'");
    this.meta.removeTag("property='og:url'");
    this.meta.removeTag("property='og:image'");
    this.meta.removeTag("property='og:image:type'");
    this.meta.removeTag("property='og:image:width'");
    this.meta.removeTag("property='og:image:height'");
    this.meta.removeTag("property='og:image:alt'");
    this.meta.removeTag("name='twitter:card'");
    this.meta.removeTag("name='twitter:title'");
    this.meta.removeTag("name='twitter:description'");
    this.meta.removeTag("name='twitter:image'");
    this.meta.removeTag("name='twitter:image:alt'");
    this.structuredDataElement?.remove();

    if (this.canonicalCreated) {
      this.canonicalElement?.remove();
    } else if (this.canonicalElement && this.previousCanonical !== null) {
      this.canonicalElement.href = this.previousCanonical;
    }
  }

  private setCanonicalUrl(): void {
    this.canonicalElement =
      this.document.head.querySelector<HTMLLinkElement>("link[rel='canonical']") ?? undefined;

    if (!this.canonicalElement) {
      this.canonicalElement = this.document.createElement('link');
      this.canonicalElement.rel = 'canonical';
      this.document.head.appendChild(this.canonicalElement);
      this.canonicalCreated = true;
    } else if (this.canonicalElement.dataset['platformSeo'] === 'true') {
      this.canonicalCreated = true;
    } else {
      this.previousCanonical = this.canonicalElement.href;
    }

    this.canonicalElement.href = LANDING_CANONICAL;
  }

  private setStructuredData(): void {
    const existingPlatformSchema = this.document.head.querySelector<HTMLScriptElement>(
      "script[type='application/ld+json'][data-platform-seo='true']",
    );
    this.structuredDataElement = existingPlatformSchema ?? this.document.createElement('script');
    this.structuredDataElement.type = 'application/ld+json';
    this.structuredDataElement.dataset['landingSeo'] = 'true';
    this.structuredDataElement.textContent = JSON.stringify({
      '@context': 'https://schema.org',
      '@graph': [
        {
          '@type': 'Organization',
          '@id': `${LANDING_CANONICAL}#organization`,
          name: 'Comercio Flex',
          url: LANDING_CANONICAL,
          logo: LANDING_SOCIAL_IMAGE,
        },
        {
          '@type': 'SoftwareApplication',
          '@id': `${LANDING_CANONICAL}#software`,
          name: 'Comercio Flex',
          url: LANDING_CANONICAL,
          description: LANDING_DESCRIPTION,
          applicationCategory: 'BusinessApplication',
          operatingSystem: 'Web',
          inLanguage: 'es-AR',
          publisher: {
            '@id': `${LANDING_CANONICAL}#organization`,
          },
        },
      ],
    });
    if (!existingPlatformSchema) {
      this.document.head.appendChild(this.structuredDataElement);
    }
  }
}

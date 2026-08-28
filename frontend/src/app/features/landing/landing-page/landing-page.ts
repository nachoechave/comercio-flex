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

const LANDING_TITLE = 'Comercio Flex | Ecommerce y gestión para comercios';
const LANDING_DESCRIPTION =
  'Creá tu tienda online y gestioná productos, inventario, pedidos y pagos desde un solo lugar con Comercio Flex.';
const LANDING_CANONICAL = 'https://comercioflex.com.ar/';

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

  ngOnInit(): void {
    this.title.setTitle(LANDING_TITLE);
    this.meta.updateTag({ name: 'description', content: LANDING_DESCRIPTION });
    this.meta.updateTag({ property: 'og:title', content: LANDING_TITLE });
    this.meta.updateTag({ property: 'og:description', content: LANDING_DESCRIPTION });
    this.meta.updateTag({ property: 'og:type', content: 'website' });
    this.meta.updateTag({ property: 'og:url', content: LANDING_CANONICAL });
    this.setCanonicalUrl();
  }

  ngOnDestroy(): void {
    this.meta.removeTag("property='og:title'");
    this.meta.removeTag("property='og:description'");
    this.meta.removeTag("property='og:type'");
    this.meta.removeTag("property='og:url'");

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
    } else {
      this.previousCanonical = this.canonicalElement.href;
    }

    this.canonicalElement.href = LANDING_CANONICAL;
  }
}

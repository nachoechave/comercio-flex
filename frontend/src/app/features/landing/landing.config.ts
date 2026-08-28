export interface LandingNavigationItem {
  readonly label: string;
  readonly href: string;
}

export const LANDING_NAVIGATION: readonly LandingNavigationItem[] = [
  { label: 'Inicio', href: '#inicio' },
  { label: 'Solución', href: '#solucion' },
  { label: 'Beneficios', href: '#beneficios' },
  { label: 'Cómo funciona', href: '#como-funciona' },
  { label: 'Rubros', href: '#rubros' },
  { label: 'Demo', href: '#contacto' },
];

export const LANDING_CONFIG = {
  // Keep the commercial contact CTA disabled until a verified, company-owned channel exists.
  // Before production activation, set this once to a real mailto: or HTTPS WhatsApp/contact URL.
  contactHref: null as string | null,
  demoStorePath: '/tiendas/tiendademo',
  showPricing: false,
  showTestimonials: false,
} as const;

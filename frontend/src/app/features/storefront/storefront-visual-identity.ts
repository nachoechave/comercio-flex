export interface StorefrontEditorialCollection {
  eyebrow: string;
  title: string;
  imageUrl: string;
  imageAlt: string;
}

export interface StorefrontVisualIdentity {
  key: 'default' | 'apparel-editorial';
  announcement: string;
  heroEyebrow: string;
  heroTitle: string;
  heroDescription: string;
  heroImageUrl?: string;
  heroImageAlt?: string;
  collections: StorefrontEditorialCollection[];
}

const DEFAULT_IDENTITY: StorefrontVisualIdentity = {
  key: 'default',
  announcement: 'Catálogo online · Stock actualizado · Compra simple',
  heroEyebrow: 'Catálogo online',
  heroTitle: 'Encontrá lo que estás buscando.',
  heroDescription: 'Explorá productos, opciones y precios disponibles en nuestra tienda.',
  collections: [],
};

const APPAREL_DEMO_IDENTITY: StorefrontVisualIdentity = {
  key: 'apparel-editorial',
  announcement: 'Nueva colección · Retiro coordinado · Pago online seguro',
  heroEyebrow: 'Esenciales contemporáneos',
  heroTitle: 'Tu estilo. Tus reglas.',
  heroDescription:
    'Prendas versátiles, tonos neutros y siluetas pensadas para acompañarte todos los días.',
  heroImageUrl: '/assets/demo/indumentaria/hero-editorial.webp',
  heroImageAlt:
    'Tres modelos presentan prendas urbanas neutras en una campaña editorial minimalista',
  collections: [
    {
      eyebrow: 'Comodidad diaria',
      title: 'Buzos esenciales',
      imageUrl: '/assets/demo/indumentaria/categoria-buzos.webp',
      imageAlt: 'Modelo con buzo oversize color crema sobre un fondo neutro',
    },
    {
      eyebrow: 'Capas livianas',
      title: 'Abrigos urbanos',
      imageUrl: '/assets/demo/indumentaria/categoria-abrigos.webp',
      imageAlt: 'Modelo con sobrecamisa estructurada color carbón',
    },
    {
      eyebrow: 'Detalles simples',
      title: 'Accesorios',
      imageUrl: '/assets/demo/indumentaria/categoria-accesorios.webp',
      imageAlt: 'Gorra, bolso de tela y gorro tejidos en tonos neutros',
    },
  ],
};

export function storefrontVisualIdentityFor(storeSlug: string): StorefrontVisualIdentity {
  return storeSlug.toLowerCase() === 'tienda-a' ? APPAREL_DEMO_IDENTITY : DEFAULT_IDENTITY;
}

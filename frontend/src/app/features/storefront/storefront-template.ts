export type StorefrontTemplate = 'FASHION' | 'FRESH' | 'CATALOG';

export interface StorefrontTemplateOption {
  value: StorefrontTemplate;
  name: string;
  shortName: string;
  description: string;
  bestFor: string;
}

export const STOREFRONT_TEMPLATES: readonly StorefrontTemplateOption[] = [
  {
    value: 'FASHION',
    name: 'Fashion Editorial',
    shortName: 'Fashion',
    description: 'Editorial, premium y visual. Hero fotográfico, tipografía elegante y foco en marca y colección.',
    bestFor: 'Moda · Calzado · Accesorios · Marcas premium',
  },
  {
    value: 'FRESH',
    name: 'Fresh Social',
    shortName: 'Fresh',
    description: 'Colorida, joven y dinámica. Tarjetas redondeadas, promos visibles y una experiencia cercana.',
    bestFor: 'Emprendimientos · Indumentaria · Lifestyle · Regalos',
  },
  {
    value: 'CATALOG',
    name: 'Catalog Pro',
    shortName: 'Catalog',
    description: 'Práctica y orientada a conversión. Búsqueda protagonista, filtros y grilla densa de productos.',
    bestFor: 'Catálogos grandes · Tecnología · Hogar · Multirrubro',
  },
];

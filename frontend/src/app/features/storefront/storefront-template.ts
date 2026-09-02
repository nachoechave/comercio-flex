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
    name: 'Editorial Moda',
    shortName: 'Fashion',
    description: 'Visual, elegante y pensada para indumentaria y marcas.',
    bestFor: 'Moda · Calzado · Accesorios · Belleza',
  },
  {
    value: 'FRESH',
    name: 'Mercado Fresco',
    shortName: 'Fresh',
    description: 'Cálida y comercial, pensada para alimentos y negocios de cercanía.',
    bestFor: 'Alimentos · Panadería · Granja · Gourmet',
  },
  {
    value: 'CATALOG',
    name: 'Catálogo Versátil',
    shortName: 'Catalog',
    description: 'Limpia, flexible y preparada para comercios con muchos tipos de productos.',
    bestFor: 'Hogar · Tecnología · Bazar · Librería',
  },
];

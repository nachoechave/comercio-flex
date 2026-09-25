export type StorefrontTemplate =
  | 'FASHION'
  | 'FRESH'
  | 'CATALOG'
  | 'COAST'
  | 'MINIMAL'
  | 'LUXE'
  | 'URBAN'
  | 'EDITORIAL'
  | 'MARKET'
  | 'STUDIO'
  | 'BOLD';

export type DesignerStorefrontTemplate =
  | 'COAST'
  | 'MINIMAL'
  | 'LUXE'
  | 'URBAN'
  | 'EDITORIAL'
  | 'MARKET'
  | 'STUDIO'
  | 'BOLD';

export interface StorefrontTemplateOption {
  value: StorefrontTemplate;
  name: string;
  shortName: string;
  description: string;
  bestFor: string;
}

export const DESIGNER_STOREFRONT_TEMPLATES: readonly DesignerStorefrontTemplate[] = [
  'COAST',
  'MINIMAL',
  'LUXE',
  'URBAN',
  'EDITORIAL',
  'MARKET',
  'STUDIO',
  'BOLD',
];

export const STOREFRONT_TEMPLATES: readonly StorefrontTemplateOption[] = [
  {
    value: 'FASHION',
    name: 'Fashion actual',
    shortName: 'Fashion',
    description:
      'La plantilla editorial que ya usa Comercio Flex. Se conserva sin cambios visuales para las tiendas publicadas.',
    bestFor: 'Moda · Surf · Indumentaria · Marcas visuales',
  },
  {
    value: 'FRESH',
    name: 'Fresh',
    shortName: 'Fresh',
    description:
      'Colorida, joven y dinámica. Tarjetas redondeadas, promos visibles y una experiencia cercana.',
    bestFor: 'Emprendimientos · Lifestyle · Regalos · Tiendas jóvenes',
  },
  {
    value: 'CATALOG',
    name: 'Catalog',
    shortName: 'Catalog',
    description:
      'Práctica y orientada a catálogo. Búsqueda protagonista, filtros y una grilla preparada para mucho stock.',
    bestFor: 'Catálogos grandes · Tecnología · Hogar · Multirrubro',
  },
  {
    value: 'COAST',
    name: 'Coast',
    shortName: 'Coast',
    description:
      'Editorial de alto impacto con fotografía protagonista, tipografía condensada y estética surf premium.',
    bestFor: 'Surf · Moda · Outdoor · Marcas visuales',
  },
  {
    value: 'MINIMAL',
    name: 'Minimal',
    shortName: 'Minimal',
    description:
      'Aire, serif elegante y tonos neutros. Una experiencia serena donde el producto y la fotografía respiran.',
    bestFor: 'Premium basics · Wellness · Deco · Diseño',
  },
  {
    value: 'LUXE',
    name: 'Luxe',
    shortName: 'Luxe',
    description:
      'Oscura, sofisticada y cálida, con acentos champagne y una puesta en escena de lujo.',
    bestFor: 'Joyería · Perfumería · Cosmética · Accesorios premium',
  },
  {
    value: 'URBAN',
    name: 'Urban',
    shortName: 'Urban',
    description:
      'Streetwear en negro con acentos neón, titulares enormes, drops y una composición gráfica agresiva.',
    bestFor: 'Streetwear · Sneakers · Skate · Cultura joven',
  },
  {
    value: 'EDITORIAL',
    name: 'Editorial',
    shortName: 'Editorial',
    description:
      'Una tienda con lenguaje de revista: historias, lookbook, fotografía grande y selección curada.',
    bestFor: 'Moda · Lifestyle · Diseño · Marcas con storytelling',
  },
  {
    value: 'MARKET',
    name: 'Market',
    shortName: 'Market',
    description:
      'Comercial, clara y escalable. Búsqueda visible, accesos por rubro, promociones y mucha densidad de producto.',
    bestFor: 'Ferretería · Hogar · Tecnología · Multicategoría',
  },
  {
    value: 'STUDIO',
    name: 'Studio',
    shortName: 'Studio',
    description:
      'Editorial cálida para interiores y objetos, con ambientes protagonistas y una grilla muy cuidada.',
    bestFor: 'Muebles · Decoración · Cerámica · Diseño de interiores',
  },
  {
    value: 'BOLD',
    name: 'Bold',
    shortName: 'Bold',
    description:
      'Alta energía, promociones fuertes, titulares deportivos y bloques pensados para conversión.',
    bestFor: 'Deportes · Outdoor · Fitness · Gaming',
  },
];

const STORE_TEMPLATE_SET = new Set<StorefrontTemplate>(
  STOREFRONT_TEMPLATES.map((template) => template.value),
);
const DESIGNER_TEMPLATE_SET = new Set<StorefrontTemplate>(DESIGNER_STOREFRONT_TEMPLATES);

export function isStorefrontTemplate(value: string | null | undefined): value is StorefrontTemplate {
  return value != null && STORE_TEMPLATE_SET.has(value as StorefrontTemplate);
}

export function isDesignerStorefrontTemplate(
  value: StorefrontTemplate | null | undefined,
): value is DesignerStorefrontTemplate {
  return value != null && DESIGNER_TEMPLATE_SET.has(value);
}

import { Meta, Title } from '@angular/platform-browser';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { DesignerHome } from './designer-home/designer-home';
import { StorefrontRoutingService } from '../storefront-routing.service';
import { ProductCard } from '../product-card/product-card';
import { StorefrontApiService } from '../storefront-api.service';
import { StorefrontContextService } from '../storefront-context.service';
import { storefrontErrorMessage } from '../storefront-errors';
import { PublicCategory, PublicProductPage } from '../storefront.models';
import {
  DesignerStorefrontTemplate,
  isDesignerStorefrontTemplate,
  isStorefrontTemplate,
  StorefrontTemplate,
} from '../storefront-template';

const PAGE_SIZE = 24;
const FASHION_LANDING_LIMIT = 10;
const FRESH_LANDING_LIMIT = 10;
const EMPTY_PAGE: PublicProductPage = { items: [], page: 0, size: PAGE_SIZE, totalItems: 0, totalPages: 0 };

@Component({
  selector: 'app-catalog-page',
  imports: [ReactiveFormsModule, RouterLink, ProductCard, DesignerHome],
  templateUrl: './catalog-page.html',
  styleUrls: ['./catalog-page.scss', './catalog-page-v2.scss', './catalog-fashion-fidelity.scss', './catalog-fashion-scaling.scss', './catalog-designer-full.scss'],
})
export class CatalogPage {
  private readonly api = inject(StorefrontApiService);
  protected readonly context = inject(StorefrontContextService);
  private readonly route = inject(ActivatedRoute);
  protected readonly storefrontRouting = inject(StorefrontRoutingService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly retryVersion = signal(0);

  protected readonly storeSlug = toSignal(this.storefrontRouting.storeSlug(this.route), { initialValue: this.route.snapshot.paramMap.get('storeSlug') ?? '' });
  private readonly queryParams = toSignal(this.route.queryParamMap, { initialValue: this.route.snapshot.queryParamMap });
  protected readonly categories = signal<PublicCategory[]>([]);
  protected readonly page = signal<PublicProductPage>(EMPTY_PAGE);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly query = signal('');
  protected readonly selectedCategory = signal('');
  protected readonly activeTemplate = computed<StorefrontTemplate>(() => {
    const preview = this.queryParams().get('previewTemplate');
    if (isStorefrontTemplate(preview)) return preview;
    return this.context.settings()?.branding?.template ?? 'CATALOG';
  });
  protected readonly designerTemplate = computed<DesignerStorefrontTemplate | null>(() => {
    const template = this.activeTemplate();
    return isDesignerStorefrontTemplate(template) ? template : null;
  });
  protected readonly isModern = computed(() => this.activeTemplate() === 'FASHION');
  protected readonly isFresh = computed(() => this.activeTemplate() === 'FRESH');
  protected readonly isMinimal = computed(() => this.activeTemplate() === 'CATALOG');
  protected readonly fullCatalog = computed(() => this.queryParams().get('catalogo') === 'todos');
  protected readonly isFashionLanding = computed(() => this.isModern() && !this.query() && !this.selectedCategory() && !this.fullCatalog() && !this.queryParams().has('page'));
  protected readonly isFreshLanding = computed(() => this.isFresh() && !this.query() && !this.selectedCategory() && !this.fullCatalog() && !this.queryParams().has('page'));
  protected readonly isDesignerLanding = computed(() => this.designerTemplate() !== null && !this.query() && !this.selectedCategory() && !this.fullCatalog() && !this.queryParams().has('page'));
  protected readonly visibleProducts = computed(() => {
    if (this.isFashionLanding()) return this.page().items.slice(0, FASHION_LANDING_LIMIT);
    if (this.isFreshLanding()) return this.page().items.slice(0, FRESH_LANDING_LIMIT);
    return this.page().items;
  });
  protected readonly hiddenProductCount = computed(() => Math.max(0, this.page().totalItems - this.visibleProducts().length));
  protected readonly heroEyebrow = computed(() => {
    const configured = this.context.settings()?.branding?.heroEyebrow;
    if (configured) return configured;
    switch (this.activeTemplate()) {
      case 'FASHION': return 'Nueva colección';
      case 'FRESH': return 'Más que productos, buena vibra';
      case 'COAST': return 'Nueva temporada';
      case 'MINIMAL': return 'Esenciales atemporales';
      case 'LUXE': return 'Piezas únicas';
      case 'URBAN': return 'Nuevo drop';
      case 'EDITORIAL': return 'Capítulo 01';
      case 'MARKET': return 'Todo para tu día';
      case 'STUDIO': return 'Objetos que hacen hogar';
      case 'BOLD': return 'Equipate. Explorá. Superá.';
      default: return 'Catálogo';
    }
  });
  protected readonly heroTitle = computed(() => {
    const configured = this.context.settings()?.branding?.heroTitle;
    if (configured) return configured;
    switch (this.activeTemplate()) {
      case 'FASHION': return 'Más que productos, un estilo de vida.';
      case 'FRESH': return 'Descubrí lo nuevo';
      case 'COAST': return 'Más allá del horizonte';
      case 'MINIMAL': return 'Menos para una vida más simple';
      case 'LUXE': return 'Descubrí la elegancia';
      case 'URBAN': return 'Cultura en cada paso';
      case 'EDITORIAL': return 'Una forma más lenta de ver el mundo';
      case 'MARKET': return 'Todo lo que necesitás';
      case 'STUDIO': return 'Diseño para tu espacio';
      case 'BOLD': return 'Rendimiento sin límites';
      default: return 'Encontrá lo que buscás.';
    }
  });
  protected readonly heroSubtitle = computed(() => {
    const configured = this.context.settings()?.branding?.heroSubtitle;
    if (configured) return configured;
    switch (this.activeTemplate()) {
      case 'FASHION': return 'Una selección con identidad propia, pensada para destacar tu marca.';
      case 'FRESH': return 'Productos para una experiencia simple, alegre y cercana.';
      case 'COAST': return 'Moda, surf y marcas visuales con identidad propia.';
      case 'MINIMAL': return 'Calidad, calma y propósito en cada detalle.';
      case 'LUXE': return 'Joyería, perfumes y cosmética seleccionados para una experiencia sofisticada.';
      case 'URBAN': return 'Streetwear, sneakers y cultura joven. Más que ropa, una forma de vida.';
      case 'EDITORIAL': return 'Moda, diseño y estilo de vida para una experiencia con más intención.';
      case 'MARKET': return 'Hogar, tecnología, herramientas y mucho más en un solo lugar.';
      case 'STUDIO': return 'Muebles, decoración y objetos seleccionados para crear ambientes con alma.';
      case 'BOLD': return 'Productos diseñados para llevarte más lejos.';
      default: return 'Buscá, filtrá y compará productos de forma rápida.';
    }
  });
  protected readonly filters = this.formBuilder.nonNullable.group({ q: [''], category: [''] });
  protected readonly currencyCode = this.context.currencyCode;

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      const params = this.queryParams();
      this.retryVersion();
      const q = (params.get('q') ?? '').trim().slice(0, 100);
      const category = params.get('categoria') ?? '';
      const requestedPage = this.readPage(params);
      const showFullCatalog = params.get('catalogo') === 'todos';
      this.query.set(q);
      this.selectedCategory.set(category);
      this.filters.setValue({ q, category }, { emitEvent: false });
      this.page.set(EMPTY_PAGE);
      this.categories.set([]);
      this.errorMessage.set(null);
      this.loading.set(true);
      if (!slug) {
        this.loading.set(false);
        this.errorMessage.set('No pudimos identificar la tienda solicitada.');
        return;
      }
      const subscription = forkJoin({
        categories: this.api.listCategories(slug),
        page: this.api.listProducts(slug, { page: requestedPage, size: PAGE_SIZE, q: q || undefined, category: category || undefined }),
      }).subscribe({
        next: ({ categories, page }) => {
          if (page.totalPages > 0 && requestedPage >= page.totalPages) {
            void this.router.navigate([], { relativeTo: this.route, queryParams: { page: page.totalPages === 1 ? null : page.totalPages }, queryParamsHandling: 'merge', replaceUrl: true });
            return;
          }
          this.categories.set(categories);
          this.page.set(page);
          this.loading.set(false);
          if (q || category || requestedPage > 0 || showFullCatalog) this.scrollToProducts();
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.errorMessage.set(storefrontErrorMessage(error, 'No pudimos cargar los productos. Intentá nuevamente.'));
        },
      });
      onCleanup(() => subscription.unsubscribe());
    });
    effect(() => {
      const slug = this.storeSlug();
      const storeName = this.context.settings()?.storeName;
      const settingsSlug = this.context.settings()?.slug;
      if (storeName && settingsSlug === slug) {
        this.title.setTitle(`Productos | ${storeName}`);
        this.meta.updateTag({ name: 'description', content: `Explorá el catálogo de ${storeName}, sus precios y opciones disponibles.` });
      } else {
        this.title.setTitle('Catálogo | Comercio Flex');
        this.meta.updateTag({ name: 'description', content: 'Explorá productos y opciones disponibles en Comercio Flex.' });
      }
    });
  }

  protected search(): void {
    const value = this.filters.getRawValue();
    const filtered = Boolean(value.q.trim() || value.category);
    void this.router.navigate([], { relativeTo: this.route, queryParams: { q: value.q.trim() || null, categoria: value.category || null, page: null, catalogo: filtered ? 'todos' : null }, queryParamsHandling: 'merge', fragment: filtered ? 'catalog-products' : undefined });
  }
  protected clearFilters(): void {
    this.filters.reset({ q: '', category: '' });
    void this.router.navigate([], { relativeTo: this.route, queryParams: { q: null, categoria: null, page: null, catalogo: null }, queryParamsHandling: 'merge' });
  }
  protected goToPage(page: number): void {
    if (page < 0 || page >= this.page().totalPages) return;
    void this.router.navigate([], { relativeTo: this.route, queryParams: { page: page === 0 ? null : page + 1, catalogo: 'todos' }, queryParamsHandling: 'merge', fragment: 'catalog-products' });
  }
  protected retry(): void { this.retryVersion.update((value) => value + 1); }
  private scrollToProducts(): void {
    setTimeout(() => {
      if (typeof document === 'undefined' || typeof window === 'undefined') return;
      const products = document.getElementById('catalog-products');
      if (!products) return;
      const top = products.getBoundingClientRect().top + window.scrollY - 96;
      window.scrollTo({ top: Math.max(0, top), behavior: 'smooth' });
    });
  }
  private readPage(params: ParamMap): number {
    const rawPage = Number(params.get('page') ?? '1');
    return Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage - 1 : 0;
  }
}

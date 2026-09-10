import { CurrencyPipe, NgIf } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RadioContext } from './radio-context';
import { RadioSite, RadioSiteApiService } from './radio-site-api.service';
import { MembershipApiService, Plan } from './membership-api.service';
import { StorefrontContextService } from '../storefront/storefront-context.service';

@Component({ selector: 'app-radio-home-page', imports: [RouterLink, CurrencyPipe, NgIf], styleUrl: './radio-public.scss', template: `
  @if (site(); as content) {
    <section class="radio-hero" [style.background-image]="contentSettingsHero()"><div class="hero-content"><p class="eyebrow">RADIO · COMUNIDAD</p><h1>{{ content.settings.heroTitle || 'Una voz que nos une.' }}</h1><p>{{ content.settings.heroSubtitle || 'Información, pasión y compañía todos los días.' }}</p><div class="hero-actions"><a class="button primary" [routerLink]="context.link('socios')">Hacete socio</a><a *ngIf="content.settings.youtubeUrl as youtube" class="button ghost" [href]="youtube" target="_blank" rel="noopener">Ver en YouTube</a></div></div></section>
    <section class="home-section"><div class="section-heading"><div><p class="eyebrow">EN VIVO Y A TU RITMO</p><h2>Programas destacados</h2></div><a [routerLink]="context.link('programas')">Ver todos</a></div><div class="content-grid program-grid">@for (program of content.programs.slice(0, 3); track program.publicId) { <article class="content-card"><img *ngIf="program.imageUrl" [src]="program.imageUrl" [alt]="program.name" loading="lazy" /><div><span class="tag">{{ program.days }}</span><h3>{{ program.name }}</h3><p>{{ program.description }}</p><strong>{{ program.schedule }}</strong></div></article> }</div></section>
    <section class="home-section membership-callout"><div><p class="eyebrow">COMUNIDAD</p><h2>Sumate a la radio</h2><p>Elegí el plan que mejor acompaña tu forma de estar cerca.</p></div><div class="content-grid plan-grid">@for (plan of plans().slice(0, 3); track plan.publicId) { <article class="mini-plan"><h3>{{ plan.name }}</h3><strong>{{ plan.price | currency:plan.currency }} <small>/ mes</small></strong><p>{{ plan.description }}</p></article> }</div><a class="button primary" [routerLink]="context.link('socios')">Conocé los planes</a></section>
    @if (content.sponsors.length) { <section class="home-section sponsors"><p class="eyebrow">NOS ACOMPAÑAN</p><div class="sponsor-row">@for (sponsor of content.sponsors; track sponsor.publicId) { <a [href]="sponsor.targetUrl || null" [attr.target]="sponsor.targetUrl ? '_blank' : null" rel="noopener"><img *ngIf="sponsor.logoUrl" [src]="sponsor.logoUrl" [alt]="sponsor.name" loading="lazy" /><span *ngIf="!sponsor.logoUrl">{{ sponsor.name }}</span></a> }</div></section> }
  } @else { <p role="status">Cargando la radio…</p> }` })
export class RadioHomePage {
  readonly context = inject(RadioContext); private readonly api = inject(RadioSiteApiService); private readonly memberships = inject(MembershipApiService); private readonly storefront = inject(StorefrontContextService);
  readonly site = signal<RadioSite | null>(null); readonly plans = signal<Plan[]>([]);
  constructor() { const slug = this.context.slug()!; this.api.get(slug).subscribe({ next: value => this.site.set(value) }); this.memberships.plans(slug).subscribe({ next: value => this.plans.set(value.filter(p => p.active)) }); }
  contentSettingsHero() { const url = this.storefront.settings()?.branding?.heroImageUrl; return url ? `linear-gradient(90deg,rgba(5,14,30,.86),rgba(5,14,30,.35)),url('${url}')` : 'linear-gradient(90deg,rgba(5,14,30,.95),rgba(5,14,30,.55))'; }
}

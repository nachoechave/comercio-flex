import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { RadioContext } from './radio-context';
import { RadioSite, RadioSiteApiService, RadioVideo } from './radio-site-api.service';
import { MembershipApiService, Plan, Member } from './membership-api.service';
import { StorefrontContextService } from '../storefront/storefront-context.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({ selector: 'app-radio-home-page', imports: [RouterLink, CurrencyPipe, DatePipe], styleUrl: './radio-home.scss', templateUrl: './radio-home-page.html' })
export class RadioHomePage {
  readonly context = inject(RadioContext); private readonly api = inject(RadioSiteApiService); private readonly memberships = inject(MembershipApiService); private readonly storefront = inject(StorefrontContextService); private readonly auth = inject(AuthService);
  readonly site = signal<RadioSite | null>(null); readonly plans = signal<Plan[]>([]); readonly member = signal<Member | null>(null); readonly videos = signal<RadioVideo[]>([]);
  constructor() { const slug = this.context.slug()!; this.api.get(slug).subscribe({ next: value => { this.site.set(value); if (value.settings.youtubeChannelId) this.api.videos(slug).subscribe({ next: videos => this.videos.set(videos) }); } }); this.memberships.plans(slug).subscribe({ next: value => this.plans.set(value.filter(p => p.active)) }); this.auth.loadSession(true).subscribe({ next: session => { if (session.authenticated) this.memberships.mine(slug).subscribe({ next: value => this.member.set(value) }); } }); }
  hasMembership() { return !!this.member() && this.member()?.state !== 'NONE'; }
  contentSettingsHero() { const url = this.storefront.settings()?.branding?.heroImageUrl; return url ? `linear-gradient(90deg,rgba(5,14,30,.86),rgba(5,14,30,.35)),url('${url}')` : 'linear-gradient(90deg,rgba(5,14,30,.95),rgba(5,14,30,.55))'; }
}

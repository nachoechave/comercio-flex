import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { switchMap } from 'rxjs';
import { CsrfService } from '../../core/auth/csrf.service';

export interface RadioSiteSettings { heroTitle: string | null; heroSubtitle: string | null; description: string | null; youtubeUrl: string | null; youtubeChannelId?: string | null; instagramUrl: string | null; xUrl: string | null; whatsappUrl: string | null; }
export interface RadioProgram { publicId: string; name: string; description: string; days: string; schedule: string; imageUrl: string | null; hosts: string | null; displayOrder: number; active: boolean; }
export interface RadioTeamMember { publicId: string; name: string; role: string; bio: string; photoUrl: string | null; socialUrl: string | null; displayOrder: number; active: boolean; }
export interface RadioSponsor { publicId: string; name: string; logoUrl: string | null; targetUrl: string | null; description: string | null; level: 'PRIMARY' | 'SECONDARY'; displayOrder: number; active: boolean; }
export interface RadioSite { settings: RadioSiteSettings; programs: RadioProgram[]; team: RadioTeamMember[]; sponsors: RadioSponsor[]; }
export interface RadioVideo { videoId: string; title: string; description: string | null; publishedAt: string | null; thumbnailUrl: string | null; videoUrl: string; }
export type RadioSiteSettingsInput = Omit<RadioSiteSettings, never>;
export type RadioProgramInput = Omit<RadioProgram, 'publicId'>;
export type RadioTeamInput = Omit<RadioTeamMember, 'publicId'>;
export type RadioSponsorInput = Omit<RadioSponsor, 'publicId'>;

@Injectable({ providedIn: 'root' })
export class RadioSiteApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private url(slug: string, suffix = 'radio-site') { return `/api/v1/stores/${encodeURIComponent(slug)}/${suffix}`; }
  get(slug: string): Observable<RadioSite> { return this.http.get<RadioSite>(this.url(slug)); }
  videos(slug: string): Observable<RadioVideo[]> { return this.http.get<RadioVideo[]>(this.url(slug, 'radio-site/videos')); }
  admin(slug: string): Observable<RadioSite> { return this.http.get<RadioSite>(this.url(slug, 'admin/radio-site')); }
  settings(slug: string, value: RadioSiteSettingsInput) { return this.write<RadioSite>('PUT', this.url(slug, 'admin/radio-site'), value); }
  saveProgram(slug: string, value: RadioProgramInput, id?: string) { return this.write<RadioProgram>(id ? 'PUT' : 'POST', this.url(slug, `admin/radio-site/programs${id ? `/${encodeURIComponent(id)}` : ''}`), value); }
  saveTeam(slug: string, value: RadioTeamInput, id?: string) { return this.write<RadioTeamMember>(id ? 'PUT' : 'POST', this.url(slug, `admin/radio-site/team${id ? `/${encodeURIComponent(id)}` : ''}`), value); }
  saveSponsor(slug: string, value: RadioSponsorInput, id?: string) { return this.write<RadioSponsor>(id ? 'PUT' : 'POST', this.url(slug, `admin/radio-site/sponsors${id ? `/${encodeURIComponent(id)}` : ''}`), value); }
  remove(slug: string, kind: 'programs' | 'team' | 'sponsors', id: string) { return this.csrf.ensureToken().pipe(switchMap(() => this.http.delete<void>(this.url(slug, `admin/radio-site/${kind}/${encodeURIComponent(id)}`)))); }
  private write<T>(method: string, url: string, body: unknown) { return this.csrf.ensureToken().pipe(switchMap(() => this.http.request<T>(method, url, { body }))); }
}

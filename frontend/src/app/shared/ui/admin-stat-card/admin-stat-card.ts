import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-admin-stat-card',
  imports: [RouterLink],
  template: `
    <article>
      <p>{{ label() }}</p>
      <strong>{{ value() }}</strong>
      @if (link(); as target) {
        <a [routerLink]="target">{{ helper() }}</a>
      } @else {
        <span>{{ helper() }}</span>
      }
    </article>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    article { display: flex; min-height: 9rem; padding: 1.1rem; border: 1px solid var(--cf-border); border-radius: var(--cf-radius-lg); background: var(--cf-surface); box-shadow: var(--cf-shadow-sm); flex-direction: column; }
    p { margin: 0 0 .65rem; color: var(--cf-text-muted); font-size: .82rem; font-weight: 700; }
    strong { overflow: hidden; color: var(--cf-text); font-size: clamp(1.45rem, 3vw, 2rem); letter-spacing: -.04em; text-overflow: ellipsis; }
    span, a { margin-top: auto; color: var(--cf-text-soft); font-size: .78rem; }
    a { color: var(--cf-primary); font-weight: 700; text-decoration: none; }
  `,
})
export class AdminStatCard {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly helper = input.required<string>();
  readonly link = input<string | readonly (string | number)[] | null>(null);
}

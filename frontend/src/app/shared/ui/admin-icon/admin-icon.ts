import { Component, input } from '@angular/core';

export type AdminIconName =
  | 'categories'
  | 'chevron'
  | 'commerce'
  | 'dashboard'
  | 'inventory'
  | 'logout'
  | 'menu'
  | 'orders'
  | 'payments'
  | 'products'
  | 'store'
  | 'transfers'
  | 'user'
  | 'x';

@Component({
  selector: 'app-admin-icon',
  template: `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
      @switch (name()) {
        @case ('dashboard') { <path d="M4 13h6V4H4v9Zm10 7h6v-9h-6v9ZM4 20h6v-3H4v3Zm10-13h6V4h-6v3Z" /> }
        @case ('products') { <path d="m4 7 8-4 8 4-8 4-8-4Zm0 0v10l8 4 8-4V7M12 11v10" /> }
        @case ('categories') { <path d="M4 5h6v6H4V5Zm10 0h6v6h-6V5ZM4 15h6v4H4v-4Zm10 0h6v4h-6v-4Z" /> }
        @case ('inventory') { <path d="M3 7 12 3l9 4-9 4-9-4Zm0 0v10l9 4 9-4V7M8 9v4l4 2 4-2V9" /> }
        @case ('orders') { <path d="M7 3h10v3H7V3ZM5 5h14v16H5V5Zm4 5h6m-6 4h6m-6 4h4" /> }
        @case ('transfers') { <path d="M4 7h16M7 3h10l4 4H3l4-4Zm-2 8v7m5-7v7m4-7v7m5-7v7M3 21h18" /> }
        @case ('commerce') { <path d="M4 10v10h16V10M3 10l2-6h14l2 6M8 20v-6h8v6M3 10c0 2 4 2 4 0 0 2 5 2 5 0 0 2 5 2 5 0 0 2 4 2 4 0" /> }
        @case ('payments') { <path d="M3 6h18v13H3V6Zm0 4h18M7 15h4" /> }
        @case ('store') { <path d="M4 10v10h16V10M3 10l2-6h14l2 6M8 20v-6h8v6" /> }
        @case ('user') { <circle cx="12" cy="8" r="4" /><path d="M4 21a8 8 0 0 1 16 0" /> }
        @case ('logout') { <path d="M10 5H5v14h5m4-4 4-3-4-3m4 3H9" /> }
        @case ('menu') { <path d="M4 6h16M4 12h16M4 18h16" /> }
        @case ('x') { <path d="m5 5 14 14M19 5 5 19" /> }
        @case ('chevron') { <path d="m9 6 6 6-6 6" /> }
      }
    </svg>
  `,
  styles: `
    :host { display: inline-flex; width: 1.25rem; height: 1.25rem; flex: 0 0 auto; }
    svg { width: 100%; height: 100%; }
  `,
})
export class AdminIcon {
  readonly name = input.required<AdminIconName>();
}

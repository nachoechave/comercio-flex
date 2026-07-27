import { Component, input } from '@angular/core';

@Component({
  selector: 'app-status-pill',
  template: `<span class="status" [attr.data-tone]="tone()">{{ label() }}</span>`,
  styles: `
    .status {
      display: inline-flex;
      padding: 0.35rem 0.65rem;
      border-radius: 999px;
      color: #233044;
      background: #e7edf5;
      font-size: 0.875rem;
      font-weight: 700;
    }

    .status[data-tone='success'] {
      color: #075b3a;
      background: #d9f6e8;
    }

    .status[data-tone='danger'] {
      color: #8a1724;
      background: #fde2e5;
    }
  `,
})
export class StatusPill {
  readonly label = input.required<string>();
  readonly tone = input<'neutral' | 'success' | 'danger'>('neutral');
}

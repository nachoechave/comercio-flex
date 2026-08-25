import { Component, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { inheritedRouteParam } from '../../../core/routing/inherited-route-param';
import { CommerceDatePipe } from '../../../shared/pipes/commerce-date.pipe';
import { StorefrontMoneyPipe } from '../../storefront/storefront-money.pipe';
import { BankTransferApiService } from './bank-transfer-api.service';
import { AdminBankTransferPayment } from './bank-transfer.models';

@Component({
  selector: 'app-bank-transfer-list',
  imports: [CommerceDatePipe, RouterLink, StorefrontMoneyPipe],
  templateUrl: './bank-transfer-list.html',
  styleUrl: './bank-transfer-list.scss',
})
export class BankTransferList {
  private readonly api = inject(BankTransferApiService);
  private readonly route = inject(ActivatedRoute);
  readonly storeSlug = toSignal(inheritedRouteParam(this.route, 'storeSlug'), { initialValue: '' });
  readonly items = signal<AdminBankTransferPayment[]>([]);
  readonly loading = signal(true);
  readonly errorMessage = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const slug = this.storeSlug();
      if (!slug) return;
      const subscription = this.api.listPending(slug).subscribe({
        next: (items) => { this.items.set(items); this.loading.set(false); },
        error: () => { this.errorMessage.set('No pudimos cargar las transferencias.'); this.loading.set(false); },
      });
      onCleanup(() => subscription.unsubscribe());
    });
  }
}

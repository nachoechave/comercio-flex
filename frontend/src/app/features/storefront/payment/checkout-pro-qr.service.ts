import { Injectable, inject } from '@angular/core';
import QRCode from 'qrcode';

import { CheckoutProNavigationService } from './checkout-pro-navigation.service';

@Injectable({ providedIn: 'root' })
export class CheckoutProQrService {
  private readonly navigation = inject(CheckoutProNavigationService);

  async create(checkoutUrl: string): Promise<string> {
    const trustedUrl = this.navigation.trustedUrl(checkoutUrl);
    return QRCode.toDataURL(trustedUrl, {
      errorCorrectionLevel: 'M',
      margin: 2,
      width: 240,
    });
  }
}

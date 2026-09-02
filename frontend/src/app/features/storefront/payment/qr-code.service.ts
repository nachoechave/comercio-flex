import { Injectable } from '@angular/core';
import QRCode from 'qrcode';

@Injectable({ providedIn: 'root' })
export class QrCodeService {
  create(qrData: string): Promise<string> {
    if (!qrData.trim()) return Promise.reject(new Error('QR data is required'));
    return QRCode.toDataURL(qrData, {
      errorCorrectionLevel: 'M',
      margin: 2,
      width: 240,
    });
  }
}

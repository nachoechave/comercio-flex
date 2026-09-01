import { TestBed } from '@angular/core/testing';
import QRCode from 'qrcode';

import { QrCodeService } from './qr-code.service';

vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn() } }));

describe('QrCodeService', () => {
  it('renders only provider qr_data', async () => {
    const toDataUrl = vi.mocked(
      QRCode.toDataURL as unknown as (text: string, options: object) => Promise<string>,
    );
    toDataUrl.mockResolvedValue('data:image/png;base64,qr');
    const service = TestBed.inject(QrCodeService);

    await expect(service.create('provider-qr-data')).resolves.toBe('data:image/png;base64,qr');

    expect(QRCode.toDataURL).toHaveBeenCalledWith(
      'provider-qr-data',
      expect.objectContaining({ width: 240 }),
    );
  });

  it('rejects empty QR data', async () => {
    const service = TestBed.inject(QrCodeService);
    await expect(service.create('  ')).rejects.toThrow('QR data is required');
  });
});

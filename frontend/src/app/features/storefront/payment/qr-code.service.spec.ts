import { TestBed } from '@angular/core/testing';

import { QrCodeService } from './qr-code.service';

describe('QrCodeService', () => {
  it('renders only provider qr_data', async () => {
    const service = TestBed.inject(QrCodeService);

    const result = await service.create('provider-qr-data');

    expect(result).toMatch(/^data:image\/png;base64,/);
    expect(result.length).toBeGreaterThan('data:image/png;base64,'.length);
  });

  it('rejects empty QR data', async () => {
    const service = TestBed.inject(QrCodeService);

    await expect(service.create('  ')).rejects.toThrow(
      'QR data is required',
    );
  });
});
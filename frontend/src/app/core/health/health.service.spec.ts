import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { HealthService } from './health.service';

describe('HealthService', () => {
  let service: HealthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(HealthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('requests the backend health endpoint', () => {
    service.getHealth().subscribe((response) => {
      expect(response.status).toBe('UP');
    });

    const request = http.expectOne('/actuator/health');
    expect(request.request.method).toBe('GET');
    request.flush({ status: 'UP' });
  });
});

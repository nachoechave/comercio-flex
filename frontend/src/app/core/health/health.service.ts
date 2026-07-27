import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface HealthResponse {
  status: string;
}

@Injectable({ providedIn: 'root' })
export class HealthService {
  private readonly http = inject(HttpClient);

  getHealth() {
    return this.http.get<HealthResponse>('/actuator/health');
  }
}

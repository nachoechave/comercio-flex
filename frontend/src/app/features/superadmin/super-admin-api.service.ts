import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

import { CsrfService } from '../../core/auth/csrf.service';
import {
  CompanyDetail,
  CompanyActivityPage,
  CompanyInfrastructure,
  CompanyPage,
  CompanyUser,
  CompanyStatusFilter,
  CompanyBranding,
  UpdateCompanyBranding,
  BrandAssetType,
  CreateCompanyRequest,
  SuperAdminDashboardSummary,
  UpdateCompanyRequest,
} from './super-admin.models';

@Injectable({ providedIn: 'root' })
export class SuperAdminApiService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfService);
  private readonly baseUrl = '/api/v1/superadmin';

  dashboard(): Observable<SuperAdminDashboardSummary> {
    return this.http.get<SuperAdminDashboardSummary>(`${this.baseUrl}/dashboard`);
  }

  companies(
    page: number,
    size: number,
    status: CompanyStatusFilter,
    query: string,
  ): Observable<CompanyPage> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('status', status);
    if (query.trim()) {
      params = params.set('q', query.trim());
    }
    return this.http.get<CompanyPage>(`${this.baseUrl}/companies`, { params });
  }

  company(companyId: string): Observable<CompanyDetail> {
    return this.http.get<CompanyDetail>(
      `${this.baseUrl}/companies/${encodeURIComponent(companyId)}`,
    );
  }

  updateCompany(companyId: string, request: UpdateCompanyRequest): Observable<CompanyDetail> {
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.put<CompanyDetail>(
          `${this.baseUrl}/companies/${encodeURIComponent(companyId)}`,
          request,
        ),
      ),
    );
  }

  companyUsers(companyId: string): Observable<CompanyUser[]> {
    return this.http.get<CompanyUser[]>(
      `${this.baseUrl}/companies/${encodeURIComponent(companyId)}/users`,
    );
  }

  companyActivity(companyId: string, page = 0, size = 20): Observable<CompanyActivityPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<CompanyActivityPage>(
      `${this.baseUrl}/companies/${encodeURIComponent(companyId)}/activity`,
      { params },
    );
  }

  companyInfrastructure(companyId: string): Observable<CompanyInfrastructure> {
    return this.http.get<CompanyInfrastructure>(
      `${this.baseUrl}/companies/${encodeURIComponent(companyId)}/infrastructure`,
    );
  }

  createCompany(request: CreateCompanyRequest): Observable<CompanyDetail> {
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.post<CompanyDetail>(`${this.baseUrl}/companies`, request),
      ),
    );
  }

  retryProvisioning(companyId: string): Observable<CompanyDetail> {
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.post<CompanyDetail>(
          `${this.baseUrl}/companies/${encodeURIComponent(companyId)}/retry-provisioning`,
          {},
        ),
      ),
    );
  }

  branding(companyId: string): Observable<CompanyBranding> {
    return this.http.get<CompanyBranding>(
      `${this.baseUrl}/companies/${encodeURIComponent(companyId)}/branding`,
    );
  }

  updateBranding(
    companyId: string,
    branding: UpdateCompanyBranding,
  ): Observable<CompanyBranding> {
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.put<CompanyBranding>(
          `${this.baseUrl}/companies/${encodeURIComponent(companyId)}/branding`,
          branding,
        ),
      ),
    );
  }

  uploadBrandingAsset(
    companyId: string,
    type: BrandAssetType,
    file: File,
  ): Observable<CompanyBranding> {
    const body = new FormData();
    body.append('file', file);
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.put<CompanyBranding>(
          `${this.baseUrl}/companies/${encodeURIComponent(companyId)}/branding/assets/${type}`,
          body,
        ),
      ),
    );
  }

  deleteBrandingAsset(
    companyId: string,
    type: BrandAssetType,
  ): Observable<CompanyBranding> {
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.delete<CompanyBranding>(
          `${this.baseUrl}/companies/${encodeURIComponent(companyId)}/branding/assets/${type}`,
        ),
      ),
    );
  }

  activate(companyId: string): Observable<CompanyDetail> {
    return this.changeStatus(companyId, 'activate');
  }

  suspend(companyId: string): Observable<CompanyDetail> {
    return this.changeStatus(companyId, 'suspend');
  }

  private changeStatus(
    companyId: string,
    action: 'activate' | 'suspend',
  ): Observable<CompanyDetail> {
    return this.csrf.ensureToken().pipe(
      switchMap(() =>
        this.http.post<CompanyDetail>(
          `${this.baseUrl}/companies/${encodeURIComponent(companyId)}/${action}`,
          {},
        ),
      ),
    );
  }
}

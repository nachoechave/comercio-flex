import { StorefrontTemplate } from '../storefront/storefront-template';

export type CompanyStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'PROVISIONING'
  | 'PROVISIONING_FAILED'
  | 'SUSPENDED';
export type CompanyStatusFilter = CompanyStatus | 'ALL';

export interface SuperAdminDashboardSummary {
  totalCompanies: number;
  activeCompanies: number;
  suspendedCompanies: number;
  provisioningCompanies: number;
  provisioningFailedCompanies: number;
  inactiveCompanies: number;
}

export interface PrimaryAdministrator {
  name: string;
  email: string;
}

export interface CompanySummary {
  id: string;
  name: string;
  slug: string;
  status: CompanyStatus;
  primaryAdministrator: PrimaryAdministrator | null;
  createdAt: string;
}

export interface CompanyDetail extends CompanySummary {
  industry: string | null;
  phone: string | null;
  domain: string | null;
  lastActivityAt: string | null;
}

export interface UpdateCompanyRequest {
  name: string;
  industry: string;
  phone: string | null;
  domain: string | null;
}

export interface CompanyUser {
  id: string;
  name: string;
  email: string;
  role: 'OWNER' | 'ADMIN' | 'STAFF';
  membershipStatus: 'ACTIVE' | 'INACTIVE';
  userStatus: 'ACTIVE' | 'LOCKED' | 'DISABLED';
  joinedAt: string;
}

export interface CompanyActivity {
  id: string;
  action: string;
  actorName: string;
  actorEmail: string;
  createdAt: string;
}

export interface CompanyActivityPage {
  items: CompanyActivity[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface CompanyInfrastructure {
  isolationMode: 'DATABASE_PER_TENANT';
  provisioningStatus: 'PENDING' | 'READY' | 'FAILED' | 'EXTERNAL';
  failureReason?: string | null;
  provisionedAt: string | null;
  updatedAt: string | null;
  customDomainConfigured: boolean;
  lastActivityAt: string | null;
}

export interface TenantProvisioningCapability {
  available: boolean;
  provider: string;
  reason: string | null;
}

export interface CreateCompanyRequest {
  name: string;
  slug: string;
  industry: string;
  administratorEmail: string;
  administratorName: string;
  administratorPhone: string | null;
  domain: string | null;
  initialPassword: string;
  status: 'ACTIVE' | 'INACTIVE';
}

export type BrandFont = 'SYSTEM' | 'SANS' | 'SERIF';
export type { StorefrontTemplate } from '../storefront/storefront-template';
export type BrandAssetType = 'logo' | 'favicon' | 'hero';

export interface CompanyBranding {
  primaryColor: string;
  secondaryColor: string;
  backgroundColor: string;
  textColor: string;
  font: BrandFont;
  heroTitle: string | null;
  heroSubtitle: string | null;
  template: StorefrontTemplate;
  logoUrl: string | null;
  faviconUrl: string | null;
  heroImageUrl: string | null;
}

export type UpdateCompanyBranding = Omit<
  CompanyBranding,
  'logoUrl' | 'faviconUrl' | 'heroImageUrl'
>;

export interface CompanyPage {
  items: CompanySummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export const COMPANY_STATUS_LABELS: Record<CompanyStatus, string> = {
  ACTIVE: 'Activa',
  INACTIVE: 'Inactiva',
  PROVISIONING: 'En aprovisionamiento',
  PROVISIONING_FAILED: 'Falló el aprovisionamiento',
  SUSPENDED: 'Suspendida',
};

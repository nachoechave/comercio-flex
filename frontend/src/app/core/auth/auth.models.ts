export const ADMIN_ROLES = ['OWNER', 'ADMIN', 'STAFF'] as const;

export type AdminRole = (typeof ADMIN_ROLES)[number];

export type PlatformRole = 'USER' | 'SUPER_ADMIN';

export interface PlatformUser {
  id: string;
  email: string;
  displayName: string;
  platformRole: PlatformRole;
}

export interface MembershipSummary {
  storeSlug: string;
  storeName: string;
  role: AdminRole;
}

export interface AnonymousSession {
  authenticated: false;
}

export interface AuthenticatedSession {
  authenticated: true;
  user: PlatformUser;
  memberships: MembershipSummary[];
}

export type CurrentSession = AnonymousSession | AuthenticatedSession;

export interface LoginCredentials {
  email: string;
  password: string;
}

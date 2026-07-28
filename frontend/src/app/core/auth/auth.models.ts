export const ADMIN_ROLES = ['OWNER', 'ADMIN', 'STAFF'] as const;

export type AdminRole = (typeof ADMIN_ROLES)[number];

export interface PlatformUser {
  id: string;
  email: string;
  displayName: string;
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

export type PaymentConnectionStatus =
  'NOT_CONNECTED' | 'AUTHORIZATION_PENDING' | 'CONNECTED' | 'REAUTHORIZATION_REQUIRED';

export type PaymentEnvironment = 'TEST' | 'PRODUCTION';

export interface PaymentConnection {
  provider: 'MERCADO_PAGO';
  environment: PaymentEnvironment;
  status: PaymentConnectionStatus;
  connectedAccountLabel: string | null;
  connectedAt: string | null;
}

export interface PaymentAuthorizationStart {
  authorizationUrl: string;
  expiresAt: string;
}

export type PaymentWebhookOperationalStatus = 'DEAD';

/**
 * Safe operational projection. The API must never include the webhook payload,
 * payment data or customer data in this response.
 */
export interface PaymentWebhookEventSummary {
  eventId: string;
  status: PaymentWebhookOperationalStatus;
  attemptCount: number;
  safeErrorCode: string | null;
  occurredAt: string;
  retryAllowed: boolean;
}

export interface PaymentWebhookRetryResult {
  eventId: string;
  status: 'RETRY_SCHEDULED';
  scheduledAt: string;
}

export interface PaymentStoreSettings {
  timezone: string;
}

export type QrProvisioningStatus = 'NO_CONFIGURADO' | 'VERIFICANDO' | 'LISTO' | 'ERROR';

export type QrAuthorizationStatus =
  | 'NOT_CHECKED'
  | 'AUTHORIZED'
  | 'UNAUTHORIZED_SCOPES'
  | 'NOT_FOUND'
  | 'PROVIDER_ERROR';

export interface QrSetup {
  environment: PaymentEnvironment;
  status: QrProvisioningStatus;
  authorization: QrAuthorizationStatus;
  storeConfigured: boolean;
  posConfigured: boolean;
  externalPosIdAvailable: boolean;
  qrOrdersReady: boolean;
}

export interface ConfigureQrRequest {
  storeName: string;
  streetName: string;
  streetNumber: string;
  cityName: string;
  stateName: string;
  latitude: number;
  longitude: number;
  reference: string | null;
}

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

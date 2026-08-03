import { HttpErrorResponse } from '@angular/common/http';

export function paymentConnectionErrorMessage(
  error: unknown,
  fallback = 'No pudimos actualizar la conexión con Mercado Pago.',
): string {
  if (!(error instanceof HttpErrorResponse)) return fallback;
  const code = isProblemDetail(error.error) ? error.error.code : null;
  if (code === 'PAYMENT_CONNECTION_DISABLED') {
    return 'La conexión de cuentas de Mercado Pago está deshabilitada en este entorno.';
  }
  if (error.status === 403) return 'Sólo el propietario puede configurar Mercado Pago.';
  if (error.status === 409) {
    return 'La conexión cambió en otra pestaña. Recargá la página e intentá nuevamente.';
  }
  if (error.status === 0 || error.status >= 500) {
    return 'No pudimos comunicarnos con el servicio de pagos. Intentá nuevamente.';
  }
  return fallback;
}

function isProblemDetail(value: unknown): value is { code?: string } {
  return typeof value === 'object' && value !== null && 'code' in value;
}
